/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.chat2db.spi.util;

import com.google.common.collect.ImmutableMap;
import com.oceanbase.tools.sqlparser.oracle.PlSqlLexer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * currently only for oracle mode <br>
 * TODO: support mysql mode
 */
@Slf4j
public class SqlSplitter {

    private static final int MAX_PL_PATTEN_TYPE_SIZE = 3;
    private static final String DEFAULT_SQL_DELIMITER = ";";
    private static final char[] SPACES_CHARS = "\r\n\t ".toCharArray();
    private final LexerTokenDefinition tokenDefinition;
    private final LexerFactory lexerFactory;
    private final InnerUtils innerUtils;
    private final int DEFAULT_PL_END_DELIMITER;
    private final int SQL_DELIMITER;
    private final int PL_ELSE;
    private final int PL_THEN;
    private final int PL_RIGHTPAREN;
    private final int PL_LEFTPAREN;
    private final int PL_GREATER_THAN_OP;

    /**
     * PL 块开始判断 token 清单
     */
    private final int[][] ALL_PL_START_PATTERNS;

    /**
     * 用于移除 SQL 语句前缀注释部分
     */
    private final int[] BLANK_OR_COMMENT_TYPES;

    /**
     * 用于判断 PL 块开始的 token type 忽略清单
     */
    private final int[] PL_START_PATTERN_IGNORE_TYPES;

    /**
     * 用于判断 IDENT Token
     */
    private final int[] PL_IDENT_TYPES;

    /**
     * 缓存 Token 类型 List， 用于判断是否进入 PL Block <br>
     * 为了保证性能，最多只使用 MAX_PL_PATTEN_TYPE_SIZE 个 token 判断，超出后则肯定不是 PL Block
     */
    private final List<Integer> cacheTokenTypes = new ArrayList<>();

    /**
     * 当前语句缓冲区，确定一个语句结束时会清空缓冲区用于下一个语句
     */
    private final StringBuilder currentStmtBuilder = new StringBuilder();

    /**
     * 拆句结果
     */
    private final List<SplitSqlString> stmts = new ArrayList<>();

    private final boolean addDelimiter;

    /**
     * 当前语句状态，初始为 SQL_STMT，进去 PL BLock 后切换到 PL_STMT 状态
     */
    private State state = State.SQL_STMT;

    /**
     * 当前拆句关键字，初始为连接会话的 delimiter，拆词结束后回写到连接会话的 delimiter
     */
    private String delimiter;

    /**
     * 为避免每次匹配拆句关键字重复解析，每次 delimiter 变化时同步计算对应的 tokens，和 delimiter 一致
     */
    private Token[] delimiterTokens;

    /**
     * 是否移除 注释前缀，用于绕过部分 OB 版本 PL 语句带注释前缀报错的问（OB 3.1.x）<br>
     */
    @Getter
    @Setter
    private boolean removeCommentPrefix = false;

    /**
     * 用于记录在PL块中的子代码块嵌套情况
     */
    private Stack<SubPLLevel> subPLStack = new Stack();

    /**
     * 用于缓存PL块中 Token 类型 List， 用于判断是否进入子 PL Block <br>
     * 为了保证性能，最多只使用 MAX_PL_PATTEN_TYPE_SIZE 个 token 判断，超出后则肯定不是 PL Block
     */
    private List<Integer> plCacheTokenTypes = new ArrayList<>();

    /**
     * 用于记录 `ALL_PL_START_PATTERNS` 中PL块开始标志index和其枚举类型的映射关系
     */
    private Map<Integer, PLStartSymbol> INDEX_2_START_SYMBOL;

    private String sql;
    private Boolean whileForLoopFlag = false;

    private Holder<Integer> currentOffset = new Holder<>(0);


    public SqlSplitter(Class<? extends Lexer> lexerType) {
        this(lexerType, DEFAULT_SQL_DELIMITER);
    }

    public SqlSplitter(Class<? extends Lexer> lexerType, String delimiter) {
        this(lexerType, delimiter, true);
    }

    public SqlSplitter(Class<? extends Lexer> lexerType, String delimiter, boolean addDelimiter) {
        this.tokenDefinition = LexerTokenDefinitions.of(lexerType);
        this.lexerFactory = LexerFactories.of(lexerType);
        this.innerUtils = new InnerUtils();
        this.delimiter = delimiter;
        this.addDelimiter = addDelimiter;

        LexerTokenDefinition definition = this.tokenDefinition;
        this.DEFAULT_PL_END_DELIMITER = definition.DIV();
        this.SQL_DELIMITER = definition.SEMICOLON();
        this.PL_ELSE = definition.ELSE();
        this.PL_THEN = definition.THEN();
        this.PL_RIGHTPAREN = definition.RIGHTBRACKET();
        this.PL_LEFTPAREN = definition.LEFTBRACKET();
        this.PL_GREATER_THAN_OP = definition.GREATER_THAN_OP();

        int[] ANONYMOUS_BLOCK_DECLARE_START = new int[] {definition.DECLARE()};
        int[] ANONYMOUS_BLOCK_BEGIN_START = new int[] {definition.BEGIN()};
        int[] CREATE_FUNCTION_START = new int[] {definition.CREATE(), definition.FUNCTION()};
        int[] CREATE_PROCEDURE_START = new int[] {definition.CREATE(), definition.PROCEDURE()};
        int[] CREATE_TRIGGER_START = new int[] {definition.CREATE(), definition.TRIGGER()};
        int[] CREATE_PACKAGE_START = new int[] {definition.CREATE(), definition.PACKAGE()};
        int[] CREATE_PACKAGE_BODY_START = new int[] {definition.CREATE(), definition.PACKAGE(), definition.BODY()};
        int[] CREATE_TYPE_START = new int[] {definition.CREATE(), definition.TYPE()};
        int[] CREATE_TYPE_BODY_START = new int[] {definition.CREATE(), definition.TYPE(), definition.BODY()};

        int[] FUNCTION_START = new int[] {definition.FUNCTION()};
        int[] PROCEDURE_START = new int[] {definition.PROCEDURE()};
        int[] TRIGGER_START = new int[] {definition.TRIGGER()};
        int[] PACKAGE_START = new int[] {definition.PACKAGE()};
        int[] PACKAGE_BODY_START = new int[] {definition.PACKAGE(), definition.BODY()};
        int[] LOOP_START = new int[] {definition.LOOP()};
        int[] FOR_START = new int[] {definition.FOR()};
        int[] IF_START = new int[] {definition.IF()};
        int[] CASE_START = new int[] {definition.CASE()};
        int[] WHILE_START = new int[] {definition.WHILE()};

        this.ALL_PL_START_PATTERNS = new int[][] {ANONYMOUS_BLOCK_DECLARE_START, ANONYMOUS_BLOCK_BEGIN_START,
                CREATE_FUNCTION_START, CREATE_PROCEDURE_START, CREATE_TRIGGER_START, CREATE_PACKAGE_START,
                CREATE_PACKAGE_BODY_START, CREATE_TYPE_START, CREATE_TYPE_BODY_START,
                FUNCTION_START, PROCEDURE_START, TRIGGER_START, PACKAGE_START, PACKAGE_BODY_START,
                LOOP_START, FOR_START, IF_START, CASE_START, WHILE_START};

        this.INDEX_2_START_SYMBOL = ImmutableMap.<Integer, PLStartSymbol>builder().put(0, PLStartSymbol.DECLARE)
                .put(1, PLStartSymbol.BEGIN)
                .put(2, PLStartSymbol.CREATE_FUNCTION)
                .put(3, PLStartSymbol.CREATE_PROCEDURE)
                .put(4, PLStartSymbol.CREATE_TRIGGER)
                .put(5, PLStartSymbol.CREATE_PACKAGE)
                .put(6, PLStartSymbol.CREATE_PACKAGE_BODY)
                .put(7, PLStartSymbol.CREATE_TYPE)
                .put(8, PLStartSymbol.CREATE_TYPE_BODY)
                .put(9, PLStartSymbol.FUNCTION)
                .put(10, PLStartSymbol.PROCEDURE)
                .put(11, PLStartSymbol.TRIGGER)
                .put(12, PLStartSymbol.PACKAGE)
                .put(13, PLStartSymbol.PACKAGE_BODY)
                .put(14, PLStartSymbol.LOOP)
                .put(15, PLStartSymbol.FOR)
                .put(16, PLStartSymbol.IF)
                .put(17, PLStartSymbol.CASE)
                .put(18, PLStartSymbol.WHILE).build();

        this.BLANK_OR_COMMENT_TYPES = definition.ANTLR_SKIP() > Token.MIN_USER_TOKEN_TYPE
                ? new int[] {definition.SPACES(), definition.ANTLR_SKIP()}
                : new int[] {definition.SPACES(), definition.SINGLE_LINE_COMMENT(),
                        definition.MULTI_LINE_COMMENT()};
        this.PL_START_PATTERN_IGNORE_TYPES = new int[] {definition.OR(), definition.REPLACE(), definition.EDITIONABLE(),
                definition.NONEDITIONABLE(), definition.BODY(), definition.AS(), definition.IS()};

        this.PL_IDENT_TYPES = definition.IDENT() > Token.MIN_USER_TOKEN_TYPE ? new int[] {definition.IDENT()}
                : new int[] {definition.REGULAR_ID(), definition.DELIMITED_ID()};

        this.delimiterTokens = innerUtils.extractDelimiterTokens(delimiter);
    }

    public String getDelimiter() {
        return delimiter;
    }

    public List<SplitSqlString> split(String sql) {
        if (StringUtils.isBlank(sql)) {
            return new ArrayList<>();
        }
        clear();
        this.sql = sql;

        /**
         * Antlr Lexer 拆词后的 token 列表
         */
        Token[] tokens = innerUtils.initTokens(sql);

        int tokenCount = tokens.length;
        int labelRightCount = 0;
        for (int pos = 0; pos < tokenCount; pos++) {
            Token token = tokens[pos];
            int type = token.getType();
            if (type < Token.MIN_USER_TOKEN_TYPE) {
                // invalid token type
                continue;
            }

            String text = token.getText();
            int offset = token.getStartIndex();
            if (">".equals(text)) {
                labelRightCount++;
            } else {
                labelRightCount = 0;
            }
            if (this.removeCommentPrefix
                    && 0 == currentStmtBuilder.length()
                    && innerUtils.isBlankOrComment(type)) {
                continue;
            }
            if (innerUtils.isPLStartPatternIgnoreTypes(type)) {
                if (StringUtils.isBlank(currentStmtBuilder.toString()) && type != tokenDefinition.SPACES()) {
                    currentOffset.setValue(offset);
                }
                // skip analysis blank, comment and other PL block start math pattern ignore types
                currentStmtBuilder.append(text);
                continue;
            }

            if (this.state == State.SQL_STMT) {
                if (cacheTokenTypes.size() < MAX_PL_PATTEN_TYPE_SIZE) {
                    cacheTokenTypes.add(type);
                }
                if (cacheTokenTypes.size() == 1 && innerUtils.isDelimiterCommand(token)) {
                    pos = executeDelimiterCommand(tokens, pos);
                    continue;
                }
                if (isPLBlockStart()) {
                    pushToStack(cacheTokenTypes);
                    if (StringUtils.isBlank(currentStmtBuilder.toString()) && type != tokenDefinition.SPACES()) {
                        currentOffset.setValue(offset);
                    }
                    currentStmtBuilder.append(text);
                    this.state = State.PL_STMT;
                    cacheTokenTypes.clear();
                } else if (isStmtEnd(tokens, pos)) {
                    pos = addStmtWhileStmtEnd(tokens, pos);
                } else {
                    if (StringUtils.isBlank(currentStmtBuilder.toString()) && type != tokenDefinition.SPACES()) {
                        currentOffset.setValue(offset);
                    }
                    currentStmtBuilder.append(text);
                }
            } else if (this.state == State.PL_STMT) {
                // sql statement inside PL block end
                if (SQL_DELIMITER == type || PL_ELSE == type || PL_THEN == type || PL_RIGHTPAREN == type
                        || (labelRightCount == 2 && PL_GREATER_THAN_OP == type) || PL_LEFTPAREN == type) {
                    plCacheTokenTypes.clear();
                    labelRightCount = 0;
                } else if (plCacheTokenTypes.size() < MAX_PL_PATTEN_TYPE_SIZE) {
                    plCacheTokenTypes.add(type);
                }
                if (!subPLStack.empty() && (type == tokenDefinition.EXTERNAL() || type == tokenDefinition.LANGUAGE())) {
                    subPLStack.peek().matchExternalOrLanguage = true;
                } else if (!subPLStack.empty() && (type == tokenDefinition.IS() || type == tokenDefinition.AS())) {
                    // `IS` may run into case like `cursor cur1 is select col from for_loop_cursor_t;`
                    // in this case, it does not have parent pl block
                    subPLStack.peek().matchIsOrAs = true;
                } else if (!subPLStack.empty() && subPLStack.peek().startSymbol == PLStartSymbol.CREATE_TYPE
                        && (type == tokenDefinition.MEMBER() || type == tokenDefinition.STATIC())) {
                    // temporarily set matchMemberOrStatic in parent subPLLevel
                    // when encounters sub Function / Procedure in create type
                    // set sub Function / Procedure's matchMemberOrStatic
                    // and recover parent subPLLevel matchMemberOrStatic value to false
                    subPLStack.peek().matchMemberOrStatic = true;
                }

                if (isStmtEnd(tokens, pos)) {
                    pos = addStmtWhileStmtEnd(tokens, pos);
                    this.state = State.SQL_STMT;
                } else {
                    if (isSubPLBlockStart()) {
                        pushToStack(plCacheTokenTypes);
                        plCacheTokenTypes.clear();
                    }
                    int posShift = isPLBlockEnd(tokens, pos);
                    if (posShift >= 0) {
                        pos += posShift;
                        subPLStack.pop();
                        plCacheTokenTypes.clear();
                    }
                    if (StringUtils.isBlank(currentStmtBuilder.toString()) && type != tokenDefinition.SPACES()) {
                        currentOffset.setValue(offset);
                    }
                    currentStmtBuilder.append(text);
                    // add additional tokens in which may contains in pl block ending tokens
                    // like end[;] / end [object_name;] / end [loop;] / end [if;] / end [case;]
                    if (posShift > 0) {
                        for (int index = 1; index <= posShift; index++) {
                            if (StringUtils.isBlank(currentStmtBuilder.toString())
                                    && type != tokenDefinition.SPACES()) {
                                currentOffset.setValue(offset);
                            }
                            currentStmtBuilder.append(tokens[pos - posShift + index].getText());
                        }
                    }
                }
            }
        }
        addStmtWhileStmtEnd(tokens, tokenCount);
        return stmts;
    }

    public static SqlStatementIterator iterator(InputStream in, Charset charset, String delimiter) {
        return iterator(in, charset, delimiter, true);
    }

    public static SqlStatementIterator iterator(InputStream in, Charset charset, String delimiter,
            boolean addDelimiter) {
        return new SqlSplitterIterator(in, charset, delimiter, addDelimiter);
    }

    private void clear() {
        this.stmts.clear();
        this.cacheTokenTypes.clear();
        this.currentStmtBuilder.setLength(0);
        this.state = State.SQL_STMT;
    }

    private int addStmtWhileStmtEnd(Token[] tokens, int pos) {
        String currentStmt = currentStmtBuilder.toString();
        boolean notDefaultSqlDelimiter = false;
        if (StringUtils.isNotBlank(currentStmt)) {
            if (addDelimiter) {
                for (int cursor = pos - 1; cursor > 0; cursor--) {
                    Token token = tokens[cursor];
                    if (innerUtils.isEOF(token.getType()) || innerUtils.isBlankOrComment(token.getType())) {
                        continue;
                    }
                    notDefaultSqlDelimiter = !DEFAULT_SQL_DELIMITER.equals(token.getText());
                    if (notDefaultSqlDelimiter) {
                        currentStmt += DEFAULT_SQL_DELIMITER;
                    }
                    break;
                }
            }
            this.stmts.add(new SplitSqlString(currentOffset.getValue(), currentStmt.trim()));
            if (notDefaultSqlDelimiter) {
                this.currentOffset.setValue(this.currentOffset.getValue() + DEFAULT_SQL_DELIMITER.length());
            }
        }
        this.cacheTokenTypes.clear();
        this.currentStmtBuilder.setLength(0);
        return pos + delimiterTokens.length - 1;
    }

    private int executeDelimiterCommand(Token[] tokens, int pos) {
        // delimiter command identified, will ignore built-in pl delimiter logic,
        // examples:
        // - delimiter $$
        // - delimiter /
        if (pos + 2 >= tokens.length) {
            // invalid syntax
            throw new IllegalArgumentException("Invalid delimiter command syntax");
        }
        pos++;
        Token expectBlank = tokens[pos];
        if (expectBlank.getType() != tokenDefinition.SPACES()) {
            throw new IllegalArgumentException(
                    "Invalid delimiter command syntax, expect blank after 'delimiter'");
        }
        List<Token> delimiterTokensToSet = new ArrayList<>();
        StringBuilder delimiterBuilder = new StringBuilder();

        // ignore multiple blanks between delimiter keyword and value of delimiter
        boolean hasDelimiterValue = false;
        while (++pos < tokens.length) {
            Token delimiterToken = tokens[pos];
            int delimiterTokenType = delimiterToken.getType();
            if (delimiterTokenType > Token.MIN_USER_TOKEN_TYPE && delimiterTokenType != tokenDefinition.SPACES()) {
                hasDelimiterValue = true;
                break;
            }
        }
        if (hasDelimiterValue) {
            --pos;
        } else {
            throw new IllegalArgumentException(
                    "Invalid delimiter command syntax, no delimiter value set");
        }

        // extract value of delimiter, may multiple tokens
        while (++pos < tokens.length) {
            Token delimiterToken = tokens[pos];
            int delimiterTokenType = delimiterToken.getType();
            if (delimiterTokenType > Token.MIN_USER_TOKEN_TYPE && delimiterTokenType != tokenDefinition.SPACES()) {
                delimiterTokensToSet.add(delimiterToken);
                delimiterBuilder.append(delimiterToken.getText());
            } else {
                break;
            }
        }
        if (delimiterTokensToSet.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid delimiter command syntax, no delimiter value set");
        }
        this.delimiterTokens = delimiterTokensToSet.toArray(new Token[0]);
        this.delimiter = delimiterBuilder.toString();
        cacheTokenTypes.clear();
        return pos;
    }

    private boolean isPLBlockStart() {
        if (cacheTokenTypes.size() > MAX_PL_PATTEN_TYPE_SIZE) {
            return false;
        }
        int[] cacheTokenTypeArray = cacheTokenTypes.stream().mapToInt(i -> i).toArray();
        for (int[] pattern : ALL_PL_START_PATTERNS) {
            if (Arrays.equals(pattern, cacheTokenTypeArray)) {
                return true;
            }
        }
        return false;
    }

    /**
     * <pre>
     * sub PL block start rule is different from the first PL block start
     * cache token types are stored in a queue because when we run into a new symbol,
     * we always add to the tail of queue and try to remove head of queue when it it full
     *
     * when trying to match ALL_PL_START_PATTERNS, we always try to match the longer start pattern,
     * for example, match `create type body` but not `create type`
     * </pre>
     */
    private boolean isSubPLBlockStart() {
        if (plCacheTokenTypes.size() > MAX_PL_PATTEN_TYPE_SIZE) {
            return false;
        }
        int[] plCacheTokenTypeArray = plCacheTokenTypes.stream().mapToInt(i -> i).toArray();
        for (int[] pattern : ALL_PL_START_PATTERNS) {
            if (Arrays.equals(pattern, plCacheTokenTypeArray)) {
                return true;
            }
        }
        return false;
    }

    private void pushToStack(Collection<Integer> sourceCache) {
        PLStartSymbol currentSymbol = recognizeStartSymbol(sourceCache);
        if (Objects.nonNull(currentSymbol)
                && (currentSymbol == PLStartSymbol.WHILE || currentSymbol == PLStartSymbol.FOR)) {
            whileForLoopFlag = true;
        }
        if (Objects.nonNull(currentSymbol) && whileForLoopFlag && currentSymbol == PLStartSymbol.LOOP) {
            whileForLoopFlag = false;
            return;
        }
        // declare does not need to push into stack
        // because declare ... begin ... end block can be recognized by begin ... end
        // declare does not have an explicit ending
        if (Objects.nonNull(currentSymbol) && currentSymbol != PLStartSymbol.DECLARE) {
            SubPLLevel subPLLevel = new SubPLLevel(currentSymbol);
            // begin symbol does not need to push into stack when its wrapped in [create] function / procedure /
            // trigger / package [body] / type [body]
            // because in this case, it is always like `create function ... begin ... end` which can be
            // recognized by create function ... end
            if (!subPLStack.empty() && currentSymbol == PLStartSymbol.BEGIN) {
                switch (subPLStack.peek().startSymbol) {
                    case FUNCTION:
                    case PROCEDURE:
                    case TRIGGER:
                    case PACKAGE:
                    case PACKAGE_BODY:
                    case CREATE_FUNCTION:
                    case CREATE_PROCEDURE:
                    case CREATE_TRIGGER:
                    case CREATE_PACKAGE:
                    case CREATE_PACKAGE_BODY:
                    case CREATE_TYPE:
                    case CREATE_TYPE_BODY:
                        return;
                }
            } else if (!subPLStack.empty() && subPLStack.peek().startSymbol == PLStartSymbol.CREATE_TYPE
                    && (currentSymbol == PLStartSymbol.FUNCTION || currentSymbol == PLStartSymbol.PROCEDURE)) {
                // inherit matchMemberOrStatic from parent `CREATE_TYPE` sentence to sub Function or procedure
                // declare
                subPLLevel.matchMemberOrStatic = subPLStack.peek().matchMemberOrStatic;
                subPLStack.peek().matchMemberOrStatic = false;
            }
            subPLStack.push(subPLLevel);
        }
    }

    private PLStartSymbol recognizeStartSymbol(Collection<Integer> sourceCache) {
        if (sourceCache.size() > MAX_PL_PATTEN_TYPE_SIZE) {
            return null;
        }
        int[] cacheTokenTypeArray = sourceCache.stream().mapToInt(i -> i).toArray();
        for (int i = 0; i < ALL_PL_START_PATTERNS.length; i++) {
            int[] pattern = ALL_PL_START_PATTERNS[i];
            if (Arrays.equals(pattern, cacheTokenTypeArray)) {
                return INDEX_2_START_SYMBOL.get(i);
            }
        }
        return PLStartSymbol.UNKNOWN;
    }

    /**
     * <pre>
     * PL block ending judgement may involve pos moving forward.
     * For example in case LOOP / IF / CASE, ending push pos to pos + 2.
     * The result of this function means position shift
     * 0 means is end and pos does not need any move
     * value < 0 means is not end
     * value > 0 means is end and pos needs moving forward according to value
     * </pre>
     */
    private int isPLBlockEnd(Token[] tokens, int pos) {
        if (pos >= tokens.length || subPLStack.empty()) {
            return -1;
        }

        boolean isEnd;
        int posShift = 0;
        /**
         * <pre>
         * `subPLStack` in this function must not be empty because this fuction must be called when state in PL_STMT
         * `subPLStack` must have been pushed at least once before enter PL_STMT
         * </pre>
         */
        SubPLLevel peekLevel = subPLStack.peek();
        switch (peekLevel.startSymbol) {
            case CREATE_TYPE:
                isEnd = matchDelimiterTokens(tokens, pos);
                break;
            case BEGIN:
            case CREATE_TYPE_BODY:
            case TRIGGER:
            case CREATE_TRIGGER:
                isEnd = tokens[pos].getType() == this.tokenDefinition.END();
                break;
            case FUNCTION:
            case PROCEDURE:
                // member or static function && procedure declare in type which does not contain IS or AS
                // should end with `)` or `,`
                if (peekLevel.matchMemberOrStatic && !peekLevel.matchIsOrAs) {
                    isEnd = tokens[pos].getText().equals(")") || tokens[pos].getText().equals(",");
                    break;
                }
            case PACKAGE:
            case PACKAGE_BODY:
            case CREATE_FUNCTION:
            case CREATE_PROCEDURE:
            case CREATE_PACKAGE:
            case CREATE_PACKAGE_BODY:
                boolean matchDelimiter = matchDelimiterTokens(tokens, pos);
                if (matchDelimiter) {
                    // in only two cases, delimiter can be the end of pl create sentence
                    // 1. `IS` or `AS` is not matched
                    // 2. `IS` or `AS` is matched but `EXTERNAL` or `LANGUAGE` is also matched
                    isEnd = !peekLevel.matchIsOrAs || peekLevel.matchExternalOrLanguage;
                } else {
                    isEnd = tokens[pos].getType() == this.tokenDefinition.END();
                }
                break;
            case FOR:
            case LOOP:
            case WHILE:
                isEnd = matchPLBlockEnd(tokens, pos, this.tokenDefinition.LOOP());
                posShift = isEnd ? 2 : 0;
                break;
            case IF:
                isEnd = matchPLBlockEnd(tokens, pos, this.tokenDefinition.IF());
                posShift = isEnd ? 2 : 0;
                break;
            case CASE:
                isEnd = matchPLBlockEnd(tokens, pos, this.tokenDefinition.CASE());
                posShift = isEnd ? 2 : 0;
                break;
            case UNKNOWN:
            default:
                throw new RuntimeException(
                        String.format("Unsupported pl start symbol: %s", peekLevel.startSymbol));
        }
        if (isEnd) {
            return posShift;
        }
        return -1;
    }

    private boolean matchPLBlockEnd(Token[] tokens, int pos, Integer endObjectType) {
        boolean match = tokens[pos].getType() == this.tokenDefinition.END();
        int tokensLength = tokens.length;
        if (Objects.nonNull(endObjectType)) {
            if (endObjectType != Token.MIN_USER_TOKEN_TYPE) {
                // use MIN_USER_TOKEN_TYPE means place holder here
                // in which we can recognize `end object_name;` as pl block ending
                match &= (pos + 2 < tokensLength) && tokens[pos + 2].getType() == endObjectType;
            }
        }
        return match;
    }

    private boolean isStmtEnd(Token[] tokens, int pos) {
        // only use Div `/` as while in PL stmt and use `;` as delimiter
        if (pos >= tokens.length) {
            return false;
        }
        if (!subPLStack.empty()) {
            return false;
        }

        if (this.state == State.PL_STMT) {
            if (!DEFAULT_SQL_DELIMITER.equals(delimiter)) {
                return matchDelimiterTokens(tokens, pos);
            }
            return tokens[pos].getType() == DEFAULT_PL_END_DELIMITER;
        }
        return matchDelimiterTokens(tokens, pos);
    }

    private boolean matchDelimiterTokens(Token[] tokens, int pos) {
        Token[] dt = delimiterTokens;
        if (this.state == State.PL_STMT && !subPLStack.empty()) {
            dt = innerUtils.extractDelimiterTokens(DEFAULT_SQL_DELIMITER);
        }
        int delimiterLength = dt.length;
        int tokensLength = tokens.length;
        if (pos + delimiterLength > tokensLength) {
            return false;
        }
        for (int i = 0; i < delimiterLength; i++) {
            if (!innerUtils.isTokenEquals(dt[i], tokens[pos + i])) {
                return false;
            }
        }
        return true;
    }

    enum State {
        SQL_STMT,
        PL_STMT
    }

    enum PLStartSymbol {
        BEGIN,
        DECLARE,
        CREATE_FUNCTION,
        CREATE_PROCEDURE,
        CREATE_TRIGGER,
        CREATE_PACKAGE,
        CREATE_PACKAGE_BODY,
        CREATE_TYPE,
        CREATE_TYPE_BODY,
        FUNCTION,
        PROCEDURE,
        TRIGGER,
        PACKAGE,
        PACKAGE_BODY,
        FOR,
        LOOP,
        IF,
        CASE,
        WHILE,
        UNKNOWN
    }

    class SubPLLevel {
        private PLStartSymbol startSymbol;
        /**
         * 用于记录PL对象DDL语句[如create package / function 等]中是否出现了 EXTERNAL 或者 LANGUAGE 关键字
         * 如果有，则当前DDL语句的结束符只能为delimiter
         */
        private boolean matchExternalOrLanguage = false;
        /**
         * 用于记录PL对象DDL语句[如create package / function 等]中是否出现了 IS 或者 AS 关键字
         */
        private boolean matchIsOrAs = false;
        /**
         * 用于记录Type对象中是否出现了 MEMBER 或者 STATIC 关键字
         */
        private boolean matchMemberOrStatic = false;

        private SubPLLevel(PLStartSymbol startSymbol) {
            this.startSymbol = startSymbol;
        }
    }

    class InnerUtils {
        Token[] initTokens(String sql) {
            return tokens(sql).toArray(new Token[0]);
        }

        Token[] extractDelimiterTokens(String delimiter) {
            return tokens(delimiter).stream()
                    .filter(token -> token.getType() > Token.MIN_USER_TOKEN_TYPE)
                    .toArray(Token[]::new);
        }

        /**
         * Oracle 词法文件识别 IDENT 关键字必须是字母开头，对于形如 delimiter $$ 语句，其中的 $$ 会被认为是错误的词法，<br>
         * 这里对不识别的词法转换为 IDENT 和 SPACES 类型的 Token，使得上层可以一致化处理
         */
        private List<Token> tokens(String sql) {
            List<Token> tokens = initTokenStream(sql).getTokens();
            int length = sql.codePointCount(0, sql.length());
            int size = tokens.size();
            Token firstToken = size > 0 ? tokens.get(0) : null;
            Token lastToken = size > 0 ? tokens.get(size - 1) : null;
            List<Token> allTokens = new ArrayList<>(tokens.size());
            if (firstToken != null) {
                if (firstToken.getStartIndex() > 0) {
                    allTokens.addAll(generateInvalidTokens(sql, 0, firstToken.getStartIndex()));
                }
                allTokens.add(firstToken);
            }
            for (int i = 1; i < size; i++) {
                Token current = tokens.get(i);
                Token previous = tokens.get(i - 1);
                if (current.getStartIndex() - previous.getStopIndex() > 1) {
                    allTokens.addAll(generateInvalidTokens(sql, previous.getStopIndex() + 1, current.getStartIndex()));
                }
                allTokens.add(current);
            }
            if (lastToken != null && lastToken.getStopIndex() < length - 1) {
                allTokens.addAll(generateInvalidTokens(sql, lastToken.getStopIndex() + 1, length));
            }
            return allTokens;
        }

        private List<Token> generateInvalidTokens(String sql, int start, int end) {
            String invalidStr = StringUtils.substring(sql, start, end);
            char[] chars = invalidStr.toCharArray();
            if (chars.length == 1) {
                Token token = new CommonToken(PL_IDENT_TYPES[0], invalidStr);
                return Collections.singletonList(token);
            }
            List<Token> invalidTokens = new ArrayList<>();
            boolean lastCharSpace = ArrayUtils.contains(SPACES_CHARS, chars[0]);
            boolean currentCharSpace;
            int charProcessPos = 0;
            for (int i = 1; i < chars.length; i++) {
                currentCharSpace = ArrayUtils.contains(SPACES_CHARS, chars[i]);
                if (lastCharSpace != currentCharSpace) {
                    invalidTokens.add(invalidToken(lastCharSpace, invalidStr, charProcessPos, i));
                    charProcessPos = i;
                }
                lastCharSpace = currentCharSpace;
            }
            if (charProcessPos <= chars.length - 1) {
                invalidTokens.add(invalidToken(lastCharSpace, invalidStr, charProcessPos, chars.length));
            }
            return invalidTokens;
        }

        private Token invalidToken(boolean spaces, String str, int start, int end) {
            String text = StringUtils.substring(str, start, end);
            Token token = spaces ? new CommonToken(tokenDefinition.SPACES(), text)
                    : new CommonToken(PL_IDENT_TYPES[0], text);
            return token;
        }

        private CommonTokenStream initTokenStream(String sql) {
            CharStream input = CharStreams.fromString(sql);
            Lexer lexer = lexerFactory.create(input);
            CommonTokenStream tokenStream = new CommonTokenStream(lexer);
            tokenStream.fill();
            return tokenStream;
        }

        boolean isDelimiterCommand(Token token) {
            int type = token.getType();
            String text = token.getText();
            return isIdent(type) && StringUtils.equalsIgnoreCase("delimiter", text);
        }

        boolean isTokenEquals(Token left, Token right) {
            return left.getType() == right.getType() && StringUtils.equalsIgnoreCase(left.getText(), right.getText());
        }

        boolean isPLStartPatternIgnoreTypes(int tokenType) {
            if (isBlankOrComment(tokenType)) {
                return true;
            }
            return ArrayUtils.contains(PL_START_PATTERN_IGNORE_TYPES, tokenType);
        }

        boolean isBlankOrComment(int tokenType) {
            return ArrayUtils.contains(BLANK_OR_COMMENT_TYPES, tokenType);
        }

        boolean isEOF(int tokenType) {
            return tokenType == -1;
        }

        private boolean isIdent(int tokenType) {
            return ArrayUtils.contains(PL_IDENT_TYPES, tokenType);
        }

    }

    private static class SqlSplitterIterator implements SqlStatementIterator {

        private final BufferedReader reader;
        private final StringBuilder buffer = new StringBuilder();
        private final LinkedList<SplitSqlString> holder = new LinkedList<>();
        private final boolean addDelimiter;

        private SplitSqlString current;
        private String delimiter;
        private boolean firstLine = true;
        private List<String> sqls = new ArrayList<>();
        private long iteratedBytes = 0;
        private int offset = 0;

        private static final Character SQL_SEPARATOR_CHAR = '/';
        private static final Character LINE_SEPARATOR_CHAR = '\n';
        private static final String SQL_MULTI_LINE_COMMENT_PREFIX = "/*";
        private static final Set<Character> DELIMITER_CHARACTERS = new HashSet<>(Arrays.asList(';', '/', '$'));

        public SqlSplitterIterator(InputStream input, Charset charset, String delimiter, boolean addDelimiter) {
            this.reader = new BufferedReader(new InputStreamReader(input, charset));
            this.delimiter = delimiter;
            this.addDelimiter = addDelimiter;
        }

        @Override
        public boolean hasNext() {
            if (this.current == null) {
                this.current = parseNext();
            }
            return this.current != null;
        }

        @Override
        public SplitSqlString next() {
            SplitSqlString next = this.current;
            this.current = null;
            if (next == null) {
                next = parseNext();
                if (next == null) {
                    throw new NoSuchElementException("No more available sql.");
                }
            }
            return next;
        }

        @Override
        public long iteratedBytes() {
            return this.iteratedBytes;
        }

        private SplitSqlString parseNext() {
            try {
                if (!this.holder.isEmpty()) {
                    return this.holder.poll();
                }
                String line;
                while (this.holder.isEmpty() && (line = this.reader.readLine()) != null) {
                    this.iteratedBytes += line.getBytes(Charset.defaultCharset()).length + 1;
                    addLineToBuffer(line);
                    SqlSplitProcessor processor = new SqlSplitProcessor();
                    LinkedList<SplitSqlString> innerHolder = new LinkedList<>();
                    StringBuffer innerBuffer = new StringBuffer();
                    Holder<Integer> bufferOrder = new Holder<>(0);
                    processor.addLineOracle(innerHolder, innerBuffer, bufferOrder,
                            line.chars().mapToObj(c -> new SqlSplitProcessor.OrderChar((char) c, -1)).collect(Collectors.toList()));
                    while (processor.isMlComment() && (line = reader.readLine()) != null) {
                        this.iteratedBytes += line.getBytes(Charset.defaultCharset()).length + 1;
                        addLineToBuffer(line);
                        processor.addLineOracle(innerHolder, innerBuffer, bufferOrder,
                                line.chars().mapToObj(c -> new SqlSplitProcessor.OrderChar((char) c, -1)).collect(Collectors.toList()));
                    }
                    // SqlSplitter is non-reentrant, so we need to create a new one for each loop
                    SqlSplitter splitter = createSplitter();
                    this.sqls = splitter.split(this.buffer.toString()).stream().map(SplitSqlString::getStr)
                            .collect(Collectors.toList());
                    while (this.sqls.size() > 1) {
                        String sql = this.sqls.remove(0);
                        int index = this.buffer.indexOf(sql.substring(0, sql.length() - 1));
                        this.holder.addLast(new SplitSqlString(this.offset + index, sql));
                        this.buffer.delete(0, index + sql.length());
                        this.offset += index + sql.length();
                        clearUselessPrefix();
                        this.delimiter = splitter.getDelimiter();
                    }
                }
                if (!this.holder.isEmpty()) {
                    return this.holder.poll();
                }
                if (this.sqls.isEmpty()) {
                    return null;
                }
                return new SplitSqlString(this.offset, this.sqls.remove(0));
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse input. reason: " + e.getMessage(), e);
            }
        }

        private void addLineToBuffer(String line) {
            if (this.firstLine) {
                this.buffer.append(line);
                this.firstLine = false;
            } else {
                this.buffer.append(LINE_SEPARATOR_CHAR).append(line);
            }
        }

        private void clearUselessPrefix() {
            while (this.buffer.length() > 0 && DELIMITER_CHARACTERS.contains(this.buffer.charAt(0))
                    && !(this.buffer.toString().startsWith(SQL_MULTI_LINE_COMMENT_PREFIX))) {
                this.buffer.deleteCharAt(0);
                this.offset++;
            }
            while ((this.buffer.length() > 0 && (Character.isWhitespace(this.buffer.charAt(0)))) ||
                    (this.buffer.toString().startsWith(SQL_SEPARATOR_CHAR.toString())
                            && !this.buffer.toString().startsWith(SQL_MULTI_LINE_COMMENT_PREFIX))) {
                this.buffer.deleteCharAt(0);
                this.offset++;
            }
        }

        private SqlSplitter createSplitter() {
            return new SqlSplitter(PlSqlLexer.class, this.delimiter, addDelimiter);
        }

    }

}

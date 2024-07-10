
package ai.chat2db.spi.util;

import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.spi.enums.DataTypeEnum;
import ai.chat2db.spi.model.ExecuteResult;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLJoinTableSource;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLTableSource;
import com.alibaba.druid.sql.parser.SQLParserUtils;
import com.google.common.collect.Lists;
import com.oceanbase.tools.sqlparser.oracle.PlSqlLexer;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.procedure.CreateProcedure;
import net.sf.jsqlparser.statement.select.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author jipengfei
 * @version : SqlUtils.java
 */
@Slf4j
public class SqlUtils {

    public static final String DEFAULT_TABLE_NAME = "table1";

    public static void buildCanEditResult(String sql, DbType dbType, ExecuteResult executeResult) {
        try {
            Statement statement;
            if (DbType.sqlserver.equals(dbType)) {
                statement = CCJSqlParserUtil.parse(sql, ccjSqlParser -> ccjSqlParser.withSquareBracketQuotation(true));
            } else {
                statement = CCJSqlParserUtil.parse(sql);
            }
            if (statement instanceof Select) {
                Select select = (Select) statement;
                PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
                if (plainSelect.getJoins() == null && plainSelect.getFromItem() != null) {
                    for (SelectItem item : plainSelect.getSelectItems()) {
                        if (item instanceof SelectExpressionItem) {
                            SelectExpressionItem expressionItem = (SelectExpressionItem) item;
                            if (expressionItem.getAlias() != null) {
                                //canEdit = false; // 找到了一个别名
                                executeResult.setCanEdit(false);
                                return;
                            }
                            if (item instanceof SelectExpressionItem) {
                                SelectExpressionItem selectExpressionItem = (SelectExpressionItem) item;
                                // if the expression is a function
                                if (selectExpressionItem.getExpression() instanceof Function) {
                                    Function function = (Function) selectExpressionItem.getExpression();
                                    // Check if the function is "COUNT"
                                    if ("COUNT".equalsIgnoreCase(function.getName())) {
                                        executeResult.setCanEdit(false);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    executeResult.setCanEdit(true);
                    SQLStatement sqlStatement = SQLUtils.parseSingleStatement(sql, dbType);
                    if ((sqlStatement instanceof SQLSelectStatement sqlSelectStatement)) {
                        SQLExprTableSource sqlExprTableSource = (SQLExprTableSource) getSQLExprTableSource(
                                sqlSelectStatement.getSelect().getFirstQueryBlock().getFrom());
                        executeResult.setTableName(getMetaDataTableName(sqlExprTableSource.getCatalog(), sqlExprTableSource.getSchema(), sqlExprTableSource.getTableName()));
                    }
                } else {
                    executeResult.setCanEdit(false);
                }
            }
        } catch (Exception e) {
            log.error("buildCanEditResult error", e);
            executeResult.setCanEdit(false);
        }
    }

    private static String getMetaDataTableName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> name).collect(Collectors.joining("."));
    }

    public static String formatSQLString(Object para) {
        return para != null ? " '" + para + "' " : null;
    }

    public static String getTableName(String sql, DbType dbType) {
        SQLStatement sqlStatement = SQLUtils.parseSingleStatement(sql, dbType);
        if (!(sqlStatement instanceof SQLSelectStatement sqlSelectStatement)) {
            throw new BusinessException("dataSource.sqlAnalysisError");
        }
        SQLExprTableSource sqlExprTableSource = (SQLExprTableSource) getSQLExprTableSource(
                sqlSelectStatement.getSelect().getFirstQueryBlock().getFrom());
        if (sqlExprTableSource == null) {
            return DEFAULT_TABLE_NAME;
        }
        return sqlExprTableSource.getTableName();
    }

    private static SQLTableSource getSQLExprTableSource(SQLTableSource sqlTableSource) {
        if (sqlTableSource instanceof SQLExprTableSource sqlExprTableSource) {
            return sqlExprTableSource;
        } else if (sqlTableSource instanceof SQLJoinTableSource sqlJoinTableSource) {
            return getSQLExprTableSource(sqlJoinTableSource.getLeft());
        }
        return null;
    }

    private static final String DELIMITER_AFTER_REGEX = "^\\s*(?i)delimiter\\s+(\\S+)";
    private static final String DELIMITER_REGEX = "(?mi)^\\s*delimiter\\s*;?";

    private static final String EVENT_REGEX = "(?i)\\bcreate\\s+event\\b.*?\\bend\\b";

    public static List<String> parse(String sql, DbType dbType) {
        List<String> list = new ArrayList<>();
        try {
            sql = SQLParserUtils.removeComment(sql, dbType);
            if (StringUtils.isBlank(sql)) {
                return list;
            }
            try {
                if (DbType.oracle.equals(dbType)) {
                    SqlSplitter sqlSplitter = new SqlSplitter(PlSqlLexer.class, ";", false);
                    sqlSplitter.setRemoveCommentPrefix(true);
                    List<SplitSqlString> sqls = sqlSplitter.split(sql);
                    return sqls.stream().map(splitSqlString -> SQLParserUtils.removeComment(splitSqlString.getStr(), dbType)).collect(Collectors.toList());
                }
            }catch (Exception e){
                log.error("sqlSplitter error",e);
            }
            try {
                if (DbType.mysql.equals(dbType) ||
                        DbType.mariadb.equals(dbType) ||
                        DbType.oceanbase.equals(dbType)) {
                    sql = updateNow(sql, dbType);
                    SqlSplitProcessor sqlSplitProcessor = new SqlSplitProcessor(dbType, true, true);
                    sqlSplitProcessor.setDelimiter(";");
                    return split(sqlSplitProcessor, sql, dbType);
                }
            }catch (Exception e){
                log.error("sqlSplitProcessor error",e);
            }
//            sql = removeDelimiter(sql);
            if (StringUtils.isBlank(sql)) {
                return list;
            }
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            // Iterate through each statement
            for (Statement stmt : statements.getStatements()) {
                if (!(stmt instanceof CreateProcedure)) {
                    list.add(stmt.toString());
                }
            }
            if (CollectionUtils.isEmpty(list)) {
                list.add(sql);
            }
        } catch (Exception e) {
            try {
                return splitWithCreateEvent(sql, dbType);
            } catch (Exception e1) {
                return SQLParserUtils.splitAndRemoveComment(sql, dbType);
            }
        }
        return list;
    }

    private static String removeDelimiter(String str) {
        try {
            if (str.toUpperCase().contains("DELIMITER")) {
                Pattern pattern = Pattern.compile(DELIMITER_AFTER_REGEX, Pattern.MULTILINE);
                Matcher matcher = pattern.matcher(str);
                while (matcher.find()) {
                    // 获取并打印 "DELIMITER" 后的第一个字符串
                    String mm = matcher.group(1);
                    if (!";".equals(mm)) {
                        str = str.replace(mm, "");
                    }
                }
            }
            return str.replaceAll(DELIMITER_REGEX, "");
        } catch (Exception e) {
            return str;
        }
    }

    private static List<String> splitWithCreateEvent(String str, DbType dbType) {
        List<String> list = new ArrayList<>();
        String sql = SQLParserUtils.removeComment(str, dbType).trim();
        Pattern pattern = Pattern.compile(EVENT_REGEX, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);
        StringBuilder stringBuilder = new StringBuilder();
        int lastEnd = 0; // 用于跟踪上一个匹配的结束位置
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                List<String> l = SQLParserUtils.split(sql.substring(lastEnd, matcher.start()), dbType);
                list.addAll(l);
            }
            list.add(matcher.group());
            lastEnd = matcher.end(); // 更新上一个匹配的结束位置
        }
        if (lastEnd < sql.length()) {
            List<String> l = SQLParserUtils.split(sql.substring(lastEnd), dbType);
            list.addAll(l);
        }
        return list;
    }


    private static String updateNow(String sql, DbType dbType) {
        if (StringUtils.isBlank(sql) || !DbType.mysql.equals(dbType)) {
            return sql;
        }
        if (sql.contains("default now()")) {
            return sql.replace("default now()", "default CURRENT_TIMESTAMP");
        }
        if (sql.contains("DEFAULT now()")) {
            return sql.replace("DEFAULT now()", "default CURRENT_TIMESTAMP");
        }
        if (sql.contains("default now ()")) {
            return sql.replace("default now ()", "default CURRENT_TIMESTAMP");
        }
        if (sql.contains("DEFAULT now ()")) {
            return sql.replace("DEFAULT now ()", "DEFAULT CURRENT_TIMESTAMP");
        }
        return sql;
    }

    private static final String DEFAULT_VALUE = "CHAT2DB_UPDATE_TABLE_DATA_USER_FILLED_DEFAULT";

    public static String getSqlValue(String value, String dataType) {
        if (value == null) {
            return null;
        }
        if("".equals(value)){
            return "''";
        }
        if (DEFAULT_VALUE.equals(value)) {
            return "DEFAULT";
        }
        DataTypeEnum dataTypeEnum = DataTypeEnum.getByCode(dataType);
        return dataTypeEnum.getSqlValue(value);
    }


    public static boolean hasPageLimit(String sql, DbType dbType) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (statement instanceof Select) {
                Select selectStatement = (Select) statement;
                SelectBody selectBody = selectStatement.getSelectBody();
                // Check out common pagination methods
                if (selectBody instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) selectBody;
                    // CHECK LIMIT
                    if (plainSelect.getLimit() != null || plainSelect.getOffset() != null || plainSelect.getTop() != null || plainSelect.getFetch() != null) {
                        return true;
                    }
                    if (DbType.oracle.equals(dbType)) {
                        return sql.contains("ROWNUM") || sql.contains("rownum");
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static List<String> split(SqlSplitProcessor processor, String sql, DbType dbType) {
        StringBuffer buffer = new StringBuffer();
        List<SplitSqlString> sqls = processor.split(buffer, sql);
        String bufferStr = buffer.toString();
        if (bufferStr.trim().length() != 0) {
            // if buffer is not empty, there will be some errors in syntax
            log.info("sql processor's buffer is not empty, there may be some errors. buffer={}", bufferStr);
            String sqlstr = SQLParserUtils.removeComment(sql, dbType);
            return Lists.newArrayList(sqlstr);
        }
        return sqls.stream().map(splitSqlString -> SQLParserUtils.removeComment(splitSqlString.getStr(), dbType)).collect(Collectors.toList());
    }

}
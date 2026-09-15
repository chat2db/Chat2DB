package ai.chat2db.plugin.informix.parser;

import ai.chat2db.plugin.postgresql.parser.PgsqlSqlParser;
import org.antlr.v4.runtime.Token;

import java.util.List;

import static ai.chat2db.plugin.informix.constant.InformixExplainConstants.EXPLAIN_KEYWORD;

public class InformixSqlParser extends PgsqlSqlParser {
    /** Returns the inner statement of an EXPLAIN command, or null for ordinary SQL. */
    public static String extractExplainSql(String sql) {
        List<Token> tokens = new InformixSqlParser().getAllTokensOnDefault(sql).stream()
                .filter(token -> token.getType() != Token.EOF).toList();
        if (tokens.isEmpty() || !EXPLAIN_KEYWORD.equalsIgnoreCase(tokens.get(0).getText())) {
            return null;
        }
        return sql.substring(tokens.get(0).getStopIndex() + 1).trim();
    }
}

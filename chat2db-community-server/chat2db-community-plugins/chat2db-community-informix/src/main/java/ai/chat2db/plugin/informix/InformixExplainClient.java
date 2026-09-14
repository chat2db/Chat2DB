package ai.chat2db.plugin.informix;

import ai.chat2db.plugin.informix.parser.InformixSqlParser;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.JdbcDriverManager;
import ai.chat2db.spi.util.JdbcUtils;
import org.apache.commons.lang3.StringUtils;
import org.antlr.v4.runtime.Token;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Retrieves optimizer information without executing the user's statement. */
public class InformixExplainClient {

    public static String explainSql(String sql) {
        List<Token> tokens = new InformixSqlParser().getAllTokensOnDefault(sql).stream()
                .filter(token -> token.getType() != Token.EOF).toList();
        if (tokens.isEmpty() || !"EXPLAIN".equalsIgnoreCase(tokens.get(0).getText())) {
            return null;
        }
        return sql.substring(tokens.get(0).getStopIndex() + 1).trim();
    }

    String getExplainInfo(Connection connection, String sql) throws SQLException {
        List<Token> tokens = new InformixSqlParser().getAllTokensOnDefault(sql).stream()
                .filter(token -> token.getType() != Token.EOF).toList();
        if (tokens.isEmpty() || !Set.of("SELECT", "WITH", "INSERT", "UPDATE", "DELETE", "MERGE")
                .contains(tokens.get(0).getText().toUpperCase(Locale.ROOT))) {
            throw new SQLException("Informix EXPLAIN requires a SELECT, INSERT, UPDATE, DELETE or MERGE statement");
        }
        for (int i = 0; i < tokens.size() - 1; i++) {
            if (";".equals(tokens.get(i).getText())) {
                throw new SQLException("Informix EXPLAIN requires a single statement");
            }
        }
        Chat2DBContext.guardStatement(sql);
        // Use the existing session for preparation, including its database, temporary
        // tables and transaction. Querying SMI on it would replace the current plan.
        try (Connection observer = openObserver(connection)) {
            int sessionId;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT FIRST 1 DBINFO('sessionid') FROM systables")) {
                if (!result.next()) {
                    throw new SQLException("Informix session ID is unavailable");
                }
                sessionId = result.getInt(1);
            }
            try (PreparedStatement prepared = connection.prepareStatement(sql)) {
                prepared.getMetaData(); // Force server preparation; never call execute().
                return readPlan(observer, sessionId);
            }
        }
    }

    Connection openObserver(Connection source) throws SQLException {
        ConnectInfo info = Chat2DBContext.getConnectInfo();
        // Do not reuse DBManager.getConnection(info), which owns the user's connection.
        return JdbcDriverManager.getConnection(observerUrl(source, info), info.getUser(),
                info.getPassword(), info.getDriverConfig() == null
                        ? Chat2DBContext.getDefaultDriverConfig(info.getDbType()) : info.getDriverConfig(),
                info.getExtendMap());
    }

    static String observerUrl(Connection source, ConnectInfo info) throws SQLException {
        String url = source.getMetaData().getURL();
        if (StringUtils.isNotBlank(url)) {
            return url;
        }
        // Informix JDBC 15 can return null from DatabaseMetaData.getURL().
        url = info.getUrl();
        if (info.getSsh() != null && info.getSsh().isUse()) {
            String localPort = info.getSsh().getLocalPort();
            if (StringUtils.isBlank(localPort)) {
                throw new SQLException("The active Informix SSH forwarding address is unavailable");
            }
            url = JdbcUtils.replaceUrlHostAndPortForSsh(url, info.getHost(),
                    String.valueOf(info.getPort()), localPort);
        }
        return url;
    }

    private String readPlan(Connection observer, int sessionId) throws SQLException {
        try (PreparedStatement statement = observer.prepareStatement(
                "SELECT * FROM sysmaster:syssqexplain WHERE sqx_sessionid = ? "
                        + "AND sqx_iscurrent = 'Y' AND sqx_ismain = 'Y'")) {
            statement.setInt(1, sessionId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Informix did not expose optimizer information for this statement");
                }
                // Older servers expose estimates but do not have sqx_sqlstatementplan.
                for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) {
                    if ("sqx_sqlstatementplan".equalsIgnoreCase(result.getMetaData().getColumnLabel(i))) {
                        String plan = result.getString(i);
                        if (plan != null && !plan.isBlank() && !"PLAN UNAVAILABLE".equals(plan.trim())) {
                            return plan.trim();
                        }
                    }
                }
                return "Estimated Cost: " + result.getString("sqx_estcost")
                        + "\nEstimated Rows: " + result.getString("sqx_estrows")
                        + "\nDetailed plan: unavailable from the server for this statement";
            }
        }
    }
}

package ai.chat2db.plugin.informix;

import ai.chat2db.plugin.informix.parser.InformixSqlParser;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import ai.chat2db.community.domain.api.service.db.ISqlExecutionStatementListener;
import ai.chat2db.community.domain.api.service.db.ISqlExecutionCancellation;
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
        return getExplainInfo(connection, sql, null, null);
    }

    String getExplainInfo(Connection connection, String sql, ISqlExecutionStatementListener listener,
                          ISqlExecutionCancellation cancellation) throws SQLException {
        checkCanceled(cancellation);
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
            int sessionId = withStatement(connection.createStatement(), listener, cancellation, statement -> {
                try (ResultSet result = statement.executeQuery(
                        "SELECT FIRST 1 DBINFO('sessionid') FROM systables")) {
                    if (!result.next()) {
                        throw new SQLException("Informix session ID is unavailable");
                    }
                    return result.getInt(1);
                }
            });
            return withStatement(connection.prepareStatement(sql), listener, cancellation, prepared -> {
                prepared.getMetaData(); // Force server preparation; never call execute().
                checkCanceled(cancellation);
                return readPlan(observer, sessionId, listener, cancellation);
            });
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

    private String readPlan(Connection observer, int sessionId, ISqlExecutionStatementListener listener,
                            ISqlExecutionCancellation cancellation) throws SQLException {
        return withStatement(observer.prepareStatement(
                "SELECT * FROM sysmaster:syssqexplain WHERE sqx_sessionid = ? "
                        + "AND sqx_iscurrent = 'Y' AND sqx_ismain = 'Y'"), listener, cancellation, statement -> {
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
        });
    }

    private static <S extends Statement, T> T withStatement(S statement,
            ISqlExecutionStatementListener listener, ISqlExecutionCancellation cancellation,
            StatementOperation<S, T> operation) throws SQLException {
        try (statement) {
            if (listener != null) {
                listener.onStatementCreated(statement);
            }
            checkCanceled(cancellation);
            T result = operation.run(statement);
            checkCanceled(cancellation);
            return result;
        } finally {
            if (listener != null) {
                listener.onStatementClosed(statement);
            }
        }
    }

    private static void checkCanceled(ISqlExecutionCancellation cancellation) throws SQLException {
        if (cancellation != null && cancellation.isCanceled()) {
            throw new SQLException("SQL execution canceled");
        }
    }

    @FunctionalInterface
    private interface StatementOperation<S extends Statement, T> {
        T run(S statement) throws SQLException;
    }
}

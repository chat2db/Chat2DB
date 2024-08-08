package ai.chat2db.plugin.kingbase;

import java.sql.Connection;
import java.sql.SQLException;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.model.Function;
import ai.chat2db.spi.model.Procedure;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.sql.SQLExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class KingBaseDBManage extends DefaultDBManage implements DBManage {

    @Override
    public void updateProcedure(Connection connection, String databaseName, String schemaName, Procedure procedure) throws SQLException {
        try {
            connection.setAutoCommit(false);
            String procedureBody = procedure.getProcedureBody();
            String parameterSignature = extractParameterSignature(procedureBody);

            if (procedureBody == null || !procedureBody.trim().toUpperCase().startsWith("CREATE")) {
                throw new IllegalArgumentException("No CREATE statement found.");
            }

            String procedureNewName = getSchemaOrProcedureName(procedureBody, schemaName, procedure);
            if (!procedureNewName.equals(procedure.getProcedureName())) {
                procedureBody = procedureBody.replace(procedure.getProcedureName(), procedureNewName);
            }
            String dropSql = "DROP PROCEDURE IF EXISTS " + procedureNewName + parameterSignature;
            SQLExecutor.getInstance().execute(connection, dropSql, resultSet -> {
            });
            SQLExecutor.getInstance().execute(connection, procedureBody, resultSet -> {
            });
        } catch (Exception e) {
            connection.rollback();
            throw new RuntimeException(e);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public void connectDatabase(Connection connection, String database) {
        try {
            ConnectInfo connectInfo = Chat2DBContext.getConnectInfo();
            if (!StringUtils.isEmpty(connectInfo.getSchemaName())) {
                SQLExecutor.getInstance().execute(connection, "SET search_path TO \"" + connectInfo.getSchemaName() + "\"");
            }
        } catch (Exception e) {
            log.error("connectDatabase error", e);
        }
    }

    @Override
    public Connection getConnection(ConnectInfo connectInfo) {
        String url = connectInfo.getUrl();
        String database = connectInfo.getDatabaseName();
        if (database != null && !database.isEmpty()) {
            url = replaceDatabaseInJdbcUrl(url, database);
        }
        connectInfo.setUrl(url);

        return super.getConnection(connectInfo);
    }


    public String replaceDatabaseInJdbcUrl(String url, String newDatabase) {
        // First split the string at the "?" character and process the query parameters
        String[] urlAndParams = url.split("\\?");
        String urlWithoutParams = urlAndParams[0];

        // Split string at "/" character in URL
        String[] parts = urlWithoutParams.split("/");

        // Take the last part, the database name, and replace it with the new database name
        parts[parts.length - 1] = newDatabase;

        // Reassemble the modified parts into a URL
        String newUrlWithoutParams = String.join("/", parts);

        // If query parameters exist, add them again
        String newUrl = urlAndParams.length > 1 ? newUrlWithoutParams + "?" + urlAndParams[1] : newUrlWithoutParams;

        return newUrl;
    }


    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "drop table if exists " + tableName;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
    }

    @Override
    public void deleteProcedure(Connection connection, String databaseName, String schemaName, Procedure procedure) {
        String procedureBody = procedure.getProcedureBody();
        String parameterSignature = extractParameterSignature(procedureBody);
        String procedureNewName = getSchemaOrProcedureName(procedure.getProcedureBody(), schemaName, procedure);
        String sql = "DROP PROCEDURE " + procedureNewName + parameterSignature;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> {});
    }

    @Override
    public void deleteFunction(Connection connection, String databaseName, String schemaName, Function function) {
        String functionBody = function.getFunctionBody();
        String parameterSignature = extractParameterSignature(functionBody);
        String functionNewName = getSchemaOrFunctionName(functionBody, schemaName, function);
        String sql = "DROP FUNCTION" + functionNewName + parameterSignature;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> {});
    }

    @Override
    public void copyTable(Connection connection, String databaseName, String schemaName, String tableName, String newTableName, boolean copyData) throws SQLException {
        String sql = "";
        if (copyData) {
            sql = "CREATE TABLE " + newTableName + " AS TABLE " + tableName + " WITH DATA";
        } else {
            sql = "CREATE TABLE " + newTableName + " AS TABLE " + tableName + " WITH NO DATA";
        }
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
    }

    private String extractParameterSignature(String input) {
        int depth = 0, start = -1;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '(') {
                if (depth++ == 0) start = i;
            } else if (c == ')' && --depth == 0 && start != -1) {
                return "(" + input.substring(start + 1, i) + ")";
            }
        }
        if (depth == 0) {
            return "";
        }
        return null;
    }

    private static String getSchemaOrProcedureName(String procedureBody, String schemaName, Procedure procedure) {
        if (procedureBody.toLowerCase().contains(schemaName.toLowerCase())) {
            return procedure.getProcedureName();
        } else {
            return schemaName + "." + procedure.getProcedureName();
        }
    }

    private static String getSchemaOrFunctionName(String functionBody, String schemaName, Function function) {
        if (functionBody.toLowerCase().contains(schemaName.toLowerCase())) {
            return function.getFunctionName();
        } else {
            return schemaName + "." + function.getFunctionName();
        }
    }
}

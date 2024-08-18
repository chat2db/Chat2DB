package ai.chat2db.plugin.postgresql;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.model.AsyncContext;
import ai.chat2db.spi.model.Function;
import ai.chat2db.spi.model.Procedure;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.sql.SQLExecutor;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ai.chat2db.plugin.postgresql.consts.SQLConst.ENUM_TYPE_DDL_SQL;

public class PostgreSQLDBManage extends DefaultDBManage implements DBManage {

    public void exportDatabase(Connection connection, String databaseName, String schemaName, AsyncContext asyncContext) throws SQLException {
        exportTypes(connection, asyncContext);
        exportTables(connection, databaseName, schemaName, asyncContext);
        exportViews(connection, schemaName, asyncContext);
        exportFunctions(connection, schemaName, asyncContext);
        exportTriggers(connection, asyncContext);
    }

    private void exportTypes(Connection connection, AsyncContext asyncContext) throws SQLException {
        try (ResultSet resultSet = connection.createStatement().executeQuery(ENUM_TYPE_DDL_SQL)) {
            while (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append(resultSet.getString("ddl")).append("\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }

    private void exportTables(Connection connection, String databaseName, String schemaName, AsyncContext asyncContext) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(databaseName, schemaName, null,
                new String[]{"TABLE", "SYSTEM TABLE", "PARTITIONED TABLE"})) {
            ArrayList<String> tableNames = new ArrayList<>();
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                tableNames.add(tableName);
            }
            for (String tableName : tableNames) {
                exportTable(connection, databaseName, schemaName, tableName, asyncContext);
            }

        }
    }

    public void exportTable(Connection connection, String databaseName, String schemaName, String tableName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("select pg_get_tabledef('%s','%s',true,'COMMENTS') as ddl;", schemaName, tableName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            if (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("\n").append("DROP TABLE IF EXISTS ").append(tableName).append(";").append("\n")
                        .append(resultSet.getString("ddl")).append("\n");
                asyncContext.write(sqlBuilder.toString());
                if (asyncContext.isContainsData()) {
                    exportTableData(connection, databaseName, schemaName, tableName, asyncContext);
                }
            }
        }
    }


    private void exportViews(Connection connection, String schemaName, AsyncContext asyncContext) throws SQLException {

        String sql = String.format("SELECT table_name, view_definition FROM information_schema.views WHERE table_schema = '%s'", schemaName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                String viewName = resultSet.getString("table_name");
                String viewDefinition = resultSet.getString("view_definition");
                sqlBuilder.append("CREATE OR REPLACE VIEW ").append(viewName).append(" AS ").append(viewDefinition).append("\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }

    private void exportFunctions(Connection connection, String schemaName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("SELECT proname, pg_get_functiondef(oid) AS function_definition FROM pg_proc " +
                "WHERE pronamespace = (SELECT oid FROM pg_namespace WHERE nspname = '%s')", schemaName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                String functionName = resultSet.getString("proname");
                String functionDefinition = resultSet.getString("function_definition");
                sqlBuilder.append("DROP FUNCTION IF EXISTS ").append(schemaName).append(".").append(functionName).append(";\n");
                sqlBuilder.append(functionDefinition).append(";\n\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }

    private void exportTriggers(Connection connection, AsyncContext asyncContext) throws SQLException {
        String sql = "SELECT pg_get_triggerdef(oid) AS trigger_definition FROM pg_trigger";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append(resultSet.getString("trigger_definition")).append(";").append("\n");
                asyncContext.write(sqlBuilder.toString());
            }
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

        }
    }

    @Override
    public void updateProcedure(Connection connection, String databaseName, String schemaName, Procedure procedure) throws SQLException {
        try {
            connection.setAutoCommit(false);
            String procedureBody = procedure.getProcedureBody();
            boolean isCreateOrReplace = procedureBody.trim().toUpperCase().startsWith("CREATE OR REPLACE ");
            String parameterSignature = extractParameterSignature(procedureBody);

            if (procedureBody == null || !procedureBody.trim().toUpperCase().startsWith("CREATE")) {
                throw new IllegalArgumentException("No CREATE statement found.");
            }

            String procedureNewName = getSchemaOrProcedureName(procedureBody, schemaName, procedure);
            if (!procedureNewName.equals(procedure.getProcedureName())) {
                procedureBody = procedureBody.replace(procedure.getProcedureName(), procedureNewName);
            }
            String dropSql = "DROP PROCEDURE IF EXISTS " + procedureNewName + parameterSignature;
            SQLExecutor.getInstance().execute(connection, dropSql, resultSet -> {});
            SQLExecutor.getInstance().execute(connection, procedureBody, resultSet -> {});
        } catch (Exception e) {
            connection.rollback();
            throw new RuntimeException(e);
        } finally {
            connection.setAutoCommit(true);
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
        String sql = "DROP TABLE " + tableName;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
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

    @Override
    public void deleteProcedure(Connection connection, String databaseName, String schemaName, Procedure procedure) {
        String procedureBody = procedure.getProcedureBody();
        String parameterSignature = extractParameterSignature(procedureBody);
        String procedureNewName = getSchemaOrProcedureName(procedureBody, schemaName, procedure);
        String sql = "DROP PROCEDURE " + procedureNewName + parameterSignature;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> {});
    }

    @Override
    public void deleteFunction(Connection connection, String databaseName, String schemaName, Function function) {
        String functionBody = function.getFunctionBody();
        String parameterSignature = extractParameterSignature(functionBody);
        String functionNewName = getSchemaOrFunctionName(functionBody, schemaName, function);
        String sql = "DROP FUNCTION" + functionNewName + parameterSignature;
        SQLExecutor.getInstance().execute(connection,sql,resultSet -> {});
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

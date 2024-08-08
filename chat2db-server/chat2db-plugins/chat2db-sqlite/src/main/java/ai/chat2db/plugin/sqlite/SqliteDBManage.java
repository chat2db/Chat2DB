package ai.chat2db.plugin.sqlite;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.model.AsyncContext;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SqliteDBManage extends DefaultDBManage implements DBManage {

    @Override
    public void exportDatabase(Connection connection, String databaseName, String schemaName, AsyncContext asyncContext) throws SQLException {
        exportTables(connection, databaseName, schemaName,asyncContext);
        exportViews(connection, databaseName, asyncContext);
        exportTriggers(connection, asyncContext);
    }

    private void exportTables(Connection connection, String databaseName, String schemaName, AsyncContext asyncContext) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(databaseName, null, null, new String[]{"TABLE", "SYSTEM TABLE"})) {
            while (resultSet.next()) {
                exportTable(connection, databaseName,schemaName, resultSet.getString("TABLE_NAME"), asyncContext);
            }
        }
    }


    public void exportTable(Connection connection, String databaseName, String schemaName, String tableName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("SELECT sql FROM sqlite_master WHERE type='table' AND name='%s'", tableName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            if (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("DROP TABLE IF EXISTS ").append(format(tableName)).append(";").append("\n")
                        .append(resultSet.getString("sql")).append(";").append("\n");
                asyncContext.write(sqlBuilder.toString());
                if (asyncContext.isContainsData()) {
                    exportTableData(connection, databaseName,schemaName, tableName, asyncContext);
                }
            }
        }
    }

    private String format(String tableName) {
        return "\""+tableName+"\"";
    }

    private void exportViews(Connection connection, String databaseName, AsyncContext asyncContext) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(databaseName, null, null, new String[]{"VIEW"})) {
            while (resultSet.next()) {
                exportView(connection, resultSet.getString("TABLE_NAME"), asyncContext);
            }
        }
    }

    private void exportView(Connection connection, String viewName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("SELECT * FROM sqlite_master WHERE type = 'view' and name='%s';", viewName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            if (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("DROP VIEW IF EXISTS ").append(format(viewName)).append(";").append("\n")
                        .append(resultSet.getString("sql")).append(";").append("\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }

    private void exportTriggers(Connection connection, AsyncContext asyncContext) throws SQLException {
        String sql = "SELECT * FROM sqlite_master WHERE type = 'trigger';";
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                String triggerName = resultSet.getString("name");
                exportTrigger(connection, triggerName, asyncContext);
            }
        }
    }

    private void exportTrigger(Connection connection, String triggerName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("SELECT * FROM sqlite_master WHERE type = 'trigger' and name='%s';", triggerName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            if (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append(resultSet.getString("sql")).append("\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }
}

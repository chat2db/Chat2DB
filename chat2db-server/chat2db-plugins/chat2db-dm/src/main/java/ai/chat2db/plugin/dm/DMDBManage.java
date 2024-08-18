package ai.chat2db.plugin.dm;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.model.AsyncContext;
import ai.chat2db.spi.model.Procedure;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.sql.SQLExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
public class DMDBManage extends DefaultDBManage implements DBManage {
    private String format(String tableName) {
        return "\"" + tableName + "\"";
    }

    private static String ROUTINES_SQL
            = "SELECT OWNER, NAME, TEXT FROM ALL_SOURCE WHERE TYPE = '%s' AND OWNER = '%s' AND NAME = '%s' ORDER BY LINE";
    private static String TRIGGER_SQL_LIST = "SELECT OWNER, TRIGGER_NAME FROM ALL_TRIGGERS WHERE OWNER = '%s'";

    private static String TRIGGER_SQL
            = "SELECT OWNER, TRIGGER_NAME, TABLE_OWNER, TABLE_NAME, TRIGGERING_TYPE, TRIGGERING_EVENT, STATUS, TRIGGER_BODY "
            + "FROM ALL_TRIGGERS WHERE OWNER = '%s' AND TRIGGER_NAME = '%s'";

    private static String PROCEDURE_SQL = "SELECT COUNT(*)\n" +
            "FROM DBA_OBJECTS\n" +
            "WHERE OBJECT_TYPE = 'PROCEDURE' \n" +
            "AND OWNER = '%s' \n" +
            "AND OBJECT_NAME = '%s'";

    @Override
    public void exportDatabase(Connection connection, String databaseName, String schemaName, AsyncContext asyncContext) throws SQLException {
        exportTables(connection, databaseName, schemaName, asyncContext);
        exportViews(connection, schemaName, asyncContext);
        exportProcedures(connection, schemaName, asyncContext);
        exportTriggers(connection, schemaName, asyncContext);
    }

    private void exportTables(Connection connection, String databaseName, String schemaName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("SELECT TABLE_NAME FROM ALL_TABLES where OWNER='%s' and TABLESPACE_NAME='MAIN'", schemaName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                exportTable(connection, databaseName, tableName, schemaName, asyncContext);
            }
        }
    }


    public void exportTable(Connection connection, String databaseName, String tableName, String schemaName, AsyncContext asyncContext) throws SQLException {
        String sql = """
                SELECT
                    (SELECT comments FROM user_tab_comments WHERE table_name = '%s') AS comments,
                    (SELECT dbms_metadata.get_ddl('TABLE', '%s', '%s') FROM dual) AS ddl
                FROM dual;
                """;
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(String.format(sql, tableName, tableName, schemaName))) {
            String formatSchemaName = format(schemaName);
            String formatTableName = format(tableName);
            if (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("DROP TABLE IF EXISTS ").append(formatSchemaName).append(".").append(formatTableName)
                        .append(";").append("\n")
                        .append(resultSet.getString("ddl")).append("\n");
                String comment = resultSet.getString("comments");
                if (StringUtils.isNotBlank(comment)) {
                    sqlBuilder.append("COMMENT ON TABLE ").append(formatSchemaName).append(".").append(formatTableName)
                            .append(" IS ").append("'").append(comment).append("';");
                }
                asyncContext.write(sqlBuilder.toString());
                exportTableColumnComment(connection, schemaName, tableName, asyncContext);
            }
            if (asyncContext.isContainsData()) {
                exportTableData(connection, databaseName, schemaName, tableName, asyncContext);
            }
        }
    }

    private void exportTableColumnComment(Connection connection, String schemaName, String tableName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("select COLNAME,COMMENT$ from SYS.SYSCOLUMNCOMMENTS\n" +
                "where SCHNAME = '%s' and TVNAME = '%s'and TABLE_TYPE = 'TABLE';", schemaName, tableName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                String columnName = resultSet.getString("COLNAME");
                String comment = resultSet.getString("COMMENT$");
                sqlBuilder.append("COMMENT ON COLUMN ").append(format(schemaName)).append(".").append(format(tableName))
                        .append(".").append(format(columnName)).append(" IS ").append("'").append(comment).append("';").append("\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }


    private void exportViews(Connection connection, String schemaName, AsyncContext asyncContext) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, schemaName, null, new String[]{"VIEW"})) {
            while (resultSet.next()) {
                String viewName = resultSet.getString("TABLE_NAME");
                exportView(connection, viewName, schemaName, asyncContext);
            }
        }
    }

    private void exportView(Connection connection, String viewName, String schemaName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("SELECT DBMS_METADATA.GET_DDL('VIEW','%s','%s') as ddl FROM DUAL;", viewName, schemaName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            if (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append(resultSet.getString("ddl")).append("\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }

    private void exportProcedures(Connection connection, String schemaName, AsyncContext asyncContext) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getProcedures(null, schemaName, null)) {
            while (resultSet.next()) {
                String procedureName = resultSet.getString("PROCEDURE_NAME");
                exportProcedure(connection, schemaName, procedureName, asyncContext);
            }
        }
    }

    private void exportProcedure(Connection connection, String schemaName, String procedureName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format(ROUTINES_SQL, "PROC", schemaName, procedureName);
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append(resultSet.getString("TEXT")).append("\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }

    private void exportTriggers(Connection connection, String schemaName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format(TRIGGER_SQL_LIST, schemaName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                String triggerName = resultSet.getString("TRIGGER_NAME");
                exportTrigger(connection, schemaName, triggerName, asyncContext);
            }
        }
    }

    private void exportTrigger(Connection connection, String schemaName, String triggerName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format(TRIGGER_SQL, schemaName, triggerName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            if (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append(resultSet.getString("TRIGGER_BODY")).append("\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }

    @Override
    public void updateProcedure(Connection connection, String databaseName, String schemaName, Procedure procedure) throws SQLException {
        try {
            connection.setAutoCommit(false);
            String procedureBody = procedure.getProcedureBody();
            boolean isCreateOrReplace = procedureBody.trim().toUpperCase().startsWith("CREATE OR REPLACE ");

            if (procedureBody == null || !procedureBody.trim().toUpperCase().startsWith("CREATE")) {
                throw new IllegalArgumentException("No CREATE statement found.");
            }

            String procedureNewName = getSchemaOrProcedureName(procedureBody, schemaName, procedure);
            if (!procedureNewName.equals(procedure.getProcedureName())) {
                procedureBody = procedureBody.replace(procedure.getProcedureName(), procedureNewName);
            }
            String checkProcedureSQL = String.format(PROCEDURE_SQL, schemaName.toUpperCase(),procedure.getProcedureName().toUpperCase());
            String finalProcedureBody = procedureBody;
            SQLExecutor.getInstance().execute(connection, checkProcedureSQL, resultSet -> {
                if (resultSet.next()) {
                    int count = resultSet.getInt(1);
                    if (count >= 1) {
                        if (isCreateOrReplace) {
                            SQLExecutor.getInstance().execute(connection, finalProcedureBody, resultSet2 -> {});
                        } else {
                            throw new SQLException("Procedure with the same name already exists.");
                        }
                    }
                }
            });
            SQLExecutor.getInstance().execute(connection, finalProcedureBody, resultSet -> {});
        } catch (Exception e) {
            connection.rollback();
            throw new RuntimeException(e);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public void connectDatabase(Connection connection, String database) {
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo();
        if (ObjectUtils.anyNull(connectInfo) || StringUtils.isEmpty(connectInfo.getSchemaName())) {
            return;
        }
        String schemaName = connectInfo.getSchemaName();
        try {
            SQLExecutor.getInstance().execute(connection, "SET SCHEMA \"" + schemaName + "\"");
        } catch (SQLException e) {
            log.error("connectDatabase error", e);
        }
    }

    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "DROP TABLE IF EXISTS " + tableName;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
    }

    private static String getSchemaOrProcedureName(String procedureBody, String schemaName, Procedure procedure) {
        if (procedureBody.toLowerCase().contains(schemaName.toLowerCase())) {
            return procedure.getProcedureName();
        } else {
            return schemaName + "." + procedure.getProcedureName();
        }
    }
}

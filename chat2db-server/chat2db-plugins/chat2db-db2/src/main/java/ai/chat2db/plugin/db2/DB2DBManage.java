package ai.chat2db.plugin.db2;

import ai.chat2db.plugin.db2.constant.SQLConstant;
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

@Slf4j
public class DB2DBManage extends DefaultDBManage implements DBManage {

    private static String PROCEDURE_SQL = "SELECT COUNT(*) AS procedure_count\n" +
            "FROM SYSCAT.PROCEDURES\n" +
            "WHERE PROCNAME = '%s';";

    @Override
    public void exportDatabase(Connection connection, String databaseName, String schemaName, AsyncContext asyncContext) throws SQLException {
        exportTables(connection, databaseName, schemaName, asyncContext);
        exportViews(connection, schemaName, asyncContext);
        exportProceduresAndFunctions(connection, schemaName, asyncContext);
        exportTriggers(connection, schemaName, asyncContext);
    }

    private void exportTables(Connection connection, String databaseName, String schemaName, AsyncContext asyncContext) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, schemaName, null, new String[]{"TABLE", "SYSTEM TABLE"})) {
            while (resultSet.next()) {
                exportTable(connection, databaseName, schemaName, resultSet.getString("TABLE_NAME"), asyncContext);
            }
        }
    }


    public void exportTable(Connection connection, String databaseName, String schemaName, String tableName, AsyncContext asyncContext) throws SQLException {
        try {
            SQLExecutor.getInstance().execute(connection, SQLConstant.TABLE_DDL_FUNCTION_SQL, resultSet -> null);
        } catch (Exception e) {
            //log.error("Failed to create function", e);
        }
        String sql = String.format("select %s.GENERATE_TABLE_DDL('%s', '%s') as sql from %s;", schemaName, schemaName, tableName, tableName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            if (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append(resultSet.getString("sql")).append("\n");
                asyncContext.write(sqlBuilder.toString());
                if (asyncContext.isContainsData()) {
                    exportTableData(connection, databaseName, schemaName, tableName, asyncContext);
                }
            }
        }
    }


    private void exportViews(Connection connection, String schemaName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("select TEXT from syscat.views where VIEWSCHEMA='%s';", schemaName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                String ddl = resultSet.getString("TEXT");
                sqlBuilder.append(ddl).append(";").append("\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }

    private void exportProceduresAndFunctions(Connection connection, String schemaName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("select TEXT from syscat.routines where ROUTINESCHEMA='%s';", schemaName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                String ddl = resultSet.getString("TEXT");
                sqlBuilder.append(ddl).append(";").append("\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }


    private void exportTriggers(Connection connection, String schemaName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("select * from SYSCAT.TRIGGERS where TRIGSCHEMA = '%s';", schemaName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                String ddl = resultSet.getString("TEXT");
                sqlBuilder.append(ddl).append(";").append("\n");
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
            String checkProcedureSQL = String.format(PROCEDURE_SQL, procedure.getProcedureName().toUpperCase());
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

        }
    }

    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "DROP TABLE " + tableName;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
    }

    @Override
    public void copyTable(Connection connection, String databaseName, String schemaName, String tableName, String newTableName, boolean copyData) throws SQLException {
        String sql = "CREATE TABLE " + newTableName + " LIKE " + tableName + " INCLUDING INDEXES";
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
        if (copyData) {
            sql = "INSERT INTO " + newTableName + " SELECT * FROM " + tableName;
            SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
        }
    }

    private static String getSchemaOrProcedureName(String procedureBody, String schemaName, Procedure procedure) {
        if (procedureBody.toLowerCase().contains(schemaName.toLowerCase())) {
            return procedure.getProcedureName();
        } else {
            return schemaName + "." + procedure.getProcedureName();
        }
    }
}

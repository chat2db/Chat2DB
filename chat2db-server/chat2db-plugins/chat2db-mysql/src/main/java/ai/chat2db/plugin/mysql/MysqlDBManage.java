package ai.chat2db.plugin.mysql;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.model.AsyncContext;
import ai.chat2db.spi.model.Function;
import ai.chat2db.spi.model.Procedure;
import ai.chat2db.spi.sql.SQLExecutor;
import cn.hutool.core.date.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import static cn.hutool.core.date.DatePattern.NORM_DATETIME_PATTERN;

@Slf4j
public class MysqlDBManage extends DefaultDBManage implements DBManage {

    private static String PROCEDURE_SQL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.ROUTINES " +
            "WHERE ROUTINE_SCHEMA = '%s' AND ROUTINE_NAME = '%s' AND ROUTINE_TYPE = 'PROCEDURE'";

    @Override
    public void exportDatabase(Connection connection, String databaseName, String schemaName, AsyncContext asyncContext) throws SQLException {
        asyncContext.write(String.format(EXPORT_TITLE, DateUtil.format(new Date(), NORM_DATETIME_PATTERN)));
        exportTables(connection, databaseName, schemaName, asyncContext);
        asyncContext.setProgress(50);
        exportViews(connection, databaseName, asyncContext);
        asyncContext.setProgress(60);
        exportProcedures(connection, asyncContext);
        asyncContext.setProgress(70);
        exportTriggers(connection, asyncContext);
        asyncContext.setProgress(90);
        exportFunctions(connection, databaseName, asyncContext);
        asyncContext.finish();
    }

    private void exportFunctions(Connection connection, String databaseName, AsyncContext asyncContext) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getFunctions(databaseName, null, null)) {
            while (resultSet.next()) {
                exportFunction(connection, resultSet.getString("FUNCTION_NAME"), asyncContext);
            }

        }
    }

    private void exportFunction(Connection connection, String functionName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("SHOW CREATE FUNCTION %s;", functionName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            if (resultSet.next()) {
                asyncContext.write(String.format(FUNCTION_TITLE, functionName));
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("DROP FUNCTION IF EXISTS ").append(functionName).append(";").append("\n");

                sqlBuilder.append("delimiter ;;").append("\n").append(resultSet.getString("Create Function")).append(";;")
                        .append("\n").append("delimiter ;").append("\n\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }

    private void exportTables(Connection connection, String databaseName, String schemaName, AsyncContext asyncContext) throws SQLException {
        asyncContext.write("SET FOREIGN_KEY_CHECKS=0;");
        try (ResultSet resultSet = connection.getMetaData().getTables(databaseName, null, null, new String[]{"TABLE", "SYSTEM TABLE"})) {
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                exportTable(connection, databaseName, schemaName, tableName, asyncContext);
            }
        }
        asyncContext.write("SET FOREIGN_KEY_CHECKS=1;");
    }


    public void exportTable(Connection connection, String databaseName, String schemaName, String tableName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("show create table %s ", tableName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            if (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                asyncContext.write(String.format(TABLE_TITLE, tableName));
                sqlBuilder.append("DROP TABLE IF EXISTS ").append(format(tableName)).append(";").append("\n")
                        .append(resultSet.getString("Create Table")).append(";").append("\n");
                asyncContext.write(sqlBuilder.toString());
                if (asyncContext.isContainsData()) {
                    exportTableData(connection, databaseName, schemaName, tableName, asyncContext);
                }
            }
        } catch (Exception e) {
            log.error("export table error", e);
            asyncContext.error(String.format("export table %s error:%s", tableName, e.getMessage()));
        }
    }


    private void exportViews(Connection connection, String databaseName, AsyncContext asyncContext) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(databaseName, null, null, new String[]{"VIEW"})) {
            while (resultSet.next()) {
                exportView(connection, resultSet.getString("TABLE_NAME"), asyncContext);
            }
        }
    }

    private void exportView(Connection connection, String viewName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("show create view %s ", viewName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            if (resultSet.next()) {
                asyncContext.write(String.format(VIEW_TITLE, viewName));
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("DROP VIEW IF EXISTS ").append(format(viewName)).append(";").append("\n")
                        .append(resultSet.getString("Create View")).append(";").append("\n\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }

    private void exportProcedures(Connection connection, AsyncContext asyncContext) throws SQLException {
        String sql = "SHOW PROCEDURE STATUS WHERE Db = DATABASE()";
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                exportProcedure(connection, resultSet.getString("Name"), asyncContext);
            }
        }
    }

    private void exportProcedure(Connection connection, String procedureName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("show create procedure %s ", procedureName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            if (resultSet.next()) {
                asyncContext.write(String.format(PROCEDURE_TITLE, procedureName));
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("DROP PROCEDURE IF EXISTS ").append(format(procedureName)).append(";").append("\n")
                        .append("delimiter ;;").append("\n").append(resultSet.getString("Create Procedure")).append(";;")
                        .append("\n").append("delimiter ;").append("\n\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }

    private void exportTriggers(Connection connection, AsyncContext asyncContext) throws SQLException {
        String sql = "SHOW TRIGGERS";
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                String triggerName = resultSet.getString("Trigger");
                exportTrigger(connection, triggerName, asyncContext);
            }
        }
    }

    private void exportTrigger(Connection connection, String triggerName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("show create trigger %s ", triggerName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            if (resultSet.next()) {
                asyncContext.write(String.format(TRIGGER_TITLE, triggerName));
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("DROP TRIGGER IF EXISTS ").append(format(triggerName)).append(";").append("\n")
                        .append("delimiter ;;").append("\n").append(resultSet.getString("SQL Original Statement")).append(";;")
                        .append("\n").append("delimiter ;").append("\n\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }

    @Override
    public void updateProcedure(Connection connection, String databaseName, String schemaName, Procedure procedure) throws SQLException {
        try {
            connection.setAutoCommit(false);
            String procedureBody = procedure.getProcedureBody();
            if (procedureBody == null || !procedureBody.trim().toUpperCase().startsWith("CREATE")) {
                throw new IllegalArgumentException("No CREATE statement found.");
            }

            String procedureNewName = getSchemaOrProcedureName(procedureBody, databaseName, procedure);
            if (!procedureNewName.equals(procedure.getProcedureName())) {
                procedureBody = procedureBody.replace(procedure.getProcedureName(), procedureNewName);
            }
            String checkProcedureSQL = String.format(PROCEDURE_SQL, databaseName, procedure.getProcedureName());
            SQLExecutor.getInstance().execute(connection, checkProcedureSQL, resultSet -> {
                try {
                    if (resultSet.next()) {
                        int count = resultSet.getInt(1);
                        if (count >= 1) {
                            throw new Exception("Procedure already exists");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            SQLExecutor.getInstance().execute(connection, procedureBody, resultSet -> {});
        } catch (Exception e) {
            connection.rollback();
            throw new RuntimeException(e);
        } finally {
            connection.setAutoCommit(true);
        }

    }

    @Override
    public void connectDatabase(Connection connection, String database) {
        if (StringUtils.isEmpty(database)) {
            return;
        }
        try {
            SQLExecutor.getInstance().execute(connection, "use `" + database + "`;");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "DROP TABLE " + format(tableName);
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
    }

    @Override
    public void deleteProcedure(Connection connection, String databaseName, String schemaName, Procedure procedure) {
        String procedureNewName = getSchemaOrProcedureName(procedure.getProcedureBody(), databaseName, procedure);
        String sql = "DROP PROCEDURE " + procedureNewName;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
    }

    @Override
    public void deleteFunction(Connection connection, String databaseName, String schemaName, Function function) {
        String functionNewName = getSchemaOrFunctionName(function.getFunctionBody(), databaseName, function);
        String sql = "DROP FUNCTION " + functionNewName;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
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

    public static String format(String tableName) {
        return "`" + tableName + "`";
    }
}

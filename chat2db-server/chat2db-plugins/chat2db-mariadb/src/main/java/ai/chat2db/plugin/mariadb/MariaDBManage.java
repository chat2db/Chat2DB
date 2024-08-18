package ai.chat2db.plugin.mariadb;

import java.sql.Connection;
import java.sql.SQLException;

import ai.chat2db.plugin.mysql.MysqlDBManage;
import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.model.Function;
import ai.chat2db.spi.model.Procedure;
import ai.chat2db.spi.sql.SQLExecutor;

public class MariaDBManage extends MysqlDBManage implements DBManage {

    String PROCEDURE_SQL = "SELECT COUNT(*)\n" +
            "FROM information_schema.ROUTINES\n" +
            "WHERE ROUTINE_TYPE = 'PROCEDURE'\n" +
            "AND ROUTINE_NAME = '%s'\n" +
            "AND ROUTINE_SCHEMA = '%s'";

    @Override
    public void updateProcedure(Connection connection, String databaseName, String schemaName, Procedure procedure) throws SQLException {
        try {
            connection.setAutoCommit(false);
            String procedureBody = procedure.getProcedureBody();
            boolean isCreateOrReplace = procedureBody.trim().toUpperCase().startsWith("CREATE OR REPLACE ");

            if (procedureBody == null || !procedureBody.trim().toUpperCase().startsWith("CREATE")) {
                throw new IllegalArgumentException("No CREATE statement found.");
            }

            String procedureNewName = getSchemaOrProcedureName(procedureBody, databaseName, procedure);
            if (!procedureNewName.equals(procedure.getProcedureName())) {
                procedureBody = procedureBody.replace(procedure.getProcedureName(), procedureNewName);
            }
            String checkProcedureSQL = String.format(PROCEDURE_SQL, procedure.getProcedureName().toUpperCase(),schemaName.toUpperCase());
            String finalProcedureBody = procedureBody;
            SQLExecutor.getInstance().execute(connection, checkProcedureSQL, resultSet -> {
                if (resultSet.next()) {
                    int count = resultSet.getInt(1);
                    if (count >= 1) {
                        if (isCreateOrReplace) {
                            SQLExecutor.getInstance().execute(connection, finalProcedureBody, resultSet2 -> {
                            });
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

}

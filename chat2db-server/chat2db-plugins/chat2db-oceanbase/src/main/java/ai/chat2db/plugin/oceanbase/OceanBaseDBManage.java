package ai.chat2db.plugin.oceanbase;

import ai.chat2db.plugin.mysql.MysqlDBManage;
import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.model.Procedure;
import ai.chat2db.spi.sql.SQLExecutor;

import java.sql.Connection;
import java.sql.SQLException;

public class OceanBaseDBManage extends MysqlDBManage implements DBManage {

    private static String PROCEDURE_SQL = "SELECT COUNT(*) FROM information_schema.routines " +
            "WHERE routine_type='PROCEDURE' AND routine_schema='%s' AND routine_name='%s';";


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
            String checkProcedureSQL = String.format(PROCEDURE_SQL, schemaName.toUpperCase(), procedure.getProcedureName().toUpperCase());
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

    private static String getSchemaOrProcedureName(String procedureBody, String schemaName, Procedure procedure) {
        if (procedureBody.toLowerCase().contains(schemaName.toLowerCase())) {
            return procedure.getProcedureName();
        } else {
            return schemaName + "." + procedure.getProcedureName();
        }
    }

}

package ai.chat2db.plugin.db2;

import ai.chat2db.plugin.db2.constant.SQLConstant;
import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
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

    @Override
    public String exportDatabase(Connection connection, String databaseName, String schemaName, boolean containData) throws SQLException {
        StringBuilder sqlBuilder = new StringBuilder();
        exportTables(connection, schemaName, sqlBuilder, containData);
        exportViews(connection, schemaName, sqlBuilder);
        exportProceduresAndFunctions(connection, schemaName, sqlBuilder);
        exportTriggers(connection, schemaName, sqlBuilder);
        return sqlBuilder.toString();
    }

    private void exportTables(Connection connection, String schemaName, StringBuilder sqlBuilder, boolean containData) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, schemaName, null, new String[]{"TABLE", "SYSTEM TABLE"})) {
            while (resultSet.next()) {
                exportTable(connection, schemaName, resultSet.getString("TABLE_NAME"), sqlBuilder, containData);
            }
        }
    }


    private void exportTable(Connection connection, String schemaName, String tableName, StringBuilder sqlBuilder, boolean containData) throws SQLException {
        try {
            SQLExecutor.getInstance().execute(connection, SQLConstant.TABLE_DDL_FUNCTION_SQL, resultSet -> null);
        } catch (Exception e) {
            //log.error("Failed to create function", e);
        }
        String sql = String.format("select %s.GENERATE_TABLE_DDL('%s', '%s') as sql from %s;", schemaName, schemaName, tableName, tableName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            if (resultSet.next()) {
                sqlBuilder.append(resultSet.getString("sql")).append("\n");
                if (containData) {
                    exportTableData(connection, schemaName, tableName, sqlBuilder);
                }
            }
        }
    }


    private void exportViews(Connection connection, String schemaName, StringBuilder sqlBuilder) throws SQLException {
        String sql = String.format("select TEXT from syscat.views where VIEWSCHEMA='%s';", schemaName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                String ddl = resultSet.getString("TEXT");
                sqlBuilder.append(ddl).append(";").append("\n");
            }
        }
    }

    private void exportProceduresAndFunctions(Connection connection, String schemaName, StringBuilder sqlBuilder) throws SQLException {
        String sql = String.format("select TEXT from syscat.routines where ROUTINESCHEMA='%s';", schemaName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                String ddl = resultSet.getString("TEXT");
                sqlBuilder.append(ddl).append(";").append("\n");
            }
        }
    }


    private void exportTriggers(Connection connection, String schemaName, StringBuilder sqlBuilder) throws SQLException {
        String sql = String.format("select * from SYSCAT.TRIGGERS where TRIGSCHEMA = '%s';", schemaName);
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                String ddl = resultSet.getString("TEXT");
                sqlBuilder.append(ddl).append(";").append("\n");
            }
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
}

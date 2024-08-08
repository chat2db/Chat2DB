package ai.chat2db.plugin.h2;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.model.AsyncContext;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.sql.SQLExecutor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Objects;

public class H2DBManage extends DefaultDBManage implements DBManage {
    @Override
    public void exportDatabase(Connection connection, String databaseName, String schemaName, AsyncContext asyncContext) throws SQLException {
        exportSchema(connection, schemaName, asyncContext);
    }

    private void exportSchema(Connection connection, String schemaName, AsyncContext asyncContext) throws SQLException {
        String sql = String.format("SCRIPT NODATA NOPASSWORDS NOSETTINGS DROP SCHEMA %s;", schemaName);
        if (asyncContext.isContainsData()) {
            sql = sql.replace("NODATA", "");
        }
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                String script = resultSet.getString("SCRIPT");
                if (!(script.startsWith("CREATE USER")||script.startsWith("--"))) {
                    StringBuilder sqlBuilder = new StringBuilder();
                    sqlBuilder.append(script);
                    sqlBuilder.append("\n");
                    asyncContext.write(sqlBuilder.toString());
                }
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

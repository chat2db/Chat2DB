package ai.chat2db.plugin.dm;

import java.sql.Connection;
import java.sql.SQLException;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.sql.SQLExecutor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

public class DMDBManage extends DefaultDBManage implements DBManage {

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
            e.printStackTrace();
        }
    }

    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "DROP TABLE IF EXISTS " +tableName;
        SQLExecutor.getInstance().execute(connection,sql, resultSet -> null);
    }
}

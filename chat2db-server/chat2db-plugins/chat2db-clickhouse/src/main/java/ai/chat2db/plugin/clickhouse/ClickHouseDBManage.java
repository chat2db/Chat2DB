package ai.chat2db.plugin.clickhouse;

import java.sql.Connection;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.sql.SQLExecutor;
import org.apache.commons.lang3.StringUtils;

public class ClickHouseDBManage extends DefaultDBManage implements DBManage {

    @Override
    public Connection getConnection(ConnectInfo connectInfo) {
        String url = setDatabaseInJdbcUrl(connectInfo);
        connectInfo.setUrl(url);

        return super.getConnection(connectInfo);
    }

    private String setDatabaseInJdbcUrl(ConnectInfo connectInfo) {
        String databaseName;
        String url = connectInfo.getUrl();
        if (StringUtils.isBlank((databaseName = connectInfo.getDatabaseName())) && StringUtils.isBlank((databaseName = connectInfo.getSchemaName()))) {
            return url;
        }

        String connectAddress = connectInfo.getHost() + ":" + connectInfo.getPort();
        String[] addressSplit = url.split(connectAddress);
        String connectParams = addressSplit[1];
        if (connectParams.startsWith("/")) {
            // 删除连接参数中的 /
            connectParams = connectParams.substring(1);
        }
        // 添加数据库名
        return addressSplit[0] + connectAddress + "/" + databaseName + connectParams;
    }

    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "DROP TABLE IF EXISTS " + databaseName + "." + tableName;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
    }


}

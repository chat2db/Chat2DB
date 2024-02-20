package ai.chat2db.plugin.mysql;

import java.sql.Connection;
import java.sql.SQLException;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.sql.SQLExecutor;
import org.springframework.util.StringUtils;

public class MysqlDBManage extends DefaultDBManage implements DBManage {
    @Override
    public void connectDatabase(Connection connection, String database) {
        if (StringUtils.isEmpty(database)) {
            return;
        }
        try {
            SQLExecutor.getInstance().execute(connection,"use `" + database + "`;");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }



    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "DROP TABLE "+ format(tableName);
        SQLExecutor.getInstance().execute(connection,sql, resultSet -> null);
    }

    public static String format(String tableName) {
        return "`" + tableName + "`";
    }
}

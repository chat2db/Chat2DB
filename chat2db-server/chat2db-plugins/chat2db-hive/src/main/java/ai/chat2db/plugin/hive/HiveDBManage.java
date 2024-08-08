package ai.chat2db.plugin.hive;

import java.sql.Connection;
import java.sql.SQLException;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.sql.SQLExecutor;
import org.springframework.util.StringUtils;

public class HiveDBManage extends DefaultDBManage implements DBManage {


    @Override
    public void connectDatabase(Connection connection, String database) {
        if (StringUtils.isEmpty(database)) {
            return;
        }
        try {
            SQLExecutor.getInstance().execute(connection,"use " + database );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "drop table if exists " +tableName;
        SQLExecutor.getInstance().execute(connection,sql, resultSet -> null);
    }

    @Override
    public void copyTable(Connection connection, String databaseName, String schemaName, String tableName, String newTableName,boolean copyData) throws SQLException {
        String sql = "CREATE TABLE " + newTableName + "LIKE " + tableName;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
        if(copyData){
            sql = "INSERT INTO " + newTableName + " SELECT * FROM " + tableName;
            SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
        }
    }
}

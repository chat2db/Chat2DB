package ai.chat2db.plugin.clickhouse;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.model.AsyncContext;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.sql.SQLExecutor;
import org.apache.commons.lang3.StringUtils;

import java.sql.*;
import java.util.Objects;

public class ClickHouseDBManage extends DefaultDBManage implements DBManage {
    @Override
    public void exportDatabase(Connection connection, String databaseName, String schemaName, AsyncContext asyncContext) throws SQLException {
        exportTablesOrViewsOrDictionaries(connection, databaseName, schemaName,asyncContext);
        exportFunctions(connection, asyncContext);
    }

    private void exportFunctions(Connection connection, AsyncContext asyncContext) throws SQLException {
        String sql ="SELECT name,create_query from system.functions where origin='SQLUserDefined'";
        try(ResultSet resultSet=connection.createStatement().executeQuery(sql)){
            while (resultSet.next()) {
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("DROP FUNCTION IF EXISTS ").append(resultSet.getString("name")).append(";")
                        .append("\n")
                        .append(resultSet.getString("create_query")).append(";").append("\n");
                asyncContext.write(sqlBuilder.toString());
            }
        }
    }

    private void exportTablesOrViewsOrDictionaries(Connection connection,String databaseName, String schemaName, AsyncContext asyncContext) throws SQLException {
        String sql =String.format("SELECT create_table_query, has_own_data,engine,name from system.`tables` WHERE `database`='%s'", databaseName);
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {

                String ddl = resultSet.getString("create_table_query");
                boolean dataFlag = resultSet.getInt("has_own_data") == 1;
                String tableType = resultSet.getString("engine");
                String tableOrViewName = resultSet.getString("name");
                if (Objects.equals("View", tableType)) {
                    StringBuilder sqlBuilder = new StringBuilder();
                    sqlBuilder.append("DROP VIEW IF EXISTS ").append(databaseName).append(".").append(tableOrViewName)
                            .append(";").append("\n").append(ddl).append(";").append("\n");
                    asyncContext.write(sqlBuilder.toString());
                } else if (Objects.equals("Dictionary", tableType)) {
                    StringBuilder sqlBuilder = new StringBuilder();
                    sqlBuilder.append("DROP DICTIONARY IF EXISTS ").append(databaseName).append(".").append(tableOrViewName)
                            .append(";").append("\n").append(ddl).append(";").append("\n");
                    asyncContext.write(sqlBuilder.toString());
                } else {
                    StringBuilder sqlBuilder = new StringBuilder();
                    sqlBuilder.append("DROP TABLE IF EXISTS ").append(databaseName).append(".").append(tableOrViewName)
                            .append(";").append("\n").append(ddl).append(";").append("\n");
                    asyncContext.write(sqlBuilder.toString());
                    if (asyncContext.isContainsData() && dataFlag) {
                        exportTableData(connection, databaseName,schemaName, tableOrViewName, asyncContext);
                    }
                }
            }
        }
    }


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
        String connectParams = addressSplit.length == 2 ? addressSplit[1] : "";
        if (connectParams.startsWith("/")) {
            // Remove / from connection parameters
            connectParams = connectParams.substring(1);
            if (connectParams.startsWith(databaseName)) {
                // 删除连接参数中的数据库名
                connectParams = connectParams.substring(databaseName.length());
            } else {
                // 是否有连接参数
                int beginIndex = connectParams.indexOf("?");
                // 无连接参数直接设置 ""
                if (beginIndex == -1) {
                    connectParams = "";
                } else {
                    // 删除连接参数前的数据库名
                    connectParams = connectParams.substring(beginIndex);
                }
            }
        }
        // Add database name
        return addressSplit[0] + connectAddress + "/" + databaseName + connectParams;
    }

    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "DROP TABLE IF EXISTS " + databaseName + "." + tableName;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
    }


    @Override
    public void copyTable(Connection connection, String databaseName, String schemaName, String tableName, String newTableName,boolean copyData) throws SQLException {
        String sql = "CREATE TABLE " + newTableName + " AS " + tableName + "";
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
        if(copyData){
            sql = "INSERT INTO " + newTableName + " SELECT * FROM " + tableName;
            SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
        }
    }
}

package ai.chat2db.plugin.clickhouse;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.sql.SQLExecutor;
import org.apache.commons.lang3.StringUtils;

import java.sql.*;
import java.util.Objects;

public class ClickHouseDBManage extends DefaultDBManage implements DBManage {
    @Override
    public String exportDatabase(Connection connection, String databaseName, String schemaName, boolean containData) throws SQLException {
        StringBuilder sqlBuilder = new StringBuilder();
        exportTablesOrViewsOrDictionaries(connection, sqlBuilder, databaseName, containData);
        exportFunctions(connection, sqlBuilder);
        return sqlBuilder.toString();
    }

    private void exportFunctions(Connection connection, StringBuilder sqlBuilder) throws SQLException {
        try(Statement statement = connection.createStatement();ResultSet resultSet = statement.executeQuery("SELECT name,create_query from system.functions where origin='SQLUserDefined'")){
            while (resultSet.next()) {
                sqlBuilder.append("DROP FUNCTION IF EXISTS ").append(resultSet.getString(1)).append(";\n");
                sqlBuilder.append(resultSet.getString(2)).append(";\n");
            }
        }
    }

    private void exportTablesOrViewsOrDictionaries(Connection connection, StringBuilder sqlBuilder, String databaseName, boolean containData) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT create_table_query, has_own_data,engine,name from system.`tables` WHERE `database`='" + databaseName + "'")) {
            while (resultSet.next()) {
                String ddl = resultSet.getString(1);
                boolean dataFlag = resultSet.getInt(2) == 1;
                String tableType = resultSet.getString(3);
                String tableOrViewName = resultSet.getString(4);
                if (Objects.equals("View", tableType)) {
                    sqlBuilder.append("DROP VIEW IF EXISTS ").append(databaseName).append(".").append(tableOrViewName).append(";\n").append(ddl).append(";");
                } else if (Objects.equals("Dictionary", tableType)) {
                    sqlBuilder.append("DROP DICTIONARY IF EXISTS ").append(databaseName).append(".").append(tableOrViewName).append(";\n").append(ddl).append(";");
                } else {
                    sqlBuilder.append("DROP TABLE IF EXISTS ").append(databaseName).append(".").append(tableOrViewName).append(";\n").append(ddl).append(";");
                    if (containData && dataFlag) {
                        exportTableData(connection, tableOrViewName, sqlBuilder);
                    }
                }
            }
        }
    }


    private void exportTableData(Connection connection, String tableName, StringBuilder sqlBuilder) throws SQLException {
        StringBuilder insertSql = new StringBuilder();
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("select * from " + tableName)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            while (resultSet.next()) {
                insertSql.append("INSERT INTO ").append(tableName).append(" VALUES (");
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    String value = resultSet.getString(i);
                    if (Objects.isNull(value)) {
                        insertSql.append("NULL");
                    } else {
                        insertSql.append("'").append(value).append("'");
                    }
                    if (i < metaData.getColumnCount()) {
                        insertSql.append(", ");
                    }
                }
                insertSql.append(");\n");
            }
            insertSql.append("\n");
        }
        sqlBuilder.append(insertSql);
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


}

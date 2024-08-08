package ai.chat2db.spi.jdbc;

import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.common.exception.ConnectionException;
import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.ValueProcessor;
import ai.chat2db.spi.model.AsyncContext;
import ai.chat2db.spi.model.JDBCDataValue;
import ai.chat2db.spi.model.Procedure;
import ai.chat2db.spi.model.SSHInfo;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.sql.IDriverManager;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.ssh.SSHManager;
import ai.chat2db.spi.util.ResultSetUtils;
import com.jcraft.jsch.Session;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jipengfei
 * @version : DefaultDBManage.java
 */
public class DefaultDBManage implements DBManage {

    protected static final String DIVIDING_LINE = "-- ----------------------------";

    protected static final String NEW_LINE = "\n";

    protected static final String EXPORT_TITLE = DIVIDING_LINE + NEW_LINE + "-- Chat2DB export data , export time: %s" + NEW_LINE + DIVIDING_LINE;

    protected static final String TABLE_TITLE = DIVIDING_LINE + NEW_LINE + "-- Table structure for table %s" + NEW_LINE + DIVIDING_LINE;

    protected static final String VIEW_TITLE = DIVIDING_LINE + NEW_LINE + "-- View structure for view %s" + NEW_LINE + DIVIDING_LINE;

    protected static final String FUNCTION_TITLE = DIVIDING_LINE + NEW_LINE + "-- Function structure for function %s" + NEW_LINE + DIVIDING_LINE;

    protected static final String TRIGGER_TITLE = DIVIDING_LINE + NEW_LINE + "-- Trigger structure for trigger %s" + NEW_LINE + DIVIDING_LINE;

    protected static final String PROCEDURE_TITLE = DIVIDING_LINE + NEW_LINE + "-- Procedure structure for procedure %s" + NEW_LINE + DIVIDING_LINE;

    private static final String RECORD_TITLE = DIVIDING_LINE + NEW_LINE + "-- Records of %s" + NEW_LINE + DIVIDING_LINE;


    @Override
    public Connection getConnection(ConnectInfo connectInfo) {
        Connection connection = connectInfo.getConnection();
        SSHInfo ssh = connectInfo.getSsh();
        String url = connectInfo.getUrl();
        String host = connectInfo.getHost();
        String port = connectInfo.getPort() + "";
        Session session = null;
        try {
            if (connection != null && !connection.isClosed()) {
                return connection;
            }
            ssh.setRHost(host);
            ssh.setRPort(port);
            session = getSession(ssh);
            if (session != null) {
                url = url.replace(host, "127.0.0.1").replace(port, ssh.getLocalPort());
            }
        } catch (Exception e) {

            throw new ConnectionException("connection.ssh.error", null, e);
        }
        try {
            connection = IDriverManager.getConnection(url, connectInfo.getUser(), connectInfo.getPassword(),
                    connectInfo.getDriverConfig(), connectInfo.getExtendMap());

        } catch (Exception e1) {
            close(connection, session, ssh);
            throw new BusinessException("connection.error", null, e1);
        }
        connectInfo.setSession(session);
        connectInfo.setConnection(connection);
        if (StringUtils.isNotBlank(connectInfo.getDatabaseName()) || StringUtils.isNotBlank(connectInfo.getSchemaName())) {
            connectDatabase(connection, connectInfo.getDatabaseName());
        }
        return connection;
    }

    private void close(Connection connection, Session session, SSHInfo ssh) {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
            }
        }
        if (session != null) {
            try {
                session.delPortForwardingL(Integer.parseInt(ssh.getLocalPort()));
            } catch (Exception e) {
            }
            try {
                session.disconnect();
            } catch (Exception e) {
            }
        }
    }

    private Session getSession(SSHInfo ssh) {
        Session session = null;
        if (ssh != null && ssh.isUse()) {
            session = SSHManager.getSSHSession(ssh);
        }
        return session;
    }

    @Override
    public void connectDatabase(Connection connection, String database) {

    }

    @Override
    public void modifyDatabase(Connection connection, String databaseName, String newDatabaseName) {

    }

    @Override
    public void createDatabase(Connection connection, String databaseName) {

    }

    @Override
    public void dropDatabase(Connection connection, String databaseName) {

    }

    @Override
    public void createSchema(Connection connection, String databaseName, String schemaName) {

    }

    @Override
    public void dropSchema(Connection connection, String databaseName, String schemaName) {

    }

    @Override
    public void modifySchema(Connection connection, String databaseName, String schemaName, String newSchemaName) {

    }

    @Override
    public void dropFunction(Connection connection, String databaseName, String schemaName, String functionName) {

    }

    @Override
    public void dropTrigger(Connection connection, String databaseName, String schemaName, String triggerName) {

    }

    @Override
    public void dropProcedure(Connection connection, String databaseName, String schemaName, String triggerName) {

    }

    @Override
    public void updateProcedure(Connection connection, String databaseName, String schemaName, Procedure procedure) throws SQLException {

    }

    @Override
    public void exportDatabase(Connection connection, String databaseName, String schemaName, AsyncContext asyncContext) throws SQLException {

    }

    @Override
    public void exportTable(Connection connection, String databaseName, String schemaName, String tableName, AsyncContext asyncContext) throws SQLException {

    }

    @Override
    public void truncateTable(Connection connection, String databaseName, String schemaName, String tableName) throws SQLException {
        String sql = "TRUNCATE TABLE " + tableName ;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
    }

    @Override
    public void copyTable(Connection connection, String databaseName, String schemaName, String tableName, String newTableName,boolean copyData) throws SQLException {
        String sql = "";
        if(copyData){
             sql = "CREATE TABLE " + newTableName + " AS SELECT * FROM " + tableName;
        }else {
            sql = "CREATE TABLE " + newTableName + " AS SELECT * FROM " + tableName + " WHERE 1=0";
        }
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
    }

//    public void exportDatabaseData(Connection connection, String databaseName, String schemaName, String tableName, AsyncContext asyncContext) throws SQLException {
//        exportTableData(connection, databaseName, schemaName, tableName, asyncContext);
//    }

    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "DROP TABLE " + tableName;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
    }

    public void exportTableData(Connection connection, String databaseName, String schemaName, String tableName, AsyncContext asyncContext) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        String tableQuerySql = sqlBuilder.buildTableQuerySql(databaseName, schemaName, tableName);
        asyncContext.info("export table data sql: " + tableQuerySql);
        SQLExecutor.getInstance().execute(connection, tableQuerySql, 1000, resultSet -> {
            ResultSetMetaData metaData = resultSet.getMetaData();
            List<String> columnList = ResultSetUtils.getRsHeader(resultSet);
            List<String> valueList = new ArrayList<>();
            asyncContext.write(String.format(RECORD_TITLE, tableName));
            while (resultSet.next()) {
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    ValueProcessor valueProcessor = Chat2DBContext.getMetaData().getValueProcessor();
                    JDBCDataValue jdbcDataValue = new JDBCDataValue(resultSet, metaData, i, false);
                    String valueString = valueProcessor.getJdbcSqlValueString(jdbcDataValue);
                    valueList.add(valueString);
                }
                String insertSql = sqlBuilder.buildSingleInsertSql(null, null, tableName, columnList, valueList);
                asyncContext.write(insertSql+";");
                valueList.clear();
            }
        });

    }
}
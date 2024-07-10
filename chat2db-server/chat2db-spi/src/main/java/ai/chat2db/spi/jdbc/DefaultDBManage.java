package ai.chat2db.spi.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Objects;

import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.common.exception.ConnectionException;
import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.model.Procedure;
import ai.chat2db.spi.model.SSHInfo;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.sql.IDriverManager;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.ssh.SSHManager;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.lang3.StringUtils;

/**
 * @author jipengfei
 * @version : DefaultDBManage.java
 */
public class DefaultDBManage implements DBManage {


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

        }catch (Exception e1) {
            close(connection,session,ssh);
            throw new BusinessException("connection.error", null, e1);
        }
        connectInfo.setSession(session);
        connectInfo.setConnection(connection);
        if (StringUtils.isNotBlank(connectInfo.getDatabaseName()) || StringUtils.isNotBlank(connectInfo.getSchemaName())) {
            connectDatabase(connection, connectInfo.getDatabaseName());
        }
        return connection;
    }
    private void close(Connection connection,Session session,SSHInfo ssh){
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
    public String exportDatabase(Connection connection, String databaseName, String schemaName, boolean containData) throws SQLException {
        return null;
    }
    public String exportDatabaseData(Connection connection, String databaseName, String schemaName, String tableName) throws SQLException {
        StringBuilder sqlBuilder = new StringBuilder();
        exportTableData(connection, schemaName,tableName, sqlBuilder);
        return sqlBuilder.toString();
    }

    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "DROP TABLE " + tableName;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
    }

    public void exportTableData(Connection connection,String schemaName, String tableName, StringBuilder sqlBuilder) throws SQLException {
        String sql;
        if (Objects.isNull(schemaName)) {
            sql = String.format("select * from %s", tableName);
        }else{
            sql = String.format("select * from %s.%s",schemaName,tableName);
        }
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            while (resultSet.next()) {
                sqlBuilder.append("INSERT INTO ").append(tableName).append(" VALUES (");
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    String value = resultSet.getString(i);
                    if (Objects.isNull(value)) {
                        sqlBuilder.append("NULL");
                    } else {
                        sqlBuilder.append("'").append(value).append("'");
                    }
                    if (i < metaData.getColumnCount()) {
                        sqlBuilder.append(", ");
                    }
                }
                sqlBuilder.append(");\n");
            }
            sqlBuilder.append("\n");
        }
    }
}
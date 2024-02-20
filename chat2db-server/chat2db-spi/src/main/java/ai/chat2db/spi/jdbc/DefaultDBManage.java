package ai.chat2db.spi.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.common.exception.ConnectionException;
import ai.chat2db.spi.DBManage;
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
        if (connection != null) {
            return connection;
        }
        Session session = null;
        SSHInfo ssh = connectInfo.getSsh();
        String url = connectInfo.getUrl();
        String host = connectInfo.getHost();
        String port = connectInfo.getPort() + "";
        try {
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
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                }
            }
            if (session != null) {
                try {
                    session.delPortForwardingL(Integer.parseInt(ssh.getLocalPort()));
                } catch (JSchException e) {
                }
                try {
                    session.disconnect();
                } catch (Exception e) {
                }
            }
            throw new BusinessException("connection.error", null, e1);
        }
        connectInfo.setSession(session);
        connectInfo.setConnection(connection);
        if (StringUtils.isNotBlank(connectInfo.getDatabaseName()) || StringUtils.isNotBlank(connectInfo.getSchemaName())) {
            connectDatabase(connection, connectInfo.getDatabaseName());
        }
        return connection;
    }

    private Session getSession(SSHInfo ssh) {
        Session session = null;
        if (ssh != null && ssh.isUse()) {
            session = SSHManager.getSSHSession(ssh);
        }
        return session;
    }

    @Override
    public void connectDatabase(Connection connection,String database) {

    }

    @Override
    public void modifyDatabase(Connection connection,String databaseName, String newDatabaseName) {

    }

    @Override
    public void createDatabase(Connection connection,String databaseName) {

    }

    @Override
    public void dropDatabase(Connection connection,String databaseName) {

    }

    @Override
    public void createSchema(Connection connection,String databaseName, String schemaName) {

    }

    @Override
    public void dropSchema(Connection connection,String databaseName, String schemaName) {

    }

    @Override
    public void modifySchema(Connection connection,String databaseName, String schemaName, String newSchemaName) {

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
    public void dropTable(Connection connection,String databaseName, String schemaName, String tableName) {
        String sql = "DROP TABLE "+ tableName ;
        SQLExecutor.getInstance().execute(connection,sql, resultSet -> null);
    }
}
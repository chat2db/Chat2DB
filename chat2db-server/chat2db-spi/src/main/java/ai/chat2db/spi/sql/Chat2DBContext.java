
package ai.chat2db.spi.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.Plugin;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.config.DBConfig;
import ai.chat2db.spi.config.DriverConfig;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author jipengfei
 * @version : Chat2DBContext.java
 */
@Slf4j
public class Chat2DBContext {
    private static final ThreadLocal<ConnectInfo> CONNECT_INFO_THREAD_LOCAL = new ThreadLocal<>();

    public static Map<String, Plugin> PLUGIN_MAP = new ConcurrentHashMap<>();

    static {
        ServiceLoader<Plugin> s = ServiceLoader.load(Plugin.class);
        Iterator<Plugin> iterator = s.iterator();
        while (iterator.hasNext()) {
            Plugin plugin = iterator.next();
            PLUGIN_MAP.put(plugin.getDBConfig().getDbType(), plugin);
        }
    }

    public static DriverConfig getDefaultDriverConfig(String dbType) {
        return PLUGIN_MAP.get(dbType).getDBConfig().getDefaultDriverConfig();
    }

    public static SqlBuilder getSqlBuilder() {
        return PLUGIN_MAP.get(getConnectInfo().getDbType()).getMetaData().getSqlBuilder();
    }

    /**
     * 获取当前线程的ContentContext
     *
     * @return
     */
    public static ConnectInfo getConnectInfo() {
        return CONNECT_INFO_THREAD_LOCAL.get();
    }

    public static MetaData getMetaData() {
        return PLUGIN_MAP.get(getConnectInfo().getDbType()).getMetaData();
    }

    public static MetaData getMetaData(String dbType) {
        if (StringUtils.isBlank(dbType)) {
            return getMetaData();
        }
        return PLUGIN_MAP.get(dbType).getMetaData();
    }

    public static DBConfig getDBConfig(String dbType) {
        return PLUGIN_MAP.get(dbType).getDBConfig();
    }

    public static DBConfig getDBConfig() {
        return PLUGIN_MAP.get(getConnectInfo().getDbType()).getDBConfig();
    }

    public static DBManage getDBManage() {
        return PLUGIN_MAP.get(getConnectInfo().getDbType()).getDBManage();
    }

    public static Connection getConnection() {
        ConnectInfo connectInfo = getConnectInfo();
        Connection connection = connectInfo.getConnection();
        if (connection == null) {
            synchronized (connectInfo) {
                connection = connectInfo.getConnection();
                try {
                    if (connection != null && !connection.isClosed()) {
                        return connection;
                    } else {
                        connection = getDBManage().getConnection(connectInfo);
                    }
                } catch (SQLException e) {
                    connection = getDBManage().getConnection(connectInfo);
                }
            }
        }
        return connection;
    }

    public static String getDbVersion() {
        ConnectInfo connectInfo = getConnectInfo();
        String dbVersion = connectInfo.getDbVersion();
        if (dbVersion == null) {
            synchronized (connectInfo) {
                if (connectInfo.getDbVersion() != null) {
                    return connectInfo.getDbVersion();
                } else {
                    dbVersion = SQLExecutor.getInstance().getDbVersion(getConnection());
                    connectInfo.setDbVersion(dbVersion);
                    return connectInfo.getDbVersion();
                }
            }
        } else {
            return dbVersion;
        }

    }


    /**
     * 设置context
     *
     * @param info
     */
    public static void putContext(ConnectInfo info) {
        DriverConfig config = info.getDriverConfig();
        if (config == null) {
            config = getDefaultDriverConfig(info.getDbType());
            info.setDriverConfig(config);
        }
        CONNECT_INFO_THREAD_LOCAL.set(info);
    }

    /**
     * 设置context
     */
    public static void removeContext() {
        ConnectInfo connectInfo = CONNECT_INFO_THREAD_LOCAL.get();
        if (connectInfo != null) {
            Connection connection = connectInfo.getConnection();
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                    connectInfo.setConnection(null);
                }
            } catch (SQLException e) {
                log.error("close connection error", e);
            }

            Session session = connectInfo.getSession();
            if (session != null && session.isConnected() && connectInfo.getSsh() != null
                    && connectInfo.getSsh().isUse()) {
                try {
                    session.delPortForwardingL(Integer.parseInt(connectInfo.getSsh().getLocalPort()));
                } catch (JSchException e) {
                }
            }
            CONNECT_INFO_THREAD_LOCAL.remove();
        }
    }

    /**
     * 设置context
     */
    public static void remove() {
        ConnectInfo connectInfo = CONNECT_INFO_THREAD_LOCAL.get();
        if (connectInfo != null) {
            CONNECT_INFO_THREAD_LOCAL.remove();
        }
    }
}

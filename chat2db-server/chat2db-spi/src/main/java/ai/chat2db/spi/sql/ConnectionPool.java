package ai.chat2db.spi.sql;

import lombok.extern.slf4j.Slf4j;
import org.h2.engine.ConnectionInfo;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ConnectionPool {

    private static ConcurrentHashMap<String, ConnectInfo> CONNECTION_MAP = new ConcurrentHashMap<>();

    static {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000 * 60 * 10);
                    log.info("CONNECTION_MAP size:{}",CONNECTION_MAP.size());
                    CONNECTION_MAP.forEach((k, v) -> {
                        log.info("CONNECTION_key:{},value:{}",k,v.getRefCount());
                        if (v.getLastAccessTime().getTime() + 1000 * 60 * 20 < System.currentTimeMillis() && v.getRefCount() == 0) {
                            try {
                                Connection connection = v.getConnection();
                                if (connection != null) {
                                    connection.close();
                                    CONNECTION_MAP.remove(k);
                                }
                            } catch (SQLException e) {
                                log.error("close connection error", e);
                            }
                        }
                    });
                } catch (InterruptedException e) {
                    log.error("close connection error", e);
                }
            }
        }).start();

    }


    public static Connection getConnection(ConnectInfo connectInfo) {
        Connection connection = connectInfo.getConnection();
        try {
            if (connection != null && !connection.isClosed()) {
                log.info("get connection from loacl");
                return connection;
            }
            String key = connectInfo.getKey();
            ConnectInfo lock = CONNECTION_MAP.computeIfAbsent(key, k -> connectInfo.copy());
            try {
                synchronized (lock) {
                    connection = connectInfo.getConnection();
                    if (connection != null && !connection.isClosed()) {
                        log.info("get connection from loacl");
                        return connection;
                    }

                    int n = lock.incrementRefCount();
                    if (n == 1) {
                        connection = lock.getConnection();
                        if (connection != null && !connection.isClosed()) {
                            log.info("get connection from cache");
                            connectInfo.setConnection(connection);
                            lock.setLastAccessTime(new Date());
                            return connection;
                        } else {
                            log.info("get connection from db begin");
                            connection = Chat2DBContext.getDBManage().getConnection(connectInfo);
                            lock.setConnection(connection);
                            lock.setLastAccessTime(new Date());
                            log.info("get connection from db end");
                        }
                        connectInfo.setConnection(connection);
                        return connection;
                    } else {
                        connection = Chat2DBContext.getDBManage().getConnection(connectInfo);
                        connectInfo.setConnection(connection);
                        return connection;
                    }

                }
            } catch (SQLException e) {
                log.error("get connection error", e);
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            }
        } catch (SQLException e) {
            log.error("get connection error", e);
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (Exception e1) {
                log.error("", e1);
            }
        }
        return null;
    }

    public static void close(ConnectInfo connectInfo) {
        String key = connectInfo.getKey();
        try {
            Connection currentConnection = connectInfo.getConnection();
            // 如果当前连接已经关闭，则不需要重复关闭
            if (currentConnection == null || currentConnection.isClosed()) {
                log.info("connection is already closed, key:{}, n:{}", connectInfo.getKey(), connectInfo.getRefCount());
                return;
            }
        } catch (SQLException e) {
            log.error("connection close error",e);
        }
        ConnectInfo lock = CONNECTION_MAP.get(key);
        if (lock != null) {
            synchronized (lock) {
                int n = lock.decrementRefCount();
                if (n == 0) {
                    lock.setLastAccessTime(new Date());
                    lock.setConnection(connectInfo.getConnection());
                } else {
                    connectInfo.close();
                }
            }
        } else {
            connectInfo.close();
        }


    }
}

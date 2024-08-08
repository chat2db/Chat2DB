package ai.chat2db.server.start.test.dialect;

import ai.chat2db.spi.model.SSHInfo;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Test tool class
 */
public class TestUtils {

    public static final AtomicLong ATOMIC_LONG = new AtomicLong();

    /**
     * a globally unique long
     *
     * @return
     */
    public static long nextLong() {
        return ATOMIC_LONG.incrementAndGet();
    }

    /**
     * If the default value is something like 'DATA'
     * then you need to remove ''
     *
     * @param defaultValue
     * @return
     */
    public static String unWrapperDefaultValue(String defaultValue) {
        if (defaultValue == null) {
            return null;
        }
        if (defaultValue.startsWith("'") && defaultValue.endsWith("'")) {
            if (defaultValue.length() < 2) {
                return defaultValue;
            } else if (defaultValue.length() == 2) {
                return "";
            } else {
                return defaultValue.substring(1, defaultValue.length() - 1);
            }
        }
        return defaultValue;
    }

    public static void buildContext(DialectProperties dialectProperties,Long dataSourceId,Long consoleId){
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setUser(dialectProperties.getUsername());
        connectInfo.setPort(dialectProperties.getPort());
        connectInfo.setHost("localhost");
        connectInfo.setSsh(new SSHInfo());
        connectInfo.setConsoleId(consoleId);
        connectInfo.setDataSourceId(dataSourceId);
        connectInfo.setPassword(dialectProperties.getPassword());
        connectInfo.setDbType(dialectProperties.getDbType());
        connectInfo.setUrl(dialectProperties.getUrl());
        connectInfo.setDatabase(dialectProperties.getDatabaseName());
        connectInfo.setConsoleOwn(false);
        Chat2DBContext.putContext(connectInfo);
    }
}

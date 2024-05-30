package ai.chat2db.spi.util;

import java.sql.*;
import java.text.Collator;
import java.util.*;

import ai.chat2db.spi.model.KeyValue;
import com.alibaba.druid.DbType;

import ai.chat2db.spi.config.DriverConfig;
import ai.chat2db.spi.enums.DataTypeEnum;
import ai.chat2db.spi.model.DataSourceConnect;
import ai.chat2db.spi.model.SSHInfo;
import ai.chat2db.spi.sql.IDriverManager;
import ai.chat2db.spi.ssh.SSHManager;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * jdbc tool class
 *
 * @author Jiaju Zhuang
 */
@Slf4j
public class JdbcUtils {

    private static final long MAX_RESULT_SIZE = 256 * 1024;

    /**
     * Get the database type of Druid
     *
     * @param dbType
     * @return
     */
    public static DbType parse2DruidDbType(String dbType) {
        if (dbType == null) {
            return null;
        }
        if("SUNDB".equalsIgnoreCase(dbType)){
            return DbType.oracle;
        }
        try {
            return DbType.valueOf(dbType.toLowerCase());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse field type
     *
     * @param typeName
     * @param type
     * @return
     */
    public static DataTypeEnum resolveDataType(String typeName, int type) {
        switch (getTypeByTypeName(typeName, type)) {
            case Types.BOOLEAN:
                return DataTypeEnum.BOOLEAN;
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.NVARCHAR:
            case Types.LONGVARCHAR:
            case Types.LONGNVARCHAR:
                return DataTypeEnum.STRING;
            case Types.BIGINT:
            case Types.DECIMAL:
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.INTEGER:
            case Types.NUMERIC:
            case Types.REAL:
            case Types.SMALLINT:
                return DataTypeEnum.NUMERIC;
            case Types.BIT:
            case Types.TINYINT:
                if (typeName.toLowerCase().contains("bool")) {
                    // Declared as numeric but actually it's a boolean
                    return DataTypeEnum.BOOLEAN;
                }
                return DataTypeEnum.NUMERIC;
            case Types.DATE:
            case Types.TIME:
            case Types.TIME_WITH_TIMEZONE:
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return DataTypeEnum.DATETIME;
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return DataTypeEnum.BINARY;
            case Types.BLOB:
            case Types.CLOB:
            case Types.NCLOB:
            case Types.SQLXML:
                return DataTypeEnum.CONTENT;
            case Types.STRUCT:
                return DataTypeEnum.STRUCT;
            case Types.ARRAY:
                return DataTypeEnum.ARRAY;
            case Types.ROWID:
                return DataTypeEnum.ROWID;
            case Types.REF:
                return DataTypeEnum.REFERENCE;
            case Types.OTHER:
                return DataTypeEnum.OBJECT;
            default:
                return DataTypeEnum.UNKNOWN;
        }
    }

    private static int getTypeByTypeName(String typeName, int type) {
        // [JDBC: SQLite driver uses VARCHAR value type for all LOBs]
        if (type == Types.OTHER || type == Types.VARCHAR) {
            if ("BLOB".equalsIgnoreCase(typeName)) {
                return Types.BLOB;
            } else if ("CLOB".equalsIgnoreCase(typeName)) {
                return Types.CLOB;
            } else if ("NCLOB".equalsIgnoreCase(typeName)) {
                return Types.NCLOB;
            }
        } else if (type == Types.BIT) {
            // Workaround for MySQL (and maybe others) when TINYINT(1) == BOOLEAN
            if ("TINYINT".equalsIgnoreCase(typeName)) {
                return Types.TINYINT;
            }
        }
        return type;
    }

    /**
     * Test database connection
     *
     * @param url database connection
     * @param userName username
     * @param password password
     * @param dbType database type
     * @return
     */
    public static DataSourceConnect testConnect(String url, String host, String port,
                                                String userName, String password, String dbType,
                                                DriverConfig driverConfig, SSHInfo ssh, Map<String, Object> properties) {
        DataSourceConnect dataSourceConnect = DataSourceConnect.builder()
                .success(Boolean.TRUE)
                .build();
        Session session = null;
        Connection connection = null;
        // Load driver
        try {
            if (ssh.isUse()) {
                ssh.setRHost(host);
                ssh.setRPort(port);
                session = SSHManager.getSSHSession(ssh);
                url = url.replace(host, "127.0.0.1").replace(port, ssh.getLocalPort());
            }
            // Create connection
            connection = IDriverManager.getConnection(url, userName, password,
                    driverConfig, properties);
        } catch (Exception e) {
            log.error("connection fail:", e);
            dataSourceConnect.setSuccess(Boolean.FALSE);
            // Get the last exception information to the front end
            Throwable t = e;
            while (t.getCause() != null) {
                t = t.getCause();
            }
            dataSourceConnect.setMessage(t.getMessage());
            dataSourceConnect.setErrorDetail(ExceptionUtils.getErrorInfoFromException(t));
            return dataSourceConnect;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    // ignore
                }
            }
            if (session != null) {
                try {
                    if (StringUtils.isNotBlank(ssh.getLocalPort())) {
                        session.delPortForwardingL(Integer.parseInt(ssh.getLocalPort()));
                    }
                    session.disconnect();
                } catch (Exception e) {

                }
            }
        }
        dataSourceConnect.setDescription("成功");
        return dataSourceConnect;
    }

    public static void closeResultSet(@Nullable ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException var2) {
                log.trace("Could not close JDBC ResultSet", var2);
            } catch (Throwable var3) {
                log.trace("Unexpected exception on closing JDBC ResultSet", var3);
            }
        }

    }

    public static void setDriverDefaultProperty(DriverConfig driverConfig) {
        if(driverConfig == null){
            return;
        }
        List<KeyValue> defaultKeyValues = driverConfig.getExtendInfo();
        Map<String, KeyValue> valueMap = Maps.newHashMap();
        if (!CollectionUtils.isEmpty(defaultKeyValues)) {
            for (KeyValue keyValue : defaultKeyValues) {
                if (keyValue == null || StringUtils.isBlank(keyValue.getKey())) {
                    continue;
                }
                valueMap.put(keyValue.getKey(), keyValue);
            }
        }
        try {
            DriverPropertyInfo[] propertyInfos = IDriverManager.getProperty(driverConfig);
            if (propertyInfos == null) {
                return;
            }
            for (int i = 0; i < propertyInfos.length; i++) {
                DriverPropertyInfo propertyInfo = propertyInfos[i];
                if (propertyInfo == null) {
                    continue;
                }
                KeyValue keyValue = valueMap.get(propertyInfo.name);
                if (keyValue != null) {
                    String[] choices = propertyInfo.choices;
                    if (CollectionUtils.isEmpty(keyValue.getChoices()) && choices != null && choices.length > 0) {
                        keyValue.setChoices(Lists.newArrayList(choices));
                    }
                } else {
                    keyValue = new KeyValue();
                    keyValue.setKey(propertyInfo.name);
                    keyValue.setValue(propertyInfo.value);
                    keyValue.setRequired(propertyInfo.required);
                    String[] choices = propertyInfo.choices;
                    if (choices != null && choices.length > 0) {
                        keyValue.setChoices(Lists.newArrayList(choices));
                    }
                    valueMap.put(keyValue.getKey(), keyValue);
                }
            }
            if (!valueMap.isEmpty()) {
                Comparator comparator = Collator.getInstance(Locale.ENGLISH);
                List<KeyValue> result = new ArrayList<>(valueMap.values());
                Collections.sort(result, (o1, o2) -> comparator.compare(o1.getKey(), o2.getKey()));
                driverConfig.setExtendInfo(result);
            }
        } catch (SQLException e) {
            log.error("get property error:", e);
        }
    }

    public static void removePropertySameAsDefault(DriverConfig driverConfig) {
        if(driverConfig == null){
            return;
        }
        List<KeyValue> customValue = driverConfig.getExtendInfo();
        if (CollectionUtils.isEmpty(customValue)) {
            return ;
        }
        Map<String, String> map = Maps.newHashMap();
        List<KeyValue> result = new ArrayList<>();
        try {
            DriverPropertyInfo[] propertyInfos = IDriverManager.getProperty(driverConfig);
            if (propertyInfos == null) {
                return ;
            }
            for (int i = 0; i < propertyInfos.length; i++) {
                DriverPropertyInfo propertyInfo = propertyInfos[i];
                if (propertyInfo == null) {
                    continue;
                }
                map.put(propertyInfo.name, propertyInfo.value);
            }
            for (KeyValue keyValue : customValue) {
                if (keyValue == null || StringUtils.isBlank(keyValue.getKey())) {
                    continue;
                }
                String value = map.get(keyValue.getKey());
                if (!StringUtils.equals(value, keyValue.getValue())) {
                    result.add(keyValue);
                }
            }
            Comparator comparator = Collator.getInstance(Locale.ENGLISH);
            Collections.sort(result, (o1, o2) -> comparator.compare(o1.getKey(), o2.getKey()));
            driverConfig.setExtendInfo(result);
        } catch (SQLException e) {

        }
    }

}

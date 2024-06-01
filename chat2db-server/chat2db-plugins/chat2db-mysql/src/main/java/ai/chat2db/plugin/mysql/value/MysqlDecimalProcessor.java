package ai.chat2db.plugin.mysql.value;

import ai.chat2db.spi.jdbc.DefaultValueProcessor;
import ai.chat2db.spi.model.JDBCDataValue;
import ai.chat2db.spi.model.SQLDataValue;

/**
 * @author: zgq
 * @date: 2024年06月01日 18:01
 */
public class MysqlDecimalProcessor extends DefaultValueProcessor {

    @Override
    public String convertSQLValueByType(SQLDataValue dataValue) {
        return dataValue.getValue();
    }


    @Override
    public Object convertJDBCValueByType(JDBCDataValue dataValue) {
        return new String(dataValue.getBytes());
    }


    @Override
    public String convertJDBCValueStrByType(JDBCDataValue dataValue) {
        return (String) convertJDBCValueByType(dataValue);
    }
}

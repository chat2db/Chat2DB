package ai.chat2db.plugin.mysql.value.sub;

import ai.chat2db.spi.jdbc.DefaultValueProcessor;
import ai.chat2db.spi.model.JDBCDataValue;
import ai.chat2db.spi.model.SQLDataValue;

/**
 * 功能描述
 *
 * @author: zgq
 * @date: 2024年06月01日 12:57
 */
public class MysqlYearProcessor extends DefaultValueProcessor {

    @Override
    public String convertSQLValueByType(SQLDataValue dataValue) {
        return dataValue.getValue();
    }


    @Override
    public String convertJDBCValueByType(JDBCDataValue dataValue) {
        return new String(dataValue.getBytes());
    }


    @Override
    public String convertJDBCValueStrByType(JDBCDataValue dataValue) {
        return new String(dataValue.getBytes());
    }
}

package ai.chat2db.plugin.mysql.value.sub;

import ai.chat2db.plugin.mysql.value.template.MysqlDmlValueTemplate;
import ai.chat2db.server.tools.common.util.EasyStringUtils;
import ai.chat2db.spi.jdbc.DefaultValueProcessor;
import ai.chat2db.spi.model.JDBCDataValue;
import ai.chat2db.spi.model.SQLDataValue;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.function.Function;

/**
 * @author: zgq
 * @date: 2024年06月01日 13:08
 */
public class MysqlBitProcessor extends DefaultValueProcessor {

    @Override
    public String convertSQLValueByType(SQLDataValue dataValue) {
        return getString(dataValue.getValue());
    }


    @Override
    public String convertJDBCValueByType(JDBCDataValue dataValue) {
        return getValue(dataValue, s -> s);
    }


    @Override
    public String convertJDBCValueStrByType(JDBCDataValue dataValue) {
        return getValue(dataValue, this::wrap);
    }

    private String getValue(JDBCDataValue dataValue, Function<String, String> function) {
        int precision = dataValue.getPrecision();
        byte[] bytes = dataValue.getBytes();
        if (precision == 1) {
            //bit(1) [1 -> true] [0 -> false]
            if (bytes.length == 1 && (bytes[0] == 0 || bytes[0] == 1)) {
                return String.valueOf(dataValue.getBoolean());
            }
            // tinyint(1)
            return String.valueOf(dataValue.getInt());
        }
        //bit(m) m: 2~64
        return function.apply(EasyStringUtils.getBitString(bytes, precision));
    }

    public String getString(String value) {

        if (Objects.equals("true", value.toLowerCase())) {
            return "1";
        }
        if (Objects.equals("false", value.toLowerCase())) {
            return "0";
        }
        if (StringUtils.isBlank(value)) {
            return "NULL";
        }
        return MysqlDmlValueTemplate.wrapBit(value);
    }

    private String wrap(String value) {
        return MysqlDmlValueTemplate.wrapBit(value);
    }
}

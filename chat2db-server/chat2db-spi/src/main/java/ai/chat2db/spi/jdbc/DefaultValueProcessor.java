package ai.chat2db.spi.jdbc;

import ai.chat2db.server.tools.common.util.EasyStringUtils;
import ai.chat2db.spi.ValueProcessor;
import ai.chat2db.spi.model.JDBCDataValue;
import ai.chat2db.spi.model.SQLDataValue;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Objects;

/**
 * @author: zgq
 * @date: 2024年05月24日 14:30
 */
public class DefaultValueProcessor implements ValueProcessor {

    @Override
    public String getSqlValueString(SQLDataValue dataValue) {
        if (Objects.isNull(dataValue.getValue())) {
            return "NULL";
        }
        return convertSQLValueByType(dataValue);

    }


    @Override
    public String getJdbcValue(JDBCDataValue dataValue) {
//        Object value = dataValue.getObject();
//        if (Objects.isNull(dataValue.getObject())) {
//            return null;
//        }
//        if (value instanceof String emptySry) {
//            if (StringUtils.isBlank(emptySry)) {
//                return emptySry;
//            }
//        }
        return convertJDBCValueByType(dataValue);
    }


    @Override
    public String getJdbcSqlValueString(JDBCDataValue dataValue) {
//        Object value = dataValue.getObject();
//        if (Objects.isNull(value)) {
//            return "NULL";
//        }
//        if (value instanceof String stringValue) {
//            if (StringUtils.isBlank(stringValue)) {
//                return EasyStringUtils.quoteString(stringValue);
//            }
//        }
        return convertJDBCValueStrByType(dataValue);
    }

    public String convertSQLValueByType(SQLDataValue dataValue) {
        return getString(dataValue.getValue());
    }


    public String convertJDBCValueByType(JDBCDataValue dataValue) {
        return dataValue.getString();
    }


    public String convertJDBCValueStrByType(JDBCDataValue dataValue) {
        String value = dataValue.getString();
        if (value == null) {
            return "NULL";
        }
        return getString(value);

    }

    private boolean isNumber(String value) {
        return NumberUtils.isCreatable(value);
    }

    private String getString(String value) {
//        if (isNumber(value)) {
//            return value;
//        }
        return EasyStringUtils.escapeAndQuoteString(value);
    }
}

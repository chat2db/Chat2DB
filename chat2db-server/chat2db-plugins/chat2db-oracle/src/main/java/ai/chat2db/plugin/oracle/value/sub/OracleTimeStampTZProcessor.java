package ai.chat2db.plugin.oracle.value.sub;

import ai.chat2db.plugin.oracle.value.template.OracleDmlValueTemplate;
import ai.chat2db.spi.jdbc.DefaultValueProcessor;
import ai.chat2db.spi.model.JDBCDataValue;
import ai.chat2db.spi.model.SQLDataValue;

/**
 * 功能描述
 *
 * @author: zgq
 * @date: 2024年06月05日 17:32
 */
public class OracleTimeStampTZProcessor extends DefaultValueProcessor {

    @Override
    public String convertSQLValueByType(SQLDataValue dataValue) {
        return wrap(dataValue.getValue(), dataValue.getScale());
    }


    @Override
    public String convertJDBCValueByType(JDBCDataValue dataValue) {
        String timeStampString = dataValue.getStringValue();
        int scale = dataValue.getScale();
        int lastSpaceIndex = timeStampString.lastIndexOf(" ");
        int lastDotIndex = timeStampString.indexOf(".");
        int nanosLength = lastSpaceIndex - lastDotIndex - 1;
        if (scale == 0) {
            return timeStampString.substring(0, lastDotIndex) + timeStampString.substring(lastDotIndex + 2);
        } else if (nanosLength < scale) {
            // 计算需要补充的零的数量
            int zerosToAdd = scale - nanosLength;
            StringBuilder sb = new StringBuilder(timeStampString);
            for (int i = 0; i < zerosToAdd; i++) {
                sb.insert(lastSpaceIndex, '0');
            }
            return sb.toString();

        }
        return timeStampString;
    }


    @Override
    public String convertJDBCValueStrByType(JDBCDataValue dataValue) {
        return wrap(convertJDBCValueByType(dataValue), dataValue.getScale());
    }

    private String wrap(String value, int scale) {
        if (scale == 0) {
            return OracleDmlValueTemplate.wrapTimestampTzWithOutNanos(value);
        }
        return OracleDmlValueTemplate.wrapTimestampTz(value, scale);
    }
}

package ai.chat2db.plugin.oracle.value.sub;

import ai.chat2db.plugin.oracle.value.template.OracleDmlValueTemplate;
import ai.chat2db.spi.jdbc.DefaultValueProcessor;
import ai.chat2db.spi.model.JDBCDataValue;
import ai.chat2db.spi.model.SQLDataValue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author: zgq
 * @date: 2024年07月05日 下午4:19
 */
public class OracleTimeStampLTZProcessor extends DefaultValueProcessor {


    @Override
    public String convertSQLValueByType(SQLDataValue dataValue) {
        return wrap(dataValue.getValue(), dataValue.getScale());
    }


    @Override
    public String convertJDBCValueByType(JDBCDataValue dataValue) {
        Timestamp timestamp = dataValue.getTimestamp();
        int scale = dataValue.getScale();
        LocalDateTime localDateTime = timestamp.toLocalDateTime();
        StringBuilder templateBuilder = new StringBuilder("yyyy-MM-dd HH:mm:ss");
        if (scale != 0) {
            templateBuilder.append(".");
            templateBuilder.append("S".repeat(scale));
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(templateBuilder.toString());
        return localDateTime.format(formatter);
    }


    @Override
    public String convertJDBCValueStrByType(JDBCDataValue dataValue) {
        Timestamp timestamp = dataValue.getTimestamp();
        int scale = dataValue.getScale();
        // 将 Timestamp 转换为 Instant 对象
        Instant instant = timestamp.toInstant();
        // 将 Instant 转换为 UTC 时区的 ZonedDateTime
        ZonedDateTime utcZonedDateTime = instant.atZone(ZoneId.of("UTC"));
        StringBuilder templateBuilder = new StringBuilder("yyyy-MM-dd HH:mm:ss");
        if (scale != 0) {
            templateBuilder.append(".");
            templateBuilder.append("S".repeat(scale));
        }
        // 定义日期时间格式化器
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(templateBuilder.toString());
        // 格式化 UTC 时区的 ZonedDateTime
        String formattedUtcTime = utcZonedDateTime.format(formatter);
        return wrap(formattedUtcTime, scale);
    }

    private String wrap(String value, int scale) {
        if (scale == 0) {
            return OracleDmlValueTemplate.wrapDate(value);
        }
        return OracleDmlValueTemplate.wrapTimestamp(value, scale);
    }
}

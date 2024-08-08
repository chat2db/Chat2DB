package ai.chat2db.plugin.oracle.value;

import ai.chat2db.plugin.oracle.type.OracleColumnTypeEnum;
import ai.chat2db.plugin.oracle.value.factory.OracleValueProcessorFactory;
import ai.chat2db.server.tools.common.util.EasyStringUtils;
import ai.chat2db.spi.jdbc.DefaultValueProcessor;
import ai.chat2db.spi.model.JDBCDataValue;
import ai.chat2db.spi.model.SQLDataValue;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author: zgq
 * @date: 2024年06月03日 22:30
 */
public class OracleValueProcessor extends DefaultValueProcessor {


    private static final Logger log = LoggerFactory.getLogger(OracleValueProcessor.class);

    @Override
    public String getJdbcValue(JDBCDataValue dataValue) {
        if (OracleColumnTypeEnum.LONG_RAW.getColumnType().getTypeName().equalsIgnoreCase(dataValue.getType())) {
            return convertJDBCValueByType(dataValue);
        }
        Object value = dataValue.getObject();
        if (Objects.isNull(value)) {
            return null;
        }
        if (value instanceof String emptyStr) {
            if (StringUtils.isBlank(emptyStr)) {
                return emptyStr;
            }
        }
        return convertJDBCValueByType(dataValue);
    }


    @Override
    public String getJdbcSqlValueString(JDBCDataValue dataValue) {
        if (OracleColumnTypeEnum.LONG_RAW.getColumnType().getTypeName().equalsIgnoreCase(dataValue.getType())) {
            return convertJDBCValueStrByType(dataValue);
        }
        Object value = dataValue.getObject();
        if (Objects.isNull(value)) {
            return "NULL";
        }
        if (value instanceof String stringValue) {
            if (StringUtils.isBlank(stringValue)) {
                return EasyStringUtils.quoteString(stringValue);
            }
        }
        return convertJDBCValueStrByType(dataValue);
    }

    @Override
    public String convertSQLValueByType(SQLDataValue dataValue) {
        try {
            DefaultValueProcessor valueProcessor = OracleValueProcessorFactory.getValueProcessor(dataValue.getDateTypeName());
            if (Objects.nonNull(valueProcessor)) {
                return valueProcessor.convertSQLValueByType(dataValue);
            }
        } catch (Exception e) {
            log.warn("convertSQLValueByType error", e);
            return super.convertSQLValueByType(dataValue);
        }
        return super.convertSQLValueByType(dataValue);
    }


    @Override
    public String convertJDBCValueByType(JDBCDataValue dataValue) {
        String type = dataValue.getType();
        try {
            DefaultValueProcessor valueProcessor = OracleValueProcessorFactory.getValueProcessor(type);
            if (Objects.nonNull(valueProcessor)) {
                return valueProcessor.convertJDBCValueByType(dataValue);
            }
        } catch (Exception e) {
            log.warn("convertJDBCValueByType error", e);
            return super.convertJDBCValueByType(dataValue);
        }
        return super.convertJDBCValueByType(dataValue);
    }


    @Override
    public String convertJDBCValueStrByType(JDBCDataValue dataValue) {
        String type = dataValue.getType();
        try {
            DefaultValueProcessor valueProcessor = OracleValueProcessorFactory.getValueProcessor(type);
            if (Objects.nonNull(valueProcessor)) {
                return valueProcessor.convertJDBCValueStrByType(dataValue);
            }
        } catch (Exception e) {
            log.warn("convertJDBCValueStrByType error", e);
            return super.convertJDBCValueStrByType(dataValue);
        }
        return super.convertJDBCValueStrByType(dataValue);
    }
}

package ai.chat2db.spi.enums;

import ai.chat2db.server.tools.base.enums.BaseEnum;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * Driver class enumeration
 *
 * @author Jiaju Zhuang
 */
@Getter
public enum DataTypeEnum implements BaseEnum<String> {
    /**
     * Boolean value
     */
    BOOLEAN("Boolean value"),

    /**
     * number
     */
    NUMERIC("number"),

    /**
     * string
     */
    STRING("string"),

    /**
     * date
     */
    DATETIME("date"),

    /**
     * binary
     */
    BINARY("binary"),

    /**
     * content
     */
    CONTENT("content"),

    /**
     * structure
     */
    STRUCT("structure"),

    /**
     * document
     */
    DOCUMENT("document"),

    /**
     * array
     */
    ARRAY("array"),

    /**
     * object
     */
    OBJECT("object"),

    /**
     * reference
     */
    REFERENCE("reference"),

    /**
     * rowid
     */
    ROWID("rowid"),

    /**
     * any
     */
    ANY("any"),

    /**
     * unknow
     */
    UNKNOWN("unknow"),

    /**
     * Row number
     */
    CHAT2DB_ROW_NUMBER("Row number"),
    ;

    final String description;

    DataTypeEnum(String description) {
        this.description = description;
    }

    @Override
    public String getCode() {
        return this.name();
    }

    public static DataTypeEnum getByCode(String code) {
        for (DataTypeEnum value : DataTypeEnum.values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return DataTypeEnum.UNKNOWN;
    }

    public String getSqlValue(String value) {
        if (this == DataTypeEnum.BOOLEAN) {
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                return value;
            } else {
                return "'" + value + "'";
            }
        }
        if (this == DataTypeEnum.NUMERIC) {
            return value;
        }
        if (this == DataTypeEnum.STRING) {
            return getStringValue(value);
        }
        if (this == DataTypeEnum.DATETIME) {
            return "'" + value + "'";
        }
        if (this == DataTypeEnum.BINARY) {
            return "''";
        }
        if (this == DataTypeEnum.CONTENT) {
            return "'" + value + "'";
        }
        if (this == DataTypeEnum.STRUCT) {
            return "'" + value + "'";
        }
        if (this == DataTypeEnum.DOCUMENT) {
            return "'" + value + "'";
        }
        if (this == DataTypeEnum.ARRAY) {
            return "'" + value + "'";
        }
        if (this == DataTypeEnum.OBJECT) {
            return "'" + value + "'";
        }
        if (this == DataTypeEnum.REFERENCE) {
            return "'" + value + "'";
        }
        if (this == DataTypeEnum.ROWID) {
            return "'" + value + "'";
        }
        if (this == DataTypeEnum.ANY) {
            return "'" + value + "'";
        }
        if (this == DataTypeEnum.UNKNOWN) {
            return "'" + value + "'";
        }
        return "'" + value + "'";
    }

    public static String getStringValue(String value) {
        if (StringUtils.isBlank(value)) {
            return "'" + value + "'";
        }
        value = value.replace("\\", "\\\\");
        value = value.replace("'", "\\'");
        value = value.replace("\"", "\\\"");
        return "'" + value + "'";
    }

}

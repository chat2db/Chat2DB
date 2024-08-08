package ai.chat2db.spi.model;

import lombok.Data;

/**
 * @author: zgq
 * @date: 2024年05月31日 16:47
 */
@Data
public class DataType {

    /**
     * 数据类型的名称，如 "VARCHAR", "INTEGER", "DECIMAL", "DATE" 等。
     * 这个名称反映了数据库中字段的确切数据类型，是根据`ResultSetMetaData.getColumnTypeName()`获取的，
     * 对理解和转换字段值至关重要，尤其是在处理数据库特定类型（如Oracle的NUMBER，MySQL的DATETIME）时。
     */
    private String dataTypeName;

    /**
     * 精度（Precision），通常用于数值类型和字符串类型，表示该类型能够存储的最大字符数量或数字的总位数。
     * 对于数值类型，如`DECIMAL(5,2)`，精度5指的是整数部分加上小数部分的总位数。
     * 在从`ResultSetMetaData.getPrecision()`获取时，它帮助确定如何格式化数值，以确保数据的完整性和准确性。
     */
    private Integer precision;

    /**
     * 小数位数（Scale），仅对数值类型有意义，表示小数点右侧的位数。
     * 例如，在`DECIMAL(5,2)`中，比例2表示小数点后保留两位数。
     * 通过`ResultSetMetaData.getScale()`获得，对于构造精确的数值字符串（特别是在财务和科学计算中）非常重要。
     */
    private Integer scale;
}

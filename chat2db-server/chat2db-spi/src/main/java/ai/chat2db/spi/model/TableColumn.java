package ai.chat2db.spi.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.Objects;

/**
 * Column information
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TableColumn  implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Old column, when modifying a column, you need this parameter
     */
    private TableColumn oldColumn;
    /**
     * The old column name, this parameter is needed when modifying the column
      * oldName=name when returning
     */
    private String oldName;

    /**
     * Column name
     */
    @JsonAlias({"COLUMN_NAME","column_name"})
    private String name;

    /**
     * Table Name
     */
    @JsonAlias({"TABLE_NAME","table_name"})
    private String tableName;

    /**
     * Column type
     * For example, varchar(100), double(10,6)
     */

    @JsonAlias({"TYPE_NAME","type_name"})
    private String columnType;

    /**
     * Column data type
     * For example, varchar, double
     */

    @JsonAlias({"DATA_TYPE","data_type"})
    private Integer dataType;


    /**
     * default value
     */

    @JsonAlias({"COLUMN_DEF","column_def"})
    private String defaultValue;



    /**
     * Whether to increase automatically
     * Empty means there is no value. The actual semantics of the database are false.
     */
    private Boolean autoIncrement;

    /**
     * Comment
     */
    @JsonAlias({"REMARKS","remarks"})
    private String comment;

    /**
     * Is it a primary key?
     */
    private Boolean primaryKey;


    /**
     * primary key name
     */
    private String primaryKeyName;


    /**
     * primaryKeyOrder
     */
    private int primaryKeyOrder;

    /**
     * Space name
     */
    @JsonAlias({"TABLE_SCHEM","table_schem"})
    private String schemaName;

    /**
     * Database name
     */
    @JsonAlias({"TABLE_CAT","table_cat"})
    private String databaseName;

//    /**
//     * Data source dependent type name, for a UDT the type name is fully qualified
//     */
//    private String typeName;

    /**
     * column size.
     */

    @JsonAlias({"COLUMN_SIZE","column_size"})
    private Integer columnSize;

    /**
     * is not used.
     */
    private Integer bufferLength;

    /**
     * the number of fractional digits. Null is returned for data types where DECIMAL_DIGITS is not applicable.
     */

    @JsonAlias({"DECIMAL_DIGITS","decimal_digits"})
    private Integer decimalDigits;

    /**
     * Radix (typically either 10 or 2)
     */

    @JsonAlias({"NUM_PREC_RADIX","num_prec_radix"})
    private Integer numPrecRadix;


    /**
     * unused
     */
    private Integer sqlDataType;


    /**
     * unused
     */
    private Integer sqlDatetimeSub;

    /**
     * for char types the maximum number of bytes in the column
     */
    private Integer charOctetLength;

    /**
     * index of column in table (starting at 1)
     */

    @JsonAlias({"ORDINAL_POSITION","ordinal_position"})
    private Integer ordinalPosition;

    /**
     * ISO rules are used to determine the nullability for a column.
     */

    @JsonAlias({"nullable","NULLABLE"})
    @JSONField(name = "nullable")
    private Integer nullable;

    /**
     * String => Indicates whether this is a generated column
     * * YES --- if this a generated column
     * * NO --- if this not a generated column
     */
    private Boolean generatedColumn;


    private String extent;


    private String editStatus;

    private String charSetName;

    private String collationName;

    //Mysql
    private String value;

    //ORACLE
    private String unit;

    // sqlserver
    private Boolean sparse;

    // sqlserver
    private String defaultConstraintName;

    // DM seed
    private Integer seed;

    // DM increment
    private Integer increment;

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true;}
        if (o == null || getClass() != o.getClass()) { return false;}
        TableColumn that = (TableColumn) o;
        return Objects.equals(name, that.name) && Objects.equals(tableName, that.tableName) && Objects.equals(columnType, that.columnType) && Objects.equals(defaultValue, that.defaultValue) && Objects.equals(autoIncrement, that.autoIncrement) && Objects.equals(comment, that.comment) && Objects.equals(columnSize, that.columnSize) && Objects.equals(decimalDigits, that.decimalDigits) && Objects.equals(numPrecRadix, that.numPrecRadix) && Objects.equals(sqlDataType, that.sqlDataType) && Objects.equals(ordinalPosition, that.ordinalPosition) && Objects.equals(nullable, that.nullable) && Objects.equals(extent, that.extent) && Objects.equals(charSetName, that.charSetName) && Objects.equals(collationName, that.collationName) && Objects.equals(value, that.value) && Objects.equals(unit, that.unit) && Objects.equals(sparse, that.sparse) && Objects.equals(defaultConstraintName, that.defaultConstraintName) && Objects.equals(seed, that.seed) && Objects.equals(increment, that.increment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, tableName, columnType, defaultValue, autoIncrement, comment, columnSize, decimalDigits, numPrecRadix, sqlDataType, ordinalPosition, nullable, extent, charSetName, collationName, value, unit, sparse, defaultConstraintName, seed, increment);
    }
}

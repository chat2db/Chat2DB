package ai.chat2db.server.start.test.dialect;

import java.util.Date;

/**
 * Dialect configuration
 */
public interface DialectProperties {

    /**
     * Supported database types
     *
     * @return
     */
    String getDbType();

    /**
     * connection
     *
     * @return
     */
    String getUrl();

    /**
     * Abnormal connection
     *
     * @return
     */
    String getErrorUrl();

    /**
     * userName
     *
     * @return
     */

    String getUsername();

    /**
     * password
     *
     * @return
     */
    String getPassword();

    /**
     *  Name database
     *
     * @return
     */
    String getDatabaseName();

    /**
     * The case depends on the specific database:
     * Create table structure: test table
     * Field:
     * id primary key auto-increment
     * date date is not empty
     * number long integer type
     * string string length 100 default value "DATA"
     *
     * Index (plus $tableName_ because some database indexes are globally unique):
     * $tableName_idx_date date index reverse order
     * $tableName_uk_number unique index
     * $tableName_idx_number_string joint index
     *
     * @return
     */
    String getCrateTableSql(String tableName);

    /**
     * Create table structure
     *
     * @return
     */
    String getDropTableSql(String tableName);

    /**
     * Create a piece of data
     *
     * @return
     */
    String getInsertSql(String tableName, Date date, Long number, String string);

    /**
     * Query a query sql
     *
     * @return
     */
    String getSelectSqlById(String tableName, Long id);

    /**
     * Get a sql whose table structure does not exist
     *
     * @return
     */
    String getTableNotFoundSqlById(String tableName);

    /**
     * Convert case
     * Some database table structures store uppercase letters by default
     * Some databases store lowercase by default
     *
     * @param string
     * @return
     */
    String toCase(String string);

    /**
     * port
     * @return
     */
    Integer getPort();
}

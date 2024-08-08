package ai.chat2db.spi;


import ai.chat2db.spi.model.JDBCDataValue;
import ai.chat2db.spi.model.SQLDataValue;

public interface ValueProcessor {

    /**
     * Converts a given value into a format suitable for use in an SQL statement
     * <br>
     * Example:
     * <br>
     * Input oracle DATE : '2024-05-29 11:35:20.0'
     * <br>
     * Output for Oracle DATE: TO_DATE('2024-05-29 14:25:00', 'SYYYY-MM-DD HH24:MI:SS')
     */
    String getSqlValueString(SQLDataValue dataValue);


    /**
     * 将JDBC数据值对象转换为适合前端展示的字符串格式。
     * <p>
     * 它旨在处理包括但不限于数字、日期、字符串以及特殊的空数据，确保这些数据
     * 在传递到前端用户界面时是格式化良好且可理解的。
     *
     * @param dataValue ResultSetMetaData, ResultSet, columnIndex的组合对象，用于获取数据值。
     * @return 一个格式化后的字符串，适配于前端展示。例如，日期可能会转换为"YYYY-MM-DD"格式，以方便用户直观理解。
     */
    String getJdbcValue(JDBCDataValue dataValue);

    /**
     * 将从JDBC ResultSet中获取的数据值转换并构造为适合DML语句的格式。
     *
     * @param dataValue JDBC数据源中检索出的数据值对象，用于准备DML操作的值。
     *
     * @return 一个格式化后的字符串，可以直接用于DML语句中，确保数据的正确插入或更新。
     */
    String getJdbcSqlValueString(JDBCDataValue dataValue);
}

package ai.chat2db.spi.sql;

import ai.chat2db.server.tools.base.constant.EasyToolsConstant;
import ai.chat2db.server.tools.base.enums.DataSourceTypeEnum;
import ai.chat2db.server.tools.common.util.I18nUtils;
import ai.chat2db.spi.ValueHandler;
import ai.chat2db.spi.jdbc.DefaultValueHandler;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.util.JdbcUtils;
import ai.chat2db.spi.util.ResultSetUtils;
import cn.hutool.core.date.TimeInterval;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.util.Assert;

import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dbhub 统一数据库连接管理
 *
 * @author jipengfei
 */
@Slf4j
public class SQLExecutor {
    /**
     * 全局单例
     */
    private static final SQLExecutor INSTANCE = new SQLExecutor();

    private SQLExecutor() {
    }

    public static SQLExecutor getInstance() {
        return INSTANCE;
    }

    public void close() {
    }

    /**
     * 执行sql
     *
     * @param connection
     * @param sql
     * @param function
     * @return
     */

    public <R> R executeSql(Connection connection, String sql, Function<ResultSet, R> function) {
        if (StringUtils.isBlank(sql)) {
            return null;
        }
        log.info("execute:{}", sql);
        try (Statement stmt = connection.createStatement();) {
            boolean query = stmt.execute(sql);
            // 代表是查询
            if (query) {
                try (ResultSet rs = stmt.getResultSet();) {
                    return function.apply(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public <R> R execute(Connection connection, String sql, ResultSetFunction<R> function) {
        if (StringUtils.isBlank(sql)) {
            return null;
        }
        log.info("execute:{}", sql);
        try (Statement stmt = connection.createStatement();) {
            boolean query = stmt.execute(sql);
            // 代表是查询
            if (query) {
                try (ResultSet rs = stmt.getResultSet();) {
                    return function.apply(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public void executeSql(Connection connection, String sql, Consumer<List<Header>> headerConsumer,
        Consumer<List<String>> rowConsumer, ValueHandler valueHandler) {
        executeSql(connection, sql, headerConsumer, rowConsumer, true, valueHandler);
    }

    public void executeSql(Connection connection, String sql, Consumer<List<Header>> headerConsumer,
        Consumer<List<String>> rowConsumer, boolean limitSize, ValueHandler valueHandler) {
        Assert.notNull(sql, "SQL must not be null");
        log.info("execute:{}", sql);
        try (Statement stmt = connection.createStatement();) {
            boolean query = stmt.execute(sql);
            // 代表是查询
            if (query) {
                ResultSet rs = null;
                try {
                    rs = stmt.getResultSet();
                    // 获取有几列
                    ResultSetMetaData resultSetMetaData = rs.getMetaData();
                    int col = resultSetMetaData.getColumnCount();

                    // 获取header信息
                    List<Header> headerList = Lists.newArrayListWithExpectedSize(col);
                    for (int i = 1; i <= col; i++) {
                        headerList.add(Header.builder()
                            .dataType(JdbcUtils.resolveDataType(
                                resultSetMetaData.getColumnTypeName(i), resultSetMetaData.getColumnType(i)).getCode())
                            .name(ResultSetUtils.getColumnName(resultSetMetaData, i))
                            .build());
                    }
                    headerConsumer.accept(headerList);

                    while (rs.next()) {
                        List<String> row = Lists.newArrayListWithExpectedSize(col);
                        for (int i = 1; i <= col; i++) {
                            row.add(valueHandler.getString(rs, i, limitSize));
                        }
                        rowConsumer.accept(row);
                    }
                } finally {
                    JdbcUtils.closeResultSet(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void executeSqlObject(Connection connection, String sql, Consumer<List<Header>> headerConsumer,
                           Consumer<List<Object>> rowConsumer, boolean limitSize, ValueHandler valueHandler) {
        Assert.notNull(sql, "SQL must not be null");
        log.info("execute:{}", sql);
        try (Statement stmt = connection.createStatement();) {
            boolean query = stmt.execute(sql);
            // 代表是查询
            if (query) {
                ResultSet rs = null;
                try {
                    rs = stmt.getResultSet();
                    // 获取有几列
                    ResultSetMetaData resultSetMetaData = rs.getMetaData();
                    int col = resultSetMetaData.getColumnCount();

                    // 获取header信息
                    List<Header> headerList = Lists.newArrayListWithExpectedSize(col);
                    for (int i = 1; i <= col; i++) {
                        headerList.add(Header.builder()
                                .dataType(JdbcUtils.resolveDataType(
                                        resultSetMetaData.getColumnTypeName(i), resultSetMetaData.getColumnType(i)).getCode())
                                .name(ResultSetUtils.getColumnName(resultSetMetaData, i))
                                .build());
                    }
                    headerConsumer.accept(headerList);

                    while (rs.next()) {
                        List<Object> row = Lists.newArrayListWithExpectedSize(col);
                        for (int i = 1; i <= col; i++) {
                            row.add(valueHandler.getObject(rs, i, limitSize));
                        }
                        rowConsumer.accept(row);
                    }
                } finally {
                    JdbcUtils.closeResultSet(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 执行sql
     *
     * @param sql
     * @return
     * @throws SQLException
     */
    public ExecuteResult execute(final String sql, Connection connection, ValueHandler valueHandler)
        throws SQLException {
        return execute(sql, connection, true, null, null, valueHandler);
    }

    public ExecuteResult executeUpdate(final String sql, Connection connection, int n)
        throws SQLException {
        Assert.notNull(sql, "SQL must not be null");
        log.info("execute:{}", sql);
        // connection.setAutoCommit(false);
        ExecuteResult executeResult = ExecuteResult.builder().sql(sql).success(Boolean.TRUE).build();
        try (Statement stmt = connection.createStatement()) {
            int affectedRows = stmt.executeUpdate(sql);
            if (affectedRows != n) {
                executeResult.setSuccess(false);
                executeResult.setMessage("Update error " + sql + " update affectedRows = " + affectedRows
                    + ", Each SQL statement should update no more than one record. Please use a unique key for "
                    + "updates.");
                // connection.rollback();
            }
        }
        return executeResult;
    }

    /**
     * 执行sql
     *
     * @param sql
     * @param connection
     * @param limitRowSize
     * @param offset
     * @param count
     * @return
     * @throws SQLException
     */
    public ExecuteResult execute(final String sql, Connection connection, boolean limitRowSize, Integer offset,
        Integer count, ValueHandler valueHandler)
        throws SQLException {
        Assert.notNull(sql, "SQL must not be null");
        log.info("execute:{}", sql);

        String type = Chat2DBContext.getConnectInfo().getDbType();
        ExecuteResult executeResult = ExecuteResult.builder().sql(sql).success(Boolean.TRUE).build();
        try (Statement stmt = connection.createStatement()) {
            stmt.setFetchSize(EasyToolsConstant.MAX_PAGE_SIZE);
            if (!DataSourceTypeEnum.MONGODB.getCode().equals(type)) {
                stmt.setQueryTimeout(30);
            }
            if (offset != null && count != null) {
                stmt.setMaxRows(offset + count);
            }

            TimeInterval timeInterval = new TimeInterval();
            boolean query = stmt.execute(sql);
            executeResult.setDescription(I18nUtils.getMessage("sqlResult.success"));
            // 代表是查询
            if (query) {
                ResultSet rs = null;
                try {
                    rs = stmt.getResultSet();
                    // 获取有几列
                    ResultSetMetaData resultSetMetaData = rs.getMetaData();
                    int col = resultSetMetaData.getColumnCount();

                    // 获取header信息
                    List<Header> headerList = Lists.newArrayListWithExpectedSize(col);
                    executeResult.setHeaderList(headerList);
                    int chat2dbAutoRowIdIndex = -1;// chat2db自动生成的行分页ID

                    boolean isMongoMap = false;
                    for (int i = 1; i <= col; i++) {
                        String name = ResultSetUtils.getColumnName(resultSetMetaData, i);
                        // The returned map is from mongodb, and you need to parse the map yourself
                        if (DataSourceTypeEnum.MONGODB.getCode().equals(type) && i == 1 && "map".equals(name)) {
                            isMongoMap = true;
                            break;
                        }
                        if ("CAHT2DB_AUTO_ROW_ID".equals(name)) {
                            chat2dbAutoRowIdIndex = i;
                            continue;
                        }
                        String dataType = JdbcUtils.resolveDataType(
                            resultSetMetaData.getColumnTypeName(i), resultSetMetaData.getColumnType(i)).getCode();
                        headerList.add(Header.builder()
                            .dataType(dataType)
                            .name(name)
                            .build());
                    }

                    // 获取数据信息
                    List<List<String>> dataList = Lists.newArrayList();
                    executeResult.setDataList(dataList);

                    Map<String, Header> headerListMap = null;
                    List<Map<String, String>> dataListMap = null;
                    if (isMongoMap) {
                        headerListMap = Maps.newLinkedHashMap();
                        dataListMap = Lists.newArrayList();
                    }

                    if (offset == null || offset < 0) {
                        offset = 0;
                    }
                    int rowNumber = 0;
                    int rowCount = 1;
                    while (rs.next()) {
                        if (rowNumber++ < offset) {
                            continue;
                        }
                        if (!isMongoMap) {
                            List<String> row = Lists.newArrayListWithExpectedSize(col);
                            dataList.add(row);
                            for (int i = 1; i <= col; i++) {
                                if (chat2dbAutoRowIdIndex == i) {
                                    continue;
                                }
                                row.add(valueHandler.getString(rs, i, limitRowSize));
                            }
                        } else {
                            for (int i = 1; i <= col; i++) {
                                Object o = rs.getObject(i);
                                Map<String, String> row = Maps.newHashMap();
                                dataListMap.add(row);
                                if (o instanceof Document document) {
                                    for (String string : document.keySet()) {
                                        headerListMap.computeIfAbsent(string, k -> Header.builder()
                                            .dataType("string")
                                            .name(string)
                                            .build());
                                        row.put(string, Objects.toString(document.get(string)));
                                    }
                                } else {
                                    headerListMap.computeIfAbsent("_unknown", k -> Header.builder()
                                        .dataType("string")
                                        .name("_unknown")
                                        .build());
                                    row.put("_unknown", Objects.toString(o));
                                }
                            }
                        }
                        if (count != null && count > 0 && rowCount++ >= count) {
                            break;
                        }
                    }

                    if (isMongoMap) {
                        headerList.addAll(headerListMap.values().stream().toList());
                        for (Map<String, String> stringStringMap : dataListMap) {
                            List<String> dataTempList = Lists.newArrayList();
                            dataList.add(dataTempList);
                            for (Header value : headerListMap.values()) {
                                dataTempList.add(stringStringMap.get(value.getName()));
                            }
                        }
                    }

                    executeResult.setDuration(timeInterval.interval());
                } finally {
                    JdbcUtils.closeResultSet(rs);
                }
            } else {
                executeResult.setDuration(timeInterval.interval());
                // 修改或者其他
                executeResult.setUpdateCount(stmt.getUpdateCount());
            }
        }
        return executeResult;
    }

    /**
     * 执行sql
     *
     * @param connection
     * @param sql
     * @return
     * @throws SQLException
     */
    public ExecuteResult execute(Connection connection, String sql, ValueHandler valueHandler) throws SQLException {
        return execute(sql, connection, true, null, null, valueHandler);
    }

    public ExecuteResult execute(Connection connection, String sql) throws SQLException {
        return execute(sql, connection, true, null, null, new DefaultValueHandler());
    }

    /**
     * 获取所有的数据库
     *
     * @param connection
     * @return
     */
    public List<Database> databases(Connection connection) {
        try (ResultSet resultSet = connection.getMetaData().getCatalogs();) {
            List<Database> databases = ResultSetUtils.toObjectList(resultSet, Database.class);
            if (CollectionUtils.isEmpty(databases)) {
                return databases;
            }
            return databases.stream().filter(database -> database.getName() != null).collect(Collectors.toList());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the schema names available in this database. The results are ordered by TABLE_CATALOG and TABLE_SCHEM.
     * The schema columns are:
     * TABLE_SCHEM String => schema name
     * TABLE_CATALOG String => catalog name (may be null)
     * Params:
     * catalog – a catalog name; must match the catalog name as it is stored in the database;"" retrieves those without
     * a catalog; null means catalog name should not be used to narrow down the search. schemaPattern – a schema name;
     * must match the schema name as it is stored in the database; null means schema name should not be used to narrow
     * down the search.
     * Returns:
     * a ResultSet object in which each row is a schema description
     * Throws:
     * SQLException – if a database access error occurs
     * Since:
     * 1.6
     * See Also:
     * getSearchStringEscape
     */
    public List<Schema> schemas(Connection connection, String databaseName, String schemaName) {
        if (StringUtils.isEmpty(databaseName) && StringUtils.isEmpty(schemaName)) {
            try (ResultSet resultSet = connection.getMetaData().getSchemas()) {
                return ResultSetUtils.toObjectList(resultSet, Schema.class);
            } catch (SQLException e) {
                throw new RuntimeException("Get schemas error", e);
            }
        }
        try (ResultSet resultSet = connection.getMetaData().getSchemas(databaseName, schemaName)) {
            return ResultSetUtils.toObjectList(resultSet, Schema.class);
        } catch (SQLException e) {
            throw new RuntimeException("Get schemas error", e);
        }
    }

    /**
     * 获取所有的数据库表
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @param types
     * @return
     */
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName,
        String types[]) {

        try {
            DatabaseMetaData metadata = connection.getMetaData();
            ResultSet resultSet = metadata.getTables(databaseName, schemaName, tableName,
                types);
            // 如果connection为mysql
            if ("MySQL".equalsIgnoreCase(metadata.getDatabaseProductName())) {
                // 获取mysql表的comment
                List<Table> tables = ResultSetUtils.toObjectList(resultSet, Table.class);
                if (CollectionUtils.isNotEmpty(tables)) {
                    for (Table table : tables) {
                        String sql = "show table status where name = '" + table.getName() + "'";
                        try (Statement stmt = connection.createStatement()) {
                            boolean query = stmt.execute(sql);
                            if (query) {
                                try (ResultSet rs = stmt.getResultSet();) {
                                    while (rs.next()) {
                                        table.setComment(rs.getString("Comment"));
                                    }
                                }
                            }
                        }
                    }

                    return tables;
                }
            }
            return ResultSetUtils.toObjectList(resultSet, Table.class);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取所有的数据库表列
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @param columnName
     * @return
     */
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String
        tableName,
        String columnName) {
        try (ResultSet resultSet = connection.getMetaData().getColumns(databaseName, schemaName, tableName,
            columnName)) {
            return ResultSetUtils.toObjectList(resultSet, TableColumn.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * get all table index info
     *
     * @param connection   connection
     * @param databaseName databaseName of the index
     * @param schemaName   schemaName of the index
     * @param tableName    tableName of the index
     * @return List<TableIndex> table index list
     */
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        List<TableIndex> tableIndices = Lists.newArrayList();
        try (ResultSet resultSet = connection.getMetaData().getIndexInfo(databaseName, schemaName, tableName,
            false,
            false)) {
            List<TableIndexColumn> tableIndexColumns = ResultSetUtils.toObjectList(resultSet, TableIndexColumn.class);
            tableIndexColumns.stream().filter(c -> c.getIndexName() != null).collect(
                    Collectors.groupingBy(TableIndexColumn::getIndexName)).entrySet()
                .stream().forEach(entry -> {
                    TableIndex tableIndex = new TableIndex();
                    TableIndexColumn column = entry.getValue().get(0);
                    tableIndex.setName(entry.getKey());
                    tableIndex.setTableName(column.getTableName());
                    tableIndex.setSchemaName(column.getSchemaName());
                    tableIndex.setDatabaseName(column.getDatabaseName());
                    tableIndex.setUnique(!column.getNonUnique());
                    tableIndex.setColumnList(entry.getValue());
                    tableIndices.add(tableIndex);
                });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return tableIndices;
    }

    /**
     * Get all functions available in a catalog.
     *
     * @param connection   connection
     * @param databaseName databaseName of the function
     * @param schemaName   schemaName of the function
     * @return List<Function>
     */
    public List<ai.chat2db.spi.model.Function> functions(Connection connection, String databaseName,
        String schemaName) {
        try (ResultSet resultSet = connection.getMetaData().getFunctions(databaseName, schemaName, null);) {
            return ResultSetUtils.toObjectList(resultSet, ai.chat2db.spi.model.Function.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves a description of all the data types supported by this database. They are ordered by DATA_TYPE and then
     * by how closely the data type maps to the corresponding JDBC SQL type.
     * If the database supports SQL distinct types, then getTypeInfo() will return a single row with a TYPE_NAME of
     * DISTINCT and a DATA_TYPE of Types.DISTINCT. If the database supports SQL structured types, then getTypeInfo()
     * will return a single row with a TYPE_NAME of STRUCT and a DATA_TYPE of Types.STRUCT.
     * If SQL distinct or structured types are supported, then information on the individual types may be obtained from
     * the getUDTs() method.
     *
     * @param connection connection
     * @return List<Function>
     */
    public List<Type> types(Connection connection) {
        try (ResultSet resultSet = connection.getMetaData().getTypeInfo();) {
            return ResultSetUtils.toObjectList(resultSet, ai.chat2db.spi.model.Type.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * procedure list
     *
     * @param connection   connection
     * @param databaseName databaseName
     * @param schemaName   schemaName
     * @return List<Procedure>
     */
    public List<Procedure> procedures(Connection connection, String databaseName, String schemaName) {
        try (ResultSet resultSet = connection.getMetaData().getProcedures(databaseName, schemaName, null)) {
            return ResultSetUtils.toObjectList(resultSet, Procedure.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getDbVersion(Connection connection) {
        try {
            String dbVersion = connection.getMetaData().getDatabaseProductVersion();
            return dbVersion;
        } catch (Exception e) {
            log.error("get db version error", e);
        }
        return "";
    }

}

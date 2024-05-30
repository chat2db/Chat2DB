package ai.chat2db.spi.sql;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import ai.chat2db.server.tools.base.constant.EasyToolsConstant;
import ai.chat2db.server.tools.base.enums.DataSourceTypeEnum;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.common.util.EasyCollectionUtils;
import ai.chat2db.server.tools.common.util.I18nUtils;
import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.ValueHandler;
import ai.chat2db.spi.enums.DataTypeEnum;
import ai.chat2db.spi.enums.SqlTypeEnum;
import ai.chat2db.spi.jdbc.DefaultValueHandler;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.util.JdbcUtils;
import ai.chat2db.spi.util.ResultSetUtils;
import ai.chat2db.spi.util.SqlUtils;
import cn.hutool.core.date.TimeInterval;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.parser.ParserException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.util.Assert;

/**
 * Dbhub unified database connection management
 *
 * @author jipengfei
 */
@Slf4j
public class SQLExecutor implements CommandExecutor {

    /**
     * Singleton instance of SQLExecutor.
     */
    private static final SQLExecutor INSTANCE = new SQLExecutor();

    public SQLExecutor() {
    }

    public static SQLExecutor getInstance() {
        return INSTANCE;
    }


    public <R> R execute(Connection connection, String sql, ResultSetFunction<R> function) {
        log.info("execute:{}", sql);
        try (Statement stmt = connection.createStatement();) {
            boolean query = stmt.execute(sql);
            // Represents the query
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

    public void execute(Connection connection, String sql, ResultSetConsumer consumer) {
        log.info("execute:{}", sql);
        try (Statement stmt = connection.createStatement()) {
            boolean query = stmt.execute(sql);
            // Represents the query
            if (query) {
                try (ResultSet rs = stmt.getResultSet();) {
                    consumer.accept(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void execute(Connection connection, String sql, Consumer<List<Header>> headerConsumer,
                        Consumer<List<String>> rowConsumer, ValueHandler valueHandler) {
        execute(connection, sql, headerConsumer, rowConsumer, true, valueHandler);
    }

    public void execute(Connection connection, String sql, Consumer<List<Header>> headerConsumer,
                        Consumer<List<String>> rowConsumer, boolean limitSize, ValueHandler valueHandler) {
        Assert.notNull(sql, "SQL must not be null");
        log.info("execute:{}", sql);
        try (Statement stmt = connection.createStatement();) {
            boolean query = stmt.execute(sql);
            // Represents the query
            if (query) {
                ResultSet rs = null;
                try {
                    rs = stmt.getResultSet();
                    // Get how many columns
                    ResultSetMetaData resultSetMetaData = rs.getMetaData();
                    int col = resultSetMetaData.getColumnCount();

                    // Get header information
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

    /**
     * Execute SQL
     *
     * @param sql
     * @return
     * @throws SQLException
     */
    public ExecuteResult execute(final String sql, Connection connection, ValueHandler valueHandler)
            throws SQLException {
        return execute(sql, connection, true, null, null, valueHandler);
    }

    @Override
    public ExecuteResult executeUpdate(String sql, Connection connection, int n)
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

    @Override
    public List<ExecuteResult> executeSelectTable(Command command) {
        MetaData metaData = Chat2DBContext.getMetaData();
        String tableName = metaData.getMetaDataName(command.getDatabaseName(), command.getSchemaName(),
                command.getTableName());
        String sql = "select * from " + tableName;
        command.setScript(sql);
        return execute(command);
    }


    /**
     * Executes the given SQL query using the provided connection.
     *
     * @param sql          The SQL query to be executed.
     * @param connection   The database connection to use for the query.
     * @param limitRowSize Flag to indicate if row size should be limited.
     * @param offset       The starting point of rows to fetch in the result set.
     * @param count        The number of rows to fetch from the result set.
     * @param valueHandler Handles the processing of the result set values.
     * @return ExecuteResult containing the result of the execution.
     * @throws SQLException If there is any SQL related error.
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
//            if (!DataSourceTypeEnum.MONGODB.getCode().equals(type)) {
//                stmt.setQueryTimeout(30);
//            }
            if (offset != null && count != null) {
                stmt.setMaxRows(offset + count);
            }

            TimeInterval timeInterval = new TimeInterval();
            boolean query = stmt.execute(sql);
            executeResult.setDescription(I18nUtils.getMessage("sqlResult.success"));
            // Represents the query
            if (query) {
                ResultSet rs = null;
                try {
                    rs = stmt.getResultSet();
                    // Get how many columns
                    ResultSetMetaData resultSetMetaData = rs.getMetaData();
                    int col = resultSetMetaData.getColumnCount();

                    // Get header information
                    List<Header> headerList = Lists.newArrayListWithExpectedSize(col);
                    executeResult.setHeaderList(headerList);
                    int chat2dbAutoRowIdIndex = -1;// Row paging ID automatically generated by chat2db

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

                    // Get data information
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
                // Modification or other
                executeResult.setUpdateCount(stmt.getUpdateCount());
            }
        }
        return executeResult;
    }

    /**
     * Execute SQL
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
     * Get all databases
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
     * Get all database tables
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
//            // If connection is mysql
//            if ("MySQL".equalsIgnoreCase(metadata.getDatabaseProductName())) {
//                // Get the comment of mysql table
//                List<Table> tables = ResultSetUtils.toObjectList(resultSet, Table.class);
//                if (CollectionUtils.isNotEmpty(tables)) {
//                    for (Table table : tables) {
//                        String sql = "show table status where name = '" + table.getName() + "'";
//                        try (Statement stmt = connection.createStatement()) {
//                            boolean query = stmt.execute(sql);
//                            if (query) {
//                                try (ResultSet rs = stmt.getResultSet();) {
//                                    while (rs.next()) {
//                                        table.setComment(rs.getString("Comment"));
//                                    }
//                                }
//                            }
//                        }
//                    }
//
//                    return tables;
//                }
//            }
            return ResultSetUtils.toObjectList(resultSet, Table.class);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** query table names
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @param types
     * @return
     */
    public List<String> tableNames(Connection connection, String databaseName, String schemaName, String tableName, String[] types) {
        List<String> tableNames = new ArrayList<>();
        try (ResultSet resultSet = connection.getMetaData().getTables(databaseName, schemaName, tableName, types)) {
            while (resultSet.next()) {
                tableNames.add(resultSet.getString("TABLE_NAME"));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return tableNames;
    }

    /**
     * Get all database table columns
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

    @Override
    public List<ExecuteResult> execute(Command command) {
        // parse sql
        String type = Chat2DBContext.getConnectInfo().getDbType();
        DbType dbType = JdbcUtils.parse2DruidDbType(type);
//        if ("SQLSERVER".equalsIgnoreCase(type)) {
//            RemoveSpecialGO(param);
//        }

        List<String> sqlList = SqlUtils.parse(command.getScript(), dbType);

        if (CollectionUtils.isEmpty(sqlList)) {
            throw new BusinessException("dataSource.sqlAnalysisError");
        }
        List<ExecuteResult> result = new ArrayList<>();
        // Execute SQL
        for (String originalSql : sqlList) {
            ExecuteResult executeResult = executeSQL(originalSql, dbType, command);
            result.add(executeResult);
        }
        return result;
    }

    private ExecuteResult executeSQL(String originalSql, DbType dbType, Command param) {
        int pageNo = 1;
        int pageSize = 0;
        Integer offset = null;
        Integer count = null;
        String sqlType = SqlTypeEnum.UNKNOWN.getCode();
        // parse sql
        String type = Chat2DBContext.getConnectInfo().getDbType();
        boolean supportDruid = !DataSourceTypeEnum.MONGODB.getCode().equals(type);
        // Parse sql pagination
        SQLStatement sqlStatement = null;
        if (supportDruid) {
            try {
                sqlStatement = SQLUtils.parseSingleStatement(originalSql, dbType);
            } catch (Exception e) {
                log.warn("Failed to parse sql: {}", originalSql, e);
            }
        }

        // Mongodb is currently unable to recognize it, so every time a page is transmitted
        if (!supportDruid || (sqlStatement instanceof SQLSelectStatement)) {
            pageNo = Optional.ofNullable(param.getPageNo()).orElse(1);
            pageSize = Optional.ofNullable(param.getPageSize()).orElse(EasyToolsConstant.MAX_PAGE_SIZE);
            offset = (pageNo - 1) * pageSize;
            count = pageSize;
            sqlType = SqlTypeEnum.SELECT.getCode();
        }

        ExecuteResult executeResult = null;
        if (SqlTypeEnum.SELECT.getCode().equals(sqlType) && !SqlUtils.hasPageLimit(originalSql, dbType)) {
            String pageLimit = Chat2DBContext.getSqlBuilder().pageLimit(originalSql, offset, pageNo, pageSize);
            if (StringUtils.isNotBlank(pageLimit)) {
                executeResult = execute(pageLimit, 0, count);
            }
        }
        if (executeResult == null || !executeResult.getSuccess()) {
            executeResult = execute(originalSql, offset, count);
        }

        executeResult.setSqlType(sqlType);
        executeResult.setOriginalSql(originalSql);

        boolean supportJsqlParser = !DataSourceTypeEnum.MONGODB.getCode().equals(type);
        if (supportJsqlParser) {
            try {
                SqlUtils.buildCanEditResult(originalSql, dbType, executeResult);
            } catch (Exception e) {
                log.warn("buildCanEditResult error", e);
            }
        }

        if (SqlTypeEnum.SELECT.getCode().equals(sqlType)) {
            executeResult.setPageNo(pageNo);
            executeResult.setPageSize(pageSize);
            executeResult.setHasNextPage(
                    CollectionUtils.size(executeResult.getDataList()) >= executeResult.getPageSize());
        } else {
            executeResult.setPageNo(pageNo);
            executeResult.setPageSize(CollectionUtils.size(executeResult.getDataList()));
            executeResult.setHasNextPage(Boolean.FALSE);
        }

        List<Header> headers = executeResult.getHeaderList();
//        if (executeResult.getSuccess() && executeResult.isCanEdit() && CollectionUtils.isNotEmpty(headers)) {
//            headers = setColumnInfo(headers, executeResult.getTableName(), param.getSchemaName(),
//                    param.getDatabaseName());
//        }
        Header rowNumberHeader = Header.builder()
                .name(I18nUtils.getMessage("sqlResult.rowNumber"))
                .dataType(DataTypeEnum.CHAT2DB_ROW_NUMBER
                        .getCode()).build();

        executeResult.setHeaderList(EasyCollectionUtils.union(Arrays.asList(rowNumberHeader), headers));
        if (executeResult.getDataList() != null) {
            int rowNumberIncrement = 1 + Math.max(pageNo - 1, 0) * pageSize;
            for (int i = 0; i < executeResult.getDataList().size(); i++) {
                List<String> row = executeResult.getDataList().get(i);
                List<String> newRow = Lists.newArrayListWithExpectedSize(row.size() + 1);
                newRow.add(Integer.toString(i + rowNumberIncrement));
                newRow.addAll(row);
                executeResult.getDataList().set(i, newRow);
            }
        }
        //  Total number of fuzzy rows
        executeResult.setFuzzyTotal(calculateFuzzyTotal(pageNo, pageSize, executeResult));
        return executeResult;
    }

    private String calculateFuzzyTotal(int pageNo, int pageSize, ExecuteResult executeResult) {
        int dataSize = CollectionUtils.size(executeResult.getDataList());
        if (pageSize <= 0) {
            return Integer.toString(dataSize);
        }
        int fuzzyTotal = Math.max(pageNo - 1, 0) * pageSize + dataSize;
        if (dataSize < pageSize) {
            return Integer.toString(fuzzyTotal);
        }
        return Integer.toString(fuzzyTotal) + "+";
    }

    private ExecuteResult execute(String sql, Integer offset, Integer count) {
        ExecuteResult executeResult;
        try {
            ValueHandler valueHandler = Chat2DBContext.getMetaData().getValueHandler();
            executeResult = SQLExecutor.getInstance().execute(sql, Chat2DBContext.getConnection(), true, offset, count,
                    valueHandler);
        } catch (SQLException e) {
            log.error("Execute sql: {} exception", sql, e);
            executeResult = ExecuteResult.builder()
                    .sql(sql)
                    .success(Boolean.FALSE)
                    .message(e.getMessage())
                    .build();
        }
        return executeResult;
    }
}

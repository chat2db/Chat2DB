package ai.chat2db.spi.sql;

import ai.chat2db.server.tools.base.constant.EasyToolsConstant;
import ai.chat2db.server.tools.base.enums.DataSourceTypeEnum;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.common.util.EasyCollectionUtils;
import ai.chat2db.server.tools.common.util.I18nUtils;
import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.ValueProcessor;
import ai.chat2db.spi.enums.DataTypeEnum;
import ai.chat2db.spi.enums.SqlTypeEnum;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.util.JdbcUtils;
import ai.chat2db.spi.util.ResultSetUtils;
import ai.chat2db.spi.util.SqlUtils;
import cn.hutool.core.date.TimeInterval;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
        try (Statement stmt = connection.createStatement();) {
            boolean query = stmt.execute(sql);
            // Represents the query
            if (query) {
                try (ResultSet rs = stmt.getResultSet();) {
                    return function.apply(rs);
                }
            }
        } catch (Exception e) {
            log.error("execute:{}", sql, e);
            throw new RuntimeException(e);
        }
        return null;
    }

    public void execute(Connection connection, String sql, ResultSetConsumer consumer) {
        try (Statement stmt = connection.createStatement()) {
            boolean query = stmt.execute(sql);
            // Represents the query
            if (query) {
                try (ResultSet rs = stmt.getResultSet();) {
                    consumer.accept(rs);
                }
            }
        } catch (Exception e) {
            log.error("execute:{}", sql, e);
            throw new RuntimeException(e);
        }
    }

//    public void execute(Connection connection, String sql, Consumer<List<Header>> headerConsumer,
//        Consumer<List<String>> rowConsumer, ValueHandler valueHandler) {
//        execute(connection, sql, headerConsumer, rowConsumer, true, valueHandler);
//    }

    public void execute(
            Connection connection, String sql,
            Consumer<List<Header>> headerConsumer,
            Consumer<List<String>> rowConsumer,
            java.util.function.Function<JDBCDataValue,
                    String> valueFunction,
            boolean limitSize) {
        Assert.notNull(sql, "SQL must not be null");
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
                    List<Header> headerList = generateHeaderList(resultSetMetaData);
                    headerConsumer.accept(headerList);

                    while (rs.next()) {
                        List<String> row = Lists.newArrayListWithExpectedSize(col);
                        for (int i = 1; i <= col; i++) {
                            JDBCDataValue jdbcDataValue = new JDBCDataValue(rs, resultSetMetaData, i, limitSize);
                            row.add(valueFunction.apply(jdbcDataValue));
                        }
                        rowConsumer.accept(row);
                    }
                } finally {
                    JdbcUtils.closeResultSet(rs);
                }
            }
        } catch (SQLException e) {
            log.error("execute:{}", sql, e);
            throw new RuntimeException(e);
        }
    }

//    /**
//     * Execute SQL
//     *
//     * @param sql
//     * @return
//     * @throws SQLException
//     */
//    public ExecuteResult execute(final String sql, Connection connection, ValueHandler valueHandler)
//        throws SQLException {
//        return execute(sql, connection, true, null, null, valueHandler);
//    }

    @Override
    public ExecuteResult executeUpdate(String sql, Connection connection, int n)
            throws SQLException {
        Assert.notNull(sql, "SQL must not be null");
        ExecuteResult executeResult = ExecuteResult.builder().sql(sql).success(Boolean.TRUE).build();
        try (Statement stmt = connection.createStatement()) {
            int affectedRows = stmt.executeUpdate(sql);
            if (affectedRows != n) {
                log.info("Update error {} update affectedRows = {}", sql, affectedRows);
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
     * @return ExecuteResult containing the result of the execution.
     * @throws SQLException If there is any SQL related error.
     */
    public ExecuteResult execute(final String sql, Connection connection, boolean limitRowSize, Integer offset, Integer count)
            throws SQLException {
        Assert.notNull(sql, "SQL must not be null");
        ExecuteResult executeResult = ExecuteResult.builder().sql(sql).success(Boolean.TRUE).build();
        try (Statement stmt = connection.createStatement()) {
            stmt.setFetchSize(EasyToolsConstant.MAX_PAGE_SIZE);
            if (offset != null && count != null) {
                stmt.setMaxRows(offset + count);
            }
            TimeInterval timeInterval = new TimeInterval();
            boolean query = stmt.execute(sql);
            executeResult.setDescription(I18nUtils.getMessage("sqlResult.success"));
            // Represents the query
            if (query) {
                executeResult = generateQueryExecuteResult(stmt, limitRowSize, offset, count);
            } else {
                // Modification or other
                executeResult.setUpdateCount(stmt.getUpdateCount());
            }
            executeResult.setDuration(timeInterval.interval());
        }
        return executeResult;
    }

    private ExecuteResult generateQueryExecuteResult(Statement stmt, boolean limitRowSize, Integer offset,
                                                     Integer count) throws SQLException {
        ExecuteResult executeResult = ExecuteResult.builder().success(Boolean.TRUE).build();
        executeResult.setDescription(I18nUtils.getMessage("sqlResult.success"));
        ResultSet rs = null;
        try {
            rs = stmt.getResultSet();
            // Get how many columns
            ResultSetMetaData resultSetMetaData = rs.getMetaData();
            int col = resultSetMetaData.getColumnCount();
            // Get header information
            List<Header> headerList = generateHeaderList(resultSetMetaData);


            int chat2dbAutoRowIdIndex = getChat2dbAutoRowIdIndex(headerList);
            // Get data information
            List<List<String>> dataList = generateDataList(rs, col, chat2dbAutoRowIdIndex, limitRowSize,
                    offset, count);

            executeResult.setHeaderList(headerList);
            executeResult.setDataList(dataList);
        } finally {
            JdbcUtils.closeResultSet(rs);
        }
        return executeResult;
    }

    private List<List<String>> generateDataList(ResultSet rs, int col, int chat2dbAutoRowIdIndex,
                                                boolean limitRowSize, Integer offset, Integer count) throws SQLException {
        List<List<String>> dataList = Lists.newArrayList();

        if (offset == null || offset < 0) {
            offset = 0;
        }
        int rowNumber = 0;
        int rowCount = 1;
        while (rs.next()) {
            if (rowNumber++ < offset) {
                continue;
            }
            List<String> row = Lists.newArrayListWithExpectedSize(col);
            dataList.add(row);
            for (int i = 1; i <= col; i++) {
                if (chat2dbAutoRowIdIndex == i) {
                    continue;
                }
                ValueProcessor valueProcessor = Chat2DBContext.getMetaData().getValueProcessor();
                row.add(valueProcessor.getJdbcValue(new JDBCDataValue(rs, rs.getMetaData(), i, limitRowSize)));
            }
            if (count != null && count > 0 && rowCount++ >= count) {
                break;
            }
        }
        return dataList;
    }

    private int getChat2dbAutoRowIdIndex(List<Header> headerList) {

        for (int i = 0; i < headerList.size(); i++) {
            Header header = headerList.get(i);
            if ("CAHT2DB_AUTO_ROW_ID".equals(header.getName())) {
                headerList.remove(i);
                return i + 1;
            }
        }
        return -1;
    }


    private List<Header> generateHeaderList(ResultSetMetaData resultSetMetaData) throws SQLException {
        int col = resultSetMetaData.getColumnCount();
        List<Header> headerList = Lists.newArrayListWithExpectedSize(col);
        for (int i = 1; i <= col; i++) {
            headerList.add(Header.builder()
                    .dataType(JdbcUtils.resolveDataType(
                            resultSetMetaData.getColumnTypeName(i), resultSetMetaData.getColumnType(i)).getCode())
                    .name(ResultSetUtils.getColumnName(resultSetMetaData, i))
                    .build());
        }
        return headerList;
    }


    public ExecuteResult execute(Connection connection, String sql) throws SQLException {
        return execute(sql, connection, true, null, null);
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
     * The schema columns are: TABLE_SCHEM String => schema name TABLE_CATALOG String => catalog name (may be null)
     * Params: catalog – a catalog name; must match the catalog name as it is stored in the database;"" retrieves those
     * without a catalog; null means catalog name should not be used to narrow down the search. schemaPattern – a schema
     * name; must match the schema name as it is stored in the database; null means schema name should not be used to
     * narrow down the search. Returns: a ResultSet object in which each row is a schema description Throws:
     * SQLException – if a database access error occurs Since: 1.6 See Also: getSearchStringEscape
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
        try (ResultSet resultSet = connection.getMetaData().getTables(databaseName, schemaName, tableName, types)) {
            return ResultSetUtils.toObjectList(resultSet, Table.class);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * query table names
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @param types
     * @return
     */
    public List<String> tableNames(Connection connection, String databaseName, String schemaName, String tableName,
                                   String[] types) {
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
     * by how closely the data type maps to the corresponding JDBC SQL type. If the database supports SQL distinct
     * types, then getTypeInfo() will return a single row with a TYPE_NAME of DISTINCT and a DATA_TYPE of
     * Types.DISTINCT. If the database supports SQL structured types, then getTypeInfo() will return a single row with a
     * TYPE_NAME of STRUCT and a DATA_TYPE of Types.STRUCT. If SQL distinct or structured types are supported, then
     * information on the individual types may be obtained from the getUDTs() method.
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
        if (StringUtils.isBlank(command.getScript())) {
            return Collections.emptyList();
        }
        // parse sql
        String type = Chat2DBContext.getConnectInfo().getDbType();
        DbType dbType = JdbcUtils.parse2DruidDbType(type);
        List<String> sqlList = Lists.newArrayList(command.getScript());
        if(!command.isSingle()) {
            sqlList = SqlUtils.parse(command.getScript(), dbType, true);
        }
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
        int pageNo = Optional.ofNullable(param.getPageNo()).orElse(1);
        int pageSize = Optional.ofNullable(param.getPageSize()).orElse(EasyToolsConstant.MAX_PAGE_SIZE);
        Integer offset = (pageNo - 1) * pageSize;
        Integer count = pageSize;
        SqlTypeEnum sqlType = getSqlType(dbType, originalSql);
        ExecuteResult executeResult = null;

        if (SqlTypeEnum.SELECT.equals(sqlType) && !SqlUtils.hasPageLimit(originalSql, dbType)) {
            String pageLimit = Chat2DBContext.getSqlBuilder().pageLimit(originalSql, offset, pageNo, pageSize);
            if (StringUtils.isNotBlank(pageLimit)) {
                executeResult = execute(pageLimit, 0, count);
            }
        }
        if (executeResult == null || !executeResult.getSuccess()) {
            executeResult = execute(originalSql, offset, count);
        }

        executeResult.setSqlType(sqlType.getCode());
        executeResult.setOriginalSql(originalSql);

        SqlUtils.buildCanEditResult(originalSql, dbType, executeResult);
        // Add row number
        addRowNumber(executeResult, pageNo, pageSize);
        //  Total number of fuzzy rows
        setPageInfo(executeResult, sqlType, pageNo, pageSize);
        return executeResult;
    }

    private SqlTypeEnum getSqlType(DbType dbType, String originalSql) {
        SqlTypeEnum sqlType = SqlTypeEnum.UNKNOWN;
        // parse sql
        String type = Chat2DBContext.getConnectInfo().getDbType();
        boolean supportDruid = !DataSourceTypeEnum.MONGODB.getCode().equals(type);
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
            sqlType = SqlTypeEnum.SELECT;
        }
        return sqlType;
    }

    private void setPageInfo(ExecuteResult executeResult, SqlTypeEnum sqlType, int pageNo, int pageSize) {
        if (SqlTypeEnum.SELECT.equals(sqlType)) {
            executeResult.setPageNo(pageNo);
            executeResult.setPageSize(pageSize);
            executeResult.setHasNextPage(
                    CollectionUtils.size(executeResult.getDataList()) >= executeResult.getPageSize());
        } else {
            executeResult.setPageNo(pageNo);
            executeResult.setPageSize(CollectionUtils.size(executeResult.getDataList()));
            executeResult.setHasNextPage(Boolean.FALSE);
        }
        executeResult.setFuzzyTotal(calculateFuzzyTotal(pageNo, pageSize, executeResult));
    }


    private void addRowNumber(ExecuteResult executeResult, int pageNo, int pageSize) {
        List<Header> headers = executeResult.getHeaderList();
        Header rowNumberHeader = Header.builder()
                .name(I18nUtils.getMessage("sqlResult.rowNumber"))
                .dataType(DataTypeEnum.CHAT2DB_ROW_NUMBER
                        .getCode()).build();
        executeResult.setHeaderList(EasyCollectionUtils.union(Arrays.asList(rowNumberHeader), headers));

        // Add row number
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
        return fuzzyTotal + "+";
    }

    private ExecuteResult execute(String sql, Integer offset, Integer count) {
        ExecuteResult executeResult;
        try {
            executeResult = SQLExecutor.getInstance().execute(sql, Chat2DBContext.getConnection(), true, offset, count);
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

    public void execute(Connection connection, String sql, int batchSize, ResultSetConsumer consumer) {
        try (Statement stmt = connection.createStatement()) {
            stmt.setFetchSize(batchSize);
            boolean query = stmt.execute(sql);
            // Represents the query
            if (query) {
                try (ResultSet rs = stmt.getResultSet()) {
                    consumer.accept(rs);
                }
            }
        } catch (Exception e) {
            log.error("execute error:{}", sql, e);
            throw new RuntimeException(e);
        }
    }

    public void executeBatchInsert(Connection connection, List<String> sqlCacheList) {
        try (Statement stmt = connection.createStatement()) {
            for (String sql : sqlCacheList) {
                stmt.addBatch(sql);
            }
            stmt.executeBatch();
            stmt.clearBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

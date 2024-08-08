package ai.chat2db.server.web.api.controller.rdb.data.sql;

import ai.chat2db.server.domain.api.enums.ExportFileSuffix;
import ai.chat2db.server.domain.api.param.datasource.DatabaseExportDataParam;
import ai.chat2db.server.web.api.controller.rdb.data.BaseDataExporter;
import ai.chat2db.server.web.api.controller.rdb.data.task.TaskManager;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.ValueProcessor;
import ai.chat2db.spi.model.JDBCDataValue;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.ResultSetUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: zgq
 * @date: 2024年06月04日 10:33
 */
@Component("sqlExporter")
@Slf4j
public class SqlDataExporter extends BaseDataExporter {

    public SqlDataExporter() {
        this.suffix = ExportFileSuffix.SQL.getSuffix();
        this.contentType = "text/sql";
    }

    /**
     * @param connection
     * @param databaseExportDataParam
     * @param file
     */
    @Override
    protected void singleExport(Connection connection, DatabaseExportDataParam databaseExportDataParam, File file) {
        String tableName = databaseExportDataParam.getTableNames().get(0);
        log.info("开始导出：{}表数据，导出类型：sql", tableName);
        try (PrintWriter writer = new PrintWriter(file);) {
            exportSql(connection, databaseExportDataParam, tableName, writer);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected ByteArrayOutputStream multiExport(Connection connection, DatabaseExportDataParam databaseExportDataParam, String tableName) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        log.info("开始导出：{}表数据，导出类型：sql", tableName);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8))) {
            exportSql(connection, databaseExportDataParam, tableName, writer);
        }
        return byteArrayOutputStream;
    }

    private void exportSql(Connection connection, DatabaseExportDataParam databaseExportDataParam, String tableName, PrintWriter writer) {
        String databaseName = databaseExportDataParam.getDatabaseName();
        String schemaName = databaseExportDataParam.getSchemaName();
        Boolean containsHeader = databaseExportDataParam.getContainsHeader();
        MetaData metaData = Chat2DBContext.getMetaData();
        String querySql = metaData.getSqlBuilder().buildTableQuerySql(databaseName, schemaName, tableName);
        SqlBuilder sqlBuilder = metaData.getSqlBuilder();
        ValueProcessor valueProcessor = metaData.getValueProcessor();
        String sqyType = databaseExportDataParam.getSqyType();

        switch (sqyType) {
            case "single" -> exportSingleInsert(connection, querySql, containsHeader, sqlBuilder,
                                                valueProcessor, databaseName, schemaName, tableName, writer);
            case "multi" -> exportMultiInsert(connection, querySql, containsHeader, sqlBuilder,
                                              valueProcessor, databaseName, schemaName, tableName, writer);
            case "update" -> exportUpdate(connection, querySql, sqlBuilder, valueProcessor,
                                          databaseName, schemaName, tableName, writer);
            default -> throw new IllegalArgumentException("Unsupported sqyType: " + sqyType);
        }
    }

    private void exportSingleInsert(Connection connection, String querySql, Boolean containsHeader,
                                    SqlBuilder sqlBuilder, ValueProcessor valueProcessor,
                                    String databaseName, String schemaName, String tableName, PrintWriter writer) {
        List<String> sqlList = new ArrayList<>(BATCH_SIZE);
        SQLExecutor.getInstance().execute(connection, querySql, BATCH_SIZE, resultSet -> {
            List<String> header = containsHeader ? ResultSetUtils.getRsHeader(resultSet) : null;
            while (resultSet.next()) {
                List<String> rowData = extractRowData(resultSet, valueProcessor);
                String sql = sqlBuilder.buildSingleInsertSql(databaseName, schemaName, tableName, header, rowData);
                sqlList.add(sql+";");
                if (sqlList.size() >= BATCH_SIZE) {
                    writeSqlList(writer, sqlList);
                }
            }
            if(CollectionUtils.isNotEmpty(sqlList)){
                writeSqlList(writer, sqlList);
            }
        });
        TaskManager.increaseCurrent();
    }

    private void exportMultiInsert(Connection connection, String querySql, Boolean containsHeader,
                                   SqlBuilder sqlBuilder, ValueProcessor valueProcessor,
                                   String databaseName, String schemaName, String tableName, PrintWriter writer) {
        SQLExecutor.getInstance().execute(connection, querySql, BATCH_SIZE, resultSet -> {
            List<List<String>> dataList = new ArrayList<>(BATCH_SIZE);
            List<String> header = containsHeader ? ResultSetUtils.getRsHeader(resultSet) : null;
            while (resultSet.next()) {
                dataList.add(extractRowData(resultSet, valueProcessor));
            }
            String sql = sqlBuilder.buildMultiInsertSql(databaseName, schemaName, tableName, header, dataList);
            writer.println(sql+";");
            writer.flush();
        });
        TaskManager.increaseCurrent();
    }

    private void exportUpdate(Connection connection, String querySql, SqlBuilder sqlBuilder,
                              ValueProcessor valueProcessor,
                              String databaseName, String schemaName, String tableName, PrintWriter writer) {
        List<String> sqlList = new ArrayList<>(BATCH_SIZE);
        SQLExecutor.getInstance().execute(connection, querySql, BATCH_SIZE, resultSet -> {
            Map<String, String> primaryKeyMap = getPrimaryKeyMap(connection, databaseName, schemaName, tableName);
            while (resultSet.next()) {
                Map<String, String> row = extractRowDataAsMap(resultSet, valueProcessor, primaryKeyMap);
                String sql = sqlBuilder.buildUpdateSql(databaseName, schemaName, tableName, row, primaryKeyMap);
                sqlList.add(sql);
                if (sqlList.size() >= BATCH_SIZE || resultSet.isLast()) {
                    writeSqlList(writer, sqlList);
                }
            }
        });
        TaskManager.increaseCurrent();
    }

    private List<String> extractRowData(ResultSet resultSet, ValueProcessor valueProcessor) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        List<String> rowData = new ArrayList<>(metaData.getColumnCount());
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            JDBCDataValue jdbcDataValue = new JDBCDataValue(resultSet, metaData, i, false);
            rowData.add(valueProcessor.getJdbcSqlValueString(jdbcDataValue));
        }
        return rowData;
    }

    private Map<String, String> extractRowDataAsMap(ResultSet resultSet, ValueProcessor valueProcessor,
                                                    Map<String, String> primaryKeyMap) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        Map<String, String> row = new HashMap<>(metaData.getColumnCount());
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            JDBCDataValue jdbcDataValue = new JDBCDataValue(resultSet, metaData, i, false);
            String columnName = metaData.getColumnName(i);
            String jdbcValueString = valueProcessor.getJdbcSqlValueString(jdbcDataValue);
            if (primaryKeyMap.containsKey(columnName)) {
                primaryKeyMap.put(columnName, jdbcValueString);
            } else {
                row.put(columnName, jdbcValueString);
            }
        }
        return row;
    }

    private Map<String, String> getPrimaryKeyMap(Connection connection, String databaseName,
                                                 String schemaName, String tableName) throws SQLException {
        Map<String, String> primaryKeyMap = new HashMap<>();
        try (ResultSet primaryKeys = connection.getMetaData().getPrimaryKeys(databaseName, schemaName, tableName)) {
            while (primaryKeys.next()) {
                primaryKeyMap.put(primaryKeys.getString("COLUMN_NAME"), "");
            }
        }
        return primaryKeyMap;
    }

    private void writeSqlList(PrintWriter writer, List<String> sqlList) {
        sqlList.forEach(writer::println);
        sqlList.clear();
    }


}

package ai.chat2db.server.web.api.controller.rdb.data.json;

import ai.chat2db.server.domain.api.enums.ExportFileSuffix;
import ai.chat2db.server.domain.api.param.datasource.DatabaseExportDataParam;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.web.api.controller.rdb.data.BaseDataExporter;
import ai.chat2db.server.web.api.controller.rdb.data.task.TaskManager;
import ai.chat2db.spi.ValueProcessor;
import ai.chat2db.spi.model.JDBCDataValue;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.SQLExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: zgq
 * @date: 2024年06月04日 10:33
 */
@Component("jsonExporter")
@Slf4j
public class JsonDataExporter extends BaseDataExporter {

    public JsonDataExporter() {
        this.suffix = ExportFileSuffix.JSON.getSuffix();
        this.contentType = "application/json";
    }


    @Override
    protected void singleExport(Connection connection, DatabaseExportDataParam databaseExportDataParam, File file) {
        String tableName = databaseExportDataParam.getTableNames().get(0);
        String querySql = getQuerySql(databaseExportDataParam, tableName);
        log.info("开始导出：{}表数据，导出类型：json", tableName);
        try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8);) {
            writeJsonData(connection, querySql, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected ByteArrayOutputStream multiExport(Connection connection, DatabaseExportDataParam databaseExportDataParam, String tableName) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        log.info("开始导出：{}表数据，导出类型：json", tableName);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8))) {
            String querySql = getQuerySql(databaseExportDataParam, tableName);
            writeJsonData(connection, querySql, writer);
        }
        return byteArrayOutputStream;
    }

    private void writeJsonData(Connection connection, String querySql, PrintWriter writer) {
        SQLExecutor.getInstance().execute(connection, querySql, BATCH_SIZE, resultSet -> {
            List<Map<String, Object>> dataBatch = new ArrayList<>();
            ResultSetMetaData metaData = resultSet.getMetaData();
            ValueProcessor valueProcessor = Chat2DBContext.getMetaData().getValueProcessor();
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

            writer.println("[");
            boolean firstBatch = true;
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    row.put(metaData.getColumnName(i), valueProcessor.getJdbcValue(new JDBCDataValue(resultSet, metaData, i, false)));
                }
                dataBatch.add(row);

                if (dataBatch.size() >= BATCH_SIZE || resultSet.isLast()) {
                    if (!firstBatch) {
                        writer.println(",");
                    }
                    writeBatch(writer, objectMapper, dataBatch);
                    firstBatch = false;
                }
            }
            writer.println("]");
        });
        TaskManager.increaseCurrent();
    }

    private void writeBatch(PrintWriter writer, ObjectMapper objectMapper, List<Map<String, Object>> dataBatch) {
        try {
            String jsonBatch = objectMapper.writeValueAsString(dataBatch);
            writer.println(jsonBatch.substring(1, jsonBatch.length() - 1));
            writer.flush();
            dataBatch.clear();
        } catch (JsonProcessingException e) {
            throw new BusinessException("data.export.json.error", null, e);
        }
    }

    private String getQuerySql(DatabaseExportDataParam databaseExportDataParam, String tableName) {
        String databaseName = databaseExportDataParam.getDatabaseName();
        String schemaName = databaseExportDataParam.getSchemaName();
        return Chat2DBContext.getSqlBuilder().buildTableQuerySql(databaseName, schemaName, tableName);
    }


}

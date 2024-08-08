package ai.chat2db.server.web.api.controller.task.biz;

import ai.chat2db.server.domain.api.enums.*;
import ai.chat2db.server.domain.api.param.*;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.domain.api.service.TaskService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.tools.common.util.EasyCollectionUtils;
import ai.chat2db.server.web.api.controller.rdb.RdbDmlExportController;
import ai.chat2db.server.web.api.controller.rdb.converter.RdbWebConverter;
import ai.chat2db.server.web.api.controller.rdb.doc.DatabaseExportService;
import ai.chat2db.server.web.api.controller.rdb.doc.conf.ExportOptions;
import ai.chat2db.server.web.api.controller.rdb.factory.ExportServiceFactory;
import ai.chat2db.server.web.api.controller.rdb.request.DataExportRequest;
import ai.chat2db.server.web.api.controller.rdb.vo.TableVO;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.ValueProcessor;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.JdbcUtils;
import ai.chat2db.spi.util.SqlUtils;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.visitor.VisitorFeature;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class TaskBizService {

    /**
     * Format insert statement
     */
    private static final SQLUtils.FormatOption INSERT_FORMAT_OPTION = new SQLUtils.FormatOption(true, false);

    static {
        INSERT_FORMAT_OPTION.config(VisitorFeature.OutputNameQuote, true);
    }


    @Autowired
    private TaskService taskService;


    @Autowired
    private TableService tableService;

    @Autowired
    private RdbWebConverter rdbWebConverter;

    public DataResult<Long> exportResultData(DataExportRequest request) {
        String sql = ExportSizeEnum.CURRENT_PAGE.getCode().equals(request.getExportSize()) ? request.getSql() : request.getOriginalSql();
        Assert.notBlank(sql, "dataSource.sqlEmpty");
        DbType dbType = JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType());
        String tableName = getTableName(request, sql, dbType);
        File file = createTempFile(tableName, request.getExportType());
        DataResult<Long> dataResult = createTask(tableName, request.getDatabaseName(), request.getSchemaName(), request.getDataSourceId(), tableName);

        LoginUser loginUser = ContextUtils.getLoginUser();
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();
        CompletableFuture.runAsync(() -> {
            buildContext(loginUser, connectInfo);
            doExport(sql, file, dbType, tableName, request.getExportType());
        }).whenComplete((aVoid, throwable) -> {
            updateStatus(dataResult.getData(), file, throwable);
            removeContext();
        });
        return dataResult;
    }

    public DataResult<Long> exportSchemaDoc(DataExportRequest request) {
        File file = createTempFile(request.getDatabaseName(), request.getExportType());
        DataResult<Long> dataResult = createTask(null, request.getDatabaseName(), request.getSchemaName(), request.getDataSourceId(), "schema_doc");
        LoginUser loginUser = ContextUtils.getLoginUser();
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();
        CompletableFuture.runAsync(() -> {
            buildContext(loginUser, connectInfo);
            doExportDoc(request, file);
        }).whenComplete((aVoid, throwable) -> {
            updateStatus(dataResult.getData(), file, throwable);
            removeContext();
        });
        return dataResult;
    }

    private void doExportDoc(DataExportRequest request, File file) {
        try {
            TablePageQueryParam queryParam = rdbWebConverter.tablePageRequest2param(request);
            queryParam.setPageNo(1);
            queryParam.setPageSize(Integer.MAX_VALUE);
            TableSelector tableSelector = new TableSelector();
            tableSelector.setColumnList(true);
            tableSelector.setIndexList(true);
            PageResult<Table> tableDTOPageResult = tableService.pageQuery(queryParam, tableSelector);
            List<TableVO> tableVOS = rdbWebConverter.tableDto2vo(tableDTOPageResult.getData());
            TableQueryParam param = rdbWebConverter.tableRequest2param(request);
            for (TableVO tableVO : tableVOS) {
                param.setTableName(tableVO.getName());
                tableVO.setColumnList(tableService.queryColumns(param));
                tableVO.setIndexList(tableService.queryIndexes(param));
            }
            Class<?> targetClass = ExportServiceFactory.get(request.getExportType());
            Constructor<?> constructor = targetClass.getDeclaredConstructor();
            DatabaseExportService databaseExportService = (DatabaseExportService) constructor.newInstance();
            // Set up data collection
            databaseExportService.setExportList(tableVOS);
            databaseExportService.generate(request.getDatabaseName(), new FileOutputStream(file), new ExportOptions());
        } catch (Exception e) {
            log.error("export error", e);
            throw new BusinessException("dataSource.exportError");
        }
    }


    private void removeContext() {
        Dbutils.removeSession();
        ContextUtils.removeContext();
        Chat2DBContext.removeContext();
    }

    private void buildContext(LoginUser loginUser, ConnectInfo connectInfo) {
        ContextUtils.setContext(Context.builder()
                .loginUser(loginUser)
                .build());
        Dbutils.setSession();
        Chat2DBContext.putContext(connectInfo);
    }

    private DataResult<Long> createTask(String tableName, String databaseName, String schemaName, Long datasourceId, String taskName) {
        TaskCreateParam param = new TaskCreateParam();
        param.setTaskName("export_" + taskName);
        param.setTaskType(TaskTypeEnum.DOWNLOAD_TABLE_DATA.name());
        param.setDatabaseName(databaseName);
        param.setSchemaName(schemaName);
        param.setTableName(tableName);
        param.setDataSourceId(datasourceId);
        param.setUserId(ContextUtils.getUserId());
        param.setTaskProgress("0.1");
        return taskService.create(param);
    }

    private void updateStatus(Long id, File file, Throwable throwable) {
        TaskUpdateParam updateParam = new TaskUpdateParam();
        updateParam.setId(id);
        updateParam.setTaskProgress("1");
        updateParam.setDownloadUrl(file.getAbsolutePath());
        if (throwable != null) {
            log.error("export error", throwable);
            updateParam.setTaskStatus(TaskStatusEnum.ERROR.name());
        } else {
            updateParam.setTaskStatus(TaskStatusEnum.FINISH.name());
        }
        taskService.updateStatus(updateParam);
    }

    private void doExport(String sql, File file, DbType dbType, String tableName, String exportType) {
        try {
            if (ExportTypeEnum.CSV.getCode().equals(exportType)) {
                doExportCsv(sql, file);
            } else {
                doExportInsert(sql, file, dbType, tableName);
            }
        } catch (Exception e) {
            log.error("export error", e);
            throw new BusinessException("dataSource.exportError");
        }
    }


    private File createTempFile(String tableName, String exportType) {
        String fileName = URLEncoder.encode(
                        tableName + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER),
                        StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        if (ExportTypeEnum.CSV.getCode().equals(exportType)) {
            return FileUtil.createTempFile(fileName, ".csv", true);
        } else if (ExportTypeEnum.INSERT.getCode().equals(exportType)) {
            return FileUtil.createTempFile(fileName, ".sql", true);
        } else if (ExportTypeEnum.EXCEL.getCode().equals(exportType)) {
            return FileUtil.createTempFile(fileName, ExportFileSuffix.EXCEL.getSuffix(), true);
        } else if (ExportTypeEnum.MARKDOWN.getCode().equals(exportType)) {
            return FileUtil.createTempFile(fileName, ExportFileSuffix.MARKDOWN.getSuffix(), true);
        } else if (ExportTypeEnum.WORD.getCode().equals(exportType)) {
            return FileUtil.createTempFile(fileName, ExportFileSuffix.WORD.getSuffix(), true);
        } else if (ExportTypeEnum.PDF.getCode().equals(exportType)) {
            return FileUtil.createTempFile(fileName, ExportFileSuffix.PDF.getSuffix(), true);
        } else if (ExportTypeEnum.HTML.getCode().equals(exportType)) {
            return FileUtil.createTempFile(fileName, ExportFileSuffix.HTML.getSuffix(), true);
        }
        return FileUtil.createTempFile(fileName, ".txt", true);
    }

    private String getTableName(DataExportRequest request, String sql, DbType dbType) {
        String tableName = null;
        if (dbType != null) {
            SQLStatement sqlStatement = SQLUtils.parseSingleStatement(sql, dbType);
            if (!(sqlStatement instanceof SQLSelectStatement)) {
                throw new BusinessException("dataSource.sqlAnalysisError");
            }
            tableName = SqlUtils.getTableName(sql, dbType);
        } else {
            tableName = StringUtils.join(Lists.newArrayList(request.getDatabaseName(), request.getSchemaName()), "_");
        }
        return tableName;
    }

    private void doExportCsv(String sql, File file) {
        RdbDmlExportController.ExcelWrapper excelWrapper = new RdbDmlExportController.ExcelWrapper();
        try {
            ExcelWriterBuilder excelWriterBuilder = EasyExcel.write(file)
                    .charset(StandardCharsets.UTF_8)
                    .excelType(ExcelTypeEnum.CSV);
            excelWrapper.setExcelWriterBuilder(excelWriterBuilder);
            ValueProcessor valueProcessor = Chat2DBContext.getMetaData().getValueProcessor();
            SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql, headerList -> {
                excelWriterBuilder.head(
                        EasyCollectionUtils.toList(headerList, header -> Lists.newArrayList(header.getName())));
                excelWrapper.setExcelWriter(excelWriterBuilder.build());
                excelWrapper.setWriteSheet(EasyExcel.writerSheet(0).build());
            }, dataList -> {
                List<List<String>> writeDataList = Lists.newArrayList();
                writeDataList.add(dataList);
                excelWrapper.getExcelWriter().write(writeDataList, excelWrapper.getWriteSheet());
            }, jdbcDataValue -> valueProcessor.getJdbcValue(jdbcDataValue),false);
        } finally {
            if (excelWrapper.getExcelWriter() != null) {
                excelWrapper.getExcelWriter().finish();
            }
        }
    }

    private void doExportInsert(String sql, File file, DbType dbType,
                                String tableName)
            throws IOException {
        try (PrintWriter printWriter = new PrintWriter(file, StandardCharsets.UTF_8.name())) {
            RdbDmlExportController.InsertWrapper insertWrapper = new RdbDmlExportController.InsertWrapper();
            ValueProcessor valueProcessor = Chat2DBContext.getMetaData().getValueProcessor();
            SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
            String databaseName = Chat2DBContext.getConnectInfo().getDatabaseName();
            String schemaName = Chat2DBContext.getConnectInfo().getSchemaName();
            List<String> headerColumns = Lists.newArrayList();
            SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql,
                    headerList -> {
                        headerList.forEach(header -> headerColumns.add(header.getName()));
                    }
                    , dataList -> {
                        String insertSql = sqlBuilder.buildSingleInsertSql(databaseName, schemaName, tableName, headerColumns, dataList);
                        printWriter.println(insertSql + ";");
                    }, jdbcDataValue -> valueProcessor.getJdbcSqlValueString(jdbcDataValue), false);
        }
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsertWrapper {
        private List<SQLIdentifierExpr> headerList;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExcelWrapper {
        private ExcelWriterBuilder excelWriterBuilder;
        private ExcelWriter excelWriter;
        private WriteSheet writeSheet;
    }
}

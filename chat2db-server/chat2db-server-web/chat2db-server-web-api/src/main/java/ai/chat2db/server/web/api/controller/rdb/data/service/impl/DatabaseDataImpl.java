package ai.chat2db.server.web.api.controller.rdb.data.service.impl;

import ai.chat2db.server.domain.api.enums.TaskStatusEnum;
import ai.chat2db.server.domain.api.enums.TaskTypeEnum;
import ai.chat2db.server.domain.api.param.TaskCreateParam;
import ai.chat2db.server.domain.api.param.TaskUpdateParam;
import ai.chat2db.server.domain.api.param.datasource.DatabaseExportDataParam;
import ai.chat2db.server.domain.api.service.TaskService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.web.api.controller.rdb.data.factory.DataExportFactory;
import ai.chat2db.server.web.api.controller.rdb.data.factory.DataImportFactory;
import ai.chat2db.server.web.api.controller.rdb.data.service.DatabaseDataService;
import ai.chat2db.server.web.api.controller.rdb.data.task.TaskManager;
import ai.chat2db.server.web.api.controller.rdb.data.task.TaskState;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author: zgq
 * @date: 2024年06月08日 10:32
 */
@Service
@Slf4j
public class DatabaseDataImpl implements DatabaseDataService {

    public static final String EXPORT_DATA_TASK_TEMPLATE = "export_%s_data";
    public static final String IMPORT_DATA_TASK_TEMPLATE = "import_%s_data";
    @Autowired
    private DataExportFactory dataExportFactory;
    @Autowired
    private DataImportFactory dataImportFactory;
    @Autowired
    private TaskService taskService;

    @Override
    public DataResult<Long> doExportAsync(DatabaseExportDataParam databaseExportDataParam) {
        List<String> tableNames = databaseExportDataParam.getTableNames();
        String databaseName = databaseExportDataParam.getDatabaseName();
        String schemaName = databaseExportDataParam.getSchemaName();
        Long dataSourceId = databaseExportDataParam.getDataSourceId();
        String taskName = buildTaskName(tableNames, databaseName, schemaName);
        String fileName = URLEncoder.encode(
                taskName + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER),
                StandardCharsets.UTF_8);
        String suffix = ".";
        int size = tableNames.size();
        if (size > 1) {
            suffix += "zip";
        } else {
            suffix += databaseExportDataParam.getExportType().toLowerCase();
        }
        File file = FileUtil.createTempFile(fileName, suffix, true);
        file.deleteOnExit();
        LoginUser loginUser = ContextUtils.getLoginUser();
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();
        DataResult<Long> dataResult = createTask(tableNames.get(0), databaseName, schemaName, dataSourceId, taskName);
        Long taskId = dataResult.getData();
        CompletableFuture.runAsync(() -> {
            buildContext(loginUser, connectInfo);
            TaskManager.addTask(taskId, TaskState.builder().state(TaskStatusEnum.PROCESSING.name()).total(size)
                    .current(0).build());
            try {
                dataExportFactory.getExporter(databaseExportDataParam.getExportType()).doExport(databaseExportDataParam, file);
            } catch (IOException | SQLException e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((v, ex) -> {
            updateStatus(taskId, file, ex);
            removeContext();
            TaskManager.removeTaskId();
        });
        return dataResult;

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

    private void removeContext() {
        Dbutils.removeSession();
        ContextUtils.removeContext();
        Chat2DBContext.removeContext();
    }

    private DataResult<Long> createTask(String tableName, String databaseName, String schemaName, Long datasourceId, String taskName) {
        TaskCreateParam param = new TaskCreateParam();
        param.setTaskName(taskName);
        param.setTaskType(TaskTypeEnum.DOWNLOAD_TABLE_DATA.name());
        param.setDatabaseName(databaseName);
        param.setSchemaName(schemaName);
        param.setTableName(tableName);
        param.setDataSourceId(datasourceId);
        param.setUserId(ContextUtils.getUserId());
        param.setTaskProgress("0.1");
        return taskService.create(param);
    }

    private void buildContext(LoginUser loginUser, ConnectInfo connectInfo) {
        ContextUtils.setContext(Context.builder()
                                        .loginUser(loginUser)
                                        .build());
        Dbutils.setSession();
        Chat2DBContext.putContext(connectInfo);
    }

    private String buildTaskName(List<String> tableNames, String databaseName, String schemaName) {
        StringBuilder taskNameBuilder = new StringBuilder();
        if (StringUtils.isNotBlank(databaseName)) {
            taskNameBuilder.append(databaseName).append("_");
        }
        if (StringUtils.isNotBlank(schemaName)) {
            taskNameBuilder.append(schemaName).append("_");
        }
        if (tableNames.size() == 1) {
            taskNameBuilder.append(StringUtils.join(tableNames, "_"));
        }
        return String.format(EXPORT_DATA_TASK_TEMPLATE, taskNameBuilder);
    }

}

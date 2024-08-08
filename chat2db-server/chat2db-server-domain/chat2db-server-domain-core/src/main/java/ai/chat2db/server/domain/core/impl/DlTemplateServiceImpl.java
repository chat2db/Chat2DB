package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.param.*;
import ai.chat2db.server.domain.api.param.operation.OperationLogCreateParam;
import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.server.domain.api.service.OperationLogService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.domain.core.converter.CommandConverter;
import ai.chat2db.server.domain.core.util.MetaNameUtils;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.common.util.EasyCollectionUtils;
import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.util.JdbcUtils;
import ai.chat2db.spi.util.SqlUtils;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.PagerUtils;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author moji
 * @version DataSourceCoreServiceImpl.java, v 0.1 September 23, 2022 15:51 moji Exp $
 * @date 2022/09/23
 */
@Slf4j
@Service
public class DlTemplateServiceImpl implements DlTemplateService {

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private TableService tableService;

    @Autowired
    private CommandConverter commandConverter;

    @Override
    public ListResult<ExecuteResult> execute(DlExecuteParam param) {
        CommandExecutor executor = Chat2DBContext.getMetaData().getCommandExecutor();
        Command command = commandConverter.param2model(param);
        List<ExecuteResult> results = executor.execute(command);
        return reBuildHeader(results,param.getSchemaName(),param.getDatabaseName());
    }

    private ListResult<ExecuteResult> reBuildHeader(List<ExecuteResult> results,String schemaName,String databaseName){
        ListResult<ExecuteResult> listResult = ListResult.of(results);
        for (ExecuteResult executeResult : results) {
            List<Header> headers = executeResult.getHeaderList();
            if (executeResult.getSuccess() && executeResult.isCanEdit() && CollectionUtils.isNotEmpty(headers)) {
                headers = setColumnInfo(headers, executeResult.getTableName(), schemaName, databaseName);
                executeResult.setHeaderList(headers);
            }
            if (!executeResult.getSuccess()) {
                listResult.setSuccess(false);
                listResult.errorCode(executeResult.getDescription());
                listResult.setErrorMessage(executeResult.getMessage());
            }
            addOperationLog(executeResult);
        }
        return listResult;
    }

    @Override
    public ListResult<ExecuteResult> executeSelectTable(DlExecuteParam param) {
        Command command = commandConverter.param2model(param);
        List<ExecuteResult> results = Chat2DBContext.getMetaData().getCommandExecutor().executeSelectTable(command);
        return reBuildHeader(results,param.getSchemaName(),param.getDatabaseName());
    }

    @Override
    public DataResult<ExecuteResult> executeUpdate(DlExecuteParam param) {
        CommandExecutor executor = Chat2DBContext.getMetaData().getCommandExecutor();
        DataResult<ExecuteResult> dataResult = new DataResult<>();
        dataResult.setSuccess(true);
        //RemoveSpecialGO(param);
        DbType dbType =
                JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType());
        List<String> sqlList = SqlUtils.parse(param.getSql(), dbType,true);
        Connection connection = Chat2DBContext.getConnection();
        try {
//            connection.setAutoCommit(false);
            for (String originalSql : sqlList) {
                ExecuteResult executeResult = executor.executeUpdate(originalSql, connection, 1);
                dataResult.setData(executeResult);
                addOperationLog(executeResult);
            }
//            connection.commit();
        } catch (Exception e) {
            log.error("executeUpdate error", e);
            dataResult.setSuccess(false);
            dataResult.setErrorCode("connection error");
            dataResult.setErrorMessage(e.getMessage());
        }
        return dataResult;
    }




    @Override
    public DataResult<Long> count(DlCountParam param) {
        if (StringUtils.isBlank(param.getSql())) {
            return DataResult.of(0L);
        }
        DbType dbType =
                JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType());
        String sql = param.getSql();
        // Parse sql pagination
        SQLStatement sqlStatement = SQLUtils.parseSingleStatement(sql, dbType);
        if (!(sqlStatement instanceof SQLSelectStatement)) {
            throw new BusinessException("dataSource.sqlAnalysisError");
        }
        sql = PagerUtils.count(sql, dbType);
        ExecuteResult executeResult;
        try {
            executeResult = Chat2DBContext.getMetaData().getCommandExecutor().execute(sql, Chat2DBContext.getConnection(), true, null, null);
        } catch (SQLException e) {
            log.warn("Execute sql: {} exception", sql, e);
            executeResult = ExecuteResult.builder()
                    .sql(sql)
                    .success(Boolean.FALSE)
                    .message(e.getMessage())
                    .build();
        }

        List<List<String>> dataList = executeResult.getDataList();
        if (CollectionUtils.isEmpty(dataList)) {
            return DataResult.of(0L);
        }
        String count = EasyCollectionUtils.stream(executeResult.getDataList())
                .findFirst()
                .orElse(Collections.emptyList())
                .stream()
                .findFirst()
                .orElse("0");
        return DataResult.of(Long.valueOf(count));
    }

    @Override
    public DataResult<String> updateSelectResult(UpdateSelectResultParam param) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        QueryResult queryResult = new QueryResult();
        BeanUtils.copyProperties(param, queryResult);
        String sql = sqlBuilder.buildSqlByQuery(queryResult);
        return DataResult.of(sql);
    }

    @Override
    public DataResult<String> getOrderBySql(OrderByParam param) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        String orderSql = sqlBuilder.buildOrderBySql(param.getOriginSql(), param.getOrderByList());
        return DataResult.of(orderSql);
    }


    private List<Header> setColumnInfo(List<Header> headers, String tableName, String schemaName, String databaseName) {
        try {
            TableQueryParam tableQueryParam = new TableQueryParam();
            tableQueryParam.setTableName(MetaNameUtils.getMetaName(tableName));
            tableQueryParam.setSchemaName(schemaName);
            tableQueryParam.setDatabaseName(databaseName);
            tableQueryParam.setRefresh(true);
            List<TableColumn> columns = tableService.queryColumns(tableQueryParam);
            if (CollectionUtils.isEmpty(columns)) {
                return headers;
            }
            Map<String, TableColumn> columnMap = columns.stream().collect(
                    Collectors.toMap(TableColumn::getName, tableColumn -> tableColumn));
            List<TableIndex> tableIndices = tableService.queryIndexes(tableQueryParam);
            if (!CollectionUtils.isEmpty(tableIndices)) {
                for (TableIndex tableIndex : tableIndices) {
                    if ("PRIMARY".equalsIgnoreCase(tableIndex.getType())) {
                        List<TableIndexColumn> columnList = tableIndex.getColumnList();
                        if (!CollectionUtils.isEmpty(columnList)) {
                            for (TableIndexColumn tableIndexColumn : columnList) {
                                TableColumn tableColumn = columnMap.get(tableIndexColumn.getColumnName());
                                if (tableColumn != null) {
                                    tableColumn.setPrimaryKey(true);
                                }
                            }
                        }
                    }
                }
            }
            for (Header header : headers) {
                TableColumn tableColumn = columnMap.get(header.getName());
                if (tableColumn != null) {
                    header.setPrimaryKey(tableColumn.getPrimaryKey());
                    header.setComment(tableColumn.getComment());
                    header.setDefaultValue(tableColumn.getDefaultValue());
                    header.setNullable(tableColumn.getNullable());
                    header.setColumnSize(tableColumn.getColumnSize());
                    header.setDecimalDigits(tableColumn.getDecimalDigits());
                }
            }

        } catch (Exception e) {
            log.error("setColumnInfo error:", e);
        }
        return headers;
    }


    private void addOperationLog(ExecuteResult executeResult) {
        if (executeResult == null) {
            return;
        }
        try {
            ConnectInfo connectInfo = Chat2DBContext.getConnectInfo();
            OperationLogCreateParam createParam = new OperationLogCreateParam();
            createParam.setDdl(executeResult.getSql());
            createParam.setStatus(executeResult.getSuccess() ? "success" : "fail");
            createParam.setDatabaseName(connectInfo.getDatabaseName());
            createParam.setDataSourceId(connectInfo.getDataSourceId());
            createParam.setSchemaName(connectInfo.getSchemaName());
            createParam.setUseTime(executeResult.getDuration());
            createParam.setType(connectInfo.getDbType());
            createParam.setOperationRows(
                    executeResult.getUpdateCount() != null ? Long.valueOf(executeResult.getUpdateCount()) : null);
            operationLogService.create(createParam);
        } catch (Exception e) {
            log.error("addOperationLog error:", e);
        }
    }
}

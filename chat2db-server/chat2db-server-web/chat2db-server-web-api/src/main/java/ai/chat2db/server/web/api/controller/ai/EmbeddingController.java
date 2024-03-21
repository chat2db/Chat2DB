package ai.chat2db.server.web.api.controller.ai;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.domain.api.model.Config;
import ai.chat2db.server.domain.api.param.ShowCreateTableParam;
import ai.chat2db.server.domain.api.param.TablePageQueryParam;
import ai.chat2db.server.domain.api.param.TableSelector;
import ai.chat2db.server.domain.api.param.TableVectorParam;
import ai.chat2db.server.domain.api.service.ConfigService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.tools.base.enums.WhiteListTypeEnum;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.ai.chat2db.client.Chat2dbAIClient;
import ai.chat2db.server.web.api.controller.ai.fastchat.embeddings.FastChatEmbeddingResponse;
import ai.chat2db.server.web.api.controller.ai.rest.client.RestAIClient;
import ai.chat2db.server.web.api.controller.rdb.converter.RdbWebConverter;
import ai.chat2db.server.web.api.controller.rdb.request.TableBriefQueryRequest;
import ai.chat2db.server.web.api.controller.rdb.request.TableMilvusQueryRequest;
import ai.chat2db.server.web.api.http.GatewayClientService;
import ai.chat2db.server.web.api.http.request.EsTableSchemaRequest;
import ai.chat2db.server.web.api.http.request.TableSchemaRequest;
import ai.chat2db.server.web.api.http.request.WhiteListRequest;
import ai.chat2db.server.web.api.util.ApplicationContextUtil;
import ai.chat2db.spi.model.Table;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author moji
 */
@RestController
@ConnectionInfoAspect
@RequestMapping("/api/ai/embedding")
@Slf4j
public class EmbeddingController extends ChatController {


    @Resource
    private GatewayClientService gatewayClientService;

    @Autowired
    private RdbWebConverter rdbWebConverter;

    @Autowired
    private TableService tableService;

    /**
     * check if in white list
     */
    @GetMapping("/white/check")
    public DataResult<Boolean> checkInWhite(WhiteListRequest request) {
        request.setWhiteType(WhiteListTypeEnum.VECTOR.getCode());
        if (StringUtils.isBlank(request.getApiKey())) {
            return DataResult.of(false);
        }
        try {
            DataResult<Boolean> res = gatewayClientService.checkInWhite(request);
        } catch (Exception ex) {
            log.error("checkInWhite error", ex);
        }
        return DataResult.of(false);
    }

    /**
     * save datasource embeddings
     *
     * @param request
     * @return
     * @throws IOException
     */
    @PostMapping("/datasource")
    @CrossOrigin
    public ActionResult embeddings(@Valid TableMilvusQueryRequest request)
        throws Exception {

        // query tables
        request.setPageNo(1);
        request.setPageSize(1000);
        TablePageQueryParam queryParam = rdbWebConverter.tablePageRequest2param(request);
        TableSelector tableSelector = new TableSelector();
        tableSelector.setColumnList(false);
        tableSelector.setIndexList(false);
        PageResult<Table> tableDTOPageResult = tableService.pageQuery(queryParam, tableSelector);

        List<Table> tables = tableDTOPageResult.getData();
        if (CollectionUtils.isEmpty(tables)) {
            return ActionResult.isSuccess();
        }

        String tableName = tables.get(0).getName();
        String tableSchema = queryTableDdl(tableName, request);

        if (StringUtils.isBlank(tableSchema)) {
            throw new ParamBusinessException("tableSchema is empty");
        }

        // save first table embedding
        TableSchemaRequest tableSchemaRequest = new TableSchemaRequest();
        tableSchemaRequest.setDataSourceId(request.getDataSourceId());
        tableSchemaRequest.setApiKey(request.getApikey());
        tableSchemaRequest.setDeleteBeforeInsert(true);
        tableSchemaRequest.setDataSourceSchema(request.getSchemaName());
        tableSchemaRequest.setDatabaseName(request.getDatabaseName());

        saveTableEmbedding(tableSchema, tableSchemaRequest);

        // save other table embedding
        tableSchemaRequest.setDeleteBeforeInsert(false);
        for (int i = 1; i < tables.size(); i++) {
            tableName = tables.get(i).getName();
            tableSchema = queryTableDdl(tableName, request);
            if (StringUtils.isBlank(tableSchema)) {
                continue;
            }
            saveTableEmbedding(tableSchema, tableSchemaRequest);
        }

        // query all the tables
        Long totalTableCount = tableDTOPageResult.getTotal();
        Integer pageSize = queryParam.getPageSize();
        if (pageSize < totalTableCount) {
            for (int i = 2; i < totalTableCount/pageSize + 1; i++) {
                queryParam.setPageNo(i);
                tableDTOPageResult = tableService.pageQuery(queryParam, tableSelector);
                tables = tableDTOPageResult.getData();
                for (Table table : tables) {
                    tableName = table.getName();
                    tableSchema = queryTableDdl(tableName, request);
                    if (StringUtils.isBlank(tableSchema)) {
                        continue;
                    }
                    saveTableEmbedding(tableSchema, tableSchemaRequest);
                }
            }
        }

        return ActionResult.isSuccess();
    }

    /**
     * save datasource schema
     *
     * @param request
     * @return
     * @throws IOException
     */
    @PostMapping("/datasource/es")
    @CrossOrigin
    public ActionResult es(@Valid EsTableSchemaRequest request)
            throws Exception {

        // query tables
        TablePageQueryParam queryParam = rdbWebConverter.schemaReq2page(request);
        TableSelector tableSelector = new TableSelector();
        tableSelector.setColumnList(false);
        tableSelector.setIndexList(false);
        queryParam.setPageNo(1);
        queryParam.setPageSize(1000);
        PageResult<Table> tableDTOPageResult = tableService.pageQuery(queryParam, tableSelector);

        List<Table> tables = tableDTOPageResult.getData();
        if (CollectionUtils.isEmpty(tables)) {
            return ActionResult.isSuccess();
        }
        String tableName = tables.get(0).getName();
        String tableSchema = queryTableDdlByEs(tableName, request);
        request.setTableName(tableName);
        request.setTableSchemaContent(tableSchema);

        if (StringUtils.isBlank(tableSchema)) {
            throw new ParamBusinessException("tableSchema is empty");
        }

        // save first table embedding
        request.setDeleteBeforeInsert(true);
        saveTableEs(request);

        // save other table embedding
        request.setDeleteBeforeInsert(false);
        for (int i = 1; i < tables.size(); i++) {
            tableName = tables.get(i).getName();
            tableSchema = queryTableDdlByEs(tableName, request);
            if (StringUtils.isBlank(tableSchema)) {
                continue;
            }
            request.setTableName(tableName);
            request.setTableSchemaContent(tableSchema);
            saveTableEs(request);
        }

        // query all the tables
        Long totalTableCount = tableDTOPageResult.getTotal();
        Integer pageSize = queryParam.getPageSize();
        if (pageSize < totalTableCount) {
            for (int i = 2; i < totalTableCount/pageSize + 1; i++) {
                queryParam.setPageNo(i);
                tableDTOPageResult = tableService.pageQuery(queryParam, tableSelector);
                tables = tableDTOPageResult.getData();
                for (Table table : tables) {
                    tableName = table.getName();
                    tableSchema = queryTableDdlByEs(tableName, request);
                    if (StringUtils.isBlank(tableSchema)) {
                        continue;
                    }
                    request.setTableName(tableName);
                    request.setTableSchemaContent(tableSchema);
                    saveTableEs(request);
                }
            }
        }

        return ActionResult.isSuccess();
    }

    /**
     * sync table vector
     *
     * @param param
     */
    public void syncTableVector(TableBriefQueryRequest param) throws Exception {
        TableVectorParam vectorParam = rdbWebConverter.param2param(param);
        if (Objects.isNull(vectorParam.getDataSourceId())) {
            return;
        }
        if (StringUtils.isBlank(vectorParam.getDatabase()) && StringUtils.isBlank(vectorParam.getSchema())) {
            return;
        }

        String apiKey = promptService.getApiKey();
        if (StringUtils.isBlank(apiKey)) {
            return;
        }

        TableMilvusQueryRequest request = rdbWebConverter.request2request(param);
        request.setApikey(apiKey);

        vectorParam.setApiKey(apiKey);
        DataResult<Boolean> result = tableService.checkTableVector(vectorParam);
        if (result.getData()) {
            return;
        }

        // check if in white list
        boolean res = gatewayClientService.checkInWhite(new WhiteListRequest(apiKey, WhiteListTypeEnum.VECTOR.getCode())).getData();
        if (!res) {
            return;
        }

        embeddings(request);

        tableService.saveTableVector(vectorParam);
    }

    /**
     * save table embedding
     *
     * @param tableSchema
     * @param tableSchemaRequest
     * @throws Exception
     */
    private void saveTableEmbedding(String tableSchema, TableSchemaRequest tableSchemaRequest) throws Exception{
        List<String> schemaList = Lists.newArrayList(tableSchema);
        tableSchemaRequest.setSchemaList(schemaList);

        List<List<BigDecimal>> contentVector = new ArrayList<>();
        for(String str : schemaList){
            // request embedding
            FastChatEmbeddingResponse response = promptService.distributeAIEmbedding(str);
            if(response == null){
                throw new ParamBusinessException();
            }
            contentVector.add(response.getData().get(0).getEmbedding());
        }
        if (CollectionUtils.isEmpty(contentVector)) {
            throw new ParamBusinessException();
        }
        tableSchemaRequest.setSchemaVector(contentVector);

        // save table embedding
        gatewayClientService.schemaVectorSave(tableSchemaRequest);
    }

    /**
     * sync table vector
     *
     * @param param
     */
    public void syncTableEs(TableBriefQueryRequest param) throws Exception {
        EsTableSchemaRequest esParam = rdbWebConverter.req2req(param);
        if (Objects.isNull(esParam.getDataSourceId())) {
            return;
        }
        if (StringUtils.isBlank(esParam.getDatabaseName()) && StringUtils.isBlank(esParam.getSchemaName())) {
            return;
        }

        String apiKey = promptService.getApiKey();
        if (StringUtils.isBlank(apiKey)) {
            return;
        }

        esParam.setApiKey(apiKey);
        es(esParam);
    }

    /**
     * save table schema
     *
     * @param tableSchemaRequest
     * @throws Exception
     */
    private void saveTableEs(EsTableSchemaRequest tableSchemaRequest) throws Exception{
        // save table es
        gatewayClientService.schemaEsSave(tableSchemaRequest);
    }

    /**
     * query table schema
     *
     * @param tableName
     * @param request
     * @return
     */
    private String queryTableDdl(String tableName, TableBriefQueryRequest request) {
        ShowCreateTableParam param = new ShowCreateTableParam();
        param.setTableName(tableName);
        param.setDataSourceId(request.getDataSourceId());
        param.setDatabaseName(request.getDatabaseName());
        param.setSchemaName(request.getSchemaName());
        DataResult<String> tableSchema = tableService.showCreateTable(param);
        return tableSchema.getData();
    }

    /**
     * query table schema
     *
     * @param tableName
     * @param request
     * @return
     */
    private String queryTableDdlByEs(String tableName, EsTableSchemaRequest request) {
        ShowCreateTableParam param = new ShowCreateTableParam();
        param.setTableName(tableName);
        param.setDataSourceId(request.getDataSourceId());
        param.setDatabaseName(request.getDatabaseName());
        param.setSchemaName(request.getSchemaName());
        DataResult<String> tableSchema = tableService.showCreateTable(param);
        return tableSchema.getData();
    }

}

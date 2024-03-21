package com.hejianjun;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TableSchemaService类用于处理表结构相关的操作。
 */
@Service
@AllArgsConstructor
public class TableSchemaService {

    private final ElasticsearchClient client;

    /**
     * 批量保存表结构。
     *
     * @param request 表结构请求对象
     * @return 保存成功后的每个文档的ID列表
     * @throws IOException IO异常
     */
    public List<String> saveSchemaBatch(TableSchemaRequest request) throws IOException {
        List<String> documentIds = new ArrayList<>();

        // 构建批量请求
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

        String indexName = request.getDataSourceId() + request.getDatabaseName() + request.getDataSourceSchema();

        for (int i = 0; i < request.getSchemaVector().size(); i++) {
            // 假设schemaVector和schemaList的长度相同，并且一一对应
            List<BigDecimal> vector = request.getSchemaVector().get(i);
            String schema = request.getSchemaList().get(i);

            // 创建文档内容，这里简化为Map，具体结构根据需求定义
            SchemaDocument document = new SchemaDocument(schema,vector);

            // 添加到批量请求
            bulkBuilder.operations(op -> op
                    .index(idx -> idx
                            .index(indexName)
                            .document(document)
                    )
            );
        }

        // 执行批量请求
        BulkResponse bulkResponse = client.bulk(bulkBuilder.build());

        // 收集文档ID
        for (BulkResponseItem item : bulkResponse.items()) {
            if (item.error()!=null) {
                throw new IOException("Error indexing document: " + item.error().reason());
            }
            documentIds.add(item.id());
        }

        return documentIds;
    }

    /**
     * 根据向量搜索表结构。
     *
     * @param request 表结构请求对象
     * @return 搜索结果列表
     * @throws IOException IO异常
     */
    public TableSchemaRequest searchByVector(TableSchemaRequest request) throws IOException {
        String indexName = request.getDataSourceId() + request.getDatabaseName() + request.getDataSourceSchema();
        List<BigDecimal> vector = request.getSchemaVector().get(0);
        // 假设schemaVector已转换为适合Elasticsearch的格式
        // 执行k-NN搜索
        SearchResponse<SchemaDocument> response = client.search(s -> s
                        .index(indexName)
                // 这里添加k-NN查询逻辑，具体实现根据实际需求
                , SchemaDocument.class
        );
        List<List<BigDecimal>> schemaVector = new ArrayList<>();
        List<String> schemaList = new ArrayList<>();
        List<Hit<SchemaDocument>> hits = response.hits().hits();
        for (Hit<SchemaDocument> hit: hits) {
            SchemaDocument document = hit.source();
            if(document!=null) {
                schemaVector.add(document.getVector());
                schemaList.add(document.getSchema());
            }
        }
        request.setSchemaVector(schemaVector);
        request.setSchemaList(schemaList);
        return request;
    }
}

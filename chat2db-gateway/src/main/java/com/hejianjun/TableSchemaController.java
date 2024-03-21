package com.hejianjun;

import co.elastic.clients.json.JsonData;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/client/milvus")
public class TableSchemaController {

    private final TableSchemaService service;


    /**
     * 保存表结构
     * @param request 表结构请求对象
     * @return 保存成功的文档ID
     */
    @PostMapping("/schema/save")
    public ResponseEntity<List<String>> saveSchema(@RequestBody TableSchemaRequest request) {
        try {
            List<String> documentId = service.saveSchemaBatch(request);
            return ResponseEntity.ok(documentId);
        } catch (IOException e) {
            log.error("保存表结构时发生错误", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 通过向量搜索表结构
     * @param request 表结构搜索请求
     * @return 搜索结果列表
     */
    @PostMapping("/schema/search")
    public ResponseEntity<TableSchemaRequest> searchByVector(@RequestBody TableSchemaRequest request) {
        try {
            TableSchemaRequest tableSchemaRequest = service.searchByVector(request);
            return ResponseEntity.ok(tableSchemaRequest);
        } catch (IOException e) {
            log.error("Error searching schema", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

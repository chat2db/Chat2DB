package com.hejianjun;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchClientConfig {

    String apiKey = "DVaOd3B6Rl*9sWUeTIHO";

    /**
     * 创建ElasticsearchClient实例
     *
     * @return ElasticsearchClient实例
     */
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        // 初始化低级客户端
        RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200))
                .setDefaultHeaders(new Header[]{
                        new BasicHeader("Authorization", "ApiKey " + apiKey)
                })
                .build();

        // 使用低级客户端创建传输层
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());

        // 创建ElasticsearchClient实例
        return new ElasticsearchClient(transport);
    }

}

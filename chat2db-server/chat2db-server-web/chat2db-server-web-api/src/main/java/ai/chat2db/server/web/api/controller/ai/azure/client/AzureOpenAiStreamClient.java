package ai.chat2db.server.web.api.controller.ai.azure.client;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.controller.ai.azure.interceptor.AzureHeaderAuthorizationInterceptor;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatCompletionsOptions;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatMessage;
import cn.hutool.http.ContentType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 自定义AI接口client
 *
 * @author moji
 */
@Slf4j
public class AzureOpenAiStreamClient {

    /**
     * apikey
     */
    @Getter
    @NotNull
    private String apiKey;

    /**
     * endpoint
     */
    @Getter
    @NotNull
    private String endpoint;

    /**
     * deployId
     */
    @Getter
    private String deployId;

    /**
     * okHttpClient
     */
    @Getter
    private OkHttpClient okHttpClient;


    /**
     * @param builder
     */
    private AzureOpenAiStreamClient(Builder builder) {
        this.apiKey = builder.apiKey;
        this.endpoint = builder.endpoint;
        this.deployId = builder.deployId;
        if (Objects.isNull(builder.okHttpClient)) {
            builder.okHttpClient = this.okHttpClient();
        }
        okHttpClient = builder.okHttpClient;
    }

    /**
     * okhttpclient
     */
    private OkHttpClient okHttpClient() {
        OkHttpClient okHttpClient = new OkHttpClient
            .Builder()
            .addInterceptor(new AzureHeaderAuthorizationInterceptor(this.apiKey))
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(50, TimeUnit.SECONDS)
            .readTimeout(50, TimeUnit.SECONDS)
            .build();
        return okHttpClient;
    }

    /**
     * 构造
     *
     * @return
     */
    public static AzureOpenAiStreamClient.Builder builder() {
        return new AzureOpenAiStreamClient.Builder();
    }

    public static final class Builder {
        private String apiKey;

        private String endpoint;

        private String deployId;

        /**
         * 自定义OkhttpClient
         */
        private OkHttpClient okHttpClient;

        public Builder() {
        }

        public AzureOpenAiStreamClient.Builder apiKey(String apiKeyValue) {
            this.apiKey = apiKeyValue;
            return this;
        }

        /**
         * @param endpointValue
         * @return
         */
        public AzureOpenAiStreamClient.Builder endpoint(String endpointValue) {
            this.endpoint = endpointValue;
            return this;
        }

        /**
         * @param deployIdValue
         * @return
         */
        public AzureOpenAiStreamClient.Builder deployId(String deployIdValue) {
            this.deployId = deployIdValue;
            return this;
        }

        public AzureOpenAiStreamClient.Builder okHttpClient(OkHttpClient val) {
            this.okHttpClient = val;
            return this;
        }

        public AzureOpenAiStreamClient build() {
            return new AzureOpenAiStreamClient(this);
        }

    }

    /**
     * 问答接口 stream 形式
     *
     * @param chatMessages
     * @param eventSourceListener
     */
    public void streamCompletions(AzureChatCompletionsOptions chatCompletionsOptions, EventSourceListener eventSourceListener) {
        if (Objects.isNull(eventSourceListener)) {
            log.error("param error：AzureEventSourceListener cannot be empty");
            throw new ParamBusinessException();
        }
        try {
            chatCompletionsOptions.setStream(true);
            chatCompletionsOptions.setModel(this.deployId);
            EventSource.Factory factory = EventSources.createFactory(this.okHttpClient);
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            String requestBody = mapper.writeValueAsString(chatCompletionsOptions);
            if (!endpoint.endsWith("/")) {
                endpoint = endpoint + "/";
            }
            String url = this.endpoint + "openai/deployments/"+ deployId + "/chat/completions?api-version=2024-02-15-preview";
            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(MediaType.parse(ContentType.JSON.getValue()), requestBody))
                .build();
            //创建事件
            EventSource eventSource = factory.newEventSource(request, eventSourceListener);
            log.info("finish invoking azure ai");
        } catch (Exception e) {
            log.error("azure ai error", e);
            eventSourceListener.onFailure(null, e, null);
            throw new ParamBusinessException();
        }
    }

}

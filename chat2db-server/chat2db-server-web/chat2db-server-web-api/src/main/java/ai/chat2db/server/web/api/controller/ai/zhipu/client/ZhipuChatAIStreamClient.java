package ai.chat2db.server.web.api.controller.ai.zhipu.client;

import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatMessage;
import ai.chat2db.server.web.api.controller.ai.zhipu.interceptor.ZhipuChatHeaderAuthorizationInterceptor;
import ai.chat2db.server.web.api.controller.ai.zhipu.model.ZhipuChatCompletionsOptions;
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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Zhipu Chat Aligned Client
 *
 * @author moji
 */
@Slf4j
public class ZhipuChatAIStreamClient {

    /**
     * apikey
     */
    @Getter
    @NotNull
    private String apiKey;

    @Getter
    private String key;

    @Getter
    private String secret;

    /**
     * apiHost
     */
    @Getter
    @NotNull
    private String apiHost;

    /**
     * model
     */
    @Getter
    private String model;

    /**
     * embeddingModel
     */
    @Getter
    private String embeddingModel;

    /**
     * okHttpClient
     */
    @Getter
    private OkHttpClient okHttpClient;


    /**
     * @param builder
     */
    private ZhipuChatAIStreamClient(Builder builder) {
        this.apiKey = builder.apiKey;
        this.key = builder.key;
        this.secret = builder.secret;
        this.apiHost = builder.apiHost;
        this.model = builder.model;
        this.embeddingModel = builder.embeddingModel;
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
            .addInterceptor(new ZhipuChatHeaderAuthorizationInterceptor(this.key, this.secret))
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(50, TimeUnit.SECONDS)
            .readTimeout(50, TimeUnit.SECONDS)
            .build();
        return okHttpClient;
    }

    /**
     * structure
     *
     * @return
     */
    public static ZhipuChatAIStreamClient.Builder builder() {
        return new ZhipuChatAIStreamClient.Builder();
    }

    /**
     * builder
     */
    public static final class Builder {
        private String apiKey;

        private String key;

        private String secret;

        private String apiHost;

        private String model;

        private String embeddingModel;

        /**
         * OkhttpClient
         */
        private OkHttpClient okHttpClient;

        public Builder() {
        }

        public ZhipuChatAIStreamClient.Builder apiKey(String apiKeyValue) {
            this.apiKey = apiKeyValue;
            String[] arrStr = apiKey.split("\\.");
            if (arrStr.length != 2) {
                throw new RuntimeException("invalid apiSecretKey");
            }
            this.key = arrStr[0];
            this.secret = arrStr[1];
            return this;
        }

        /**
         * @param apiHostValue
         * @return
         */
        public ZhipuChatAIStreamClient.Builder apiHost(String apiHostValue) {
            this.apiHost = apiHostValue;
            return this;
        }

        /**
         * @param modelValue
         * @return
         */
        public ZhipuChatAIStreamClient.Builder model(String modelValue) {
            this.model = modelValue;
            return this;
        }

        public ZhipuChatAIStreamClient.Builder embeddingModel(String embeddingModelValue) {
            this.embeddingModel = embeddingModelValue;
            return this;
        }

        public ZhipuChatAIStreamClient.Builder okHttpClient(OkHttpClient val) {
            this.okHttpClient = val;
            return this;
        }

        public ZhipuChatAIStreamClient build() {
            return new ZhipuChatAIStreamClient(this);
        }

    }

    /**
     * Q&A interface stream form
     *
     * @param chatMessages
     * @param eventSourceListener
     */
    public void streamCompletions(List<FastChatMessage> chatMessages, EventSourceListener eventSourceListener) {
        if (CollectionUtils.isEmpty(chatMessages)) {
            log.error("param error：Zhipu Chat Prompt cannot be empty");
            throw new ParamBusinessException("prompt");
        }
        if (Objects.isNull(eventSourceListener)) {
            log.error("param error：Zhipu ChatEventSourceListener cannot be empty");
            throw new ParamBusinessException();
        }
        log.info("Zhipu Chat AI, prompt:{}", chatMessages.get(chatMessages.size() - 1).getContent());
        try {
            // It is recommended to check the demo package code directly. The update here may not be timely.
            ZhipuChatCompletionsOptions completionsOptions = new ZhipuChatCompletionsOptions();
            completionsOptions.setPrompt(chatMessages);
            completionsOptions.setModel(this.model);
            String requestId = String.valueOf(System.currentTimeMillis());
            completionsOptions.setRequestId(requestId);
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            String requestBody = mapper.writeValueAsString(completionsOptions);
            log.info("使用的model:{}", this.model);
            EventSource.Factory factory = EventSources.createFactory(this.okHttpClient);
            Request request = new Request.Builder()
                .url(apiHost)
                .post(RequestBody.create(MediaType.parse(ContentType.JSON.getValue()), requestBody))
                .build();
            //Create event
            EventSource eventSource = factory.newEventSource(request, eventSourceListener);
            log.info("finish invoking zhipu chat ai");
        } catch (Exception e) {
            log.error("fast chat ai error", e);
            eventSourceListener.onFailure(null, e, null);
            throw new ParamBusinessException();
        }
    }

}

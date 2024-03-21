package ai.chat2db.server.web.api.controller.ai.chat2db.client;

import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.controller.ai.chat2db.interceptor.Chat2dbHeaderAuthorizationInterceptor;
import ai.chat2db.server.web.api.controller.ai.fastchat.client.FastChatOpenAiApi;
import ai.chat2db.server.web.api.controller.ai.fastchat.embeddings.FastChatEmbedding;
import ai.chat2db.server.web.api.controller.ai.fastchat.embeddings.FastChatEmbeddingResponse;
import cn.hutool.http.ContentType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unfbx.chatgpt.entity.chat.ChatCompletion;
import com.unfbx.chatgpt.entity.chat.Message;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Fast Chat Aligned Client
 *
 * @author moji
 */
@Slf4j
public class Chat2DBAIStreamClient {

    /**
     * apikey
     */
    @Getter
    @NotNull
    private String apiKey;

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

    @Getter
    private FastChatOpenAiApi fastChatOpenAiApi;


    /**
     * @param builder
     */
    private Chat2DBAIStreamClient(Builder builder) {
        this.apiKey = builder.apiKey;
        this.apiHost = builder.apiHost;
        if (!apiHost.endsWith("/")){
            apiHost = apiHost + "/";
        }
        this.model = builder.model;
        this.embeddingModel = builder.embeddingModel;
        if (Objects.isNull(builder.okHttpClient)) {
            builder.okHttpClient = this.okHttpClient();
        }
        okHttpClient = builder.okHttpClient;
        this.fastChatOpenAiApi = new Retrofit.Builder()
                .baseUrl(apiHost)
                .client(okHttpClient)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(JacksonConverterFactory.create())
                .build().create(FastChatOpenAiApi.class);
    }

    /**
     * okhttpclient
     */
    private OkHttpClient okHttpClient() {
        OkHttpClient okHttpClient = new OkHttpClient
            .Builder()
            .addInterceptor(new Chat2dbHeaderAuthorizationInterceptor(this.apiKey, this.model))
            .connectTimeout(50, TimeUnit.SECONDS)
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
    public static Chat2DBAIStreamClient.Builder builder() {
        return new Chat2DBAIStreamClient.Builder();
    }

    /**
     * builder
     */
    public static final class Builder {
        private String apiKey;

        private String apiHost;

        private String model;

        private String embeddingModel;

        /**
         * OkhttpClient
         */
        private OkHttpClient okHttpClient;

        public Builder() {
        }

        public Chat2DBAIStreamClient.Builder apiKey(String apiKeyValue) {
            this.apiKey = apiKeyValue;
            return this;
        }

        /**
         * @param apiHostValue
         * @return
         */
        public Chat2DBAIStreamClient.Builder apiHost(String apiHostValue) {
            this.apiHost = apiHostValue;
            return this;
        }

        /**
         * @param modelValue
         * @return
         */
        public Chat2DBAIStreamClient.Builder model(String modelValue) {
            this.model = modelValue;
            return this;
        }

        public Chat2DBAIStreamClient.Builder embeddingModel(String embeddingModelValue) {
            this.embeddingModel = embeddingModelValue;
            return this;
        }

        public Chat2DBAIStreamClient.Builder okHttpClient(OkHttpClient val) {
            this.okHttpClient = val;
            return this;
        }

        public Chat2DBAIStreamClient build() {
            return new Chat2DBAIStreamClient(this);
        }

    }

    /**
     * 问答接口 stream 形式
     *
     * @param chatMessages
     * @param eventSourceListener
     */
    public void streamCompletions(List<Message> chatMessages, EventSourceListener eventSourceListener) {
        if (CollectionUtils.isEmpty(chatMessages)) {
            log.error("param error:Chat Prompt cannot be empty");
            throw new ParamBusinessException("prompt");
        }
        if (Objects.isNull(eventSourceListener)) {
            log.error("param error：ChatEventSourceListener cannot be empty");
            throw new ParamBusinessException();
        }
        try {
            ChatCompletion chatCompletion = ChatCompletion.builder()
                    .messages(chatMessages)
                    .stream(true)
                    .build();

            EventSource.Factory factory = EventSources.createFactory(this.okHttpClient);
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            String requestBody = mapper.writeValueAsString(chatCompletion);
            Request request = new Request.Builder()
                    .url(this.apiHost + "v1/chat/completions")
                    .post(RequestBody.create(MediaType.parse(ContentType.JSON.getValue()), requestBody))
                    .build();
            //创建事件
            EventSource eventSource = factory.newEventSource(request, eventSourceListener);
            log.info("finish invoking chat ai");
        } catch (Exception e) {
            log.error("chat ai error", e);
            eventSourceListener.onFailure(null, e, null);
            throw new ParamBusinessException();
        }
    }

    /**
     * Creates an embedding vector representing the input text.
     *
     * @param input
     * @return EmbeddingResponse
     */
    public FastChatEmbeddingResponse embeddings(String input) {
        FastChatEmbedding embedding = FastChatEmbedding.builder().input(input).build();
        if (StringUtils.isNotBlank(this.embeddingModel)) {
            embedding.setModel(this.embeddingModel);
        }
        return this.embeddings(embedding);
    }

    /**
     * Creates an embedding vector representing the input text.
     *
     * @param embedding
     * @return EmbeddingResponse
     */
    public FastChatEmbeddingResponse embeddings(FastChatEmbedding embedding) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            String requestBody = mapper.writeValueAsString(embedding);
            Request request = new Request.Builder()
                    .url(this.apiHost + "v1/embeddings")
                    .post(RequestBody.create(MediaType.parse(ContentType.JSON.getValue()), requestBody))
                    .build();

            FastChatEmbeddingResponse chatEmbeddingResponse = null;
            Response response = this.okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                String body = response.body().string();
                chatEmbeddingResponse = mapper.readValue(body, FastChatEmbeddingResponse.class);
            }
            log.info("finish invoking chat embedding");
            return chatEmbeddingResponse;
        } catch (Exception e) {
            log.error("chat ai error", e);
            throw new ParamBusinessException();
        }
    }
}

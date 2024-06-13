
package ai.chat2db.server.web.api.controller.ai.chat2db.client;

import ai.chat2db.server.domain.api.model.Config;
import ai.chat2db.server.domain.api.service.ConfigService;
import ai.chat2db.server.web.api.util.ApplicationContextUtil;
import com.unfbx.chatgpt.constant.OpenAIConst;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * @author jipengfei
 * @version : OpenAIClient.java
 */
@Slf4j
public class Chat2dbAIClient {

    public static final String CHAT2DB_OPENAI_KEY = "chat2db.apiKey";

    /**
     * OPENAI interface domain name
     */
    public static final String CHAT2DB_OPENAI_HOST = "chat2db.apiHost";

    /**
     * OPENAI model
     */
    public static final String CHAT2DB_OPENAI_MODEL = "chat2db.model";

    /**
     * FASTCHAT OPENAI embedding model
     */
    public static final String CHAT2DB_EMBEDDING_MODEL= "fastchat.embedding.model";


    private static volatile Chat2DBAIStreamClient CHAT2DB_AI_STREAM_CLIENT;

    public static Chat2DBAIStreamClient getInstance() {
        if (CHAT2DB_AI_STREAM_CLIENT != null) {
            return CHAT2DB_AI_STREAM_CLIENT;
        } else {
            return singleton();
        }
    }

    private static Chat2DBAIStreamClient singleton() {
        if (CHAT2DB_AI_STREAM_CLIENT == null) {
            synchronized (Chat2dbAIClient.class) {
                if (CHAT2DB_AI_STREAM_CLIENT == null) {
                    refresh();
                }
            }
        }
        return CHAT2DB_AI_STREAM_CLIENT;
    }

    public static void refresh() {
        ConfigService configService = ApplicationContextUtil.getBean(ConfigService.class);

        CHAT2DB_AI_STREAM_CLIENT = Chat2DBAIStreamClient.builder().apiHost(getApiHost(configService))
                .apiKey(getApiKey(configService)).model(getModel(configService)).build();
    }

    private static String getApiHost(ConfigService configService) {
        Config apiHostConfig = configService.find(CHAT2DB_OPENAI_HOST).getData();

        if (Objects.nonNull(apiHostConfig)) {
            return apiHostConfig.getContent();
        }

        String apiHost = ApplicationContextUtil.getProperty(CHAT2DB_OPENAI_HOST);

        if (apiHost.isBlank()) {
            return OpenAIConst.OPENAI_HOST;
        }

        return apiHost;
    }

    private static String getApiKey(ConfigService configService) {
        String apiKey;

        Config config = configService.find(CHAT2DB_OPENAI_KEY).getData();

        if (Objects.nonNull(config)) {
            apiKey = config.getContent();
        } else {
            apiKey = ApplicationContextUtil.getProperty(CHAT2DB_OPENAI_KEY);
        }

        log.info("refresh chat2db apikey:{}", maskApiKey(apiKey));

        return apiKey;
    }

    private static String getModel(ConfigService configService) {
        Config modelConfig = configService.find(CHAT2DB_OPENAI_MODEL).getData();

        if (Objects.nonNull(modelConfig)) {
            return modelConfig.getContent();
        }

        return null;
    }

    private static String maskApiKey(String input) {
        if (Objects.isNull(input)) {
            return null;
        }

        StringBuilder maskedString = new StringBuilder(input);
        for (int i = input.length() / 4; i < input.length() / 2; i++) {
            maskedString.setCharAt(i, '*');
        }
        return maskedString.toString();
    }
}


package ai.chat2db.server.web.api.controller.ai.zhipu.client;

import ai.chat2db.server.domain.api.model.Config;
import ai.chat2db.server.domain.api.service.ConfigService;
import ai.chat2db.server.web.api.util.ApplicationContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author moji
 * @date 23/09/26
 */
@Slf4j
public class ZhipuChatAIClient {

    /**
     * ZHIPU OPENAI KEY
     */
    public static final String ZHIPU_API_KEY = "zhipu.chatgpt.apiKey";

    /**
     * ZHIPU OPENAI HOST
     */
    public static final String ZHIPU_HOST = "zhipu.host";

    /**
     * ZHIPU OPENAI model
     */
    public static final String ZHIPU_MODEL= "zhipu.model";

    /**
     * ZHIPU OPENAI embedding model
     */
    public static final String ZHIPU_EMBEDDING_MODEL = "zhipu.embedding.model";

    private static ZhipuChatAIStreamClient ZHIPU_AI_CLIENT;


    public static ZhipuChatAIStreamClient getInstance() {
        if (ZHIPU_AI_CLIENT != null) {
            return ZHIPU_AI_CLIENT;
        } else {
            return singleton();
        }
    }

    private static ZhipuChatAIStreamClient singleton() {
        if (ZHIPU_AI_CLIENT == null) {
            synchronized (ZhipuChatAIClient.class) {
                if (ZHIPU_AI_CLIENT == null) {
                    refresh();
                }
            }
        }
        return ZHIPU_AI_CLIENT;
    }

    public static void refresh() {
        String apiKey = "";
        String apiHost = "";
        String model = "";
        ConfigService configService = ApplicationContextUtil.getBean(ConfigService.class);
        Config apiHostConfig = configService.find(ZHIPU_HOST).getData();
        if (apiHostConfig != null && StringUtils.isNotBlank(apiHostConfig.getContent())) {
            apiHost = apiHostConfig.getContent();
        }
        Config config = configService.find(ZHIPU_API_KEY).getData();
        if (config != null && StringUtils.isNotBlank(config.getContent())) {
            apiKey = config.getContent();
        }
        Config deployConfig = configService.find(ZHIPU_MODEL).getData();
        if (deployConfig != null && StringUtils.isNotBlank(deployConfig.getContent())) {
            model = deployConfig.getContent();
        }
        ZHIPU_AI_CLIENT = ZhipuChatAIStreamClient.builder().apiKey(apiKey).apiHost(apiHost).model(model)
            .build();
    }

}

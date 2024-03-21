
package ai.chat2db.server.web.api.controller.ai.openai.client;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import ai.chat2db.server.domain.api.model.Config;
import ai.chat2db.server.domain.api.service.ConfigService;

import ai.chat2db.server.web.api.util.ApplicationContextUtil;
import com.google.common.collect.Lists;
import com.unfbx.chatgpt.OpenAiStreamClient;
import com.unfbx.chatgpt.constant.OpenAIConst;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;

/**
 * @author jipengfei
 * @version : OpenAIClient.java
 */
@Slf4j
public class OpenAIClient {

    public static final String OPENAI_KEY = "chatgpt.apiKey";

    /**
     * OPENAI接口域名
     */
    public static final String OPENAI_HOST = "chatgpt.apiHost";

    /**
     * 代理IP
     */
    public static final String PROXY_HOST = "chatgpt.proxy.host";

    /**
     * 代理端口
     */
    public static final String PROXY_PORT = "chatgpt.proxy.port";

    private static OpenAiStreamClient OPEN_AI_STREAM_CLIENT;
    private static String apiKey;

    public static OpenAiStreamClient getInstance() {
        if (OPEN_AI_STREAM_CLIENT != null) {
            return OPEN_AI_STREAM_CLIENT;
        } else {
            return singleton();
        }
    }

    private static OpenAiStreamClient singleton() {
        if (OPEN_AI_STREAM_CLIENT == null) {
            synchronized (OpenAIClient.class) {
                if (OPEN_AI_STREAM_CLIENT == null) {
                    refresh();
                }
            }
        }
        return OPEN_AI_STREAM_CLIENT;
    }

    public static void refresh() {
        String apikey;
        String apiHost = ApplicationContextUtil.getProperty(OPENAI_HOST);
        if (StringUtils.isBlank(apiHost)) {
            apiHost = OpenAIConst.OPENAI_HOST;
        }
        ConfigService configService = ApplicationContextUtil.getBean(ConfigService.class);
        Config apiHostConfig = configService.find(OPENAI_HOST).getData();
        if (apiHostConfig != null) {
            apiHost = apiHostConfig.getContent();
        }
        Config config = configService.find(OPENAI_KEY).getData();
        if (config != null) {
            apikey = config.getContent();
        } else {
            apikey = ApplicationContextUtil.getProperty(OPENAI_KEY);
        }
        String host = System.getProperty("http.proxyHost");
        Config hostConfig = configService.find(PROXY_HOST).getData();
        if (hostConfig != null) {
            host = hostConfig.getContent();
        }
        Integer port = Objects.nonNull(System.getProperty("http.proxyPort")) ? Integer.valueOf(
            System.getProperty("http.proxyPort")) : null;
        Config portConfig = configService.find(PROXY_PORT).getData();
        if (portConfig != null && StringUtils.isNotBlank(portConfig.getContent())) {
            port = Integer.valueOf(portConfig.getContent());
        }
        log.info("refresh openai apikey:{}", maskApiKey(apikey));
        if (Objects.nonNull(host) && Objects.nonNull(port)) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    // 设置连接超时为10秒
                    .connectTimeout(10, TimeUnit.SECONDS)
                    // 设置读取超时为30秒
                    .readTimeout(30, TimeUnit.SECONDS)
                    // 设置写入超时为15秒
                    .writeTimeout(15, TimeUnit.SECONDS)
                    // 设置整个调用的超时为1分钟
                    .callTimeout(1, TimeUnit.MINUTES)
                    .proxy(proxy)
                    .build();
            OPEN_AI_STREAM_CLIENT = OpenAiStreamClient.builder().apiHost(apiHost).apiKey(
                Lists.newArrayList(apikey)).okHttpClient(okHttpClient).build();
        } else {
            OPEN_AI_STREAM_CLIENT = OpenAiStreamClient.builder().apiHost(apiHost).apiKey(
                Lists.newArrayList(apikey)).build();
        }
        apiKey = apikey;
    }

    private static String maskApiKey(String input) {
        if (input == null) {
            return input;
        }

        StringBuilder maskedString = new StringBuilder(input);
        for (int i = input.length() / 4; i < input.length() / 2; i++) {
            maskedString.setCharAt(i, '*');
        }
        return maskedString.toString();
    }
}

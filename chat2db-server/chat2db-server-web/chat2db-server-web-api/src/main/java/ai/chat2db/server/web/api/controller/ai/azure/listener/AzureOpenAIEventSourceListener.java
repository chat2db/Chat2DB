package ai.chat2db.server.web.api.controller.ai.azure.listener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.web.api.controller.ai.azure.client.AzureOpenAIClient;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatChoice;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatCompletions;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatCompletionsOptions;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatMessage;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatRole;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureCompletionsUsage;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatMessage;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatRole;
import ai.chat2db.server.web.api.controller.ai.openai.listener.OpenAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.utils.PromptService;
import ai.chat2db.server.web.api.controller.ai.zhipu.client.ZhipuChatAIClient;
import ai.chat2db.server.web.api.controller.ai.zhipu.model.ZhipuChatCompletionsOptions;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unfbx.chatgpt.entity.chat.Message;
import com.unfbx.chatgpt.entity.chat.tool.Tools;
import com.unfbx.chatgpt.entity.chat.tool.ToolsFunction;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 描述：OpenAIEventSourceListener
 *
 * @author https:www.unfbx.com
 * @date 2023-02-22
 */
@Slf4j
public class AzureOpenAIEventSourceListener extends OpenAIEventSourceListener {


    public AzureOpenAIEventSourceListener(SseEmitter sseEmitter, PromptService promptService,
            ChatQueryRequest queryRequest, LoginUser loginUser) {
        super(sseEmitter, promptService, queryRequest, loginUser);
    }

    @Override
    public String getName(){
        return "AzureOpenAI";
    }

   @Override
    public void functionCall(String prompt){
        AzureChatMessage currentMessage = new AzureChatMessage(AzureChatRole.USER).setContent(prompt);
        List<AzureChatMessage> messages = new ArrayList<>();
        messages.add(currentMessage);
        AzureChatCompletionsOptions chatCompletionsOptions = new AzureChatCompletionsOptions(messages);
        chatCompletionsOptions.setStream(true);
        AzureOpenAIClient.getInstance().streamCompletions(chatCompletionsOptions, this);
    }
}

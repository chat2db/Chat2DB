package ai.chat2db.server.web.api.controller.ai.zhipu.listener;

import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatMessage;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatRole;
import ai.chat2db.server.web.api.controller.ai.openai.listener.OpenAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.utils.PromptService;
import ai.chat2db.server.web.api.controller.ai.zhipu.client.ZhipuChatAIClient;
import ai.chat2db.server.web.api.controller.ai.zhipu.model.ZhipuChatCompletionsOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.unfbx.chatgpt.entity.chat.tool.Tools;
import com.unfbx.chatgpt.entity.chat.tool.ToolsFunction;

/**
 * 描述：OpenAIEventSourceListener
 *
 * @author https:www.unfbx.com
 * @date 2023-02-22
 */
@Slf4j
public class ZhipuChatAIEventSourceListener extends OpenAIEventSourceListener {
    
    public ZhipuChatAIEventSourceListener(SseEmitter sseEmitter, PromptService promptService,
            ChatQueryRequest queryRequest, LoginUser loginUser) {
        super(sseEmitter, promptService, queryRequest, loginUser);
    }

    @Override
    public String getName(){
        return "Zhipu";
    }

    @Override
    public void functionCall(String prompt){
        FastChatMessage currentMessage = new FastChatMessage(FastChatRole.USER).setContent(prompt);
        List<FastChatMessage> messages = new ArrayList<>();
        messages.add(currentMessage);
        String requestId = String.valueOf(System.currentTimeMillis());
        ToolsFunction function = PromptService.getToolsFunction();
        ZhipuChatCompletionsOptions completionsOptions = ZhipuChatCompletionsOptions.builder()
                .requestId(requestId)
                .stream(true)
                .toolChoice("auto")
                .tools(List.of(new Tools(Tools.Type.FUNCTION.getName(), function)))
                .messages(messages)
                .build();  
        ZhipuChatAIClient.getInstance().streamCompletions(completionsOptions, this);
    }
}

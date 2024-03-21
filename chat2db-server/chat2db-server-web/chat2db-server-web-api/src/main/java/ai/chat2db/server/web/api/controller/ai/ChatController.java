package ai.chat2db.server.web.api.controller.ai;



import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.domain.api.model.Config;
import ai.chat2db.server.domain.api.service.ConfigService;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.ai.azure.client.AzureOpenAIClient;
import ai.chat2db.server.web.api.controller.ai.azure.listener.AzureOpenAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatCompletionsOptions;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatMessage;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatRole;
import ai.chat2db.server.web.api.controller.ai.baichuan.client.BaichuanAIClient;
import ai.chat2db.server.web.api.controller.ai.baichuan.listener.BaichuanChatAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.chat2db.client.Chat2dbAIClient;
import ai.chat2db.server.web.api.controller.ai.chat2db.listener.Chat2dbAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.claude.client.ClaudeAIClient;
import ai.chat2db.server.web.api.controller.ai.claude.listener.ClaudeAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.claude.model.ClaudeChatCompletionsOptions;
import ai.chat2db.server.web.api.controller.ai.claude.model.ClaudeChatMessage;
import ai.chat2db.server.web.api.controller.ai.config.LocalCache;
import ai.chat2db.server.web.api.controller.ai.fastchat.client.FastChatAIClient;
import ai.chat2db.server.web.api.controller.ai.fastchat.listener.FastChatAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatCompletionsOptions;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatMessage;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatRole;
import ai.chat2db.server.web.api.controller.ai.openai.client.OpenAIClient;
import ai.chat2db.server.web.api.controller.ai.openai.listener.OpenAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.request.ChatRequest;
import ai.chat2db.server.web.api.controller.ai.rest.client.RestAIClient;
import ai.chat2db.server.web.api.controller.ai.rest.listener.RestAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.tongyi.client.TongyiChatAIClient;
import ai.chat2db.server.web.api.controller.ai.tongyi.listener.TongyiChatAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.utils.PromptService;
import ai.chat2db.server.web.api.controller.ai.wenxin.client.WenxinAIClient;
import ai.chat2db.server.web.api.controller.ai.wenxin.listener.WenxinAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.zhipu.client.ZhipuChatAIClient;
import ai.chat2db.server.web.api.controller.ai.zhipu.listener.ZhipuChatAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.zhipu.model.ZhipuChatCompletionsOptions;
import ai.chat2db.server.web.api.http.GatewayClientService;
import ai.chat2db.server.web.api.util.ApplicationContextUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.google.common.collect.Lists;
import com.unfbx.chatgpt.entity.chat.ChatCompletion;
import com.unfbx.chatgpt.entity.chat.Message;
import com.unfbx.chatgpt.entity.chat.tool.Tools;
import com.unfbx.chatgpt.entity.chat.tool.ToolsFunction;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 描述：
 *
 * @author https:www.unfbx.com
 * @date 2023-03-01
 */
@RestController
@ConnectionInfoAspect
@RequestMapping("/api/ai")
@Slf4j
public class ChatController {


    @Value("${chatgpt.context.length}")
    private Integer contextLength;

    @Value("${chatgpt.version}")
    private String gptVersion;

    @Resource
    private GatewayClientService gatewayClientService;


    @Resource
    protected PromptService promptService;

    /**
     * chat的超时时间
     */
    private static final Long CHAT_TIMEOUT = Duration.ofMinutes(50).toMillis();

    /**
     * 提示语最大token数
     */
    private Integer MAX_PROMPT_LENGTH = 3850;

    /**
     * token转换字符串长度
     */
    private Integer TOKEN_CONVERT_CHAR_LENGTH = 4;

    /**
     * 返回token大小
     */
    private Integer RETURN_TOKEN_LENGTH = 150;


    /**
     * 自定义模型流式输出接口DEMO
     * <p>
     *     Note:使用自己本地的流式输出的自定义AI，接口输入和输出需与该样例保持一致
     * </p>
     *
     * @param queryRequest
     * @return
     * @throws IOException
     */
    @PostMapping("/custom/stream/chat")
    @CrossOrigin
    public SseEmitter customChat(@RequestBody ChatRequest queryRequest) throws IOException {
        SseEmitter emitter = new SseEmitter(CHAT_TIMEOUT);

        // 设置 SSEEmitter 的事件处理程序
        emitter.onCompletion(() -> log.info(LocalDateTime.now() + ", on completion"));
        emitter.onTimeout(() -> {
            log.info(LocalDateTime.now() + ", uid# on timeout");
            emitter.complete();
        });

        // 启动一个新的线程来生成 SSE 事件
        new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    emitter.send(SseEmitter.event().name("message").data("Event " + i));
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            } finally {
                emitter.complete();
            }
        }).start();

        return emitter;
    }

    /**
     * 自定义模型非流式输出接口DEMO
     * <p>
     * Note:使用自己本地的飞流式输出自定义AI，接口输入和输出需与该样例保持一致
     * </p>
     *
     * @param queryRequest
     * @return
     * @throws IOException
     */
    @PostMapping("/custom/non/stream/chat")
    @CrossOrigin
    public String customNonStreamChat(@RequestBody ChatRequest queryRequest) throws IOException {
        String data = "自定义AI样例接口连接成功！！！！";
        return data;
    }

    /**
     * SQL转换模型
     *
     * @param queryRequest
     * @param headers
     * @return
     * @throws IOException
     */
    @GetMapping("/chat")
    @CrossOrigin
    public SseEmitter completions(ChatQueryRequest queryRequest, @RequestHeader Map<String, String> headers)
        throws IOException {
        //默认30秒超时,设置为0L则永不超时
        SseEmitter sseEmitter = new SseEmitter(CHAT_TIMEOUT);
        String uid = headers.get("uid");
        if (StrUtil.isBlank(uid)) {
            throw new ParamBusinessException("uid");
        }

        //提示消息不得为空
        if (StringUtils.isBlank(queryRequest.getMessage())) {
            throw new ParamBusinessException("message");
        }

        return distributeAISql(queryRequest, sseEmitter, uid);
    }

    /**
     * distribute with different AI
     *
     * @return
     */
    public SseEmitter distributeAISql(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        ConfigService configService = ApplicationContextUtil.getBean(ConfigService.class);
        Config config = configService.find(RestAIClient.AI_SQL_SOURCE).getData();
        String aiSqlSource = AiSqlSourceEnum.CHAT2DBAI.getCode();
        if (Objects.nonNull(config)) {
            aiSqlSource = config.getContent();
        }
        AiSqlSourceEnum aiSqlSourceEnum = AiSqlSourceEnum.getByName(aiSqlSource);
        if (Objects.isNull(aiSqlSourceEnum)) {
            aiSqlSourceEnum = AiSqlSourceEnum.OPENAI;
        }
        uid = aiSqlSourceEnum.getCode() + uid;
        switch (Objects.requireNonNull(aiSqlSourceEnum)) {
            case OPENAI :
                return chatWithOpenAi(queryRequest, sseEmitter, uid);
            case CHAT2DBAI:
                return chatWithChat2dbAi(queryRequest, sseEmitter, uid);
            case RESTAI :
            case FASTCHATAI:
                return chatWithFastChatAi(queryRequest, sseEmitter, uid);
            case AZUREAI :
                return chatWithAzureAi(queryRequest, sseEmitter, uid);
            case CLAUDEAI:
                return chatWithClaudeAi(queryRequest, sseEmitter, uid);
            case WENXINAI:
                return chatWithWenxinAi(queryRequest, sseEmitter, uid);
            case BAICHUANAI:
                return chatWithBaichuanAi(queryRequest, sseEmitter, uid);
            case TONGYIQIANWENAI:
                return chatWithTongyiChatAi(queryRequest, sseEmitter, uid);
            case ZHIPUAI:
                return chatWithZhipuChatAi(queryRequest, sseEmitter, uid);
        }
        return chatWithOpenAi(queryRequest, sseEmitter, uid);
    }

    /**
     * 使用自定义AI接口进行聊天
     *
     * @param prompt
     * @param sseEmitter
     * @return
     */
    private SseEmitter chatWithRestAi(ChatQueryRequest prompt, SseEmitter sseEmitter) {
        RestAIEventSourceListener eventSourceListener = new RestAIEventSourceListener(sseEmitter);
        RestAIClient.getInstance().restCompletions(promptService.buildPrompt(prompt), eventSourceListener);
        return sseEmitter;
    }

    /**
     * 使用OPENAI SQL接口
     *
     * @param queryRequest
     * @param sseEmitter
     * @param uid
     * @return
     * @throws IOException
     */
    private SseEmitter chatWithOpenAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid)
            throws IOException {
        String prompt = promptService.buildAutoPrompt(queryRequest);
        if (prompt.length() / TOKEN_CONVERT_CHAR_LENGTH > MAX_PROMPT_LENGTH) {
            log.error("提示语超出最大长度:{}，输入长度:{}, 请重新输入", MAX_PROMPT_LENGTH,
                    prompt.length() / TOKEN_CONVERT_CHAR_LENGTH);
            throw new ParamBusinessException();
        }

        List<Message> messages = new ArrayList<>();
        prompt = prompt.replaceAll("#", "");
        log.info(prompt);
        Message currentMessage = Message.builder().content(prompt).role(Message.Role.USER).build();
        messages.add(currentMessage);
        buildSseEmitter(sseEmitter, uid);
        LoginUser loginUser = ContextUtils.getLoginUser();
        OpenAIEventSourceListener openAIEventSourceListener = new OpenAIEventSourceListener(sseEmitter, promptService, queryRequest,loginUser);
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .messages(messages).stream(true).build();
        if(queryRequest.getDatabaseName()!=null){
            ToolsFunction function = PromptService.getToolsFunction();
            chatCompletion.setModel("gpt-3.5-turbo-0125");
            chatCompletion.setTools(List.of(new Tools(Tools.Type.FUNCTION.getName(), function)));
            chatCompletion.setToolChoice("auto");
        }
        OpenAIClient.getInstance().streamChatCompletion(chatCompletion, openAIEventSourceListener);
        LocalCache.CACHE.put(uid, JSONUtil.toJsonStr(messages), LocalCache.TIMEOUT);
        return sseEmitter;
    }

    /**
     * 使用OPENAI SQL接口
     *
     * @param queryRequest
     * @param sseEmitter
     * @param uid
     * @return
     * @throws IOException
     */
    private SseEmitter chatWithChat2dbAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid)
        throws IOException {
        String prompt = promptService.buildPrompt(queryRequest);
        if (prompt.length() / TOKEN_CONVERT_CHAR_LENGTH > MAX_PROMPT_LENGTH) {
            log.error("exceed max token length:{}，input length:{}", MAX_PROMPT_LENGTH,
                prompt.length() / TOKEN_CONVERT_CHAR_LENGTH);
            throw new ParamBusinessException();
        }

        prompt = prompt.replaceAll("#", "");
        log.info(prompt);
        Message currentMessage = Message.builder().content(prompt).role(Message.Role.USER).build();
        List<Message> messages = new ArrayList<>();
        messages.add(currentMessage);
        buildSseEmitter(sseEmitter, uid);

        Chat2dbAIEventSourceListener openAIEventSourceListener = new Chat2dbAIEventSourceListener(sseEmitter);
        Chat2dbAIClient.getInstance().streamCompletions(messages, openAIEventSourceListener);
        LocalCache.CACHE.put(uid, JSONUtil.toJsonStr(messages), LocalCache.TIMEOUT);
        return sseEmitter;
    }

    /**
     * chat with azure openai
     *
     * @param queryRequest
     * @param sseEmitter
     * @param uid
     * @return
     * @throws IOException
     */
    private SseEmitter chatWithAzureAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = promptService.buildAutoPrompt(queryRequest);
        if (prompt.length() / TOKEN_CONVERT_CHAR_LENGTH > MAX_PROMPT_LENGTH) {
            log.error("提示语超出最大长度:{}，输入长度:{}, 请重新输入", MAX_PROMPT_LENGTH,
                    prompt.length() / TOKEN_CONVERT_CHAR_LENGTH);
            throw new ParamBusinessException();
        }
        List<AzureChatMessage> messages = (List<AzureChatMessage>)LocalCache.CACHE.get(uid);
        if (CollectionUtils.isNotEmpty(messages)) {
            if (messages.size() >= contextLength) {
                messages = messages.subList(1, contextLength);
            }
        } else {
            messages = Lists.newArrayList();
        }
        AzureChatMessage currentMessage = new AzureChatMessage(AzureChatRole.USER).setContent(prompt);
        messages.add(currentMessage);

        buildSseEmitter(sseEmitter, uid);
        LoginUser loginUser = ContextUtils.getLoginUser();
        AzureOpenAIEventSourceListener sourceListener = new AzureOpenAIEventSourceListener(sseEmitter,promptService,queryRequest,loginUser);
        AzureChatCompletionsOptions chatCompletionsOptions = new AzureChatCompletionsOptions(messages);
        chatCompletionsOptions.setStream(true);
        if(queryRequest.getDatabaseName()!=null){
            ToolsFunction function = PromptService.getToolsFunction();
            chatCompletionsOptions.setTools(List.of(new Tools(Tools.Type.FUNCTION.getName(), function)));
            chatCompletionsOptions.setToolChoice("auto");
        }
        AzureOpenAIClient.getInstance().streamCompletions(chatCompletionsOptions, sourceListener);
        LocalCache.CACHE.put(uid, messages, LocalCache.TIMEOUT);
        return sseEmitter;
    }

    /**
     * chat with fast chat openai
     *
     * @param queryRequest
     * @param sseEmitter
     * @param uid
     * @return
     * @throws IOException
     */
    private SseEmitter chatWithFastChatAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = promptService.buildPrompt(queryRequest);
        List<FastChatMessage> messages = promptService.getFastChatMessage(uid, prompt);

        buildSseEmitter(sseEmitter, uid);

        FastChatAIEventSourceListener sourceListener = new FastChatAIEventSourceListener(sseEmitter);
        FastChatAIClient.getInstance().streamCompletions(messages, sourceListener);
        LocalCache.CACHE.put(uid, messages, LocalCache.TIMEOUT);
        return sseEmitter;
    }

    /**
     * chat with zhipu chat openai
     *
     * @param queryRequest
     * @param sseEmitter
     * @param uid
     * @return
     * @throws IOException
     */
    private SseEmitter chatWithZhipuChatAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = promptService.buildAutoPrompt(queryRequest);
        log.info("原始提示词{}",prompt);
        List<FastChatMessage> messages = promptService.getFastChatMessage(uid, prompt);

        buildSseEmitter(sseEmitter, uid);
        LoginUser loginUser = ContextUtils.getLoginUser();
        ZhipuChatAIEventSourceListener sourceListener = new ZhipuChatAIEventSourceListener(sseEmitter,promptService,queryRequest,loginUser);
        String requestId = String.valueOf(System.currentTimeMillis());
        // 建议直接查看demo包代码，这里更新可能不及时
        ZhipuChatCompletionsOptions completionsOptions = ZhipuChatCompletionsOptions.builder()
                .requestId(requestId)
                .stream(true)

                .messages(messages)
                .build();
        if(queryRequest.getDatabaseName()!=null){
            ToolsFunction function = PromptService.getToolsFunction();
            completionsOptions.setTools(List.of(new Tools(Tools.Type.FUNCTION.getName(), function)));
            completionsOptions.setToolChoice("auto");
        }
        ZhipuChatAIClient.getInstance().streamCompletions(completionsOptions, sourceListener);
        LocalCache.CACHE.put(uid, messages, LocalCache.TIMEOUT);
        return sseEmitter;
    }

    /**
     * chat with tongyi chat openai
     *
     * @param queryRequest
     * @param sseEmitter
     * @param uid
     * @return
     * @throws IOException
     */
    private SseEmitter chatWithTongyiChatAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = promptService.buildPrompt(queryRequest);
        List<FastChatMessage> messages = promptService.getFastChatMessage(uid, prompt);

        buildSseEmitter(sseEmitter, uid);

        TongyiChatAIEventSourceListener sourceListener = new TongyiChatAIEventSourceListener(sseEmitter);
        TongyiChatAIClient.getInstance().streamCompletions(messages, sourceListener);
        LocalCache.CACHE.put(uid, messages, LocalCache.TIMEOUT);
        return sseEmitter;
    }

    /**
     * chat with baichuan chat openai
     *
     * @param queryRequest
     * @param sseEmitter
     * @param uid
     * @return
     * @throws IOException
     */
    private SseEmitter chatWithBaichuanAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = promptService.buildPrompt(queryRequest);
        List<FastChatMessage> messages = promptService.getFastChatMessage(uid, prompt);

        buildSseEmitter(sseEmitter, uid);

        BaichuanChatAIEventSourceListener sourceListener = new BaichuanChatAIEventSourceListener(sseEmitter);
        BaichuanAIClient.getInstance().streamCompletions(messages, sourceListener);
        LocalCache.CACHE.put(uid, messages, LocalCache.TIMEOUT);
        return sseEmitter;
    }

    

    /**
     * chat with wenxin chat openai
     *
     * @param queryRequest
     * @param sseEmitter
     * @param uid
     * @return
     * @throws IOException
     */
    private SseEmitter chatWithWenxinAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = promptService.buildPrompt(queryRequest);
        List<FastChatMessage> messages = promptService.getFastChatMessage(uid, prompt);
        if (messages.size() >= 2 && messages.size() % 2 == 0) {
            messages.remove(messages.size() - 1);
        }

        buildSseEmitter(sseEmitter, uid);

        WenxinAIEventSourceListener sourceListener = new WenxinAIEventSourceListener(sseEmitter);
        WenxinAIClient.getInstance().streamCompletions(messages, sourceListener);
        LocalCache.CACHE.put(uid, messages, LocalCache.TIMEOUT);
        return sseEmitter;
    }


    /**
     * chat with claude ai
     *
     * @param queryRequest
     * @param sseEmitter
     * @param uid
     * @return
     * @throws IOException
     */
    private SseEmitter chatWithClaudeAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = promptService.buildPrompt(queryRequest);
        ClaudeChatMessage claudeChatMessage = new ClaudeChatMessage();
        claudeChatMessage.setText(prompt);
        ClaudeChatCompletionsOptions chatCompletionsOptions = new ClaudeChatCompletionsOptions();
        chatCompletionsOptions.setPrompt(prompt);
        claudeChatMessage.setCompletion(chatCompletionsOptions);

        buildSseEmitter(sseEmitter, uid);

        ClaudeAIEventSourceListener sourceListener = new ClaudeAIEventSourceListener(sseEmitter);
        ClaudeAIClient.getInstance().streamCompletions(claudeChatMessage, sourceListener);
        return sseEmitter;
    }

    /**
     * construct sseEmitter
     *
     * @param sseEmitter
     * @param uid
     * @return
     * @throws IOException
     */
    private SseEmitter buildSseEmitter(SseEmitter sseEmitter, String uid) throws IOException {
        sseEmitter.send(SseEmitter.event().id(uid).name("connect successfully！！！！").data(LocalDateTime.now()).reconnectTime(3000));
        sseEmitter.onCompletion(() -> {
            log.info(LocalDateTime.now() + ", uid#" + uid + ", on completion");
        });
        sseEmitter.onTimeout(
            () -> log.info(LocalDateTime.now() + ", uid#" + uid + ", on timeout#" + sseEmitter.getTimeout()));
        sseEmitter.onError(
            throwable -> {
                try {
                    log.info(LocalDateTime.now() + ", uid#" + "765431" + ", on error#" + throwable.toString());
                    sseEmitter.send(SseEmitter.event().id("765431").name("发生异常！").data(throwable.getMessage())
                        .reconnectTime(3000));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        );
        return sseEmitter;
    }


}

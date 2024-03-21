package ai.chat2db.server.web.api.controller.ai;

import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.http.GatewayClientService;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * @author moji
 */
@RestController
@ConnectionInfoAspect
@RequestMapping("/api/ai/text/generation")
@Slf4j
public class TextGenerationController extends ChatController {


    /**
     * chat timeout time
     */
    private static final Long CHAT_TIMEOUT = Duration.ofMinutes(50).toMillis();


    @Resource
    private GatewayClientService gatewayClientService;

    /**
     * sql auto complete
     *
     * @param queryRequest
     * @return
     * @throws IOException
     */
    @GetMapping("/prompt")
    @CrossOrigin
    public SseEmitter prompt(ChatQueryRequest queryRequest, @RequestHeader Map<String, String> headers)
            throws Exception {
        queryRequest.setPromptType(PromptType.TEXT_GENERATION.getCode());

        String promptTemplate = "### Instructions:\n" +
                "Your task is generate a SQL query according to the prompt %s.\n" +
                "Adhere to these rules:\n" +
                "- **Deliberately go through the prompt and database schema word by word** to appropriately answer the question\n" +
                "- **Use Table Aliases** to prevent ambiguity. For example, `SELECT table1.col1, table2.col1 FROM table1 JOIN table2 ON table1.id = table2.id`.\n" +
                "\n" +
                "### Input:\n" +
                "Generate a SQL query according to the prompt `%s`.\n" +
                "%s\n" +
                "\n" +
                "### Response:\n" +
                "Based on your instructions, here is the SQL query I have generated to complete the prompt `{%s}`:\n" +
                "```sql";

        // query database schema info
        String databaseType = promptService.queryDatabaseType(queryRequest);
        String schemas = promptService.queryDatabaseSchema(queryRequest);
        if (StringUtils.isNotBlank(schemas)) {
            databaseType = String.format(", given a %s database schema", databaseType);
            schemas = String.format("This query will run on a database whose schema is represented in this string:\n$s", schemas);
        } else  {
            databaseType = "";
            schemas = "";
        }
        String prompt = String.format(promptTemplate, databaseType, queryRequest.getMessage(), schemas, queryRequest.getMessage());
        queryRequest.setMessage(prompt);

        // chat with AI
        SseEmitter sseEmitter = new SseEmitter(CHAT_TIMEOUT);
        String uid = headers.get("uid");
        if (StrUtil.isBlank(uid)) {
            throw new ParamBusinessException("uid");
        }

        if (StringUtils.isBlank(queryRequest.getMessage())) {
            throw new ParamBusinessException("message");
        }

        return distributeAISql(queryRequest, sseEmitter, uid);
    }

}

package ai.chat2db.server.web.api.controller.ai;

import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.ai.DocParser.AbstractParser;
import ai.chat2db.server.web.api.controller.ai.DocParser.PdfParse;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.fastchat.embeddings.FastChatEmbeddingResponse;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.http.GatewayClientService;
import ai.chat2db.server.web.api.http.model.Knowledge;
import ai.chat2db.server.web.api.http.request.KnowledgeRequest;
import ai.chat2db.server.web.api.http.response.KnowledgeResponse;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author moji
 */
@RestController
@ConnectionInfoAspect
@RequestMapping("/api/ai/knowledge")
@Slf4j
public class KnowledgeController extends ChatController {


    /**
     * chat的超时时间
     */
    private static final Long CHAT_TIMEOUT = Duration.ofMinutes(50).toMillis();


    @Resource
    private GatewayClientService gatewayClientService;

    /**
     * save knowledge from pdf file
     *
     * @param file
     * @return
     * @throws IOException
     */
    @PostMapping("/embeddings")
    @CrossOrigin
    public ActionResult embeddings(MultipartFile file, HttpServletRequest request)
        throws Exception {
        AbstractParser pdfParse = new PdfParse();
        List<String> sentenceList = pdfParse.parse(file.getInputStream());

        List<Integer> contentWordCount = new ArrayList<>();
        List<List<BigDecimal>> contentVector = new ArrayList<>();
        for(String str : sentenceList){
            contentWordCount.add(str.length());

            // request embedding
            FastChatEmbeddingResponse response = promptService.distributeAIEmbedding(str);
            if(response == null){
                continue;
            }
            contentVector.add(response.getData().get(0).getEmbedding());
        }

        KnowledgeRequest knowledgeRequest = new KnowledgeRequest();
        knowledgeRequest.setContentVector(contentVector);
        knowledgeRequest.setSentenceList(sentenceList);
        // save knowledge embedding
        ActionResult actionResult = gatewayClientService.knowledgeVectorSave(knowledgeRequest);
        return actionResult;
    }

    /**
     * search knowledge
     *
     * @param queryRequest
     * @return
     * @throws IOException
     */
    @GetMapping("/search")
    @CrossOrigin
    public SseEmitter search(ChatQueryRequest queryRequest, @RequestHeader Map<String, String> headers)
            throws Exception {
        // request embedding
        FastChatEmbeddingResponse response = promptService.distributeAIEmbedding(queryRequest.getMessage());
        List<List<BigDecimal>> contentVector = new ArrayList<>();
        contentVector.add(response.getData().get(0).getEmbedding());

        // search embedding
        KnowledgeRequest knowledgeRequest = new KnowledgeRequest();
        knowledgeRequest.setContentVector(contentVector);
        DataResult<KnowledgeResponse> result = gatewayClientService.knowledgeVectorSearch(knowledgeRequest);
        queryRequest.setPromptType(PromptType.TEXT_GENERATION.getCode());
        String prompt = queryRequest.getMessage();
        if (CollectionUtils.isNotEmpty(result.getData().getKnowledgeList())) {
            List<String> contents = new ArrayList<>();
            for(Knowledge data: result.getData().getKnowledgeList()){
                contents.add(data.getContent());
            }

            prompt = String.format("基于%s。请回答%s。", JSON.toJSONString(contents), prompt);
            queryRequest.setMessage(prompt);
        }

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

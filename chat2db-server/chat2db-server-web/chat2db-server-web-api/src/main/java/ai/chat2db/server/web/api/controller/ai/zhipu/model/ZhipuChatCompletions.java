package ai.chat2db.server.web.api.controller.ai.zhipu.model;

import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatChoice;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatCompletionsUsage;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ZhipuChatCompletions {

    /*
     * A unique identifier associated with this chat completions response.
     */
    @JsonProperty(value = "id")
    private String id;
    @JsonProperty(value = "created")
    private Long created;

    @JsonProperty(value = "choices")
    private List<FastChatChoice> choices;

    @JsonProperty(value = "usage")
    private FastChatCompletionsUsage usage;
    @JsonCreator
    private ZhipuChatCompletions(
        @JsonProperty(value = "id") String id,
        @JsonProperty(value = "created") Long created,
        @JsonProperty(value = "choices") List<FastChatChoice> choices,
        @JsonProperty(value = "usage") FastChatCompletionsUsage usage) {
        this.id = id;
        this.created = created;
        this.choices = choices;
        this.usage = usage;
    }
}

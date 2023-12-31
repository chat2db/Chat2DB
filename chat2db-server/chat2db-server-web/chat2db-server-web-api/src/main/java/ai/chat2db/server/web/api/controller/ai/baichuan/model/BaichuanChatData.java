// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
// Code generated by Microsoft (R) AutoRest Code Generator.
package ai.chat2db.server.web.api.controller.ai.baichuan.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * The representation of a single prompt completion as part of an overall completions request. Generally, `n` choices
 * are generated per provided prompt with a default value of 1. Token limits and other settings may limit the number of
 * choices generated.
 */
@Data
public final class BaichuanChatData {

    /*
     * The log probabilities model for tokens associated with this completions choice.
     */
    @JsonProperty(value = "messages")
    private List<BaichuanChatMessage> messages;



    /**
     * Creates an instance of Choice class.
     *
     * @param message the message value to set
     */
    @JsonCreator
    private BaichuanChatData(
            @JsonProperty(value = "messages") List<BaichuanChatMessage> message) {
        this.messages = message;
    }

}

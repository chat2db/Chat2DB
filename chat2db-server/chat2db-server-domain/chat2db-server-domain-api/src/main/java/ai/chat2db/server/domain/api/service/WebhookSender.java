package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.message.MessageCreateParam;

/**
 * @author Juechen
 * @version : WebhookSender.java
 */
public interface WebhookSender {

    void sendMessage(MessageCreateParam param);

}

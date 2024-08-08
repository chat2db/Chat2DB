package ai.chat2db.server.domain.core.notification;

import ai.chat2db.server.domain.api.param.message.MessageCreateParam;
import ai.chat2db.server.domain.api.service.WebhookSender;
import ai.chat2db.server.domain.core.enums.ExternalNotificationTypeEnum;
import org.springframework.stereotype.Service;

@Service
public class BaseWebhookSender implements WebhookSender {

    /**
     * Sends a message through the specified webhook platform.
     *
     * @param param The parameter object containing message details and platform type.
     * @throws IllegalArgumentException if the provided param is null or invalid.
     * @throws RuntimeException if an error occurs while attempting to send the message.
     */
    public void sendMessage(MessageCreateParam param) throws IllegalArgumentException {
        // Validate the input parameter to ensure it's not null and meets the necessary criteria.
        if (param == null || param.getPlatformType() == null) {
            throw new IllegalArgumentException("MessageCreateParam or its platform type cannot be null.");
        }

        try {
            // Attempt to retrieve the appropriate WebhookSender based on the platform type.
            ExternalNotificationTypeEnum extern = ExternalNotificationTypeEnum.getByName(param.getPlatformType());
            WebhookSender sender = extern.getWebhookSender(param.getPlatformType());

            // Guard clause for null sender. Ideally, getWebhookSender should prevent this, but it's good to be cautious.
            if (sender == null) {
                throw new RuntimeException("Failed to retrieve WebhookSender for platform type: " + param.getPlatformType());
            }

            // Send the message. Any exceptions thrown by sendMessage should be caught and handled here.
            sender.sendMessage(param);
        } catch (Exception e) {
            // Wrap and re-throw any runtime exceptions as a checked exception specific to webhook sending.
            throw new RuntimeException("An error occurred while sending the message.", e);
        }
    }

}

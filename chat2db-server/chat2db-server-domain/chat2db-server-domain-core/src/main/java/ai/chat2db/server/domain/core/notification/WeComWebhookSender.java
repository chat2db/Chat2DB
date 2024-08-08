package ai.chat2db.server.domain.core.notification;

import ai.chat2db.server.domain.api.param.message.MessageCreateParam;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * @author Juechen
 * @version : WeComWebhookSender.java
 */
@Service
public class WeComWebhookSender extends BaseWebhookSender {

    @Override
    public void sendMessage(MessageCreateParam param) {
        try {
            OkHttpClient client = new OkHttpClient();
            String webhookUrl = param.getServiceUrl();
            String text = param.getTextTemplate();

            String payload = "{\"msgtype\": \"text\",\"text\": {\"content\": \"" + text + "\"}}";

            RequestBody requestBody = RequestBody.create(payload, MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build();

            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to send message: " + response.code());
            }
            System.out.println(response.body().string());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}

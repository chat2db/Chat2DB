package ai.chat2db.server.domain.core.notification;

import ai.chat2db.server.domain.api.param.message.MessageCreateParam;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Service;

/**
 * @author Juechen
 * @version : LarkWebhookSender.java
 */
@Service
public class LarkWebhookSender extends BaseWebhookSender {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    @Override
    public void sendMessage(MessageCreateParam param) {
        try {
            OkHttpClient client = new OkHttpClient();
            String webhookUrl = param.getServiceUrl();
            String secret = param.getSecretKey();
            int timestamp = (int) (System.currentTimeMillis() / 1000);

            String signature = GenSign(secret, timestamp);

            String payload = "{\"timestamp\": \"" + timestamp
                    + "\",\"sign\": \"" + signature
                    + "\",\"msg_type\":\"text\",\"content\":{\"text\":\""+ param.getTextTemplate() +"\"}}";
            RequestBody body = RequestBody.create(payload, MediaType.parse("application/json; charset=utf-8"));


            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to send message: " + response.code());
            }
            System.out.println(response.body().string());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static String GenSign(String secret, int timestamp) throws NoSuchAlgorithmException, InvalidKeyException {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM));
        byte[] signData = mac.doFinal(new byte[]{});
        return new String(Base64.encodeBase64(signData));
    }
}
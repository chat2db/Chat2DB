package ai.chat2db.server.domain.core.notification;

import ai.chat2db.server.domain.api.param.message.MessageCreateParam;
import okhttp3.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Service;

/**
 * @author Juechen
 * @version : DingTalkWebhookSender.java
 */
@Service
public class DingTalkWebhookSender extends BaseWebhookSender {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    @Override
    public void sendMessage(MessageCreateParam param) {
        try {
            OkHttpClient client = new OkHttpClient();
            String secret = param.getSecretKey();
            Long timestamp = System.currentTimeMillis();

            String sign = generateSign(secret, timestamp);

            String webhookUrl = param.getServiceUrl() + "&sign=" + sign + "&timestamp=" + timestamp;


            String payload = "{\"msgtype\": \"text\",\"text\": {\"content\": \"" + param.getTextTemplate() + "\"}}";
            RequestBody requestBody = RequestBody.create(payload, MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build();


            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to send message: " + response.code());
            }
            System.out.println(response.body().string());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }

    }

    private static String generateSign(String secret, Long timestamp) throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes("UTF-8"));
        return URLEncoder.encode(new String(Base64.encodeBase64(signData)), "UTF-8");
    }
}
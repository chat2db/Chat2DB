package ai.chat2db.server.start.test.core;

import ai.chat2db.server.domain.api.param.message.MessageCreateParam;
import ai.chat2db.server.domain.core.notification.BaseWebhookSender;
import ai.chat2db.server.start.test.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * @author Juechen
 * @version : WebhookServiceTest.java
 */
public class WebhookServiceTest extends TestApplication {

    @Autowired
    private BaseWebhookSender baseWebhookSender;

    @Test
    public void test() {
        MessageCreateParam param = new MessageCreateParam();
//        param.setServiceUrl("https://oapi.dingtalk.com/robot/send?access_token=3dc1c8a55a3ba966d38fb37466c93c536ac210895304e2682966252ea8f8a252");
//        param.setSecretKey("SEC5058616c6ea2e5745abeb381d510579538ea5baa7cdd28a386c809289b1f1db9");
//        param.setPlatformType("DingTalk");
//        param.setTextTemplate("你好，钉钉！");

        param.setServiceUrl("https://open.feishu.cn/open-apis/bot/v2/hook/da4c4585-b320-4a72-8fbe-920b48c4a0c9");
        param.setSecretKey("tm3p2x2IBs8Lh8cBiJo1F");
        param.setPlatformType("LaRK");
        param.setTextTemplate("你好，飞书");

//        param.setServiceUrl("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=346b7d1e-39bd-4146-89e4-bca5fe05f5b4");
//        param.setSecretKey("");
//        param.setPlatformType("WeCom");
//        param.setTextTemplate("你好，企业微信");
        baseWebhookSender.sendMessage(param);
    }
}

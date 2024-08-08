package ai.chat2db.server.domain.api.param.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Juechen
 * @version : MessageCreateParam.java
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MessageCreateParam {

    /**
     * 平台类型
     * @see ai.chat2db.server.domain.core.enums.ExternalNotificationTypeEnum
     */
    private String platformType;

    /**
     * 服务URL
     */
    private String serviceUrl;

    /**
     * 密钥
     */
    private String secretKey;

    /**
     * 消息模版
     */
    private String textTemplate;


}

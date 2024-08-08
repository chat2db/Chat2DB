package ai.chat2db.server.start.test.core;

import ai.chat2db.server.domain.api.model.Config;
import ai.chat2db.server.domain.api.param.SystemConfigParam;
import ai.chat2db.server.domain.api.service.ConfigService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.start.test.TestApplication;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.SecureRandom;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConfigServiceTest extends TestApplication {

    @Autowired
    private ConfigService configService;

    @Test
    public void testCreate() {
        userLoginIdentity(true, 1L);
//        userLoginIdentity(false, 2L);

        SystemConfigParam systemConfigParam = new SystemConfigParam();
        Optional.ofNullable(systemConfigParam).ifPresent(param -> {
            param.setCode(RandomCodeGenerator.generateRandomCode(6));
            param.setContent(RandomCodeGenerator.generateRandomCode(6));
            param.setSummary(RandomCodeGenerator.generateRandomCode(6));
        });

        ActionResult actionResult = configService.create(systemConfigParam);
        assertNotNull(actionResult);
    }

    @Test
    public void testUpdate() {
        userLoginIdentity(true, 4L);
//        userLoginIdentity(false, 5L);

        SystemConfigParam systemConfigParam = new SystemConfigParam();
        systemConfigParam.setCode(RandomCodeGenerator.generateRandomCode(6));
        systemConfigParam.setContent(RandomCodeGenerator.generateRandomCode(6));
        systemConfigParam.setSummary(RandomCodeGenerator.generateRandomCode(6));

        ActionResult update = configService.update(systemConfigParam);
        assertNotNull(update);

    }

    @Test
    public void testCreateOrUpdate() {
        userLoginIdentity(true, 3L);
//        userLoginIdentity(false, 6L);


        SystemConfigParam systemConfigParam = new SystemConfigParam();
        systemConfigParam.setCode(RandomCodeGenerator.generateRandomCode(6));
        systemConfigParam.setContent(RandomCodeGenerator.generateRandomCode(6));
        systemConfigParam.setSummary(RandomCodeGenerator.generateRandomCode(6));
        ActionResult orUpdate = configService.createOrUpdate(systemConfigParam);
        assertNotNull(orUpdate);

    }

    @Test
    public void testFind() {
        userLoginIdentity(true, 9L);
//        userLoginIdentity(false, 4L);

        DataResult<Config> configDataResult = configService.find("4TxfzW");
        assertNotNull(configDataResult.getData());
    }

    @Test
    public void testDelete() {
        userLoginIdentity(true, 11L);
//        userLoginIdentity(false, 12L);

        ActionResult result = configService.delete("4TxfzW");
        assertNotNull(result);
    }

    /**
     * Save the current user identity (administrator or normal user) and user ID to the context and database session for subsequent use.
     *
     * @param isAdmin
     * @param userId
     */
    private static void userLoginIdentity(boolean isAdmin, Long userId) {
        Context context = Context.builder().loginUser(
                LoginUser.builder().admin(isAdmin).id(userId).build()
        ).build();
        ContextUtils.setContext(context);
        Dbutils.setSession();
    }

    public class RandomCodeGenerator {
        private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        private static final SecureRandom RANDOM = new SecureRandom();

        public static String generateRandomCode(int length) {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
            }
            return sb.toString();
        }
    }
}

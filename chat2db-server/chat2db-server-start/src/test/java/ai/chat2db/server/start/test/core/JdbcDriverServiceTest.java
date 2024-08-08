package ai.chat2db.server.start.test.core;

import ai.chat2db.server.domain.api.service.JdbcDriverService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.start.test.TestApplication;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.spi.config.DBConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Juechen
 * @version : JdbcDriverServiceTest.java
 */
public class JdbcDriverServiceTest extends TestApplication {

    @Autowired
    private JdbcDriverService jdbcDriverService;

    @Test
    public void testGetDrivers() {

        userLoginIdentity(false, 2L);

        String dbType = "POSTGRESQL";
        DataResult<DBConfig> drivers = jdbcDriverService.getDrivers(dbType);
        Assertions.assertTrue(drivers.success(), drivers.errorMessage());
    }

    @Test
    public void testUpload() {

        userLoginIdentity(false, 2L);

        String dbType = "MYSQL";
        ActionResult result = jdbcDriverService.upload(dbType, "com.mysql.cj.jdbc.Driver", "mysql-connector-java-8.0.30.jar");
        Assertions.assertTrue(result.success(), result.errorMessage());
    }

    @Test
    public void testDownload() {
        userLoginIdentity(false, 5L);

        String dbType = "ORACLE";
        DataResult<DBConfig> drivers = jdbcDriverService.getDrivers(dbType);
        Assertions.assertTrue(drivers.success(), drivers.errorMessage());
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
}

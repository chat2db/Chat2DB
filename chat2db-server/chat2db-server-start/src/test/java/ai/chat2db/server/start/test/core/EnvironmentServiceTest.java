package ai.chat2db.server.start.test.core;

import ai.chat2db.server.domain.api.model.Environment;
import ai.chat2db.server.domain.api.param.EnvironmentPageQueryParam;
import ai.chat2db.server.domain.api.service.EnvironmentService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.start.test.TestApplication;
import ai.chat2db.server.start.test.dialect.DialectProperties;
import ai.chat2db.server.start.test.dialect.TestUtils;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Juechen
 * @version : EnvironmentServiceTest.java
 */
public class EnvironmentServiceTest extends TestApplication {

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private List<DialectProperties> dialectPropertiesList;

    @Test
    public void testListQuery() {

        userLoginIdentity(false, 6L);

        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            ArrayList<Long> list = new ArrayList<>();
            list.add(1L);
            list.add(2L);
            list.add(3L);

            ListResult<Environment> query = environmentService.listQuery(list);
            Assertions.assertTrue(query.getSuccess(), query.getErrorMessage());
            Assertions.assertFalse(query.getData().isEmpty(), "Result should not be empty for non-empty input list");
        }
    }

    @Test
    public void testPageQuery() {

        userLoginIdentity(false, 3L);

        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            EnvironmentPageQueryParam param = new EnvironmentPageQueryParam();
            param.setSearchKey("release");
//            param.setSearchKey("test");
            param.setPageNo(1);
            param.setPageSize(10);

            PageResult<Environment> query = environmentService.pageQuery(param);
            Assertions.assertTrue(query.getSuccess(), query.getErrorMessage());
            Assertions.assertFalse(query.getData().isEmpty(), "Result should not be empty for non-empty input list");
        }
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

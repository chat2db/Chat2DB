package ai.chat2db.server.start.test.core;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.service.DataSourceAccessBusinessService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.start.test.TestApplication;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DataSourceAccessBusinessServiceTest extends TestApplication {

    @Autowired
    private DataSourceAccessBusinessService dataSourceAccessBusinessService;

    /**
     * 1. First, determine whether it is a private data source (PRIVATE) based on the type of the data source.
     * If it is a private data source, determine whether the currently logged-in user is the owner of the data source.
     * If so, allow the operation, otherwise throw a permission exception.
     * <p>
     * 2. If the currently logged-in user is an administrator userLoginIdentity(true, **), the operation is allowed.
     * If the currently logged-in user is a common user, determine whether the user has permission to access the data source.
     * If so, the operation is allowed.
     * <p>
     * 3. If the team to which the currently logged-in user belongs has permission to access the data source, the operation is allowed.
     * <p>
     * 4.  If none of the above conditions are met, a permission exception is thrown.
     */
    @Test
    public void testCheckPermission() {
//        userLoginIdentity(false, 3L);
        userLoginIdentity(true, 2L);

        DataSource source = new DataSource();
//        source.setKind("PRIVATE");
        source.setKind("SHARED");
        source.setUserId(5L);
        source.setId(3L);

        ActionResult actionResult = dataSourceAccessBusinessService.checkPermission(source);
        assertNotNull(actionResult);
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

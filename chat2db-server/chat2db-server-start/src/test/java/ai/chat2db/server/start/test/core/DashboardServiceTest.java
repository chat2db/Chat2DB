package ai.chat2db.server.start.test.core;

import ai.chat2db.server.domain.api.model.Dashboard;
import ai.chat2db.server.domain.api.param.dashboard.DashboardCreateParam;
import ai.chat2db.server.domain.api.param.dashboard.DashboardPageQueryParam;
import ai.chat2db.server.domain.api.param.dashboard.DashboardQueryParam;
import ai.chat2db.server.domain.api.param.dashboard.DashboardUpdateParam;
import ai.chat2db.server.domain.api.service.DashboardService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.start.test.TestApplication;
import ai.chat2db.server.tools.base.enums.YesOrNoEnum;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DashboardServiceTest extends TestApplication {

    @Autowired
    private DashboardService dashboardService;

    @Test
    public void testCreateWithPermission() {
        userLoginIdentity(false, 9L);
//        userLoginIdentity(true, 11L);

        DashboardCreateParam dashboardCreateParam = new DashboardCreateParam();
        dashboardCreateParam.setName("chat2db");
        dashboardCreateParam.setSchema("test");
        dashboardCreateParam.setDescription("This is a test!");
        dashboardCreateParam.setDeleted(YesOrNoEnum.NO.getLetter());
        dashboardCreateParam.setUserId(5L);
        dashboardCreateParam.setChartIds(new ArrayList<Long>());

        DataResult<Long> withPermission = dashboardService.createWithPermission(dashboardCreateParam);
        assertNotNull(withPermission);
    }

    @Test
    public void testUpdateWithPermission() {
        // Note: Only administrators can edit this.
        userLoginIdentity(true, 9L);

        DashboardUpdateParam dashboardUpdateParam = new DashboardUpdateParam();
        dashboardUpdateParam.setId(1L);
        dashboardUpdateParam.setName("chat2db");
        dashboardUpdateParam.setSchema("test");
        dashboardUpdateParam.setDescription("This is a test!");
        dashboardUpdateParam.setDeleted(YesOrNoEnum.NO.getLetter());
        dashboardUpdateParam.setUserId(5L);
        dashboardUpdateParam.setChartIds(new ArrayList<Long>());

        ActionResult actionResult = dashboardService.updateWithPermission(dashboardUpdateParam);
        assertNotNull(actionResult);

    }

    @Test
    public void testFind() {
        userLoginIdentity(false, 4L);
//        userLoginIdentity(true, 2L);

        DataResult<Dashboard> find = dashboardService.find(2L);
        assertNotNull(find.getData());
    }

    @Test
    public void testQueryExistent() {
        userLoginIdentity(false, 8L);

        DashboardQueryParam param = new DashboardQueryParam();
        param.setId(5L);
        param.setUserId(9L);

        DataResult<Dashboard> existent = dashboardService.queryExistent(param);
        DataResult<Dashboard> dashboardDataResult = dashboardService.queryExistent(5L);
        assertNotNull(existent.getData());
        assertNotNull(dashboardDataResult.getData());
        assertEquals(existent, dashboardDataResult);
    }

    @Test
    public void testDeleteWithPermission() {
        userLoginIdentity(false, 7L);
//        userLoginIdentity(true, 4L);

        DataResult<Dashboard> dashboardDataResult = dashboardService.find(4L);
        if (dashboardDataResult.getData() != null) {
            ActionResult actionResult = dashboardService.deleteWithPermission(dashboardDataResult.getData().getId());
            assertNotNull(actionResult);
        }

    }

    @Test
    public void testQueryPage() {
        userLoginIdentity(false, 12L);

        DashboardPageQueryParam param = new DashboardPageQueryParam();
        param.setUserId(5L);
        param.setSearchKey("chat");

        PageResult<Dashboard> queryPage = dashboardService.queryPage(param);
        assertNotNull(queryPage.getData());
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

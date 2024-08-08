package ai.chat2db.server.start.test.core;

import ai.chat2db.server.domain.api.chart.ChartCreateParam;
import ai.chat2db.server.domain.api.chart.ChartListQueryParam;
import ai.chat2db.server.domain.api.chart.ChartQueryParam;
import ai.chat2db.server.domain.api.chart.ChartUpdateParam;
import ai.chat2db.server.domain.api.model.Chart;
import ai.chat2db.server.domain.api.service.ChartService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.start.test.TestApplication;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class ChartServiceTest extends TestApplication {

    @Autowired
    private ChartService chartService;


    @Test
    public void testCreateWithPermission() {
        userLoginIdentity(false, 4L);
//        userLoginIdentity(true, 3L);

        ChartCreateParam createParam = new ChartCreateParam();
        Optional.of(createParam).ifPresent(param -> {
            param.setName("chat2db");
            param.setSchema("test");
            param.setDataSourceId(1L);
            param.setType("MYSQL");
            param.setDatabaseName("chat2db");
            param.setSchemaName("ali_dbhub");
            param.setDdl("test");
        });

        DataResult<Long> withPermission = chartService.createWithPermission(createParam);
        assertNotNull(withPermission);

        Long id = withPermission.getData();
        chartService.find(id);

    }


    @Test
    public void testUpdateWithPermission() {
        userLoginIdentity(false, 1L);
//        userLoginIdentity(true, 4L);

        ChartUpdateParam chartUpdateParam = new ChartUpdateParam();
        Optional.of(chartUpdateParam).ifPresent(param -> {
            param.setId(1L);
            param.setName("chat2db");
            param.setSchema("test");
            param.setDataSourceId(1L);
            param.setType("DM");
            param.setDatabaseName("chat2db");
            param.setSchemaName("ali_dbhub");
            param.setDdl("test");
        });

        ActionResult actionResult = chartService.updateWithPermission(chartUpdateParam);
        assertNotNull(actionResult);
    }


    @Test
    public void testFind() {
        userLoginIdentity(false, 6L);
//        userLoginIdentity(true, 8L);

        DataResult<Chart> result = chartService.find(2L);
        assertNotNull(result.getData());
    }


    @Test
    public void testQueryExistent() {
        userLoginIdentity(false, 7L);
//        userLoginIdentity(true, 9L);

        ChartQueryParam chartQueryParam = new ChartQueryParam();
        chartQueryParam.setId(1L);
        chartQueryParam.setUserId(1L);

        DataResult<Chart> chartDataResult = chartService.queryExistent(chartQueryParam);
        DataResult<Chart> queryExistent = chartService.queryExistent(chartDataResult.getData().getId());
        assertNotNull(chartDataResult);
        assertNotNull(queryExistent);
        assertEquals(chartDataResult, queryExistent);
    }


    @Test
    public void testListQuery() {
        userLoginIdentity(false, 8L);
//        userLoginIdentity(true, 10L);

        ChartListQueryParam param = new ChartListQueryParam();
        param.setIdList(Arrays.asList(4L, 5L, 6L));
        param.setUserId(1L);

        ListResult<Chart> listQuery = chartService.listQuery(param);
        assertNotNull(listQuery);

    }


    @Test
    public void testQueryByIds() {
        userLoginIdentity(false, 9L);
//        userLoginIdentity(true, 11L);

        ListResult<Chart> chartListResult = chartService.queryByIds(Arrays.asList(1L, 2L, 3L));
        assertNotNull(chartListResult.getData());
    }

    @Test
    public void testDeleteWithPermission() {
        userLoginIdentity(false, 10L);
//        userLoginIdentity(true, 12L);

        ActionResult actionResult = chartService.deleteWithPermission(3L);
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

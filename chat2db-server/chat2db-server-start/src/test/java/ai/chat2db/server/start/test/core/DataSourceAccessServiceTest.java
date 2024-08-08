package ai.chat2db.server.start.test.core;

import ai.chat2db.server.domain.api.model.DataSourceAccess;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.param.datasource.access.DataSourceAccessComprehensivePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.access.DataSourceAccessCreatParam;
import ai.chat2db.server.domain.api.param.datasource.access.DataSourceAccessPageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.access.DataSourceAccessSelector;
import ai.chat2db.server.domain.api.service.DataSourceAccessService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.start.test.TestApplication;
import ai.chat2db.server.start.test.dialect.TestUtils;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DataSourceAccessServiceTest extends TestApplication {

    @Autowired
    private DataSourceAccessService dataSourceAccessService;

    @Test
    public void testPageQuery() {
//        userLoginIdentity(true,5L);
        userLoginIdentity(false, 2L);


        DataSourceAccessPageQueryParam queryParam = new DataSourceAccessPageQueryParam();
        queryParam.setDataSourceId(TestUtils.nextLong());
//        queryParam.setAccessObjectType("TEAM");
        queryParam.setAccessObjectType("USER");
        queryParam.setAccessObjectId(TestUtils.nextLong());
        queryParam.setPageNo(3);
        queryParam.setPageSize(5);

        // Returns false by default
        queryParam.setEnableReturnCount(true);


        DataSourceAccessSelector accessSelector = new DataSourceAccessSelector();
        accessSelector.setAccessObject(true);
        accessSelector.setDataSource(true);
        accessSelector.setDataSourceSelector(new DataSourceSelector(true));

        PageResult<DataSourceAccess> result = dataSourceAccessService.pageQuery(queryParam, accessSelector);
        assertNotNull(result);

    }

    @Test
    public void testComprehensivePageQuery() {

        userLoginIdentity(false, 2L);
//        userLoginIdentity(true,5L);

        DataSourceAccessComprehensivePageQueryParam param = new DataSourceAccessComprehensivePageQueryParam();
        param.setPageNo(1);
        param.setPageSize(10);
        param.setEnableReturnCount(true);
        param.setDataSourceId(TestUtils.nextLong());
        param.setAccessObjectType("USER");
//        param.setAccessObjectType("TEAM");
        param.setAccessObjectId(TestUtils.nextLong());
        param.setUserOrTeamSearchKey("test");
        param.setDataSourceSearchKey("m");

        DataSourceAccessSelector selector = new DataSourceAccessSelector();
        selector.setAccessObject(true);
        selector.setDataSource(true);
        selector.setDataSourceSelector(new DataSourceSelector(true));

        PageResult<DataSourceAccess> result = dataSourceAccessService.comprehensivePageQuery(param, selector);
        assertNotNull(result);
    }

    @Test
    public void testCreateAndDelete() {

        userLoginIdentity(false, 8L);
//        userLoginIdentity(true,6L);

        DataSourceAccessCreatParam creatParam = new DataSourceAccessCreatParam();
        creatParam.setDataSourceId(TestUtils.nextLong());
        creatParam.setAccessObjectId(TestUtils.nextLong());
        creatParam.setAccessObjectType("USER");
//        creatParam.setAccessObjectType("TEAM");

        DataResult<Long> result = dataSourceAccessService.create(creatParam);
        assertNotNull(result);
        ActionResult delete = dataSourceAccessService.delete(result.getData());
        assertNotNull(delete);

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

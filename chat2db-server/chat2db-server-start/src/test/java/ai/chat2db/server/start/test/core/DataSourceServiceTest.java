package ai.chat2db.server.start.test.core;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.datasource.*;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.start.test.TestApplication;
import ai.chat2db.server.start.test.dialect.DialectProperties;
import ai.chat2db.server.start.test.dialect.TestUtils;
import ai.chat2db.server.tools.base.wrapper.param.OrderBy;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.spi.config.DriverConfig;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.KeyValue;
import ai.chat2db.spi.model.SSHInfo;
import ai.chat2db.spi.model.SSLInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DataSourceServiceTest extends TestApplication {

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private List<DialectProperties> dialectPropertiesList;

    @Test
    public void testCreateWithPermission() {
//        userLoginIdentity(true, 1L);
        userLoginIdentity(false, 2L);

        DataSourceCreateParam createParam = new DataSourceCreateParam();
        createParam.setKind("PRIVATE");
//        createParam.setKind("SHARED");
        createParam.setDriverConfig(new DriverConfig());

        DataResult<Long> withPermission = dataSourceService.createWithPermission(createParam);
        assertNotNull(withPermission.getData());

    }

    @Test
    public void testUpdateWithPermission() {
//        userLoginIdentity(true, 7L);
        userLoginIdentity(false, 2L);

        DataSourceUpdateParam updateParam = new DataSourceUpdateParam();
        updateParam.setId(4L);
        updateParam.setDriverConfig(new DriverConfig());
        updateParam.setPassword("123456");

        DataResult<Long> result = dataSourceService.updateWithPermission(updateParam);
        ActionResult delete = dataSourceService.deleteWithPermission(4L);
        assertNotNull(result.getData());
        assertNotNull(delete);

    }

    @Test
    public void testQueryById() {
        userLoginIdentity(false, 2L);
//        userLoginIdentity(true, 7L);

        DataResult<DataSource> result = dataSourceService.queryById(3L);
        ListResult<DataSource> dataSourceListResult = dataSourceService.listQuery(new ArrayList<>(), null);
        assertNotNull(result.getData());
        assertNotNull(dataSourceListResult.getData());
    }

    @Test
    public void testQueryExistent() {
        userLoginIdentity(false, 2L);
//        userLoginIdentity(true, 7L);

        DataSourceSelector selector = new DataSourceSelector();
        selector.setEnvironment(true);
//        selector.setEnvironment(false);

        DataResult<DataSource> result = dataSourceService.queryExistent(3L, null);
        assertNotNull( result.getData(),"Data should not be null");
    }

    @Test
    public void testCopyByIdWithPermission() {
        userLoginIdentity(false, 2L);
//        userLoginIdentity(true, 7L);

        DataResult<Long> longDataResult = dataSourceService.copyByIdWithPermission(3L);
        assertNotNull(longDataResult.getData());

    }

    @Test
    public void testQueryPage() {
        userLoginIdentity(false,6L);
//        userLoginIdentity(true,9L);

        DataSourcePageQueryParam queryParam = new DataSourcePageQueryParam();
        queryParam.setSearchKey("test");
        queryParam.setPageNo(1);
        queryParam.setPageSize(10);

        DataSourceSelector selector = new DataSourceSelector();
        selector.setEnvironment(true);
//        selector.setEnvironment(false);

        PageResult<DataSource> result = dataSourceService.queryPage(queryParam, selector);
        assertNotNull(result.getData());
    }

    @Test
    public void testQueryPageWithPermission() {
//        userLoginIdentity(false,3L);
        userLoginIdentity(true,9L);

        DataSourcePageQueryParam queryParam = new DataSourcePageQueryParam();
        queryParam.setSearchKey("test");
        queryParam.setKind("PRIVATE");
//        queryParam.setKind("SHARED");
        queryParam.setPageNo(1);
        queryParam.setPageSize(10);
        queryParam.setOrderByList(new ArrayList<OrderBy>());

        DataSourceSelector selector = new DataSourceSelector();
        selector.setEnvironment(true);

        PageResult<DataSource> result = dataSourceService.queryPageWithPermission(queryParam, selector);
        assertNotNull(result.getData());

    }

    @Test
    public void testPreConnect() {

        for (DialectProperties dialectProperties : dialectPropertiesList) {

            DataSourcePreConnectParam param = new DataSourcePreConnectParam();
            param.setType(dialectProperties.getDbType());
            param.setUser(dialectProperties.getUsername());
            param.setUrl(dialectProperties.getUrl());
            param.setPassword(dialectProperties.getPassword());
            param.setPort(String.valueOf(dialectProperties.getPort()));
            param.setHost("localhost");
            param.setSsh(new SSHInfo());
            param.setSsl(new SSLInfo());
            param.setExtendInfo(new ArrayList<KeyValue>());

            ActionResult result = dataSourceService.preConnect(param);
            assertNotNull(result);

            Long consoleId= TestUtils.nextLong();
            Long dataSourceId= TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties,dataSourceId,consoleId);
            ListResult<Database> connect = dataSourceService.connect(dataSourceId);
            assertNotNull(connect.getData());

            dataSourceService.close(dataSourceId);

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

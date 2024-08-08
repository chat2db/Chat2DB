package ai.chat2db.server.start.test.core;

import ai.chat2db.server.domain.api.param.DlCountParam;
import ai.chat2db.server.domain.api.param.DlExecuteParam;
import ai.chat2db.server.domain.api.param.OrderByParam;
import ai.chat2db.server.domain.api.param.UpdateSelectResultParam;
import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.start.test.TestApplication;
import ai.chat2db.server.start.test.dialect.DialectProperties;
import ai.chat2db.server.start.test.dialect.TestUtils;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.model.Header;
import ai.chat2db.spi.model.OrderBy;
import ai.chat2db.spi.model.ResultOperation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class DlTemplateServiceTest extends TestApplication {

    @Autowired
    private DlTemplateService dlTemplateService;

    @Autowired
    private List<DialectProperties> dialectPropertiesList;

    // MYSQL: ali_dbhub_test -- test_data
    // POSTGRESQL: ali_dbhub_test -- test -- test_data
    // ORACLE: TEST_USER -- test_data
    @Test
    public void testExecute() {

        userLoginIdentity(false, 6L);

        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = 11L;
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            String testData = dialectProperties.getCrateTableSql("test_data006");
            DlExecuteParam dlExecuteParam = new DlExecuteParam();
            dlExecuteParam.setSql(testData);
            dlExecuteParam.setConsoleId(consoleId);
            dlExecuteParam.setDataSourceId(dataSourceId);
            dlExecuteParam.setTableName("test_data006");
            dlExecuteParam.setPageNo(1);
            dlExecuteParam.setPageSize(10);
            dlExecuteParam.setPageSizeAll(false);
            if (dialectProperties.getDbType().equals("POSTGRESQL")) {
                dlExecuteParam.setDatabaseName(dialectProperties.getDatabaseName());
                dlExecuteParam.setSchemaName("public");
            } else if (dialectProperties.getDbType().equals("ORACLE")) {
                dlExecuteParam.setDatabaseName("");
                dlExecuteParam.setSchemaName("TEST_USER");
            } else if (dialectProperties.getDbType().equals("MYSQL")) {
                dlExecuteParam.setDatabaseName(dialectProperties.getDatabaseName());
                dlExecuteParam.setSchemaName("");
            } else {
                continue;
            }


            ListResult<ExecuteResult> execute = dlTemplateService.execute(dlExecuteParam);
            Assertions.assertTrue(execute.getSuccess(), execute.errorMessage());

        }

    }

    @Test
    public void testExecuteSelectTable() {

        userLoginIdentity(false, 3L);

        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = 20858L;
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            if (dialectProperties.getDbType().equals("MYSQL")) {
                DlExecuteParam dlExecuteParam = new DlExecuteParam();
                dlExecuteParam.setConsoleId(consoleId);
                dlExecuteParam.setDataSourceId(dataSourceId);
                dlExecuteParam.setTableName("test_data004");
                dlExecuteParam.setPageNo(1);
                dlExecuteParam.setPageSize(10);
                dlExecuteParam.setPageSizeAll(false);

                ListResult<ExecuteResult> execute = dlTemplateService.executeSelectTable(dlExecuteParam);
                Assertions.assertTrue(execute.getSuccess(), execute.errorMessage());
            }

        }
    }

    @Test
    public void testExecuteUpdate() {

        userLoginIdentity(false, 7L);

        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            String testData = dialectProperties.getInsertSql("test_data006", new Timestamp(new Date().getTime()), 1L, "test");
            DlExecuteParam dlExecuteParam = new DlExecuteParam();
            dlExecuteParam.setSql(testData);
            dlExecuteParam.setConsoleId(consoleId);
            dlExecuteParam.setDataSourceId(dataSourceId);
            dlExecuteParam.setTableName("test_data006");
            dlExecuteParam.setPageNo(1);
            dlExecuteParam.setPageSize(10);
            dlExecuteParam.setPageSizeAll(false);
            if (dialectProperties.getDbType().equals("POSTGRESQL")) {
                dlExecuteParam.setDatabaseName(dialectProperties.getDatabaseName());
                dlExecuteParam.setSchemaName("public");
            } else if (dialectProperties.getDbType().equals("ORACLE")) {
                dlExecuteParam.setDatabaseName("");
                dlExecuteParam.setSchemaName("TEST_USER");
            } else if (dialectProperties.getDbType().equals("MYSQL")) {
                dlExecuteParam.setDatabaseName(dialectProperties.getDatabaseName());
                dlExecuteParam.setSchemaName("");
            } else {
                continue;
            }


            DataResult<ExecuteResult> result = dlTemplateService.executeUpdate(dlExecuteParam);
            Assertions.assertTrue(result.getSuccess(), result.errorMessage());

        }
    }

    @Test
    public void testCount() {

        userLoginIdentity(true, 8L);

        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            DlCountParam param = new DlCountParam();
            DataResult<Long> count = null;
            if (dialectProperties.getDbType().equals("MYSQL")) {
                param.setSql("select * from test_data");
                count = dlTemplateService.count(param);
                System.out.println("Mysql Total rows:" + count.getData());
                Assertions.assertTrue(count.getSuccess(), count.errorMessage());
            } else if (dialectProperties.getDbType().equals("ORACLE")) {
                param.setSql("select * from TEST_USER.DEMO");
                count = dlTemplateService.count(param);
                System.out.println("Oracle Total rows:" + count.getData());
                Assertions.assertTrue(count.getSuccess(), count.errorMessage());
            }else if (dialectProperties.getDbType().equals("POSTGRESQL")) {
                param.setSql("select * from test.reference_table");
                count = dlTemplateService.count(param);
                System.out.println("PG Total rows:" + count.getData());
                Assertions.assertTrue(count.getSuccess(), count.errorMessage());
            } else if (dialectProperties.getDbType().equals("MARIADB")) {
                param.setSql("select * from test.test_data");
                count = dlTemplateService.count(param);
                System.out.println("Mariadb Total rows:" + count.getData());
                Assertions.assertTrue(count.getSuccess(), count.errorMessage());
            }
        }

    }

    @Test
    public void testUpdateSelectResult() {

        userLoginIdentity(true, 8L);

        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            UpdateSelectResultParam param = new UpdateSelectResultParam();
            param.setTableName("test_data");
            param.setHeaderList(new ArrayList<Header>());
            param.setOperations(new ArrayList<ResultOperation>());

            DataResult<String> result = dlTemplateService.updateSelectResult(param);
            System.out.println("result:" + result.getData());
            Assertions.assertTrue(result.getSuccess(), result.errorMessage());
        }

    }

    @Test
    public void testGetOrderBySql() {

        userLoginIdentity(false, 4L);

        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            if (dialectProperties.getDbType().equals("MYSQL")) {
                ArrayList<OrderBy> orderByList = new ArrayList<>();
                OrderBy orderBy1 = new OrderBy();
                orderBy1.setColumnName("number");
                orderBy1.setAsc(true);
                orderByList.add(orderBy1);

                OrderByParam orderByParam = new OrderByParam();
                orderByParam.setOrderByList(orderByList);
                orderByParam.setOriginSql("select * from test_data");

                DataResult<String> result = dlTemplateService.getOrderBySql(orderByParam);
                System.out.println("Mysql Final Sql:" + result.getData());
                Assertions.assertTrue(result.getSuccess(), result.errorMessage());

            }
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

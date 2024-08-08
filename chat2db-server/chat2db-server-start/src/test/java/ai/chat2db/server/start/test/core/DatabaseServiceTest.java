package ai.chat2db.server.start.test.core;

import ai.chat2db.server.domain.api.param.MetaDataQueryParam;
import ai.chat2db.server.domain.api.param.SchemaOperationParam;
import ai.chat2db.server.domain.api.param.SchemaQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DatabaseCreateParam;
import ai.chat2db.server.domain.api.param.datasource.DatabaseExportParam;
import ai.chat2db.server.domain.api.param.datasource.DatabaseQueryAllParam;
import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.start.test.TestApplication;
import ai.chat2db.server.start.test.dialect.DialectProperties;
import ai.chat2db.server.start.test.dialect.TestUtils;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.MetaSchema;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.Sql;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DatabaseServiceTest extends TestApplication {

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private List<DialectProperties> dialectPropertiesList;

    @Test
    public void testQueryAll() {

        // MYSQL  ORACLE  POSTGRESQL MONGODB MARIADB
        for (DialectProperties dialectProperties : dialectPropertiesList) {
            String dbType = dialectProperties.getDbType();
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            DatabaseQueryAllParam queryAllParam = new DatabaseQueryAllParam();
            queryAllParam.setDbType(dbType);
            queryAllParam.setDataSourceId(dataSourceId);

            ListResult<Database> result = databaseService.queryAll(queryAllParam);
            assertNotNull(result.getData());
        }

    }

    @Test
    public void testQuerySchema() {
        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            SchemaQueryParam schemaQueryParam = new SchemaQueryParam();
            schemaQueryParam.setDataSourceId(dataSourceId);
            schemaQueryParam.setDataBaseName(dialectProperties.getDatabaseName());

            ListResult<Schema> schemaListResult = databaseService.querySchema(schemaQueryParam);
            assertNotNull(schemaListResult.getData());
        }
    }

    @Test
    public void testQueryDatabaseSchema() {
        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            MetaDataQueryParam param = new MetaDataQueryParam();
            param.setDataSourceId(dataSourceId);
            param.setRefresh(true);

            DataResult<MetaSchema> metaSchemaDataResult = databaseService.queryDatabaseSchema(param);
            assertNotNull(metaSchemaDataResult.getData());
        }
    }

    @Test
    public void testDeleteDatabase() {
        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            DatabaseCreateParam param = new DatabaseCreateParam();
            param.setName("test");

            ActionResult actionResult = databaseService.deleteDatabase(param);
            assertNotNull(actionResult);
        }
    }

    @Test
    public void testCreateDatabase() {
        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            Database database = new Database();
            DataResult<Sql> database1 = databaseService.createDatabase(database);
            assertNotNull(database1);
        }
    }

    @Test
    public void testModifyDatabase() {
        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            DatabaseCreateParam databaseCreateParam = new DatabaseCreateParam();
            databaseCreateParam.setName("test" + TestUtils.nextLong());

            ActionResult actionResult = databaseService.modifyDatabase(databaseCreateParam);
            assertNotNull(actionResult);

        }
    }

    @Test
    public void testDeleteSchema() {
        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            SchemaOperationParam operationParam = new SchemaOperationParam();
            operationParam.setDatabaseName(dialectProperties.getDatabaseName());
            operationParam.setSchemaName("test" + TestUtils.nextLong());

            ActionResult actionResult = databaseService.deleteSchema(operationParam);
            assertNotNull(actionResult);
        }
    }


    @Test
    public void testCreateSchema() {
        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            Schema schema = new Schema();
            DataResult<Sql> result = databaseService.createSchema(schema);
            assertNotNull(result);
        }
    }

    @Test
    public void testModifySchema() {
        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            SchemaOperationParam schemaOperationParam = new SchemaOperationParam();
            schemaOperationParam.setDatabaseName(dialectProperties.getDatabaseName());
            schemaOperationParam.setSchemaName("test" + TestUtils.nextLong());
            schemaOperationParam.setNewSchemaName("test" + TestUtils.nextLong());

            ActionResult actionResult = databaseService.modifySchema(schemaOperationParam);
            assertNotNull(actionResult);
        }
    }

    // TODO：回头专门测试
    @Test
    public void testExportDatabase() {
        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            DatabaseExportParam exportParam = new DatabaseExportParam();
            exportParam.setDatabaseName(dialectProperties.getDatabaseName());
            exportParam.setContainData(true);
            exportParam.setSchemaName("test" + TestUtils.nextLong());

            try {
                String exportDatabase = databaseService.exportDatabase(exportParam);
                assertNotNull(exportDatabase);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}

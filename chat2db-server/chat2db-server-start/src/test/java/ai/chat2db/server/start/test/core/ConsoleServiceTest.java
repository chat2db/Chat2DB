package ai.chat2db.server.start.test.core;

import ai.chat2db.server.domain.api.param.ConsoleCloseParam;
import ai.chat2db.server.domain.api.param.ConsoleConnectParam;
import ai.chat2db.server.domain.api.service.ConsoleService;
import ai.chat2db.server.start.test.TestApplication;
import ai.chat2db.server.start.test.dialect.DialectProperties;
import ai.chat2db.server.start.test.dialect.TestUtils;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConsoleServiceTest extends TestApplication {

    @Autowired
    private ConsoleService consoleService;

    @Autowired
    private List<DialectProperties> dialectPropertiesList;

    @Test
    public void testCreateAndCloseConsole() {
        // MYSQL  ORACLE  POSTGRESQL
        for (DialectProperties dialectProperties : dialectPropertiesList) {
            Long dataSourceId = TestUtils.nextLong();
            Long consoleId = TestUtils.nextLong();
            TestUtils.buildContext(dialectProperties, dataSourceId, consoleId);

            // creat
            ConsoleConnectParam consoleCreateParam = new ConsoleConnectParam();
            consoleCreateParam.setDataSourceId(dataSourceId);
            consoleCreateParam.setConsoleId(consoleId);
            consoleCreateParam.setDatabaseName(dialectProperties.getDatabaseName());
            ActionResult console = consoleService.createConsole(consoleCreateParam);
            assertNotNull(console);

            // close
            ConsoleCloseParam consoleCloseParam = new ConsoleCloseParam();
            consoleCloseParam.setDataSourceId(dataSourceId);
            consoleCloseParam.setConsoleId(consoleId);
            consoleService.closeConsole(consoleCloseParam);
            Chat2DBContext.removeContext();
        }
    }


}

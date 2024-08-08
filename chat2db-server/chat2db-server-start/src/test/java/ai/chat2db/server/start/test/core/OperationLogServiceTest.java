package ai.chat2db.server.start.test.core;

import ai.chat2db.server.domain.api.service.OperationLogService;
import ai.chat2db.server.start.test.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Juechen
 * @version : OperationLogServiceTest.java
 */
public class OperationLogServiceTest extends TestApplication {

    @Autowired
    private OperationLogService operationLogService;

    @Test
    public void testCreate() {
        operationLogService.create(null);
    }

    @Test
    public void testQueryPage() {
        operationLogService.queryPage(null);
    }

}

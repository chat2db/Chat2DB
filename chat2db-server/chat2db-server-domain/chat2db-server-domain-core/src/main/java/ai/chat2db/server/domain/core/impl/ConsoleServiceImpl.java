package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.param.ConsoleConnectParam;
import ai.chat2db.server.domain.api.service.ConsoleService;
import ai.chat2db.server.domain.api.param.ConsoleCloseParam;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;

import org.springframework.stereotype.Service;

/**
 * @author moji
 * @version DataSourceCoreServiceImpl.java, v 0.1 2022年09月23日 15:51 moji Exp $
 * @date 2022/09/23
 */
@Service
public class ConsoleServiceImpl implements ConsoleService {
    @Override
    public ActionResult createConsole(ConsoleConnectParam param) {
        Chat2DBContext.getDBManage().connectDatabase(Chat2DBContext.getConnection(),param.getDatabaseName());
        return ActionResult.isSuccess();
    }

    @Override
    public ActionResult closeConsole(ConsoleCloseParam param) {
        return ActionResult.isSuccess();
    }

}

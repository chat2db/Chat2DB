package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.service.FunctionService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.spi.model.Function;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.springframework.stereotype.Service;

@Service
public class FunctionServiceImpl implements FunctionService {
    @Override
    public ListResult<Function> functions(String databaseName, String schemaName) {
        return ListResult.of(Chat2DBContext.getMetaData().functions(Chat2DBContext.getConnection(),databaseName, schemaName));
    }

    @Override
    public DataResult<Function> detail(String databaseName, String schemaName, String functionName) {
        return DataResult.of(Chat2DBContext.getMetaData().function(Chat2DBContext.getConnection(), databaseName, schemaName, functionName));
    }

    @Override
    public ActionResult delete(String databaseName, String schemaName, Function function) {
        Chat2DBContext.getDBManage().deleteFunction(Chat2DBContext.getConnection(), databaseName, schemaName, function);
        return ActionResult.isSuccess();
    }
}

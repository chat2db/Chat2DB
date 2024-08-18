package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.service.ProcedureService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.spi.model.Procedure;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.springframework.stereotype.Service;

import java.sql.SQLException;

@Service
public class ProcedureServiceImpl implements ProcedureService {

    @Override
    public ListResult<Procedure> procedures(String databaseName, String schemaName) {
        return ListResult.of(Chat2DBContext.getMetaData().procedures(Chat2DBContext.getConnection(),databaseName, schemaName));
    }

    @Override
    public DataResult<Procedure> detail(String databaseName, String schemaName, String procedureName) {
        return DataResult.of(Chat2DBContext.getMetaData().procedure(Chat2DBContext.getConnection(), databaseName, schemaName, procedureName));
    }
    @Override
    public ActionResult update(String databaseName, String schemaName, Procedure procedure) throws SQLException {
        Chat2DBContext.getDBManage().updateProcedure(Chat2DBContext.getConnection(), databaseName, schemaName, procedure);
        return ActionResult.isSuccess();
    }

    @Override
    public ActionResult delete(String databaseName, String schemaName, Procedure procedure) {
        Chat2DBContext.getDBManage().deleteProcedure(Chat2DBContext.getConnection(), databaseName, schemaName, procedure);
        return ActionResult.isSuccess();
    }
}

package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.spi.model.Procedure;
import jakarta.validation.constraints.NotEmpty;

import java.sql.SQLException;

public interface ProcedureService {

    /**
     * Querying all procedures under a schema.
     *
     * @param databaseName
     * @return
     */
    ListResult<Procedure> procedures(@NotEmpty String databaseName, String schemaName);

    /**
     * Querying procedure information.
     *
     * @param databaseName
     * @param schemaName
     * @param procedureName
     * @return
     */
    DataResult<Procedure> detail(String databaseName, String schemaName, String procedureName);

    /**
     * Update procedure.
     *
     * @param databaseName
     * @param schemaName
     * @param procedure
     * @return
     */
    ActionResult update(String databaseName, String schemaName, Procedure procedure) throws SQLException;

    /**
     * Delete procedure.
     *
     * @param databaseName
     * @param schemaName
     * @param procedure
     * @return
     */
    ActionResult delete(String databaseName, String schemaName, Procedure procedure);
}

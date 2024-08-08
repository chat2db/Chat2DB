package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.spi.model.Function;
import jakarta.validation.constraints.NotEmpty;

/**
 * author jipengfei
 * date 2021/9/23 15:22
 */
public interface FunctionService {

    /**
     * Querying all functions under a schema.
     *
     * @param databaseName
     * @return
     */
    ListResult<Function> functions(@NotEmpty String databaseName, String schemaName);

    /**
     * Querying function information.
     *
     * @param databaseName
     * @param schemaName
     * @param functionName
     * @return
     */
    DataResult<Function> detail(String databaseName, String schemaName, String functionName);

    /**
     * Delete function.
     *
     * @param databaseName
     * @param schemaName
     * @param function
     * @return
     */
    ActionResult delete(String databaseName, String schemaName, Function function);

}

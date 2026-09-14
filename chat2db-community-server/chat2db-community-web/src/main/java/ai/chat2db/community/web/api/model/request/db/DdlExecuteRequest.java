package ai.chat2db.community.web.api.model.request.db;

import ai.chat2db.community.web.api.model.request.data.source.IDataSourceSchemaRequestInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DdlExecuteRequest implements IDataSourceSchemaRequestInfo {

    @NotNull
    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    @NotBlank
    private String sql;

    private String tableName;

    /** Omit to retain the executor's default; false stops after the first failed statement. */
    private Boolean errorContinue;
}

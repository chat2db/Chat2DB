package ai.chat2db.server.domain.api.param.datasource;

import ai.chat2db.server.tools.common.model.data.option.AbstractImportDataOptions;
import ai.chat2db.server.tools.common.model.data.option.table.ImportTableOptions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述
 *
 * @author: zgq
 * @date: 2024年04月23日 13:52
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseImportDataParam {
    private String databaseName;
    private String schemaName;
    private ImportTableOptions importTableOption;
    private AbstractImportDataOptions importDataOption;
}

package ai.chat2db.server.web.api.controller.rdb.request;

import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequest;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author: zgq
 * @date: 2024年03月24日 12:36
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseExportDataRequest extends DataSourceBaseRequest {
    @NotNull
    private String exportType;
    @NotEmpty
    private List<String> tableNames;
    /**
     * single：单行插入，multi：多行插入，update：更新语句
     */
    private String sqyType;
    private Boolean containsHeader;
}
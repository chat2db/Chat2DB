package ai.chat2db.server.domain.api.param;

import ai.chat2db.spi.model.Header;
import ai.chat2db.spi.model.ResultOperation;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateSelectResultParam {
    /**
     * 控制台id
     */
    @NotNull
    private Long consoleId;

    /**
     * 数据源id
     */
    @NotNull
    private Long dataSourceId;

    /**
     * DB名称
     */
    private String databaseName;


    /**
     * schema名称
     */
    private String schemaName;


    /**
     * 展示头的列表
     */
    @NotEmpty
    private List<Header> headerList;


    /**
     * 修改后数据的列表
     */
    @NotEmpty
    private List<ResultOperation> operations;


    /**
     * 表名
     */
    @NotEmpty
    private String tableName;
}

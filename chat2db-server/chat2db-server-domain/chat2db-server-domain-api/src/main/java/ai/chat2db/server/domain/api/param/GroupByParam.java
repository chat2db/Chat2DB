package ai.chat2db.server.domain.api.param;


import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;


@Data
public class GroupByParam {

    /**
     * console id
     */
    @NotNull
    private Long consoleId;

    /**
     * Data source id
     */
    @NotNull
    private Long dataSourceId;

    /**
     * databaseName
     */
    private String databaseName;


    /**
     * schema name
     */
    private String schemaName;


    /**
     * origin sql
     */
    private String originSql;


    /**
     * sort field
     */
    private List<String> groupByList;
}

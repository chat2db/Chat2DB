package ai.chat2db.spi.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class Command  implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * sql statement
     */
    @NotNull
    private String script;

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
     * DB name
     */
    @NotNull
    private String databaseName;

    /**
     * schema name
     */
    private String schemaName;

    /**
     *
     */
    private String tableName;

    /**
     *Page coding
      * Only available for select statements
     */
    private Integer pageNo;

    /**
     * Paging Size
      * Only available for select statements
     */
    private Integer pageSize;

    /**
     * Return all data
     * Only available for select statements
     */
    private Boolean pageSizeAll;

    /**
     * single SQL
     */
    private boolean single;
}

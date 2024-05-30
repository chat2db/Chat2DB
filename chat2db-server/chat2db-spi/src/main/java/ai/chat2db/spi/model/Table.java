package ai.chat2db.spi.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.List;

/**
 * Table information
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Table implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Table Name
     */
    @JsonAlias({"TABLE_NAME"})
    private String name;

    /**
     * description
     */
    @JsonAlias({"REMARKS"})

    private String comment;

    /**
     * DB name
     */
    @JsonAlias({"TABLE_SCHEM"})

    private String schemaName;

    /**
     * columnList
     */
    private List<TableColumn> columnList;

    /**
     * indexList
     */
    private List<TableIndex> indexList;

    /**
     * DB type
     */
    private String dbType;

    /**
     * Database name
     */
    @JsonAlias("TABLE_CAT")
    private String databaseName;

    /**
     * table type
     */
    @JsonAlias("TABLE_TYPE")
    private String type;

    /**
     * Whether to pin it to the top
     */
    private boolean pinned;

    /**
     * ddl
     */
    private String ddl;

    /**
     * engine
     */
    @JsonAlias("TYPE_NAME")
    private String engine;


    private String charset;


    private String collate;


    private Long incrementValue;


    private String partition;


    private String tablespace;

    private Long rows;

    private Long dataLength;

    private String createTime;

    private String updateTime;
}


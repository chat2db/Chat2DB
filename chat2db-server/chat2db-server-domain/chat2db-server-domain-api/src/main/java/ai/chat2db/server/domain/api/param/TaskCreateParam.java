package ai.chat2db.server.domain.api.param;

import lombok.Data;

import java.io.Serializable;

@Data
public class TaskCreateParam implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据源连接ID
     */
    private Long dataSourceId;

    /**
     * db名称
     */
    private String databaseName;

    /**
     * schema名称
     */
    private String schemaName;

    /**
     * table_name
     */
    private String tableName;

    /**
     * 用户id
     */
    private Long userId;


    /**
     * task progress
     */
    private String taskProgress;

    /**
     * task name
     */
    private String taskName;

    /**
     * task type, such as: DOWNLOAD_DATA, UPLOAD_TABLE_DATA, DOWNLOAD_TABLE_STRUCTURE, UPLOAD_TABLE_STRUCTURE,
     */
    private String taskType;


}

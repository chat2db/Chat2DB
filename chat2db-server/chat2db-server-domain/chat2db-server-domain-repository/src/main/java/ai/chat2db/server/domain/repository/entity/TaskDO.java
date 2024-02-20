package ai.chat2db.server.domain.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * TASK TABLE
 * </p>
 *
 * @author chat2db
 * @since 2024-01-25
 */
@Getter
@Setter
@TableName("TASK")
public class TaskDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    /**
     * 创建时间
     */
    private Date gmtCreate;

    /**
     * 修改时间
     */
    private Date gmtModified;

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
     * 是否被删除,y表示删除,n表示未删除
     */
    private String deleted;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * task type, such as: DOWNLOAD_DATA, UPLOAD_TABLE_DATA, DOWNLOAD_TABLE_STRUCTURE, UPLOAD_TABLE_STRUCTURE,
     */
    private String taskType;

    /**
     * task status
     */
    private String taskStatus;

    /**
     * task progress
     */
    private String taskProgress;

    /**
     * task name
     */
    private String taskName;

    /**
     * download url
     */
    private String downloadUrl;

    /**
     * task content
     */
    private byte[] content;
}

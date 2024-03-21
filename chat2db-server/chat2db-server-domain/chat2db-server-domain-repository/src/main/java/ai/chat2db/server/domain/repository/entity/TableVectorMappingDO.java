package ai.chat2db.server.domain.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * milvus映射表保存记录
 * </p>
 *
 * @author chat2db
 * @since 2023-10-14
 */
@Getter
@Setter
@TableName("TABLE_VECTOR_MAPPING")
public class TableVectorMappingDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    /**
     * api key
     */
    private String apiKey;

    /**
     * 数据源连接ID
     */
    private Long dataSourceId;

    /**
     * 数据库名称
     */
    private String database;

    /**
     * schema名称
     */
    @TableField("`schema`")
    private String schema;

    /**
     * 向量保存状态
     */
    private String status;
}

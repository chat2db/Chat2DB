package ai.chat2db.server.domain.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 自定义报表表
 * </p>
 *
 * @author chat2db
 * @since 2023-09-09
 */
@Getter
@Setter
@TableName("CHART")
public class ChartDO implements Serializable {

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
     * 图表名称
     */
    private String name;

    /**
     * 图表描述
     */
    private String description;

    /**
     * 图表信息
     */
    @TableField("`schema`")
    private String schema;

    /**
     * 数据源连接ID
     */
    private Long dataSourceId;

    /**
     * 数据库类型
     */
    private String type;

    /**
     * db名称
     */
    private String databaseName;


    private String schemaName;

    /**
     * ddl内容
     */
    private String ddl;

    /**
     * 是否被删除,y表示删除,n表示未删除
     */
    private String deleted;

    /**
     * 用户id
     */
    private Long userId;
}

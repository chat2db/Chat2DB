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
 * 数据源连接表
 * </p>
 *
 * @author chat2db
 * @since 2023-08-26
 */
@Getter
@Setter
@TableName("DATA_SOURCE")
public class DataSourceDO implements Serializable {

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
     * 别名
     */
    private String alias;

    /**
     * 连接地址
     */
    private String url;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 密码
     */
    private String password;

    /**
     * 数据库类型
     */
    private String type;

    /**
     * 环境类型
     */
    private String envType;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * host地址
     */
    private String host;

    /**
     * 端口
     */
    private String port;

    /**
     * ssh配置信息json
     */
    private String ssh;

    /**
     * ssl配置信息json
     */
    @TableField("`ssl`")
    private String ssl;

    /**
     * sid
     */
    private String sid;

    /**
     * 驱动信息
     */
    private String driver;

    /**
     * jdbc版本
     */
    private String jdbc;

    /**
     * 自定义扩展字段json
     */
    private String extendInfo;

    /**
     * driver_config配置
     */
    private String driverConfig;

    /**
     * 环境id
     */
    private Long environmentId;

    /**
     * 连接类型
     */
    private String kind;

    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 服务类型
     */
    private String serviceType;

}

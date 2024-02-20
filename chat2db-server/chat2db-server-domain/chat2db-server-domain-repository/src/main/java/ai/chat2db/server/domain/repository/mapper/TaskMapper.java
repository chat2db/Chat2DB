package ai.chat2db.server.domain.repository.mapper;

import ai.chat2db.server.domain.repository.entity.TaskDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * TASK TABLE Mapper 接口
 * </p>
 *
 * @author chat2db
 * @since 2024-01-25
 */
public interface TaskMapper extends BaseMapper<TaskDO> {

    IPage<TaskDO> pageQuery(IPage<TaskDO> page, @Param("userId") Long userId,@Param("deleted") String deleted);
}

package ai.chat2db.server.domain.core.converter;

import ai.chat2db.server.domain.api.model.Task;
import ai.chat2db.server.domain.api.param.TaskCreateParam;
import ai.chat2db.server.domain.repository.entity.TaskDO;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;

import java.util.List;
@Slf4j
@Mapper(componentModel = "spring")
public abstract class TaskConverter {

    public abstract TaskDO todo(TaskCreateParam param);


    public abstract Task toModel(TaskDO param);


    public abstract List<Task> toModel(List<TaskDO> param);
}

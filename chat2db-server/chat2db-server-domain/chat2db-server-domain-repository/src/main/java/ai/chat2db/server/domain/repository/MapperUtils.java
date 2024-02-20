package ai.chat2db.server.domain.repository;

import ai.chat2db.server.domain.repository.mapper.TaskMapper;

public class MapperUtils {

    public static TaskMapper getTaskMapper() {
        return Dbutils.getMapper(TaskMapper.class);
    }
}

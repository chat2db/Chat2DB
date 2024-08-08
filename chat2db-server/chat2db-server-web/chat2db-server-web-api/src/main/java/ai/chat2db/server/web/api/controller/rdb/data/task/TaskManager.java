package ai.chat2db.server.web.api.controller.rdb.data.task;

import ai.chat2db.server.domain.api.enums.TaskStatusEnum;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


public class TaskManager {
    public static final ThreadLocal<Long> TASK_ID = new ThreadLocal<>();
    public static final Map<Long, TaskState> taskMap = new ConcurrentHashMap<>();


    public static void increaseCurrent(int current) {
        TaskState task = getTask();
        task.setCurrent(task.getCurrent() + current);
        if (task.getCurrent() >= task.getTotal()) {
            task.setState(TaskStatusEnum.FINISH.name());
        }
    }

    public static void increaseCurrent() {
        TaskState task = getTask();
        task.setCurrent(task.getCurrent() +1);
        if (task.getCurrent() >= task.getTotal()) {
            task.setState(TaskStatusEnum.FINISH.name());
        }
    }

    public static void updateStatus(TaskStatusEnum status) {
        TaskState task = getTask();
        task.setState(status.name());
    }


    public static void addTask(Long taskId, TaskState taskState) {
        setTaskId(taskId);
        taskMap.put(taskId, taskState);
    }

    public static TaskState getTask(Long taskId) {
        TaskState taskState = taskMap.get(taskId);
        if (Objects.isNull(taskState)) {
            throw new IllegalArgumentException("taskId is not valid");
        }
        return taskState;
    }

    public static TaskState getTask() {
        return getTask(getTaskId());
    }

    public static void removeTask(Long taskId) {
        taskMap.remove(taskId);
    }

    public static void setTaskId(Long taskId) {
        TASK_ID.set(taskId);
    }

    public static Long getTaskId() {
        return TASK_ID.get();
    }

    public static void removeTaskId() {
        TASK_ID.remove();
    }
}

package ai.chat2db.community.domain.core.impl.task;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.domain.api.model.task.ExportTaskSpec;
import ai.chat2db.community.domain.api.model.task.ImportTaskSpec;
import ai.chat2db.community.domain.api.model.task.Task;
import ai.chat2db.community.domain.api.model.task.TaskArtifact;
import ai.chat2db.community.domain.api.model.task.TaskConstants;
import ai.chat2db.community.domain.api.model.task.TaskDownload;
import ai.chat2db.community.domain.api.model.task.TaskEvent;
import ai.chat2db.community.domain.api.model.task.TaskEventCode;
import ai.chat2db.community.domain.api.model.task.TaskEventLevel;
import ai.chat2db.community.domain.api.model.task.TaskQuery;
import ai.chat2db.community.domain.api.model.task.TaskSpec;
import ai.chat2db.community.domain.api.model.task.TaskStage;
import ai.chat2db.community.domain.api.model.task.TaskStatus;
import ai.chat2db.community.domain.api.service.task.ArtifactService;
import ai.chat2db.community.domain.api.service.task.TaskDeletionService;
import ai.chat2db.community.domain.api.service.task.TaskService;
import ai.chat2db.community.domain.api.service.task.TaskStorage;
import ai.chat2db.community.tools.exception.BusinessException;
import ai.chat2db.community.tools.exception.DataNotFoundException;
import ai.chat2db.community.tools.model.Context;
import ai.chat2db.community.tools.util.ContextUtils;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import ai.chat2db.spi.sql.Chat2DBContext;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class TaskServiceImpl implements TaskService {

    @org.springframework.beans.factory.annotation.Value("${chat2db.task.import.allowed-roots:}")
    private String importAllowedRoots;
    private final TaskStorage taskStorage;

    private final LocalTaskManager localTaskManager;

    private final TaskDeletionService deletionService;



    @Autowired
    public TaskServiceImpl(TaskStorage taskStorage, LocalTaskManager localTaskManager,
            TaskDeletionService deletionService) {
        this.taskStorage = taskStorage;
        this.localTaskManager = localTaskManager;
        this.deletionService = deletionService;
    }

    TaskServiceImpl(TaskStorage taskStorage, LocalTaskManager localTaskManager, ArtifactService artifactService) {
        this(taskStorage, localTaskManager, new TaskDeletionServiceImpl(taskStorage, artifactService));
    }

    @PostConstruct
    void recoverInterruptedArtifactDeletions() {
        deletionService.retryPendingDeletions();
    }

    @Override
    public Long submitExport(ExportTaskSpec spec) {
        return submit(spec);
    }

    @Override
    /** Staged/desktop local paths are the intended import source boundary. */
    @SuppressWarnings("lgtm[java/path-injection]")
    public Long submitImport(ImportTaskSpec spec) {
        validateImportSource(spec.getSourceFile());
        return submit(spec);
    }

    /**
     * Server deployments can restrict which directories import files may come from; desktop runs
     * keep the unrestricted default. Paths are compared normalized and absolute so `..` segments
     * cannot escape the allowlist.
     */
    private void validateImportSource(String sourceFile) {
        if (StringUtils.isBlank(importAllowedRoots) || StringUtils.isBlank(sourceFile)) {
            return;
        }
        java.nio.file.Path candidate;
        try {
            candidate = java.nio.file.Path.of(sourceFile).toAbsolutePath().normalize().toRealPath();
        } catch (java.nio.file.InvalidPathException | java.io.IOException invalidPath) {
            throw new BusinessException("task.import.sourceNotAllowed", null);
        }
        for (String root : importAllowedRoots.split(",")) {
            if (StringUtils.isBlank(root)) {
                continue;
            }
            try {
                java.nio.file.Path allowedRoot = java.nio.file.Path.of(root.trim()).toAbsolutePath()
                        .normalize().toRealPath();
                if (candidate.startsWith(allowedRoot)) {
                    return;
                }
            } catch (java.nio.file.InvalidPathException | java.io.IOException ignored) {
                // An invalid configured root cannot authorize access to any source path.
            }
        }
        throw new BusinessException("task.import.sourceNotAllowed", null);
    }

    @Override
    public PageResponse<Task> list(TaskQuery query) {
        TaskOwner owner = currentOwner();
        TaskQuery effectiveQuery = query == null ? new TaskQuery() : query;
        effectiveQuery.setUserId(owner.userId());
        effectiveQuery.setOrganizationId(owner.organizationId());
        return taskStorage.list(effectiveQuery);
    }

    @Override
    public Task get(Long taskId) {
        Task task = taskStorage.get(taskId).orElse(null);
        return isOwnedBy(task, currentOwner()) ? task : null;
    }

    @Override
    public List<TaskEvent> listEvents(Long taskId, long afterSequence, int limit) {
        if (get(taskId) == null) {
            return List.of();
        }
        return taskStorage.listEvents(taskId, Math.max(0L, afterSequence), limit);
    }

    @Override
    public List<TaskEvent> listEventsBefore(Long taskId, Long beforeSequence, int limit) {
        if (get(taskId) == null) {
            return List.of();
        }
        return taskStorage.listEventsBefore(taskId, beforeSequence, limit);
    }

    @Override
    public void delete(Long taskId) {
        Task task = get(taskId);
        if (task == null) {
            throw new DataNotFoundException();
        }
        if (!TaskStatus.isTerminal(task.getStatus())) {
            throw new BusinessException(TaskConstants.DELETE_ACTIVE_FORBIDDEN_MESSAGE_CODE);
        }
        deletionService.delete(task);
    }

    @Override
    public int activeTaskCount() {
        TaskOwner owner = currentOwner();
        return localTaskManager.activeTaskCount(owner.userId(), owner.organizationId());
    }

    @Override
    public void prepareForUserExit() {
        TaskOwner owner = currentOwner();
        localTaskManager.prepareForUserExit(owner.userId(), owner.organizationId());
    }

    @Override
    public void abortUserExit() {
        localTaskManager.abortUserExit();
    }

    @Override
    public TaskDownload resolveArtifact(Long taskId) {
        Task task = get(taskId);
        if (task == null || !TaskStatus.SUCCESS.name().equals(task.getStatus())
                || StringUtils.isBlank(task.getArtifactId())) {
            throw new DataNotFoundException();
        }
        File file = deletionService.resolveArtifact(task);
        return downloadFor(file, new File(task.getArtifactId()).getName());
    }

    @Override
    public TaskDownload resolveArtifact(Long taskId, String artifactId) {
        Task task = get(taskId);
        if (task == null || !TaskStatus.SUCCESS.name().equals(task.getStatus())) {
            throw new DataNotFoundException();
        }
        // The parameter is only a lookup key; the served path always comes from the stored row, so
        // a caller cannot name an arbitrary file.
        TaskArtifact artifact = taskStorage.listArtifacts(taskId).stream()
                .filter(candidate -> candidate.getArtifactId().equals(artifactId))
                .findFirst()
                .orElseThrow(DataNotFoundException::new);
        return downloadFor(new File(artifact.getArtifactId()), new File(artifact.getArtifactId()).getName());
    }

    @Override
    public List<TaskArtifact> listArtifacts(Long taskId) {
        return get(taskId) == null ? List.of() : taskStorage.listArtifacts(taskId);
    }

    private TaskDownload downloadFor(File file, String fileName) {
        if (!file.isFile() || !file.canRead()) {
            throw new DataNotFoundException();
        }
        return TaskDownload.builder().fileName(fileName)
                .fileUri(file.toURI().toString()).build();
    }

    private <S extends TaskSpec> Long submit(S spec) {
        localTaskManager.validate(spec);
        if (spec.getTarget() == null) {
            throw new IllegalArgumentException("Task target is required");
        }
        Context context = ContextUtils.queryContext();
        TaskOwner owner = owner(context);
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo();
        Task task = Task.builder()
                .type(spec.getTaskType())
                .name(StringUtils.defaultIfBlank(spec.getTaskName(), spec.getTaskType()))
                .target(spec.getTarget())
                .userId(owner.userId())
                .organizationId(owner.organizationId())
                .build();
        TaskEvent createdEvent = TaskEvent.builder()
                .level(TaskEventLevel.INFO.name())
                .code(TaskEventCode.TASK_CREATED.name())
                .stage(TaskStage.PENDING.name())
                .message("Task created")
                .details(Collections.emptyMap())
                .build();
        task = localTaskManager.submit(task, createdEvent, spec, context, connectInfo);
        return task.getId();
    }

    private TaskOwner currentOwner() {
        return owner(ContextUtils.queryContext());
    }

    private TaskOwner owner(Context context) {
        Long userId = context != null && context.getLoginUser() != null
                ? context.getLoginUser().getId() : null;
        Long organizationId = context == null ? null : context.getOrganizationId();
        return new TaskOwner(userId, organizationId);
    }

    private boolean isOwnedBy(Task task, TaskOwner owner) {
        return task != null && Objects.equals(task.getUserId(), owner.userId())
                && Objects.equals(task.getOrganizationId(), owner.organizationId());
    }

    private record TaskOwner(Long userId, Long organizationId) {
    }
}

package ai.chat2db.server.web.api.controller.task;

import ai.chat2db.server.domain.api.model.Task;
import ai.chat2db.server.domain.api.param.TaskPageParam;
import ai.chat2db.server.domain.api.service.TaskService;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.MalformedURLException;

@ConnectionInfoAspect
@RequestMapping("/api/task")
@Controller
@Slf4j
public class TaskController {

    @Autowired
    private TaskService taskService;


    @GetMapping("/list")
    public WebPageResult<Task> list() {
        TaskPageParam taskPageParam = new TaskPageParam();
        taskPageParam.setPageNo(1);
        taskPageParam.setPageSize(10);
        taskPageParam.setUserId(ContextUtils.getUserId());
        PageResult<Task> task = taskService.page(taskPageParam);
        return WebPageResult.of(task.getData(), 100L, 1, 10);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        DataResult<Task> task = taskService.get(id);
        if(task.getData() == null){
            log.error("task is null");
            throw new RuntimeException("task is null");
        }
        if(ContextUtils.getUserId() != task.getData().getUserId()){
            log.error("task is not belong to user");
            throw new RuntimeException("task is not belong to user");
        }

        Resource resource = null;
        try {
            resource = new UrlResource("file://"+task.getData().getDownloadUrl());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        if (resource.exists() || resource.isReadable()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } else {
            throw new RuntimeException("Could not read the file!");
        }

    }



}

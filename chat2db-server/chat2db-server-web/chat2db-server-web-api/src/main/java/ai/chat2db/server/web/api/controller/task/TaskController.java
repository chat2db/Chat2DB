package ai.chat2db.server.web.api.controller.task;

import ai.chat2db.server.domain.api.model.Task;
import ai.chat2db.server.domain.api.param.TaskPageParam;
import ai.chat2db.server.domain.api.service.TaskService;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.*;

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
    public void download(@PathVariable Long id, HttpServletResponse response) {
        DataResult<Task> task = taskService.get(id);
        Task data = task.getData();
        if (data == null) {
            log.error("task is null");
            throw new RuntimeException("task is null");
        }
        if (!ContextUtils.getUserId().equals(data.getUserId())) {
            log.error("task is not belong to user");
            throw new RuntimeException("task is not belong to user");
        }

        File file = new File(data.getDownloadUrl());

        if (!file.exists() || !file.isFile()) {
            log.error("File not found or is not a file: {}", file.getAbsolutePath());
            throw new RuntimeException("File not found or accessible");
        }


        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        try (InputStream inputStream = new FileInputStream(file)) {
            OutputStream outputStream = response.getOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush();
            }
        } catch (IOException e) {
            log.error("Error occurred while processing file download", e);
            throw new RuntimeException("Error in file download", e);
        }
    }


}

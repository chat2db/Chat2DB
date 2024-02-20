package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.enums.DeletedTypeEnum;
import ai.chat2db.server.domain.api.enums.TaskStatusEnum;
import ai.chat2db.server.domain.api.model.Task;
import ai.chat2db.server.domain.api.param.TaskCreateParam;
import ai.chat2db.server.domain.api.param.TaskPageParam;
import ai.chat2db.server.domain.api.param.TaskUpdateParam;
import ai.chat2db.server.domain.api.service.TaskService;
import ai.chat2db.server.domain.core.converter.TaskConverter;
import ai.chat2db.server.domain.repository.MapperUtils;
import ai.chat2db.server.domain.repository.entity.TaskDO;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TaskServiceImpl implements TaskService {

    /**
     * task converter
     */
    @Autowired
    private TaskConverter taskConverter;

    @Override
    public DataResult<Long> create(TaskCreateParam param) {
        TaskDO taskDO = taskConverter.todo(param);
        taskDO.setDeleted(DeletedTypeEnum.N.name());
        taskDO.setTaskStatus(TaskStatusEnum.INIT.name());
        MapperUtils.getTaskMapper().insert(taskDO);
        return DataResult.of(taskDO.getId());
    }

    @Override
    public ActionResult updateStatus(TaskUpdateParam param) {
        TaskDO taskDO = new TaskDO();
        taskDO.setId(param.getId());
        taskDO.setTaskStatus(param.getTaskStatus());
        taskDO.setContent(param.getContent());
        MapperUtils.getTaskMapper().updateById(taskDO);
        return ActionResult.isSuccess();
    }

    @Override
    public PageResult<Task> page(TaskPageParam param) {
        Page<TaskDO> page = new Page<>();
        page.setCurrent(param.getPageNo());
        page.setSize(param.getPageSize());
        page.setOrders(Lists.newArrayList(OrderItem.desc("gmt_create")));
        IPage<TaskDO> iPage = MapperUtils.getTaskMapper().pageQuery(page, param.getUserId(), DeletedTypeEnum.N.name());
        if (iPage != null) {
            return PageResult.of(taskConverter.toModel(iPage.getRecords()), param);
        }
        return PageResult.empty(param.getPageNo(), param.getPageSize());
    }

    @Override
    public DataResult<Task> get(Long id) {
        TaskDO task = MapperUtils.getTaskMapper().selectById(id);
        return DataResult.of(taskConverter.toModel(task));
    }
}

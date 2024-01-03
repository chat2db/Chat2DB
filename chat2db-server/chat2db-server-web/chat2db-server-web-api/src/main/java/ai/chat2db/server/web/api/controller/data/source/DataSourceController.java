package ai.chat2db.server.web.api.controller.data.source;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.ConsoleCloseParam;
import ai.chat2db.server.domain.api.param.ConsoleConnectParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceCreateParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePreConnectParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.param.datasource.DataSourceUpdateParam;
import ai.chat2db.server.domain.api.service.ConsoleService;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import ai.chat2db.server.tools.common.exception.ConnectionException;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.data.source.converter.DataSourceWebConverter;
import ai.chat2db.server.web.api.controller.data.source.converter.SSHWebConverter;
import ai.chat2db.server.web.api.controller.data.source.request.ConsoleCloseRequest;
import ai.chat2db.server.web.api.controller.data.source.request.ConsoleConnectRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceAttachRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceCloneRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceCloseRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceCreateRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceQueryRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceTestRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceUpdateRequest;
import ai.chat2db.server.web.api.controller.data.source.request.SSHTestRequest;
import ai.chat2db.server.web.api.controller.data.source.vo.DataSourceVO;
import ai.chat2db.server.web.api.controller.data.source.vo.DatabaseVO;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.ssh.SSHManager;
import com.jcraft.jsch.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据库连接类
 *
 * @author moji
 * @version ConnectionController.java, v 0.1 2022年09月16日 14:07 moji Exp $
 * @date 2022/09/16
 */
@ConnectionInfoAspect
@RequestMapping("/api/connection")
@RestController
@Slf4j
public class DataSourceController {

    private static final DataSourceSelector DATA_SOURCE_SELECTOR = DataSourceSelector.builder()
        .environment(Boolean.TRUE)
        .build();

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private ConsoleService consoleService;

    @Autowired
    private DataSourceWebConverter dataSourceWebConverter;

    @Autowired
    private SSHWebConverter sshWebConverter;

    /**
     * 数据库连接测试
     *
     * @param request
     * @return
     */
    @RequestMapping("/datasource/pre_connect")
    public ActionResult preConnect(@RequestBody DataSourceTestRequest request) {
        DataSourcePreConnectParam param = dataSourceWebConverter.testRequest2param(request);
        return dataSourceService.preConnect(param);
    }

    /**
     * 数据库连接测试
     *
     * @param request
     * @return
     */
    @RequestMapping("/ssh/pre_connect")
    public ActionResult sshConnect(@RequestBody SSHTestRequest request) {
        Session session = null;
        try {
            session = SSHManager.getSSHSession(sshWebConverter.toInfo(request));
        } catch (Exception e) {
            log.error("sshConnect error", e);
            throw new ConnectionException("connection.ssh.error", null, e);
        } finally {
            if (session != null) {
                session.disconnect();
            }
        }
        return ActionResult.isSuccess();
    }

    /**
     * 数据库连接
     *
     * @param request
     * @return
     */
    @GetMapping("/datasource/connect")
    public ListResult<DatabaseVO> attach(@Valid @NotNull DataSourceAttachRequest request) {
        ListResult<Database> databaseDTOListResult = dataSourceService.connect(request.getId());
        List<DatabaseVO> databaseVOS = dataSourceWebConverter.databaseDto2vo(databaseDTOListResult.getData());
        return ListResult.of(databaseVOS);
    }

    /**
     * 关闭数据库连接
     *
     * @param request
     * @return
     */
    @GetMapping("/datasource/close")
    public ActionResult close(@Valid @NotNull DataSourceCloseRequest request) {
        return dataSourceService.close(request.getId());
    }

    /**
     * Console连接
     *
     * @param request
     * @return
     */
    @GetMapping("/console/connect")
    public ActionResult connect(@Valid @NotNull ConsoleConnectRequest request) {
        ConsoleConnectParam consoleConnectParam = dataSourceWebConverter.request2connectParam(request);
        return consoleService.createConsole(consoleConnectParam);
    }

    /**
     * 关闭Console连接
     *
     * @param request
     * @return
     */
    @GetMapping("/console/close")
    public ActionResult closeConsole(@Valid @NotNull ConsoleCloseRequest request) {
        ConsoleCloseParam closeParam = dataSourceWebConverter.request2closeParam(request);
        return consoleService.closeConsole(closeParam);
    }

    /**
     * 查询我建立的数据库连接
     *
     * @param request
     * @return
     * @version 2.1.0
     */
    @GetMapping("/datasource/list")
    public WebPageResult<DataSourceVO> list(DataSourceQueryRequest request) {
        DataSourcePageQueryParam param = dataSourceWebConverter.queryReq2param(request);
        PageResult<DataSource> result = dataSourceService.queryPageWithPermission(param, DATA_SOURCE_SELECTOR);
        List<DataSourceVO> dataSourceVOS = dataSourceWebConverter.dto2voAndRemoveSensitivity(result.getData());
        return WebPageResult.of(dataSourceVOS, result.getTotal(), result.getPageNo(), result.getPageSize());
    }

    /**
     * 获取连接内容
     *
     * @param id
     * @return
     */
    @GetMapping("/datasource/{id}")
    public DataResult<DataSourceVO> queryById(@PathVariable("id") Long id) {
        DataResult<DataSource> dataResult = dataSourceService.queryExistent(id, DATA_SOURCE_SELECTOR);
        DataSourceVO dataSourceVO = dataSourceWebConverter.dto2vo(dataResult.getData());
        if (StringUtils.isNotBlank(dataSourceVO.getUser())) {
            dataSourceVO.setAuthenticationType("1");
        } else {
            dataSourceVO.setAuthenticationType("2");
        }
        return DataResult.of(dataSourceVO);
    }

    /**
     * 保存连接
     *
     * @param request
     * @return
     */
    @PostMapping("/datasource/create")
    public DataResult<Long> create(@RequestBody DataSourceCreateRequest request) {
        DataSourceCreateParam param = dataSourceWebConverter.createReq2param(request);
        return dataSourceService.createWithPermission(param);
    }

    /**
     * 更新连接
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/datasource/update", method = {RequestMethod.POST, RequestMethod.PUT})
    public DataResult<Long> update(@RequestBody DataSourceUpdateRequest request) {
        DataSourceUpdateParam param = dataSourceWebConverter.updateReq2param(request);
        return dataSourceService.updateWithPermission(param);
    }

    /**
     * 克隆连接
     *
     * @param request
     * @return
     */
    @PostMapping("/datasource/clone")
    public DataResult<Long> copy(@RequestBody DataSourceCloneRequest request) {
        return dataSourceService.copyByIdWithPermission(request.getId());
    }

    /**
     * 删除连接
     *
     * @param id
     * @return
     */
    @DeleteMapping("/datasource/{id}")
    public ActionResult delete(@PathVariable Long id) {
        return dataSourceService.deleteWithPermission(id);
    }

}

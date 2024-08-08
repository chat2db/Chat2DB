package ai.chat2db.server.web.api.controller.rdb;

import ai.chat2db.server.domain.api.service.FunctionService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.rdb.converter.FunctionConverter;
import ai.chat2db.server.web.api.controller.rdb.request.FunctionDetailRequest;
import ai.chat2db.server.web.api.controller.rdb.request.FunctionPageRequest;
import ai.chat2db.server.web.api.controller.rdb.request.FunctionUpdateRequest;
import ai.chat2db.spi.model.Function;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConnectionInfoAspect
@RequestMapping("/api/rdb/function")
@RestController
public class FunctionController {

    @Autowired
    private FunctionService functionService;

    @Autowired
    private FunctionConverter functionConverter;

    @GetMapping("/list")
    public WebPageResult<Function> list(@Valid FunctionPageRequest request) {
        ListResult<Function> functionListResult = functionService.functions(request.getDatabaseName(),
                request.getSchemaName());
        return WebPageResult.of(functionListResult.getData(), Long.valueOf(functionListResult.getData().size()), 1,
                functionListResult.getData().size());
    }

    @GetMapping("/detail")
    public DataResult<Function> detail(@Valid FunctionDetailRequest request) {
        return functionService.detail(request.getDatabaseName(), request.getSchemaName(), request.getFunctionName());
    }

    @PostMapping("/delete")
    public ActionResult delete(@Valid FunctionUpdateRequest request) {
        Function function = functionConverter.request2param(request);
        return functionService.delete(request.getDatabaseName(), request.getSchemaName(), function);
    }
}

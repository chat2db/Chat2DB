package ai.chat2db.server.web.api.controller.data.source.converter;

import ai.chat2db.server.domain.api.enums.DataSourceKindEnum;
import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.ConsoleCloseParam;
import ai.chat2db.server.domain.api.param.ConsoleConnectParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceCreateParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePreConnectParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceUpdateParam;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.web.api.controller.data.source.request.ConsoleCloseRequest;
import ai.chat2db.server.web.api.controller.data.source.request.ConsoleConnectRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceCreateRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceQueryRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceTestRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceUpdateRequest;
import ai.chat2db.server.web.api.controller.data.source.vo.DataSourceVO;
import ai.chat2db.server.web.api.controller.data.source.vo.DatabaseVO;
import ai.chat2db.spi.model.Database;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

/**
 * @formatter:off
 * @author moji
 * @version DataSourceWebConverter.java, v 0.1 2022年09月23日 16:45 moji Exp $
 * @date 2022/09/23
 */
@Mapper(componentModel = "spring", imports = {DataSourceKindEnum.class})
public abstract class DataSourceWebConverter {

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    @Mappings({
        @Mapping(source = "user", target = "userName"),
        @Mapping(target = "kind", expression = "java(DataSourceKindEnum.PRIVATE.getCode())")
    })
    public abstract DataSourceCreateParam createReq2param(DataSourceCreateRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    @Mappings({
        @Mapping(source = "user", target = "userName")
    })
    public abstract DataSourceUpdateParam updateReq2param(DataSourceUpdateRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract DataSourcePageQueryParam queryReq2param(DataSourceQueryRequest request);

    /**
     * 模型转换
     *
     * @param dataSource
     * @return
     */
    @Mappings({
        @Mapping(target = "user", source = "userName")
    })
    public abstract DataSourceVO dto2vo(DataSource dataSource);

    /**
     * 模型转换
     *
     * @param dataSources
     * @return
     */
    public abstract List<DataSourceVO> dto2vo(List<DataSource> dataSources);

    /**
     * 模型转换
     *
     * @param databaseDTO
     * @return
     */
    public abstract DatabaseVO databaseDto2vo(Database databaseDTO);

    /**
     * 模型转换
     *
     * @param databaseDTOS
     * @return
     */
    public abstract List<DatabaseVO> databaseDto2vo(List<Database> databaseDTOS);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract DataSourcePreConnectParam testRequest2param(DataSourceTestRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract ConsoleConnectParam request2connectParam(ConsoleConnectRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract ConsoleCloseParam request2closeParam(ConsoleCloseRequest request);

    /**
     * 移除敏感信息
     *
     * @param dataSources
     * @return java.util.List<ai.chat2db.server.web.api.controller.data.source.vo.DataSourceVO>
     * @author 敖癸
     * @since 2023/12/28 - 23:34
     */
    public List<DataSourceVO> dto2voAndRemoveSensitivity(List<DataSource> dataSources) {
        LoginUser loginUser = ContextUtils.getLoginUser();
        List<DataSourceVO> vos = dto2vo(dataSources);
        if (!loginUser.getAdmin()) {
            vos.stream().filter(vo -> "SHARED".equals(vo.getKind()))
                    .forEach(vo -> {
                        vo.setUser(null);
                        vo.setPassword(null);
                    });
        }
        return vos;
    }
}

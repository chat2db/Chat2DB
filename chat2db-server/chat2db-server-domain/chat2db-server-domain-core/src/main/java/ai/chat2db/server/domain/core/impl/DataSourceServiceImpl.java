package ai.chat2db.server.domain.core.impl;

import java.sql.Connection;
import java.util.List;

import ai.chat2db.server.domain.api.enums.DataSourceKindEnum;
import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.datasource.DataSourceCloseParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceCreateParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePreConnectParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.param.datasource.DataSourceTestParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceUpdateParam;
import ai.chat2db.server.domain.api.param.datasource.DatabaseQueryAllParam;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.core.converter.DataSourceConverter;
import ai.chat2db.server.domain.core.converter.EnvironmentConverter;
import ai.chat2db.server.domain.core.util.PermissionUtils;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.DataSourceAccessDO;
import ai.chat2db.server.domain.repository.entity.DataSourceDO;
import ai.chat2db.server.domain.repository.mapper.DataSourceAccessMapper;
import ai.chat2db.server.domain.repository.mapper.DataSourceCustomMapper;
import ai.chat2db.server.domain.repository.mapper.DataSourceMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.exception.DataNotFoundException;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.tools.common.exception.PermissionDeniedBusinessException;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.tools.common.util.EasyCollectionUtils;
import ai.chat2db.server.tools.common.util.EasyEnumUtils;
import ai.chat2db.server.tools.common.util.EasySqlUtils;
import ai.chat2db.spi.config.DBConfig;
import ai.chat2db.spi.config.DriverConfig;
import ai.chat2db.spi.model.DataSourceConnect;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.KeyValue;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.IDriverManager;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.JdbcUtils;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author moji
 * @version DataSourceCoreServiceImpl.java, v 0.1 2022年09月23日 15:51 moji Exp $
 * @date 2022/09/23
 */
@Slf4j
@Service
public class DataSourceServiceImpl implements DataSourceService {


    private DataSourceMapper getMapper() {
        return Dbutils.getMapper(DataSourceMapper.class);
    }

    @Autowired
    private DataSourceConverter dataSourceConverter;

    @Autowired
    private DatabaseService databaseService;


    private DataSourceCustomMapper getCustomMapper() {
        return Dbutils.getMapper(DataSourceCustomMapper.class);
    }
    @Resource
    private EnvironmentConverter environmentConverter;
    private DataSourceAccessMapper getAccessMapper() {
        return Dbutils.getMapper(DataSourceAccessMapper.class);
    }

    @Override
    public DataResult<Long> createWithPermission(DataSourceCreateParam param) {
        DataSourceKindEnum dataSourceKind = EasyEnumUtils.getEnum(DataSourceKindEnum.class, param.getKind());
        if (dataSourceKind == null) {
            throw new ParamBusinessException("kind");
        }
        if (dataSourceKind == DataSourceKindEnum.SHARED && !ContextUtils.getLoginUser().getAdmin()) {
            throw new PermissionDeniedBusinessException();
        }
        JdbcUtils.removePropertySameAsDefault(param.getDriverConfig());
        DataSourceDO dataSourceDO = dataSourceConverter.param2do(param);
        dataSourceDO.setGmtCreate(DateUtil.date());
        dataSourceDO.setGmtModified(DateUtil.date());
        dataSourceDO.setUserId(ContextUtils.getUserId());
        //dataSourceDO.setExtendInfo(null);

        getMapper().insert(dataSourceDO);
        preWarmingData(dataSourceDO.getId());
        return DataResult.of(dataSourceDO.getId());
    }

    private void preWarmingData(Long dataSourceId) {
        DataResult<DataSource> dataResult = queryById(dataSourceId);
        if (dataResult.success() && dataResult.getData() != null) {
            DataSource dataSource = dataResult.getData();
            DriverConfig driverConfig = dataSource.getDriverConfig();
            if (driverConfig == null || StringUtils.isBlank(driverConfig.getJdbcDriver())) {
                return;
            }
            try (Connection connection = IDriverManager.getConnection(dataSource.getUrl(), dataSource.getUserName(),
                    dataSource.getPassword(), dataSource.getDriverConfig(), dataSource.getExtendMap())) {
                DatabaseQueryAllParam databaseQueryAllParam = new DatabaseQueryAllParam();
                databaseQueryAllParam.setDataSourceId(dataSourceId);
                databaseQueryAllParam.setConnection(connection);
                databaseQueryAllParam.setDbType(dataSource.getType());
                databaseQueryAllParam.setRefresh(true);
                databaseService.queryAll(databaseQueryAllParam);
            } catch (Exception e) {
                log.error("preWarmingData error", e);
            }
        }
    }

    @Override
    public DataResult<Long> updateWithPermission(DataSourceUpdateParam param) {
        DataSource dataSource = queryExistent(param.getId(), null).getData();
        PermissionUtils.checkOperationPermission(dataSource.getUserId());

        JdbcUtils.removePropertySameAsDefault(param.getDriverConfig());
        DataSourceDO dataSourceDO = dataSourceConverter.param2do(param);
        dataSourceDO.setGmtModified(DateUtil.date());
        getMapper().updateById(dataSourceDO);
        return DataResult.of(dataSourceDO.getId());
    }

    @Override
    public ActionResult deleteWithPermission(Long id) {

        DataSource dataSource = queryExistent(id, null).getData();
        PermissionUtils.checkOperationPermission(dataSource.getUserId());

        getMapper().deleteById(id);

        LambdaQueryWrapper<DataSourceAccessDO> dataSourceAccessQueryWrapper = new LambdaQueryWrapper<>();
        dataSourceAccessQueryWrapper.eq(DataSourceAccessDO::getDataSourceId, id)
        ;
        getAccessMapper().delete(dataSourceAccessQueryWrapper);
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<DataSource> queryById(Long id) {
        DataSourceDO dataSourceDO = getMapper().selectById(id);
        return DataResult.of(dataSourceConverter.do2dto(dataSourceDO));
    }

    @Override
    public DataResult<DataSource> queryExistent(Long id, DataSourceSelector selector) {
        DataResult<DataSource> dataResult = queryById(id);
        if (dataResult.getData() == null) {
            throw new DataNotFoundException();
        }

        fillData(Lists.newArrayList(dataResult.getData()), selector);

        return dataResult;
    }

    @Override
    public DataResult<Long> copyByIdWithPermission(Long id) {
        DataSource dataSource = queryExistent(id, null).getData();
        PermissionUtils.checkOperationPermission(dataSource.getUserId());

        DataSourceDO dataSourceDO = getMapper().selectById(id);
        dataSourceDO.setId(null);
        String alias = dataSourceDO.getAlias() + "Copy";
        dataSourceDO.setAlias(alias);
        dataSourceDO.setGmtCreate(DateUtil.date());
        dataSourceDO.setGmtModified(DateUtil.date());
        getMapper().insert(dataSourceDO);
        return DataResult.of(dataSourceDO.getId());
    }

    @Override
    public PageResult<DataSource> queryPage(DataSourcePageQueryParam param, DataSourceSelector selector) {
        LambdaQueryWrapper<DataSourceDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            queryWrapper.and(wrapper -> wrapper.like(DataSourceDO::getAlias, "%" + param.getSearchKey() + "%")
                    .or()
                    .like(DataSourceDO::getUrl, "%" + param.getSearchKey() + "%"));
        }
        Integer start = param.getPageNo();
        Integer offset = param.getPageSize();
        Page<DataSourceDO> page = new Page<>(start, offset);
        IPage<DataSourceDO> iPage = getMapper().selectPage(page, queryWrapper);
        List<DataSource> dataSources = dataSourceConverter.do2dto(iPage.getRecords());

        fillData(dataSources, selector);

        return PageResult.of(dataSources, iPage.getTotal(), param);
    }

    @Override
    public PageResult<DataSource> queryPageWithPermission(DataSourcePageQueryParam param, DataSourceSelector selector) {
        LoginUser loginUser = ContextUtils.getLoginUser();

        IPage<DataSourceDO> iPage = getCustomMapper().selectPageWithPermission(
                new Page<>(param.getPageNo(), param.getPageSize()),
                BooleanUtils.isTrue(loginUser.getAdmin()), loginUser.getId(), param.getSearchKey(), param.getKind(),
                EasySqlUtils.orderBy(param.getOrderByList()));

        List<DataSource> dataSources = dataSourceConverter.do2dto(iPage.getRecords());

        fillData(dataSources, selector);

        return PageResult.of(dataSources, iPage.getTotal(), param);

    }

    @Override
    public ListResult<DataSource> queryByIds(List<Long> ids) {
        return listQuery(ids, null);
    }

    @Override
    public ListResult<DataSource> listQuery(List<Long> idList, DataSourceSelector selector) {
        if (CollectionUtils.isEmpty(idList)) {
            return ListResult.empty();
        }
        List<DataSourceDO> dataList = getMapper().selectBatchIds(idList);
        List<DataSource> list = dataSourceConverter.do2dto(dataList);

        fillData(list, selector);
        return ListResult.of(list);
    }

    @Override
    public ActionResult preConnect(DataSourcePreConnectParam param) {
        DataSourceTestParam testParam
                = dataSourceConverter.param2param(param);
        DriverConfig driverConfig = testParam.getDriverConfig();
        if (driverConfig == null || !driverConfig.notEmpty()) {
            driverConfig = Chat2DBContext.getDefaultDriverConfig(param.getType());
        }
        DataSourceConnect dataSourceConnect = JdbcUtils.testConnect(testParam.getUrl(), testParam.getHost(),
                testParam.getPort(),
                testParam.getUsername(), testParam.getPassword(), testParam.getDbType(),
                driverConfig, param.getSsh(), KeyValue.toMap(param.getExtendInfo()));
        if (BooleanUtils.isNotTrue(dataSourceConnect.getSuccess())) {
            return ActionResult.fail(dataSourceConnect.getMessage(), dataSourceConnect.getDescription(),
                    dataSourceConnect.getErrorDetail());
        }
        return ActionResult.isSuccess();
    }

    @Override
    public ListResult<Database> connect(Long id) {
        DatabaseQueryAllParam queryAllParam = new DatabaseQueryAllParam();
        queryAllParam.setDataSourceId(id);
        List<Database> databases = Chat2DBContext.getMetaData().databases(Chat2DBContext.getConnection());
        return ListResult.of(databases);
    }

    @Override
    public ActionResult close(Long id) {
        DataSourceCloseParam closeParam = new DataSourceCloseParam();
        closeParam.setDataSourceId(id);
        return ActionResult.isSuccess();
    }

    private void fillData(List<DataSource> list, DataSourceSelector selector) {
        if (CollectionUtils.isEmpty(list) || selector == null) {
            return;
        }

        fillEnvironment(list, selector);

        fillSupportDatabase(list);
    }

    private void fillSupportDatabase(List<DataSource> list) {

        if(CollectionUtils.isEmpty(list)) {
            return;
        }
        for (DataSource dataSource:list) {
            String type = dataSource.getType();
            if(StringUtils.isNotBlank(type)) {
                DBConfig config = Chat2DBContext.getDBConfig(type);
                if(config != null) {
                    dataSource.setSupportDatabase(config.isSupportDatabase());
                    dataSource.setSupportSchema(config.isSupportSchema());
                }
            }
        }
    }


    private void fillEnvironment(List<DataSource> list, DataSourceSelector selector) {
        if (BooleanUtils.isNotTrue(selector.getEnvironment())) {
            return;
        }
        environmentConverter.fillDetail(EasyCollectionUtils.toList(list, DataSource::getEnvironment));
    }

}

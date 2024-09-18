package ai.chat2db.server.domain.core.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import ai.chat2db.server.domain.api.model.Dashboard;
import ai.chat2db.server.domain.api.param.dashboard.DashboardCreateParam;
import ai.chat2db.server.domain.api.param.dashboard.DashboardPageQueryParam;
import ai.chat2db.server.domain.api.param.dashboard.DashboardQueryParam;
import ai.chat2db.server.domain.api.param.dashboard.DashboardUpdateParam;
import ai.chat2db.server.domain.api.service.DashboardService;
import ai.chat2db.server.domain.core.converter.DashboardConverter;
import ai.chat2db.server.domain.core.util.PermissionUtils;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.DashboardChartRelationDO;
import ai.chat2db.server.domain.repository.entity.DashboardDO;
import ai.chat2db.server.domain.repository.mapper.DashboardChartRelationMapper;
import ai.chat2db.server.domain.repository.mapper.DashboardMapper;
import ai.chat2db.server.tools.base.enums.YesOrNoEnum;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.exception.DataNotFoundException;
import ai.chat2db.server.tools.common.model.EasyLambdaQueryWrapper;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.tools.common.util.EasySqlUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author moji
 * @version DashboardServiceImpl.java, v 0.1 June 9, 2023 16:06 moji Exp $
 * @date 2023/06/09
 */
@Service
public class DashboardServiceImpl implements DashboardService {


    private DashboardMapper getMapper() {
        return Dbutils.getMapper(DashboardMapper.class);
    }
    private DashboardChartRelationMapper getMapper1() {
        return Dbutils.getMapper(DashboardChartRelationMapper.class);
    }


    @Autowired
    private DashboardConverter dashboardConverter;

    @Override
    public DataResult<Long> createWithPermission(DashboardCreateParam param) {
        param.setGmtCreate(LocalDateTime.now());
        param.setGmtModified(LocalDateTime.now());
        param.setDeleted(YesOrNoEnum.NO.getLetter());
        param.setUserId(ContextUtils.getUserId());
        DashboardDO dashboardDO = dashboardConverter.param2do(param);
        getMapper().insert(dashboardDO);
        insertDashboardRelation(dashboardDO.getId(), param.getChartIds());
        return DataResult.of(dashboardDO.getId());
    }

    @Override
    public ActionResult updateWithPermission(DashboardUpdateParam param) {
        Dashboard dashboardData = queryExistent(param.getId()).getData();
        PermissionUtils.checkOperationPermission(dashboardData.getUserId());

        param.setGmtModified(LocalDateTime.now());
        DashboardDO dashboardDO = dashboardConverter.updateParam2do(param);
        getMapper().updateById(dashboardDO);
        if (CollectionUtils.isEmpty(param.getChartIds())) {
            return ActionResult.isSuccess();
        }
        deleteDashboardRelation(dashboardDO.getId());
        insertDashboardRelation(dashboardDO.getId(), param.getChartIds());
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<Dashboard> find(Long id) {
        DashboardDO dashboardDO = getMapper().selectById(id);
        if (YesOrNoEnum.YES.getLetter().equals(dashboardDO.getDeleted())) {
            return DataResult.empty();
        }
        Dashboard dashboard = dashboardConverter.do2model(dashboardDO);
        LambdaQueryWrapper<DashboardChartRelationDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DashboardChartRelationDO::getDashboardId, id);
        List<DashboardChartRelationDO> relationDO = getMapper1().selectList(queryWrapper);
        List<Long> chartIds = relationDO.stream().map(DashboardChartRelationDO::getChartId).toList();
        dashboard.setChartIds(chartIds);
        return DataResult.of(dashboard);
    }

    @Override
    public DataResult<Dashboard> queryExistent(DashboardQueryParam param) {
        EasyLambdaQueryWrapper<DashboardDO> queryWrapper = new EasyLambdaQueryWrapper<>();
        queryWrapper
            .eq(DashboardDO::getDeleted, YesOrNoEnum.NO.getLetter())
            .eqWhenPresent(DashboardDO::getId, param.getId())
            .eqWhenPresent(DashboardDO::getUserId, param.getUserId());
        IPage<DashboardDO> page = getMapper().selectPage(new Page<>(1, 1), queryWrapper);
        if (CollectionUtils.isEmpty(page.getRecords())) {
            throw new DataNotFoundException();
        }
        Dashboard dashboardData = dashboardConverter.do2model(page.getRecords().get(0));
        LambdaQueryWrapper<DashboardChartRelationDO> dashboardChartRelationQueryWrapper = new LambdaQueryWrapper<>();
        dashboardChartRelationQueryWrapper.eq(DashboardChartRelationDO::getDashboardId, param.getId());
        List<DashboardChartRelationDO> relationDO = getMapper1().selectList(
            dashboardChartRelationQueryWrapper);
        List<Long> chartIds = relationDO.stream().map(DashboardChartRelationDO::getChartId).toList();
        dashboardData.setChartIds(chartIds);
        return DataResult.of(dashboardData);
    }

    @Override
    public DataResult<Dashboard> queryExistent(Long id) {
        DataResult<Dashboard> dataResult = find(id);
        if (dataResult.getData() == null) {
            throw new DataNotFoundException();
        }
        return dataResult;
    }

    @Override
    public ActionResult deleteWithPermission(Long id) {
        Dashboard data = queryExistent(id).getData();
        PermissionUtils.checkOperationPermission(data.getUserId());

        DashboardDO dashboardDO = new DashboardDO();
        dashboardDO.setId(id);
        dashboardDO.setDeleted(YesOrNoEnum.YES.getLetter());
        getMapper().updateById(dashboardDO);
        deleteDashboardRelation(id);
        return ActionResult.isSuccess();
    }

    /**
     * delete dashboard relation
     *
     * @param id
     */
    private void deleteDashboardRelation(Long id) {
        LambdaQueryWrapper<DashboardChartRelationDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DashboardChartRelationDO::getDashboardId, id);
        List<DashboardChartRelationDO> relationDO = getMapper1().selectList(queryWrapper);
        List<Long> relationIds = relationDO.stream().map(DashboardChartRelationDO::getId).toList();
        if (CollectionUtils.isNotEmpty(relationIds)) {
            getMapper1().deleteBatchIds(relationIds);
        }
    }

    /**
     * insert dashboard relation
     *
     * @param dashboardId
     * @param chartIds
     */
    private void insertDashboardRelation(Long dashboardId, List<Long> chartIds) {
        if (Objects.isNull(dashboardId) || CollectionUtils.isEmpty(chartIds)) {
            return;
        }
        chartIds.forEach(chartId -> {
            DashboardChartRelationDO relationDO = new DashboardChartRelationDO();
            relationDO.setGmtCreate(LocalDateTime.now());
            relationDO.setGmtModified(LocalDateTime.now());
            relationDO.setDashboardId(dashboardId);
            relationDO.setChartId(chartId);
            getMapper1().insert(relationDO);
        });
    }

    @Override
    public PageResult<Dashboard> queryPage(DashboardPageQueryParam param) {
        EasyLambdaQueryWrapper<DashboardDO> queryWrapper = new EasyLambdaQueryWrapper<>();
        queryWrapper
            .eq(DashboardDO::getDeleted, YesOrNoEnum.NO.getLetter())
            .likeWhenPresent(DashboardDO::getName, EasySqlUtils.buildLikeRightFuzzy(param.getSearchKey()))
            .eqWhenPresent(DashboardDO::getUserId, param.getUserId());
        Integer start = param.getPageNo();
        Integer offset = param.getPageSize();
        Page<DashboardDO> page = new Page<>(start, offset);
        IPage<DashboardDO> iPage = getMapper().selectPage(page, queryWrapper);
        List<Dashboard> dashboards = dashboardConverter.do2model(iPage.getRecords());
        return PageResult.of(dashboards, iPage.getTotal(), param);
    }
}

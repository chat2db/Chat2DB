package ai.chat2db.server.domain.api.service;

import java.util.List;

import ai.chat2db.server.domain.api.chart.ChartCreateParam;
import ai.chat2db.server.domain.api.chart.ChartListQueryParam;
import ai.chat2db.server.domain.api.chart.ChartQueryParam;
import ai.chat2db.server.domain.api.chart.ChartUpdateParam;
import ai.chat2db.server.domain.api.model.Chart;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * @author moji
 * @version ChartService.java, v 0.1 June 9, 2023 15:28 moji Exp $
 * @date 2023/06/09
 */
public interface ChartService {
    /**
     * Create report
     *
     * @param param
     * @return
     */
    DataResult<Long> createWithPermission(ChartCreateParam param);

    /**
     * Update report
     *
     * @param param
     * @return
     */
    ActionResult updateWithPermission(ChartUpdateParam param);

    /**
     * Query based on id
     *
     * @param id
     * @return
     */
    DataResult<Chart> find(@NotNull Long id);

    /**
     * Query a piece of data
     *
     * @param param
     * @return
     */
    DataResult<Chart> queryExistent(@NotNull ChartQueryParam param);

    /**
     * Query a piece of data
     *
     * @param id
     * @return
     */
    DataResult<Chart> queryExistent(@NotNull Long id);

    /**
     * Query multiple pieces of data
     *
     * @param param
     * @return
     */
    ListResult<Chart> listQuery(@NotNull ChartListQueryParam param);

    /**
     * Query chart list by ID
     *
     * @param ids
     * @return
     */
    ListResult<Chart> queryByIds(@NotEmpty List<Long> ids);

    /**
     * delete
     *
     * @param id
     * @return
     */
    ActionResult deleteWithPermission(@NotNull Long id);

}

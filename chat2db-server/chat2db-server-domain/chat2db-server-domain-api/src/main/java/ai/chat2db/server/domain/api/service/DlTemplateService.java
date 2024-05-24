package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.DlCountParam;
import ai.chat2db.server.domain.api.param.DlExecuteParam;
import ai.chat2db.server.domain.api.param.OrderByParam;
import ai.chat2db.server.domain.api.param.UpdateSelectResultParam;
import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.domain.api.param.GroupByParam;

import ai.chat2db.server.tools.base.wrapper.result.ListResult;

/**
 * Data source management services
 *
 * @author moji
 * @version DataSourceCoreService.java, v 0.1 September 23, 2022 15:22 moji Exp $
 * @date 2022/09/23
 */
public interface DlTemplateService {

    /**
     * data source execution dl
     *
     * @param param
     * @return
     */
    ListResult<ExecuteResult> execute(DlExecuteParam param);


    /**
     *
     * @param param
     * @return
     */
    ListResult<ExecuteResult> executeSelectTable(DlExecuteParam param);


    /**
     * Data source execution update
     *
     * @param param
     * @return
     */
    DataResult<ExecuteResult> executeUpdate(DlExecuteParam param);

    /**
     * Execute statistics sql
     *
     * @param param
     * @return
     */
    DataResult<Long> count(DlCountParam param);

    /**
     * Update query results
     * @param param
     * @return
     */
    DataResult<String> updateSelectResult(UpdateSelectResultParam param);

    /**
     *
     * @param param
     * @return
     */
    DataResult<String> getGroupBySql(GroupByParam param);

    /**
     *
     * @param param
     * @return
     */
    DataResult<String> getOrderBySql(OrderByParam param);

}

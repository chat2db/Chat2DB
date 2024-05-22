package ai.chat2db.server.web.api.controller.rdb.request;

import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequestInfo;
import ai.chat2db.spi.model.OrderBy;
import lombok.Data;

import java.util.List;

@Data
public class GroupByRequest extends DataSourceBaseRequest implements DataSourceBaseRequestInfo {

    /**
     * origin sql
     */
    private String originSql;

     /**
     * group by field
     */

    private List<String> groupByList;

}
package ai.chat2db.server.domain.api.param.dashboard;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * @author moji
 * @version DashboardSaveParam.java, v 0.1 June 9, 2023 15:29 moji Exp $
 * @date 2023/06/09
 */
@Data
public class DashboardCreateParam {

    /**
     * creation time
     */
    private LocalDateTime gmtCreate;

    /**
     * modified time
     */
    private LocalDateTime gmtModified;

    /**
     * Report name
     */
    private String name;

    /**
     * description
     */
    private String description;

    /**
     * Report layout information
     */
    private String schema;

    /**
     * Whether it has been deleted, 'Y' means deleted, 'N' means not deleted
     */
    private String deleted;

    /**
     * user id
     */
    private Long userId;

    /**
     * Chart ID list
     */
    private List<Long> chartIds;
}

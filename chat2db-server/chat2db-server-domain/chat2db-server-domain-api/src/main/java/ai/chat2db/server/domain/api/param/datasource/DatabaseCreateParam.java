
package ai.chat2db.server.domain.api.param.datasource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author jipengfei
 * @version : DatabaseOperationParam.java
 */
@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class DatabaseCreateParam {

    private Long dataSourceId;

    private String name;

    private String newName;

    private String comment;

    private String charset;

    private String collation;

}
package ai.chat2db.server.tools.common.model.data.option.table;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author: zgq
 * @date: 2024年04月26日 9:32
 */
@Data
@EqualsAndHashCode(callSuper =true)
public class ImportNewTableOptions extends ImportTableOptions{

    /*
    * create table sql
    * */
    private String sql;
}

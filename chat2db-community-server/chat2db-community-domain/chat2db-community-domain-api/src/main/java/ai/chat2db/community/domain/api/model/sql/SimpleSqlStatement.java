package ai.chat2db.community.domain.api.model.sql;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimpleSqlStatement {

    private String sql;
    private String sqlType;
    private String paginationRowId;
    private String comment;
    private List<RefreshTarget> refreshTargets;
    private List<SimpleTable> tables;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SimpleTable {
        private String databaseName;
        private String schemaName;
        private String tableName;
        private Long datasourceId;
        private String alias;
        private List<Column> columns;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Column {
        private String tableName;
        private String columnName;
        private String alias;
    }


    public SimpleSqlStatement(String sql) {
        this.sql = sql;
    }
}

package ai.chat2db.plugin.duckdb.builder;

import ai.chat2db.spi.DefaultSqlBuilder;
import ai.chat2db.spi.model.request.PageLimitRequest;

import static ai.chat2db.plugin.duckdb.constant.DuckDBSqlBuilderConstants.PAGE_LIMIT_SQL;

public class DuckDBSqlBuilder extends DefaultSqlBuilder {
    @Override
    public String buildPageLimit(PageLimitRequest request) {
        return PAGE_LIMIT_SQL.formatted(request.getSql(), Math.max(1, request.getPageSize()),
                Math.max(0, request.getOffset()));
    }
}

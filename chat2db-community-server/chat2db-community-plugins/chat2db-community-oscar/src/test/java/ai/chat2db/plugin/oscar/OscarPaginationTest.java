package ai.chat2db.plugin.oscar;

import ai.chat2db.community.domain.api.config.DriverConfig;
import ai.chat2db.community.domain.api.model.result.ExecuteResponse;
import ai.chat2db.community.domain.api.model.result.Header;
import ai.chat2db.community.domain.api.model.result.ResultCell;
import ai.chat2db.community.domain.api.model.sql.SqlExecuteRequest;
import ai.chat2db.community.domain.api.service.db.ISqlExecutionResultConsumer;
import ai.chat2db.community.domain.api.service.db.ISqlExecutionStatementListener;
import ai.chat2db.community.tools.util.I18nUtils;
import ai.chat2db.spi.DefaultSQLExecutor;
import ai.chat2db.spi.IPlugin;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import ai.chat2db.spi.model.request.PageLimitRequest;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises Oscar's actual pagination SQL on H2 Oracle mode, not a native Oscar server. */
class OscarPaginationTest {
    private Connection connection;
    private IPlugin previousPlugin;
    private Object previousMessages;

    @BeforeEach
    void setup() throws Exception {
        Field messages = I18nUtils.class.getDeclaredField("messageSourceStatic");
        messages.setAccessible(true);
        previousMessages = messages.get(null);
        StaticMessageSource source = new StaticMessageSource();
        source.setUseCodeAsDefaultMessage(true);
        messages.set(null, source);
        connection = DriverManager.getConnection("jdbc:h2:mem:oscar_page;MODE=Oracle");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE PROBE AS SELECT X AS ID, X+100 AS CHAT2DB_AUTO_ROW_ID FROM SYSTEM_RANGE(1,5)");
        }
        previousPlugin = Chat2DBContext.PLUGIN_MAP.put("OSCAR", new OscarPlugin());
        ConnectInfo info = new ConnectInfo();
        info.setDbType("OSCAR");
        info.setDriverConfig(new DriverConfig());
        info.setConnection(connection);
        Chat2DBContext.putContext(info);
    }

    @AfterEach
    void cleanup() throws Exception {
        Chat2DBContext.removeContext();
        connection.close();
        if (previousPlugin == null) Chat2DBContext.PLUGIN_MAP.remove("OSCAR");
        else Chat2DBContext.PLUGIN_MAP.put("OSCAR", previousPlugin);
        Field messages = I18nUtils.class.getDeclaredField("messageSourceStatic");
        messages.setAccessible(true);
        messages.set(null, previousMessages);
    }

    @Test
    void pagesHideOnlyTheInternalColumnAndPreserveUserColumn() {
        for (int page : List.of(1, 2, 3, 4)) {
            ExecuteResponse result = DefaultSQLExecutor.getInstance().execute(request(page)).get(0);
            assertTrue(result.getSuccess(), result.getMessage());
            assertEquals(List.of("sqlResult.rowNumber", "ID", "CHAT2DB_AUTO_ROW_ID"),
                    result.getHeaderList().stream().map(Header::getName).toList());
            List<List<String>> expected = new ArrayList<>();
            for (int id = (page-1)*2+1; id <= Math.min(page*2,5); id++) {
                expected.add(List.of(String.valueOf(id), String.valueOf(id), String.valueOf(id+100)));
            }
            assertEquals(expected, result.getDisplayDataList());
        }
    }

    @Test
    void streamingAndExportKeepHeadersAlignedWithValues() throws Exception {
        List<List<String>> batches = new ArrayList<>();
        List<ExecuteResponse> results = new ArrayList<>();
        DefaultSQLExecutor.getInstance().executeStreaming(request(2), new ISqlExecutionResultConsumer() {
            public void statementStarted(String sql, String originalSql, String comment) { }
            public void resultStarted(ExecuteResponse result) {
                assertEquals(List.of("sqlResult.rowNumber", "ID", "CHAT2DB_AUTO_ROW_ID"),
                        result.getHeaderList().stream().map(Header::getName).toList());
            }
            public void rows(ExecuteResponse result, List<List<ResultCell>> rows) {
                rows.forEach(row -> batches.add(row.stream().map(ResultCell::getValue).toList()));
            }
            public void resultFinished(ExecuteResponse result) { results.add(result); }
            public void updateCount(ExecuteResponse result) { fail("Expected query result"); }
            public void statementFinished(String sql, long duration) { }
        }, new ISqlExecutionStatementListener() {
            public void onStatementCreated(Statement statement) { }
            public void onStatementClosed(Statement statement) { }
        }, () -> false);
        assertEquals(1, results.size());
        assertEquals(List.of(List.of("3","3","103"), List.of("4","4","104")), batches);

        String pageSql = new OscarPlugin().getSqlBuilder().dql().buildPageLimit(PageLimitRequest.builder()
                .sql(request(2).getScript()).offset(2).pageSize(2).build());
        for (String sql : List.of(request(2).getScript(), pageSql)) {
            List<List<String>> exported = new ArrayList<>();
            DefaultSQLExecutor.getInstance().execute(connection, sql,
                    headers -> assertEquals(List.of("ID", "CHAT2DB_AUTO_ROW_ID"),
                            headers.stream().map(Header::getName).toList()),
                    exported::add, value -> value.getString(), false);
            assertEquals(sql.equals(pageSql) ? 2 : 5, exported.size());
            assertEquals(sql.equals(pageSql) ? List.of("3", "103") : List.of("1", "101"), exported.get(0));
        }
    }

    private static SqlExecuteRequest request(int page) {
        SqlExecuteRequest request = new SqlExecuteRequest();
        request.setScript("SELECT ID, CHAT2DB_AUTO_ROW_ID FROM PROBE ORDER BY ID");
        request.setSingle(true);
        request.setPageNo(page);
        request.setPageSize(2);
        return request;
    }
}

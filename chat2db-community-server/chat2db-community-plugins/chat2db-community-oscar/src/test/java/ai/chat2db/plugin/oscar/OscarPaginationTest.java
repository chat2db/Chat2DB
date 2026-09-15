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
            statement.execute("CREATE TABLE PROBE AS SELECT X AS ID, X+100 AS CHAT2DB_AUTO_ROW_ID, X+200 AS CAHT2DB_AUTO_ROW_ID FROM SYSTEM_RANGE(1,5)");
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
            assertEquals(List.of("sqlResult.rowNumber", "ID", "CHAT2DB_AUTO_ROW_ID", "CAHT2DB_AUTO_ROW_ID"),
                    result.getHeaderList().stream().map(Header::getName).toList());
            List<List<String>> expected = new ArrayList<>();
            for (int id = (page-1)*2+1; id <= Math.min(page*2,5); id++) {
                expected.add(List.of(String.valueOf(id), String.valueOf(id), String.valueOf(id+100), String.valueOf(id+200)));
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
                assertEquals(List.of("sqlResult.rowNumber", "ID", "CHAT2DB_AUTO_ROW_ID", "CAHT2DB_AUTO_ROW_ID"),
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
        assertEquals(List.of(List.of("3","3","103","203"), List.of("4","4","104","204")), batches);

        List<List<String>> exported = new ArrayList<>();
        DefaultSQLExecutor.getInstance().execute(connection, request(2).getScript(),
                headers -> assertEquals(List.of("ID", "CHAT2DB_AUTO_ROW_ID", "CAHT2DB_AUTO_ROW_ID"),
                        headers.stream().map(Header::getName).toList()),
                exported::add, value -> value.getString(), false);
        assertEquals(5, exported.size());
        assertEquals(List.of("1", "101", "201"), exported.get(0));
    }

    @Test
    void sqlTableExportOmitsOnlyItsGeneratedColumn() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO PROBE SELECT X, X+100, X+200 FROM SYSTEM_RANGE(6,100001)");
        }
        List<String> tail = new ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger inserts = new java.util.concurrent.atomic.AtomicInteger();
        var context = (ai.chat2db.community.domain.api.service.task.TaskExecutionContext)
                java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{ai.chat2db.community.domain.api.service.task.TaskExecutionContext.class}, (proxy, method, args) -> {
                            if (method.getName().equals("write") && ((String) args[0]).startsWith("INSERT")) {
                                int count = inserts.incrementAndGet();
                                String sql = (String) args[0];
                                assertTrue(sql.contains("CHAT2DB_AUTO_ROW_ID"));
                                assertTrue(sql.contains("CAHT2DB_AUTO_ROW_ID"));
                                assertFalse(sql.matches("(?s).*CHAT2DB_AUTO_ROW_ID_[a-f0-9]{10}.*"));
                                if (count > 100000) tail.add(sql);
                            }
                            return null;
                        });
        new OscarDBManager().exportTableData(connection, null, "PUBLIC", "PROBE", context);
        assertEquals(100001, inserts.get());
        assertEquals(1, tail.size());
        assertTrue(tail.get(0).contains("100101"));
        assertTrue(tail.get(0).contains("100201"));
    }

    @Test
    void fallbackToOriginalQueryKeepsBothUserColumns() {
        Chat2DBContext.PLUGIN_MAP.put("OSCAR", new OscarPlugin() {
            @Override
            public ai.chat2db.spi.IDbMetaData getDbMetaData() {
                return new OscarMetaData() {
                    @Override
                    public ai.chat2db.spi.ISqlBuilder getSqlBuilder() {
                        return new ai.chat2db.plugin.oscar.builder.OscarSqlBuilder() {
                            @Override
                            public String buildPageLimit(PageLimitRequest request) {
                                request.createPaginationRowId();
                                return "SELECT * FROM MISSING_PAGINATION_TABLE";
                            }
                        };
                    }
                };
            }
        });
        ExecuteResponse result = DefaultSQLExecutor.getInstance().execute(request(2)).get(0);
        assertTrue(result.getSuccess());
        assertEquals(request(2).getScript(), result.getSql());
        assertEquals(List.of(List.of("3", "3", "103", "203"), List.of("4", "4", "104", "204")),
                result.getDisplayDataList());
    }

    @Test
    void executingGeneratedSqlAsUserSqlDoesNotHideItsLastColumn() throws Exception {
        PageLimitRequest page = PageLimitRequest.builder().sql(request(2).getScript()).offset(2).pageSize(2).build();
        String sql = new OscarPlugin().getSqlBuilder().dql().buildPageLimit(page);
        ExecuteResponse result = DefaultSQLExecutor.getInstance().execute(
                ai.chat2db.spi.model.request.SqlStatementExecuteRequest.builder()
                        .connection(connection).sql(sql).limitRowSize(false).build());
        assertEquals(4, result.getHeaderList().size());
        assertEquals(page.getPaginationRowId().toUpperCase(java.util.Locale.ROOT), result.getHeaderList().get(3).getName());
        assertEquals(List.of("3", "103", "203", "3"), result.getDisplayDataList().get(0));
    }

    private static SqlExecuteRequest request(int page) {
        SqlExecuteRequest request = new SqlExecuteRequest();
        request.setScript("SELECT * FROM PROBE ORDER BY ID");
        request.setSingle(true);
        request.setPageNo(page);
        request.setPageSize(2);
        return request;
    }
}

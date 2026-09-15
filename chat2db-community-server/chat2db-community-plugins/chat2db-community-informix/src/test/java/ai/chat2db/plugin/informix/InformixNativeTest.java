package ai.chat2db.plugin.informix;

import ai.chat2db.community.domain.api.config.DriverConfig;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.result.ExecuteResponse;
import ai.chat2db.community.domain.api.model.result.ResultCell;
import ai.chat2db.community.domain.api.model.sql.SqlExecuteRequest;
import ai.chat2db.community.domain.api.service.db.ISqlExecutionResultConsumer;
import ai.chat2db.community.domain.api.service.db.ISqlExecutionStatementListener;
import ai.chat2db.community.tools.exception.BusinessException;
import ai.chat2db.community.tools.util.I18nUtils;
import ai.chat2db.plugin.informix.builder.InformixSqlBuilder;
import ai.chat2db.spi.IPlugin;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import ai.chat2db.spi.model.datasource.DriverEntry;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.JdbcDriverManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.support.StaticMessageSource;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Requires an explicitly selected disposable Informix database and driver jars. */
@EnabledIfEnvironmentVariable(named = "INFORMIX_TEST_URL", matches = ".+")
class InformixNativeTest {
    private static final String DRIVER_KEY = "informix-native-test";
    private static URLClassLoader loader;
    private static DriverConfig driverConfig;
    private static Map<String, DriverEntry> drivers;
    private static Object previousMessages;
    private Connection connection;
    private IPlugin previousPlugin;
    private final List<String> tables = new ArrayList<>();

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadDriver() throws Exception {
        String[] jars = System.getenv("INFORMIX_TEST_JARS").split(",");
        URL[] urls = new URL[jars.length];
        for (int i = 0; i < jars.length; i++) urls[i] = Path.of(jars[i]).toUri().toURL();
        loader = new URLClassLoader(urls, InformixNativeTest.class.getClassLoader());
        Driver driver = (Driver) Class.forName("com.informix.jdbc.IfxDriver", true, loader)
                .getDeclaredConstructor().newInstance();
        driverConfig = new DriverConfig();
        driverConfig.setJdbcDriver(DRIVER_KEY);
        driverConfig.setJdbcDriverClass("com.informix.jdbc.IfxDriver");
        driverConfig.setDbType("INFORMIX");
        Field field = JdbcDriverManager.class.getDeclaredField("DRIVER_ENTRY_MAP");
        field.setAccessible(true);
        drivers = (Map<String, DriverEntry>) field.get(null);
        drivers.put(DRIVER_KEY, DriverEntry.builder().driverConfig(driverConfig).driver(driver).build());
        Field messages = I18nUtils.class.getDeclaredField("messageSourceStatic");
        messages.setAccessible(true);
        previousMessages = messages.get(null);
        StaticMessageSource source = new StaticMessageSource();
        source.setUseCodeAsDefaultMessage(true);
        messages.set(null, source);
    }

    @AfterAll
    static void unloadDriver() throws Exception {
        drivers.remove(DRIVER_KEY);
        loader.close();
        Field messages = I18nUtils.class.getDeclaredField("messageSourceStatic");
        messages.setAccessible(true);
        messages.set(null, previousMessages);
    }

    @BeforeEach
    void connect() throws Exception {
        connection = JdbcDriverManager.getConnection(System.getenv("INFORMIX_TEST_URL"),
                System.getenv("INFORMIX_TEST_USER"), System.getenv("INFORMIX_TEST_PASSWORD"), driverConfig);
        ConnectInfo info = new ConnectInfo();
        info.setDbType("INFORMIX");
        info.setDatabaseName("review_2488");
        info.setUser(System.getenv("INFORMIX_TEST_USER"));
        info.setPassword(System.getenv("INFORMIX_TEST_PASSWORD"));
        info.setDriverConfig(driverConfig);
        info.setConnection(connection);
        info.setUrl(System.getenv("INFORMIX_TEST_URL"));
        previousPlugin = Chat2DBContext.PLUGIN_MAP.put("INFORMIX", new InformixPlugin());
        Chat2DBContext.putContext(info);
    }

    @AfterEach
    void cleanUp() throws Exception {
        Collections.reverse(tables);
        try {
            if (connection != null) {
                try (Statement statement = connection.createStatement()) {
                    for (String table : tables) statement.execute("DROP TABLE " + table);
                }
                connection.close();
            }
        } finally {
            Chat2DBContext.removeContext();
            if (previousPlugin == null) Chat2DBContext.PLUGIN_MAP.remove("INFORMIX");
            else Chat2DBContext.PLUGIN_MAP.put("INFORMIX", previousPlugin);
        }
    }

    @Test
    void explainButtonReturnsPlanAndNeverRunsSelectOrDml() throws Exception {
        String table = create("id INTEGER PRIMARY KEY, qty INTEGER DEFAULT 1 NOT NULL");
        execute("INSERT INTO " + table + "(id) VALUES(1)");
        execute("INSERT INTO " + table + "(id) VALUES(2)");
        for (String sql : List.of("SELECT * FROM " + table + " WHERE id=1",
                "DELETE FROM " + table + " WHERE id=1", "UPDATE " + table + " SET qty=5 WHERE id=1",
                "INSERT INTO " + table + " SELECT id+10,qty FROM " + table)) {
            List<ExecuteResponse> responses = InformixCommandExecutor.INSTANCE.execute(request(sql));
            assertEquals(1, responses.size());
            ExecuteResponse response = responses.get(0);
            assertTrue(response.getSuccess(), () -> String.valueOf(response));
            assertEquals("EXPLAIN", response.getSqlType());
            String plan = response.getDataList().get(0).get(1).getValue();
            assertTrue(plan.contains("Estimated Cost"), plan);
            if (sql.startsWith("SELECT")) assertTrue(plan.contains("INDEX PATH"), plan);
        }
        assertEquals(2, scalar("SELECT COUNT(*) FROM " + table));
        assertEquals(2, scalar("SELECT SUM(qty) FROM " + table));
        assertFalse(connection.isClosed());
    }

    @Test
    void streamingEmitsOneResultAndKeepsTemporaryTableSession() throws Exception {
        String table = "ifx_tmp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        execute("CREATE TEMP TABLE " + table + " (id INTEGER)");
        tables.add(table);
        execute("INSERT INTO " + table + " VALUES(1)");
        List<ExecuteResponse> finished = new ArrayList<>();
        List<List<ResultCell>> rows = new ArrayList<>();
        int[] starts = {0};
        ISqlExecutionResultConsumer consumer = new ISqlExecutionResultConsumer() {
            public void statementStarted(String sql, String originalSql, String comment) { }
            public void resultStarted(ExecuteResponse response) { starts[0]++; }
            public void rows(ExecuteResponse response, List<List<ResultCell>> batch) { rows.addAll(batch); }
            public void resultFinished(ExecuteResponse response) { finished.add(response); }
            public void updateCount(ExecuteResponse response) { fail("EXPLAIN produced an update count"); }
            public void statementFinished(String sql, long duration) { }
        };
        List<Statement> created = new ArrayList<>();
        List<Statement> closed = new ArrayList<>();
        ISqlExecutionStatementListener listener = new ISqlExecutionStatementListener() {
            public void onStatementCreated(Statement statement) { created.add(statement); }
            public void onStatementClosed(Statement statement) { closed.add(statement); }
        };
        InformixCommandExecutor.INSTANCE.executeStreaming(request("SELECT * FROM " + table), consumer, listener, () -> false);
        assertEquals(3, created.size());
        assertEquals(new HashSet<>(created), new HashSet<>(closed));
        assertEquals(1, starts[0]);
        assertEquals(1, finished.size());
        assertEquals(1, rows.size());
        assertEquals("EXPLAIN", finished.get(0).getSqlType());
        assertTrue(rows.get(0).get(1).getValue().contains(table));
    }

    @Test
    void modifyKeepsDefaultsNullabilityAndDimensions() throws Exception {
        String table = create("qty SMALLINT DEFAULT 1 NOT NULL, note VARCHAR(12) DEFAULT 'ready', amount DECIMAL(5,1)");
        TableColumn qty = column(table, "qty", "INTEGER");
        qty.setDefaultValue("1");
        qty.setNullable(0);
        alter(table, qty);
        TableColumn note = column(table, "note", "VARCHAR");
        note.setColumnSize(32);
        note.setDefaultValue("'ready'");
        note.setNullable(1);
        alter(table, note);
        TableColumn amount = column(table, "amount", "DECIMAL");
        amount.setColumnSize(8);
        amount.setDecimalDigits(2);
        amount.setNullable(1);
        alter(table, amount);
        execute("INSERT INTO " + table + "(amount) VALUES(123456.78)");
        assertEquals(1, scalar("SELECT qty FROM " + table));
        assertThrows(SQLException.class, () -> execute("INSERT INTO " + table + "(qty) VALUES(NULL)"));
        execute("UPDATE " + table + " SET note='12345678901234567890123456789012'");
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT note,amount FROM " + table)) {
            assertTrue(result.next());
            assertEquals(32, result.getString(1).length());
            assertEquals("123456.78", result.getBigDecimal(2).toPlainString());
        }
    }

    @Test
    void constrainedColumnsAreRejectedAndInboundForeignKeySurvives() throws Exception {
        String parent = create("id SMALLINT PRIMARY KEY, checked SMALLINT CHECK(checked > 0)");
        String child = create("pid SMALLINT REFERENCES " + parent + "(id)");
        for (TableColumn column : List.of(column(parent, "id", "INTEGER"), column(parent, "checked", "INTEGER"),
                column(child, "pid", "INTEGER"))) {
            BusinessException error = assertThrows(BusinessException.class, () -> alter(column.getTableName(), column));
            assertEquals("informix.column.constraintModification", error.getCode());
        }
        assertThrows(SQLException.class, () -> execute("INSERT INTO " + child + " VALUES(999)"));
        assertThrows(SQLException.class, () -> execute("INSERT INTO " + parent + " VALUES(1,-1)"));
    }

    @Test
    void changingVarcharToAliasPreservesExistingTextAndLength() throws Exception {
        String table = create("note VARCHAR(16) DEFAULT 'four' NOT NULL");
        execute("INSERT INTO " + table + "(note) VALUES('four')");
        TableColumn column = column(table, "note", "character varying");
        column.setColumnSize(64);
        column.setDefaultValue("'four'");
        column.setNullable(0);
        alter(table, column);
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT note FROM " + table)) {
            assertTrue(result.next());
            assertEquals("four", result.getString(1));
        }
        try (ResultSet columns = connection.getMetaData().getColumns(null, "informix", table, "note")) {
            assertTrue(columns.next());
            assertEquals(64, columns.getInt("COLUMN_SIZE"));
            assertEquals(DatabaseMetaData.columnNoNulls, columns.getInt("NULLABLE"));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "INFORMIX_TEST_ANSI_URL", matches = ".+")
    void renameOnlyChangesTheSelectedOwnerInAnsiDatabase() throws Exception {
        String name = "ifx_owner_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String owner = "other_owner";
        try (Connection ansi = JdbcDriverManager.getConnection(System.getenv("INFORMIX_TEST_ANSI_URL"),
                System.getenv("INFORMIX_TEST_USER"), System.getenv("INFORMIX_TEST_PASSWORD"), driverConfig);
             Statement statement = ansi.createStatement()) {
            List<String> created = new ArrayList<>();
            try {
                statement.execute("CREATE TABLE 'informix'." + name + " (id INTEGER)");
                created.add("'informix'." + name);
                statement.execute("CREATE TABLE '" + owner + "'." + name + " (id INTEGER)");
                created.add("'" + owner + "'." + name);
                statement.execute("INSERT INTO 'informix'." + name + " VALUES(1)");
                statement.execute("INSERT INTO '" + owner + "'." + name + " VALUES(2)");
                Table before = Table.builder().schemaName(owner).name(name).columnList(List.of()).indexList(List.of()).build();
                Table after = Table.builder().schemaName(owner).name(name + "_new").columnList(List.of()).indexList(List.of()).build();
                statement.execute(new InformixSqlBuilder().buildAlterTable(before, after));
                created.set(1, "'" + owner + "'." + name + "_new");
                try (ResultSet rows = statement.executeQuery("SELECT id FROM 'informix'." + name)) {
                    assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
                }
                try (ResultSet rows = statement.executeQuery("SELECT id FROM '" + owner + "'." + name + "_new")) {
                    assertTrue(rows.next()); assertEquals(2, rows.getInt(1));
                }
            } finally {
                for (String table : created) statement.execute("DROP TABLE " + table);
            }
        }
    }

    @Test
    void renameWithTypeModificationUsesTheNewTableAndColumnNames() throws Exception {
        String table = create("qty SMALLINT DEFAULT 1 NOT NULL");
        execute("INSERT INTO " + table + " VALUES(7)");
        TableColumn column = column(table, "quantity", "INTEGER");
        column.setOldName("qty"); column.setDefaultValue("1"); column.setNullable(0);
        Table before = Table.builder().schemaName("informix").name(table).columnList(List.of()).indexList(List.of()).build();
        Table after = Table.builder().name(table + "_new").columnList(List.of(column)).indexList(List.of()).build();
        String script = new InformixSqlBuilder().buildAlterTable(before, after);
        String[] statements = script.split(";");
        execute(statements[0]);
        tables.set(tables.size() - 1, table + "_new");
        for (int i = 1; i < statements.length; i++) if (!statements[i].isBlank()) execute(statements[i]);
        assertEquals(7, scalar("SELECT quantity FROM " + table + "_new"));
    }

    @Test
    void metadataReadsConstraintsUsingOnlyTheSuppliedConnection() throws Exception {
        String table = create("value INTEGER NOT NULL CHECK(value > 0)");
        ConnectInfo previous = Chat2DBContext.getConnectInfo();
        previous.setConnection(null);
        Chat2DBContext.removeContext();
        try {
            var constraints = new InformixMetaData().columnConstraints(connection, null, table, "value");
            assertTrue(constraints.stream().anyMatch(c -> c.type().equals("N")));
            assertTrue(constraints.stream().anyMatch(c -> c.type().equals("C")));
            assertFalse(connection.isClosed());
        } finally {
            previous.setConnection(connection);
            Chat2DBContext.putContext(previous);
        }
    }

    private String create(String columns) throws Exception {
        String table = "ifx_2488_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        execute("CREATE TABLE " + table + " (" + columns + ")");
        tables.add(table);
        return table;
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }

    private int scalar(String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next()); return result.getInt(1);
        }
    }

    private static TableColumn column(String table, String name, String type) {
        TableColumn column = new TableColumn();
        column.setTableName(table); column.setName(name); column.setColumnType(type); column.setEditStatus("MODIFY");
        return column;
    }

    private void alter(String table, TableColumn column) throws SQLException {
        Table before = Table.builder().name(table).schemaName("informix").columnList(List.of()).indexList(List.of()).build();
        Table after = Table.builder().name(table).columnList(List.of(column)).indexList(List.of()).build();
        String sql = new InformixSqlBuilder().buildAlterTable(before, after);
        assertTrue(sql.startsWith("ALTER TABLE 'informix'." + table));
        execute(sql);
    }

    private SqlExecuteRequest request(String sql) {
        SqlExecuteRequest request = new SqlExecuteRequest();
        request.setScript(sql); request.setExplain(true); request.setSingle(true);
        request.setDatabaseName("review_2488"); request.setPageNo(1); request.setPageSize(100);
        return request;
    }
}

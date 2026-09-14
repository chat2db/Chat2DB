package ai.chat2db.community.domain.core.impl.task.export;

import ai.chat2db.community.domain.api.config.DBConfig;
import ai.chat2db.community.domain.api.config.DriverConfig;
import ai.chat2db.community.domain.api.model.task.ArtifactDraft;
import ai.chat2db.community.domain.api.model.task.ExportTaskSpec;
import ai.chat2db.community.domain.api.model.task.TaskTargetSnapshot;
import ai.chat2db.community.domain.api.service.task.TaskCancelable;
import ai.chat2db.community.domain.api.service.task.TaskExecutionContext;
import ai.chat2db.community.domain.core.impl.db.extension.SqlExecutionPolicyManager;
import ai.chat2db.community.domain.core.impl.task.export.sink.CsvSink;
import ai.chat2db.community.domain.core.impl.task.export.sink.SqlSink;
import ai.chat2db.community.tools.constant.JdbcDriverConstants;
import ai.chat2db.spi.DefaultMetaService;
import ai.chat2db.spi.IDbMetaData;
import ai.chat2db.spi.IPlugin;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parallel keyset shards must still produce one artifact in strict key order: workers fill
 * per-shard queues and the caller drains them shard by shard into the single sink. Each worker
 * opens its own JDBC connection through the standard driver manager, like production shards do.
 * The standard mode runs the same table through the serial path and must not spin up shards.
 */
class ShardedKeysetExportTest {

    private static final String DB_TYPE = "SHARD_EXPORT_TEST";
    private static final int ROWS = 20_001;
    private static final String H2_DRIVER_NAME = "shard-test-h2.jar";

    private static String previousUserHome;

    private Connection connection;
    private IPlugin previousPlugin;

    @BeforeAll
    static void isolateHomeAndSeedDriver() throws Exception {
        previousUserHome = System.getProperty("user.home");
        File tempHome = Files.createTempDirectory("chat2db-shard-test-home").toFile();
        System.setProperty("user.home", tempHome.getAbsolutePath());
        File libDir = new File(JdbcDriverConstants.DRIVER_LIB_PATH);
        libDir.mkdirs();
        File h2Jar = new File(org.h2.Driver.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Files.copy(h2Jar.toPath(), new File(libDir, H2_DRIVER_NAME).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @AfterAll
    static void restoreHome() {
        System.setProperty("user.home", previousUserHome);
    }

    @BeforeEach
    void setUp() throws Exception {
        DBConfig config = new DBConfig();
        config.setDbType(DB_TYPE);
        config.setDefaultDriverConfig(new DriverConfig());
        previousPlugin = Chat2DBContext.PLUGIN_MAP.put(DB_TYPE, new IPlugin() {
            @Override
            public DBConfig getDBConfig() {
                return config;
            }

            @Override
            public IDbMetaData getDbMetaData() {
                return new DefaultMetaService();
            }

            @Override
            public ai.chat2db.spi.IDbManager getDbManager() {
                return new ai.chat2db.spi.DefaultDBManager() {
                    @Override
                    public ai.chat2db.spi.model.export.ExportCapability getExportCapability() {
                        return ai.chat2db.spi.model.export.ExportCapability.KEYSET_SHARDING;
                    }
                };
            }
        });
        connection = DriverManager.getConnection("jdbc:h2:mem:shard_export;DB_CLOSE_DELAY=-1");
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS SHARD_ITEMS");
            statement.execute("CREATE TABLE SHARD_ITEMS (ID INT PRIMARY KEY, NAME VARCHAR(20))");
            statement.execute("INSERT INTO SHARD_ITEMS SELECT X, 'name' FROM SYSTEM_RANGE(1, " + ROWS + ")");
        }
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDbType(DB_TYPE);
        DriverConfig driverConfig = new DriverConfig();
        driverConfig.setJdbcDriverClass("org.h2.Driver");
        driverConfig.setJdbcDriver(H2_DRIVER_NAME);
        connectInfo.setDriverConfig(driverConfig);
        connectInfo.setUrl("jdbc:h2:mem:shard_export;DB_CLOSE_DELAY=-1");
        Chat2DBContext.putContext(connectInfo);
    }

    @AfterEach
    void tearDown() throws Exception {
        Chat2DBContext.removeContext();
        if (previousPlugin == null) {
            Chat2DBContext.PLUGIN_MAP.remove(DB_TYPE);
        } else {
            Chat2DBContext.PLUGIN_MAP.put(DB_TYPE, previousPlugin);
        }
        connection.close();
    }

    @Test
    void shardsDrainInKeyOrderThroughOneSink() throws Exception {
        RecordingContext context = new RecordingContext();
        ShardTestExporter exporter = new ShardTestExporter();
        setShardMaxParallelism(exporter, 3);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        String csv = runThroughPipeline(exporter, context, out, "ULTRA_FAST");
        List<String> lines = new java.util.ArrayList<>(java.util.Arrays.asList(
                csv.replace("﻿", "").split("\r\n")));
        assertEquals("ID,NAME", lines.get(0));
        assertEquals(ROWS, lines.size() - 1);
        Matcher matcher = Pattern.compile("^(\\d+),name$").matcher("");
        for (int row = 1; row < lines.size(); row++) {
            matcher.reset(lines.get(row));
            assertTrue(matcher.matches() && Integer.parseInt(matcher.group(1)) == row, lines.get(row));
        }
        assertTrue(context.workerThreads.size() >= 2,
                "expected several shard threads, saw " + context.workerThreads);
    }

    /**
     * Standard mode must keep the export on the single-cursor serial path: ordered artifact, no
     * shard workers, regardless of the configured fan-out.
     */
    @Test
    void standardModeFallsBackToTheSerialPath() throws Exception {
        RecordingContext context = new RecordingContext();
        ShardTestExporter exporter = new ShardTestExporter();
        setShardMaxParallelism(exporter, 3);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        String csv = runThroughPipeline(exporter, context, out, "STANDARD");
        List<String> lines = new java.util.ArrayList<>(java.util.Arrays.asList(
                csv.replace("﻿", "").split("\r\n")));
        assertEquals("ID,NAME", lines.get(0));
        assertEquals(ROWS, lines.size() - 1);
        assertEquals(0, context.workerThreads.stream()
                        .filter(name -> name.startsWith("chat2db-shard-")).count(),
                "standard mode must not spin up shard workers, saw " + context.workerThreads);
    }

    /**
     * SQL dumps shard too: rows become dialect literals on the shard threads and the ordered
     * drain must keep the multi-value INSERT statements in key order.
     */
    @Test
    void shardsDrainInKeyOrderThroughSqlSink() throws Exception {
        RecordingContext context = new RecordingContext();
        SqlShardTestExporter exporter = new SqlShardTestExporter();
        setShardMaxParallelism(exporter, 3);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        exporter.exportDirect(context, out, "ULTRA_FAST");
        String dump = out.toString(StandardCharsets.UTF_8);
        assertTrue(dump.startsWith("INSERT"), dump.substring(0, Math.min(80, dump.length())));
        String[] statements = dump.split(";\n");
        assertTrue(statements.length > 1 && statements.length <= ROWS / 10,
                "rows must merge into multi-value statements, saw " + statements.length
                        + " statements for " + ROWS + " rows");
        // Values arrive as dialect literals, so the numeric key may be quoted ('1'); the regex
        // tolerates both bare and quoted integers as long as tuples stay in key order.
        Matcher ids = Pattern.compile("\\(('?)(\\d+)\\1\\s*,").matcher(dump);
        long expected = 1;
        int tuples = 0;
        while (ids.find()) {
            assertEquals(expected, Long.parseLong(ids.group(2)), "statements must stay in key order");
            expected++;
            tuples++;
        }
        assertEquals(ROWS, tuples, "every row must appear exactly once, dump sample: "
                + dump.substring(0, Math.min(300, dump.length())));
        assertTrue(context.workerThreads.size() >= 2,
                "expected several shard threads, saw " + context.workerThreads);
    }

    private String runThroughPipeline(ShardTestExporter exporter, RecordingContext context,
            ByteArrayOutputStream out, String mode) throws Exception {
        exporter.exportDirect(context, out, mode);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static void setShardMaxParallelism(BaseExporter exporter, int value) throws Exception {
        Field field = BaseExporter.class.getDeclaredField("shardMaxParallelism");
        field.setAccessible(true);
        field.setInt(exporter, value);
    }

    private static final class ShardTestExporter extends BaseExporter {

        private ShardTestExporter() {
            super(new ExportCellProcessorChain(List.of()), new SqlExecutionPolicyManager(List.of()));
            this.suffix = ".csv";
        }

        @Override
        public String type() {
            return "csv";
        }

        @Override
        protected void singleExport(ExportTaskSpec spec, TaskExecutionContext context, String tableName,
                java.io.OutputStream output) {
            streamTable(spec, tableName, context, output,
                    (stream, effectiveSpec, effectiveTable) -> new CsvSink(stream, true),
                    ExportValueMode.NATIVE, 1000,
                    new ExportProgressLogger(context, "CSV", tableName));
        }

        private void exportDirect(RecordingContext context, java.io.ByteArrayOutputStream out,
                String mode) throws Exception {
            ExportTaskSpec spec = ExportTaskSpec.builder()
                    .tableNames(List.of("SHARD_ITEMS"))
                    .target(TaskTargetSnapshot.builder().dataSourceId(1L).tableName("SHARD_ITEMS").build())
                    .mode(mode)
                    .build();
            singleExport(spec, context, "SHARD_ITEMS", out);
        }
    }

    private static final class SqlShardTestExporter extends BaseExporter {

        private SqlShardTestExporter() {
            super(new ExportCellProcessorChain(List.of()), new SqlExecutionPolicyManager(List.of()));
            this.suffix = ".sql";
        }

        @Override
        public String type() {
            return "sql";
        }

        @Override
        protected void singleExport(ExportTaskSpec spec, TaskExecutionContext context, String tableName,
                java.io.OutputStream output) {
            streamTable(spec, tableName, context, output,
                    (stream, effectiveSpec, effectiveTable) -> new SqlSink(stream,
                            Chat2DBContext.getSqlBuilder(), null, null),
                    ExportValueMode.SQL_LITERAL, 1000,
                    new ExportProgressLogger(context, "SQL", tableName));
        }

        private void exportDirect(RecordingContext context, java.io.ByteArrayOutputStream out,
                String mode) throws Exception {
            ExportTaskSpec spec = ExportTaskSpec.builder()
                    .tableNames(List.of("SHARD_ITEMS"))
                    .target(TaskTargetSnapshot.builder().dataSourceId(1L).tableName("SHARD_ITEMS").build())
                    .mode(mode)
                    .build();
            singleExport(spec, context, "SHARD_ITEMS", out);
        }
    }

    private static final class RecordingContext implements TaskExecutionContext {

        private final Set<String> workerThreads = ConcurrentHashMap.newKeySet();
        private final AtomicInteger progress = new AtomicInteger();

        @Override
        public Long taskId() {
            return 42L;
        }

        @Override
        public void reportProgress(int value, String stage, String message) {
            progress.set(value);
        }

        @Override
        public void logInfo(String code, String message) {
        }

        @Override
        public void logInfo(String code, String message, Map<String, Object> details) {
        }

        @Override
        public void logWarn(String code, String message, Map<String, Object> details) {
        }

        @Override
        public void logError(String code, String message, Map<String, Object> details) {
        }

        @Override
        public void checkCancelled() {
        }

        @Override
        public void registerCancelable(TaskCancelable resource) {
            workerThreads.add(Thread.currentThread().getName());
        }

        @Override
        public ArtifactDraft createArtifact(String outputDirectory, String fileName, String mediaType) {
            return createArtifact(ai.chat2db.community.domain.api.model.task.TaskArtifactRole.OUTPUT,
                    outputDirectory, fileName, mediaType);
        }

        @Override
        public ArtifactDraft createArtifact(String role, String outputDirectory, String fileName,
                String mediaType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void write(String content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onStatementCreated(java.sql.Statement statement) {
            workerThreads.add(Thread.currentThread().getName());
        }

        @Override
        public void onStatementClosed(java.sql.Statement statement) {
        }
    }
}

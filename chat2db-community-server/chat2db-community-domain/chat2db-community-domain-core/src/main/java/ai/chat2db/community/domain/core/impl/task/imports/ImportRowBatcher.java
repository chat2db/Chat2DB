package ai.chat2db.community.domain.core.impl.task.imports;

import ai.chat2db.community.domain.api.model.metadata.DataType;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.task.CsvOptions;
import ai.chat2db.community.domain.api.model.task.ImportOptions;
import ai.chat2db.community.domain.api.model.task.ImportTaskSpec;
import ai.chat2db.community.domain.api.model.task.TaskCancelledException;
import ai.chat2db.community.domain.api.model.task.TaskErrorCode;
import ai.chat2db.community.domain.api.model.task.TaskExecutionException;
import ai.chat2db.community.domain.api.model.task.TaskExecutionMode;
import ai.chat2db.community.domain.api.model.value.SQLDataValue;
import ai.chat2db.community.domain.api.service.task.TaskExecutionContext;
import ai.chat2db.community.domain.core.impl.task.AdaptiveBatchSizer;
import ai.chat2db.community.domain.core.impl.task.AdaptiveConcurrencyGate;
import ai.chat2db.community.domain.core.impl.task.imports.ImportColumnResolver.Resolution;
import ai.chat2db.community.domain.core.impl.task.imports.excel.CsvImportValueNormalizer;
import ai.chat2db.spi.ISqlBuilder;
import ai.chat2db.spi.IValueProcessor;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import ai.chat2db.spi.model.request.SingleInsertSqlRequest;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectionPool;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.LongAdder;

/**
 * Turns file rows into buffered {@code INSERT} statements and executes them in JDBC batches.
 * With {@code onError=SKIP} a failing row is retried individually and recorded in a
 * {@code REJECT}-role NDJSON sub-artifact instead of aborting the task.
 *
 * <p>Parallel execution starts at the fast-mode contract baseline of {@code 4} workers and
 * {@code 20000} rows per batch, shrinks to at most {@code 1} worker and {@code 100} rows when the
 * target is slow, and grows in steps while the measured throughput keeps improving - bounded by
 * the machine's available parallelism, so it can never out-run the threads this computer actually
 * has left. The
 * {@code chat2db.task.import.parallelism} system property overrides the fan-out
 * ({@code 1} forces the serial path, a larger value pins the worker count). Finished batches are
 * handed to partitioned queues: per worker the order is strict, while workers run in parallel, and
 * the queue set grows together with the adaptive gate. The number of <em>active</em>
 * workers and the batch size are self-tuning (see {@link AdaptiveConcurrencyGate} and
 * {@link AdaptiveBatchSizer}), so the pipeline converges to the throughput the target database
 * actually sustains. Rows have no ordering constraints, so inter-worker interleaving is safe; the
 * only visible effect is that auto-generated key values may interleave across workers.
 */
@Slf4j
public final class ImportRowBatcher implements AutoCloseable {

    /**
     * Standard-mode batch size: the historical value, untouched by the fast mode so the plain
     * import path keeps behaving exactly as it did before.
     */
    private static final int DEFAULT_BATCH_ROWS = 500;

    /** Fast-mode contract baseline: batches start at 20000 rows and may grow beyond it. */
    private static final int FAST_MODE_BATCH_ROWS = 20_000;

    private static final int QUEUE_CAPACITY = 4;

    /** Contract baseline fan-out of the fast mode; the adaptive gate grows it further on demand. */
    private static final int BASE_WORKERS = 4;

    /** How long a worker waits for an adaptive gate permit before degrading to ungated execution. */
    private static final long GATE_WAIT_MILLIS = 30_000L;

    private static final String ON_ERROR_SKIP = "SKIP";

    private static final String REJECT_ROLE = "REJECT";

    private final ImportTaskSpec spec;

    private final TaskExecutionContext context;

    private final Resolution resolution;

    private final ImportOptions options;

    private final CsvOptions csvOptions;

    private final IValueProcessor valueProcessor;

    private final ISqlBuilder sqlBuilder;

    private final ConnectInfo connectInfo;

    private final ImportSqlExecutor sqlExecutor;

    private final AdaptiveBatchSizer batchSizer;

    private final LongAdder importedCount = new LongAdder();

    private final Object rejectLock = new Object();

    private final List<String> bufferedSqls = new ArrayList<>(DEFAULT_BATCH_ROWS);

    private final List<String> bufferedRows = new ArrayList<>(DEFAULT_BATCH_ROWS);

    private final List<Long> bufferedRowNumbers = new ArrayList<>(DEFAULT_BATCH_ROWS);

    private BufferedWriter rejectWriter;

    private long rejectedRowCount;

    // --- parallel-execution state, null on the serial path ---
    private volatile int workerCount;

    private final List<BlockingQueue<PendingBatch>> queues;

    private final ExecutorService workerPool;

    private final Object workerGrowthLock = new Object();

    private final AtomicBoolean closing = new AtomicBoolean();

    private final AdaptiveConcurrencyGate gate;

    private final AtomicBoolean aborted = new AtomicBoolean();

    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    private final AtomicInteger inFlightBatches = new AtomicInteger();

    private final AtomicInteger peakInFlightBatches = new AtomicInteger();

    private final Object quiesceMonitor = new Object();

    private long submittedBatches;

    private final long createdNanos = System.nanoTime();

    private volatile long totalImportNanos;

    private final AtomicBoolean ungatedWarned = new AtomicBoolean();

    private final boolean standardMode;

    public ImportRowBatcher(ImportTaskSpec spec, TaskExecutionContext context, Resolution resolution,
            IValueProcessor valueProcessor) {
        this.spec = spec;
        this.context = context;
        this.resolution = resolution;
        this.options = spec.getOptions() == null ? new ImportOptions() : spec.getOptions();
        this.csvOptions = spec.getCsvOptions() == null ? null : spec.getCsvOptions().validate();
        this.valueProcessor = valueProcessor;
        this.sqlBuilder = Chat2DBContext.getSqlBuilder();
        this.connectInfo = Chat2DBContext.getConnectInfo();
        this.standardMode = !TaskExecutionMode.isUltraFast(spec.getMode());
        this.sqlExecutor = new ImportSqlExecutor(context, !standardMode);
        this.batchSizer = new AdaptiveBatchSizer(
                standardMode ? DEFAULT_BATCH_ROWS : FAST_MODE_BATCH_ROWS);
        int requestedWorkers = standardMode ? 1 : effectiveWorkerCount(connectInfo);
        List<BlockingQueue<PendingBatch>> builtQueues = null;
        AdaptiveConcurrencyGate builtGate = null;
        ExecutorService builtPool = null;
        if (requestedWorkers > 1) {
            try {
                builtQueues = new CopyOnWriteArrayList<>();
                for (int index = 0; index < requestedWorkers; index++) {
                    builtQueues.add(new ArrayBlockingQueue<>(QUEUE_CAPACITY));
                }
                // The fan-out may grow, but never past the machine's available parallelism: the
                // AIMD tuning moves inside [BASE_WORKERS, machineThreadCeiling()], and an explicit
                // chat2db.task.import.parallelism pin is bounded by the same ceiling.
                int gateCeiling = parallelismPinned()
                        ? Math.min(requestedWorkers, machineThreadCeiling())
                        : machineThreadCeiling();
                builtGate = AdaptiveConcurrencyGate.create(Math.min(BASE_WORKERS, requestedWorkers),
                        gateCeiling);
                builtPool = Executors.newCachedThreadPool(runnable -> {
                    Thread thread = new Thread(runnable, "chat2db-import-" + context.taskId());
                    thread.setDaemon(true);
                    return thread;
                });
            } catch (Throwable parallelStartupFailure) {
                // Adaptive parallel plumbing must never block the import: fall back to the exact
                // serial path, which stays fully supported.
                log.warn("Parallel import infrastructure failed to start; degrading to serial execution",
                        parallelStartupFailure);
                if (builtPool != null) {
                    builtPool.shutdownNow();
                }
                builtQueues = null;
                builtGate = null;
                builtPool = null;
                requestedWorkers = 1;
            }
        }
        this.workerCount = requestedWorkers;
        this.queues = builtQueues;
        this.gate = builtGate;
        this.workerPool = builtPool;
        if (this.workerPool != null) {
            for (int index = 0; index < this.workerCount; index++) {
                int workerIndex = index;
                this.workerPool.execute(() -> runWorker(workerIndex));
            }
        }
        warnIfSelfReferencing(spec, this.workerPool != null);
    }

    public void accept(long fileRowNumber, List<String> fileValues) {
        try {
            acceptRow(fileRowNumber, fileValues);
        } catch (RuntimeException taskFailure) {
            recordFailure(taskFailure);
            throw taskFailure;
        }
    }

    private void acceptRow(long fileRowNumber, List<String> fileValues) {
        context.checkCancelled();
        throwIfFailed();
        String sql;
        String raw;
        try {
            sql = buildInsert(fileRowNumber, fileValues);
            raw = JSON.toJSONString(fileValues);
        } catch (RuntimeException conversionFailure) {
            handleFailedRow(fileRowNumber, fileValues, conversionFailure);
            return;
        }
        bufferedSqls.add(sql);
        bufferedRows.add(raw);
        bufferedRowNumbers.add(fileRowNumber);
        if (bufferedSqls.size() >= batchSizer.batchSize()) {
            flushBufferedBatch();
        }
    }

    public long importedRows() {
        return importedCount.sum();
    }

    public long rejectedRows() {
        synchronized (rejectLock) {
            return rejectedRowCount;
        }
    }

    /** Final adaptive batch size; observability for tests and ops dashboards. */
    public int finalBatchSize() {
        return batchSizer.batchSize();
    }

    /** Available permits of the adaptive gate at call time (1 on the serial path). */
    public int gatePermits() {
        return gate == null ? 1 : gate.availablePermits();
    }

    /** Wall time of the import measured in {@link #close()}; 0 before the first close. */
    public long elapsedNanos() {
        return totalImportNanos;
    }

    /**
     * Final adaptive state of the most recently closed batcher. A process-wide snapshot because
     * callers that drive the importer through {@code CSVImporter} never hold the instance; the
     * last closed batcher wins when several imports run at once.
     */
    public record ImportTuningSnapshot(int workers, long batches, long rows, long nanos,
            int batchSize, int gatePermits, int peakInFlightBatches,
            long rejectedRows) { }

    private static final AtomicReference<ImportTuningSnapshot> LAST_TUNING = new AtomicReference<>();

    public static ImportTuningSnapshot lastTuningSnapshot() {
        return LAST_TUNING.get();
    }

    /**
     * Executes whatever is buffered; called at end of stream and whenever the caller needs a sync
     * point. In parallel mode this waits until every submitted batch finished.
     */
    public void flush() {
        try {
            context.checkCancelled();
            throwIfFailed();
            flushBufferedBatch();
            if (workerPool != null) {
                awaitQuiesce();
            }
        } catch (RuntimeException taskFailure) {
            recordFailure(taskFailure);
            throw taskFailure;
        }
    }

    /** Submits the current buffer without turning normal producer flow into a global barrier. */
    private void flushBufferedBatch() {
        if (bufferedSqls.isEmpty()) {
            return;
        }
        long firstRowNumber = bufferedRowNumbers.get(0);
        PendingBatch batch = new PendingBatch(List.copyOf(bufferedSqls), List.copyOf(bufferedRows),
                List.copyOf(bufferedRowNumbers), submittedBatches, firstRowNumber);
        submittedBatches++;
        bufferedSqls.clear();
        bufferedRows.clear();
        bufferedRowNumbers.clear();
        executeBatch(batch);
    }

    private void executeBatch(PendingBatch batch) {
        if (workerPool != null) {
            submitBatch(batch);
        } else {
            executeWithTolerance(batch);
        }
    }

    /**
     * Executes a finished batch in the calling (serial) or a worker (parallel) context and reports
     * the measured cost to the adaptive sizer and gate.
     */
    private void executeWithTolerance(PendingBatch batch) {
        long started = System.nanoTime();
        int rows = batch.sqls().size();
        try {
            try {
                sqlExecutor.executeBatch(batch.sqls());
                importedCount.add(rows);
            } catch (TaskCancelledException cancellation) {
                throw cancellation;
            } catch (RuntimeException batchFailure) {
                context.logError("IMPORT_BATCH_FAILED", "Could not import batch", Map.of(
                        "statementCount", rows,
                        "firstRow", batch.firstRowNumber(),
                        "message", StringUtils.defaultString(batchFailure.getMessage())));
                if (!isSkipMode()) {
                    throw batchFailure;
                }
                // Apply healthy halves as batches and record individually rejected rows.
                isolateBatchFailures(batch);
            }
        } finally {
            long elapsed = System.nanoTime() - started;
            if (gate != null) {
                gate.record(rows, elapsed);
            }
            if (!standardMode) {
                batchSizer.record(rows, elapsed);
            }
            if (workerPool != null) {
                batchCompleted();
            }
        }
    }

    /**
     * Locates the rows of a failed batch by repeated bisection: a half that executes cleanly is
     * applied as one batch again, a half that still fails is split further until the offending
     * rows stand alone. Each is recorded with its own cause and the import continues,
     * so k bad rows cost O(k log n)
     * executions instead of replaying every row of the batch one by one.
     */
    private void isolateBatchFailures(PendingBatch batch) {
        List<String> sqls = batch.sqls();
        isolateRange(sqls, batch.rows(), batch.rowNumbers(), 0, sqls.size());
    }

    private void isolateRange(List<String> sqls, List<String> rows, List<Long> rowNumbers,
            int from, int to) {
        if (from >= to) {
            return;
        }
        if (to - from == 1) {
            retryIsolatedRow(sqls.get(from), rows.get(from), rowNumbers.get(from));
            return;
        }
        int mid = (from + to) >>> 1;
        if (tryExecuteRange(sqls, from, mid)) {
            importedCount.add(mid - from);
        } else {
            isolateRange(sqls, rows, rowNumbers, from, mid);
        }
        if (tryExecuteRange(sqls, mid, to)) {
            importedCount.add(to - mid);
        } else {
            isolateRange(sqls, rows, rowNumbers, mid, to);
        }
    }

    /** Runs one range as a batch; a clean range is applied, a failing range is split further. */
    private boolean tryExecuteRange(List<String> sqls, int from, int to) {
        context.checkCancelled();
        try {
            sqlExecutor.executeBatch(sqls.subList(from, to));
            return true;
        } catch (TaskCancelledException cancellation) {
            throw cancellation;
        } catch (RuntimeException rangeFailure) {
            if (isConnectionFailure(rangeFailure)) {
                throw rangeFailure;
            }
            return false;
        }
    }

    /** Retries one isolated row and records its failure when skipping errors. */
    private void retryIsolatedRow(String sql, String rawRow, Long fileRowNumber) {
        try {
            sqlExecutor.executeBatch(List.of(sql));
            // A row that only failed inside a larger batch is healthy on its own.
            importedCount.increment();
            return;
        } catch (TaskCancelledException cancellation) {
            throw cancellation;
        } catch (RuntimeException rowFailure) {
            if (isConnectionFailure(rowFailure)) {
                throw rowFailure;
            }
            handleRejectedRow(fileRowNumber, rawRow, rootMessage(rowFailure));
        }
    }

    private static boolean isConnectionFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLNonTransientConnectionException
                    || current instanceof SQLRecoverableException
                    || current instanceof SQLTransientConnectionException) {
                return true;
            }
            if (current instanceof SQLException sqlException
                    && StringUtils.startsWith(sqlException.getSQLState(), "08")) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    // --- parallel plumbing ---------------------------------------------------------------

    /**
     * Resolves the worker count from the {@code chat2db.task.import.parallelism} system property:
     * {@code 0}, the default, picks the adaptive band ceiling {@code max(2, min(16, CPU cores))};
     * {@code 1} forces the serial path; explicit values are clamped into the [2, ceiling] band so
     * a pinned value can neither exceed the machine nor drop below the minimum fan-out. Parallel
     * workers each need their own connection, so without a JDBC url (test fixtures and
     * non-relational sources bind a prebuilt connection instead) the batcher stays serial.
     */
    /**
     * Upper bound of the import fan-out: how many processors this JVM may use. The adaptive gate
     * grows at most to that many concurrent batches, so an import can never request more threads
     * than the machine has left to run them.
     */
    private static int machineThreadCeiling() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    /** Whether {@code chat2db.task.import.parallelism} pins the fan-out explicitly. */
    private static boolean parallelismPinned() {
        return Integer.getInteger("chat2db.task.import.parallelism", 0) > 1;
    }

    private static int effectiveWorkerCount(ConnectInfo connectInfo) {
        if (StringUtils.isBlank(connectInfo.getUrl())) {
            return 1;
        }
        int configured = Integer.getInteger("chat2db.task.import.parallelism", 0);
        if (configured == 1) {
            return 1;
        }
        if (configured > 1) {
            return Math.min(configured, machineThreadCeiling());
        }
        return BASE_WORKERS;
    }

    /**
     * Grows the live worker set to match the adaptive gate: once the AIMD tuning admits more
     * concurrent batches than there are workers, another queue/worker pair is added. Growth is
     * unbounded by contract; the throughput feedback inside the gate is the only ceiling, so the
     * pool ends up exactly as wide as this machine and target database sustain.
     */
    private void ensureWorkerCapacity() {
        AdaptiveConcurrencyGate liveGate = gate;
        if (liveGate == null || closing.get()) {
            return;
        }
        int target = liveGate.totalPermits();
        synchronized (workerGrowthLock) {
            if (closing.get()) {
                return;
            }
            while (workerCount < target) {
                int index = workerCount;
                queues.add(new ArrayBlockingQueue<>(QUEUE_CAPACITY));
                workerPool.execute(() -> runWorker(index));
                workerCount = index + 1;
            }
        }
    }

    private void submitBatch(PendingBatch batch) {
        throwIfFailed();
        ensureWorkerCapacity();
        int inFlight = inFlightBatches.incrementAndGet();
        peakInFlightBatches.accumulateAndGet(inFlight, Math::max);
        BlockingQueue<PendingBatch> queue = queues.get((int) (batch.seq() % workerCount));
        try {
            // Bounded offer with failure checks: when every worker died there is nobody left to
            // drain the queues, and a blocking put would hang the import forever.
            while (!queue.offer(batch, 200L, TimeUnit.MILLISECONDS)) {
                throwIfFailed();
                context.checkCancelled();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            inFlightBatches.decrementAndGet();
            throw new TaskExecutionException(TaskErrorCode.IMPORT_FAILED.name(), "Import was interrupted");
        }
    }

    private void batchCompleted() {
        if (inFlightBatches.decrementAndGet() == 0) {
            synchronized (quiesceMonitor) {
                quiesceMonitor.notifyAll();
            }
        }
    }

    private void awaitQuiesce() {
        synchronized (quiesceMonitor) {
            while (inFlightBatches.get() > 0) {
                throwIfFailed();
                try {
                    quiesceMonitor.wait(50L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new TaskExecutionException(TaskErrorCode.IMPORT_FAILED.name(),
                            "Import was interrupted");
                }
            }
        }
        throwIfFailed();
    }

    /**
     * Warns once when the target references itself (e.g. {@code category.parent_id}): parallel
     * batches carry no parent-before-child order, so an enforced self-referencing foreign key
     * needs the serial path or a deferred constraint. Purely advisory — never fails the import.
     */
    private void warnIfSelfReferencing(ImportTaskSpec spec, boolean parallel) {
        if (!parallel) {
            return;
        }
        try (ResultSet keys = Chat2DBContext.getConnection().getMetaData()
                .getImportedKeys(null, null, spec.getTarget().getTableName())) {
            while (keys.next()) {
                String referencing = keys.getString("FKTABLE_NAME");
                String referenced = keys.getString("PKTABLE_NAME");
                if (referencing != null && referencing.equalsIgnoreCase(referenced)) {
                    log.warn("Target table {} references itself; parallel batch import carries no "
                            + "parent-before-child order — if the foreign key is enforced, use the "
                            + "serial path (chat2db.task.import.parallelism=1) or defer the constraint",
                            spec.getTarget().getTableName());
                    break;
                }
            }
        } catch (Throwable probeFailure) {
            log.debug("Self-reference probe skipped", probeFailure);
        }
    }

    private void runWorker(int workerIndex) {
        Thread.currentThread().setName("chat2db-import-" + context.taskId() + "-" + workerIndex);
        // Created on first use and owned by this worker until it exits; copy() carries no
        // connection, so Chat2DBContext.getConnection() builds a dedicated one per worker.
        ConnectInfo isolated = null;
        try {
            isolated = connectInfo.copy();
            isolated.setLoginUser("task-" + context.taskId() + "#import-" + workerIndex);
            Chat2DBContext.putContext(isolated);
            while (true) {
                PendingBatch batch = queues.get(workerIndex).take();
                if (batch == END_OF_QUEUE) {
                    return;
                }
                boolean permitted = gate.admit(GATE_WAIT_MILLIS);
                if (!permitted && ungatedWarned.compareAndSet(false, true)) {
                    log.warn("Adaptive import gate did not admit within {}ms; executing batches "
                            + "ungated until the gate recovers (degraded concurrency)", GATE_WAIT_MILLIS);
                }
                try {
                    throwIfFailed();
                    executeWithTolerance(batch);
                } finally {
                    gate.relinquish(permitted);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            recordFailure(t);
        } finally {
            if (isolated != null) {
                // Hand the dedicated connection back to the pool (or close it) instead of
                // leaking it until the JVM exits.
                ConnectionPool.close(isolated);
            }
            Chat2DBContext.removeContext();
        }
    }

    private void recordFailure(Throwable taskFailure) {
        failure.compareAndSet(null, taskFailure);
        aborted.set(true);
        synchronized (quiesceMonitor) {
            quiesceMonitor.notifyAll();
        }
    }

    private void throwIfFailed() {
        if (aborted.get()) {
            Throwable cause = failure.get();
            throw cause instanceof RuntimeException runtime ? runtime
                    : new TaskExecutionException(TaskErrorCode.IMPORT_FAILED.name(),
                    "Import failed", cause);
        }
    }

    private static final PendingBatch END_OF_QUEUE =
            new PendingBatch(List.of(), List.of(), List.of(), -1L, Long.MAX_VALUE);

    private record PendingBatch(List<String> sqls, List<String> rows, List<Long> rowNumbers,
            long seq, long firstRowNumber) {
    }

    // --- rejected-row bookkeeping ---------------------------------------------------------

    private void handleFailedRow(long fileRowNumber, List<String> fileValues, RuntimeException failure) {
        handleRejectedRow(fileRowNumber, JSON.toJSONString(fileValues), rootMessage(failure));
    }

    @SuppressWarnings("unused")
    private void handleFailedRowText(String rawRow, RuntimeException failure) {
        handleRejectedRow(null, rawRow, rootMessage(failure));
    }

    private void handleRejectedRow(Long fileRowNumber, String rawRow, String reason) {
        if (!isSkipMode()) {
            throw new TaskExecutionException(TaskErrorCode.IMPORT_FAILED.name(),
                    "Import row failed: " + reason);
        }
        synchronized (rejectLock) {
            rejectedRowCount++;
            Integer maxErrors = options.getMaxErrors();
            if (maxErrors != null && maxErrors >= 0 && rejectedRowCount > maxErrors) {
                throw new TaskExecutionException(TaskErrorCode.IMPORT_FAILED.name(),
                        "Import aborted after " + rejectedRowCount + " rejected rows");
            }
            try {
                rejectWriter().write(JSON.toJSONString(Map.of(
                        "row", fileRowNumber == null ? -1L : fileRowNumber,
                        "line", rawRow,
                        "reason", reason == null ? "unknown" : reason)));
                rejectWriter().write("\n");
            } catch (IOException e) {
                throw new UncheckedIOException("Could not write reject file", e);
            }
        }
        context.logWarn("IMPORT_ROW_REJECTED", "Import row rejected: " + reason,
                Map.of("row", fileRowNumber == null ? -1L : fileRowNumber,
                        "rejectedRows", rejectedRows()));
    }

    private BufferedWriter rejectWriter() throws IOException {
        if (rejectWriter == null) {
            String fileName = StringUtils.firstNonBlank(
                    new java.io.File(StringUtils.defaultString(spec.getSourceFile())).getName(), "import")
                    + ".rejects.ndjson";
            var draft = context.createArtifact(REJECT_ROLE,
                    StringUtils.substringBeforeLast(spec.getSourceFile(), java.io.File.separator),
                    fileName, "application/x-ndjson");
            rejectWriter = Files.newBufferedWriter(draft.getTemporaryFile().toPath(), StandardCharsets.UTF_8);
        }
        return rejectWriter;
    }

    private String buildInsert(long fileRowNumber, List<String> fileValues) {
        List<String> tableColumnNames = new ArrayList<>(resolution.tableColumns().size());
        List<String> values = new ArrayList<>(resolution.tableColumns().size());
        for (int index = 0; index < resolution.tableColumns().size(); index++) {
            TableColumn column = resolution.tableColumns().get(index);
            Integer sourceIndex = resolution.fileIndexes().get(index);
            String raw = sourceIndex != null && sourceIndex < fileValues.size()
                    ? fileValues.get(sourceIndex) : null;
            tableColumnNames.add(column.getName());
            values.add(toSqlLiteral(column, raw, fileRowNumber));
        }
        return sqlBuilder.dml().buildInsert(SingleInsertSqlRequest.builder()
                .databaseName(connectInfo.getDatabaseName())
                .schemaName(connectInfo.getSchemaName())
                .tableName(spec.getTarget().getTableName())
                .columnList(tableColumnNames)
                .valueList(values)
                .build());
    }

    private String toSqlLiteral(TableColumn column, String raw, long fileRowNumber) {
        if (raw == null || (options.getNullString() != null && options.getNullString().equals(raw))) {
            return null;
        }
        if (csvOptions != null) {
            raw = CsvImportValueNormalizer.normalize(raw, column, csvOptions, fileRowNumber);
        }
        DataType dataType = new DataType();
        dataType.setDataTypeName(column.getColumnType());
        dataType.setScale(column.getDecimalDigits());
        dataType.setPrecision(column.getColumnSize());
        SQLDataValue sqlDataValue = new SQLDataValue();
        sqlDataValue.setDataType(dataType);
        sqlDataValue.setValue(raw);
        return valueProcessor.getSqlValueString(sqlDataValue);
    }

    private boolean isSkipMode() {
        return ON_ERROR_SKIP.equalsIgnoreCase(StringUtils.trimToEmpty(options.getOnError()));
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    /** Stops pending writes when the source parser fails outside the batch executor. */
    public void abort(RuntimeException sourceFailure) {
        recordFailure(sourceFailure);
    }

    @Override
    public void close() {
        Throwable existingFailure = failure.get();
        try {
            if (existingFailure == null) {
                flush();
            }
        } finally {
            if (workerPool != null) {
                closing.set(true);
                for (BlockingQueue<PendingBatch> queue : queues) {
                    while (!queue.offer(END_OF_QUEUE)) {
                        if (aborted.get()) {
                            break;
                        }
                    }
                }
                if (aborted.get()) {
                    workerPool.shutdownNow();
                } else {
                    workerPool.shutdown();
                }
                try {
                    if (!workerPool.awaitTermination(30L, TimeUnit.SECONDS)) {
                        workerPool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    workerPool.shutdownNow();
                }
            }
            totalImportNanos = System.nanoTime() - createdNanos;
            long importedRows = importedCount.sum();
            double seconds = totalImportNanos / 1_000_000_000.0D;
            long rowsPerSecond = seconds > 0 ? (long) (importedRows / seconds) : 0L;
            // Final adaptive state: how far the AIMD gate grew/shrank and where the batch sizer
            // settled, for production observability and stress-test reporting.
            log.info("Import batcher finished: workers={}, batches={}, imported rows={}, "
                            + "in {}s -> {} rows/s, final batch size={}, "
                            + "final gate permits={}",
                    workerCount, submittedBatches, importedRows,
                    Math.round(seconds), rowsPerSecond,
                    batchSizer.batchSize(), gate == null ? 1 : gate.availablePermits());
            LAST_TUNING.set(new ImportTuningSnapshot(workerCount, submittedBatches, importedRows,
                    totalImportNanos, batchSizer.batchSize(),
                    gate == null ? 1 : gate.availablePermits(), peakInFlightBatches.get(),
                    rejectedRows()));
            if (rejectWriter != null) {
                try {
                    rejectWriter.flush();
                    rejectWriter.close();
                } catch (IOException e) {
                    log.warn("Could not close import reject writer", e);
                }
            }
        }
    }
}

package ai.chat2db.community.domain.core.impl.task.imports;

import ai.chat2db.community.domain.api.model.task.TaskCancelledException;
import ai.chat2db.community.domain.api.model.task.TaskErrorCode;
import ai.chat2db.community.domain.api.model.task.TaskEventCode;
import ai.chat2db.community.domain.api.model.task.TaskExecutionException;
import ai.chat2db.community.domain.api.service.task.TaskExecutionContext;
import ai.chat2db.spi.DefaultSQLExecutor;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class ImportSqlExecutor {

    private final TaskExecutionContext context;

    private final boolean atomicBatches;

    private final AtomicInteger batchSequence = new AtomicInteger();

    public ImportSqlExecutor(TaskExecutionContext context) {
        this(context, false);
    }

    /**
     * @param atomicBatches whether the whole row batch is committed as one transaction
     */
    public ImportSqlExecutor(TaskExecutionContext context, boolean atomicBatches) {
        this.context = context;
        this.atomicBatches = atomicBatches;
    }

    public void executeBatch(List<String> sqls) {
        if (CollectionUtils.isEmpty(sqls)) {
            return;
        }
        int batch = batchSequence.incrementAndGet();
        List<String> inserts = new ArrayList<>();
        int statementCount = 0;
        try {
            for (String sql : sqls) {
                context.checkCancelled();
                if (StringUtils.isBlank(sql)) {
                    continue;
                }
                statementCount++;
                if (sql.trim().toUpperCase().startsWith("INSERT")) {
                    inserts.add(sql);
                    continue;
                }
                flushInserts(inserts);
                executeStatement(sql);
            }
            flushInserts(inserts);
            context.logInfo(TaskEventCode.BATCH_EXECUTED.name(), "SQL batch executed",
                    Map.of("batch", batch, "statementCount", statementCount));
        } catch (TaskCancelledException | TaskExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new TaskExecutionException(TaskErrorCode.IMPORT_FAILED.name(),
                    "Could not execute imported SQL", e);
        }
    }

    public void executeSql(String sql) {
        if (StringUtils.isBlank(sql)) {
            return;
        }
        int batch = batchSequence.incrementAndGet();
        try {
            context.checkCancelled();
            executeStatement(sql);
            context.logInfo(TaskEventCode.BATCH_EXECUTED.name(), "SQL statement executed",
                    Map.of("batch", batch, "statementCount", 1));
        } catch (TaskCancelledException | TaskExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new TaskExecutionException(TaskErrorCode.IMPORT_FAILED.name(),
                    "Could not execute imported SQL", e);
        }
    }

    private void flushInserts(List<String> inserts) {
        if (inserts.isEmpty()) {
            return;
        }
        context.checkCancelled();
        // Keep a failed row batch atomic so error isolation can retry healthy rows safely.
        DefaultSQLExecutor.getInstance().executeBatchInsert(
                Chat2DBContext.getConnection(), List.copyOf(inserts), context, context::checkCancelled,
                atomicBatches ? 0 : DefaultSQLExecutor.BATCH_INSERT_CHUNK_SIZE);
        inserts.clear();
    }

    private void executeStatement(String sql) throws SQLException {
        context.checkCancelled();
        DefaultSQLExecutor.getInstance().execute(
                Chat2DBContext.getConnection(), sql, context, context::checkCancelled);
        context.checkCancelled();
    }
}

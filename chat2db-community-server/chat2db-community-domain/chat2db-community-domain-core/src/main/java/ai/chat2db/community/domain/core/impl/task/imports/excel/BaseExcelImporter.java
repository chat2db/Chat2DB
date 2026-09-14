package ai.chat2db.community.domain.core.impl.task.imports.excel;

import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.task.ImportTaskSpec;
import ai.chat2db.community.domain.api.service.task.TaskExecutionContext;
import ai.chat2db.community.domain.core.impl.task.imports.BaseImporter;
import ai.chat2db.community.domain.core.impl.task.imports.ImportColumnResolver;
import ai.chat2db.community.domain.core.impl.task.imports.ImportRowBatcher;
import ai.chat2db.spi.IValueProcessor;
import ai.chat2db.spi.sql.Chat2DBContext;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.util.ConverterUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * XLSX/XLS import through EasyExcel, sharing column resolution, batching and reject handling with
 * the CSV path.
 */
public abstract class BaseExcelImporter extends BaseImporter {

    @Override
    protected void doImportData(ImportTaskSpec spec, TaskExecutionContext context, List<TableColumn> columns) {
        ExcelTypeEnum excelType = getExcelType();
        try (NoModelDataListener listener = new NoModelDataListener(spec, context, columns,
                Chat2DBContext.getDbMetaData().getValueProcessor())) {
            try {
                EasyExcel.read(new File(spec.getSourceFile()), listener)
                        .excelType(excelType).sheet().headRowNumber(1).doRead();
                context.checkCancelled();
                listener.finish();
            } catch (RuntimeException failure) {
                listener.abort(failure);
                throw failure;
            }
        }
    }

    protected abstract ExcelTypeEnum getExcelType();

    public class NoModelDataListener extends AnalysisEventListener<Map<Integer, String>> implements AutoCloseable {

        private final ImportTaskSpec spec;

        private final TaskExecutionContext taskContext;

        private final List<TableColumn> columns;

        private final IValueProcessor valueProcessor;

        private ImportColumnResolver.Resolution resolution;

        private ImportRowBatcher batcher;

        private long rowNumber;

        private NoModelDataListener(ImportTaskSpec spec, TaskExecutionContext taskContext,
                List<TableColumn> columns, IValueProcessor valueProcessor) {
            this.spec = spec;
            this.taskContext = taskContext;
            this.columns = columns;
            this.valueProcessor = valueProcessor;
        }

        @Override
        public void invokeHead(Map<Integer, ReadCellData<?>> headCells, AnalysisContext context) {
            this.taskContext.checkCancelled();
            acceptHead(ConverterUtils.convertToStringMap(headCells, context));
        }

        void acceptHead(Map<Integer, String> headMap) {
            this.taskContext.checkCancelled();
            List<String> headers = values(headMap);
            resolution = ImportColumnResolver.resolveForSpec(columns, headers, spec);
            reportResolution(taskContext, resolution);
            ImportColumnResolver.validateForImport(columns, resolution, spec);
            batcher = new ImportRowBatcher(spec, taskContext, resolution, valueProcessor);
        }

        @Override
        public void invoke(Map<Integer, String> data, AnalysisContext context) {
            acceptRow(data, ++rowNumber);
        }

        void acceptRow(Map<Integer, String> data, long sourceRowNumber) {
            this.taskContext.checkCancelled();
            if (data == null || data.isEmpty() || batcher == null || resolution == null) {
                return;
            }
            batcher.accept(sourceRowNumber, values(data));
        }

        private List<String> values(Map<Integer, String> cells) {
            int count = cells.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
            List<String> values = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                values.add(cells.get(index));
            }
            return values;
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            this.taskContext.checkCancelled();
        }

        void finish() {
            if (batcher == null) {
                return;
            }
            batcher.flush();
            taskContext.logInfo("IMPORT_SUMMARY", "Excel import finished", Map.of(
                    "importedRows", batcher.importedRows(),
                    "rejectedRows", batcher.rejectedRows()));
        }

        void abort(RuntimeException failure) {
            if (batcher != null) {
                batcher.abort(failure);
            }
        }

        @Override
        public void close() {
            if (batcher != null) {
                batcher.close();
            }
        }
    }
}

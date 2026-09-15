package ai.chat2db.community.domain.core.impl.task.imports.excel;

import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.task.CsvOptions;
import ai.chat2db.community.domain.api.model.task.ImportTaskSpec;
import ai.chat2db.community.domain.api.service.task.TaskExecutionContext;
import ai.chat2db.community.domain.core.impl.db.CsvParser;
import ai.chat2db.community.domain.core.impl.task.imports.BaseImporter;
import ai.chat2db.community.domain.core.impl.task.imports.ImportColumnResolver;
import ai.chat2db.community.domain.core.impl.task.imports.ImportRowBatcher;
import ai.chat2db.community.domain.core.impl.task.imports.ImportRowSqlBuilder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CSV row parsing and submission for the explicitly selected fast mode. */
final class ParallelCSVImporter extends BaseImporter {

    @Override
    protected void doImportData(ImportTaskSpec spec, TaskExecutionContext context,
            List<TableColumn> columns) {
        CsvOptions options = (spec.getCsvOptions() == null ? CsvOptions.defaults() : spec.getCsvOptions()).validate();
        spec.setCsvOptions(options);
        ImportRowBatcher[] batcher = {null};
        ImportRowSqlBuilder rowSqlBuilder = new ImportRowSqlBuilder(spec, columns);
        int[] sourceRow = {0};
        try {
            new CsvParser(options).forEachRow(Path.of(spec.getSourceFile()), row -> {
                int rowNumber = ++sourceRow[0];
                if (Boolean.TRUE.equals(options.getHasHeader()) && rowNumber == options.getHeaderRow()) {
                    batcher[0] = createBatcher(spec, context, columns, row, rowSqlBuilder);
                    return;
                }
                if (rowNumber < options.getDataStartRow()
                        || options.getDataEndRow() != null && rowNumber > options.getDataEndRow()) {
                    return;
                }
                if (batcher[0] == null) {
                    int width = Math.max(row.size(), CSVImporter.mappedSourceColumnCount(spec));
                    batcher[0] = createBatcher(spec, context, columns, CSVImporter.syntheticHeader(width), rowSqlBuilder);
                }
                batcher[0].accept(rowNumber, rowSqlBuilder.build(row, rowNumber));
            }, context::checkCancelled);
            if (batcher[0] != null) {
                batcher[0].flush();
                context.logInfo("IMPORT_SUMMARY", "CSV import finished", Map.of(
                        "importedRows", batcher[0].importedRows()));
            }
        } catch (RuntimeException failure) {
            if (batcher[0] != null) {
                batcher[0].abort(failure);
            }
            throw failure;
        } finally {
            if (batcher[0] != null) {
                batcher[0].close();
            }
        }
    }

    private ImportRowBatcher createBatcher(ImportTaskSpec spec, TaskExecutionContext context,
            List<TableColumn> columns, Map<Integer, String> headers, ImportRowSqlBuilder rowSqlBuilder) {
        ImportColumnResolver.Resolution resolution = ImportColumnResolver.resolveForSpec(columns, values(headers), spec);

        ImportColumnResolver.validateForImport(columns, resolution, spec);
        rowSqlBuilder.acceptHead(headers);
        return new ImportRowBatcher(context);
    }

    private static List<String> values(Map<Integer, String> row) {
        int count = row.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(row.get(index));
        }
        return values;
    }

}

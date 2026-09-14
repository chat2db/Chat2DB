package ai.chat2db.community.domain.core.impl.task.imports.excel;

import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.task.CsvOptions;
import ai.chat2db.community.domain.api.model.task.ImportTaskSpec;
import ai.chat2db.community.domain.api.service.task.TaskExecutionContext;
import ai.chat2db.community.domain.core.impl.db.CsvParser;
import ai.chat2db.community.domain.core.impl.task.imports.BaseImporter;
import ai.chat2db.community.domain.core.impl.task.imports.ImportColumnResolver;
import ai.chat2db.community.domain.core.impl.task.imports.ImportRowBatcher;
import ai.chat2db.community.domain.core.impl.task.imports.IImportStrategy;
import ai.chat2db.spi.sql.Chat2DBContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CSV import driven by the validated {@link CsvOptions} contract and row batches. */
public class CSVImporter extends BaseImporter implements IImportStrategy {

    @Override
    protected void doImportData(ImportTaskSpec spec, TaskExecutionContext context,
            List<TableColumn> columns) {
        CsvOptions options = (spec.getCsvOptions() == null ? CsvOptions.defaults() : spec.getCsvOptions()).validate();
        spec.setCsvOptions(options);
        int skipRows = spec.getOptions() == null || spec.getOptions().getSkipRows() == null
                ? 0 : Math.max(0, spec.getOptions().getSkipRows());
        ImportRowBatcher[] batcher = {null};
        int[] sourceRow = {0};
        int[] skippedDataRows = {0};
        try {
            new CsvParser(options).forEachRow(Path.of(spec.getSourceFile()), row -> {
                int rowNumber = ++sourceRow[0];
                if (Boolean.TRUE.equals(options.getHasHeader()) && rowNumber == options.getHeaderRow()) {
                    batcher[0] = createBatcher(spec, context, columns, values(row));
                    return;
                }
                if (rowNumber < options.getDataStartRow()
                        || options.getDataEndRow() != null && rowNumber > options.getDataEndRow()) {
                    return;
                }
                if (skippedDataRows[0] < skipRows) {
                    skippedDataRows[0]++;
                    return;
                }
                if (batcher[0] == null) {
                    int width = Math.max(row.size(), mappedSourceColumnCount(spec));
                    batcher[0] = createBatcher(spec, context, columns, values(syntheticHeader(width)));
                }
                batcher[0].accept(rowNumber, values(row));
            }, context::checkCancelled);
            if (batcher[0] != null) {
                batcher[0].flush();
                context.logInfo("IMPORT_SUMMARY", "CSV import finished", Map.of(
                        "importedRows", batcher[0].importedRows(),
                        "rejectedRows", batcher[0].rejectedRows()));
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
            List<TableColumn> columns, List<String> headers) {
        ImportColumnResolver.Resolution resolution = ImportColumnResolver.resolveForSpec(columns, headers, spec);
        reportResolution(context, resolution);
        ImportColumnResolver.validateForImport(columns, resolution, spec);
        return new ImportRowBatcher(spec, context, resolution,
                Chat2DBContext.getDbMetaData().getValueProcessor());
    }

    private static List<String> values(Map<Integer, String> row) {
        int count = row.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(row.get(index));
        }
        return values;
    }

    private static Map<Integer, String> syntheticHeader(int columnCount) {
        Map<Integer, String> header = new LinkedHashMap<>();
        for (int index = 0; index < columnCount; index++) {
            header.put(index, "column_" + (index + 1));
        }
        return header;
    }

    private static int mappedSourceColumnCount(ImportTaskSpec spec) {
        if (spec.getColumnMappings() == null) {
            return 0;
        }
        return spec.getColumnMappings().stream()
                .map(mapping -> mapping.getSourceColumn())
                .filter(source -> source != null && source.startsWith("column_"))
                .mapToInt(source -> {
                    try {
                        return Integer.parseInt(source.substring("column_".length()));
                    } catch (NumberFormatException ignored) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
    }
}

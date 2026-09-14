package ai.chat2db.community.domain.core.impl.task.imports;

import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.task.ImportAdmissionFinding;
import ai.chat2db.community.domain.api.model.task.ImportAdmissionReport;
import ai.chat2db.community.domain.api.model.task.ImportTaskSpec;
import ai.chat2db.community.domain.api.model.task.TaskErrorCode;
import ai.chat2db.community.domain.api.model.task.TaskExecutionException;
import ai.chat2db.community.domain.api.model.task.TaskExecutionMode;
import ai.chat2db.community.domain.api.service.task.TaskExecutionContext;
import ai.chat2db.community.domain.api.model.task.CsvOptions;
import ai.chat2db.community.domain.core.impl.db.CsvParser;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Mandatory read-only admission gate for imports. It deliberately separates facts from mode
 * selection: the report records every detected blocker/degradation, then {@link #enforce}
 * applies the strict policy before an importer can construct worker threads.
 */
@SuppressWarnings("lgtm[java/path-injection]")
public final class ImportParallelAdmission {

    static final String SAFE = "PARALLEL_SAFE";
    static final String DEGRADED = "PARALLEL_DEGRADED";
    static final String FORBIDDEN = "PARALLEL_FORBIDDEN";
    static final String BLOCKER = "BLOCKER";
    static final String DEGRADATION = "DEGRADATION";

    private ImportParallelAdmission() {
    }

    @SuppressWarnings("lgtm[java/path-injection]")
    public static ImportAdmissionReport assess(ImportTaskSpec spec, List<TableColumn> tableColumns) {
        File source = new File(StringUtils.defaultString(spec.getSourceFile()));
        String format = StringUtils.upperCase(StringUtils.trimToEmpty(spec.getFormat()), Locale.ROOT);
        boolean requestedParallel = TaskExecutionMode.isUltraFast(spec.getMode());
        boolean relationshipAccepted = Boolean.TRUE.equals(spec.getConfirmedNoStrongRelations());
        List<ImportAdmissionFinding> findings = new ArrayList<>();
        long rows = -1L;
        boolean fullScan = false;

        if (!source.isFile() || !source.canRead()) {
            blocker(findings, "C4", "The import source is not a readable, seekable file",
                    source.getAbsolutePath(), "Select and stage a local file again.");
        } else if (isCompressed(source)) {
            blocker(findings, "C2", "Compressed or container input cannot enter parallel execution directly",
                    "ZIP/GZIP container signature detected", "Decompress to a supported plain-text format first.");
        } else if ("CSV".equals(format)) {
            CsvFacts facts = scanCsv(source, spec, findings);
            rows = facts.dataRows();
            fullScan = facts.fullScan();
            assessCsvTarget(spec, tableColumns, facts.headers(), relationshipAccepted, findings);
        } else if ("XLS".equals(format) || "XLSX".equals(format)) {
            blocker(findings, "C2", "Excel containers are not safely shardable",
                    format + " is a compressed binary workbook", "Use STANDARD mode or export a UTF-8 CSV source.");
        } else if ("SQL".equals(format)) {
            blocker(findings, "P0", "Safe parallel SQL planning is not available",
                    "SQL execution currently preserves file order on one connection",
                    "Use STANDARD mode until statement-boundary sharding, dependency barriers and validation are available.");
        } else if ("JSON".equals(format)) {
            blocker(findings, "P0", "The JSON array importer has no parallel shard planner",
                    "JSON arrays are consumed as one ordered stream", "Use STANDARD mode or convert to a validated CSV source.");
        } else {
            blocker(findings, "C2", "The file format is not eligible for parallel import",
                    StringUtils.defaultIfBlank(format, "UNKNOWN"), "Choose a supported UTF-8 CSV source or STANDARD mode.");
        }

        if (requestedParallel && "CSV".equals(format) && !relationshipAccepted) {
            blocker(findings, "R1", "Strong relationship and ordering risks have not been acknowledged",
                    "Logical foreign keys, trigger ordering and application-level parent/child dependencies cannot be proven from column metadata",
                    "Confirm that the target has no strong relationship or ordering dependency, or use STANDARD mode.");
        }
        if (requestedParallel && StringUtils.isBlank(spec.getImportFileId())) {
            blocker(findings, "G7", "Parallel input has not completed mandatory staging",
                    "No opaque staged-file identity is attached to the task", "Select the source through the import file picker and retry.");
        }

        boolean blocked = findings.stream().anyMatch(item -> BLOCKER.equals(item.getSeverity()));
        boolean degraded = findings.stream().anyMatch(item -> DEGRADATION.equals(item.getSeverity()));
        String verdict = blocked ? FORBIDDEN : degraded ? DEGRADED : SAFE;
        boolean parallelAllowed = requestedParallel && !blocked;
        String effectiveMode = parallelAllowed ? TaskExecutionMode.ULTRA_FAST : TaskExecutionMode.STANDARD;
        return ImportAdmissionReport.builder()
                .verdict(verdict)
                .requestedMode(requestedParallel ? TaskExecutionMode.ULTRA_FAST : TaskExecutionMode.STANDARD)
                .effectiveMode(effectiveMode)
                .parallelAllowed(parallelAllowed)
                .fileFormat(format)
                .fileSizeBytes(source.isFile() ? source.length() : 0L)
                .dataRows(rows)
                .fullScan(fullScan)
                .relationshipRiskAccepted(relationshipAccepted)
                .findings(List.copyOf(findings))
                .build();
    }

    public static ImportAdmissionReport enforce(ImportTaskSpec spec, List<TableColumn> tableColumns,
            TaskExecutionContext context) {
        ImportAdmissionReport report = assess(spec, tableColumns);
        Map<String, Object> details = details(report);
        if (TaskExecutionMode.isUltraFast(spec.getMode()) && FORBIDDEN.equals(report.getVerdict())) {
            context.logError("IMPORT_PARALLEL_ADMISSION", "Parallel import rejected before execution", details);
            String codes = report.getFindings().stream()
                    .filter(item -> BLOCKER.equals(item.getSeverity()))
                    .map(ImportAdmissionFinding::getCode).distinct().reduce((left, right) -> left + ", " + right)
                    .orElse("UNKNOWN");
            throw new TaskExecutionException(TaskErrorCode.IMPORT_PARALLEL_FORBIDDEN.name(),
                    "Parallel import rejected by admission rules: " + codes + ". Review the task event for evidence and remediation.");
        }
        if (FORBIDDEN.equals(report.getVerdict())) {
            context.logWarn("IMPORT_PARALLEL_ADMISSION",
                    "Serial import selected because parallel admission blockers were detected", details);
        } else if (DEGRADED.equals(report.getVerdict())) {
            context.logWarn("IMPORT_PARALLEL_ADMISSION", "Parallel import admitted with mandatory warnings", details);
        } else {
            context.logInfo("IMPORT_PARALLEL_ADMISSION", "Import admission completed", details);
        }
        if (report.isRelationshipRiskAccepted() && TaskExecutionMode.isUltraFast(report.getRequestedMode())) {
            context.logWarn("IMPORT_RELATIONSHIP_RISK_ACCEPTED",
                    "Operator confirmed that the target has no strong relationship or ordering dependency", details);
        }
        return report;
    }

    private static CsvFacts scanCsv(File source, ImportTaskSpec spec, List<ImportAdmissionFinding> findings) {
        try {
            CsvOptions options = (spec.getCsvOptions() == null ? CsvOptions.defaults() : spec.getCsvOptions()).validate();
            CsvScan scan = new CsvScan();
            new CsvParser(options).forEachRow(source.toPath(), record -> {
                long rowNumber = ++scan.records;
                List<String> values = new ArrayList<>(record.values());
                if (Boolean.TRUE.equals(options.getHasHeader()) && rowNumber == options.getHeaderRow()) {
                    scan.headers = values;
                    scan.width = values.size();
                    validateHeaders(values, findings);
                    return;
                }
                if (rowNumber < options.getDataStartRow()
                        || options.getDataEndRow() != null && rowNumber > options.getDataEndRow()) {
                    return;
                }
                if (scan.width < 0) {
                    scan.width = values.size();
                    scan.headers = java.util.stream.IntStream.rangeClosed(1, scan.width)
                            .mapToObj(index -> "column_" + index).toList();
                }
                scan.dataRows++;
                if (values.stream().anyMatch(value -> value != null
                        && (value.contains("\n") || value.contains("\r")))) {
                    blocker(findings, "C3", "A CSV field spans physical lines",
                            "Logical record " + rowNumber,
                            "Use STANDARD mode or pre-process embedded newlines into escaped text.");
                }
                if (values.size() != scan.width) {
                    blocker(findings, "D2", "CSV column count is inconsistent",
                            "Record " + rowNumber + " has " + values.size() + " columns; expected " + scan.width,
                            "Correct the CSV dialect or regenerate the source with a stable header.");
                }
            }, null);
            if (scan.headers.isEmpty()) {
                blocker(findings, "D2", "CSV has no usable columns", "No header or data columns were found",
                        "Check the header and data row settings.");
            }
            return new CsvFacts(scan.headers, scan.dataRows, true);
        } catch (Exception failure) {
            blocker(findings, "D1", "The CSV source could not be decoded and parsed deterministically",
                    failure.getClass().getSimpleName() + ": " + StringUtils.defaultString(failure.getMessage()),
                    "Specify the correct charset and CSV dialect, then preview the file again.");
            return new CsvFacts(List.of(), -1L, false);
        }
    }

    private static final class CsvScan {
        private List<String> headers = List.of();
        private int width = -1;
        private long records;
        private long dataRows;
    }

    private static void validateHeaders(List<String> headers, List<ImportAdmissionFinding> findings) {
        Set<String> normalized = new HashSet<>();
        for (String header : headers) {
            String value = StringUtils.trimToEmpty(header).toLowerCase(Locale.ROOT);
            if (value.isEmpty() || !normalized.add(value)) {
                blocker(findings, "D2", "CSV header cannot identify every source column",
                        value.isEmpty() ? "A blank column name was found" : "Duplicate column: " + header,
                        "Provide unique, non-empty headers and an explicit column mapping.");
            }
        }
    }

    private static void assessCsvTarget(ImportTaskSpec spec, List<TableColumn> columns, List<String> headers,
            boolean relationshipAccepted, List<ImportAdmissionFinding> findings) {
        if (headers.isEmpty() || columns == null || columns.isEmpty()) {
            return;
        }
        try {
            ImportColumnResolver.Resolution resolution = ImportColumnResolver.resolveForSpec(columns, headers, spec);
            Set<String> mapped = resolution.tableColumns().stream().map(TableColumn::getName)
                    .collect(java.util.stream.Collectors.toSet());
            List<String> omittedGeneratedKeys = columns.stream()
                    .filter(column -> Boolean.TRUE.equals(column.getAutoIncrement()))
                    .map(TableColumn::getName).filter(name -> !mapped.contains(name)).toList();
            if (!omittedGeneratedKeys.isEmpty()) {
                String evidence = "Generated key columns omitted from the input: " + omittedGeneratedKeys;
                if (relationshipAccepted) {
                    degradation(findings, "B1", "Generated key allocation order may change under concurrency",
                            evidence, "The operator accepted this risk; verify dependent data after import.");
                } else {
                    blocker(findings, "B1", "Generated key allocation may break parent/child identity ordering",
                            evidence, "Map explicit keys, confirm there are no strong relationships, or use STANDARD mode.");
                }
            }
        } catch (RuntimeException invalidMapping) {
            blocker(findings, "D2", "Column mapping is not deterministic", invalidMapping.getMessage(),
                    "Correct duplicate, missing or invalid source/target mappings.");
        }
    }

    @SuppressWarnings("lgtm[java/path-injection]")
    private static boolean isCompressed(File source) {
        try (var input = Files.newInputStream(source.toPath())) {
            int first = input.read();
            int second = input.read();
            return (first == 0x1f && second == 0x8b) || (first == 0x50 && second == 0x4b);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static Map<String, Object> details(ImportAdmissionReport report) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("verdict", report.getVerdict());
        details.put("requestedMode", report.getRequestedMode());
        details.put("effectiveMode", report.getEffectiveMode());
        details.put("parallelAllowed", report.isParallelAllowed());
        details.put("relationshipRiskAccepted", report.isRelationshipRiskAccepted());
        details.put("fileFormat", report.getFileFormat());
        details.put("fileSizeBytes", report.getFileSizeBytes());
        details.put("dataRows", report.getDataRows());
        details.put("fullScan", report.isFullScan());
        details.put("findings", report.getFindings().stream().map(item -> Map.of(
                "code", item.getCode(), "severity", item.getSeverity(), "message", item.getMessage(),
                "evidence", StringUtils.defaultString(item.getEvidence()),
                "remediation", StringUtils.defaultString(item.getRemediation()))).toList());
        return details;
    }

    private static void blocker(List<ImportAdmissionFinding> findings, String code, String message,
            String evidence, String remediation) {
        findings.add(finding(code, BLOCKER, message, evidence, remediation));
    }

    private static void degradation(List<ImportAdmissionFinding> findings, String code, String message,
            String evidence, String remediation) {
        findings.add(finding(code, DEGRADATION, message, evidence, remediation));
    }

    private static ImportAdmissionFinding finding(String code, String severity, String message,
            String evidence, String remediation) {
        return ImportAdmissionFinding.builder().code(code).severity(severity).message(message)
                .evidence(evidence).remediation(remediation).build();
    }

    private record CsvFacts(List<String> headers, long dataRows, boolean fullScan) {
    }
}

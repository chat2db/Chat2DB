package ai.chat2db.community.domain.core.impl.task.imports;

import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.task.ImportAdmissionReport;
import ai.chat2db.community.domain.api.model.task.ImportColumnMapping;
import ai.chat2db.community.domain.api.model.task.ImportTaskSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportParallelAdmissionTest {

    @TempDir
    Path tempDirectory;

    @Test
    void admitsFullyScannedCsvAfterRelationshipConfirmation() throws Exception {
        ImportAdmissionReport report = ImportParallelAdmission.assess(
                csvSpec("ID,NAME\n1,Alice\n", true, true), columns());

        assertEquals("PARALLEL_SAFE", report.getVerdict());
        assertEquals("ULTRA_FAST", report.getEffectiveMode());
        assertTrue(report.isParallelAllowed());
        assertTrue(report.isFullScan());
        assertEquals(1L, report.getDataRows());
    }

    @Test
    void requiresExplicitRelationshipConfirmation() throws Exception {
        ImportAdmissionReport report = ImportParallelAdmission.assess(
                csvSpec("ID,NAME\n1,Alice\n", false, true), columns());

        assertEquals("PARALLEL_FORBIDDEN", report.getVerdict());
        assertTrue(hasFinding(report, "R1", "BLOCKER"));
        assertFalse(report.isParallelAllowed());
    }

    @Test
    void confirmationTurnsGeneratedKeyOrderingIntoAnExplainableDegradation() throws Exception {
        ImportAdmissionReport report = ImportParallelAdmission.assess(
                csvSpec("NAME\nAlice\n", true, false), columns());

        assertEquals("PARALLEL_DEGRADED", report.getVerdict());
        assertTrue(hasFinding(report, "B1", "DEGRADATION"));
        assertTrue(report.isParallelAllowed());
        assertTrue(report.isRelationshipRiskAccepted());
    }

    @Test
    void embeddedCsvNewlineRemainsAHardBlocker() throws Exception {
        ImportAdmissionReport report = ImportParallelAdmission.assess(
                csvSpec("ID,NAME\n1,\"Alice\nCooper\"\n", true, true), columns());

        assertEquals("PARALLEL_FORBIDDEN", report.getVerdict());
        assertTrue(hasFinding(report, "C3", "BLOCKER"));
    }

    @Test
    void sqlCannotClaimParallelExecutionWithoutAPlanner() throws Exception {
        Path source = Files.writeString(tempDirectory.resolve("input.sql"),
                "CREATE TABLE sample(id INT); INSERT INTO sample VALUES (1);", StandardCharsets.UTF_8);
        ImportTaskSpec spec = ImportTaskSpec.builder().sourceFile(source.toString()).importFileId("staged")
                .format("SQL").mode("ULTRA_FAST").confirmedNoStrongRelations(true).build();

        ImportAdmissionReport report = ImportParallelAdmission.assess(spec, List.of());

        assertEquals("PARALLEL_FORBIDDEN", report.getVerdict());
        assertTrue(hasFinding(report, "P0", "BLOCKER"));
    }

    @Test
    void usesTheExistingCsvDelimiterInsteadOfSeparatePreviewOptions() throws Exception {
        ImportTaskSpec spec = csvSpec("ID;NAME\n1;Alice\n", true, true);
        var options = ai.chat2db.community.domain.api.model.task.CsvOptions.defaults();
        options.setDelimiter(";");
        spec.setCsvOptions(options);
        ImportAdmissionReport report = ImportParallelAdmission.assess(spec, columns());
        assertTrue(report.isParallelAllowed());
        assertEquals(1L, report.getDataRows());
    }

    @Test
    void honorsHeaderlessCsvAndSourceColumnMapping() throws Exception {
        ImportTaskSpec spec = csvSpec("1,Alice\n2,Bob\n", true, true);
        var options = ai.chat2db.community.domain.api.model.task.CsvOptions.defaults();
        options.setHasHeader(false);
        options.setDataStartRow(1);
        spec.setCsvOptions(options);
        spec.setColumnMappings(List.of(new ImportColumnMapping("column_1", "ID"),
                new ImportColumnMapping("column_2", "NAME")));
        ImportAdmissionReport report = ImportParallelAdmission.assess(spec, columns());
        assertTrue(report.isParallelAllowed());
        assertEquals(2L, report.getDataRows());
    }

    private ImportTaskSpec csvSpec(String content, boolean confirmed, boolean mapGeneratedKey) throws Exception {
        Path source = Files.writeString(tempDirectory.resolve("input-" + System.nanoTime() + ".csv"),
                content, StandardCharsets.UTF_8);
        List<ImportColumnMapping> mappings = mapGeneratedKey
                ? List.of(new ImportColumnMapping("ID", "ID"), new ImportColumnMapping("NAME", "NAME"))
                : List.of(new ImportColumnMapping("NAME", "NAME"));
        return ImportTaskSpec.builder().sourceFile(source.toString()).importFileId("staged")
                .format("CSV").mode("ULTRA_FAST").confirmedNoStrongRelations(confirmed)
                .columnMappings(mappings)
                .build();
    }

    private List<TableColumn> columns() {
        return List.of(
                TableColumn.builder().name("ID").primaryKey(true).autoIncrement(true).nullable(0).build(),
                TableColumn.builder().name("NAME").nullable(0).build());
    }

    private boolean hasFinding(ImportAdmissionReport report, String code, String severity) {
        return report.getFindings().stream()
                .anyMatch(item -> code.equals(item.getCode()) && severity.equals(item.getSeverity()));
    }
}

package ai.chat2db.community.updater.v2.runtime;

import ai.chat2db.community.updater.v2.audit.UpdateAuditLog;
import ai.chat2db.community.updater.v2.installation.UpdateLayout;
import ai.chat2db.community.updater.v2.model.UpdateHealth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateStartupCoordinatorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void candidateInstallRootPrefersPropertyAndFallsBackToHelperEnvironment() {
        assertEquals("/Applications/property.app",
            UpdateStartupCoordinator.resolveInstallRoot(
                "/Applications/property.app", "/Applications/environment.app"));
        assertEquals("/Applications/environment.app",
            UpdateStartupCoordinator.resolveInstallRoot(
                "", "/Applications/environment.app"));
    }

    @Test
    void candidateStartupFailureIsWrittenToTheUnifiedOperationLog() throws Exception {
        Path install = temporaryDirectory.resolve("Chat2DB Community.app");
        UpdateLayout layout = new UpdateLayout(
            install,
            install.resolve("Contents/app"),
            temporaryDirectory.resolve("cache"),
            temporaryDirectory.resolve("support")
        );
        UpdateAuditLog.begin(layout, "tx-candidate-failure", "APPLICATION");

        UpdateStartupCoordinator.reportStartupFailure(
            layout, "tx-candidate-failure", new IllegalStateException("spring failed"));

        String audit = Files.readString(layout.auditLogFile("tx-candidate-failure"));
        org.junit.jupiter.api.Assertions.assertTrue(audit.contains("actor=CANDIDATE"));
        org.junit.jupiter.api.Assertions.assertTrue(audit.contains("event=PROCESS_START_FAILED"));
        org.junit.jupiter.api.Assertions.assertTrue(audit.contains("spring failed"));
    }

    @Test
    void writesTransactionScopedHealthMarkerAtomically() throws Exception {
        Path install = temporaryDirectory.resolve("Other App.app");
        UpdateLayout layout = new UpdateLayout(
            install,
            install.resolve("Contents/app"),
            temporaryDirectory.resolve("cache"),
            temporaryDirectory.resolve("support")
        );
        Path healthFile = layout.healthFile("tx-health");

        UpdateStartupCoordinator.writeHealthMarker(
            healthFile,
            new UpdateHealth(
                "tx-health",
                "5.3.6-beta.1",
                UpdateHealth.TRIAL_HEALTHY,
                42L,
                1L
            )
        );

        assertEquals(healthFile,
            UpdateStartupCoordinator.healthFile(layout, "tx-health"));
        org.junit.jupiter.api.Assertions.assertTrue(Files.readString(healthFile)
            .contains("\"transactionId\" : \"tx-health\""));
        org.junit.jupiter.api.Assertions.assertTrue(Files.readString(healthFile)
            .contains("\"processId\" : 42"));
        org.junit.jupiter.api.Assertions.assertFalse(
            Files.exists(healthFile.resolveSibling("health-tx-health.json.tmp")));
    }
}

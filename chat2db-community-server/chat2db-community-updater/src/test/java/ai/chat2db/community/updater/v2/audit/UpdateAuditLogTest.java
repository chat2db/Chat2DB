package ai.chat2db.community.updater.v2.audit;

import ai.chat2db.community.updater.v2.enums.UpdatePhaseEnum;
import ai.chat2db.community.updater.v2.installation.UpdateLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateAuditLogTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void combinesActorsPhasesAndResultsInOneLog() throws Exception {
        UpdateLayout layout = layout();
        UpdateAuditLog application = UpdateAuditLog.begin(layout, "tx-1", "APPLICATION");
        application.versions("5.3.4", "5.3.5");
        application.info("DISCOVERY", "REQUEST", "GET https://example.com/latest.json?token=secret");
        application.phase(UpdatePhaseEnum.DISCOVERED, UpdatePhaseEnum.DOWNLOADING, 12, "COMMITTED");

        UpdateAuditLog helper = UpdateAuditLog.open(layout, "tx-1", "HELPER");
        helper.critical("NATIVE_INSTALLER", "INSTALL_RESULT",
            "status=INSTALLER_SUCCESS installRoot=/Users/private/path");
        helper.status(UpdateAuditLog.STATUS_SUCCESS, "COMMITTED", "done");

        String log = Files.readString(layout.auditLogFile("tx-1"));
        assertTrue(log.contains("actor=APPLICATION"));
        assertTrue(log.contains("actor=HELPER"));
        assertTrue(log.contains("stage=DOWNLOADING"));
        assertTrue(log.contains("event=INSTALL_RESULT"));
        assertFalse(log.contains("token=secret"));
        assertFalse(log.contains(System.getProperty("user.home")));
        assertTrue(Files.isRegularFile(layout.auditLogFile("tx-1")));
    }

    @Test
    void appendsCompleteRecordsFromTwoJvmProcesses() throws Exception {
        UpdateLayout layout = layout();
        UpdateAuditLog.begin(layout, "tx-concurrent", "APPLICATION");
        Process first = startAppender(layout, "APPLICATION", 50);
        Process second = startAppender(layout, "HELPER", 50);

        assertTrue(first.waitFor(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS));
        assertTrue(second.waitFor(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS));
        assertEquals(0, first.exitValue());
        assertEquals(0, second.exitValue());

        List<String> lines = Files.readAllLines(layout.auditLogFile("tx-concurrent"));
        assertEquals(102, lines.size());
        assertEquals(50, lines.stream().filter(line -> line.contains("actor=APPLICATION")
            && line.contains("stage=CONCURRENT")).count());
        assertEquals(50, lines.stream().filter(line -> line.contains("actor=HELPER")
            && line.contains("stage=CONCURRENT")).count());
        assertTrue(lines.stream().allMatch(line -> line.contains(" eventId=") && line.endsWith("\"")));
    }

    private Process startAppender(UpdateLayout layout, String actor, int count) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java");
        return new ProcessBuilder(
            java.toString(),
            "-cp", System.getProperty("java.class.path"),
            AuditAppenderMain.class.getName(),
            layout.installTarget().toString(),
            layout.appDirectory().toString(),
            layout.cacheRoot().toString(),
            layout.supportRoot().toString(),
            "tx-concurrent",
            actor,
            Integer.toString(count)
        ).redirectErrorStream(true).start();
    }

    private UpdateLayout layout() {
        Path install = temporaryDirectory.resolve("Chat2DB Community.app");
        return new UpdateLayout(
            install,
            install.resolve("Contents/app"),
            temporaryDirectory.resolve("cache"),
            temporaryDirectory.resolve("support")
        );
    }
}

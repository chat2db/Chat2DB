package ai.chat2db.community.updater.v2.runtime;

import ai.chat2db.community.updater.v2.installation.UpdateLayout;
import ai.chat2db.community.updater.v2.model.InstalledAppVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstalledAppVersionReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsLegacyV1MetadataWhenInstalledVersionIsAbsent() throws Exception {
        UpdateLayout layout = layout();
        Files.writeString(layout.appDirectory().resolve("local_version.json"), """
            {
              "version": "5.3.5",
              "releaseNotes": "Known issue fixes",
              "files": [
                {
                  "id": "chat2db-server",
                  "serverFileName": "chat2db-community.jar",
                  "localTargetName": "chat2db-community.jar",
                  "sha256": "27c3b8824ab26bd335cbb9522da138ef1311f6bf92f1ee5a7d2aa6e24dbcf7b1",
                  "type": "jar",
                  "fileSizeByte": 206779706
                }
              ],
              "launchCommand": null
            }
            """);

        InstalledAppVersion installed = new InstalledAppVersionReader(layout).read();

        assertEquals("5.3.5", installed.version());
        assertEquals(0L, installed.releaseEpoch());
        assertEquals("legacy-v1-bridge", installed.buildSha());
    }

    @Test
    void prefersInstalledVersionMetadataOverLegacyV1Metadata() throws Exception {
        UpdateLayout layout = layout();
        Files.writeString(layout.appDirectory().resolve("version.json"), """
            {"version":"5.3.6","releaseEpoch":2,"buildSha":"build-536"}
            """);
        Files.writeString(layout.appDirectory().resolve("local_version.json"), """
            {"version":"5.3.5","files":[]}
            """);

        InstalledAppVersion installed = new InstalledAppVersionReader(layout).read();

        assertEquals("5.3.6", installed.version());
        assertEquals(2L, installed.releaseEpoch());
        assertEquals("build-536", installed.buildSha());
    }

    @Test
    void rejectsLegacyV1MetadataWithoutVersion() throws Exception {
        UpdateLayout layout = layout();
        Path legacyVersionFile = layout.appDirectory().resolve("local_version.json");
        Files.writeString(legacyVersionFile, "{\"files\":[]}");

        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> new InstalledAppVersionReader(layout).read()
        );

        assertEquals("Cannot load legacy installed app version: " + legacyVersionFile, failure.getMessage());
    }

    @Test
    void readsExplicitLegacyVersionPropertyWhenMetadataIsAbsent() throws Exception {
        System.setProperty("chat2db.version", " 5.3.0 ");
        try {
            InstalledAppVersion installed = new InstalledAppVersionReader(layout()).read();

            assertEquals("5.3.0", installed.version());
            assertEquals(0L, installed.releaseEpoch());
            assertEquals("legacy-bridge", installed.buildSha());
        } finally {
            System.clearProperty("chat2db.version");
        }
    }

    @Test
    void rejectsMissingInstalledVersionInsteadOfGuessing() throws Exception {
        System.clearProperty("chat2db.version");

        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> new InstalledAppVersionReader(layout()).read()
        );

        assertEquals("Cannot determine installed app version", failure.getMessage());
    }

    private UpdateLayout layout() throws Exception {
        Path installTarget = tempDir.resolve("Other App.app");
        Path appDirectory = installTarget.resolve("Contents/app");
        Files.createDirectories(appDirectory);
        return new UpdateLayout(
            installTarget,
            appDirectory,
            tempDir.resolve("cache"),
            tempDir.resolve("support")
        );
    }
}

package ai.chat2db.community.updater.v2.installation;

import ai.chat2db.community.updater.v2.enums.ReleaseStatusEnum;
import ai.chat2db.community.updater.v2.enums.UpdateArchitectureEnum;
import ai.chat2db.community.updater.v2.enums.UpdateChannelEnum;
import ai.chat2db.community.updater.v2.model.UpdateManifest;
import ai.chat2db.community.updater.v2.enums.UpdatePackageTypeEnum;
import ai.chat2db.community.updater.v2.enums.UpdatePlatformEnum;
import ai.chat2db.community.updater.v2.enums.UpdateScopeEnum;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullPackageStagerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void stagesOpaqueFullPackageArchive() throws Exception {
        Path source = temporaryDirectory.resolve("source");
        Path launcher = writeExecutable(source.resolve("package/bin/chat2db"), "launcher");
        Files.writeString(source.resolve("package/new-layout.cfg"), "cfg");
        Path archive = archive(source, "package");
        UpdateLayout layout = testLayout("installed");

        Path staged = new FullPackageStager().stage(
            archive, manifest(UpdatePackageTypeEnum.MACOS_APP_ARCHIVE, "bin/chat2db"), layout
        );

        assertEquals("cfg", Files.readString(staged.resolve("new-layout.cfg")));
        assertTrue(Files.isRegularFile(staged.resolve("bin/chat2db")));
        assertFalse(Files.exists(layout.installTarget().resolve("new-layout.cfg")));
    }

    @Test
    void rejectsArchiveWithoutPackageRoot() throws Exception {
        Path source = temporaryDirectory.resolve("wrong-root-source");
        writeExecutable(source.resolve("other/bin/chat2db"), "launcher");
        Path archive = archive(source, "other");
        UpdateLayout layout = testLayout("installed");

        assertThrows(IllegalStateException.class, () -> new FullPackageStager().stage(
            archive, manifest(UpdatePackageTypeEnum.MACOS_APP_ARCHIVE, "bin/chat2db"), layout
        ));
        assertFalse(Files.exists(layout.stagingDirectory()));
    }

    @Test
    void rejectsSymlinkEscapingFullPackage() throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
        Path source = temporaryDirectory.resolve("symlink-source");
        writeExecutable(source.resolve("package/bin/chat2db"), "launcher");
        Files.createSymbolicLink(source.resolve("package/outside"), Path.of("../../outside"));
        Path archive = archive(source, "package");

        assertThrows(IllegalStateException.class, () -> new FullPackageStager().stage(
            archive,
            manifest(UpdatePackageTypeEnum.MACOS_APP_ARCHIVE, "bin/chat2db"),
            testLayout("installed")
        ));
    }

    @Test
    void stagesAppImageAsExecutableSingleFile() throws Exception {
        Path appImage = temporaryDirectory.resolve("Chat2DB.AppImage");
        Files.writeString(appImage, "image");
        UpdateLayout layout = testLayout("installed.AppImage");

        Path staged = new FullPackageStager().stage(
            appImage, manifest(UpdatePackageTypeEnum.LINUX_APPIMAGE, "."), layout
        );

        assertEquals("image", Files.readString(staged));
        assertTrue(Files.isExecutable(staged));
    }

    private Path archive(Path source, String topLevel) throws Exception {
        Path archive = temporaryDirectory.resolve(topLevel + "-" + System.nanoTime() + ".tar.gz");
        Process process = new ProcessBuilder(
            "tar", "-czf", archive.toString(), "-C", source.toString(), topLevel
        ).inheritIO().start();
        assertEquals(0, process.waitFor());
        return archive;
    }

    private static Path writeExecutable(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        assertTrue(path.toFile().setExecutable(true, false));
        return path;
    }

    private static UpdateManifest manifest(UpdatePackageTypeEnum packageType, String launcher) {
        return new UpdateManifest(
            2, 1, ReleaseStatusEnum.ACTIVE, "COMMUNITY", UpdateChannelEnum.STABLE, "5.3.4", "5.3.401", "sha",
            packageType == UpdatePackageTypeEnum.LINUX_APPIMAGE ? UpdatePlatformEnum.LINUX : UpdatePlatformEnum.MACOS,
            UpdateArchitectureEnum.ARM64, UpdateScopeEnum.FULL_PACKAGE, packageType,
            "https://example.com/package." + packageType.fileExtension(), 1024, "a".repeat(64), launcher,
            3, 3, "https://example.com/notes", "key", "signature"
        );
    }

    private UpdateLayout testLayout(String installName) {
        Path install = temporaryDirectory.resolve("Applications").resolve(installName);
        return new UpdateLayout(
            install,
            install.resolve("app"),
            temporaryDirectory.resolve("cache").resolve(installName),
            temporaryDirectory.resolve("support").resolve(installName)
        );
    }
}

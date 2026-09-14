package ai.chat2db.community.updater.v2.installation;

import ai.chat2db.community.updater.v2.enums.UpdatePlatformEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateWorkspaceInitializerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsWritableUserCacheAndSupportWorkspacesWithoutInstallSiblingDirectory() {
        Path installTarget = temporaryDirectory.resolve("Applications/Chat2DB Community.app");
        UpdateLayout layout = testLayout(installTarget, "pro");

        new UpdateWorkspaceInitializer(layout).ensureReady(UpdatePlatformEnum.MACOS);

        assertTrue(Files.isDirectory(layout.cacheRoot()));
        assertTrue(Files.isDirectory(layout.supportRoot()));
        assertFalse(layout.cacheRoot().startsWith(installTarget.getParent()));
        assertFalse(layout.supportRoot().startsWith(installTarget.getParent()));
        assertFalse(Files.exists(installTarget.getParent().resolve(".Chat2DB_Pro.app.update")));
    }

    private UpdateLayout testLayout(Path installTarget, String product) {
        return new UpdateLayout(
            installTarget,
            installTarget.resolve("Contents/app"),
            temporaryDirectory.resolve("Library/Caches/Chat2DB/Updater").resolve(product),
            temporaryDirectory.resolve("Library/Application Support/Chat2DB/Updater").resolve(product)
        );
    }
}

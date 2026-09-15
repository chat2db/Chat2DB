package ai.chat2db.community.updater.v2.state;

import ai.chat2db.community.updater.v2.installation.UpdateLayout;
import ai.chat2db.community.updater.v2.model.UpdatePreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdatePreferencesStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultsToStableAndPersistsBetaInApplicationSupport() {
        Path install = temporaryDirectory.resolve("Applications/Chat2DB Community.app");
        Path support = temporaryDirectory.resolve("Library/Application Support/Chat2DB/Updater/community");
        UpdateLayout layout = new UpdateLayout(
            install, install.resolve("Contents/app"),
            temporaryDirectory.resolve("Library/Caches/Chat2DB/Updater/community"), support);
        UpdatePreferencesStore store = new UpdatePreferencesStore(layout);

        assertFalse(store.load().receiveBeta());
        store.save(new UpdatePreferences(true));

        assertTrue(store.load().receiveBeta());
        assertFalse(layout.preferencesFile().startsWith(layout.installTarget()));
        assertTrue(layout.preferencesFile().startsWith(support));
    }
}

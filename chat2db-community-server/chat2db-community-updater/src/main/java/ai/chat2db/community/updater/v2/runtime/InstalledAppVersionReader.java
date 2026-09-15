package ai.chat2db.community.updater.v2.runtime;

import ai.chat2db.community.updater.v2.model.InstalledAppVersion;
import ai.chat2db.community.updater.v2.installation.UpdateLayout;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

public final class InstalledAppVersionReader {

    private final UpdateLayout layout;
    private final ObjectMapper objectMapper;
    private final java.util.function.Supplier<String> legacyVersion;

    public InstalledAppVersionReader(UpdateLayout layout) {
        this(layout, () -> System.getProperty("chat2db.version"));
    }

    public InstalledAppVersionReader(UpdateLayout layout, java.util.function.Supplier<String> legacyVersion) {
        this.legacyVersion = legacyVersion;
        this.layout = layout;
        this.objectMapper = new ObjectMapper();
    }

    public InstalledAppVersion read() {
        Path versionFile = layout.appDirectory().resolve("version.json");
        if (Files.isRegularFile(versionFile)) {
            try {
                return objectMapper.readValue(versionFile.toFile(), InstalledAppVersion.class);
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot load installed app version: " + versionFile, exception);
            }
        }
        Path legacyVersionFile = layout.appDirectory().resolve("local_version.json");
        if (Files.isRegularFile(legacyVersionFile)) {
            try {
                JsonNode root = objectMapper.readTree(legacyVersionFile.toFile());
                JsonNode versionNode = root == null ? null : root.get("version");
                String version = versionNode == null ? null : versionNode.asText();
                if (version == null || version.isBlank()) {
                    throw new IllegalStateException("Legacy installed app version is missing");
                }
                return new InstalledAppVersion(version.trim(), 0L, "legacy-v1-bridge");
            } catch (Exception exception) {
                throw new IllegalStateException(
                    "Cannot load legacy installed app version: " + legacyVersionFile,
                    exception
                );
            }
        }
        String legacyVersion = this.legacyVersion.get();
        if (legacyVersion == null || legacyVersion.isBlank()) {
            legacyVersion = System.getProperty("chat2db.version");
        }
        if (legacyVersion == null || legacyVersion.isBlank()) {
            throw new IllegalStateException("Cannot determine installed app version");
        }
        return new InstalledAppVersion(legacyVersion.trim(), 0L, "legacy-bridge");
    }
}

package ai.chat2db.community.updater.v2.state;

import ai.chat2db.community.updater.v2.installation.UpdateLayout;
import ai.chat2db.community.updater.v2.model.UpdatePreferences;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class UpdatePreferencesStore {

    private final UpdateLayout layout;
    private final ObjectMapper objectMapper;

    public UpdatePreferencesStore(UpdateLayout layout) {
        this(layout, new ObjectMapper());
    }

    UpdatePreferencesStore(UpdateLayout layout, ObjectMapper objectMapper) {
        this.layout = layout;
        this.objectMapper = objectMapper;
    }

    public synchronized UpdatePreferences load() {
        Path file = layout.preferencesFile();
        if (!Files.isRegularFile(file)) {
            return UpdatePreferences.defaults();
        }
        try {
            return objectMapper.readValue(file.toFile(), UpdatePreferences.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load update preferences", exception);
        }
    }

    public synchronized void save(UpdatePreferences preferences) {
        Path file = layout.preferencesFile();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), preferences);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist update preferences", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }
}

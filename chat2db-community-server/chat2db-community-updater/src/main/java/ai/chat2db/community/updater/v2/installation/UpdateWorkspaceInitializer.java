package ai.chat2db.community.updater.v2.installation;

import ai.chat2db.community.updater.v2.enums.UpdatePlatformEnum;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.UUID;

public final class UpdateWorkspaceInitializer {

    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE
    );

    private final UpdateLayout layout;

    public UpdateWorkspaceInitializer(UpdateLayout layout) {
        this.layout = layout;
    }

    public void ensureReady(UpdatePlatformEnum platform) {
        try {
            createAndVerify(layout.cacheRoot());
            createAndVerify(layout.supportRoot());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot prepare the user update workspace", exception);
        }
    }

    private static void createAndVerify(Path root) throws IOException {
        rejectSymbolicLink(root);
        Files.createDirectories(root);
        rejectSymbolicLink(root);
        try {
            Files.setPosixFilePermissions(root, OWNER_ONLY);
        } catch (UnsupportedOperationException ignored) {
            // The current user's standard application-data directory already owns its ACL on Windows.
        }
        Path probe = root.resolve(".write-probe-" + UUID.randomUUID());
        try {
            Files.writeString(probe, "ok", StandardCharsets.US_ASCII);
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    private static void rejectSymbolicLink(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new IOException("Update workspace path must not be a symbolic link: " + path);
        }
    }
}

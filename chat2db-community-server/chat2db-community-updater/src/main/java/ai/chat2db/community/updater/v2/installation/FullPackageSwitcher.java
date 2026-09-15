package ai.chat2db.community.updater.v2.installation;

import ai.chat2db.community.updater.v2.enums.UpdatePackageTypeEnum;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;

public final class FullPackageSwitcher {

    private final UpdateLayout layout;

    public FullPackageSwitcher(UpdateLayout layout) {
        this.layout = layout;
    }

    public void switchToCandidate(String operationId, UpdatePackageTypeEnum packageType) {
        if (!packageType.directReplacement()) {
            throw new IllegalArgumentException("Native installer package cannot use direct replacement: " + packageType);
        }
        Path currentPackage = layout.installTarget();
        Path stagedPackage = layout.stagedPackage(packageType);
        requirePackage(currentPackage, packageType, "Current full package is missing");
        requirePackage(stagedPackage, packageType, "Staged full package is missing");
        try {
            deleteRecursively(currentPackage);
            copyPackage(stagedPackage, currentPackage);
            requirePackage(currentPackage, packageType, "Copied candidate package is missing");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot switch full application package", exception);
        }
    }

    public void commit(String operationId) {
        try {
            deleteRecursively(layout.stagingDirectory());
            for (UpdatePackageTypeEnum packageType : UpdatePackageTypeEnum.values()) {
                Files.deleteIfExists(layout.cachedPackage(packageType));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot clean committed full-package transaction", exception);
        }
    }

    private static void requirePackage(Path path, UpdatePackageTypeEnum type, String message) {
        boolean present = type.singleFile() ? Files.isRegularFile(path) : Files.isDirectory(path);
        if (!present) {
            throw new IllegalStateException(message + ": " + path);
        }
    }

    private static void copyPackage(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source)) {
            Files.copy(source, target, LinkOption.NOFOLLOW_LINKS);
            return;
        }
        if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        }
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                Path destination = target.resolve(source.relativize(directory));
                Files.createDirectory(destination);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Path destination = target.resolve(source.relativize(file));
                Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(path) || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(path);
            return;
        }
        try (var entries = Files.walk(path)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }
}

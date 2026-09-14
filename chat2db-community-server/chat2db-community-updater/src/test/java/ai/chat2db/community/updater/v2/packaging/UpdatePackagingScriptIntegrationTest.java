package ai.chat2db.community.updater.v2.packaging;

import ai.chat2db.community.updater.v2.installation.FullPackageStager;
import ai.chat2db.community.updater.v2.model.ReleaseIndex;
import ai.chat2db.community.updater.v2.model.ReleaseReference;
import ai.chat2db.community.updater.v2.enums.ReleaseStatusEnum;
import ai.chat2db.community.updater.v2.enums.UpdateArchitectureEnum;
import ai.chat2db.community.updater.v2.enums.UpdateChannelEnum;
import ai.chat2db.community.updater.v2.model.UpdateEnvironment;
import ai.chat2db.community.updater.v2.installation.UpdateLayout;
import ai.chat2db.community.updater.v2.model.UpdateManifest;
import ai.chat2db.community.updater.v2.verification.UpdateManifestVerifier;
import ai.chat2db.community.updater.v2.enums.UpdatePackageTypeEnum;
import ai.chat2db.community.updater.v2.enums.UpdatePlatformEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UpdatePackagingScriptIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String VERSION = "5.3.4";
    private static final long RELEASE_EPOCH = 101L;
    private static final String BUILD_SHA = "a".repeat(40);

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatedFullPackagesRoundTripThroughJavaVerifierStagerAndIndex() throws Exception {
        assumeTrue(!System.getProperty("os.name", "").toLowerCase().contains("win"));
        for (String command : List.of("bash", "jq", "openssl", "tar", "perl")) {
            assumeTrue(commandExists(command), () -> "Required packaging command is missing: " + command);
        }

        Path projectRoot = findProjectRoot();
        Path archive = createFullPackageArchive();
        Path appImage = temporaryDirectory.resolve("Chat2DB-Community-5.3.4-arm64.AppImage");
        Files.writeString(appImage, "complete-appimage");
        assertTrue(appImage.toFile().setExecutable(true, false));
        Path nativePackage = temporaryDirectory.resolve("native-package.bin");
        Files.writeString(nativePackage, "native-package");
        Path outputDirectory = temporaryDirectory.resolve("release");
        Path privateKey = temporaryDirectory.resolve("update-key.pem");
        String openssl = opensslCommand();
        run(List.of(openssl, "genpkey", "-algorithm", "ED25519", "-out", privateKey.toString()), Map.of());
        byte[] encodedPublicKey = runBinary(List.of(
            openssl, "pkey", "-in", privateKey.toString(), "-pubout", "-outform", "DER"
        ));
        String publicKeyBase64 = Base64.getEncoder().encodeToString(encodedPublicKey);
        Map<String, String> signingEnvironment = Map.of(
            "CHAT2DB_UPDATE_KEY_ID", "integration-key",
            "CHAT2DB_UPDATE_SIGNING_PRIVATE_KEY_FILE", privateKey.toString(),
            "CHAT2DB_UPDATE_PUBLIC_KEY_B64", publicKeyBase64,
            "CHAT2DB_OPENSSL_BIN", openssl
        );

        List<Target> targets = List.of(
            new Target(UpdatePlatformEnum.MACOS, UpdateArchitectureEnum.ARM64,
                UpdatePackageTypeEnum.MACOS_APP_ARCHIVE, archive, "Contents/MacOS/Chat2DB Community"),
            new Target(UpdatePlatformEnum.MACOS, UpdateArchitectureEnum.X64,
                UpdatePackageTypeEnum.MACOS_APP_ARCHIVE, archive, "Contents/MacOS/Chat2DB Community"),
            new Target(UpdatePlatformEnum.WINDOWS, UpdateArchitectureEnum.X64,
                UpdatePackageTypeEnum.WINDOWS_EXE, nativePackage, "Chat2DB Community.exe"),
            new Target(UpdatePlatformEnum.LINUX, UpdateArchitectureEnum.ARM64,
                UpdatePackageTypeEnum.LINUX_APPIMAGE, appImage, "."),
            new Target(UpdatePlatformEnum.LINUX, UpdateArchitectureEnum.X64,
                UpdatePackageTypeEnum.LINUX_APPIMAGE, appImage, "."),
            new Target(UpdatePlatformEnum.LINUX, UpdateArchitectureEnum.ARM64,
                UpdatePackageTypeEnum.LINUX_DEB, nativePackage, "bin/Chat2DB Community"),
            new Target(UpdatePlatformEnum.LINUX, UpdateArchitectureEnum.X64,
                UpdatePackageTypeEnum.LINUX_DEB, nativePackage, "bin/Chat2DB Community"),
            new Target(UpdatePlatformEnum.LINUX, UpdateArchitectureEnum.ARM64,
                UpdatePackageTypeEnum.LINUX_RPM, nativePackage, "bin/Chat2DB Community"),
            new Target(UpdatePlatformEnum.LINUX, UpdateArchitectureEnum.X64,
                UpdatePackageTypeEnum.LINUX_RPM, nativePackage, "bin/Chat2DB Community")
        );
        List<Path> manifests = new ArrayList<>();
        for (Target target : targets) {
            Map<String, String> targetEnvironment = new java.util.HashMap<>(signingEnvironment);
            run(List.of(
                "bash",
                projectRoot.resolve("script/package/generate_update_v2.sh").toString(),
                VERSION,
                "5.3.401",
                "COMMUNITY",
                "STABLE",
                target.platform().name(),
                target.architecture().name(),
                target.packageType().name(),
                target.packageFile().toString(),
                target.launcher(),
                outputDirectory.toString(),
                "https://github.com/OtterMind/Chat2DB/releases/download/" + VERSION,
                Long.toString(RELEASE_EPOCH),
                BUILD_SHA,
                "https://chat2db.ai/release-notes/" + VERSION
            ), targetEnvironment);
            manifests.add(outputDirectory.resolve(manifestName(target)));
        }

        Path armManifestFile = outputDirectory.resolve(
            "manifest-community-macos-arm64-macos-app-archive.json");
        UpdateManifest armManifest = OBJECT_MAPPER.readValue(armManifestFile.toFile(), UpdateManifest.class);
        PublicKey publicKey = KeyFactory.getInstance("Ed25519")
            .generatePublic(new X509EncodedKeySpec(encodedPublicKey));
        UpdateEnvironment environment = new UpdateEnvironment(
            "5.3.3", 100L, "COMMUNITY", UpdateChannelEnum.STABLE,
            UpdatePlatformEnum.MACOS, UpdateArchitectureEnum.ARM64,
            UpdatePackageTypeEnum.MACOS_APP_ARCHIVE, 3
        );

        UpdateManifestVerifier.VerifiedUpdate verified = new UpdateManifestVerifier(
            Map.of("integration-key", publicKey)
        ).verify(armManifest, environment);

        assertEquals(armManifest, verified.manifest());
        Path generatedPackage = outputDirectory.resolve(
            "package-community-macos-arm64-macos-app-archive.tar.gz");
        Path install = temporaryDirectory.resolve("install");
        UpdateLayout layout = new UpdateLayout(
            install, install.resolve("app"),
            temporaryDirectory.resolve("cache"), temporaryDirectory.resolve("support"));
        Path staged = new FullPackageStager().stage(
            generatedPackage,
            armManifest,
            layout
        );
        assertEquals(VERSION, OBJECT_MAPPER.readTree(
            staged.resolve("Contents/app/version.json").toFile()).get("version").asText());
        assertTrue(Files.isRegularFile(staged.resolve("Contents/MacOS/Chat2DB Community")));

        Path index = outputDirectory.resolve("latest_version.json");
        Path previousIndex = outputDirectory.resolve("previous-latest-version.json");
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(
            previousIndex.toFile(),
            new ReleaseIndex(
                2, 100L, ReleaseStatusEnum.ACTIVE, UpdateChannelEnum.STABLE,
                List.of(new ReleaseReference(
                    "5.3.3", UpdatePlatformEnum.MACOS, UpdateArchitectureEnum.ARM64,
                    UpdatePackageTypeEnum.MACOS_APP_ARCHIVE,
                    "https://github.com/OtterMind/Chat2DB/releases/download/5.3.3/manifest.json"
                ))
            )
        );
        List<String> indexCommand = new ArrayList<>(List.of(
            "bash",
            projectRoot.resolve("script/package/generate_update_index_v2.sh").toString(),
            "STABLE",
            Long.toString(RELEASE_EPOCH),
            "https://github.com/OtterMind/Chat2DB/releases/download/" + VERSION,
            index.toString()
        ));
        manifests.stream().map(Path::toString).forEach(indexCommand::add);
        run(indexCommand, Map.of("CHAT2DB_PREVIOUS_UPDATE_INDEX", previousIndex.toString()));

        ReleaseIndex releaseIndex = OBJECT_MAPPER.readValue(index.toFile(), ReleaseIndex.class);
        assertEquals(10, releaseIndex.releases().size());
        assertTrue(releaseIndex.releases().stream().anyMatch(reference -> "5.3.3".equals(reference.version())));
        assertNotNull(releaseIndex.releases().stream()
            .filter(reference -> reference.platform() == UpdatePlatformEnum.LINUX)
            .filter(reference -> reference.arch() == UpdateArchitectureEnum.X64)
            .filter(reference -> reference.packageType() == UpdatePackageTypeEnum.LINUX_APPIMAGE)
            .findFirst()
            .orElse(null));

        UpdateManifest windowsManifest = OBJECT_MAPPER.readValue(
            outputDirectory.resolve("manifest-community-windows-x64-windows-exe.json").toFile(),
            UpdateManifest.class
        );
        assertEquals(UpdatePackageTypeEnum.WINDOWS_EXE, windowsManifest.packageType());
        assertEquals("5.3.401", windowsManifest.nativeVersion());
        assertEquals(3, windowsManifest.updaterProtocolVersion());
        assertEquals(3, windowsManifest.minUpdaterProtocolVersion());
        JsonNode windowsManifestJson = OBJECT_MAPPER.readTree(
            outputDirectory.resolve("manifest-community-windows-x64-windows-exe.json").toFile());
        assertFalse(windowsManifestJson.has("dataSchemaVersion"));
    }

    private Path createFullPackageArchive() throws Exception {
        Path source = temporaryDirectory.resolve("full-package-source");
        Path app = source.resolve("package/Contents/app");
        write(app, "tools/chat2db-bootstrap.jar", "bootstrap");
        write(app, "tools/chat2db-updater.jar", "updater");
        write(app, "runtime/launch.json", "{}");
        write(app, "runtime/chat2db-community.jar", "server");
        write(app, "runtime/lib/dependency.jar", "dependency");
        write(app, "runtime/dist/index.html", "html");
        Path launcher = source.resolve("package/Contents/MacOS/Chat2DB Community");
        write(source.resolve("package"), "Contents/MacOS/Chat2DB Community", "launcher");
        assertTrue(launcher.toFile().setExecutable(true, false));
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(
            app.resolve("version.json").toFile(),
            Map.of(
                "version", VERSION,
                "releaseEpoch", RELEASE_EPOCH,
                "buildSha", BUILD_SHA
            )
        );
        Path archive = temporaryDirectory.resolve("full-package.tar.gz");
        run(List.of("tar", "-czf", archive.toString(), "-C", source.toString(), "package"), Map.of());
        return archive;
    }

    private static String manifestName(Target target) {
        String packageType = target.packageType().name().toLowerCase().replace('_', '-');
        return "manifest-community-" + target.platform().name().toLowerCase() + "-"
            + target.architecture().name().toLowerCase() + "-" + packageType + ".json";
    }

    private static void write(Path root, String relativePath, String content) throws Exception {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static Path findProjectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("script/package/generate_update_v2.sh"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot find Community project root");
    }

    private static boolean commandExists(String command) throws Exception {
        Process process = new ProcessBuilder("bash", "-lc", "command -v " + command).start();
        return process.waitFor() == 0;
    }

    private static String opensslCommand() {
        for (String candidate : List.of(
            "/opt/homebrew/opt/openssl@3/bin/openssl",
            "/usr/local/opt/openssl@3/bin/openssl",
            "openssl"
        )) {
            if (!candidate.contains("/") || Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("OpenSSL is not available");
    }

    private static void run(List<String> command, Map<String, String> environment) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), () -> String.join(" ", command) + "\n" + output);
    }

    private static byte[] runBinary(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).start();
        byte[] output = process.getInputStream().readAllBytes();
        byte[] error = process.getErrorStream().readAllBytes();
        assertEquals(0, process.waitFor(), () -> new String(error, StandardCharsets.UTF_8));
        return output;
    }

    private record Target(
        UpdatePlatformEnum platform,
        UpdateArchitectureEnum architecture,
        UpdatePackageTypeEnum packageType,
        Path packageFile,
        String launcher
    ) {
    }
}

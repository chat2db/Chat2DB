package ai.chat2db.community.updater.v2.verification;

import ai.chat2db.community.updater.v2.enums.ReleaseStatusEnum;
import ai.chat2db.community.updater.v2.enums.UpdateArchitectureEnum;
import ai.chat2db.community.updater.v2.enums.UpdateChannelEnum;
import ai.chat2db.community.updater.v2.model.UpdateEnvironment;
import ai.chat2db.community.updater.v2.model.UpdateManifest;
import ai.chat2db.community.updater.v2.enums.UpdatePackageTypeEnum;
import ai.chat2db.community.updater.v2.enums.UpdatePlatformEnum;
import ai.chat2db.community.updater.v2.enums.UpdateScopeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateManifestVerifierTest {

    private KeyPair keyPair;
    private UpdateEnvironment environment;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        environment = environment("COMMUNITY", UpdateChannelEnum.STABLE,
            UpdateArchitectureEnum.ARM64, UpdatePackageTypeEnum.MACOS_APP_ARCHIVE);
    }

    @Test
    void acceptsMatchingSignedFullPackage() throws Exception {
        UpdateManifest manifest = signedManifest(baseManifest("5.3.4", 101));

        assertEquals(manifest, verifier().verify(manifest, environment).manifest());
    }

    @Test
    void acceptsNativeInstallerFromAnyLowerVersion() throws Exception {
        UpdateManifest manifest = signedManifest(nativeManifest());
        UpdateEnvironment windows = new UpdateEnvironment(
            "5.3.3", 100, "COMMUNITY", UpdateChannelEnum.STABLE,
            UpdatePlatformEnum.WINDOWS, UpdateArchitectureEnum.X64, UpdatePackageTypeEnum.WINDOWS_EXE, 3
        );

        assertEquals(manifest, verifier().verify(manifest, windows).manifest());

        UpdateEnvironment olderVersion = new UpdateEnvironment(
            "5.3.2", 99, "COMMUNITY", UpdateChannelEnum.STABLE,
            UpdatePlatformEnum.WINDOWS, UpdateArchitectureEnum.X64, UpdatePackageTypeEnum.WINDOWS_EXE, 3
        );
        assertEquals(manifest, verifier().verify(manifest, olderVersion).manifest());
    }

    @Test
    void rejectsTamperingAfterSigning() throws Exception {
        UpdateManifest signed = signedManifest(baseManifest("5.3.4", 101));

        assertThrows(ManifestVerificationException.class,
            () -> verifier().verify(copy(signed, "5.3.5", signed.signature()), environment));
    }

    @Test
    void rejectsWrongProductChannelArchitectureAndPackageType() throws Exception {
        UpdateManifest signed = signedManifest(baseManifest("5.3.4", 101));

        assertThrows(ManifestVerificationException.class, () -> verifier().verify(signed,
            environment("OTHER", UpdateChannelEnum.STABLE,
                UpdateArchitectureEnum.ARM64, UpdatePackageTypeEnum.MACOS_APP_ARCHIVE)));
        assertThrows(ManifestVerificationException.class, () -> verifier().verify(signed,
            environment("COMMUNITY", UpdateChannelEnum.BETA,
                UpdateArchitectureEnum.ARM64, UpdatePackageTypeEnum.MACOS_APP_ARCHIVE)));
        assertThrows(ManifestVerificationException.class, () -> verifier().verify(signed,
            environment("COMMUNITY", UpdateChannelEnum.STABLE,
                UpdateArchitectureEnum.X64, UpdatePackageTypeEnum.MACOS_APP_ARCHIVE)));
        assertThrows(ManifestVerificationException.class, () -> verifier().verify(signed,
            environment("COMMUNITY", UpdateChannelEnum.STABLE,
                UpdateArchitectureEnum.ARM64, UpdatePackageTypeEnum.LINUX_APPIMAGE)));
    }

    @Test
    void rejectsReplayPausedRevokedAndUnsafeLauncher() throws Exception {
        assertThrows(ManifestVerificationException.class, () -> verifier().verify(
            signedManifest(baseManifest("5.3.4", 100)), environment));
        assertThrows(ManifestVerificationException.class, () -> verifier().verify(
            signedManifest(withStatus(baseManifest("5.3.4", 101), ReleaseStatusEnum.PAUSED)), environment));
        assertThrows(ManifestVerificationException.class, () -> verifier().verify(
            signedManifest(withStatus(baseManifest("5.3.4", 101), ReleaseStatusEnum.REVOKED)), environment));

        UpdateManifest unsafe = withLauncher(baseManifest("5.3.4", 101), "../outside");
        assertThrows(ManifestVerificationException.class,
            () -> verifier().verify(signedManifest(unsafe), environment));
    }

    @Test
    void rejectsUnsupportedProtocol() throws Exception {
        UpdateManifest base = baseManifest("5.3.4", 101);
        UpdateManifest protocol = new UpdateManifest(
            base.schemaVersion(), base.releaseEpoch(), base.status(), base.product(), base.channel(), base.version(),
            base.nativeVersion(), base.buildSha(), base.platform(), base.arch(), base.updateScope(), base.packageType(), base.packageUrl(),
            base.packageSize(), base.packageSha256(), base.launcherRelativePath(), 4, 4,
            base.releaseNotesUrl(), base.keyId(), null
        );
        assertThrows(ManifestVerificationException.class,
            () -> verifier().verify(signedManifest(protocol), environment));

    }

    private UpdateManifestVerifier verifier() {
        return new UpdateManifestVerifier(Map.of("release-2026", keyPair.getPublic()));
    }

    private UpdateManifest signedManifest(UpdateManifest manifest) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(ManifestCanonicalizer.canonicalBytes(manifest));
        return withSignature(manifest, Base64.getEncoder().encodeToString(signer.sign()));
    }

    private static UpdateEnvironment environment(String product, UpdateChannelEnum channel,
            UpdateArchitectureEnum architecture, UpdatePackageTypeEnum packageType) {
        return new UpdateEnvironment(
            "5.3.3", 100, product, channel, UpdatePlatformEnum.MACOS, architecture,
            packageType, 3
        );
    }

    private static UpdateManifest baseManifest(String version, long epoch) {
        return new UpdateManifest(
            2, epoch, ReleaseStatusEnum.ACTIVE, "COMMUNITY", UpdateChannelEnum.STABLE,
            version, "5.3.401", "abc123", UpdatePlatformEnum.MACOS, UpdateArchitectureEnum.ARM64,
            UpdateScopeEnum.FULL_PACKAGE, UpdatePackageTypeEnum.MACOS_APP_ARCHIVE,
            "https://github.com/OtterMind/Chat2DB/releases/download/" + version + "/package.tar.gz",
            1024, "a".repeat(64), "Contents/MacOS/Chat2DB Community", 3, 3,
            "https://chat2db.ai/release-notes/" + version, "release-2026", null
        );
    }

    private static UpdateManifest nativeManifest() {
        return new UpdateManifest(
            2, 101, ReleaseStatusEnum.ACTIVE, "COMMUNITY", UpdateChannelEnum.STABLE,
            "5.3.4", "5.3.401", "abc123", UpdatePlatformEnum.WINDOWS, UpdateArchitectureEnum.X64,
            UpdateScopeEnum.FULL_PACKAGE, UpdatePackageTypeEnum.WINDOWS_EXE,
            "https://github.com/OtterMind/Chat2DB/releases/download/5.3.4/package.exe",
            2048, "a".repeat(64), "Chat2DB Community.exe", 3, 3,
            "https://chat2db.ai/release-notes/5.3.4", "release-2026", null
        );
    }

    private static UpdateManifest withStatus(UpdateManifest manifest, ReleaseStatusEnum status) {
        return rebuild(manifest, status, manifest.version(), manifest.launcherRelativePath(), manifest.signature());
    }

    private static UpdateManifest withLauncher(UpdateManifest manifest, String launcher) {
        return rebuild(manifest, manifest.status(), manifest.version(), launcher, manifest.signature());
    }

    private static UpdateManifest copy(UpdateManifest manifest, String version, String signature) {
        return rebuild(manifest, manifest.status(), version, manifest.launcherRelativePath(), signature);
    }

    private static UpdateManifest withSignature(UpdateManifest manifest, String signature) {
        return rebuild(manifest, manifest.status(), manifest.version(), manifest.launcherRelativePath(), signature);
    }

    private static UpdateManifest rebuild(UpdateManifest manifest, ReleaseStatusEnum status, String version,
            String launcher, String signature) {
        return new UpdateManifest(
            manifest.schemaVersion(), manifest.releaseEpoch(), status, manifest.product(), manifest.channel(),
            version, manifest.nativeVersion(), manifest.buildSha(), manifest.platform(), manifest.arch(), manifest.updateScope(),
            manifest.packageType(), manifest.packageUrl(), manifest.packageSize(), manifest.packageSha256(),
            launcher, manifest.updaterProtocolVersion(), manifest.minUpdaterProtocolVersion(),
            manifest.releaseNotesUrl(), manifest.keyId(), signature
        );
    }
}

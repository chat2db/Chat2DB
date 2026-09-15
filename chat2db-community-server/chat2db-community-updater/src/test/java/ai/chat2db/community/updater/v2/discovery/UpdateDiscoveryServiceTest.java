package ai.chat2db.community.updater.v2.discovery;

import ai.chat2db.community.updater.v2.verification.ManifestCanonicalizer;
import ai.chat2db.community.updater.v2.model.ReleaseIndex;
import ai.chat2db.community.updater.v2.model.ReleaseReference;
import ai.chat2db.community.updater.v2.enums.ReleaseStatusEnum;
import ai.chat2db.community.updater.v2.enums.UpdateArchitectureEnum;
import ai.chat2db.community.updater.v2.enums.UpdateChannelEnum;
import ai.chat2db.community.updater.v2.model.UpdateEnvironment;
import ai.chat2db.community.updater.v2.model.UpdateManifest;
import ai.chat2db.community.updater.v2.verification.UpdateManifestVerifier;
import ai.chat2db.community.updater.v2.enums.UpdatePackageTypeEnum;
import ai.chat2db.community.updater.v2.enums.UpdatePlatformEnum;
import ai.chat2db.community.updater.v2.enums.UpdateScopeEnum;
import ai.chat2db.community.updater.v2.transport.UpdateTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateDiscoveryServiceTest {

    private static final String BASE = "https://cdn.example.com/download/updates-v2/";

    private KeyPair keyPair;
    private StubTransport transport;
    private UpdateEnvironment environment;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        transport = new StubTransport();
        environment = new UpdateEnvironment(
            "5.3.3", 100, "COMMUNITY", UpdateChannelEnum.STABLE,
            UpdatePlatformEnum.LINUX, UpdateArchitectureEnum.ARM64,
            UpdatePackageTypeEnum.LINUX_APPIMAGE, 3
        );
    }

    @Test
    void stableUsersOnlyReceiveStableRelease() throws Exception {
        addRelease(UpdateChannelEnum.STABLE, "5.3.4", 101);
        addRelease(UpdateChannelEnum.BETA, "5.3.5-beta.1", 102);

        assertEquals("5.3.4", service().discover(environment, false).manifest().version());
    }

    @Test
    void betaUsersChooseHighestValidStableOrBetaSemver() throws Exception {
        addRelease(UpdateChannelEnum.STABLE, "5.3.4", 101);
        addRelease(UpdateChannelEnum.BETA, "5.3.5-beta.1", 102);

        assertEquals("5.3.5-beta.1", service().discover(environment, true).manifest().version());
    }

    @Test
    void betaChannelStillWorksWhenStablePointerIsUnavailable() throws Exception {
        transport.fail(BASE + "stable/latest_version.json");
        addRelease(UpdateChannelEnum.BETA, "5.3.4-beta.1", 101);

        assertEquals("5.3.4-beta.1", service().discover(environment, true).manifest().version());
    }

    @Test
    void selectsReferenceForCurrentInstallationPackageType() throws Exception {
        String version = "5.3.4";
        long epoch = 101;
        String channelPath = BASE + "stable/";
        String archiveManifest = channelPath + version + "/archive.json";
        String appImageManifest = channelPath + version + "/appimage.json";
        transport.put(channelPath + "latest_version.json", new ReleaseIndex(
            2, epoch, ReleaseStatusEnum.ACTIVE, UpdateChannelEnum.STABLE,
            List.of(
                new ReleaseReference(version, UpdatePlatformEnum.LINUX, UpdateArchitectureEnum.ARM64,
                    UpdatePackageTypeEnum.LINUX_DEB, archiveManifest),
                new ReleaseReference(version, UpdatePlatformEnum.LINUX, UpdateArchitectureEnum.ARM64,
                    UpdatePackageTypeEnum.LINUX_APPIMAGE, appImageManifest)
            )
        ));
        transport.put(appImageManifest, signedManifest(UpdateChannelEnum.STABLE, version, epoch));

        assertEquals(UpdatePackageTypeEnum.LINUX_APPIMAGE,
            service().discover(environment, false).manifest().packageType());
    }

    @Test
    void nativeInstallerSelectsHighestValidVersion() throws Exception {
        UpdateEnvironment windows = new UpdateEnvironment(
            "5.3.3", 100, "COMMUNITY", UpdateChannelEnum.STABLE,
            UpdatePlatformEnum.WINDOWS, UpdateArchitectureEnum.X64, UpdatePackageTypeEnum.WINDOWS_EXE, 3
        );
        String firstUrl = BASE + "stable/5.3.4/windows.json";
        String latestUrl = BASE + "stable/5.3.5/windows.json";
        transport.put(BASE + "stable/latest_version.json", new ReleaseIndex(
            2, 102, ReleaseStatusEnum.ACTIVE, UpdateChannelEnum.STABLE,
            List.of(
                new ReleaseReference("5.3.4", UpdatePlatformEnum.WINDOWS, UpdateArchitectureEnum.X64,
                    UpdatePackageTypeEnum.WINDOWS_EXE, firstUrl),
                new ReleaseReference("5.3.5", UpdatePlatformEnum.WINDOWS, UpdateArchitectureEnum.X64,
                    UpdatePackageTypeEnum.WINDOWS_EXE, latestUrl)
            )
        ));
        transport.put(firstUrl, signedNativeManifest("5.3.4", 101));
        transport.put(latestUrl, signedNativeManifest("5.3.5", 102));

        assertEquals("5.3.5", service().discover(windows, false).manifest().version());
    }

    private UpdateDiscoveryService service() {
        return new UpdateDiscoveryService(
            transport,
            new UpdateManifestVerifier(Map.of("release", keyPair.getPublic())),
            BASE
        );
    }

    private void addRelease(UpdateChannelEnum channel, String version, long epoch) throws Exception {
        String channelName = channel.name().toLowerCase();
        String manifestUrl = BASE + channelName + "/" + version + "/manifest.json";
        transport.put(
            BASE + channelName + "/latest_version.json",
            new ReleaseIndex(
                2, epoch, ReleaseStatusEnum.ACTIVE, channel,
                List.of(new ReleaseReference(version, UpdatePlatformEnum.LINUX, UpdateArchitectureEnum.ARM64,
                    UpdatePackageTypeEnum.LINUX_APPIMAGE, manifestUrl))
            )
        );
        transport.put(manifestUrl, signedManifest(channel, version, epoch));
    }

    private UpdateManifest signedManifest(UpdateChannelEnum channel, String version, long epoch) throws Exception {
        UpdateManifest unsigned = new UpdateManifest(
            2, epoch, ReleaseStatusEnum.ACTIVE, "COMMUNITY", channel, version, "5.3.401", "sha",
            UpdatePlatformEnum.LINUX, UpdateArchitectureEnum.ARM64, UpdateScopeEnum.FULL_PACKAGE,
            UpdatePackageTypeEnum.LINUX_APPIMAGE, "https://cdn.example.com/package.AppImage", 100,
            "a".repeat(64), ".", 3, 3,
            "https://example.com/notes", "release", null
        );
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(ManifestCanonicalizer.canonicalBytes(unsigned));
        return new UpdateManifest(
            unsigned.schemaVersion(), unsigned.releaseEpoch(), unsigned.status(), unsigned.product(),
            unsigned.channel(), unsigned.version(), unsigned.nativeVersion(), unsigned.buildSha(), unsigned.platform(), unsigned.arch(),
            unsigned.updateScope(), unsigned.packageType(), unsigned.packageUrl(), unsigned.packageSize(),
            unsigned.packageSha256(), unsigned.launcherRelativePath(), unsigned.updaterProtocolVersion(),
            unsigned.minUpdaterProtocolVersion(),
            unsigned.releaseNotesUrl(), unsigned.keyId(), Base64.getEncoder().encodeToString(signer.sign())
        );
    }

    private UpdateManifest signedNativeManifest(String version, long epoch) throws Exception {
        UpdateManifest unsigned = new UpdateManifest(
            2, epoch, ReleaseStatusEnum.ACTIVE, "COMMUNITY", UpdateChannelEnum.STABLE, version, "5.3.401", "sha",
            UpdatePlatformEnum.WINDOWS, UpdateArchitectureEnum.X64, UpdateScopeEnum.FULL_PACKAGE,
            UpdatePackageTypeEnum.WINDOWS_EXE, "https://cdn.example.com/" + version + "/package.exe", 100,
            "a".repeat(64), "Chat2DB Community.exe", 3, 3,
            "https://example.com/notes", "release", null
        );
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(ManifestCanonicalizer.canonicalBytes(unsigned));
        return new UpdateManifest(
            unsigned.schemaVersion(), unsigned.releaseEpoch(), unsigned.status(), unsigned.product(),
            unsigned.channel(), unsigned.version(), unsigned.nativeVersion(), unsigned.buildSha(), unsigned.platform(), unsigned.arch(),
            unsigned.updateScope(), unsigned.packageType(), unsigned.packageUrl(), unsigned.packageSize(),
            unsigned.packageSha256(), unsigned.launcherRelativePath(), unsigned.updaterProtocolVersion(),
            unsigned.minUpdaterProtocolVersion(),
            unsigned.releaseNotesUrl(), unsigned.keyId(), Base64.getEncoder().encodeToString(signer.sign())
        );
    }

    private static final class StubTransport implements UpdateTransport {
        private final Map<String, Object> values = new HashMap<>();
        private final Map<String, RuntimeException> failures = new HashMap<>();

        void put(String url, Object value) {
            values.put(url, value);
        }

        void fail(String url) {
            failures.put(url, new IllegalStateException("unavailable"));
        }

        @Override
        public <T> T getJson(String url, Class<T> type) {
            RuntimeException failure = failures.get(url);
            if (failure != null) {
                throw failure;
            }
            return type.cast(values.get(url));
        }

        @Override
        public Path download(String url, Path destination, long expectedSize, String expectedSha256,
                DownloadProgress listener) {
            throw new UnsupportedOperationException();
        }
    }
}

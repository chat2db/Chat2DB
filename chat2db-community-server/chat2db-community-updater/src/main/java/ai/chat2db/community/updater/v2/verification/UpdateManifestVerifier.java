package ai.chat2db.community.updater.v2.verification;

import ai.chat2db.community.updater.v2.enums.ReleaseStatusEnum;
import ai.chat2db.community.updater.v2.version.SemanticVersion;
import ai.chat2db.community.updater.v2.model.UpdateEnvironment;
import ai.chat2db.community.updater.v2.model.UpdateManifest;
import ai.chat2db.community.updater.v2.enums.UpdatePackageTypeEnum;
import ai.chat2db.community.updater.v2.enums.UpdateScopeEnum;
import java.net.URI;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class UpdateManifestVerifier {

    public static final int SCHEMA_VERSION = 2;
    private static final Pattern SHA_256 = Pattern.compile("[a-fA-F0-9]{64}");

    private final Map<String, PublicKey> trustedKeys;

    public UpdateManifestVerifier(Map<String, PublicKey> trustedKeys) {
        this.trustedKeys = Map.copyOf(trustedKeys);
    }

    public VerifiedUpdate verify(UpdateManifest manifest, UpdateEnvironment environment) {
        Objects.requireNonNull(manifest, "Update manifest is required");
        Objects.requireNonNull(environment, "Update environment is required");
        require(manifest.schemaVersion() == SCHEMA_VERSION, "Unsupported manifest schema version");
        require(manifest.status() == ReleaseStatusEnum.ACTIVE, "Release is not active: " + manifest.status());
        require(Objects.equals(manifest.product(), environment.product()), "Release product does not match this application");
        require(manifest.channel() == environment.channel(), "Release channel does not match requested channel");
        require(manifest.platform() == environment.platform(), "Release platform does not match this computer");
        require(manifest.arch() == environment.architecture(), "Release architecture does not match this computer");
        require(manifest.updateScope() == UpdateScopeEnum.FULL_PACKAGE, "Release is not a full-package update");
        require(manifest.packageType() == environment.packageType(),
            "Release package type does not match this installation");
        require(manifest.updaterProtocolVersion() >= manifest.minUpdaterProtocolVersion(),
            "Manifest updater protocol range is invalid");
        require(environment.updaterProtocolVersion() >= manifest.minUpdaterProtocolVersion(),
            "Installed updater protocol is too old");
        require(manifest.updaterProtocolVersion() <= environment.updaterProtocolVersion(),
            "Release requires an unsupported updater protocol");
        require(manifest.releaseEpoch() > environment.installedReleaseEpoch(),
            "Release epoch does not advance the installed release");
        require(manifest.packageSize() > 0, "Package size must be positive");
        requireSha256(manifest.packageSha256(), "Package SHA-256 is invalid");
        requireHttpsUrl(manifest.packageUrl(), "Package URL must use HTTPS");
        requireHttpsUrl(manifest.releaseNotesUrl(), "Release notes URL must use HTTPS");
        require(notBlank(manifest.buildSha()), "Build SHA is required");
        require(manifest.nativeVersion() != null
                && manifest.nativeVersion().matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"),
            "Native package version must be numeric major.minor.build");
        requireSafeLauncherPath(manifest);

        SemanticVersion current = parseVersion(environment.currentVersion(), "Installed version is invalid");
        SemanticVersion target = parseVersion(manifest.version(), "Release version is invalid");
        require(target.compareTo(current) > 0, "Release version does not advance the installed version");
        verifySignature(manifest);
        return new VerifiedUpdate(manifest);
    }

    private void verifySignature(UpdateManifest manifest) {
        require(notBlank(manifest.keyId()), "Manifest signing key id is required");
        PublicKey publicKey = trustedKeys.get(manifest.keyId());
        require(publicKey != null, "Manifest signing key is not trusted");
        require(notBlank(manifest.signature()), "Manifest signature is required");
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(ManifestCanonicalizer.canonicalBytes(manifest));
            byte[] signature = Base64.getDecoder().decode(manifest.signature());
            require(verifier.verify(signature), "Manifest signature is invalid");
        } catch (ManifestVerificationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ManifestVerificationException("Cannot verify manifest signature", exception);
        }
    }

    private static SemanticVersion parseVersion(String version, String message) {
        try {
            return SemanticVersion.parse(version);
        } catch (IllegalArgumentException exception) {
            throw new ManifestVerificationException(message, exception);
        }
    }

    private static void requireHttpsUrl(String value, String message) {
        try {
            URI uri = URI.create(value);
            require("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null, message);
        } catch (RuntimeException exception) {
            if (exception instanceof ManifestVerificationException verificationException) {
                throw verificationException;
            }
            throw new ManifestVerificationException(message, exception);
        }
    }

    private static void requireSha256(String value, String message) {
        require(value != null && SHA_256.matcher(value).matches(), message);
    }

    private static void requireSafeLauncherPath(UpdateManifest manifest) {
        String value = manifest.launcherRelativePath();
        if (manifest.packageType() == UpdatePackageTypeEnum.LINUX_APPIMAGE) {
            require(".".equals(value), "Single-file package launcher must be the package itself");
            return;
        }
        require(notBlank(value), "Candidate launcher path is required");
        try {
            Path path = Path.of(value).normalize();
            require(!path.isAbsolute() && !path.startsWith("..") && !".".equals(path.toString()),
                "Candidate launcher path must stay inside the full package");
        } catch (RuntimeException exception) {
            if (exception instanceof ManifestVerificationException verificationException) {
                throw verificationException;
            }
            throw new ManifestVerificationException("Candidate launcher path is invalid", exception);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new ManifestVerificationException(message);
        }
    }

    public record VerifiedUpdate(UpdateManifest manifest) {
    }
}

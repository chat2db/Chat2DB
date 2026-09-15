package ai.chat2db.community.updater.v2.discovery;

import ai.chat2db.community.updater.v2.verification.ManifestVerificationException;
import ai.chat2db.community.updater.v2.audit.UpdateAuditLog;
import ai.chat2db.community.updater.v2.model.ReleaseIndex;
import ai.chat2db.community.updater.v2.model.ReleaseReference;
import ai.chat2db.community.updater.v2.enums.ReleaseStatusEnum;
import ai.chat2db.community.updater.v2.version.SemanticVersion;
import ai.chat2db.community.updater.v2.enums.UpdateChannelEnum;
import ai.chat2db.community.updater.v2.model.UpdateEnvironment;
import ai.chat2db.community.updater.v2.model.UpdateManifest;
import ai.chat2db.community.updater.v2.verification.UpdateManifestVerifier;
import ai.chat2db.community.updater.v2.transport.UpdateTransport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class UpdateDiscoveryService {

    private static final int INDEX_SCHEMA_VERSION = 2;

    private final UpdateTransport transport;
    private final UpdateManifestVerifier verifier;
    private final java.util.function.Function<UpdateChannelEnum, String> indexUrl;

    public UpdateDiscoveryService(UpdateTransport transport, UpdateManifestVerifier verifier, String channelBaseUrl) {
        this(transport, verifier, channel -> (channelBaseUrl.endsWith("/") ? channelBaseUrl : channelBaseUrl + "/")
            + channel.name().toLowerCase() + "/latest_version.json");
    }

    public UpdateDiscoveryService(UpdateTransport transport, UpdateManifestVerifier verifier,
            java.util.function.Function<UpdateChannelEnum, String> indexUrl) {
        this.transport = transport;
        this.verifier = verifier;
        this.indexUrl = indexUrl;
    }

    public PendingUpdate discover(UpdateEnvironment baseEnvironment, boolean receiveBeta) {
        return discover(baseEnvironment, receiveBeta, null);
    }

    public PendingUpdate discover(UpdateEnvironment baseEnvironment, boolean receiveBeta, UpdateAuditLog audit) {
        List<PendingUpdate> candidates = new ArrayList<>();
        List<RuntimeException> failures = new ArrayList<>();
        discoverChannelSafely(baseEnvironment, UpdateChannelEnum.STABLE, candidates, failures, audit);
        if (receiveBeta) {
            discoverChannelSafely(baseEnvironment, UpdateChannelEnum.BETA, candidates, failures, audit);
        }
        if (candidates.isEmpty() && !failures.isEmpty()) {
            RuntimeException failure = failures.get(0);
            failures.stream().skip(1).forEach(failure::addSuppressed);
            throw failure;
        }
        return candidates.stream()
            .max(Comparator.comparing((PendingUpdate update) -> SemanticVersion.parse(update.manifest().version()))
                .thenComparing(update -> update.manifest().releaseEpoch()))
            .orElse(null);
    }

    private void discoverChannelSafely(UpdateEnvironment environment, UpdateChannelEnum channel,
            List<PendingUpdate> candidates, List<RuntimeException> failures, UpdateAuditLog audit) {
        try {
            discoverChannel(environment, channel, candidates, audit);
        } catch (RuntimeException failure) {
            failures.add(failure);
            if (audit != null) {
                audit.error("DISCOVERY_" + channel, "CHANNEL_FAILED", failure);
            }
        }
    }

    private void discoverChannel(UpdateEnvironment baseEnvironment, UpdateChannelEnum channel,
            List<PendingUpdate> candidates, UpdateAuditLog audit) {
        String indexUrl = this.indexUrl.apply(channel);
        if (audit != null) {
            audit.info("DISCOVERY_" + channel, "INDEX_REQUEST",
                "url=" + UpdateAuditLog.auditUrl(indexUrl));
        }
        ReleaseIndex index = transport.getJson(
            indexUrl,
            ReleaseIndex.class
        );
        if (audit != null) {
            audit.info("DISCOVERY_" + channel, "INDEX_RESPONSE",
                index == null
                    ? "index=null"
                    : "status=" + index.status() + " epoch=" + index.releaseEpoch()
                        + " releases=" + (index.releases() == null ? 0 : index.releases().size()));
        }
        if (index == null || index.schemaVersion() != INDEX_SCHEMA_VERSION || index.status() != ReleaseStatusEnum.ACTIVE
                || index.channel() != channel || index.releaseEpoch() <= baseEnvironment.installedReleaseEpoch()) {
            if (audit != null) {
                audit.info("DISCOVERY_" + channel, "INDEX_SKIPPED",
                    "index is absent, inactive, incompatible, or not newer");
            }
            return;
        }
        List<ReleaseReference> references = index.releases() == null ? List.of() : index.releases().stream()
            .filter(item -> item.platform() == baseEnvironment.platform())
            .filter(item -> item.arch() == baseEnvironment.architecture())
            .filter(item -> item.packageType() == baseEnvironment.packageType())
            .filter(item -> SemanticVersion.parse(item.version())
                .compareTo(SemanticVersion.parse(baseEnvironment.currentVersion())) > 0)
            .sorted(Comparator.comparing(item -> SemanticVersion.parse(item.version())))
            .toList();
        if (references.isEmpty()) {
            if (audit != null) {
                audit.info("DISCOVERY_" + channel, "NO_MATCHING_TARGET",
                    "no newer reference for current platform, architecture, and package type");
            }
            return;
        }
        UpdateEnvironment channelEnvironment = new UpdateEnvironment(
            baseEnvironment.currentVersion(),
            baseEnvironment.installedReleaseEpoch(),
            baseEnvironment.product(),
            channel,
            baseEnvironment.platform(),
            baseEnvironment.architecture(),
            baseEnvironment.packageType(),
            baseEnvironment.updaterProtocolVersion()
        );
        List<RuntimeException> verificationFailures = new ArrayList<>();
        int acceptedBefore = candidates.size();
        for (ReleaseReference reference : references) {
            try {
                if (audit != null) {
                    audit.info("DISCOVERY_" + channel, "MANIFEST_REQUEST",
                        "version=" + reference.version()
                            + " url=" + UpdateAuditLog.auditUrl(reference.manifestUrl()));
                }
                UpdateManifest manifest = transport.getJson(reference.manifestUrl(), UpdateManifest.class);
                if (manifest.status() != ReleaseStatusEnum.ACTIVE) {
                    continue;
                }
                if (!reference.version().equals(manifest.version())
                        || reference.packageType() != manifest.packageType()
                        || reference.platform() != manifest.platform()
                        || reference.arch() != manifest.arch()) {
                    throw new ManifestVerificationException(
                        "Release index and manifest target do not match");
                }
                UpdateManifestVerifier.VerifiedUpdate verified = verifier.verify(manifest, channelEnvironment);
                candidates.add(new PendingUpdate(verified.manifest()));
                if (audit != null) {
                    audit.info("DISCOVERY_" + channel, "MANIFEST_ACCEPTED",
                        "version=" + manifest.version() + " epoch=" + manifest.releaseEpoch()
                            + " sha256=" + manifest.packageSha256());
                }
            } catch (RuntimeException failure) {
                verificationFailures.add(failure);
                if (audit != null) {
                    audit.error("DISCOVERY_" + channel, "MANIFEST_REJECTED", failure);
                }
            }
        }
        if (candidates.size() == acceptedBefore && !verificationFailures.isEmpty()) {
            RuntimeException failure = verificationFailures.get(0);
            verificationFailures.stream().skip(1).forEach(failure::addSuppressed);
            throw failure;
        }
    }

    public record PendingUpdate(UpdateManifest manifest) {
    }
}

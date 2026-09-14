package ai.chat2db.community.updater.v2.model;

import ai.chat2db.community.updater.v2.enums.ReleaseStatusEnum;
import ai.chat2db.community.updater.v2.enums.UpdateArchitectureEnum;
import ai.chat2db.community.updater.v2.enums.UpdateChannelEnum;
import ai.chat2db.community.updater.v2.enums.UpdatePackageTypeEnum;
import ai.chat2db.community.updater.v2.enums.UpdatePlatformEnum;
import ai.chat2db.community.updater.v2.enums.UpdateScopeEnum;
public record UpdateManifest(
    int schemaVersion,
    long releaseEpoch,
    ReleaseStatusEnum status,
    String product,
    UpdateChannelEnum channel,
    String version,
    String nativeVersion,
    String buildSha,
    UpdatePlatformEnum platform,
    UpdateArchitectureEnum arch,
    UpdateScopeEnum updateScope,
    UpdatePackageTypeEnum packageType,
    String packageUrl,
    long packageSize,
    String packageSha256,
    String launcherRelativePath,
    int updaterProtocolVersion,
    int minUpdaterProtocolVersion,
    String releaseNotesUrl,
    String keyId,
    String signature
) {
}

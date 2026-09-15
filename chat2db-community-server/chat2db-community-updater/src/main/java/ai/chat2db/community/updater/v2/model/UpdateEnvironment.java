package ai.chat2db.community.updater.v2.model;

import ai.chat2db.community.updater.v2.enums.UpdateArchitectureEnum;
import ai.chat2db.community.updater.v2.enums.UpdateChannelEnum;
import ai.chat2db.community.updater.v2.enums.UpdatePackageTypeEnum;
import ai.chat2db.community.updater.v2.enums.UpdatePlatformEnum;
public record UpdateEnvironment(
    String currentVersion,
    long installedReleaseEpoch,
    String product,
    UpdateChannelEnum channel,
    UpdatePlatformEnum platform,
    UpdateArchitectureEnum architecture,
    UpdatePackageTypeEnum packageType,
    int updaterProtocolVersion
) {
}

package ai.chat2db.community.updater.v2.model;

import ai.chat2db.community.updater.v2.enums.UpdateArchitectureEnum;
import ai.chat2db.community.updater.v2.enums.UpdatePackageTypeEnum;
import ai.chat2db.community.updater.v2.enums.UpdatePlatformEnum;
public record ReleaseReference(
    String version,
    UpdatePlatformEnum platform,
    UpdateArchitectureEnum arch,
    UpdatePackageTypeEnum packageType,
    String manifestUrl
) {
}

package ai.chat2db.community.updater.v2.model;

import ai.chat2db.community.updater.v2.enums.ReleaseStatusEnum;
import ai.chat2db.community.updater.v2.enums.UpdateChannelEnum;
import java.util.List;

public record ReleaseIndex(
    int schemaVersion,
    long releaseEpoch,
    ReleaseStatusEnum status,
    UpdateChannelEnum channel,
    List<ReleaseReference> releases
) {
}

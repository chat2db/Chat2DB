package ai.chat2db.community.updater.v2.model;

public record InstalledAppVersion(
    String version,
    long releaseEpoch,
    String buildSha
) {
}

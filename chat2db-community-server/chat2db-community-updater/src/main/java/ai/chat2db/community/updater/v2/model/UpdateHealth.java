package ai.chat2db.community.updater.v2.model;

public record UpdateHealth(
    String transactionId,
    String version,
    String status,
    long processId,
    long timestampEpochMillis
) {
    public static final String TRIAL_HEALTHY = "TRIAL_HEALTHY";
    public static final String NORMAL_HEALTHY = "NORMAL_HEALTHY";
}

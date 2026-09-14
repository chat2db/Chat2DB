package ai.chat2db.community.updater.v2.model;

import ai.chat2db.community.updater.v2.enums.UpdatePackageTypeEnum;
import java.util.List;

public record UpdateHelperPlan(
    UpdateTransaction transaction,
    String installRoot,
    String product,
    String cacheRoot,
    String supportRoot,
    long oldProcessId,
    UpdatePackageTypeEnum packageType,
    String candidateLauncherRelativePath,
    List<String> candidateLaunchArguments,
    List<String> candidateLaunchCommand,
    int healthTimeoutSeconds
) {

    public String transactionId() {
        return transaction.transactionId();
    }
}

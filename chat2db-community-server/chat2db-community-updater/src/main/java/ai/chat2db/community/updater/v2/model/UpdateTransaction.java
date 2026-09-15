package ai.chat2db.community.updater.v2.model;

import ai.chat2db.community.updater.v2.enums.UpdatePhaseEnum;
import ai.chat2db.community.updater.v2.state.UpdateStateMachine;
public record UpdateTransaction(
    String transactionId,
    String fromVersion,
    String toVersion,
    long releaseEpoch,
    String targetPackageSha256,
    UpdatePhaseEnum phase,
    long createdAtEpochMillis,
    long updatedAtEpochMillis,
    String failureMessage
) {

    public static UpdateTransaction create(String transactionId, String fromVersion, UpdateManifest manifest, long now) {
        return new UpdateTransaction(
            transactionId,
            fromVersion,
            manifest.version(),
            manifest.releaseEpoch(),
            manifest.packageSha256(),
            UpdatePhaseEnum.DISCOVERED,
            now,
            now,
            null
        );
    }

    public UpdateTransaction transition(UpdatePhaseEnum next, long now) {
        UpdateStateMachine.requireTransition(phase, next);
        return new UpdateTransaction(
            transactionId,
            fromVersion,
            toVersion,
            releaseEpoch,
            targetPackageSha256,
            next,
            createdAtEpochMillis,
            now,
            failureMessage
        );
    }

    public UpdateTransaction fail(String message, long now) {
        UpdateStateMachine.requireTransition(phase, UpdatePhaseEnum.FAILED);
        return new UpdateTransaction(
            transactionId,
            fromVersion,
            toVersion,
            releaseEpoch,
            targetPackageSha256,
            UpdatePhaseEnum.FAILED,
            createdAtEpochMillis,
            now,
            message
        );
    }

}

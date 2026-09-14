package ai.chat2db.community.domain.api.model.task;

import org.apache.commons.lang3.StringUtils;

/**
 * CSV import execution mode. ULTRA_FAST uses parallel row batches with adaptive tuning;
 * absent or unknown values resolve to STANDARD.
 */
public final class TaskExecutionMode {

    public static final String ULTRA_FAST = "ULTRA_FAST";

    public static final String STANDARD = "STANDARD";

    private TaskExecutionMode() {
    }

    /** True only for an explicit {@code ULTRA_FAST}; anything else (null, blank, unknown) is standard. */
    public static boolean isUltraFast(String mode) {
        return ULTRA_FAST.equalsIgnoreCase(StringUtils.trimToEmpty(mode));
    }
}

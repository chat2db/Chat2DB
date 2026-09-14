package ai.chat2db.community.updater.v2.enums;

public enum UpdatePhaseEnum {
    DISCOVERED,
    DOWNLOADING,
    VERIFIED,
    PRECHECKING,
    PRECHECKED,
    QUIESCING,
    READY_TO_SWITCH,
    SWITCHING,
    STARTING_CANDIDATE,
    POSTCHECKING,
    RESTARTING_NORMAL,
    COMMITTED,
    FAILED
}

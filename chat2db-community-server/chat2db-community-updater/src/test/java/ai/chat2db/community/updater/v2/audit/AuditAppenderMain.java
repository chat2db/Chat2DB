package ai.chat2db.community.updater.v2.audit;

import ai.chat2db.community.updater.v2.installation.UpdateLayout;

import java.nio.file.Path;

public final class AuditAppenderMain {

    private AuditAppenderMain() {
    }

    public static void main(String[] args) {
        UpdateLayout layout = new UpdateLayout(
            Path.of(args[0]),
            Path.of(args[1]),
            Path.of(args[2]),
            Path.of(args[3])
        );
        UpdateAuditLog audit = UpdateAuditLog.open(layout, args[4], args[5]);
        int count = Integer.parseInt(args[6]);
        for (int index = 0; index < count; index++) {
            audit.info("CONCURRENT", "APPEND", "index=" + index);
        }
    }
}

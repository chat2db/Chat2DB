package ai.chat2db.community.updater.v2.model;

public record UpdatePreferences(boolean receiveBeta) {

    public static UpdatePreferences defaults() {
        return new UpdatePreferences(false);
    }
}

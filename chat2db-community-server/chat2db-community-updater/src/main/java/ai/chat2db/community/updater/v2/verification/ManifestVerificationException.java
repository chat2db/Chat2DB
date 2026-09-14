package ai.chat2db.community.updater.v2.verification;

public class ManifestVerificationException extends RuntimeException {

    public ManifestVerificationException(String message) {
        super(message);
    }

    public ManifestVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

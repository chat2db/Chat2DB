package ai.chat2db.community.updater.v2.audit;

import ai.chat2db.community.updater.v2.enums.UpdatePhaseEnum;
import ai.chat2db.community.updater.v2.installation.UpdateLayout;
import ai.chat2db.community.updater.v2.model.UpdateTransaction;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateAuditLog {

    public static final String STATUS_CHECKING = "CHECKING";
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_NO_UPDATE = "NO_UPDATE";
    public static final String STATUS_CHECK_FAILED = "CHECK_FAILED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    private static final Pattern URL = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final ConcurrentHashMap<Path, Object> JVM_FILE_LOCKS = new ConcurrentHashMap<>();

    private final UpdateLayout layout;
    private final String operationId;
    private final String actor;
    private final Path logFile;

    private String fromVersion = "";
    private String toVersion = "";

    public static UpdateAuditLog begin(UpdateLayout layout, String operationId, String actor) {
        UpdateAuditLog audit = open(layout, operationId, actor);
        audit.append("INFO", "AUDIT", "BEGIN", "STARTED", "update operation started", true);
        audit.status(STATUS_CHECKING, "AUDIT", "");
        return audit;
    }

    public static UpdateAuditLog open(UpdateLayout layout, String operationId, String actor) {
        UpdateAuditLog audit = new UpdateAuditLog(layout, operationId, actor);
        audit.ensureDirectory();
        return audit;
    }

    private UpdateAuditLog(UpdateLayout layout, String operationId, String actor) {
        this.layout = layout;
        this.operationId = operationId;
        this.actor = actor == null || actor.isBlank() ? "UNKNOWN" : actor;
        this.logFile = layout.auditLogFile(operationId);
    }

    public String operationId() {
        return operationId;
    }

    public void versions(String fromVersion, String toVersion) {
        this.fromVersion = safe(fromVersion);
        this.toVersion = safe(toVersion);
        append("INFO", "RELEASE", "VERSIONS", "OBSERVED",
            "from=" + this.fromVersion + " to=" + this.toVersion, true);
    }

    /** Records a transaction snapshot for diagnostics only. Runtime coordination
     * uses the helper plan and transaction-scoped health marker instead. */
    public void state(UpdateTransaction transaction) {
        append("INFO", "STATE", "SNAPSHOT", "COMMITTED",
            "transactionId=" + token(transaction.transactionId())
                + " fromVersion=" + token(transaction.fromVersion())
                + " toVersion=" + token(transaction.toVersion())
                + " releaseEpoch=" + transaction.releaseEpoch()
                + " targetPackageSha256=" + token(transaction.targetPackageSha256())
                + " phase=" + token(transaction.phase().name())
                + " createdAtEpochMillis=" + transaction.createdAtEpochMillis()
                + " updatedAtEpochMillis=" + transaction.updatedAtEpochMillis()
                + " failureMessage=" + token(transaction.failureMessage())
                + " failureMessageB64=" + encodeStateValue(transaction.failureMessage()), true);
    }

    private static String encodeStateValue(String value) {
        return value == null || value.isBlank()
            ? ""
            : Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public void info(String stage, String event, String message) {
        append("INFO", stage, event, "OBSERVED", message, false);
    }

    public void critical(String stage, String event, String message) {
        append("INFO", stage, event, "PERSISTED", message, true);
    }

    public void warn(String stage, String event, String message) {
        append("WARN", stage, event, "WARNING", message, true);
    }

    public void error(String stage, String event, Throwable failure) {
        String message = failure == null ? "unknown failure" : failure.getClass().getName() + ": " + failure.getMessage();
        append("ERROR", stage, event, "FAILED", message, true);
        if (failure != null) {
            StringWriter stack = new StringWriter();
            failure.printStackTrace(new PrintWriter(stack));
            for (String line : stack.toString().split("\\R")) {
                append("ERROR", stage, "STACK", "FAILED", line, false);
            }
        }
    }

    public void phase(UpdatePhaseEnum from, UpdatePhaseEnum to, long elapsedMillis, String outcome) {
        append("INFO", to.name(), "PHASE", outcome,
            "from=" + from + " to=" + to + " previousPhaseMillis=" + Math.max(0L, elapsedMillis), true);
    }

    public void status(String status, String stage, String reason) {
        append("INFO", stage, "RESULT", status, "reason=" + safe(reason), true);
    }

    public static String auditUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            java.net.URI uri = java.net.URI.create(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "redacted-url";
            }
            return new java.net.URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
        } catch (Exception ignored) {
            return "redacted-url";
        }
    }

    private void append(String level, String stage, String event, String outcome, String message, boolean force) {
        ensureDirectory();
        String line = Instant.now()
            + " level=" + token(level)
            + " operation=" + token(operationId)
            + " actor=" + token(actor)
            + " pid=" + ProcessHandle.current().pid()
            + " stage=" + token(stage)
            + " event=" + token(event)
            + " outcome=" + token(outcome)
            + " eventId=" + UUID.randomUUID()
            + " message=\"" + escape(message) + "\""
            + System.lineSeparator();
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        Object jvmLock = JVM_FILE_LOCKS.computeIfAbsent(logFile.toAbsolutePath().normalize(), ignored -> new Object());
        synchronized (jvmLock) {
            try (FileChannel channel = FileChannel.open(logFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                 FileLock ignored = channel.lock()) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                if (force) {
                    channel.force(false);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot append update audit log: " + logFile, exception);
            }
        }
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(logFile.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create update audit directories", exception);
        }
    }

    private static String escape(String value) {
        String sanitized = safe(value);
        Matcher matcher = URL.matcher(sanitized);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(auditUrl(matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        String home = System.getProperty("user.home", "");
        String result = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return home.isBlank() ? result : result.replace(home, "$HOME");
    }

    private static String token(String value) {
        return safe(value).replaceAll("[^0-9A-Za-z._:-]", "_");
    }

}

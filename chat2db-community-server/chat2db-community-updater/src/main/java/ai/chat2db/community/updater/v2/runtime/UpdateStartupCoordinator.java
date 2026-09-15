package ai.chat2db.community.updater.v2.runtime;

import ai.chat2db.community.updater.v2.model.UpdateHealth;
import ai.chat2db.community.updater.v2.audit.UpdateAuditLog;
import ai.chat2db.community.updater.v2.installation.UpdateLayout;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.function.BooleanSupplier;

public final class UpdateStartupCoordinator {

    public static final String TRANSACTION_ENV = "CHAT2DB_UPDATE_TRANSACTION";
    public static final String NORMAL_TRANSACTION_ENV = "CHAT2DB_UPDATE_NORMAL_TRANSACTION";
    public static final String TARGET_VERSION_ENV = "CHAT2DB_UPDATE_TARGET_VERSION";
    public static final String INSTALL_TARGET_ENV = "CHAT2DB_INSTALL_TARGET";
    private static String product = "COMMUNITY";

    public static void configureProduct(String productId) {
        product = java.util.Objects.requireNonNull(productId);
    }

    private static final Duration READY_TIMEOUT = Duration.ofSeconds(90);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private UpdateStartupCoordinator() {
    }

    public static boolean prepareTrialMode() {
        if (!isTrialStartup()) {
            return false;
        }
        System.setProperty("chat2db.update.trial", "true");
        System.setProperty("spring.task.scheduling.enabled", "false");
        System.setProperty("spring.ai.mcp.server.enabled", "false");
        return true;
    }

    public static boolean isTrialStartup() {
        String transactionId = System.getenv(TRANSACTION_ENV);
        return transactionId != null && !transactionId.isBlank();
    }

    public static void reportReadyWhen(BooleanSupplier ready) {
        if (!isUpdateStartup()) {
            return;
        }
        String transactionId = startupTransactionId();
        String version = requiredEnvironment(TARGET_VERSION_ENV);
        String status = isTrialStartup() ? UpdateHealth.TRIAL_HEALTHY : UpdateHealth.NORMAL_HEALTHY;
        UpdateLayout layout = installLayout();
        Path healthFile = healthFile(layout, transactionId);
        try {
            waitUntilReady(ready);
            writeHealthMarker(healthFile, new UpdateHealth(
                transactionId,
                version,
                status,
                ProcessHandle.current().pid(),
                System.currentTimeMillis()
            ));
            try {
                UpdateAuditLog.open(layout, transactionId, startupActor()).critical(
                    startupStage(),
                    "HEALTH_MARKER_WRITTEN",
                    "health=" + status + " version=" + version
                );
            } catch (RuntimeException ignored) {
                // Health coordination must not depend on diagnostic logging.
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Updated application did not become ready", exception);
        }
    }

    public static void reportStartupFailure(Throwable failure) {
        if (!isUpdateStartup()) {
            return;
        }
        try {
            String transactionId = startupTransactionId();
            UpdateLayout layout = installLayout();
            reportStartupFailure(layout, transactionId, startupActor(), startupStage(), failure);
        } catch (RuntimeException ignored) {
            // The original startup failure remains primary when audit recovery is also unavailable.
        }
    }

    static void reportStartupFailure(UpdateLayout layout, String transactionId, Throwable failure) {
        reportStartupFailure(layout, transactionId, "CANDIDATE", "STARTING_CANDIDATE", failure);
    }

    private static void reportStartupFailure(UpdateLayout layout, String transactionId,
            String actor, String stage, Throwable failure) {
        UpdateAuditLog audit = UpdateAuditLog.open(layout, transactionId, actor);
        audit.error(stage, "PROCESS_START_FAILED", failure);
    }

    public static Path healthFile(UpdateLayout layout, String transactionId) {
        return layout.healthFile(transactionId);
    }

    static void writeHealthMarker(Path healthFile, UpdateHealth health) throws IOException {
        Files.createDirectories(healthFile.getParent());
        Path temporary = healthFile.resolveSibling(healthFile.getFileName() + ".tmp");
        try {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), health);
            try {
                Files.move(temporary, healthFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, healthFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void waitUntilReady(BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (ready.getAsBoolean()) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new IllegalStateException("Timed out waiting for frontend readiness");
    }

    private static UpdateLayout installLayout() {
        String installRoot = resolveInstallRoot(
            System.getProperty("chat2db.install.root"),
            System.getenv(INSTALL_TARGET_ENV)
        );
        if (installRoot == null || installRoot.isBlank()) {
            throw new IllegalStateException("Update trial startup is missing chat2db.install.root");
        }
        return new UpdateLayout(Path.of(installRoot), currentProduct());
    }

    static String resolveInstallRoot(String propertyValue, String environmentValue) {
        return propertyValue == null || propertyValue.isBlank() ? environmentValue : propertyValue;
    }

    private static String currentProduct() {
        return product;
    }

    private static boolean isUpdateStartup() {
        return isTrialStartup() || isNormalStartup();
    }

    private static boolean isNormalStartup() {
        String transactionId = System.getenv(NORMAL_TRANSACTION_ENV);
        return transactionId != null && !transactionId.isBlank();
    }

    private static String startupTransactionId() {
        return isTrialStartup()
            ? requiredEnvironment(TRANSACTION_ENV)
            : requiredEnvironment(NORMAL_TRANSACTION_ENV);
    }

    private static String startupActor() {
        return isTrialStartup() ? "CANDIDATE" : "NORMAL_APPLICATION";
    }

    private static String startupStage() {
        return isTrialStartup() ? "STARTING_CANDIDATE" : "RESTARTING_NORMAL";
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Update startup is missing " + name);
        }
        return value;
    }
}

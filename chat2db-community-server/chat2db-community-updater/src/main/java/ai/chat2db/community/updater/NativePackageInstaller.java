package ai.chat2db.community.updater;

import ai.chat2db.community.updater.v2.audit.UpdateAuditLog;
import ai.chat2db.community.updater.v2.enums.UpdatePackageTypeEnum;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.ShellAPI.SHELLEXECUTEINFO;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class NativePackageInstaller {

    private static final Duration INSTALL_TIMEOUT = Duration.ofMinutes(15);
    private static final int ERROR_CANCELLED = 1223;
    private static final int WAIT_TIMEOUT = 0x00000102;
    private static final int MSI_INSTALL_CANCELLED = 1602;
    private static final int MSI_INSTALL_BLOCKED_BY_POLICY = 1625;

    private final List<WindowsInstallerLauncher> windowsLaunchers;

    NativePackageInstaller() {
        this(defaultWindowsLaunchers());
    }

    NativePackageInstaller(List<WindowsInstallerLauncher> windowsLaunchers) {
        this.windowsLaunchers = List.copyOf(windowsLaunchers);
    }

    void install(UpdatePackageTypeEnum packageType, Path packageFile, Path installRoot,
            String launcherRelativePath, UpdateAuditLog audit) throws Exception {
        if (!packageType.nativeInstaller()) {
            throw new IllegalArgumentException("Package type is not a native installer: " + packageType);
        }
        if (!Files.isRegularFile(packageFile)) {
            throw new IllegalStateException("Native update package is missing: " + packageFile);
        }
        if (packageType == UpdatePackageTypeEnum.WINDOWS_EXE) {
            installWindows(packageFile, installRoot, launcherRelativePath, audit);
            return;
        }
        int exitCode = runProcess(packageType, commandFor(packageType, packageFile), "PROCESS", audit);
        requireSuccessfulExitCode(packageType, exitCode);
        audit.critical("NATIVE_INSTALLER", "INSTALL_RESULT",
            installResultMessage("INSTALLER_SUCCESS", "AUTOMATIC", packageType, exitCode,
                installRoot, launcherRelativePath, null));
    }

    private void installWindows(Path packageFile, Path installRoot, String launcherRelativePath,
            UpdateAuditLog audit) throws Exception {
        Set<String> unavailableLaunchers = new HashSet<>();
        Exception automaticFailure;
        try {
            int exitCode = runWindowsInstaller(
                new WindowsInstallRequest(packageFile, installRoot, true), unavailableLaunchers, audit);
            requireSuccessfulExitCode(UpdatePackageTypeEnum.WINDOWS_EXE, exitCode);
            verifyInstalledPath(installRoot, launcherRelativePath);
            audit.critical("NATIVE_INSTALLER", "INSTALL_RESULT",
                installResultMessage("INSTALLER_SUCCESS", "AUTOMATIC", UpdatePackageTypeEnum.WINDOWS_EXE,
                    exitCode, installRoot, launcherRelativePath, null));
            return;
        } catch (InstallerCancelledException | InstallerStateUnknownException exception) {
            auditInstallFailure("AUTOMATIC", exception, installRoot, launcherRelativePath, audit);
            throw exception;
        } catch (InstallerExitException exception) {
            auditInstallFailure("AUTOMATIC", exception, installRoot, launcherRelativePath, audit);
            if (exception.exitCode() == MSI_INSTALL_BLOCKED_BY_POLICY) {
                throw exception;
            }
            automaticFailure = exception;
        } catch (InstallerNotStartedException exception) {
            auditInstallFailure("AUTOMATIC", exception, installRoot, launcherRelativePath, audit);
            automaticFailure = exception;
        }

        audit.critical("NATIVE_INSTALLER", "VISIBLE_FALLBACK",
            "automatic Windows installation failed; opening visible fixed-directory installer");
        try {
            int exitCode = runWindowsInstaller(
                new WindowsInstallRequest(packageFile, installRoot, false), unavailableLaunchers, audit);
            requireSuccessfulExitCode(UpdatePackageTypeEnum.WINDOWS_EXE, exitCode);
            verifyInstalledPath(installRoot, launcherRelativePath);
            audit.critical("NATIVE_INSTALLER", "INSTALL_RESULT",
                installResultMessage("INSTALLER_SUCCESS", "VISIBLE_FIXED",
                    UpdatePackageTypeEnum.WINDOWS_EXE, exitCode,
                    installRoot, launcherRelativePath, null));
        } catch (Exception exception) {
            exception.addSuppressed(automaticFailure);
            auditInstallFailure("VISIBLE_FIXED", exception, installRoot, launcherRelativePath, audit);
            throw exception;
        }
    }

    private int runWindowsInstaller(WindowsInstallRequest request, Set<String> unavailableLaunchers,
            UpdateAuditLog audit) throws Exception {
        List<InstallerNotStartedException> failures = new ArrayList<>();
        for (WindowsInstallerLauncher launcher : windowsLaunchers) {
            if (unavailableLaunchers.contains(launcher.name())) {
                continue;
            }
            audit.critical("NATIVE_INSTALLER", "LAUNCH_ATTEMPT",
                "launcher=" + launcher.name() + " mode=" + request.mode());
            try {
                int exitCode = launcher.launch(request, audit);
                audit.critical("NATIVE_INSTALLER", "PROCESS_EXITED",
                    "launcher=" + launcher.name() + " mode=" + request.mode()
                        + " exitCode=" + exitCode);
                if (exitCode == MSI_INSTALL_CANCELLED) {
                    throw new InstallerCancelledException("Windows installation was cancelled", exitCode);
                }
                if (!isSuccessfulExitCode(UpdatePackageTypeEnum.WINDOWS_EXE, exitCode)) {
                    throw new InstallerExitException("Windows installation failed with exit code " + exitCode,
                        exitCode);
                }
                return exitCode;
            } catch (InstallerNotStartedException exception) {
                unavailableLaunchers.add(launcher.name());
                failures.add(exception);
                audit.critical("NATIVE_INSTALLER", "LAUNCH_REJECTED",
                    "launcher=" + launcher.name() + " mode=" + request.mode()
                        + " reason=" + safeMessage(exception));
            }
        }
        InstallerNotStartedException failure = new InstallerNotStartedException(
            "No Windows installer launcher could start the package");
        failures.forEach(failure::addSuppressed);
        throw failure;
    }

    private static void auditInstallFailure(String mode, Exception failure, Path installRoot,
            String launcherRelativePath, UpdateAuditLog audit) {
        int exitCode = -1;
        if (failure instanceof InstallerExitException exitFailure) {
            exitCode = exitFailure.exitCode();
        } else if (failure instanceof InstallerCancelledException cancelled) {
            exitCode = cancelled.exitCode();
        }
        audit.critical("NATIVE_INSTALLER", "INSTALL_RESULT",
            installResultMessage("FAILED", mode, UpdatePackageTypeEnum.WINDOWS_EXE, exitCode,
                installRoot, launcherRelativePath, safeMessage(failure)));
        audit.error("NATIVE_INSTALLER", mode + "_FAILED", failure);
    }

    private static void verifyInstalledPath(Path installRoot, String launcherRelativePath) {
        if (installRoot == null || launcherRelativePath == null || launcherRelativePath.isBlank()) {
            throw new IllegalStateException("Windows install path cannot be verified");
        }
        Path launcher = installRoot.resolve(launcherRelativePath).normalize();
        if (!launcher.startsWith(installRoot) || !Files.isRegularFile(launcher)) {
            throw new IllegalStateException("Windows installer did not place the launcher at " + launcher);
        }
    }

    private static int runProcess(UpdatePackageTypeEnum packageType, List<String> command,
            String launcherName, UpdateAuditLog audit) throws Exception {
        Process process;
        try {
            process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        } catch (Exception exception) {
            throw new InstallerNotStartedException(
                "Cannot start " + launcherName + ": " + safeMessage(exception), exception);
        }
        audit.critical("NATIVE_INSTALLER", "PROCESS_STARTED",
            "launcher=" + launcherName + " packageType=" + packageType + " pid=" + process.pid());
        if (!process.waitFor(INSTALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            throw new InstallerStateUnknownException(
                "Native package installation timed out after it was started by " + launcherName);
        }
        return process.exitValue();
    }

    private static void requireSuccessfulExitCode(UpdatePackageTypeEnum packageType, int exitCode)
            throws InstallerExitException {
        if (!isSuccessfulExitCode(packageType, exitCode)) {
            throw new InstallerExitException(
                "Native package installation failed with exit code " + exitCode, exitCode);
        }
    }

    private static String installResultMessage(String status, String mode, UpdatePackageTypeEnum packageType,
            int exitCode, Path installRoot, String launcherRelativePath, String error) {
        StringBuilder message = new StringBuilder()
            .append("status=").append(status)
            .append(" mode=").append(mode)
            .append(" packageType=").append(packageType)
            .append(" exitCode=").append(exitCode)
            .append(" installRoot=").append(installRoot)
            .append(" launcherRelativePath=").append(launcherRelativePath);
        if (error != null && !error.isBlank()) {
            message.append(" error=").append(error);
        }
        return message.toString();
    }

    static boolean isSuccessfulExitCode(UpdatePackageTypeEnum packageType, int exitCode) {
        return exitCode == 0 || (packageType == UpdatePackageTypeEnum.WINDOWS_EXE && exitCode == 3010);
    }

    static List<String> commandFor(UpdatePackageTypeEnum packageType, Path packageFile) {
        return commandFor(packageType, packageFile, null, false);
    }

    static List<String> commandFor(UpdatePackageTypeEnum packageType, Path packageFile,
            Path installRoot, boolean automatic) {
        Path normalized = packageFile.toAbsolutePath().normalize();
        return switch (packageType) {
            case WINDOWS_EXE -> powerShellCommand(
                new WindowsInstallRequest(normalized, installRoot, automatic));
            case LINUX_DEB -> List.of("pkexec", "/usr/bin/dpkg", "-i", normalized.toString());
            case LINUX_RPM -> List.of("pkexec", "/usr/bin/rpm", "-U", "--replacepkgs", normalized.toString());
            default -> throw new IllegalArgumentException("Package type is not a native installer: " + packageType);
        };
    }

    static List<String> windowsInstallerArguments(Path installRoot, boolean automatic) {
        List<String> arguments = new ArrayList<>();
        if (automatic) {
            arguments.addAll(List.of(
                "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/CLOSEAPPLICATIONS",
                "/FORCECLOSEAPPLICATIONS", "/CHAT2DBUPDATE=1"));
        } else {
            arguments.addAll(List.of("/NORESTART", "/CHAT2DBFIXEDDIR=1"));
        }
        if (installRoot != null) {
            arguments.add("/CHAT2DBTARGETDIR=" + installRoot.toAbsolutePath().normalize());
        }
        return List.copyOf(arguments);
    }

    private static List<String> powerShellCommand(WindowsInstallRequest request) {
        String quotedPath = request.packageFile().toString().replace("'", "''");
        String arguments = powerShellArgumentList(request.arguments());
        String script = "$ProgressPreference='SilentlyContinue';"
            + "$p = Start-Process -FilePath '" + quotedPath
            + "' -ArgumentList " + arguments
            + " -Verb RunAs -Wait -PassThru; exit $p.ExitCode";
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    private static String powerShellArgumentList(List<String> arguments) {
        return arguments.stream()
            .map(argument -> {
                String escaped = argument.replace("'", "''");
                return argument.chars().anyMatch(Character::isWhitespace)
                    ? "'\"" + escaped + "\"'"
                    : "'" + escaped + "'";
            })
            .reduce((left, right) -> left + "," + right)
            .orElse("''");
    }

    private static List<WindowsInstallerLauncher> defaultWindowsLaunchers() {
        return List.of(
            new ShellExecuteExInstallerLauncher(),
            new ProcessInstallerLauncher("POWERSHELL", NativePackageInstaller::powerShellCommand),
            new ProcessInstallerLauncher("CMD", NativePackageInstaller::cmdCommand)
        );
    }

    private static List<String> cmdCommand(WindowsInstallRequest request) {
        String command = System.getenv().getOrDefault("ComSpec", "cmd.exe");
        List<String> result = new ArrayList<>(List.of(command, "/D", "/S", "/C", "start", "", "/WAIT",
            request.packageFile().toString()));
        result.addAll(request.arguments());
        return List.copyOf(result);
    }

    private static String windowsCommandLine(List<String> arguments) {
        return arguments.stream()
            .map(NativePackageInstaller::quoteWindowsArgument)
            .reduce((left, right) -> left + " " + right)
            .orElse("");
    }

    private static String quoteWindowsArgument(String argument) {
        if (!argument.isEmpty() && argument.chars().noneMatch(character -> Character.isWhitespace(character)
                || character == '"')) {
            return argument;
        }
        StringBuilder quoted = new StringBuilder("\"");
        int backslashes = 0;
        for (int index = 0; index < argument.length(); index++) {
            char character = argument.charAt(index);
            if (character == '\\') {
                backslashes++;
            } else if (character == '"') {
                quoted.append("\\".repeat(backslashes * 2 + 1)).append('"');
                backslashes = 0;
            } else {
                quoted.append("\\".repeat(backslashes)).append(character);
                backslashes = 0;
            }
        }
        quoted.append("\\".repeat(backslashes * 2)).append('"');
        return quoted.toString();
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    enum WindowsInstallMode {
        AUTOMATIC,
        VISIBLE_FIXED
    }

    record WindowsInstallRequest(Path packageFile, Path installRoot, boolean automatic) {
        WindowsInstallRequest {
            packageFile = packageFile.toAbsolutePath().normalize();
            installRoot = installRoot == null ? null : installRoot.toAbsolutePath().normalize();
        }

        List<String> arguments() {
            return windowsInstallerArguments(installRoot, automatic);
        }

        WindowsInstallMode mode() {
            return automatic ? WindowsInstallMode.AUTOMATIC : WindowsInstallMode.VISIBLE_FIXED;
        }
    }

    interface WindowsInstallerLauncher {
        String name();

        int launch(WindowsInstallRequest request, UpdateAuditLog audit) throws Exception;
    }

    private static final class ShellExecuteExInstallerLauncher implements WindowsInstallerLauncher {

        @Override
        public String name() {
            return "SHELLEXECUTE";
        }

        @Override
        public int launch(WindowsInstallRequest request, UpdateAuditLog audit) throws Exception {
            boolean processStarted = false;
            try {
                SHELLEXECUTEINFO info = new SHELLEXECUTEINFO();
                info.cbSize = info.size();
                info.fMask = Shell32.SEE_MASK_NOCLOSEPROCESS | Shell32.SEE_MASK_FLAG_NO_UI;
                info.lpVerb = "runas";
                info.lpFile = request.packageFile().toString();
                info.lpParameters = windowsCommandLine(request.arguments());
                info.lpDirectory = request.packageFile().getParent().toString();
                info.nShow = request.automatic() ? WinUser.SW_HIDE : WinUser.SW_SHOWNORMAL;
                info.write();
                if (!Shell32.INSTANCE.ShellExecuteEx(info)) {
                    int errorCode = Kernel32.INSTANCE.GetLastError();
                    if (errorCode == ERROR_CANCELLED) {
                        throw new InstallerCancelledException("Windows elevation was cancelled", errorCode);
                    }
                    throw new InstallerNotStartedException(
                        "ShellExecuteEx failed before starting the installer; osError=" + errorCode);
                }
                processStarted = true;
                info.read();
                HANDLE process = info.hProcess;
                if (process == null) {
                    throw new InstallerStateUnknownException(
                        "ShellExecuteEx started the installer without returning a process handle");
                }
                audit.critical("NATIVE_INSTALLER", "PROCESS_STARTED",
                    "launcher=" + name() + " packageType=WINDOWS_EXE pid="
                        + Kernel32.INSTANCE.GetProcessId(process));
                try {
                    int waitResult = Kernel32.INSTANCE.WaitForSingleObject(
                        process, Math.toIntExact(INSTALL_TIMEOUT.toMillis()));
                    if (waitResult == WAIT_TIMEOUT) {
                        throw new InstallerStateUnknownException(
                            "Windows installer timed out after ShellExecuteEx started it");
                    }
                    if (waitResult != WinBase.WAIT_OBJECT_0) {
                        throw new InstallerStateUnknownException(
                            "Cannot determine Windows installer completion; waitResult=" + waitResult);
                    }
                    IntByReference exitCode = new IntByReference();
                    if (!Kernel32.INSTANCE.GetExitCodeProcess(process, exitCode)) {
                        throw new InstallerStateUnknownException(
                            "Cannot read Windows installer exit code; osError="
                                + Kernel32.INSTANCE.GetLastError());
                    }
                    return exitCode.getValue();
                } finally {
                    Kernel32.INSTANCE.CloseHandle(process);
                }
            } catch (LinkageError | RuntimeException failure) {
                if (processStarted) {
                    throw new InstallerStateUnknownException(
                        "Windows native launcher failed after starting the installer", failure);
                }
                throw new InstallerNotStartedException(
                    "Windows native launcher is unavailable: " + safeMessage(failure), failure);
            }
        }
    }

    private record ProcessInstallerLauncher(String name, WindowsCommandFactory commandFactory)
            implements WindowsInstallerLauncher {

        @Override
        public int launch(WindowsInstallRequest request, UpdateAuditLog audit) throws Exception {
            return runProcess(UpdatePackageTypeEnum.WINDOWS_EXE,
                commandFactory.command(request), name, audit);
        }
    }

    @FunctionalInterface
    private interface WindowsCommandFactory {
        List<String> command(WindowsInstallRequest request);
    }

    static class InstallerNotStartedException extends Exception {
        InstallerNotStartedException(String message) {
            super(message);
        }

        InstallerNotStartedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class InstallerCancelledException extends Exception {
        private final int exitCode;

        InstallerCancelledException(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }

        int exitCode() {
            return exitCode;
        }
    }

    static final class InstallerExitException extends Exception {
        private final int exitCode;

        InstallerExitException(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }

        int exitCode() {
            return exitCode;
        }
    }

    static final class InstallerStateUnknownException extends Exception {
        InstallerStateUnknownException(String message) {
            super(message);
        }

        InstallerStateUnknownException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

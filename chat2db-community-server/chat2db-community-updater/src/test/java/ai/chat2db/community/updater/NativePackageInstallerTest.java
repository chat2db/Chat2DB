package ai.chat2db.community.updater;

import ai.chat2db.community.updater.v2.audit.UpdateAuditLog;
import ai.chat2db.community.updater.v2.enums.UpdatePackageTypeEnum;
import ai.chat2db.community.updater.v2.installation.UpdateLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativePackageInstallerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void usesFixedDpkgAndRpmCommands() {
        Path deb = temporaryDirectory.resolve("Chat2DB Community.deb").toAbsolutePath();
        Path rpm = temporaryDirectory.resolve("Chat2DB Community.rpm").toAbsolutePath();

        assertEquals(List.of("pkexec", "/usr/bin/dpkg", "-i", deb.toString()),
            NativePackageInstaller.commandFor(UpdatePackageTypeEnum.LINUX_DEB, deb));
        assertEquals(List.of(
                "pkexec", "/usr/bin/rpm", "-U", "--replacepkgs", rpm.toString()),
            NativePackageInstaller.commandFor(UpdatePackageTypeEnum.LINUX_RPM, rpm));
    }

    @Test
    void windowsInstallerUsesElevatedSilentFixedArguments() {
        Path installer = temporaryDirectory.resolve("Chat2DB Community.exe").toAbsolutePath();
        Path installRoot = temporaryDirectory.resolve("D:/Apps/Chat2DB Community").toAbsolutePath();

        List<String> command = NativePackageInstaller.commandFor(
            UpdatePackageTypeEnum.WINDOWS_EXE, installer, installRoot, true);
        String script = new String(
            Base64.getDecoder().decode(command.get(4)), StandardCharsets.UTF_16LE);

        assertEquals(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand"),
            command.subList(0, 4));
        assertTrue(script.contains(installer.toString()));
        assertTrue(script.contains("/VERYSILENT"));
        assertTrue(script.contains("/CHAT2DBUPDATE=1"));
        assertFalse(script.contains("/CHAT2DBMSILOG="));
        assertTrue(script.contains(",'\"/CHAT2DBTARGETDIR=" + installRoot + "\"'"));
        assertTrue(script.contains("$ProgressPreference='SilentlyContinue'"));
        assertTrue(script.contains("-Verb RunAs"));
    }

    @Test
    void windowsFallbackKeepsTheOriginalInstallDirectory() {
        Path installer = temporaryDirectory.resolve("Chat2DB Community.exe").toAbsolutePath();
        Path installRoot = temporaryDirectory.resolve("installed").toAbsolutePath();
        List<String> command = NativePackageInstaller.commandFor(
            UpdatePackageTypeEnum.WINDOWS_EXE, installer, installRoot, false);
        String script = new String(
            Base64.getDecoder().decode(command.get(4)), StandardCharsets.UTF_16LE);
        assertFalse(script.contains("/VERYSILENT"));
        assertFalse(script.contains("/CHAT2DBUPDATE=1"));
        assertTrue(script.contains("/CHAT2DBFIXEDDIR=1"));
        assertFalse(script.contains("/CHAT2DBMSILOG="));
        assertTrue(script.contains("/CHAT2DBTARGETDIR=" + installRoot));
        assertTrue(script.contains("-Verb RunAs"));
    }

    @Test
    void rejectsDirectReplacementPackageTypes() {
        assertThrows(IllegalArgumentException.class, () -> NativePackageInstaller.commandFor(
            UpdatePackageTypeEnum.MACOS_APP_ARCHIVE, temporaryDirectory.resolve("package.tar.gz")));
    }

    @Test
    void acceptsWindowsRebootRequiredExitCodeOnlyForWindowsInstaller() {
        assertTrue(NativePackageInstaller.isSuccessfulExitCode(UpdatePackageTypeEnum.WINDOWS_EXE, 3010));
        assertTrue(NativePackageInstaller.isSuccessfulExitCode(UpdatePackageTypeEnum.LINUX_DEB, 0));
        assertTrue(!NativePackageInstaller.isSuccessfulExitCode(UpdatePackageTypeEnum.LINUX_DEB, 3010));
    }

    @Test
    void fallsBackWhenWindowsLauncherCannotStart() throws Exception {
        WindowsFixture fixture = windowsFixture("spawn-fallback");
        List<String> attempts = new ArrayList<>();
        NativePackageInstaller installer = new NativePackageInstaller(List.of(
            launcher("POWERSHELL", (request, audit) -> {
                attempts.add("POWERSHELL");
                throw new NativePackageInstaller.InstallerNotStartedException("blocked by policy");
            }),
            launcher("CMD", (request, audit) -> {
                attempts.add("CMD");
                return 0;
            })
        ));

        installer.install(UpdatePackageTypeEnum.WINDOWS_EXE, fixture.packageFile(),
            fixture.installRoot(), "Chat2DB Community.exe", fixture.audit());

        assertEquals(List.of("POWERSHELL", "CMD"), attempts);
        String audit = Files.readString(fixture.auditFile());
        assertTrue(audit.contains("event=LAUNCH_REJECTED"));
        assertTrue(audit.contains("launcher=POWERSHELL"));
        assertTrue(audit.contains("status=INSTALLER_SUCCESS mode=AUTOMATIC"));
    }

    @Test
    void opensVisibleInstallerAfterAutomaticInstallerFailure() throws Exception {
        WindowsFixture fixture = windowsFixture("visible-fallback");
        List<NativePackageInstaller.WindowsInstallMode> modes = new ArrayList<>();
        NativePackageInstaller installer = new NativePackageInstaller(List.of(
            launcher("SHELLEXECUTE", (request, audit) -> {
                modes.add(request.mode());
                return request.automatic() ? 1 : 0;
            })
        ));

        installer.install(UpdatePackageTypeEnum.WINDOWS_EXE, fixture.packageFile(),
            fixture.installRoot(), "Chat2DB Community.exe", fixture.audit());

        assertEquals(List.of(
            NativePackageInstaller.WindowsInstallMode.AUTOMATIC,
            NativePackageInstaller.WindowsInstallMode.VISIBLE_FIXED
        ), modes);
        assertTrue(Files.readString(fixture.auditFile()).contains("event=VISIBLE_FALLBACK"));
    }

    @Test
    void doesNotRetryAfterUserCancelsInstaller() throws Exception {
        WindowsFixture fixture = windowsFixture("cancelled");
        List<String> attempts = new ArrayList<>();
        NativePackageInstaller installer = new NativePackageInstaller(List.of(
            launcher("SHELLEXECUTE", (request, audit) -> {
                attempts.add("SHELLEXECUTE");
                return 1602;
            }),
            launcher("POWERSHELL", (request, audit) -> {
                attempts.add("POWERSHELL");
                return 0;
            })
        ));

        assertThrows(NativePackageInstaller.InstallerCancelledException.class,
            () -> installer.install(UpdatePackageTypeEnum.WINDOWS_EXE, fixture.packageFile(),
                fixture.installRoot(), "Chat2DB Community.exe", fixture.audit()));
        assertEquals(List.of("SHELLEXECUTE"), attempts);
    }

    @Test
    void doesNotRetryWhenStartedInstallerStateIsUnknown() throws Exception {
        WindowsFixture fixture = windowsFixture("unknown-state");
        List<String> attempts = new ArrayList<>();
        NativePackageInstaller installer = new NativePackageInstaller(List.of(
            launcher("SHELLEXECUTE", (request, audit) -> {
                attempts.add("SHELLEXECUTE");
                throw new NativePackageInstaller.InstallerStateUnknownException("timed out");
            }),
            launcher("POWERSHELL", (request, audit) -> {
                attempts.add("POWERSHELL");
                return 0;
            })
        ));

        assertThrows(NativePackageInstaller.InstallerStateUnknownException.class,
            () -> installer.install(UpdatePackageTypeEnum.WINDOWS_EXE, fixture.packageFile(),
                fixture.installRoot(), "Chat2DB Community.exe", fixture.audit()));
        assertEquals(List.of("SHELLEXECUTE"), attempts);
    }

    @Test
    void doesNotOpenVisibleInstallerWhenSystemPolicyRejectsPackage() throws Exception {
        WindowsFixture fixture = windowsFixture("policy-rejected");
        List<NativePackageInstaller.WindowsInstallMode> modes = new ArrayList<>();
        NativePackageInstaller installer = new NativePackageInstaller(List.of(
            launcher("SHELLEXECUTE", (request, audit) -> {
                modes.add(request.mode());
                return 1625;
            })
        ));

        NativePackageInstaller.InstallerExitException failure = assertThrows(
            NativePackageInstaller.InstallerExitException.class,
            () -> installer.install(UpdatePackageTypeEnum.WINDOWS_EXE, fixture.packageFile(),
                fixture.installRoot(), "Chat2DB Community.exe", fixture.audit()));

        assertEquals(1625, failure.exitCode());
        assertEquals(List.of(NativePackageInstaller.WindowsInstallMode.AUTOMATIC), modes);
    }

    private WindowsFixture windowsFixture(String name) throws Exception {
        Path installRoot = temporaryDirectory.resolve(name).resolve("D:/Program Files/Chat2DB Community");
        Files.createDirectories(installRoot);
        Files.writeString(installRoot.resolve("Chat2DB Community.exe"), "existing launcher");
        Path packageFile = temporaryDirectory.resolve(name + "-package.exe");
        Files.writeString(packageFile, "installer");
        UpdateLayout layout = new UpdateLayout(installRoot);
        String transactionId = "tx-" + name;
        return new WindowsFixture(
            installRoot,
            packageFile,
            UpdateAuditLog.open(layout, transactionId, "TEST"),
            layout.auditLogFile(transactionId)
        );
    }

    private static NativePackageInstaller.WindowsInstallerLauncher launcher(
            String name, WindowsLaunchAction action) {
        return new NativePackageInstaller.WindowsInstallerLauncher() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int launch(NativePackageInstaller.WindowsInstallRequest request,
                    UpdateAuditLog audit) throws Exception {
                return action.launch(request, audit);
            }
        };
    }

    @FunctionalInterface
    private interface WindowsLaunchAction {
        int launch(NativePackageInstaller.WindowsInstallRequest request, UpdateAuditLog audit)
            throws Exception;
    }

    private record WindowsFixture(Path installRoot, Path packageFile, UpdateAuditLog audit,
                                  Path auditFile) {
    }
}

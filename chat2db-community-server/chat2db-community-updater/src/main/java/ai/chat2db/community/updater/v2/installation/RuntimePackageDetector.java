package ai.chat2db.community.updater.v2.installation;

import ai.chat2db.community.updater.v2.enums.UpdatePackageTypeEnum;
import ai.chat2db.community.updater.v2.enums.UpdatePlatformEnum;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RuntimePackageDetector {

    private RuntimePackageDetector() {
    }

    public static UpdatePackageTypeEnum packageType(UpdatePlatformEnum platform, String packageName) {
        if (platform == UpdatePlatformEnum.MACOS) {
            return UpdatePackageTypeEnum.MACOS_APP_ARCHIVE;
        }
        if (platform == UpdatePlatformEnum.WINDOWS) {
            return UpdatePackageTypeEnum.WINDOWS_EXE;
        }
        if (platform == UpdatePlatformEnum.LINUX) {
            String appImage = System.getenv("APPIMAGE");
            if (appImage != null && !appImage.isBlank() && Files.isRegularFile(Path.of(appImage))) {
                return UpdatePackageTypeEnum.LINUX_APPIMAGE;
            }
            if (commandSucceeds("dpkg-query", "-W", "-f=${Status}", packageName)) {
                return UpdatePackageTypeEnum.LINUX_DEB;
            }
            if (commandSucceeds("rpm", "-q", packageName)) {
                return UpdatePackageTypeEnum.LINUX_RPM;
            }
            throw new IllegalStateException("Cannot identify the installed Linux package type");
        }
        throw new IllegalStateException("Unsupported full-package update platform: " + platform);
    }

    private static boolean commandSucceeds(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}

package ai.chat2db.community.updater.v2.runtime;

import ai.chat2db.community.updater.v2.enums.UpdateArchitectureEnum;
import ai.chat2db.community.updater.v2.enums.UpdatePlatformEnum;
import java.util.Locale;

public final class RuntimePlatformDetector {

    private RuntimePlatformDetector() {
    }

    public static UpdatePlatformEnum platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return UpdatePlatformEnum.MACOS;
        }
        if (os.contains("win")) {
            return UpdatePlatformEnum.WINDOWS;
        }
        if (os.contains("linux")) {
            return UpdatePlatformEnum.LINUX;
        }
        throw new IllegalStateException("Unsupported desktop update platform: " + os);
    }

    public static UpdateArchitectureEnum architecture() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return UpdateArchitectureEnum.ARM64;
        }
        if (arch.equals("x86_64") || arch.equals("amd64") || arch.equals("x64")) {
            return UpdateArchitectureEnum.X64;
        }
        throw new IllegalStateException("Unsupported desktop update architecture: " + arch);
    }
}

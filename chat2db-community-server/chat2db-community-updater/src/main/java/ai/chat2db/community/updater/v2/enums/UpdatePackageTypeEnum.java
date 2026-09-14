package ai.chat2db.community.updater.v2.enums;

public enum UpdatePackageTypeEnum {
    MACOS_APP_ARCHIVE("tar.gz", false, false),
    WINDOWS_EXE("exe", false, true),
    LINUX_APPIMAGE("AppImage", true, false),
    LINUX_DEB("deb", true, true),
    LINUX_RPM("rpm", true, true);

    private final String fileExtension;
    private final boolean singleFile;
    private final boolean nativeInstaller;

    UpdatePackageTypeEnum(String fileExtension, boolean singleFile, boolean nativeInstaller) {
        this.fileExtension = fileExtension;
        this.singleFile = singleFile;
        this.nativeInstaller = nativeInstaller;
    }

    public String fileExtension() {
        return fileExtension;
    }

    public boolean singleFile() {
        return singleFile;
    }

    public boolean nativeInstaller() {
        return nativeInstaller;
    }

    public boolean directReplacement() {
        return !nativeInstaller;
    }
}

package ai.chat2db.community.jcef.update;

import ai.chat2db.community.jcef.update.v2.FullPackageDesktopUpdater;
import ai.chat2db.community.updater.v2.discovery.UpdateDiscoveryService;
import ai.chat2db.community.updater.v2.transport.HttpsUpdateTransport;
import ai.chat2db.community.updater.v2.verification.TrustedUpdateKeys;
import ai.chat2db.community.updater.v2.verification.UpdateManifestVerifier;

public final class GitHubReleaseDesktopUpdater {
    public static final String INDEX_URL =
        "https://github.com/OtterMind/Chat2DB/releases/latest/download/release-index.json";

    private GitHubReleaseDesktopUpdater() {
    }

    static boolean isAllowedUrl(java.net.URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return false;
        }
        String host = uri.getHost();
        return ("github.com".equalsIgnoreCase(host)
                && uri.getPath().startsWith("/OtterMind/Chat2DB/releases/"))
            || "release-assets.githubusercontent.com".equalsIgnoreCase(host)
            || "objects.githubusercontent.com".equalsIgnoreCase(host);
    }

    public static FullPackageDesktopUpdater createDefault() {
        HttpsUpdateTransport transport = new HttpsUpdateTransport(GitHubReleaseDesktopUpdater::isAllowedUrl);
        return FullPackageDesktopUpdater.create("COMMUNITY", "chat2db-community", transport,
            new UpdateDiscoveryService(transport,
                new UpdateManifestVerifier(TrustedUpdateKeys.load("/chat2db-community-update-keys.properties", "chat2db.community.update")),
                channel -> INDEX_URL));
    }
}

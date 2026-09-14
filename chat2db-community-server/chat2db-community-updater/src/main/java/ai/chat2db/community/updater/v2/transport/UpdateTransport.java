package ai.chat2db.community.updater.v2.transport;

import java.nio.file.Path;

public interface UpdateTransport {

    <T> T getJson(String url, Class<T> type);

    Path download(String url, Path destination, long expectedSize, String expectedSha256,
        DownloadProgress progress);

    @FunctionalInterface
    interface DownloadProgress {
        void onBytes(long downloadedBytes, long totalBytes);
    }
}

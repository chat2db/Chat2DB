package ai.chat2db.community.updater.v2.verification;

import ai.chat2db.community.updater.v2.model.UpdateManifest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;
import java.util.TreeMap;

public final class ManifestCanonicalizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private ManifestCanonicalizer() {
    }

    public static byte[] canonicalBytes(UpdateManifest manifest) {
        Map<String, Object> fields = new TreeMap<>();
        fields.put("arch", manifest.arch());
        fields.put("buildSha", manifest.buildSha());
        fields.put("channel", manifest.channel());
        fields.put("keyId", manifest.keyId());
        fields.put("launcherRelativePath", manifest.launcherRelativePath());
        fields.put("minUpdaterProtocolVersion", manifest.minUpdaterProtocolVersion());
        fields.put("nativeVersion", manifest.nativeVersion());
        fields.put("packageSha256", manifest.packageSha256());
        fields.put("packageSize", manifest.packageSize());
        fields.put("packageType", manifest.packageType());
        fields.put("packageUrl", manifest.packageUrl());
        fields.put("platform", manifest.platform());
        fields.put("product", manifest.product());
        fields.put("releaseEpoch", manifest.releaseEpoch());
        fields.put("releaseNotesUrl", manifest.releaseNotesUrl());
        fields.put("schemaVersion", manifest.schemaVersion());
        fields.put("status", manifest.status());
        fields.put("updateScope", manifest.updateScope());
        fields.put("updaterProtocolVersion", manifest.updaterProtocolVersion());
        fields.put("version", manifest.version());
        try {
            return OBJECT_MAPPER.writeValueAsBytes(fields);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot canonicalize update manifest", exception);
        }
    }
}

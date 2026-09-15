package ai.chat2db.community.updater.v2.verification;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class TrustedUpdateKeys {

    private static final String BUNDLED_KEYS_RESOURCE = "/chat2db-update-keys.properties";

    private TrustedUpdateKeys() {
    }

    public static Map<String, PublicKey> load() {
        return load(BUNDLED_KEYS_RESOURCE);
    }

    public static Map<String, PublicKey> load(String resource) {
        return load(resource, "chat2db.update");
    }

    public static Map<String, PublicKey> load(String resource, String propertyPrefix) {
        Map<String, PublicKey> trustedKeys = new LinkedHashMap<>();
        Properties bundled = new Properties();
        try (var input = TrustedUpdateKeys.class.getResourceAsStream(resource)) {
            if (input != null) {
                bundled.load(input);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load bundled update signing key", exception);
        }

        addKeyPair(
            trustedKeys,
            resolvedValue(bundled.getProperty("keyId")),
            resolvedValue(bundled.getProperty("publicKey")),
            "Bundled update signing key"
        );
        addKeyPair(
            trustedKeys,
            resolvedValue(System.getProperty(propertyPrefix + ".key-id")),
            resolvedValue(System.getProperty(propertyPrefix + ".public-key")),
            "Configured update signing key"
        );
        return Map.copyOf(trustedKeys);
    }

    private static void addKeyPair(Map<String, PublicKey> trustedKeys, String keyId, String encodedKey,
            String label) {
        if (keyId == null && encodedKey == null) {
            return;
        }
        if (keyId == null || encodedKey == null) {
            throw new IllegalStateException(label + " id and public key must be configured together");
        }
        try {
            byte[] encoded = Base64.getDecoder().decode(encodedKey);
            PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(encoded));
            trustedKeys.put(keyId, publicKey);
        } catch (Exception exception) {
            throw new IllegalStateException(label + " is invalid", exception);
        }
    }

    private static String resolvedValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || (trimmed.startsWith("${") && trimmed.endsWith("}"))) {
            return null;
        }
        return trimmed;
    }
}

package ai.chat2db.community.updater.v2.verification;

import org.junit.jupiter.api.Test;
import java.security.KeyPairGenerator;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UpdateKeyIsolationTest {
    @Test
    void loadsOnlyTheSelectedProductsConfiguredTrustRoot() throws Exception {
        var generator = KeyPairGenerator.getInstance("Ed25519");
        var first = generator.generateKeyPair().getPublic();
        var second = generator.generateKeyPair().getPublic();
        String prefix = "chat2db.community.update";
        var keys = java.util.List.of("chat2db.update.key-id", "chat2db.update.public-key",
            prefix + ".key-id", prefix + ".public-key");
        var original = new java.util.HashMap<String, String>();
        keys.forEach(key -> original.put(key, System.getProperty(key)));
        try {
            System.setProperty("chat2db.update.key-id", "other-key");
            System.setProperty("chat2db.update.public-key", Base64.getEncoder().encodeToString(first.getEncoded()));
            System.setProperty(prefix + ".key-id", "community-key");
            System.setProperty(prefix + ".public-key", Base64.getEncoder().encodeToString(second.getEncoded()));
            var community = TrustedUpdateKeys.load("/absent.properties", prefix);
            assertEquals(second, community.get("community-key"));
            assertFalse(community.containsKey("other-key"));
            assertEquals(first, TrustedUpdateKeys.load("/absent.properties").get("other-key"));
        } finally {
            original.forEach((key, value) -> {
                if (value == null) System.clearProperty(key);
                else System.setProperty(key, value);
            });
        }
    }
}

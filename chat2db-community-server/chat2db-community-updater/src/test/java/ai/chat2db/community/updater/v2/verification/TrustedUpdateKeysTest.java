package ai.chat2db.community.updater.v2.verification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrustedUpdateKeysTest {

    @AfterEach
    void clearConfiguredKey() {
        System.clearProperty("chat2db.update.key-id");
        System.clearProperty("chat2db.update.public-key");
    }

    @Test
    void loadsConfiguredKeyAlongsideBundledTrustRoot() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        System.setProperty("chat2db.update.key-id", "test-key");
        System.setProperty(
            "chat2db.update.public-key",
            Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
        );

        Map<String, java.security.PublicKey> keys = TrustedUpdateKeys.load();

        assertArrayEquals(keyPair.getPublic().getEncoded(), keys.get("test-key").getEncoded());
    }

    @Test
    void rejectsHalfConfiguredKeyPair() {
        System.setProperty("chat2db.update.key-id", "test-key");

        assertThrows(IllegalStateException.class, TrustedUpdateKeys::load);
    }
}

package ai.chat2db.community.updater.v2.version;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticVersionTest {

    @Test
    void followsSemverPrereleasePrecedence() {
        List<String> ordered = List.of(
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-alpha.beta",
            "1.0.0-beta",
            "1.0.0-beta.2",
            "1.0.0-beta.11",
            "1.0.0-rc.1",
            "1.0.0"
        );
        for (int index = 0; index < ordered.size() - 1; index++) {
            assertTrue(SemanticVersion.parse(ordered.get(index))
                .compareTo(SemanticVersion.parse(ordered.get(index + 1))) < 0);
        }
    }

    @Test
    void ignoresBuildMetadataForPrecedence() {
        assertEquals(
            SemanticVersion.parse("5.3.4+build.1"),
            SemanticVersion.parse("5.3.4+build.2")
        );
    }

    @Test
    void supportsBridgeVersionPrefix() {
        assertEquals(SemanticVersion.parse("v5.3.3"), SemanticVersion.parse("5.3.3"));
    }

    @Test
    void rejectsNonSemverAndLeadingZeroes() {
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("5.3"));
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("05.3.4"));
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("5.3.4-beta.01"));
    }
}

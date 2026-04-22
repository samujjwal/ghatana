package com.ghatana.core.activej;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveJVendoringPolicyTest {

    private static final List<String> POLICY_ARTIFACTS = List.of( // GH-90000
        "io/README.md",
        "io/activej/PATCHES.md",
        "io/activej/package-info.java"
    );

    private static final List<String> VENDORED_SOURCES = List.of( // GH-90000
        "io/activej/promise/AbstractPromise.java",
        "io/activej/promise/SettablePromise.java",
        "io/activej/common/time/Stopwatch.java",
        "io/activej/eventloop/inspector/EventloopInspector.java"
    );

    @Test
    void vendoredActivejAreaMustRemainDocumented() { // GH-90000
        Path repoRoot = findRepoRoot(); // GH-90000

        for (String relativePath : POLICY_ARTIFACTS) { // GH-90000
            assertThat(repoRoot.resolve(relativePath)) // GH-90000
                .as("Expected vendored ActiveJ policy artifact %s", relativePath) // GH-90000
                .exists(); // GH-90000
        }
    }

    @Test
    void vendoredSourceFilesMustCarryOwnershipMarkers() throws IOException { // GH-90000
        Path repoRoot = findRepoRoot(); // GH-90000

        for (String relativePath : VENDORED_SOURCES) { // GH-90000
            String content = Files.readString(repoRoot.resolve(relativePath)); // GH-90000
            assertThat(content) // GH-90000
                .as("Expected vendored ActiveJ file %s to carry GHATANA-PATCH ownership marker", relativePath) // GH-90000
                .contains("GHATANA-PATCH: [GH-90000]")
                .contains("6.0-rc2 [GH-90000]");
        }
    }

    @Test
    void patchManifestMustTrackCurrentActivejBaseline() throws IOException { // GH-90000
        Path repoRoot = findRepoRoot(); // GH-90000
        String manifest = Files.readString(repoRoot.resolve("io/activej/PATCHES.md [GH-90000]"));

        assertThat(manifest).contains("Baseline upstream version:", "activej-6.0-rc2"); // GH-90000
        assertThat(manifest).contains("AbstractPromise.java", "SettablePromise.java", "Stopwatch.java", "EventloopInspector.java"); // GH-90000
    }

    private static Path findRepoRoot() { // GH-90000
        Path current = Path.of(" [GH-90000]").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle.kts [GH-90000]"))) {
            current = current.getParent(); // GH-90000
        }
        if (current == null) { // GH-90000
            throw new IllegalStateException("Could not locate repository root from test working directory [GH-90000]");
        }
        return current;
    }
}

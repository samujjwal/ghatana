package com.ghatana.core.activej;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveJVendoringPolicyTest {

    private static final List<String> POLICY_ARTIFACTS = List.of(
        "io/README.md",
        "io/activej/PATCHES.md",
        "io/activej/package-info.java"
    );

    private static final List<String> VENDORED_SOURCES = List.of(
        "io/activej/promise/AbstractPromise.java",
        "io/activej/promise/SettablePromise.java",
        "io/activej/common/time/Stopwatch.java",
        "io/activej/eventloop/inspector/EventloopInspector.java"
    );

    @Test
    void vendoredActivejAreaMustRemainDocumented() {
        Path repoRoot = findRepoRoot();

        for (String relativePath : POLICY_ARTIFACTS) {
            assertThat(repoRoot.resolve(relativePath))
                .as("Expected vendored ActiveJ policy artifact %s", relativePath)
                .exists();
        }
    }

    @Test
    void vendoredSourceFilesMustCarryOwnershipMarkers() throws IOException {
        Path repoRoot = findRepoRoot();

        for (String relativePath : VENDORED_SOURCES) {
            String content = Files.readString(repoRoot.resolve(relativePath));
            assertThat(content)
                .as("Expected vendored ActiveJ file %s to carry GHATANA-PATCH ownership marker", relativePath)
                .contains("GHATANA-PATCH:")
                .contains("6.0-rc2");
        }
    }

    @Test
    void patchManifestMustTrackCurrentActivejBaseline() throws IOException {
        Path repoRoot = findRepoRoot();
        String manifest = Files.readString(repoRoot.resolve("io/activej/PATCHES.md"));

        assertThat(manifest).contains("Baseline upstream version:", "activej-6.0-rc2");
        assertThat(manifest).contains("AbstractPromise.java", "SettablePromise.java", "Stopwatch.java", "EventloopInspector.java");
    }

    private static Path findRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Could not locate repository root from test working directory");
        }
        return current;
    }
}

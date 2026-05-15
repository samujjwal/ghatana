package com.ghatana.digitalmarketing.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Digital Marketing kernel bridge boundary")
class DigitalMarketingKernelBridgeBoundaryTest {

    private static final Path MODULE_ROOT = resolveModuleRoot();
    private static final List<String> FORBIDDEN_PRODUCTION_TERMS = List.of(
        "ProductLifecycleExecutor",
        "ProductLifecyclePlanner",
        "LifecycleManifestWriter",
        "kernel-lifecycle",
        "lifecycle-runner",
        "Runtime.getRuntime(",
        "new ProcessBuilder("
    );

    @Test
    @DisplayName("bridge production sources do not embed lifecycle runner or shell execution")
    void shouldNotEmbedLifecycleRunnerOrShellExecution() throws IOException {
        try (Stream<Path> files = Files.walk(MODULE_ROOT.resolve("src/main"))) {
            List<String> violations = files
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".kts"))
                .flatMap(path -> forbiddenTerms(path).stream())
                .toList();

            assertThat(violations).isEmpty();
        }
    }

    @Test
    @DisplayName("bridge owns product policy and authorization tests")
    void shouldOwnProductPolicyAndAuthorizationTests() {
        assertThat(MODULE_ROOT.resolve("src/main/resources/policies/dmos.rego"))
            .exists()
            .isRegularFile();
        assertThat(MODULE_ROOT.resolve("src/test/java/com/ghatana/digitalmarketing/bridge/OpaAuthorizationServiceTest.java"))
            .exists()
            .isRegularFile();
    }

    private static List<String> forbiddenTerms(Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            return FORBIDDEN_PRODUCTION_TERMS.stream()
                .filter(source::contains)
                .map(term -> path + " contains " + term)
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    private static Path resolveModuleRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("src/main/resources/policies/dmos.rego"))) {
            return current;
        }
        Path fromRepositoryRoot = current.resolve("products/digital-marketing/dm-kernel-bridge");
        if (Files.exists(fromRepositoryRoot.resolve("src/main/resources/policies/dmos.rego"))) {
            return fromRepositoryRoot;
        }
        throw new IllegalStateException("Unable to locate dm-kernel-bridge module root from " + current);
    }
}

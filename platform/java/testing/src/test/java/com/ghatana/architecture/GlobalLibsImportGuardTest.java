package com.ghatana.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardrail test to ensure global libs/java modules do not import
 * product-specific packages (e.g. com.ghatana.aep.*, com.ghatana.products.*)
 * in their main source sets.
 */
class GlobalLibsImportGuardTest {

    @Test
    void libsShouldNotImportProductSpecificPackagesInMainSources() throws IOException {
        // Project layout: this test runs with working directory at libs/java/architecture-tests
        Path libsJavaRoot = Paths.get("").toAbsolutePath().getParent();

        List<String> violations = new ArrayList<>();

        Files.walk(libsJavaRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> path.toString().contains("src/main/java"))
                .forEach(path -> scanFileForDisallowedImports(path, violations));

        assertTrue(
                violations.isEmpty(),
                () -> "Found disallowed product imports in libs/java main sources:\n" + String.join("\n", violations)
        );
    }

    private static void scanFileForDisallowedImports(Path path, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("import ") &&
                        (line.contains("com.ghatana.aep.") || line.contains("com.ghatana.products."))) {
                    violations.add(path + ":" + (i + 1) + " -> " + line);
                }
            }
        } catch (IOException e) {
            // If we can't read a file, surface it as a violation to be safe
            violations.add(path + " -> ERROR reading file: " + e.getMessage());
        }
    }
}

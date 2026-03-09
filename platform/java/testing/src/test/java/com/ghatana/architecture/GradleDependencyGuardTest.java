package com.ghatana.architecture;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardrail test to detect Gradle dependencies from libs/java modules to
 * products:* modules. This helps keep global libs free from product-specific
 * runtime dependencies.
 */
class GradleDependencyGuardTest {

    private static final String ALLOWED_PRODUCT_PREFIX = ":products:data-cloud:event-cloud:";
    private static final String ALLOWED_DATACLOUD_PLATFORM = ":products:data-cloud:platform";

    @Test
    void libsShouldNotDependOnProductModulesInGradleBuildFiles() throws IOException {
        Path libsJavaRoot = Paths.get("").toAbsolutePath().getParent();

        List<String> violations = new ArrayList<>();

        Files.walk(libsJavaRoot)
                .filter(path -> {
                    String name = path.getFileName().toString();
                    return name.equals("build.gradle") || name.equals("build.gradle.kts");
                })
                .forEach(path -> scanGradleFile(path, libsJavaRoot, violations));

        assertTrue(
                violations.isEmpty(),
                () -> "Found disallowed Gradle dependencies from libs/java to products:* modules:\n" +
                        String.join("\n", violations)
        );
    }

    private static void scanGradleFile(Path path, Path libsJavaRoot, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            String rel = libsJavaRoot.relativize(path).toString().replace(File.separatorChar, '/');

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if ((line.contains("project(':products:") || line.contains("project(\":products:"))
                        && !line.contains(ALLOWED_PRODUCT_PREFIX)
                        && !line.contains(ALLOWED_DATACLOUD_PLATFORM)) {
                    violations.add(rel + ":" + (i + 1) + " -> " + line);
                }
            }
        } catch (IOException e) {
            violations.add(path + " -> ERROR reading file: " + e.getMessage());
        }
    }
}

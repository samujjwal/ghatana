package com.ghatana.platform.database.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Prevent reintroduction of legacy database cache package namespaces
 * @doc.layer platform
 * @doc.pattern ValidationTest
 */
@DisplayName("Database Cache Namespace Drift Tests")
class DatabaseCacheNamespaceDriftTest {

    @Test
    @DisplayName("database cache sources should not reintroduce the legacy core redis cache package")
    void databaseCacheSourcesShouldNotUseLegacyCoreRedisNamespace() throws IOException { // GH-90000
        Path moduleRoot = findDatabaseModuleRoot(); // GH-90000
        Map<String, String> violations = new LinkedHashMap<>(); // GH-90000

        Files.walk(moduleRoot.resolve("src/main/java"))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> inspectSource(path, moduleRoot, violations)); // GH-90000

        assertThat(violations) // GH-90000
                .as("legacy com.ghatana.core.cache.redis namespace should not appear in platform database sources")
                .isEmpty(); // GH-90000
    }

    private static void inspectSource(Path path, Path moduleRoot, Map<String, String> violations) { // GH-90000
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8); // GH-90000
            if (source.contains("package com.ghatana.core.cache.redis")
                    || source.contains("com.ghatana.core.cache.redis.")) {
                violations.put(moduleRoot.relativize(path).toString(), "legacy core redis cache namespace"); // GH-90000
            }
        } catch (IOException exception) { // GH-90000
            throw new IllegalStateException("Failed to inspect source file: " + path, exception); // GH-90000
        }
    }

    private static Path findDatabaseModuleRoot() { // GH-90000
        Path current = Path.of("").toAbsolutePath();
        while (current != null) { // GH-90000
            Path candidate = current.resolve("platform/java/database/build.gradle.kts");
            if (Files.exists(candidate)) { // GH-90000
                return candidate.getParent(); // GH-90000
            }
            current = current.getParent(); // GH-90000
        }
        throw new IllegalStateException("Could not locate platform/java/database module root");
    }
}

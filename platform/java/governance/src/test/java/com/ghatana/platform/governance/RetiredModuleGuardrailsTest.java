package com.ghatana.platform.governance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardrails for audit-driven module retirements and migrations.
 *
 * @doc.type class
 * @doc.purpose Prevent retired shadow modules and stale settings includes from re-entering the repo
 * @doc.layer platform
 * @doc.pattern ArchitecturalTest
 */
class RetiredModuleGuardrailsTest {

    @Test
    @DisplayName("Retired shadow module roots stay deleted [GH-90000]")
    void retiredShadowModuleRootsStayDeleted() { // GH-90000
        Path repoRoot = findRepoRoot(); // GH-90000

        List<String> retiredRoots = List.of( // GH-90000
                "platform/java/audio-video",
                "platform/java/incident-response",
                "platform/java/agent-memory",
                "platform/java/distributed-cache",
                "platform/java/security-analytics",
                "shared-services/feature-store-ingest",
                "products/yappc/core/yappc-domain-api",
                "products/yappc/tools/validation-tests"
        );

        for (String retiredRoot : retiredRoots) { // GH-90000
            assertThat(repoRoot.resolve(retiredRoot)) // GH-90000
                    .as("Retired root should stay deleted: %s", retiredRoot) // GH-90000
                    .doesNotExist(); // GH-90000
        }

        List<String> retiredDuplicateContracts = List.of( // GH-90000
            "products/virtual-org/engine/service/src/main/java/com/ghatana/virtualorg/events/EventEnvelope.java",
            "platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/contracts/validator.disabled"
        );

        for (String retiredContract : retiredDuplicateContracts) { // GH-90000
            assertThat(repoRoot.resolve(retiredContract)) // GH-90000
                .as("Retired duplicate contract should stay deleted: %s", retiredContract) // GH-90000
                .doesNotExist(); // GH-90000
        }
    }

    @Test
    @DisplayName("Root settings does not re-include retired modules [GH-90000]")
    void settingsDoesNotReincludeRetiredModules() throws IOException { // GH-90000
        Path repoRoot = findRepoRoot(); // GH-90000

        List<String> forbiddenIncludes = List.of( // GH-90000
                "include(\":platform:java:audio-video\")", // GH-90000
                "include(\":platform:java:incident-response\")", // GH-90000
                "include(\":platform:java:agent-memory\")", // GH-90000
                "include(\":platform:java:distributed-cache\")", // GH-90000
                "include(\":platform:java:security-analytics\")" // GH-90000
        );

        String settingsFile = "settings.gradle.kts";
        String content = Files.readString(repoRoot.resolve(settingsFile)); // GH-90000
        for (String forbiddenInclude : forbiddenIncludes) { // GH-90000
            assertThat(content) // GH-90000
                    .as("%s must not contain retired include %s", settingsFile, forbiddenInclude) // GH-90000
                    .doesNotContain(forbiddenInclude); // GH-90000
        }
    }

    private static Path findRepoRoot() { // GH-90000
        Path cursor = Path.of(System.getProperty("user.dir [GH-90000]")).toAbsolutePath();
        while (cursor != null) { // GH-90000
            if (Files.exists(cursor.resolve("settings.gradle.kts [GH-90000]"))) {
                return cursor;
            }
            cursor = cursor.getParent(); // GH-90000
        }
        throw new IllegalStateException("Could not locate repository root from user.dir [GH-90000]");
    }
}

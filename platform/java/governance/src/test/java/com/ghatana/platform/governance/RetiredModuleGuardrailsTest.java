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
    @DisplayName("Retired shadow module roots stay deleted")
    void retiredShadowModuleRootsStayDeleted() {
        Path repoRoot = findRepoRoot();

        List<String> retiredRoots = List.of(
                "platform/java/audio-video",
                "platform/java/incident-response",
                "platform/java/agent-memory",
                "platform/java/distributed-cache",
                "platform/java/security-analytics",
                "shared-services/feature-store-ingest",
                "products/yappc/core/yappc-domain-api",
                "products/yappc/tools/validation-tests"
        );

        for (String retiredRoot : retiredRoots) {
            assertThat(repoRoot.resolve(retiredRoot))
                    .as("Retired root should stay deleted: %s", retiredRoot)
                    .doesNotExist();
        }

        List<String> retiredDuplicateContracts = List.of(
            "products/virtual-org/engine/service/src/main/java/com/ghatana/virtualorg/events/EventEnvelope.java",
            "platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/contracts/validator.disabled"
        );

        for (String retiredContract : retiredDuplicateContracts) {
            assertThat(repoRoot.resolve(retiredContract))
                .as("Retired duplicate contract should stay deleted: %s", retiredContract)
                .doesNotExist();
        }
    }

    @Test
    @DisplayName("Settings snapshots do not re-include retired modules")
    void settingsSnapshotsDoNotReincludeRetiredModules() throws IOException {
        Path repoRoot = findRepoRoot();

        List<String> forbiddenIncludes = List.of(
                "include(\":platform:java:audio-video\")",
                "include(\":platform:java:incident-response\")",
                "include(\":platform:java:agent-memory\")",
                "include(\":platform:java:distributed-cache\")",
                "include(\":platform:java:security-analytics\")"
        );

        for (String settingsFile : List.of("settings.gradle.kts", "settings.gradle.kts.fixed")) {
            String content = Files.readString(repoRoot.resolve(settingsFile));
            for (String forbiddenInclude : forbiddenIncludes) {
                assertThat(content)
                        .as("%s must not contain retired include %s", settingsFile, forbiddenInclude)
                        .doesNotContain(forbiddenInclude);
            }
        }
    }

    private static Path findRepoRoot() {
        Path cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (cursor != null) {
            if (Files.exists(cursor.resolve("settings.gradle.kts"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Could not locate repository root from user.dir");
    }
}

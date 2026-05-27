package com.ghatana.yappc.common;

import com.ghatana.yappc.services.phase.PhasePacketServiceImpl;
import com.ghatana.yappc.storage.InMemoryEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static observability contract tests for YAPPC structured logging.
 */
@DisplayName("Structured logging contracts")
class StructuredLoggingContractTest {

    @Test
    @DisplayName("phase packet critical logs carry tenant, workspace, project, phase, and correlation fields")
    void phasePacketCriticalLogsCarryCanonicalScopeFields() throws IOException {
        assertThat(PhasePacketServiceImpl.class).isNotNull();
        String source = normalizedSource("products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketServiceImpl.java");

        List<String> criticalMessages = List.of(
                "Building phase packet",
                "Project in degraded state",
                "Built phase packet successfully",
                "DataCloud query failed for project",
                "Project state not found",
                "Unexpected error in queryProjectState",
                "Error querying phase blockers",
                "Error querying phase evidence",
                "Error querying governance records"
        );

        for (String message : criticalMessages) {
            String logTemplate = sliceAround(source, message);
            assertThat(logTemplate)
                    .as("log template for '%s'", message)
                    .contains("tenantId={}", "workspaceId={}", "projectId={}", "phase={}", "correlationId={}");
        }
    }

    @Test
    @DisplayName("dev event publisher logs payload shape rather than payload values")
    void eventPublisherLogsPayloadShapeOnly() throws IOException {
        assertThat(InMemoryEventPublisher.class).isNotNull();
        String source = normalizedSource("products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/InMemoryEventPublisher.java");

        assertThat(source).contains("payloadKeys={}", "payloadSize={}");
        assertThat(source).doesNotContain("Event data: {}", "log.debug(\"Event data: {}\", payload)");
    }

    private static String normalizedSource(String relativePath) throws IOException {
        return Files.readString(resolveRepoPath(relativePath)).replaceAll("\\s+", " ");
    }

    private static String sliceAround(String source, String marker) {
        int index = source.indexOf(marker);
        assertThat(index).as("source contains log marker '%s'", marker).isGreaterThanOrEqualTo(0);
        int end = Math.min(source.length(), index + 360);
        return source.substring(index, end);
    }

    private static Path resolveRepoPath(String relativePath) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository path: " + relativePath);
    }
}

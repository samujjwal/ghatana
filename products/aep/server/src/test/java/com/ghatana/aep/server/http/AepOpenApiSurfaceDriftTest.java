package com.ghatana.aep.server.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Guards AEP public route documentation from drifting behind exercised HTTP surfaces
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AEP OpenAPI surface drift")
class AepOpenApiSurfaceDriftTest {

    private static final List<String> REQUIRED_PATHS = List.of(
        "/health",
        "/ready",
        "/live",
        "/info",
        "/metrics",
        "/api/v1/events",
        "/api/v1/patterns",
        "/api/v1/agents",
        "/api/v1/runs",
        "/api/v1/runs/{runId}",
        "/api/v1/runs/{runId}/cancel",
        "/api/v1/hitl/pending",
        "/api/v1/hitl/{reviewId}/approve",
        "/api/v1/hitl/{reviewId}/reject",
        "/api/v1/hitl/{reviewId}/escalate",
        "/api/v1/learning/episodes",
        "/api/v1/learning/policies",
        "/api/v1/learning/reflect",
        "/api/v1/compliance/gdpr/access",
        "/api/v1/compliance/gdpr/erasure",
        "/api/v1/compliance/gdpr/portability",
        "/api/v1/compliance/ccpa/opt-out",
        "/api/v1/compliance/soc2/report",
        "/governance/kill-switch",
        "/governance/kill-switch/activate",
        "/governance/kill-switch/deactivate",
        "/governance/degradation",
        "/governance/policy/evaluate",
        "/governance/compliance/summary",
        "/governance/audit/summary",
        "/governance/security/egress",
        "/governance/security/scan",
        "/api/v1/analytics/anomalies",
        "/api/v1/analytics/forecast",
        "/api/v1/analytics/kpis",
        "/api/v1/analytics/query",
        "/api/v1/reports",
        "/api/v1/deployments",
        "/api/v1/session"
    );

    @Test
    @DisplayName("contracts and server OpenAPI specs stay in sync and document exercised public routes")
    void specsStayInSyncAndCoverRequiredRoutes() throws IOException {
        String contractsSpec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));
        String serverSpec = Files.readString(findRepoFile("products/aep/server/src/main/resources/openapi.yaml"));

        assertThat(contractsSpec).isEqualTo(serverSpec);

        for (String route : REQUIRED_PATHS) {
            assertThat(contractsSpec)
                .as("expected route %s to be documented in AEP OpenAPI", route)
                .contains("  " + route + ":");
        }
    }

    private static Path findRepoFile(String relativePath) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Unable to locate repo file: " + relativePath);
    }
}
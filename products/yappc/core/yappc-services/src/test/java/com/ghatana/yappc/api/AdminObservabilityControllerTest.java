package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies admin observability release-gate evidence API payloads
 * @doc.layer api
 * @doc.pattern Test
 */
@DisplayName("AdminObservabilityController")
class AdminObservabilityControllerTest extends EventloopTestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("release gates expose SLO, cost, domain, and API evidence")
    void listReleaseGatesExposesCanonicalGateEvidence() throws Exception {
        Path evidenceDir = tempDir.resolve(".kernel/evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("product-slo-budgets.json"), """
                {"status":"passed","summary":"SLO budgets passed","generatedAt":"2026-05-26T00:00:00.000Z"}
                """, StandardCharsets.UTF_8);
        Files.writeString(evidenceDir.resolve("product-cost-budgets.json"), """
                {"passed":true,"summary":"Cost budgets passed","generatedAt":"2026-05-26T00:01:00.000Z"}
                """, StandardCharsets.UTF_8);
        Files.writeString(evidenceDir.resolve("product-domain-invariants.json"), """
                {"warnings":["review"],"summary":"Domain invariant warning","generatedAt":"2026-05-26T00:02:00.000Z"}
                """, StandardCharsets.UTF_8);
        Files.writeString(evidenceDir.resolve("openapi-breaking-changes.json"), """
                {"status":"failed","summary":"Breaking change review required","generatedAt":"2026-05-26T00:03:00.000Z"}
                """, StandardCharsets.UTF_8);

        HttpResponse response = runPromise(() -> controller().listReleaseGates(
                HttpRequest.get("http://localhost/api/admin/observability/release-gates").build()));

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"status\":\"down\"")
                .contains("\"id\":\"product-slo-budgets\"")
                .contains("\"category\":\"SLO\"")
                .contains("\"id\":\"product-cost-budgets\"")
                .contains("\"category\":\"Cost\"")
                .contains("\"id\":\"product-domain-invariants\"")
                .contains("\"category\":\"Domain\"")
                .contains("\"id\":\"openapi-breaking-changes\"")
                .contains("\"category\":\"API\"")
                .contains("\"summary\":\"Breaking change review required\"");
    }

    @Test
    @DisplayName("missing evidence is explicit down state")
    void listReleaseGatesMarksMissingEvidenceDown() {
        HttpResponse response = runPromise(() -> controller().listReleaseGates(
                HttpRequest.get("http://localhost/api/admin/observability/release-gates").build()));

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"status\":\"down\"")
                .contains("Product SLO budgets evidence file is missing")
                .contains("OpenAPI breaking changes evidence file is missing");
    }

    private AdminObservabilityController controller() {
        return new AdminObservabilityController(
                objectMapper,
                tempDir,
                Executors.newVirtualThreadPerTaskExecutor());
    }
}

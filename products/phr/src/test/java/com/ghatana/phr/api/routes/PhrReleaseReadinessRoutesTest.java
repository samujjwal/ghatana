package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.kernel.release.ReleaseReadinessRuntimeService;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Enforcement matrix tests for {@link PhrReleaseReadinessRoutes}.
 *
 * <p>Verifies that the release readiness endpoint:
 * <ul>
 *   <li>Returns a structured response for authenticated users.</li>
 *   <li>Returns 400 when required context headers are absent.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Release readiness enforcement matrix: verifies admin-gated release cockpit route
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrReleaseReadinessRoutes — enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrReleaseReadinessRoutesTest extends EventloopTestBase {

    @Mock
    private ReleaseReadinessRuntimeService releaseReadinessService;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        lenient().when(releaseReadinessService.getReleaseReadiness(anyString(), anyString()))
            .thenReturn(io.activej.promise.Promise.of(Map.of("sections", Map.of("evidenceFreshness", Map.of("runtimeProven", true)))));
        servlet = new PhrReleaseReadinessRoutes(eventloop(), releaseReadinessService)
            .getServlet();
    }

    @Test
    @DisplayName("200 — admin with valid context receives release readiness report")
    void adminReceivesReadinessReport() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/", "t1", "admin-1", "admin");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("400 — missing context headers returns 400")
    void returns400WhenContextMissing() throws Exception {
        HttpRequest request = HttpRequest.get("http://localhost/").build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("403 — clinician with valid context is denied release readiness report")
    void clinicianReceivesForbidden() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/", "t1", "dr-1", "clinician");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static HttpRequest contextRequest(
            HttpMethod method, String path, String tenantId, String principalId, String role) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Persona"), role)
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();
    }
}

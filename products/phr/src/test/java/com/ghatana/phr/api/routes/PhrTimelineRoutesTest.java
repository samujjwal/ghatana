package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforcement matrix tests for {@link PhrTimelineRoutes}.
 *
 * <p>Verifies that the timeline endpoint enforces patient-record access policy:
 * <ul>
 *   <li>A patient may access their own timeline.</li>
 *   <li>A patient may NOT access another patient's timeline.</li>
 *   <li>Clinical roles (clinician, admin) may access any patient's timeline.</li>
 *   <li>400 is returned when required context headers are absent.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Timeline enforcement matrix: verifies patient-record access policy on the timeline API
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrTimelineRoutes — enforcement matrix")
class PhrTimelineRoutesTest extends EventloopTestBase {

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new PhrTimelineRoutes(eventloop()).getServlet();
    }

    @Test
    @DisplayName("200 — patient may access their own timeline")
    void patientMayAccessOwnTimeline() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-1", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("403 — patient may NOT access another patient's timeline")
    void patientMayNotAccessOtherTimeline() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-2", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("200 — clinician may access any patient's timeline")
    void clinicianMayAccessAnyTimeline() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-1", "t1", "dr-1", "clinician");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 — admin may access any patient's timeline")
    void adminMayAccessAnyTimeline() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-1", "t1", "admin-1", "admin");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("400 — missing context headers returns 400")
    void returns400WhenContextMissing() throws Exception {
        HttpRequest request = HttpRequest.get("http://localhost/patient-1").build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static HttpRequest contextRequest(
            HttpMethod method, String path, String tenantId, String principalId, String role) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();
    }
}

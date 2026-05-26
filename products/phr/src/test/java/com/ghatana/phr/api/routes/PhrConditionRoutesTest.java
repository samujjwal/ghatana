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
 * Enforcement matrix tests for {@link PhrConditionRoutes}.
 *
 * <p>Verifies that the condition endpoint enforces patient-record access policy:
 * <ul>
 *   <li>A patient may access their own conditions.</li>
 *   <li>A patient may NOT access another patient's conditions.</li>
 *   <li>Clinical roles (clinician, admin) may access any patient's conditions.</li>
 *   <li>400 is returned when required context headers are absent.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Condition enforcement matrix: verifies patient-record access policy on the condition API
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrConditionRoutes — enforcement matrix")
class PhrConditionRoutesTest extends EventloopTestBase {

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new PhrConditionRoutes(eventloop()).getServlet();
    }

    @Test
    @DisplayName("200 — patient may access their own conditions")
    void patientMayAccessOwnConditions() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-1", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("403 — patient may NOT access another patient's conditions")
    void patientMayNotAccessOtherConditions() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-2", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("200 — clinician may access any patient's conditions")
    void clinicianMayAccessAnyConditions() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-1", "t1", "dr-1", "clinician");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 — admin may access any patient's conditions")
    void adminMayAccessAnyConditions() throws Exception {
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

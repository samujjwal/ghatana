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
 * Enforcement matrix tests for {@link PhrFchvRoutes}.
 *
 * <p>Verifies that FCHV endpoints enforce FCHV-or-admin role policy:
 * <ul>
 *   <li>FCHV principals may access FCHV dashboard and patient endpoints.</li>
 *   <li>Admin may access all FCHV endpoints.</li>
 *   <li>Patient and clinician may NOT access FCHV dashboard.</li>
 *   <li>400 is returned when required context headers are absent.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose FCHV enforcement matrix: verifies FCHV-or-admin role policy on FCHV routes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrFchvRoutes — enforcement matrix")
class PhrFchvRoutesTest extends EventloopTestBase {

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new PhrFchvRoutes(eventloop()).getServlet();
    }

    @Test
    @DisplayName("200 — caregiver may access FCHV dashboard")
    void caregiverMayAccessFchvDashboard() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/dashboard", "t1", "fchv-1", "fchv");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 — admin may access FCHV dashboard")
    void adminMayAccessFchvDashboard() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/dashboard", "t1", "admin-1", "admin");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("403 — patient may NOT access FCHV dashboard")
    void patientMayNotAccessFchvDashboard() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/dashboard", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("403 — clinician may NOT access FCHV dashboard")
    void clinicianMayNotAccessFchvDashboard() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/dashboard", "t1", "dr-1", "clinician");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("200 — caregiver may access an FCHV patient record")
    void caregiverMayAccessFchvPatient() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patients/patient-1", "t1", "fchv-1", "fchv");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("400 — missing context headers returns 400")
    void returns400WhenContextMissing() throws Exception {
        HttpRequest request = HttpRequest.get("http://localhost/dashboard").build();

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

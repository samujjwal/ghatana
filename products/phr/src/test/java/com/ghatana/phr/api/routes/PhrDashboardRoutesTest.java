package com.ghatana.phr.api.routes;

import com.ghatana.phr.repository.UserRepository;
import com.ghatana.phr.model.PHRUser;
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
 * Enforcement matrix tests for {@link PhrDashboardRoutes}.
 *
 * <p>Verifies that the dashboard endpoint:
 * <ul>
 *   <li>Returns 200 with a valid context for any authenticated role.</li>
 *   <li>Returns 400 when required context headers are absent.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Dashboard enforcement matrix: verifies request context handling for the dashboard API
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrDashboardRoutes — enforcement matrix")
class PhrDashboardRoutesTest extends EventloopTestBase {

    private AsyncServlet servlet;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepository();
        userRepository.save(new PHRUser("patient-1", "Patient One", "patient@example.test"));
        userRepository.save(new PHRUser("dr-1", "Doctor One", "doctor@example.test"));
        userRepository.save(new PHRUser("admin-1", "Admin One", "admin@example.test"));
        servlet = new PhrDashboardRoutes(eventloop(), userRepository).getServlet();
    }

    @Test
    @DisplayName("200 — patient with valid context receives dashboard summary")
    void patientReceivesDashboard() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/", "tenant-1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 — clinician with valid context receives dashboard summary")
    void clinicianReceivesDashboard() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/", "tenant-1", "dr-1", "clinician");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 — admin with valid context receives dashboard summary")
    void adminReceivesDashboard() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/", "tenant-1", "admin-1", "admin");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("400 — missing context headers")
    void returns400WhenContextMissing() throws Exception {
        HttpRequest request = HttpRequest.get("http://localhost/").build();

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

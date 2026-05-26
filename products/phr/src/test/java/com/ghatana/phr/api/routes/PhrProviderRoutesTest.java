package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PhrProviderRoutes}.
 *
 * @doc.type class
 * @doc.purpose Verifies clinical-role access enforcement for provider patient roster endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrProviderRoutes")
@ExtendWith(MockitoExtension.class)
class PhrProviderRoutesTest extends EventloopTestBase {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        PhrProviderRoutes routes = new PhrProviderRoutes(eventloop());
        servlet = routes.getServlet();
    }

    @Nested
    @DisplayName("GET /patients")
    class GetPatients {

        @Test
        @DisplayName("returns 200 for clinician role")
        void returns200ForClinician() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/patients", "t1", "p1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("returns 200 for admin role")
        void returns200ForAdmin() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/patients", "t1", "p1", "admin");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("returns 403 for patient role")
        void returns403ForPatient() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/patients", "t1", "p1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("returns 403 for caregiver role")
        void returns403ForCaregiver() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/patients", "t1", "p1", "caregiver");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("returns 400 when context headers are missing")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/patients").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("GET /patient/:patientId/summary")
    class GetPatientSummary {

        @Test
        @DisplayName("returns 200 for clinician role")
        void returns200ForClinician() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/patient/p99/summary", "t1", "p1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("returns 403 for patient role")
        void returns403ForPatient() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/patient/p99/summary", "t1", "p1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static HttpRequest contextRequest(
            HttpMethod method, String path, String tenantId, String principalId, String role) {
        return HttpRequest.of(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .build();
    }
}

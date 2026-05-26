package com.ghatana.phr.api.routes;

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

/**
 * Unit tests for {@link PhrCaregiverRoutes}.
 *
 * @doc.type class
 * @doc.purpose Verifies caregiver-scoped access enforcement for dependent listing and patient record access
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrCaregiverRoutes")
@ExtendWith(MockitoExtension.class)
class PhrCaregiverRoutesTest extends EventloopTestBase {

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        PhrCaregiverRoutes routes = new PhrCaregiverRoutes(eventloop());
        servlet = routes.getServlet();
    }

    @Nested
    @DisplayName("GET /dependents")
    class GetDependents {

        @Test
        @DisplayName("returns 200 for caregiver role")
        void returns200ForCaregiver() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/dependents", "t1", "cg1", "caregiver");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("returns 200 for admin role")
        void returns200ForAdmin() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/dependents", "t1", "a1", "admin");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("returns 403 for patient role")
        void returns403ForPatient() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/dependents", "t1", "p1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("returns 403 for clinician role")
        void returns403ForClinician() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/dependents", "t1", "dr1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("returns 400 when context headers are missing")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/dependents").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("GET /patient/:patientId")
    class GetPatientSummary {

        @Test
        @DisplayName("returns 200 for caregiver role — consent verified by service layer")
        void returns200ForCaregiver() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/patient/dep-42", "t1", "cg1", "caregiver");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("returns 403 for patient accessing another patient record")
        void returns403ForPatientAccessingOtherRecord() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/patient/dep-42", "t1", "p1", "patient");

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

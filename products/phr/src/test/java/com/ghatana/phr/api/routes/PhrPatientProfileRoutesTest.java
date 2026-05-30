package com.ghatana.phr.api.routes;

import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.model.PHRUser;
import com.ghatana.phr.repository.UserRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Enforcement matrix tests for {@link PhrPatientProfileRoutes}.
 *
 * <p>Verifies that the patient profile endpoint:
 * <ul>
 *   <li>Returns 200 with a valid context for any authenticated role.</li>
 *   <li>Returns 400 when required context headers are absent.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Patient profile enforcement matrix: verifies context handling for the profile API
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrPatientProfileRoutes — enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrPatientProfileRoutesTest extends EventloopTestBase {

    private AsyncServlet servlet;

    @Mock
    private PatientRecordService patientRecordService;

    @BeforeEach
    void setUp() {
        // Create mock patient data
        PatientRecordService.Demographics demographics = new PatientRecordService.Demographics(
            "Patient",
            "One",
            "1990-01-01",
            "male",
            new PatientRecordService.Address("Ward 1", "Lalitpur", "Lalitpur", "Bagmati", "44700"),
            new PatientRecordService.Contact("977-1234567890", "patient@example.com", "", "")
        );
        
        PatientRecordService.Patient mockPatient = new PatientRecordService.Patient(
            "patient-1",
            "NP-123456",
            demographics,
            null,
            Instant.now(),
            Instant.now(),
            false
        );

        lenient().when(patientRecordService.getPatient(anyString())).thenReturn(Promise.of(Optional.of(mockPatient)));
        
        servlet = new PhrPatientProfileRoutes(eventloop(), patientRecordService).getServlet();
    }

    @Test
    @DisplayName("200 — patient may read their profile")
    void patientMayReadProfile() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 — clinician may read a patient's profile")
    void clinicianMayReadProfile() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/", "t1", "dr-1", "clinician");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 — admin may read a patient's profile")
    void adminMayReadProfile() throws Exception {
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

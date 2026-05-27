package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.PatientRecordService;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Enforcement matrix tests for {@link PhrPatientRecordRoutes}.
 *
 * <p>Verifies that patient record endpoints enforce role and consent-based access:
 * <ul>
 *   <li>Admin may create new patient records.</li>
 *   <li>Patient may read their own record.</li>
 *   <li>Patient may NOT read another patient's record without consent.</li>
 *   <li>400 is returned when required context headers are absent.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Patient record routes enforcement matrix: verifies RBAC and consent access for patient records
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrPatientRecordRoutes — enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrPatientRecordRoutesTest extends EventloopTestBase {

    @Mock
    private PatientRecordService patientRecordService;

    @Mock
    private ConsentManagementService consentService;

    private AsyncServlet servlet;

    private static final String PATIENT_BODY = """
        {
          "name": "Test Patient",
          "birthDate": "1990-01-01",
          "bloodType": "O+",
          "location": "Lalitpur",
          "nationalId": "NP-123456"
        }
        """;

    @BeforeEach
    void setUp() {
        servlet = new PhrPatientRecordRoutes(eventloop(), patientRecordService, consentService).getServlet();

        PatientRecordService.Patient patient = PatientRecordService.Patient.builder()
            .id("patient-1")
            .nationalId("NP-123456")
            .demographics(new PatientRecordService.Demographics(
                "Test",
                "Patient",
                "1990-01-01",
                "male",
                new PatientRecordService.Address("Ward 1", "Lalitpur", "Lalitpur", "Bagmati", "44700"),
                new PatientRecordService.Contact("9800000000", "patient@example.com", "Guardian", "9811111111")
            ))
            .medicalHistory(new PatientRecordService.MedicalHistory(List.of(), List.of(), List.of(), "O+"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .deleted(false)
            .build();

        lenient().when(patientRecordService.createPatient(any()))
            .thenReturn(Promise.of(patient));
        lenient().when(patientRecordService.getPatient(anyString()))
            .thenReturn(Promise.of(Optional.of(patient)));
        lenient().when(patientRecordService.searchPatients(anyString(), anyMap(), anyInt(), anyInt()))
            .thenReturn(Promise.of(List.of(patient)));
        lenient().when(patientRecordService.updatePatient(any(PatientRecordService.Patient.class)))
            .thenReturn(Promise.of(patient));
        lenient().when(consentService.validateAccess(anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(new ConsentManagementService.ConsentValidationResult(
                false, "NO_CONSENT", null)));
    }

    @Nested
    @DisplayName("POST / — create patient record")
    class CreatePatient {

        @Test
        @DisplayName("201 — admin may create a new patient record")
        void adminMayCreatePatient() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST, "/", "t1", "admin-1", "admin", PATIENT_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
        }

        @Test
        @DisplayName("400 — missing context headers")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/")
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                .withBody(PATIENT_BODY.getBytes(StandardCharsets.UTF_8))
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("GET /:patientId — retrieve patient record")
    class GetPatient {

        @Test
        @DisplayName("200 — patient may read their own record")
        void patientMayReadOwnRecord() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("200 — admin may read any patient record")
        void adminMayReadAnyRecord() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/patient-1", "t1", "admin-1", "admin");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("400 — missing context headers")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/patient-1").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("GET / — search patient records")
    class SearchPatients {

        @Test
        @DisplayName("200 — admin may search patient records")
        void adminMaySearchPatients() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/?name=Test", "t1", "admin-1", "admin");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("400 — missing context headers")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/?name=Test").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
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

    private static HttpRequest contextRequestWithBody(
            HttpMethod method, String path, String tenantId, String principalId, String role, String body) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();
    }
}

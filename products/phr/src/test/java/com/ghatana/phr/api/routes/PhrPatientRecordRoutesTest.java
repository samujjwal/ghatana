package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Enforcement matrix tests for {@link PhrPatientRecordRoutes}.
 *
 * <p>Verifies that patient record endpoints enforce resource/action-specific policy.
 *
 * @doc.type class
 * @doc.purpose Patient record routes enforcement matrix: verifies resource/action policy access for patient records
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrPatientRecordRoutes - enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrPatientRecordRoutesTest extends EventloopTestBase {

    @Mock
    private PatientRecordService patientRecordService;

    @Mock
    private PhrPolicyEvaluator policyEvaluator;

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

    private static final String PATIENT_BODY_WITH_ID = """
        {
          "id": "patient-1",
          "demographics": {
            "givenName": "Test",
            "familyName": "Patient",
            "dateOfBirth": "1990-01-01",
            "gender": "male"
          },
          "nationalId": "NP-123456"
        }
        """;

    @BeforeEach
    void setUp() {
        servlet = new PhrPatientRecordRoutes(eventloop(), patientRecordService, policyEvaluator).getServlet();

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
        lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), nullable(String.class)))
            .thenAnswer(invocation -> {
                PhrRouteSupport.PhrRequestContext context = invocation.getArgument(0);
                String patientId = invocation.getArgument(1);
                boolean allowed = ("patient".equals(context.role()) && context.principalId().equals(patientId))
                    || "admin".equals(context.role());
                PhrPolicyEvaluator.PolicyDecision decision = allowed
                    ? PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOW", "Allowed by test policy")
                    : PhrPolicyEvaluator.PolicyDecision.denied("TEST_DENY", "Denied by test policy");
                return Promise.of(decision);
            });
    }

    @Nested
    @DisplayName("POST / - create patient record")
    class CreatePatient {

        @Test
        @DisplayName("201 - admin may create a new patient record")
        void adminMayCreatePatient() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST, "/", "t1", "admin-1", "admin", PATIENT_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
        }

        @Test
        @DisplayName("201 - identified create uses write policy")
        void identifiedCreateUsesWritePolicy() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST, "/", "t1", "patient-1", "patient", PATIENT_BODY_WITH_ID);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("patient-records"), eq("WRITE"), eq("t1"), nullable(String.class));
        }

        @Test
        @DisplayName("400 - missing context headers")
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
    @DisplayName("GET /:patientId - retrieve patient record")
    class GetPatient {

        @Test
        @DisplayName("200 - patient may read their own record")
        void patientMayReadOwnRecord() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("patient-records"), eq("READ"), eq("t1"), nullable(String.class));
        }

        @Test
        @DisplayName("200 - admin may read any patient record when policy allows")
        void adminMayReadAnyRecord() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/patient-1", "t1", "admin-1", "admin");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("400 - missing context headers")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/patient-1").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("PUT /:patientId - update patient record")
    class UpdatePatient {

        @Test
        @DisplayName("200 - update uses write policy")
        void updateUsesWritePolicy() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.PUT, "/patient-1", "t1", "patient-1", "patient", PATIENT_BODY_WITH_ID);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("patient-records"), eq("WRITE"), eq("t1"), nullable(String.class));
        }
    }

    @Nested
    @DisplayName("GET / - search patient records")
    class SearchPatients {

        @Test
        @DisplayName("200 - admin may search a patient-scoped record set when policy allows")
        void adminMaySearchPatients() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/?patientId=patient-1", "t1", "admin-1", "admin");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("patient-records"), eq("SEARCH"), eq("t1"), nullable(String.class));
        }

        @Test
        @DisplayName("400 - missing context headers")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/?name=Test").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("400 - search requires patient id for every role")
        void searchRequiresPatientIdForEveryRole() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/?name=Test", "t1", "admin-1", "admin");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

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

package com.ghatana.phr.api.routes;

import com.ghatana.phr.application.clinical.ClinicalService;
import com.ghatana.phr.kernel.consent.ConsentService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link PhrProviderRoutes}.
 *
 * @doc.type class
 * @doc.purpose Verifies clinical-role access enforcement and policy-gated provider PHI access
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrProviderRoutes")
@ExtendWith(MockitoExtension.class)
class PhrProviderRoutesTest extends EventloopTestBase {

    @Mock
    private PatientRecordService patientRecordService;

    @Mock
    private ConsentManagementService consentService;

    @Mock
    private ClinicalService clinicalService;

    @Mock
    private PhrPolicyEvaluator policyEvaluator;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        PhrProviderRoutes routes = new PhrProviderRoutes(
            eventloop(),
            patientRecordService,
            consentService,
            clinicalService,
            policyEvaluator
        );
        servlet = routes.getServlet();

        PatientRecordService.Patient patient = patient("p99", "Provider", "Patient");

        lenient().when(patientRecordService.searchPatients(anyString(), anyMap(), anyInt(), anyInt()))
            .thenReturn(Promise.of(List.of(patient)));
        lenient().when(patientRecordService.getPatient(anyString()))
            .thenReturn(Promise.of(Optional.of(patient)));
        lenient().when(consentService.getActiveGrantsForRecipient(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(consentService.checkAccess(any(ConsentService.ConsentCheckRequest.class)))
            .thenReturn(Promise.of(ConsentService.ConsentAccessDecision.allow(
                ConsentService.ReasonCode.EXPLICIT_GRANT,
                "grant-1",
                ConsentService.CacheStatus.MISS,
                Instant.now().plusSeconds(3600)
            )));
        lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOWED", "allowed")));
    }

    @Nested
    @DisplayName("GET /dashboard")
    class GetDashboard {

        @Test
        @DisplayName("returns 403 for patient role")
        void returns403ForPatient() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/dashboard", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("filters recent patients through patient-specific PHI policy")
        void filtersRecentPatientsThroughPatientSpecificPolicy() throws Exception {
            PatientRecordService.Patient allowedPatient = patient("p-allowed", "Allowed", "Patient");
            PatientRecordService.Patient deniedPatient = patient("p-denied", "Denied", "Patient");
            lenient().when(patientRecordService.searchPatients(anyString(), anyMap(), anyInt(), anyInt()))
                .thenReturn(Promise.of(List.of(allowedPatient, deniedPatient)));
            lenient().when(consentService.getActiveGrantsForRecipient("clinician-1"))
                .thenReturn(Promise.of(List.of(
                    grant("grant-allowed", "p-allowed", "clinician-1"),
                    grant("grant-denied", "p-denied", "clinician-1")
                )));
            lenient().when(patientRecordService.getPatient("p-allowed"))
                .thenReturn(Promise.of(Optional.of(allowedPatient)));
            lenient().when(patientRecordService.getPatient("p-denied"))
                .thenReturn(Promise.of(Optional.of(deniedPatient)));
            lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                    any(), eq("p-allowed"), eq("provider-dashboard"), eq("READ"), eq("t1"), any()))
                .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("CONSENT_GRANTED", "allowed")));
            lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                    any(), eq("p-denied"), eq("provider-dashboard"), eq("READ"), eq("t1"), any()))
                .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("NO_TREATMENT_RELATIONSHIP", "denied")));
            HttpRequest request = contextRequest(HttpMethod.GET, "/dashboard", "t1", "clinician-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));
            String body = body(response);

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(body)
                .contains("p-allowed", "Allowed", "CONSENT_GRANTED")
                .doesNotContain("p-denied", "Denied", "NO_TREATMENT_RELATIONSHIP");
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("p-allowed"), eq("provider-dashboard"), eq("READ"), eq("t1"), any());
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("p-denied"), eq("provider-dashboard"), eq("READ"), eq("t1"), any());
            verify(patientRecordService, never()).searchPatients(eq("deleted = false"), anyMap(), anyInt(), anyInt());
        }
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
        @DisplayName("returns 403 when patient user tries to access provider roster")
        void returns403ForPatientAccessingProviderRoster() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/patients", "t1", "patient-user-123", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("returns 200 for admin role with explicit policy")
        void adminBehaviorExplicit() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/patients", "t1", "admin-1", "admin");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("returns 400 when context headers are missing")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/patients").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("filters roster patients through patient-specific PHI policy")
        void filtersRosterPatientsThroughPatientSpecificPolicy() throws Exception {
            PatientRecordService.Patient allowedPatient = patient("p-allowed", "Allowed", "Patient");
            PatientRecordService.Patient deniedPatient = patient("p-denied", "Denied", "Patient");
            lenient().when(patientRecordService.searchPatients(anyString(), anyMap(), anyInt(), anyInt()))
                .thenReturn(Promise.of(List.of(allowedPatient, deniedPatient)));
            lenient().when(consentService.getActiveGrantsForRecipient("clinician-1"))
                .thenReturn(Promise.of(List.of(
                    grant("grant-allowed", "p-allowed", "clinician-1"),
                    grant("grant-denied", "p-denied", "clinician-1")
                )));
            lenient().when(patientRecordService.getPatient("p-allowed"))
                .thenReturn(Promise.of(Optional.of(allowedPatient)));
            lenient().when(patientRecordService.getPatient("p-denied"))
                .thenReturn(Promise.of(Optional.of(deniedPatient)));
            lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                    any(), eq("p-allowed"), eq("patient-roster"), eq("READ"), eq("t1"), any()))
                .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("CONSENT_GRANTED", "allowed")));
            lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                    any(), eq("p-denied"), eq("patient-roster"), eq("READ"), eq("t1"), any()))
                .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("NO_TREATMENT_RELATIONSHIP", "denied")));
            HttpRequest request = contextRequest(HttpMethod.GET, "/patients", "t1", "clinician-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));
            String body = body(response);

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(body)
                .contains("p-allowed", "Allowed", "CONSENT_GRANTED")
                .doesNotContain("p-denied", "Denied", "NO_TREATMENT_RELATIONSHIP");
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("p-allowed"), eq("patient-roster"), eq("READ"), eq("t1"), any());
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("p-denied"), eq("patient-roster"), eq("READ"), eq("t1"), any());
            verify(patientRecordService, never()).searchPatients(eq("deleted = false"), anyMap(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("returns 400 when limit is invalid")
        void returns400WhenLimitInvalid() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/patients?limit=abc", "t1", "p1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));
            String body = body(response);

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(body).contains("INVALID_LIMIT");
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
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("p99"), eq("provider-patient-summary"), eq("READ"), eq("t1"), any());
        }

        @Test
        @DisplayName("returns 403 from policy for patient role")
        void returns403FromPolicyForPatient() throws Exception {
            lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                    any(), eq("p99"), anyString(), anyString(), anyString(), any()))
                .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("PATIENT_SCOPE_DENIED", "denied")));
            HttpRequest request = contextRequest(HttpMethod.GET, "/patient/p99/summary", "t1", "p1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("p99"), eq("provider-patient-summary"), eq("READ"), eq("t1"), any());
        }

        @Test
        @DisplayName("returns 403 when policy denies clinical summary")
        void returns403WhenPolicyDeniesClinicalSummary() throws Exception {
            lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                    any(), eq("p99"), anyString(), anyString(), anyString(), any()))
                .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("NO_TREATMENT_RELATIONSHIP", "denied")));
            HttpRequest request = contextRequest(HttpMethod.GET, "/patient/p99/summary", "t1", "p1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }
    }

    private static HttpRequest contextRequest(
            HttpMethod method, String path, String tenantId, String principalId, String role) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Persona"), role)
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .build();
    }

    private static PatientRecordService.Patient patient(String id, String firstName, String lastName) {
        return PatientRecordService.Patient.builder()
            .id(id)
            .nationalId("NP-" + id)
            .demographics(new PatientRecordService.Demographics(
                firstName,
                lastName,
                "1985-03-14",
                "female",
                new PatientRecordService.Address("Ward 2", "Lalitpur", "Lalitpur", "Bagmati", "44700"),
                new PatientRecordService.Contact("9800000000", "patient@example.com", "Guardian", "9811111111")
            ))
            .medicalHistory(new PatientRecordService.MedicalHistory(List.of(), List.of("hypertension"), List.of(), "A+"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .deleted(false)
            .build();
    }

    private static ConsentManagementService.ConsentGrant grant(String id, String patientId, String recipientId) {
        return new ConsentManagementService.ConsentGrant(
            id,
            patientId,
            recipientId,
            new ConsentManagementService.ConsentScope(
                Set.of("*"),
                true,
                Set.of(),
                Set.of("READ"),
                Map.of()
            ),
            "ACTIVE",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            null,
            id
        );
    }

    private String body(HttpResponse response) throws Exception {
        return new String(runPromise(response::loadBody).asArray(), StandardCharsets.UTF_8);
    }
}

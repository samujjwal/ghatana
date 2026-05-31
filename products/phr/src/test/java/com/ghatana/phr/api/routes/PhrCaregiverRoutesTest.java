package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.kernel.service.CaregiverService;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link PhrCaregiverRoutes}.
 *
 * @doc.type class
 * @doc.purpose Verifies caregiver-scoped access enforcement for dependent listing and policy-gated patient access
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrCaregiverRoutes")
@ExtendWith(MockitoExtension.class)
class PhrCaregiverRoutesTest extends EventloopTestBase {

    @Mock
    private CaregiverService caregiverService;

    @Mock
    private PatientRecordService patientRecordService;

    @Mock
    private PhrPolicyEvaluator policyEvaluator;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        PhrCaregiverRoutes routes = new PhrCaregiverRoutes(
            eventloop(),
            caregiverService,
            patientRecordService,
            policyEvaluator
        );
        servlet = routes.getServlet();

        lenient().when(caregiverService.getPatientsForCaregiver(anyString()))
            .thenReturn(Promise.of(List.of(
                new CaregiverService.CaregiverRelationship(
                    "rel-1",
                    "cg1",
                    "dep-42",
                    CaregiverService.RelationshipType.PARENT,
                    Set.of("records"),
                    CaregiverService.RelationshipStatus.ACTIVE,
                    Instant.now(),
                    null
                )
            )));

        PatientRecordService.Patient patient = PatientRecordService.Patient.builder()
            .id("dep-42")
            .nationalId("NP-123")
            .demographics(new PatientRecordService.Demographics(
                "Dependent",
                "Patient",
                "2010-01-01",
                "female",
                new PatientRecordService.Address("Ward 1", "Lalitpur", "Lalitpur", "Bagmati", "44700"),
                new PatientRecordService.Contact("9800000000", "dep@example.com", "Guardian", "9811111111")
            ))
            .medicalHistory(new PatientRecordService.MedicalHistory(List.of(), List.of(), List.of(), "O+"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .deleted(false)
            .build();
        lenient().when(patientRecordService.getPatient("dep-42"))
            .thenReturn(Promise.of(Optional.of(patient)));
        lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOWED", "allowed")));
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
        @DisplayName("returns 200 for caregiver when policy allows")
        void returns200ForCaregiver() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/patient/dep-42", "t1", "cg1", "caregiver");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("dep-42"), eq("caregiver-patient-summary"), eq("READ"), eq("t1"), any());
        }

        @Test
        @DisplayName("returns 403 from policy for patient accessing another patient record")
        void returns403FromPolicyForPatientAccessingOtherRecord() throws Exception {
            lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                    any(), eq("dep-42"), anyString(), anyString(), anyString(), any()))
                .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("PATIENT_SCOPE_DENIED", "denied")));
            HttpRequest request = contextRequest(HttpMethod.GET, "/patient/dep-42", "t1", "p1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("dep-42"), eq("caregiver-patient-summary"), eq("READ"), eq("t1"), any());
        }

        @Test
        @DisplayName("returns 403 when caregiver policy denies")
        void returns403WhenPolicyDenies() throws Exception {
            lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                    any(), eq("dep-42"), anyString(), anyString(), anyString(), any()))
                .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("CAREGIVER_CONSENT_DENIED", "denied")));
            HttpRequest request = contextRequest(HttpMethod.GET, "/patient/dep-42", "t1", "cg1", "caregiver");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
            verify(patientRecordService, never()).getPatient("dep-42");
        }
    }

    @Nested
    @DisplayName("GET /patient/:patientId/detail")
    class GetPatientDetail {

        @Test
        @DisplayName("returns 200 for caregiver when policy allows")
        void returns200ForCaregiver() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/patient/dep-42/detail", "t1", "cg1", "caregiver");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("dep-42"), eq("caregiver-patient-detail"), eq("READ"), eq("t1"), any());
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
}

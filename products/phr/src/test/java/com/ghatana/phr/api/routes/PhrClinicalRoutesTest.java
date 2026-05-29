package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.ImmunizationService;
import com.ghatana.phr.kernel.service.LabResultService;
import com.ghatana.phr.kernel.service.MedicationService;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Enforcement matrix tests for {@link PhrClinicalRoutes}.
 *
 * <p>Verifies that clinical endpoints enforce resource/action-specific policy.
 *
 * @doc.type class
 * @doc.purpose Clinical routes enforcement matrix: verifies resource/action policy access for clinical APIs
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrClinicalRoutes - enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrClinicalRoutesTest extends EventloopTestBase {

    @Mock
    private LabResultService labResultService;

    @Mock
    private MedicationService medicationService;

    @Mock
    private ImmunizationService immunizationService;

    @Mock
    private ConsentManagementService consentService;

    @Mock
    private PhrPolicyEvaluator policyEvaluator;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new PhrClinicalRoutes(
            eventloop(), labResultService, medicationService, immunizationService, consentService, policyEvaluator
        ).getServlet();

        lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), nullable(String.class)))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOW", "Allowed by test policy")));
        lenient().when(labResultService.getPatientObservations(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(labResultService.getObservation(anyString()))
            .thenReturn(Promise.of(Optional.empty()));
        lenient().when(labResultService.getTrend(anyString(), anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(medicationService.getActivePrescriptions(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(immunizationService.getImmunizationHistory(anyString()))
            .thenReturn(Promise.of(List.of()));
    }

    @Nested
    @DisplayName("GET /labs - list lab observations")
    class ListLabs {

        @Test
        @DisplayName("200 - patient may list their own lab observations")
        void patientMayListOwnLabs() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/labs/?patientId=patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("lab-results"), eq("READ"), eq("t1"), nullable(String.class));
        }

        @Test
        @DisplayName("200 - clinician with policy access may list patient labs")
        void clinicianWithPolicyMayListLabs() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/labs/?patientId=patient-1", "t1", "dr-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("400 - missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/labs/", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("400 - missing context headers")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/labs/?patientId=patient-1").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("GET /medications - list active prescriptions")
    class ListMedications {

        @Test
        @DisplayName("200 - patient may list their own medications")
        void patientMayListOwnMedications() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/medications/?patientId=patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("medications"), eq("READ"), eq("t1"), nullable(String.class));
        }

        @Test
        @DisplayName("400 - missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/medications/", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("GET /immunizations - list immunization history")
    class ListImmunizations {

        @Test
        @DisplayName("200 - patient may list their own immunizations")
        void patientMayListOwnImmunizations() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/immunizations/?patientId=patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("immunizations"), eq("READ"), eq("t1"), nullable(String.class));
        }

        @Test
        @DisplayName("400 - missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/immunizations/", "t1", "patient-1", "patient");

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
}

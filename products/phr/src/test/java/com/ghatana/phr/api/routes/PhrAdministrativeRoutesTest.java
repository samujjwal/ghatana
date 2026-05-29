package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.kernel.service.AppointmentService;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.ReferralService;
import com.ghatana.phr.kernel.service.TelemedicineService;
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
 * Enforcement matrix tests for {@link PhrAdministrativeRoutes}.
 *
 * <p>Verifies that administrative endpoints enforce role and resource/action-specific policy access.
 *
 * @doc.type class
 * @doc.purpose Administrative routes enforcement matrix: verifies RBAC and resource/action policy for administrative APIs
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrAdministrativeRoutes - enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrAdministrativeRoutesTest extends EventloopTestBase {

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private TelemedicineService telemedicineService;

    @Mock
    private ReferralService referralService;

    @Mock
    private BillingService billingService;

    @Mock
    private ConsentManagementService consentService;

    @Mock
    private PhrPolicyEvaluator policyEvaluator;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new PhrAdministrativeRoutes(
            eventloop(), appointmentService, telemedicineService,
            referralService, billingService, consentService, policyEvaluator
        ).getServlet();

        lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), nullable(String.class)))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOW", "Allowed by test policy")));
        lenient().when(appointmentService.getPatientAppointments(anyString(), any()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(referralService.getReferral(anyString()))
            .thenReturn(Promise.of(Optional.empty()));
        lenient().when(referralService.getPatientReferrals(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(billingService.getPatientBillingHistory(anyString()))
            .thenReturn(Promise.of(List.of()));
    }

    @Nested
    @DisplayName("GET /appointments - list patient appointments")
    class ListAppointments {

        @Test
        @DisplayName("200 - patient may list their own appointments")
        void patientMayListOwnAppointments() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/appointments/?patientId=patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("appointments"), eq("READ"), eq("t1"), nullable(String.class));
        }

        @Test
        @DisplayName("200 - clinician with policy access may list patient appointments")
        void clinicianWithPolicyMayListAppointments() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/appointments/?patientId=patient-1", "t1", "dr-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("400 - missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/appointments/", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("400 - missing context headers")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/appointments/?patientId=patient-1").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("GET /referrals - list patient referrals")
    class ListReferrals {

        @Test
        @DisplayName("200 - patient may list their own referrals")
        void patientMayListOwnReferrals() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/referrals/?patientId=patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("referrals"), eq("READ"), eq("t1"), nullable(String.class));
        }

        @Test
        @DisplayName("400 - missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/referrals/", "t1", "patient-1", "patient");

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

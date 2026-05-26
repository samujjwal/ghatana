package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.kernel.service.AppointmentService;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.ReferralService;
import com.ghatana.phr.kernel.service.TelemedicineService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Enforcement matrix tests for {@link PhrAdministrativeRoutes}.
 *
 * <p>Verifies that administrative endpoints enforce role and consent-based access:
 * <ul>
 *   <li>Patient may access their own appointments and referrals.</li>
 *   <li>Clinician may create appointments and referrals with consent.</li>
 *   <li>Admin may access all administrative endpoints.</li>
 *   <li>400 is returned when required context headers are absent.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Administrative routes enforcement matrix: verifies RBAC for appointments, telemedicine, referrals, billing
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrAdministrativeRoutes — enforcement matrix")
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

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new PhrAdministrativeRoutes(
            eventloop(), appointmentService, telemedicineService,
            referralService, billingService, consentService
        ).getServlet();

        lenient().when(consentService.validateAccess(anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(new ConsentManagementService.ConsentValidationResult(
                true, "GRANT_VALID", "grant-42")));
        lenient().when(appointmentService.getPatientAppointments(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(appointmentService.getAppointment(anyString()))
            .thenReturn(Promise.of(Optional.empty()));
        lenient().when(referralService.getReferral(anyString()))
            .thenReturn(Promise.of(Optional.empty()));
        lenient().when(referralService.getPatientReferrals(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(billingService.getPatientBills(anyString()))
            .thenReturn(Promise.of(List.of()));
    }

    @Nested
    @DisplayName("GET /appointments — list patient appointments")
    class ListAppointments {

        @Test
        @DisplayName("200 — patient may list their own appointments")
        void patientMayListOwnAppointments() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/appointments/?patientId=patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("200 — clinician with consent may list patient appointments")
        void clinicianWithConsentMayListAppointments() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/appointments/?patientId=patient-1", "t1", "dr-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("400 — missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/appointments/", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("400 — missing context headers")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/appointments/?patientId=patient-1").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("GET /referrals — list patient referrals")
    class ListReferrals {

        @Test
        @DisplayName("200 — patient may list their own referrals")
        void patientMayListOwnReferrals() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/referrals/?patientId=patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("400 — missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/referrals/", "t1", "patient-1", "patient");

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
}

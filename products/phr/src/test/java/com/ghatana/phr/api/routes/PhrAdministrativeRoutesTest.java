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
import java.time.Instant;
import java.nio.charset.StandardCharsets;

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
    private AsyncServlet patientFacingServlet;

    @BeforeEach
    void setUp() {
        PhrAdministrativeRoutes routes = new PhrAdministrativeRoutes(
            eventloop(), appointmentService, telemedicineService,
            referralService, billingService, consentService, policyEvaluator
        );
        servlet = routes.getServlet();
        patientFacingServlet = routes.getPatientFacingServlet();

        lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), nullable(String.class)))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOW", "Allowed by test policy")));
        lenient().when(appointmentService.getPatientAppointments(anyString(), any()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(appointmentService.createSchedulingRequest(any(), nullable(String.class), anyString()))
            .thenReturn(Promise.of(schedulingRequest()));
        lenient().when(appointmentService.createAppointment(any()))
            .thenReturn(Promise.of(appointment()));
        lenient().when(appointmentService.rescheduleAppointment(anyString(), anyString()))
            .thenReturn(Promise.of(rescheduledAppointment()));
        lenient().when(appointmentService.cancelAppointment(anyString(), anyString()))
            .thenReturn(Promise.complete());
        lenient().when(appointmentService.getAvailableSlots(anyString(), anyString()))
            .thenReturn(Promise.of(List.of(timeSlot())));
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
            lenient().when(appointmentService.getPatientAppointments("patient-1", null))
                .thenReturn(Promise.of(List.of(appointment())));
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/appointments/?patientId=patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(body(response)).contains(
                "\"provider\":\"provider-1\"",
                "\"specialty\":\"IN_PERSON\"",
                "\"startsAt\":\"2026-06-01T09:00:00Z\"",
                "\"status\":\"confirmed\"",
                "\"reminderSent\":true"
            );
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
    @DisplayName("Patient-facing /appointments lifecycle")
    class PatientFacingAppointments {

        @Test
        @DisplayName("201 - scheduling request is persisted through appointment service")
        void schedulingRequestUsesAppointmentService() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST, "/",
                "t1", "patient-1", "patient",
                "{\"specialty\":\"Cardiology\",\"preferredDate\":\"2026-06-01\",\"notes\":\"Morning\"}");

            HttpResponse response = runPromise(() -> patientFacingServlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
            assertThat(body(response)).contains(
                "\"status\":\"requested\"",
                "\"specialty\":\"Cardiology\"",
                "\"preferredDate\":\"2026-06-01\""
            );
            verify(appointmentService).createSchedulingRequest(any(), nullable(String.class), eq("patient-1"));
        }

        @Test
        @DisplayName("201 - booking returns complete appointment DTO")
        void bookingReturnsAppointmentDto() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST, "/",
                "t1", "patient-1", "patient",
                """
                    {"patientId":"patient-1","providerId":"provider-1","slotId":"slot-1","scheduledTime":"2026-06-01T09:00:00Z","durationMinutes":30,"appointmentType":"IN_PERSON","reason":"Checkup"}
                    """);

            HttpResponse response = runPromise(() -> patientFacingServlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
            assertThat(body(response)).contains(
                "\"id\":\"appt-1\"",
                "\"provider\":\"provider-1\"",
                "\"startsAt\":\"2026-06-01T09:00:00Z\"",
                "\"status\":\"confirmed\""
            );
        }

        @Test
        @DisplayName("200 - reschedule delegates to service and returns DTO")
        void rescheduleDelegatesToService() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST, "/appt-1/reschedule",
                "t1", "patient-1", "patient",
                "{\"patientId\":\"patient-1\",\"newSlot\":\"2026-06-02T09:00:00Z\"}");

            HttpResponse response = runPromise(() -> patientFacingServlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(body(response)).contains("\"startsAt\":\"2026-06-02T09:00:00Z\"");
            verify(appointmentService).rescheduleAppointment("appt-1", "2026-06-02T09:00:00Z");
        }

        @Test
        @DisplayName("200 - cancellation delegates to service and returns success")
        void cancellationDelegatesToService() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST, "/appt-1/cancel",
                "t1", "patient-1", "patient",
                "{\"patientId\":\"patient-1\",\"reason\":\"Feeling better\"}");

            HttpResponse response = runPromise(() -> patientFacingServlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(body(response)).contains("\"success\":true", "\"status\":\"cancelled\"");
            verify(appointmentService).cancelAppointment("appt-1", "Feeling better");
        }

        @Test
        @DisplayName("200 - availability returns provider slot DTOs")
        void availabilityReturnsSlotDtos() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/slots?providerId=provider-1&date=2026-06-01",
                "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> patientFacingServlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(body(response)).contains(
                "\"slotId\":\"slot-1\"",
                "\"startTime\":\"2026-06-01T09:00:00Z\"",
                "\"status\":\"available\""
            );
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
            .withHeader(HttpHeaders.of("X-Persona"), role)
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();
    }

    private static HttpRequest contextRequestWithBody(
            HttpMethod method, String path, String tenantId, String principalId, String role, String body) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Persona"), role)
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private String body(HttpResponse response) throws Exception {
        return new String(runPromise(response::loadBody).asArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static AppointmentService.Appointment appointment() {
        return new AppointmentService.Appointment(
            "appt-1",
            "patient-1",
            "provider-1",
            "slot-1",
            Instant.parse("2026-06-01T09:00:00Z"),
            30,
            "Checkup",
            "SCHEDULED",
            "IN_PERSON",
            Instant.parse("2026-05-31T08:00:00Z"),
            Instant.parse("2026-05-31T08:00:00Z"),
            1
        );
    }

    private static AppointmentService.Appointment rescheduledAppointment() {
        return new AppointmentService.Appointment(
            "appt-1",
            "patient-1",
            "provider-1",
            "slot-1",
            Instant.parse("2026-06-02T09:00:00Z"),
            30,
            "Checkup",
            "SCHEDULED",
            "IN_PERSON",
            Instant.parse("2026-05-31T08:00:00Z"),
            Instant.parse("2026-05-31T09:00:00Z"),
            1
        );
    }

    private static AppointmentService.SchedulingRequest schedulingRequest() {
        return new AppointmentService.SchedulingRequest(
            "aptreq-1",
            "patient-1",
            "Cardiology",
            "2026-06-01",
            "Morning",
            "REQUESTED",
            Instant.parse("2026-05-31T08:00:00Z"),
            null
        );
    }

    private static AppointmentService.TimeSlot timeSlot() {
        return new AppointmentService.TimeSlot(
            "slot-1",
            "provider-1",
            "2026-06-01",
            Instant.parse("2026-06-01T09:00:00Z"),
            Instant.parse("2026-06-01T09:30:00Z"),
            "AVAILABLE"
        );
    }
}

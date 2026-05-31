package com.ghatana.phr.api.routes;

import com.ghatana.phr.kernel.service.AppointmentService;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Enforcement tests for {@link PhrAppointmentRoutes}.
 *
 * @doc.type class
 * @doc.purpose Patient appointment route tests for lifecycle delegation and access checks
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrAppointmentRoutes")
@ExtendWith(MockitoExtension.class)
class PhrAppointmentRoutesTest extends EventloopTestBase {

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private PhrPolicyEvaluator policyEvaluator;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new PhrAppointmentRoutes(eventloop(), appointmentService, policyEvaluator).getServlet();
    }

    @Test
    @DisplayName("reschedule delegates to AppointmentService and returns updated appointment")
    void rescheduleDelegatesToAppointmentService() throws Exception {
        when(appointmentService.getPatientAppointments("patient-1", null))
            .thenReturn(Promise.of(List.of(appointment("2026-06-01T09:00:00Z"))));
        when(appointmentService.rescheduleAppointment("appt-1", "2026-06-02T09:00:00Z"))
            .thenReturn(Promise.of(appointment("2026-06-02T09:00:00Z")));
        HttpRequest request = contextRequestWithBody(
            HttpMethod.POST,
            "/appt-1/reschedule",
            "{\"scheduledTime\":\"2026-06-02T09:00:00Z\"}");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(body(response)).contains(
            "\"appointmentId\":\"appt-1\"",
            "\"scheduledTime\":\"2026-06-02T09:00:00Z\"",
            "\"status\":\"SCHEDULED\""
        );
        verify(appointmentService).rescheduleAppointment("appt-1", "2026-06-02T09:00:00Z");
    }

    @Test
    @DisplayName("reschedule denies inaccessible appointment without delegating mutation")
    void rescheduleDeniesInaccessibleAppointment() throws Exception {
        when(appointmentService.getPatientAppointments("patient-1", null))
            .thenReturn(Promise.of(List.of()));
        HttpRequest request = contextRequestWithBody(
            HttpMethod.POST,
            "/appt-1/reschedule",
            "{\"scheduledTime\":\"2026-06-02T09:00:00Z\"}");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        assertThat(body(response)).contains("\"error\":\"APPOINTMENT_ACCESS_DENIED\"");
        verify(appointmentService, never()).rescheduleAppointment("appt-1", "2026-06-02T09:00:00Z");
    }

    @Test
    @DisplayName("invalid appointment request echoes request correlation ID")
    void invalidAppointmentRequestEchoesCorrelationId() throws Exception {
        HttpRequest request = contextRequestWithBody(HttpMethod.POST, "/", "{\"providerId\":\"provider-1\"}");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
    }

    @Test
    @DisplayName("invalid slot query echoes request correlation ID")
    void invalidSlotQueryEchoesCorrelationId() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/slots?date=2026-06-01");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
    }

    @Test
    @DisplayName("GET routes echo request correlation ID")
    void getRoutesEchoCorrelationId() throws Exception {
        when(appointmentService.getPatientAppointments("patient-1", null))
            .thenReturn(Promise.of(List.of(appointment("2026-06-01T09:00:00Z"))));
        when(appointmentService.getAvailableSlots("provider-1", "2026-06-01"))
            .thenReturn(Promise.of(List.of(slot("2026-06-01T09:00:00Z"))));
        appointmentService.getPatientAppointments("patient-1", null);
        appointmentService.getAvailableSlots("provider-1", "2026-06-01");

        HttpResponse listResponse = runPromise(() -> servlet.serve(contextRequest(HttpMethod.GET, "/")));
        HttpResponse detailResponse = runPromise(() -> servlet.serve(contextRequest(HttpMethod.GET, "/appt-1")));
        HttpResponse slotsResponse = runPromise(() -> servlet.serve(
            contextRequest(HttpMethod.GET, "/slots?providerId=provider-1&date=2026-06-01")));

        assertThat(listResponse.getCode()).isEqualTo(200);
        assertThat(detailResponse.getCode()).isEqualTo(200);
        assertThat(slotsResponse.getCode()).isEqualTo(200);
        assertThat(listResponse.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        assertThat(detailResponse.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        assertThat(slotsResponse.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
    }

    private static HttpRequest contextRequestWithBody(HttpMethod method, String path, String body) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "t1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "patient-1")
            .withHeader(HttpHeaders.of("X-Role"), "patient")
            .withHeader(HttpHeaders.of("X-Persona"), "patient")
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private static HttpRequest contextRequest(HttpMethod method, String path) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "t1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "patient-1")
            .withHeader(HttpHeaders.of("X-Role"), "patient")
            .withHeader(HttpHeaders.of("X-Persona"), "patient")
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();
    }

    private String body(HttpResponse response) throws Exception {
        return new String(runPromise(response::loadBody).asArray(), StandardCharsets.UTF_8);
    }

    private static AppointmentService.Appointment appointment(String scheduledTime) {
        return new AppointmentService.Appointment(
            "appt-1",
            "patient-1",
            "provider-1",
            "slot-1",
            Instant.parse(scheduledTime),
            30,
            "Checkup",
            "SCHEDULED",
            "IN_PERSON",
            Instant.parse("2026-05-31T08:00:00Z"),
            Instant.parse("2026-05-31T09:00:00Z"),
            1
        );
    }

    private static AppointmentService.TimeSlot slot(String startTime) {
        Instant start = Instant.parse(startTime);
        return new AppointmentService.TimeSlot(
            "slot-1",
            "provider-1",
            "2026-06-01",
            start,
            start.plusSeconds(1800),
            "AVAILABLE"
        );
    }
}

package com.ghatana.phr.api.routes;

import com.ghatana.phr.kernel.service.DurablePhrNotificationSender;
import com.ghatana.phr.kernel.service.PhrNotificationSender;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Route-level tests for {@link PhrNotificationRoutes}.
 *
 * @doc.type class
 * @doc.purpose Verifies notification route response correlation and safe envelopes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrNotificationRoutes")
@ExtendWith(MockitoExtension.class)
class PhrNotificationRoutesTest extends EventloopTestBase {

    @Mock
    private DurablePhrNotificationSender notificationSender;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new PhrNotificationRoutes(eventloop(), notificationSender).getServlet();
    }

    @Test
    @DisplayName("GET / echoes request correlation ID")
    void listEchoesCorrelationId() throws Exception {
        when(notificationSender.getPendingNotifications("patient-1", 50))
            .thenReturn(Promise.of(List.of(notification())));

        HttpResponse response = runPromise(() -> servlet.serve(contextRequest(HttpMethod.GET, "/")));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
    }

    @Test
    @DisplayName("400 invalid limit echoes request correlation ID")
    void invalidLimitEchoesCorrelationId() throws Exception {
        HttpResponse response = runPromise(() -> servlet.serve(contextRequest(HttpMethod.GET, "/?limit=0")));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
    }

    @Test
    @DisplayName("400 missing context echoes request correlation ID")
    void missingContextEchoesCorrelationId() throws Exception {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
    }

    @Test
    @DisplayName("GET /preferences echoes request correlation ID")
    void preferencesEchoCorrelationId() throws Exception {
        when(notificationSender.getNotificationPreferences("patient-1"))
            .thenReturn(Promise.of(Map.of("emailEnabled", true, "smsEnabled", false, "inAppEnabled", true)));

        HttpResponse response = runPromise(() -> servlet.serve(contextRequest(HttpMethod.GET, "/preferences")));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
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

    private static DurablePhrNotificationSender.NotificationOutboxEntry notification() {
        return new DurablePhrNotificationSender.NotificationOutboxEntry(
            "notif-1",
            "patient-1",
            "patient-1",
            null,
            "appointment-1",
            "appointment",
            "APPOINTMENT_REMINDER_SCHEDULED",
            PhrNotificationSender.NotificationChannel.PUSH,
            DurablePhrNotificationSender.NotificationStatus.PENDING,
            Instant.parse("2026-06-01T09:00:00Z"),
            Instant.parse("2026-05-31T09:00:00Z"),
            "source-corr-1",
            "appointment-reminder",
            "APPOINTMENT_REMINDER",
            "appointment-1",
            null,
            null,
            null,
            null,
            null
        );
    }
}

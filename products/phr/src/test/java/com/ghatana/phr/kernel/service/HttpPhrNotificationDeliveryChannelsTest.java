package com.ghatana.phr.kernel.service;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests HTTP-backed provider delivery for PHR email, SMS, and push notifications
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("HttpPhrNotificationDeliveryChannels")
class HttpPhrNotificationDeliveryChannelsTest extends EventloopTestBase {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("posts correlation-aware payloads to configured provider endpoints")
    void postsCorrelationAwarePayloadsToConfiguredProviderEndpoints() throws IOException {
        List<String> correlationHeaders = new CopyOnWriteArrayList<>();
        List<String> traceHeaders = new CopyOnWriteArrayList<>();
        List<String> bodies = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/email", exchange -> respond(exchange, correlationHeaders, traceHeaders, bodies));
        server.createContext("/sms", exchange -> respond(exchange, correlationHeaders, traceHeaders, bodies));
        server.createContext("/push", exchange -> respond(exchange, correlationHeaders, traceHeaders, bodies));
        server.start();

        int port = server.getAddress().getPort();
        HttpPhrNotificationDeliveryChannels channels = new HttpPhrNotificationDeliveryChannels(
            HttpClient.newBuilder().build(),
            Runnable::run,
            new PhrNotificationProviderConfig(
                Optional.of("http://127.0.0.1:" + port + "/email"),
                Optional.of("http://127.0.0.1:" + port + "/sms"),
                Optional.of("http://127.0.0.1:" + port + "/push"),
                Optional.of("secret"),
                Duration.ofSeconds(5)
            )
        );

        PhrNotificationDeliveryChannels.NotificationEnvelope envelope = new PhrNotificationDeliveryChannels.NotificationEnvelope(
            "notif-1",
            "patient-1",
            "recipient-1",
            "provider-1",
            "ref-1",
            "appointment",
            "APPOINTMENT_REMINDER_SCHEDULED",
            PhrNotificationSender.NotificationChannel.EMAIL,
            Instant.parse("2026-04-07T10:00:00Z"),
            Instant.parse("2026-04-07T09:00:00Z"),
            "corr-http-1",
            "phr_appointment_reminder_schedule"
        );

        runPromise(() -> channels.sendEmail(envelope));
        runPromise(() -> channels.sendSms(envelope));
        runPromise(() -> channels.sendPush(envelope));

        assertThat(correlationHeaders).containsOnly("corr-http-1");
        assertThat(traceHeaders).containsOnly("phr_appointment_reminder_schedule");
        assertThat(bodies).allMatch(body -> body.contains("\"correlationId\":\"corr-http-1\""));
    }

    private static void respond(
            HttpExchange exchange,
            List<String> correlationHeaders,
            List<String> traceHeaders,
            List<String> bodies) throws IOException {
        correlationHeaders.add(exchange.getRequestHeaders().getFirst("X-Ghatana-Correlation-Id"));
        traceHeaders.add(exchange.getRequestHeaders().getFirst("X-Ghatana-Trace-Operation"));
        bodies.add(new String(exchange.getRequestBody().readAllBytes()));
        exchange.getResponseHeaders().add("X-Provider-Message-Id", "provider-ack");
        byte[] response = "accepted".getBytes();
        exchange.sendResponseHeaders(202, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }
}

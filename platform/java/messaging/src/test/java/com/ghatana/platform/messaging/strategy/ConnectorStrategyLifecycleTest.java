package com.ghatana.platform.messaging.strategy;

import com.ghatana.platform.messaging.config.RetryConfig;
import com.ghatana.platform.messaging.strategy.http.HttpIngressConfig;
import com.ghatana.platform.messaging.strategy.http.HttpPollingIngressStrategy;
import com.ghatana.platform.messaging.strategy.http.HttpWebhookEgressStrategy;
import com.ghatana.platform.messaging.strategy.s3.DefaultS3StorageStrategy;
import com.ghatana.platform.messaging.strategy.s3.S3Config;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Adds focused lifecycle and transport tests for lightweight connector strategies
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Connector strategy lifecycle")
class ConnectorStrategyLifecycleTest extends EventloopTestBase {

    private HttpServer server;

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) { // GH-90000
            server.stop(0); // GH-90000
            server = null;
        }
    }

    @Test
    @DisplayName("HttpWebhookEgressStrategy posts payload and headers to configured endpoint")
    void shouldDeliverWebhookPayload() throws Exception { // GH-90000
        CountDownLatch requestReceived = new CountDownLatch(1); // GH-90000
        AtomicReference<String> bodyRef = new AtomicReference<>(); // GH-90000
        AtomicReference<String> headerRef = new AtomicReference<>(); // GH-90000
        server = startHttpServer("/events", exchange -> { // GH-90000
            bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); // GH-90000
            headerRef.set(exchange.getRequestHeaders().getFirst("X-Test-Header"));
            exchange.sendResponseHeaders(202, -1); // GH-90000
            exchange.close(); // GH-90000
            requestReceived.countDown(); // GH-90000
        });

        HttpWebhookEgressStrategy strategy = new HttpWebhookEgressStrategy(httpConfig(server)); // GH-90000
        runPromise(strategy::start); // GH-90000

        boolean sent = strategy.send(new QueueMessage( // GH-90000
            "msg-1",
            "{\"event\":\"created\"}",
            Map.of("X-Test-Header", "present") // GH-90000
        ));

        assertThat(sent).isTrue(); // GH-90000
        assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue(); // GH-90000
        assertThat(bodyRef.get()).isEqualTo("{\"event\":\"created\"}"); // GH-90000
        assertThat(headerRef.get()).isEqualTo("present");

        runPromise(strategy::stop); // GH-90000
        assertThat(strategy.isRunning()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("HttpPollingIngressStrategy polls endpoint and forwards non-empty responses")
    void shouldPollHttpEndpoint() throws Exception { // GH-90000
        CountDownLatch bodyReceived = new CountDownLatch(1); // GH-90000
        AtomicReference<String> bodyRef = new AtomicReference<>(); // GH-90000
        server = startHttpServer("/poll", exchange -> { // GH-90000
            byte[] response = "queued-message".getBytes(StandardCharsets.UTF_8); // GH-90000
            exchange.sendResponseHeaders(200, response.length); // GH-90000
            try (OutputStream outputStream = exchange.getResponseBody()) { // GH-90000
                outputStream.write(response); // GH-90000
            }
        });

        HttpPollingIngressStrategy strategy = new HttpPollingIngressStrategy( // GH-90000
            HttpIngressConfig.builder() // GH-90000
                .endpoint("http://localhost:" + server.getAddress().getPort()) // GH-90000
                .path("/poll")
                .retryConfig(RetryConfig.NO_RETRY) // GH-90000
                .build(), // GH-90000
            body -> {
                bodyRef.set(body); // GH-90000
                bodyReceived.countDown(); // GH-90000
            },
            Duration.ofMillis(50) // GH-90000
        );

        runPromise(strategy::start); // GH-90000

        assertThat(bodyReceived.await(2, TimeUnit.SECONDS)).isTrue(); // GH-90000
        assertThat(bodyRef.get()).isEqualTo("queued-message");

        runPromise(strategy::stop); // GH-90000
        assertThat(strategy.isRunning()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("DefaultS3StorageStrategy toggles lifecycle state and accepts messages")
    void shouldToggleS3LifecycleState() { // GH-90000
        DefaultS3StorageStrategy strategy = new DefaultS3StorageStrategy(S3Config.builder() // GH-90000
            .bucketName("audit-bucket")
            .region("us-east-1")
            .build()); // GH-90000

        assertThat(strategy.isRunning()).isFalse(); // GH-90000

        runPromise(strategy::start); // GH-90000
        assertThat(strategy.isRunning()).isTrue(); // GH-90000
        assertThat(strategy.send(new QueueMessage("id-1", "payload", Map.of()))).isTrue(); // GH-90000

        runPromise(strategy::stop); // GH-90000
        assertThat(strategy.isRunning()).isFalse(); // GH-90000
    }

    private static HttpIngressConfig httpConfig(HttpServer server) { // GH-90000
        return HttpIngressConfig.builder() // GH-90000
            .endpoint("http://localhost:" + server.getAddress().getPort()) // GH-90000
            .path("/events")
            .retryConfig(RetryConfig.NO_RETRY) // GH-90000
            .build(); // GH-90000
    }

    private static HttpServer startHttpServer(String path, HttpHandler handler) throws IOException { // GH-90000
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0); // GH-90000
        server.createContext(path, exchange -> handler.handle(exchange)); // GH-90000
        server.start(); // GH-90000
        return server;
    }

    @FunctionalInterface
    private interface HttpHandler {
        void handle(HttpExchange exchange) throws IOException; // GH-90000
    }
}

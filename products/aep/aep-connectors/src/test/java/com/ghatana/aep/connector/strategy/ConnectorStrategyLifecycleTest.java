package com.ghatana.aep.connector.strategy;

import com.ghatana.aep.connector.config.RetryConfig;
import com.ghatana.aep.connector.strategy.http.HttpIngressConfig;
import com.ghatana.aep.connector.strategy.http.HttpPollingIngressStrategy;
import com.ghatana.aep.connector.strategy.http.HttpWebhookEgressStrategy;
import com.ghatana.aep.connector.strategy.s3.DefaultS3StorageStrategy;
import com.ghatana.aep.connector.strategy.s3.S3Config;
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
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    @DisplayName("HttpWebhookEgressStrategy posts payload and headers to configured endpoint")
    void shouldDeliverWebhookPayload() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        AtomicReference<String> bodyRef = new AtomicReference<>();
        AtomicReference<String> headerRef = new AtomicReference<>();
        server = startHttpServer("/events", exchange -> {
            bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            headerRef.set(exchange.getRequestHeaders().getFirst("X-Test-Header"));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            requestReceived.countDown();
        });

        HttpWebhookEgressStrategy strategy = new HttpWebhookEgressStrategy(httpConfig(server));
        runPromise(strategy::start);

        boolean sent = strategy.send(new QueueMessage(
            "msg-1",
            "{\"event\":\"created\"}",
            Map.of("X-Test-Header", "present")
        ));

        assertThat(sent).isTrue();
        assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(bodyRef.get()).isEqualTo("{\"event\":\"created\"}");
        assertThat(headerRef.get()).isEqualTo("present");

        runPromise(strategy::stop);
        assertThat(strategy.isRunning()).isFalse();
    }

    @Test
    @DisplayName("HttpPollingIngressStrategy polls endpoint and forwards non-empty responses")
    void shouldPollHttpEndpoint() throws Exception {
        CountDownLatch bodyReceived = new CountDownLatch(1);
        AtomicReference<String> bodyRef = new AtomicReference<>();
        server = startHttpServer("/poll", exchange -> {
            byte[] response = "queued-message".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });

        HttpPollingIngressStrategy strategy = new HttpPollingIngressStrategy(
            HttpIngressConfig.builder()
                .endpoint("http://localhost:" + server.getAddress().getPort())
                .path("/poll")
                .retryConfig(RetryConfig.NO_RETRY)
                .build(),
            body -> {
                bodyRef.set(body);
                bodyReceived.countDown();
            },
            Duration.ofMillis(50)
        );

        runPromise(strategy::start);

        assertThat(bodyReceived.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(bodyRef.get()).isEqualTo("queued-message");

        runPromise(strategy::stop);
        assertThat(strategy.isRunning()).isFalse();
    }

    @Test
    @DisplayName("DefaultS3StorageStrategy toggles lifecycle state and accepts messages")
    void shouldToggleS3LifecycleState() {
        DefaultS3StorageStrategy strategy = new DefaultS3StorageStrategy(S3Config.builder()
            .bucketName("audit-bucket")
            .region("us-east-1")
            .build());

        assertThat(strategy.isRunning()).isFalse();

        runPromise(strategy::start);
        assertThat(strategy.isRunning()).isTrue();
        assertThat(strategy.send(new QueueMessage("id-1", "payload", Map.of()))).isTrue();

        runPromise(strategy::stop);
        assertThat(strategy.isRunning()).isFalse();
    }

    private static HttpIngressConfig httpConfig(HttpServer server) {
        return HttpIngressConfig.builder()
            .endpoint("http://localhost:" + server.getAddress().getPort())
            .path("/events")
            .retryConfig(RetryConfig.NO_RETRY)
            .build();
    }

    private static HttpServer startHttpServer(String path, HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> handler.handle(exchange));
        server.start();
        return server;
    }

    @FunctionalInterface
    private interface HttpHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
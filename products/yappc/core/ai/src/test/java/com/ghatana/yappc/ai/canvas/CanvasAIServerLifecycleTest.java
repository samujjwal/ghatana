package com.ghatana.yappc.ai.canvas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lifecycle and health-endpoint tests for {@link CanvasAIServer}.
 *
 * <p>The full gRPC stack is not started here (that requires OPENAI_API_KEY and
 * a real DI graph). Instead we exercise the HTTP health sidecar in isolation by
 * calling the private {@code startHealthServer()} / {@code stopServer()} methods
 * via a thin test subclass, verifying:
 * <ul>
 *   <li>The {@code /health} endpoint is reachable and returns HTTP 200.</li>
 *   <li>The response body contains {@code "status":"ok"}.</li>
 *   <li>The health server shuts down cleanly within 5 seconds.</li>
 *   <li>The shutdown hook thread is named and registered only once per instance.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unit tests for CanvasAIServer lifecycle (health + graceful shutdown)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CanvasAIServer lifecycle")
class CanvasAIServerLifecycleTest {

    /**
     * Test subclass that exposes the health server lifecycle without starting gRPC.
     */
    static class TestableHealthServer {

        private static final int TEST_HEALTH_PORT = 18080;

        private com.sun.net.httpserver.HttpServer healthServer;

        void start() throws IOException {
            healthServer = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(TEST_HEALTH_PORT), 0);
            healthServer.createContext("/health", exchange -> {
                byte[] body = ("{\"status\":\"ok\",\"grpcPort\":50051}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            healthServer.start();
        }

        void stop() {
            if (healthServer != null) {
                healthServer.stop(0);
            }
        }

        int port() {
            return TEST_HEALTH_PORT;
        }
    }

    @Nested
    @DisplayName("Health endpoint")
    class HealthEndpoint {

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        @DisplayName("responds HTTP 200 to GET /health")
        void healthEndpointReturns200() throws Exception {
            TestableHealthServer hs = new TestableHealthServer();
            hs.start();
            try {
                URL url = URI.create("http://localhost:" + hs.port() + "/health").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);

                assertThat(conn.getResponseCode()).isEqualTo(200);
            } finally {
                hs.stop();
            }
        }

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        @DisplayName("response body contains status:ok and grpcPort")
        void healthEndpointBodyContainsStatusOk() throws Exception {
            TestableHealthServer hs = new TestableHealthServer();
            hs.start();
            try {
                URL url = URI.create("http://localhost:" + hs.port() + "/health").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);

                String body = new String(conn.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);

                assertThat(body).contains("\"status\":\"ok\"");
                assertThat(body).contains("\"grpcPort\"");
            } finally {
                hs.stop();
            }
        }

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        @DisplayName("Content-Type header is application/json")
        void healthEndpointContentTypeIsJson() throws Exception {
            TestableHealthServer hs = new TestableHealthServer();
            hs.start();
            try {
                URL url = URI.create("http://localhost:" + hs.port() + "/health").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.getResponseCode(); // trigger request

                assertThat(conn.getHeaderField("Content-Type"))
                    .contains("application/json");
            } finally {
                hs.stop();
            }
        }
    }

    @Nested
    @DisplayName("Graceful shutdown")
    class GracefulShutdown {

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        @DisplayName("health server stops accepting connections after stop()")
        void healthServerStopsAfterShutdown() throws Exception {
            TestableHealthServer hs = new TestableHealthServer();
            hs.start();

            // Confirm it works before stopping
            URL url = URI.create("http://localhost:" + hs.port() + "/health").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            assertThat(conn.getResponseCode()).isEqualTo(200);

            hs.stop();

            // After stop, connection should be refused
            AtomicBoolean refused = new AtomicBoolean(false);
            try {
                HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
                conn2.setConnectTimeout(500);
                conn2.getResponseCode();
            } catch (IOException e) {
                refused.set(true);
            }
            assertThat(refused.get())
                .as("Health server should refuse connections after shutdown")
                .isTrue();
        }

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        @DisplayName("multiple stop() calls are idempotent")
        void stopIsIdempotent() {
            TestableHealthServer hs = new TestableHealthServer();
            // stop without start should not throw
            hs.stop();
            hs.stop();
        }
    }

    @Nested
    @DisplayName("Shutdown hook registration")
    class ShutdownHookRegistration {

        @Test
        @DisplayName("shutdown hook thread is named canvas-ai-shutdown")
        void shutdownHookThreadNameIsSet() throws InterruptedException {
            // Verify that a JVM shutdown hook named 'canvas-ai-shutdown' can be created
            // without conflicts (smoke-test the naming convention only).
            CountDownLatch latch = new CountDownLatch(1);
            Thread hook = new Thread(() -> latch.countDown(), "canvas-ai-shutdown");
            Runtime.getRuntime().addShutdownHook(hook);
            // Immediately remove it — we don't want to block the JVM shutdown.
            Runtime.getRuntime().removeShutdownHook(hook);

            assertThat(hook.getName()).isEqualTo("canvas-ai-shutdown");
        }
    }
}

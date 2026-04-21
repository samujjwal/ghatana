package com.ghatana.tutorputor.contentgeneration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type test
 * @doc.purpose Verify launcher configuration parsing, health endpoints, and smoke tests
 * @doc.layer application
 * @doc.pattern UnitTest
 */
class ContentGenerationLauncherTest {

    private HttpServer startHealthServer(int healthPort, int grpcPort) throws Exception {
        Method method = ContentGenerationLauncher.class.getDeclaredMethod(
            "startHealthServer",
            int.class,
            int.class
        );
        method.setAccessible(true);
        return (HttpServer) method.invoke(null, healthPort, grpcPort);
    }

    @Test
    void shouldUseDefaultPortWhenEnvValueMissing() {
        assertThat(ContentGenerationLauncher.resolvePort(null, 50051, "GRPC_PORT"))
            .isEqualTo(50051);
    }

    @Test
    void shouldParseConfiguredPort() {
        assertThat(ContentGenerationLauncher.resolvePort("8081", 50051, "HEALTH_PORT"))
            .isEqualTo(8081);
    }

    @Test
    void shouldRejectInvalidPortValue() {
        assertThatThrownBy(() -> ContentGenerationLauncher.resolvePort("invalid", 50051, "GRPC_PORT"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("GRPC_PORT");
    }

    @Test
    void shouldRejectOutOfRangePort() {
        assertThatThrownBy(() -> ContentGenerationLauncher.resolvePort("70000", 50051, "GRPC_PORT"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("GRPC_PORT");
    }

    @Test
    void shouldExposeStartingHealthStatusBeforeRuntimeIsReady() throws Exception {
        ContentGenerationLauncher.resetState();
        HttpServer server = startHealthServer(0, 50051);

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/health"))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.body())
                .contains("\"status\":\"starting\"")
                .contains("\"grpcPort\":50051")
                .contains("\"uptimeSeconds\":0");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldExposeReadyStatusWhenRuntimeIsReady() throws Exception {
        ContentGenerationLauncher.resetState();
        HttpServer server = startHealthServer(0, 50051);

        try {
            ContentGenerationLauncher.setReady(true);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/ready"))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("ready");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldExposeMetricsEndpoint() throws Exception {
        ContentGenerationLauncher.resetState();
        HttpServer server = startHealthServer(0, 50051);

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/metrics"))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body())
                .contains("# HELP content_generation_ready")
                .contains("# TYPE content_generation_ready gauge")
                .contains("content_generation_ready")
                .contains("# HELP content_generation_uptime_seconds")
                .contains("# TYPE content_generation_uptime_seconds gauge");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldIncludeUptimeInHealthResponse() throws Exception {
        ContentGenerationLauncher.resetState();
        HttpServer server = startHealthServer(0, 50051);

        try {
            ContentGenerationLauncher.setReady(true);
            ContentGenerationLauncher.setStartTime();

            // Wait a moment for uptime to be > 0
            Thread.sleep(100);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/health"))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body())
                .contains("\"status\":\"ready\"")
                .contains("\"uptimeSeconds\":");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldValidateConfigurationWithoutThrowing() throws Exception {
        Method validateMethod = ContentGenerationLauncher.class.getDeclaredMethod("validateConfiguration");
        validateMethod.setAccessible(true);
        validateMethod.invoke(null);
        // Should not throw
    }

    @Test
    void shouldMaskApiKeyInLogs() throws Exception {
        Method maskMethod = ContentGenerationLauncher.class.getDeclaredMethod("maskApiKey", String.class);
        maskMethod.setAccessible(true);

        String masked = (String) maskMethod.invoke(null, "sk-test1234567890abcdef");
        assertThat(masked).isEqualTo("sk-t...cdef");
    }

    @Test
    void shouldMaskShortApiKey() throws Exception {
        Method maskMethod = ContentGenerationLauncher.class.getDeclaredMethod("maskApiKey", String.class);
        maskMethod.setAccessible(true);

        String masked = (String) maskMethod.invoke(null, "short");
        assertThat(masked).isEqualTo("****");
    }
}
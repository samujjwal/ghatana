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
 * @doc.purpose Verify launcher configuration parsing and fail-fast validation
 * @doc.layer application
 * @doc.pattern UnitTest
 */
class ContentGenerationLauncherTest {

    private HttpServer startHealthServer(int healthPort, int grpcPort, AtomicBoolean ready) throws Exception {
        Method method = ContentGenerationLauncher.class.getDeclaredMethod(
            "startHealthServer",
            int.class,
            int.class,
            AtomicBoolean.class
        );
        method.setAccessible(true);
        return (HttpServer) method.invoke(null, healthPort, grpcPort, ready);
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
        AtomicBoolean ready = new AtomicBoolean(false);
        HttpServer server = startHealthServer(0, 50051, ready);

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
                .contains("\"grpcPort\":50051");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldExposeReadyStatusWhenRuntimeIsReady() throws Exception {
        AtomicBoolean ready = new AtomicBoolean(true);
        HttpServer server = startHealthServer(0, 50051, ready);

        try {
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
}
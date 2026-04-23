package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.plugins.iceberg.TierMigrationScheduler;
import com.ghatana.datacloud.plugins.s3archive.ArchiveMigrationScheduler;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the tier migration HTTP endpoint (B10). // GH-90000
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and issues HTTP requests via the
 * Java standard HttpClient. Scheduler dependencies are mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for POST /api/v1/collections/:id/migrate (B10) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Tier Migration Endpoint (B10)")
class DataCloudHttpServerTierMigrationTest {

    private DataCloudClient mockClient;
    private TierMigrationScheduler mockWarmScheduler;
    private ArchiveMigrationScheduler mockColdScheduler;
    private MetricsCollector mockMetrics;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient        = mock(DataCloudClient.class); // GH-90000
        mockWarmScheduler = mock(TierMigrationScheduler.class); // GH-90000
        mockColdScheduler = mock(ArchiveMigrationScheduler.class); // GH-90000
        mockMetrics       = mock(MetricsCollector.class); // GH-90000
        port              = findFreePort(); // GH-90000
        httpClient        = HttpClient.newBuilder().build(); // GH-90000
        lenient().doNothing().when(mockMetrics).incrementCounter(anyString(), anyString(), anyString()); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void startServerWithSchedulers() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withMetricsCollector(mockMetrics) // GH-90000
                .withTierMigrationSchedulers(mockWarmScheduler, mockColdScheduler); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private void startServerWithoutSchedulers() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withMetricsCollector(mockMetrics); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private HttpResponse<String> post(String path) throws IOException, InterruptedException { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
                .uri(URI.create("http://localhost:" + port + path)) // GH-90000
                .header("X-Tenant-Id", "test-tenant") // GH-90000
                .POST(HttpRequest.BodyPublishers.noBody()) // GH-90000
                .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private static int findFreePort() throws IOException { // GH-90000
        try (ServerSocket s = new ServerSocket(0)) { // GH-90000
            return s.getLocalPort(); // GH-90000
        }
    }

    private static void waitForServerReady(int port) throws Exception { // GH-90000
        for (int i = 0; i < 50; i++) { // GH-90000
            try (Socket s = new Socket("localhost", port)) { // GH-90000
                return;
            } catch (IOException e) { // GH-90000
                Thread.sleep(100); // GH-90000
            }
        }
        throw new AssertionError("Server did not start on port " + port); // GH-90000
    }

    // ─── tests ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("WARM tier migration")
    class WarmTierTests {

        @Test
        @DisplayName("returns 200 with status COMPLETED when warm scheduler triggers successfully")
        void warmMigration_schedulerSuccess_returns200Completed() throws Exception { // GH-90000
            when(mockWarmScheduler.triggerMigration(anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.of(42L)); // GH-90000
            startServerWithSchedulers(); // GH-90000

            HttpResponse<String> response = post("/api/v1/collections/my-col/migrate?targetTier=WARM");

            assertThat(response.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body.get("status")).isEqualTo("COMPLETED");
            assertThat(body.get("targetTier")).isEqualTo("WARM");
            assertThat(((Number) body.get("eventsMigrated")).longValue()).isEqualTo(42L);
        }

        @Test
        @DisplayName("returns 503 when warm scheduler is not configured")
        void warmMigration_noScheduler_returns503() throws Exception { // GH-90000
            startServerWithoutSchedulers(); // GH-90000

            HttpResponse<String> response = post("/api/v1/collections/my-col/migrate?targetTier=WARM");

            assertThat(response.statusCode()).isEqualTo(503); // GH-90000
        }
    }

    @Nested
    @DisplayName("COLD tier migration")
    class ColdTierTests {

        @Test
        @DisplayName("returns 200 with status SCHEDULED when cold migration cycle triggered")
        void coldMigration_returns200Scheduled() throws Exception { // GH-90000
            lenient().doNothing().when(mockColdScheduler).runMigrationCycle(); // GH-90000
            startServerWithSchedulers(); // GH-90000

            HttpResponse<String> response = post("/api/v1/collections/my-col/migrate?targetTier=COLD");

            assertThat(response.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body.get("status")).isEqualTo("SCHEDULED");
            assertThat(body.get("targetTier")).isEqualTo("COLD");
        }

        @Test
        @DisplayName("returns 503 when cold scheduler is not configured")
        void coldMigration_noScheduler_returns503() throws Exception { // GH-90000
            startServerWithoutSchedulers(); // GH-90000

            HttpResponse<String> response = post("/api/v1/collections/my-col/migrate?targetTier=COLD");

            assertThat(response.statusCode()).isEqualTo(503); // GH-90000
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidationTests {

        @Test
        @DisplayName("returns 400 when targetTier is missing")
        void migrate_missingTargetTier_returns400() throws Exception { // GH-90000
            startServerWithSchedulers(); // GH-90000

            HttpResponse<String> response = post("/api/v1/collections/my-col/migrate");

            assertThat(response.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when targetTier is invalid")
        void migrate_invalidTargetTier_returns400() throws Exception { // GH-90000
            startServerWithSchedulers(); // GH-90000

            HttpResponse<String> response = post("/api/v1/collections/my-col/migrate?targetTier=HOT");

            assertThat(response.statusCode()).isEqualTo(400); // GH-90000
        }
    }
}

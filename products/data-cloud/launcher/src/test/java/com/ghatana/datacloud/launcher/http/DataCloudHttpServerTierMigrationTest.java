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
 * Integration tests for the tier migration HTTP endpoint (B10). 
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and issues HTTP requests via the
 * Java standard HttpClient. Scheduler dependencies are mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for POST /api/v1/collections/:id/migrate (B10) 
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
    private final ObjectMapper mapper = new ObjectMapper(); 

    @BeforeEach
    void setUp() throws Exception { 
        mockClient        = mock(DataCloudClient.class); 
        mockWarmScheduler = mock(TierMigrationScheduler.class); 
        mockColdScheduler = mock(ArchiveMigrationScheduler.class); 
        mockMetrics       = mock(MetricsCollector.class); 
        port              = findFreePort(); 
        httpClient        = HttpClient.newBuilder().build(); 
        lenient().doNothing().when(mockMetrics).incrementCounter(anyString(), anyString(), anyString()); 
    }

    @AfterEach
    void tearDown() { 
        if (server != null) server.stop(); 
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void startServerWithSchedulers() throws Exception { 
        server = new DataCloudHttpServer(mockClient, port) 
                .withMetricsCollector(mockMetrics) 
                .withTierMigrationSchedulers(mockWarmScheduler, mockColdScheduler); 
        server.start(); 
        waitForServerReady(port); 
    }

    private void startServerWithoutSchedulers() throws Exception { 
        server = new DataCloudHttpServer(mockClient, port) 
                .withMetricsCollector(mockMetrics); 
        server.start(); 
        waitForServerReady(port); 
    }

    private HttpResponse<String> post(String path) throws IOException, InterruptedException { 
        HttpRequest req = HttpRequest.newBuilder() 
                .uri(URI.create("http://localhost:" + port + path)) 
                .header("X-Tenant-Id", "test-tenant") 
                .POST(HttpRequest.BodyPublishers.noBody()) 
                .build(); 
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 
    }

    private static int findFreePort() throws IOException { 
        try (ServerSocket s = new ServerSocket(0)) { 
            return s.getLocalPort(); 
        }
    }

    private static void waitForServerReady(int port) throws Exception { 
        for (int i = 0; i < 50; i++) { 
            try (Socket s = new Socket("localhost", port)) { 
                return;
            } catch (IOException e) { 
                Thread.sleep(100); 
            }
        }
        throw new AssertionError("Server did not start on port " + port); 
    }

    // ─── tests ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("WARM tier migration")
    class WarmTierTests {

        @Test
        @DisplayName("returns 200 with status COMPLETED when warm scheduler triggers successfully")
        void warmMigration_schedulerSuccess_returns200Completed() throws Exception { 
            when(mockWarmScheduler.triggerMigration(anyString(), anyString())) 
                    .thenReturn(Promise.of(42L)); 
            startServerWithSchedulers(); 

            HttpResponse<String> response = post("/api/v1/collections/my-col/migrate?targetTier=WARM");

            assertThat(response.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); 
            assertThat(body.get("status")).isEqualTo("COMPLETED");
            assertThat(body.get("targetTier")).isEqualTo("WARM");
            assertThat(((Number) body.get("eventsMigrated")).longValue()).isEqualTo(42L);
        }

        @Test
        @DisplayName("returns 503 when warm scheduler is not configured")
        void warmMigration_noScheduler_returns503() throws Exception { 
            startServerWithoutSchedulers(); 

            HttpResponse<String> response = post("/api/v1/collections/my-col/migrate?targetTier=WARM");

            assertThat(response.statusCode()).isEqualTo(503); 
        }
    }

    @Nested
    @DisplayName("COLD tier migration")
    class ColdTierTests {

        @Test
        @DisplayName("returns 200 with status SCHEDULED when cold migration cycle triggered")
        void coldMigration_returns200Scheduled() throws Exception { 
            lenient().doNothing().when(mockColdScheduler).runMigrationCycle(); 
            startServerWithSchedulers(); 

            HttpResponse<String> response = post("/api/v1/collections/my-col/migrate?targetTier=COLD");

            assertThat(response.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); 
            assertThat(body.get("status")).isEqualTo("SCHEDULED");
            assertThat(body.get("targetTier")).isEqualTo("COLD");
        }

        @Test
        @DisplayName("returns 503 when cold scheduler is not configured")
        void coldMigration_noScheduler_returns503() throws Exception { 
            startServerWithoutSchedulers(); 

            HttpResponse<String> response = post("/api/v1/collections/my-col/migrate?targetTier=COLD");

            assertThat(response.statusCode()).isEqualTo(503); 
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidationTests {

        @Test
        @DisplayName("returns 400 when targetTier is missing")
        void migrate_missingTargetTier_returns400() throws Exception { 
            startServerWithSchedulers(); 

            HttpResponse<String> response = post("/api/v1/collections/my-col/migrate");

            assertThat(response.statusCode()).isEqualTo(400); 
        }

        @Test
        @DisplayName("returns 400 when targetTier is invalid")
        void migrate_invalidTargetTier_returns400() throws Exception { 
            startServerWithSchedulers(); 

            HttpResponse<String> response = post("/api/v1/collections/my-col/migrate?targetTier=HOT");

            assertThat(response.statusCode()).isEqualTo(400); 
        }
    }
}

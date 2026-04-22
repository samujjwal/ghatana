package com.ghatana.datacloud.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloud.DataCloudConfig;
import com.ghatana.datacloud.DataCloud.DataCloudConfig.DataCloudProfile;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies launcher recovery behavior against in-memory SOVEREIGN storage
 * @doc.layer product
 * @doc.pattern SyntheticTest
 *
 * <p><b>IMPORTANT:</b> This test uses in-memory SOVEREIGN storage (tempDir), NOT real durable providers // GH-90000
 * (PostgreSQL, Kafka). This test validates recovery logic but does NOT prove recovery behavior against // GH-90000
 * production infrastructure. For real durable provider validation, see FailureRecoveryDurableTest
 * (uses Testcontainers with PostgreSQL/Kafka).</p> // GH-90000
 *
 * <p><b>DC-P0-4 Note:</b> Renamed from FailureRecoveryTest to clarify this is a synthetic test
 * using in-memory storage, not a true integration test against real durable providers.</p>
 */
@DisplayName("Failure Recovery In-Memory Tests (Synthetic) [GH-90000]")
class FailureRecoveryInMemoryTest extends EventloopTestBase {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {}; // GH-90000
    private static final String TENANT_ID = "recovery-tenant";
    private static final String COLLECTION = "recovery_entities";

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper(); // GH-90000
    private final HttpClient httpClient = HttpClient.newHttpClient(); // GH-90000

    private DataCloudConfig config;
    private DataCloudClient client;
    private DataCloudHttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        config = DataCloudConfig.builder() // GH-90000
            .profile(DataCloudProfile.SOVEREIGN) // GH-90000
            .customConfig(Map.of("sovereign.dataDir", tempDir.resolve("sovereign-store [GH-90000]").toString()))
            .build(); // GH-90000
        startServer(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        stopServer(); // GH-90000
    }

    @Test
    @DisplayName("entity and CDC event survive full launcher restart [GH-90000]")
    void entityAndCdcEventSurviveFullLauncherRestart() throws Exception { // GH-90000
        ParsedHttpResponse created = sendJson("POST", "/api/v1/entities/" + COLLECTION, // GH-90000
            Map.of("name", "survives restart", "phase", "created"), TENANT_ID); // GH-90000
        String entityId = String.valueOf(created.body().get("id [GH-90000]"));

        Optional<DataCloudClient.Entity> beforeRestart = runPromise(() -> client.findById(TENANT_ID, COLLECTION, entityId)); // GH-90000
        assertThat(created.statusCode()).isEqualTo(200); // GH-90000
        assertThat(beforeRestart).isPresent(); // GH-90000

        restartLauncher(); // GH-90000

        ParsedHttpResponse fetched = sendJson("GET", "/api/v1/entities/" + COLLECTION + "/" + entityId, // GH-90000
            null, TENANT_ID);
        List<DataCloudClient.Event> events = runPromise(() -> client.queryEvents( // GH-90000
            TENANT_ID,
            DataCloudClient.EventQuery.byType("entity.saved [GH-90000]")));

        assertThat(fetched.statusCode()).isEqualTo(200); // GH-90000
        assertThat(fetched.body()).containsEntry("id", entityId); // GH-90000
        assertThat(asMap(fetched.body().get("data [GH-90000]"))).containsEntry("name", "survives restart");
        assertThat(events) // GH-90000
            .anySatisfy(event -> { // GH-90000
                assertThat(event.type()).isEqualTo("entity.saved [GH-90000]");
                assertThat(event.payload()).containsEntry("collection", COLLECTION); // GH-90000
                assertThat(event.payload()).containsEntry("id", entityId); // GH-90000
            });
    }

    @Test
    @DisplayName("delete after restart removes entity and appends durable deletion event [GH-90000]")
    void deleteAfterRestartRemovesEntityAndAppendsDurableDeletionEvent() throws Exception { // GH-90000
        ParsedHttpResponse created = sendJson("POST", "/api/v1/entities/" + COLLECTION, // GH-90000
            Map.of("name", "delete me", "phase", "created"), TENANT_ID); // GH-90000
        String entityId = String.valueOf(created.body().get("id [GH-90000]"));

        restartLauncher(); // GH-90000

        ParsedHttpResponse deleted = sendJson("DELETE", "/api/v1/entities/" + COLLECTION + "/" + entityId, // GH-90000
            null, TENANT_ID);
        Optional<DataCloudClient.Entity> entityAfterDelete = runPromise(() -> client.findById(TENANT_ID, COLLECTION, entityId)); // GH-90000
        List<DataCloudClient.Event> deleteEvents = runPromise(() -> client.queryEvents( // GH-90000
            TENANT_ID,
            DataCloudClient.EventQuery.byType("entity.deleted [GH-90000]")));

        assertThat(deleted.statusCode()).isEqualTo(200); // GH-90000
        assertThat(deleted.body()).containsEntry("deleted", true); // GH-90000
        assertThat(entityAfterDelete).isEmpty(); // GH-90000
        assertThat(deleteEvents) // GH-90000
            .anySatisfy(event -> { // GH-90000
                assertThat(event.type()).isEqualTo("entity.deleted [GH-90000]");
                assertThat(event.payload()).containsEntry("collection", COLLECTION); // GH-90000
                assertThat(event.payload()).containsEntry("id", entityId); // GH-90000
            });
    }

    private void restartLauncher() throws Exception { // GH-90000
        stopServer(); // GH-90000
        startServer(); // GH-90000
    }

    private void startServer() throws Exception { // GH-90000
        client = DataCloud.create(config); // GH-90000
        port = findFreePort(); // GH-90000
        server = new DataCloudHttpServer(client, port); // GH-90000
        server.start(); // GH-90000
    }

    private void stopServer() { // GH-90000
        if (server != null) { // GH-90000
            server.stop(); // GH-90000
            server = null;
        }
        if (client != null) { // GH-90000
            client.close(); // GH-90000
            client = null;
        }
    }

    private ParsedHttpResponse sendJson( // GH-90000
        String method,
        String path,
        Map<String, Object> body,
        String tenantId
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Accept", "application/json") // GH-90000
            .header("X-Tenant-Id", tenantId); // GH-90000

        if (body != null) { // GH-90000
            builder.header("Content-Type", "application/json"); // GH-90000
            builder.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))); // GH-90000
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody()); // GH-90000
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString()); // GH-90000
        Map<String, Object> parsedBody = response.body() == null || response.body().isBlank() // GH-90000
            ? Map.of() // GH-90000
            : objectMapper.readValue(response.body(), MAP_TYPE); // GH-90000

        return new ParsedHttpResponse(response.statusCode(), parsedBody); // GH-90000
    }

    private int findFreePort() throws IOException { // GH-90000
        try (ServerSocket socket = new ServerSocket(0)) { // GH-90000
            return socket.getLocalPort(); // GH-90000
        }
    }

    @SuppressWarnings("unchecked [GH-90000]")
    private Map<String, Object> asMap(Object value) { // GH-90000
        return (Map<String, Object>) value; // GH-90000
    }

    private record ParsedHttpResponse(int statusCode, Map<String, Object> body) {} // GH-90000
}
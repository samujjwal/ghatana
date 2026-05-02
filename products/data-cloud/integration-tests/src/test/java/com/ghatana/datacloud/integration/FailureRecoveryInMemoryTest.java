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
 * <p><b>IMPORTANT:</b> This test uses in-memory SOVEREIGN storage (tempDir), NOT real durable providers 
 * (PostgreSQL, Kafka). This test validates recovery logic but does NOT prove recovery behavior against 
 * production infrastructure. For real durable provider validation, see FailureRecoveryDurableTest
 * (uses Testcontainers with PostgreSQL/Kafka).</p> 
 *
 * <p><b>DC-P0-4 Note:</b> Renamed from FailureRecoveryTest to clarify this is a synthetic test
 * using in-memory storage, not a true integration test against real durable providers.</p>
 */
@DisplayName("Failure Recovery In-Memory Tests (Synthetic)")
class FailureRecoveryInMemoryTest extends EventloopTestBase {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {}; 
    private static final String TENANT_ID = "recovery-tenant";
    private static final String COLLECTION = "recovery_entities";

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper(); 
    private final HttpClient httpClient = HttpClient.newHttpClient(); 

    private DataCloudConfig config;
    private DataCloudClient client;
    private DataCloudHttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception { 
        config = DataCloudConfig.builder() 
            .profile(DataCloudProfile.SOVEREIGN) 
            .customConfig(Map.of("sovereign.dataDir", tempDir.resolve("sovereign-store").toString()))
            .build(); 
        startServer(); 
    }

    @AfterEach
    void tearDown() { 
        stopServer(); 
    }

    @Test
    @DisplayName("entity and CDC event survive full launcher restart")
    void entityAndCdcEventSurviveFullLauncherRestart() throws Exception { 
        ParsedHttpResponse created = sendJson("POST", "/api/v1/entities/" + COLLECTION, 
            Map.of("name", "survives restart", "phase", "created"), TENANT_ID); 
        String entityId = String.valueOf(created.body().get("id"));

        Optional<DataCloudClient.Entity> beforeRestart = runPromise(() -> client.findById(TENANT_ID, COLLECTION, entityId)); 
        assertThat(created.statusCode()).isEqualTo(200); 
        assertThat(beforeRestart).isPresent(); 

        restartLauncher(); 

        ParsedHttpResponse fetched = sendJson("GET", "/api/v1/entities/" + COLLECTION + "/" + entityId, 
            null, TENANT_ID);
        List<DataCloudClient.Event> events = runPromise(() -> client.queryEvents( 
            TENANT_ID,
            DataCloudClient.EventQuery.byType("entity.saved")));

        assertThat(fetched.statusCode()).isEqualTo(200); 
        assertThat(fetched.body()).containsEntry("id", entityId); 
        assertThat(asMap(fetched.body().get("data"))).containsEntry("name", "survives restart");
        assertThat(events) 
            .anySatisfy(event -> { 
                assertThat(event.type()).isEqualTo("entity.saved");
                assertThat(event.payload()).containsEntry("collection", COLLECTION); 
                assertThat(event.payload()).containsEntry("id", entityId); 
            });
    }

    @Test
    @DisplayName("delete after restart removes entity and appends durable deletion event")
    void deleteAfterRestartRemovesEntityAndAppendsDurableDeletionEvent() throws Exception { 
        ParsedHttpResponse created = sendJson("POST", "/api/v1/entities/" + COLLECTION, 
            Map.of("name", "delete me", "phase", "created"), TENANT_ID); 
        String entityId = String.valueOf(created.body().get("id"));

        restartLauncher(); 

        ParsedHttpResponse deleted = sendJson("DELETE", "/api/v1/entities/" + COLLECTION + "/" + entityId, 
            null, TENANT_ID);
        Optional<DataCloudClient.Entity> entityAfterDelete = runPromise(() -> client.findById(TENANT_ID, COLLECTION, entityId)); 
        List<DataCloudClient.Event> deleteEvents = runPromise(() -> client.queryEvents( 
            TENANT_ID,
            DataCloudClient.EventQuery.byType("entity.deleted")));

        assertThat(deleted.statusCode()).isEqualTo(200); 
        assertThat(deleted.body()).containsEntry("deleted", true); 
        assertThat(entityAfterDelete).isEmpty(); 
        assertThat(deleteEvents) 
            .anySatisfy(event -> { 
                assertThat(event.type()).isEqualTo("entity.deleted");
                assertThat(event.payload()).containsEntry("collection", COLLECTION); 
                assertThat(event.payload()).containsEntry("id", entityId); 
            });
    }

    private void restartLauncher() throws Exception { 
        stopServer(); 
        startServer(); 
    }

    private void startServer() throws Exception { 
        client = DataCloud.create(config); 
        port = findFreePort(); 
        server = new DataCloudHttpServer(client, port); 
        server.start(); 
    }

    private void stopServer() { 
        if (server != null) { 
            server.stop(); 
            server = null;
        }
        if (client != null) { 
            client.close(); 
            client = null;
        }
    }

    private ParsedHttpResponse sendJson( 
        String method,
        String path,
        Map<String, Object> body,
        String tenantId
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder() 
            .uri(URI.create("http://127.0.0.1:" + port + path)) 
            .header("Accept", "application/json") 
            .header("X-Tenant-Id", tenantId); 

        if (body != null) { 
            builder.header("Content-Type", "application/json"); 
            builder.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))); 
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody()); 
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString()); 
        Map<String, Object> parsedBody = response.body() == null || response.body().isBlank() 
            ? Map.of() 
            : objectMapper.readValue(response.body(), MAP_TYPE); 

        return new ParsedHttpResponse(response.statusCode(), parsedBody); 
    }

    private int findFreePort() throws IOException { 
        try (ServerSocket socket = new ServerSocket(0)) { 
            return socket.getLocalPort(); 
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) { 
        return (Map<String, Object>) value; 
    }

    private record ParsedHttpResponse(int statusCode, Map<String, Object> body) {} 
}
package com.ghatana.datacloud.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloud.DataCloudConfig;
import com.ghatana.datacloud.DataCloud.DataCloudConfig.DataCloudProfile;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Proves tenant isolation through the live launcher against in-memory SOVEREIGN storage
 * @doc.layer product
 * @doc.pattern SyntheticTest
 *
 * <p><b>IMPORTANT:</b> This test uses in-memory SOVEREIGN storage (tempDir), NOT real durable providers // GH-90000
 * (PostgreSQL, Kafka). This test validates tenant isolation logic but does NOT prove isolation against // GH-90000
 * production infrastructure. For real durable provider validation, see MultiTenantIsolationDurableTest
 * (uses Testcontainers with PostgreSQL/Kafka).</p> // GH-90000
 *
 * <p><b>DC-P0-4 Note:</b> Renamed from MultiTenantIsolationTest to clarify this is a synthetic test
 * using in-memory storage, not a true integration test against real durable providers.</p>
 */
@DisplayName("Multi-Tenant Isolation In-Memory Tests (Synthetic) [GH-90000]")
class MultiTenantIsolationInMemoryTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {}; // GH-90000
    private static final String COLLECTION = "tenant_isolation_entities";

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper(); // GH-90000
    private final HttpClient httpClient = HttpClient.newHttpClient(); // GH-90000

    private DataCloudClient client;
    private DataCloudHttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        DataCloudConfig config = DataCloudConfig.builder() // GH-90000
            .profile(DataCloudProfile.SOVEREIGN) // GH-90000
            .customConfig(Map.of("sovereign.dataDir", tempDir.resolve("sovereign-store [GH-90000]").toString()))
            .build(); // GH-90000
        client = DataCloud.create(config); // GH-90000
        port = findFreePort(); // GH-90000
        server = new DataCloudHttpServer(client, port); // GH-90000
        server.start(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) { // GH-90000
            server.stop(); // GH-90000
        }
        if (client != null) { // GH-90000
            client.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("query endpoint returns only the requesting tenant's entities [GH-90000]")
    void queryEndpointReturnsOnlyTheRequestingTenantsEntities() throws Exception { // GH-90000
        String tenantAId = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION, // GH-90000
            Map.of("name", "tenant-a-doc", "owner", "tenant-a"), "tenant-a").body().get("id [GH-90000]"));
        String tenantBId = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION, // GH-90000
            Map.of("name", "tenant-b-doc", "owner", "tenant-b"), "tenant-b").body().get("id [GH-90000]"));

        ParsedHttpResponse tenantAQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?limit=10", // GH-90000
            null, "tenant-a");
        ParsedHttpResponse tenantBQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?limit=10", // GH-90000
            null, "tenant-b");

        assertThat(tenantAQuery.statusCode()).isEqualTo(200); // GH-90000
        assertThat(tenantBQuery.statusCode()).isEqualTo(200); // GH-90000
        assertThat(entityIds(tenantAQuery.body())).containsExactly(tenantAId); // GH-90000
        assertThat(entityIds(tenantBQuery.body())).containsExactly(tenantBId); // GH-90000
    }

    @Test
    @DisplayName("cross-tenant reads and deletes do not expose another tenant's entity [GH-90000]")
    void crossTenantReadsAndDeletesDoNotExposeAnotherTenantsEntity() throws Exception { // GH-90000
        String tenantAId = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION, // GH-90000
            Map.of("name", "secret-doc", "classification", "private"), "tenant-a").body().get("id [GH-90000]"));

        ParsedHttpResponse crossTenantRead = sendJson("GET", "/api/v1/entities/" + COLLECTION + "/" + tenantAId, // GH-90000
            null, "tenant-b");
        ParsedHttpResponse crossTenantDelete = sendJson("DELETE", "/api/v1/entities/" + COLLECTION + "/" + tenantAId, // GH-90000
            null, "tenant-b");
        ParsedHttpResponse ownerRead = sendJson("GET", "/api/v1/entities/" + COLLECTION + "/" + tenantAId, // GH-90000
            null, "tenant-a");

        assertThat(crossTenantRead.statusCode()).isEqualTo(404); // GH-90000
        assertThat(crossTenantDelete.statusCode()).isEqualTo(404); // GH-90000
        assertThat(ownerRead.statusCode()).isEqualTo(200); // GH-90000
        assertThat(ownerRead.body()).containsEntry("id", tenantAId); // GH-90000
        assertThat(asMap(ownerRead.body().get("data [GH-90000]"))).containsEntry("classification", "private");
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

    @SuppressWarnings("unchecked [GH-90000]")
    private List<String> entityIds(Map<String, Object> responseBody) { // GH-90000
        return ((List<Map<String, Object>>) responseBody.get("entities [GH-90000]")).stream()
            .map(entity -> String.valueOf(entity.get("id [GH-90000]")))
            .toList(); // GH-90000
    }

    @SuppressWarnings("unchecked [GH-90000]")
    private Map<String, Object> asMap(Object value) { // GH-90000
        return (Map<String, Object>) value; // GH-90000
    }

    private int findFreePort() throws IOException { // GH-90000
        try (ServerSocket socket = new ServerSocket(0)) { // GH-90000
            return socket.getLocalPort(); // GH-90000
        }
    }

    private record ParsedHttpResponse(int statusCode, Map<String, Object> body) {} // GH-90000
}
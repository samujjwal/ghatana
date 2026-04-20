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
 * @doc.purpose Proves tenant isolation through the live launcher against sovereign storage
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Multi-Tenant Isolation Integration Tests")
class MultiTenantIsolationTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String COLLECTION = "tenant_isolation_entities";

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private DataCloudClient client;
    private DataCloudHttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        DataCloudConfig config = DataCloudConfig.builder()
            .profile(DataCloudProfile.SOVEREIGN)
            .customConfig(Map.of("sovereign.dataDir", tempDir.resolve("sovereign-store").toString()))
            .build();
        client = DataCloud.create(config);
        port = findFreePort();
        server = new DataCloudHttpServer(client, port);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (client != null) {
            client.close();
        }
    }

    @Test
    @DisplayName("query endpoint returns only the requesting tenant's entities")
    void queryEndpointReturnsOnlyTheRequestingTenantsEntities() throws Exception {
        String tenantAId = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION,
            Map.of("name", "tenant-a-doc", "owner", "tenant-a"), "tenant-a").body().get("id"));
        String tenantBId = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION,
            Map.of("name", "tenant-b-doc", "owner", "tenant-b"), "tenant-b").body().get("id"));

        ParsedHttpResponse tenantAQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?limit=10",
            null, "tenant-a");
        ParsedHttpResponse tenantBQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?limit=10",
            null, "tenant-b");

        assertThat(tenantAQuery.statusCode()).isEqualTo(200);
        assertThat(tenantBQuery.statusCode()).isEqualTo(200);
        assertThat(entityIds(tenantAQuery.body())).containsExactly(tenantAId);
        assertThat(entityIds(tenantBQuery.body())).containsExactly(tenantBId);
    }

    @Test
    @DisplayName("cross-tenant reads and deletes do not expose another tenant's entity")
    void crossTenantReadsAndDeletesDoNotExposeAnotherTenantsEntity() throws Exception {
        String tenantAId = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION,
            Map.of("name", "secret-doc", "classification", "private"), "tenant-a").body().get("id"));

        ParsedHttpResponse crossTenantRead = sendJson("GET", "/api/v1/entities/" + COLLECTION + "/" + tenantAId,
            null, "tenant-b");
        ParsedHttpResponse crossTenantDelete = sendJson("DELETE", "/api/v1/entities/" + COLLECTION + "/" + tenantAId,
            null, "tenant-b");
        ParsedHttpResponse ownerRead = sendJson("GET", "/api/v1/entities/" + COLLECTION + "/" + tenantAId,
            null, "tenant-a");

        assertThat(crossTenantRead.statusCode()).isEqualTo(404);
        assertThat(crossTenantDelete.statusCode()).isEqualTo(404);
        assertThat(ownerRead.statusCode()).isEqualTo(200);
        assertThat(ownerRead.body()).containsEntry("id", tenantAId);
        assertThat(asMap(ownerRead.body().get("data"))).containsEntry("classification", "private");
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

    @SuppressWarnings("unchecked")
    private List<String> entityIds(Map<String, Object> responseBody) {
        return ((List<Map<String, Object>>) responseBody.get("entities")).stream()
            .map(entity -> String.valueOf(entity.get("id")))
            .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private record ParsedHttpResponse(int statusCode, Map<String, Object> body) {}
}
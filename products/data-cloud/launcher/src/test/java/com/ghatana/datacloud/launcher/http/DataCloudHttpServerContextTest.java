package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for the tenant-scoped context layer API (P3.1). // GH-90000
 *
 * <p>Covers GET /api/v1/context, PUT /api/v1/context,
 * DELETE /api/v1/context/keys/:key, and GET /api/v1/context/snapshot.
 */
@DisplayName("DataCloudHttpServer – context layer API (P3.1)")
class DataCloudHttpServerContextTest {

    private static final String TEST_JWT_SECRET = "0123456789abcdef0123456789abcdef";

    private DataCloudHttpServer server;
    private HttpClient httpClient;
    private int port;
    private JwtTokenProvider provider;
    private String token;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        httpClient = HttpClient.newHttpClient(); // GH-90000
        port = findFreePort(); // GH-90000
        provider = JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60_000L); // GH-90000
        token = provider.createToken("test-user", List.of("viewer"), Map.of("tenant_id", "tenant-ctx"));

        server = new DataCloudHttpServer(mock(DataCloudClient.class), port) // GH-90000
            .withJwtProvider(provider); // GH-90000
        server.start(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) { // GH-90000
            server.stop(); // GH-90000
        }
    }

    // ─── GET /api/v1/context ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/context returns empty entries for new tenant")
    @SuppressWarnings("unchecked")
    void getContextReturnsEmptyEntriesForNewTenant() throws Exception { // GH-90000
        HttpResponse<String> response = get("/api/v1/context");

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
        Map<String, Object> entries = (Map<String, Object>) body.get("entries");

        assertThat(entries).isEmpty(); // GH-90000
        assertThat(body).containsEntry("count", 0); // GH-90000
        assertThat(body).containsKey("version");
        assertThat(body).containsKey("tenantId");
    }

    // ─── PUT /api/v1/context ─────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/context upserts entries and returns version 1")
    @SuppressWarnings("unchecked")
    void putContextUpsertsEntriesReturnsVersion() throws Exception { // GH-90000
        String body = mapper.writeValueAsString(Map.of("entries", Map.of("feature.dark-mode", true, "locale", "en-US"))); // GH-90000

        HttpResponse<String> response = put("/api/v1/context", body); // GH-90000

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        Map<String, Object> resp = mapper.readValue(response.body(), Map.class); // GH-90000
        assertThat(resp).containsEntry("upserted", 2); // GH-90000
        assertThat(((Number) resp.get("version")).longValue()).isEqualTo(1L);
    }

    @Test
    @DisplayName("PUT /api/v1/context with flat body treats all keys as entries")
    @SuppressWarnings("unchecked")
    void putContextFlatBodyTreatsAllKeysAsEntries() throws Exception { // GH-90000
        String body = mapper.writeValueAsString(Map.of("key1", "value1", "key2", 42)); // GH-90000

        HttpResponse<String> response = put("/api/v1/context", body); // GH-90000

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        Map<String, Object> resp = mapper.readValue(response.body(), Map.class); // GH-90000
        assertThat(resp).containsEntry("upserted", 2); // GH-90000
    }

    @Test
    @DisplayName("PUT /api/v1/context with empty body returns 400")
    void putContextWithEmptyBodyReturns400() throws Exception { // GH-90000
        HttpResponse<String> response = put("/api/v1/context", "{}"); // GH-90000

        assertThat(response.statusCode()).isEqualTo(400); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/context reflects previously PUT entries")
    @SuppressWarnings("unchecked")
    void getContextReflectsPreviouslyPutEntries() throws Exception { // GH-90000
        put("/api/v1/context", mapper.writeValueAsString(Map.of("entries", Map.of("theme", "dark")))); // GH-90000

        HttpResponse<String> response = get("/api/v1/context");

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
        Map<String, Object> entries = (Map<String, Object>) body.get("entries");

        assertThat(entries).containsEntry("theme", "dark"); // GH-90000
        assertThat(body).containsEntry("count", 1); // GH-90000
    }

    // ─── DELETE /api/v1/context/keys/:key ────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/context/keys/:key removes the key and returns 204")
    void deleteContextKeyRemovesKeyReturns204() throws Exception { // GH-90000
        put("/api/v1/context", mapper.writeValueAsString(Map.of("entries", Map.of("toDelete", "yes")))); // GH-90000

        HttpResponse<String> deleteResponse = delete("/api/v1/context/keys/toDelete");
        assertThat(deleteResponse.statusCode()).isEqualTo(204); // GH-90000

        HttpResponse<String> getResponse = get("/api/v1/context");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(getResponse.body(), Map.class); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> entries = (Map<String, Object>) body.get("entries");
        assertThat(entries).doesNotContainKey("toDelete");
    }

    @Test
    @DisplayName("DELETE /api/v1/context/keys/:key returns 404 for unknown key")
    void deleteContextKeyReturns404ForUnknownKey() throws Exception { // GH-90000
        HttpResponse<String> response = delete("/api/v1/context/keys/nonexistent");
        assertThat(response.statusCode()).isEqualTo(404); // GH-90000
    }

    // ─── GET /api/v1/context/snapshot ────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/context/snapshot returns versioned snapshot with createdAt")
    @SuppressWarnings("unchecked")
    void getSnapshotReturnsVersionedSnapshot() throws Exception { // GH-90000
        put("/api/v1/context", mapper.writeValueAsString(Map.of("entries", Map.of("snapKey", "snapValue")))); // GH-90000

        HttpResponse<String> response = get("/api/v1/context/snapshot");

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        Map<String, Object> snapshot = mapper.readValue(response.body(), Map.class); // GH-90000

        assertThat(snapshot).containsKey("tenantId");
        assertThat(snapshot).containsKey("version");
        assertThat(snapshot).containsKey("count");
        assertThat(snapshot).containsKey("createdAt");
        assertThat(snapshot).containsKey("snapshotAt");
        Map<String, Object> entries = (Map<String, Object>) snapshot.get("entries");
        assertThat(entries).containsEntry("snapKey", "snapValue"); // GH-90000
        assertThat(((Number) snapshot.get("count")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("GET /api/v1/context/snapshot version increments with each PUT")
    void snapshotVersionIncrementsWithEachPut() throws Exception { // GH-90000
        put("/api/v1/context", mapper.writeValueAsString(Map.of("entries", Map.of("k1", "v1")))); // GH-90000
        put("/api/v1/context", mapper.writeValueAsString(Map.of("entries", Map.of("k2", "v2")))); // GH-90000

        HttpResponse<String> response = get("/api/v1/context/snapshot");
        Map<?, ?> snapshot = mapper.readValue(response.body(), Map.class); // GH-90000

        assertThat(((Number) snapshot.get("version")).longValue()).isEqualTo(2L);
        assertThat(((Number) snapshot.get("count")).intValue()).isEqualTo(2);
    }

    // ─── HTTP helpers ─────────────────────────────────────────────────────────

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        HttpRequest request = HttpRequest.newBuilder() // GH-90000
            .uri(URI.create("http://localhost:" + port + path)) // GH-90000
            .header("Authorization", "Bearer " + token) // GH-90000
            .GET() // GH-90000
            .build(); // GH-90000
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> put(String path, String jsonBody) throws Exception { // GH-90000
        HttpRequest request = HttpRequest.newBuilder() // GH-90000
            .uri(URI.create("http://localhost:" + port + path)) // GH-90000
            .header("Authorization", "Bearer " + token) // GH-90000
            .header("Content-Type", "application/json") // GH-90000
            .PUT(HttpRequest.BodyPublishers.ofString(jsonBody)) // GH-90000
            .build(); // GH-90000
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> delete(String path) throws Exception { // GH-90000
        HttpRequest request = HttpRequest.newBuilder() // GH-90000
            .uri(URI.create("http://localhost:" + port + path)) // GH-90000
            .header("Authorization", "Bearer " + token) // GH-90000
            .DELETE() // GH-90000
            .build(); // GH-90000
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private int findFreePort() throws IOException { // GH-90000
        try (ServerSocket socket = new ServerSocket(0)) { // GH-90000
            return socket.getLocalPort(); // GH-90000
        }
    }
}

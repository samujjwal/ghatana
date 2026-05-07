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
 * Integration tests for the tenant-scoped context layer API (P3.1). 
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
    private final ObjectMapper mapper = new ObjectMapper(); 

    @BeforeEach
    void setUp() throws Exception { 
        httpClient = HttpClient.newHttpClient(); 
        port = findFreePort(); 
        provider = JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60_000L); 
        token = provider.createToken("test-user", List.of("viewer"), Map.of("tenant_id", "tenant-ctx"));

        server = new DataCloudHttpServer(mock(DataCloudClient.class), port) 
            .withJwtProvider(provider); 
        server.start(); 
    }

    @AfterEach
    void tearDown() { 
        if (server != null) { 
            server.stop(); 
        }
    }

    // ─── GET /api/v1/context ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/context returns empty entries for new tenant")
    @SuppressWarnings("unchecked")
    void getContextReturnsEmptyEntriesForNewTenant() throws Exception { 
        HttpResponse<String> response = get("/api/v1/context");

        assertThat(response.statusCode()).isEqualTo(200); 
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); 
        Map<String, Object> entries = (Map<String, Object>) body.get("entries");

        assertThat(entries).isEmpty(); 
        assertThat(body).containsEntry("count", 0); 
        assertThat(body).containsKey("version");
        assertThat(body).containsKey("tenantId");
    }

    // ─── PUT /api/v1/context ─────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/context upserts entries and returns version 1")
    @SuppressWarnings("unchecked")
    void putContextUpsertsEntriesReturnsVersion() throws Exception { 
        String body = mapper.writeValueAsString(Map.of("entries", Map.of("feature.dark-mode", true, "locale", "en-US"))); 

        HttpResponse<String> response = put("/api/v1/context", body); 

        assertThat(response.statusCode()).isEqualTo(200); 
        Map<String, Object> resp = mapper.readValue(response.body(), Map.class); 
        assertThat(resp).containsEntry("upserted", 2); 
        assertThat(((Number) resp.get("version")).longValue()).isEqualTo(1L);
    }

    @Test
    @DisplayName("PUT /api/v1/context with flat body treats all keys as entries")
    @SuppressWarnings("unchecked")
    void putContextFlatBodyTreatsAllKeysAsEntries() throws Exception { 
        String body = mapper.writeValueAsString(Map.of("key1", "value1", "key2", 42)); 

        HttpResponse<String> response = put("/api/v1/context", body); 

        assertThat(response.statusCode()).isEqualTo(200); 
        Map<String, Object> resp = mapper.readValue(response.body(), Map.class); 
        assertThat(resp).containsEntry("upserted", 2); 
    }

    @Test
    @DisplayName("PUT /api/v1/context with empty body returns 400")
    void putContextWithEmptyBodyReturns400() throws Exception { 
        HttpResponse<String> response = put("/api/v1/context", "{}"); 

        assertThat(response.statusCode()).isEqualTo(400); 
    }

    @Test
    @DisplayName("GET /api/v1/context reflects previously PUT entries")
    @SuppressWarnings("unchecked")
    void getContextReflectsPreviouslyPutEntries() throws Exception { 
        put("/api/v1/context", mapper.writeValueAsString(Map.of("entries", Map.of("theme", "dark")))); 

        HttpResponse<String> response = get("/api/v1/context");

        assertThat(response.statusCode()).isEqualTo(200); 
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); 
        Map<String, Object> entries = (Map<String, Object>) body.get("entries");

        assertThat(entries).containsEntry("theme", "dark"); 
        assertThat(body).containsEntry("count", 1); 
    }

    // ─── DELETE /api/v1/context/keys/:key ────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/context/keys/:key removes the key and returns 204")
    void deleteContextKeyRemovesKeyReturns204() throws Exception { 
        put("/api/v1/context", mapper.writeValueAsString(Map.of("entries", Map.of("toDelete", "yes")))); 

        HttpResponse<String> deleteResponse = delete("/api/v1/context/keys/toDelete");
        assertThat(deleteResponse.statusCode()).isEqualTo(204); 

        HttpResponse<String> getResponse = get("/api/v1/context");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(getResponse.body(), Map.class); 
        @SuppressWarnings("unchecked")
        Map<String, Object> entries = (Map<String, Object>) body.get("entries");
        assertThat(entries).doesNotContainKey("toDelete");
    }

    @Test
    @DisplayName("DELETE /api/v1/context/keys/:key returns 404 for unknown key")
    void deleteContextKeyReturns404ForUnknownKey() throws Exception { 
        HttpResponse<String> response = delete("/api/v1/context/keys/nonexistent");
        assertThat(response.statusCode()).isEqualTo(404); 
    }

    // ─── GET /api/v1/context/snapshot ────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/context/snapshot returns versioned snapshot with createdAt")
    @SuppressWarnings("unchecked")
    void getSnapshotReturnsVersionedSnapshot() throws Exception { 
        put("/api/v1/context", mapper.writeValueAsString(Map.of("entries", Map.of("snapKey", "snapValue")))); 

        HttpResponse<String> response = get("/api/v1/context/snapshot");

        assertThat(response.statusCode()).isEqualTo(200); 
        Map<String, Object> snapshot = mapper.readValue(response.body(), Map.class); 

        assertThat(snapshot).containsKey("tenantId");
        assertThat(snapshot).containsKey("version");
        assertThat(snapshot).containsKey("count");
        assertThat(snapshot).containsKey("createdAt");
        assertThat(snapshot).containsKey("snapshotAt");
        Map<String, Object> entries = (Map<String, Object>) snapshot.get("entries");
        assertThat(entries).containsEntry("snapKey", "snapValue"); 
        assertThat(((Number) snapshot.get("count")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("GET /api/v1/context/snapshot version increments with each PUT")
    void snapshotVersionIncrementsWithEachPut() throws Exception { 
        put("/api/v1/context", mapper.writeValueAsString(Map.of("entries", Map.of("k1", "v1")))); 
        put("/api/v1/context", mapper.writeValueAsString(Map.of("entries", Map.of("k2", "v2")))); 

        HttpResponse<String> response = get("/api/v1/context/snapshot");
        Map<?, ?> snapshot = mapper.readValue(response.body(), Map.class); 

        assertThat(((Number) snapshot.get("version")).longValue()).isEqualTo(2L);
        assertThat(((Number) snapshot.get("count")).intValue()).isEqualTo(2);
    }

    // ─── HTTP helpers ─────────────────────────────────────────────────────────

    private HttpResponse<String> get(String path) throws Exception { 
        HttpRequest request = HttpRequest.newBuilder() 
            .uri(URI.create("http://localhost:" + port + path)) 
            .header("Authorization", "Bearer " + token) 
            .GET() 
            .build(); 
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> put(String path, String jsonBody) throws Exception { 
        HttpRequest request = HttpRequest.newBuilder() 
            .uri(URI.create("http://localhost:" + port + path)) 
            .header("Authorization", "Bearer " + token) 
            .header("Content-Type", "application/json") 
            .PUT(HttpRequest.BodyPublishers.ofString(jsonBody)) 
            .build(); 
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> delete(String path) throws Exception { 
        HttpRequest request = HttpRequest.newBuilder() 
            .uri(URI.create("http://localhost:" + port + path)) 
            .header("Authorization", "Bearer " + token) 
            .DELETE() 
            .build(); 
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()); 
    }

    private int findFreePort() throws IOException { 
        try (ServerSocket socket = new ServerSocket(0)) { 
            return socket.getLocalPort(); 
        }
    }
}

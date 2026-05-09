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

@DisplayName("DataCloudHttpServer capability registry")
class DataCloudHttpServerCapabilityTest {

    private static final String TEST_JWT_SECRET = "0123456789abcdef0123456789abcdef";

    private DataCloudHttpServer server;
    private HttpClient httpClient;
    private int port;
    private final ObjectMapper mapper = new ObjectMapper(); 

    @BeforeEach
    void setUp() throws Exception { 
        httpClient = HttpClient.newHttpClient(); 
        port = findFreePort(); 
    }

    @AfterEach
    void tearDown() { 
        if (server != null) { 
            server.stop(); 
        }
    }

    @Test
    @DisplayName("capabilities endpoint reports configured and degraded features")
    @SuppressWarnings("unchecked")
    void capabilitiesEndpointReportsConfiguredAndDegradedFeatures() throws Exception { 
        JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60000L); 
        String token = provider.createToken("ui-user", List.of("viewer"), Map.of("tenant_id", "tenant-a"));

        server = new DataCloudHttpServer(mock(DataCloudClient.class), port) 
            .withJwtProvider(provider) 
            .withHealthSubsystem("database", () -> Map.of("status", "DOWN")); 
        server.start(); 

        HttpResponse<String> response = get("/api/v1/surfaces", token); 

        assertThat(response.statusCode()).isEqualTo(200); 
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); 
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        Map<String, Object> capabilities = (Map<String, Object>) data.get("capabilities");
        Map<String, Object> meta = (Map<String, Object>) capabilities.get("_meta");
        Map<String, Object> runtimePosture = (Map<String, Object>) meta.get("runtimePosture");
        Map<String, Object> jwtCapability = (Map<String, Object>) capabilities.get("authentication.jwt");
        Map<String, Object> databaseCapability = (Map<String, Object>) capabilities.get("health.database");
        Map<String, Object> searchCapability = (Map<String, Object>) capabilities.get("search.openSearch");
        Map<String, Object> eventTail = (Map<String, Object>) runtimePosture.get("eventTail");

        assertThat(jwtCapability).containsEntry("status", "ACTIVE"); 
        assertThat(databaseCapability).containsEntry("status", "DEGRADED"); 
        assertThat(databaseCapability).containsEntry("dependencyStatus", "DOWN"); 
        assertThat(searchCapability).containsEntry("status", "NOT_CONFIGURED"); 
        assertThat(eventTail).containsEntry("available", false);
        assertThat(eventTail).containsEntry("configurable", false);
    }

    @Test
    @DisplayName("surfaces endpoint is idempotent")
    @SuppressWarnings("unchecked")
    void surfacesEndpointMatchesCapabilitiesPayload() throws Exception {
        JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60000L);
        String token = provider.createToken("ui-user", List.of("viewer"), Map.of("tenant_id", "tenant-a"));

        server = new DataCloudHttpServer(mock(DataCloudClient.class), port)
            .withJwtProvider(provider)
            .withHealthSubsystem("database", () -> Map.of("status", "DOWN"));
        server.start();

        HttpResponse<String> surfacesResponse = get("/api/v1/surfaces", token);
        HttpResponse<String> surfacesResponse2 = get("/api/v1/surfaces", token);

        assertThat(surfacesResponse.statusCode()).isEqualTo(200);
        assertThat(surfacesResponse2.statusCode()).isEqualTo(200);

        Map<String, Object> surfacesBody = mapper.readValue(surfacesResponse.body(), Map.class);
        Map<String, Object> surfacesBody2 = mapper.readValue(surfacesResponse2.body(), Map.class);

        Map<String, Object> surfacesData = (Map<String, Object>) surfacesBody.get("data");
        Map<String, Object> surfacesData2 = (Map<String, Object>) surfacesBody2.get("data");
        Map<String, Object> surfacesCapabilities = (Map<String, Object>) surfacesData.get("capabilities");
        Map<String, Object> surfacesCapabilities2 = (Map<String, Object>) surfacesData2.get("capabilities");

        assertThat(surfacesCapabilities.keySet()).containsAll(surfacesCapabilities2.keySet());
        assertThat(surfacesCapabilities2.keySet()).containsAll(surfacesCapabilities.keySet());

        Map<String, Object> surfacesRuntimePosture = (Map<String, Object>) ((Map<String, Object>) surfacesCapabilities.get("_meta")).get("runtimePosture");
        Map<String, Object> surfacesRuntimePosture2 = (Map<String, Object>) ((Map<String, Object>) surfacesCapabilities2.get("_meta")).get("runtimePosture");
        
        // DC-P1.18: Profile-posture parity checks for all durability fields
        assertThat(surfacesRuntimePosture.get("authenticationConfigured")).isEqualTo(true);
        assertThat(surfacesRuntimePosture2.get("authenticationConfigured")).isEqualTo(true);
        assertThat(surfacesRuntimePosture.get("productionLikeProfile")).isEqualTo(surfacesRuntimePosture2.get("productionLikeProfile"));
        
        // DC-P1.18: Non-local durability posture parity checks
        assertThat(surfacesRuntimePosture).containsKey("settingsDurable");
        assertThat(surfacesRuntimePosture).containsKey("entityStoreDurable");
        assertThat(surfacesRuntimePosture).containsKey("coreEventStoreDurable");
        assertThat(surfacesRuntimePosture).containsKey("idempotencyStoreDurable");
        assertThat(surfacesRuntimePosture).containsKey("settingsStorageMode");
        assertThat(surfacesRuntimePosture).containsKey("eventStoreWired");
        assertThat(surfacesRuntimePosture).containsKey("eventTail");
        assertThat(surfacesRuntimePosture).containsKey("auditConfigured");
        assertThat(surfacesRuntimePosture).containsKey("policyConfigured");
        assertThat(surfacesRuntimePosture).containsKey("metricsConfigured");
        assertThat(surfacesRuntimePosture).containsKey("traceConfigured");
        
        // DC-P1.18: Ensure parity across both responses for durability fields
        assertThat(surfacesRuntimePosture.get("settingsDurable")).isEqualTo(surfacesRuntimePosture2.get("settingsDurable"));
        assertThat(surfacesRuntimePosture.get("entityStoreDurable")).isEqualTo(surfacesRuntimePosture2.get("entityStoreDurable"));
        assertThat(surfacesRuntimePosture.get("coreEventStoreDurable")).isEqualTo(surfacesRuntimePosture2.get("coreEventStoreDurable"));
        assertThat(surfacesRuntimePosture.get("idempotencyStoreDurable")).isEqualTo(surfacesRuntimePosture2.get("idempotencyStoreDurable"));
        assertThat(surfacesRuntimePosture.get("settingsStorageMode")).isEqualTo(surfacesRuntimePosture2.get("settingsStorageMode"));

        assertThat(((Map<String, Object>) surfacesCapabilities.get("authentication.jwt")).get("status"))
            .isEqualTo(((Map<String, Object>) surfacesCapabilities2.get("authentication.jwt")).get("status"));
        assertThat(((Map<String, Object>) surfacesCapabilities.get("health.database")).get("status"))
            .isEqualTo(((Map<String, Object>) surfacesCapabilities2.get("health.database")).get("status"));
        assertThat(((Map<String, Object>) surfacesBody.get("meta")).get("tenantId"))
            .isEqualTo(((Map<String, Object>) surfacesBody2.get("meta")).get("tenantId"));
    }

    private HttpResponse<String> get(String path, String token) throws Exception { 
        HttpRequest request = HttpRequest.newBuilder() 
            .uri(URI.create("http://localhost:" + port + path)) 
            .header("Authorization", "Bearer " + token) 
            .GET() 
            .build(); 
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()); 
    }

    private int findFreePort() throws IOException { 
        try (ServerSocket socket = new ServerSocket(0)) { 
            return socket.getLocalPort(); 
        }
    }
}
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.handlers.JdbcContextStore;
import com.ghatana.datacloud.spi.TransactionManager;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import org.h2.jdbcx.JdbcDataSource;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("DataCloudHttpServer surface registry")
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
    @DisplayName("surfaces endpoint reports configured and degraded features")
    @SuppressWarnings("unchecked")
    void surfacesEndpointReportsConfiguredAndDegradedFeatures() throws Exception { 
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
        List<Map<String, Object>> surfacesList = (List<Map<String, Object>>) data.get("surfaces");
        Map<String, Map<String, Object>> surfacesById = surfacesById(surfacesList);

        Map<String, Object> jwtCapability = surfacesById.get("authentication.jwt");
        Map<String, Object> entityStoreCapability = surfacesById.get("data.entityStore");
        Map<String, Object> aiCompletionCapability = surfacesById.get("intelligence.aiCompletion");
        Map<String, Object> eventStoreCapability = surfacesById.get("event.store");
        Map<String, Object> runtimePosture = (Map<String, Object>) eventStoreCapability.get("runtimePosture");
        Map<String, Object> details = (Map<String, Object>) runtimePosture.get("details");
        Map<String, Object> eventTail = (Map<String, Object>) details.get("eventTail");

        assertThat(jwtCapability).containsEntry("state", "LIVE");
        assertThat(jwtCapability).containsEntry("status", "ACTIVE");
        assertThat(entityStoreCapability).containsEntry("state", "DEGRADED");
        assertThat(entityStoreCapability).containsEntry("status", "DEGRADED");
        assertThat(aiCompletionCapability).containsEntry("state", "DISABLED");
        assertThat(aiCompletionCapability).containsEntry("status", "NOT_CONFIGURED");
        assertThat(eventTail).containsEntry("available", false);
        assertThat(eventTail).containsEntry("configurable", false);
    }

    @Test
    @DisplayName("surfaces endpoint is idempotent")
    @SuppressWarnings("unchecked")
    void surfacesEndpointIsStableAcrossRequests() throws Exception {
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
        List<Map<String, Object>> surfacesList = (List<Map<String, Object>>) surfacesData.get("surfaces");
        List<Map<String, Object>> surfacesList2 = (List<Map<String, Object>>) surfacesData2.get("surfaces");
        Map<String, Map<String, Object>> surfacesById = surfacesById(surfacesList);
        Map<String, Map<String, Object>> surfacesById2 = surfacesById(surfacesList2);

        assertThat(surfacesById.keySet()).containsExactlyInAnyOrderElementsOf(surfacesById2.keySet());

        Map<String, Object> transactionSurface = surfacesById.get("operations.transactionManager");
        Map<String, Object> transactionSurface2 = surfacesById2.get("operations.transactionManager");
        Map<String, Object> runtimePosture = (Map<String, Object>) transactionSurface.get("runtimePosture");
        Map<String, Object> runtimePosture2 = (Map<String, Object>) transactionSurface2.get("runtimePosture");
        Map<String, Object> details = (Map<String, Object>) runtimePosture.get("details");
        Map<String, Object> details2 = (Map<String, Object>) runtimePosture2.get("details");

        assertThat(details).containsEntry("authenticationConfigured", true);
        assertThat(details2).containsEntry("authenticationConfigured", true);
        assertThat(details).containsEntry("transactionManager", false);
        assertThat(details2).containsEntry("transactionManager", false);
        assertThat(details).containsEntry("contextStoreMode", "InMemoryContextStore");
        assertThat(details2).containsEntry("contextStoreMode", "InMemoryContextStore");
        assertThat(details.get("settingsStorageMode")).isEqualTo(details2.get("settingsStorageMode"));
        assertThat(details.get("eventTail")).isEqualTo(details2.get("eventTail"));
        assertThat(((Map<String, Object>) surfacesBody.get("meta")).get("tenantId"))
            .isEqualTo(((Map<String, Object>) surfacesBody2.get("meta")).get("tenantId"));
    }

    @Test
    @DisplayName("surfaces endpoint exposes transactional and durable context posture when configured")
    @SuppressWarnings("unchecked")
    void surfacesEndpointExposesTransactionalAndDurableContextPosture() throws Exception {
        JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60000L);
        String token = provider.createToken("ui-user", List.of("viewer"), Map.of("tenant_id", "tenant-a"));

        server = new DataCloudHttpServer(mock(DataCloudClient.class), port)
            .withJwtProvider(provider)
            .withTransactionManager(mock(TransactionManager.class))
            .withContextStore(durableContextStore());
        server.start();

        HttpResponse<String> response = get("/api/v1/surfaces", token);

        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        List<Map<String, Object>> surfacesList = (List<Map<String, Object>>) data.get("surfaces");
        Map<String, Object> transactionManagerSurface = surfacesList.stream()
            .filter(surface -> "operations.transactionManager".equals(surface.get("surfaceId")))
            .findFirst()
            .orElseThrow();
        Map<String, Object> runtimePosture = (Map<String, Object>) transactionManagerSurface.get("runtimePosture");

        assertThat(transactionManagerSurface).containsEntry("state", "LIVE");
        Map<String, Object> details = (Map<String, Object>) runtimePosture.get("details");
        assertThat(details).containsEntry("transactionManager", true);
        assertThat(details).containsEntry("transactionOrchestrationMode", "transactional");
        assertThat(details).containsEntry("contextStoreMode", "JdbcContextStore");
        assertThat(details).containsEntry("contextStoreDurable", true);
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

    private static JdbcContextStore durableContextStore() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:capability-context-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return new JdbcContextStore(dataSource);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> surfacesById(List<Map<String, Object>> surfacesList) {
        Map<String, Map<String, Object>> surfacesById = new LinkedHashMap<>();
        for (Map<String, Object> surface : surfacesList) {
            Object surfaceId = surface.get("surfaceId");
            if (surfaceId instanceof String id) {
                surfacesById.put(id, surface);
            }
        }
        return surfacesById;
    }
}
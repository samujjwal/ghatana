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

@DisplayName("DataCloudHttpServer capability registry [GH-90000]")
class DataCloudHttpServerCapabilityTest {

    private static final String TEST_JWT_SECRET = "0123456789abcdef0123456789abcdef";

    private DataCloudHttpServer server;
    private HttpClient httpClient;
    private int port;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        httpClient = HttpClient.newHttpClient(); // GH-90000
        port = findFreePort(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) { // GH-90000
            server.stop(); // GH-90000
        }
    }

    @Test
    @DisplayName("capabilities endpoint reports configured and degraded features [GH-90000]")
    @SuppressWarnings("unchecked [GH-90000]")
    void capabilitiesEndpointReportsConfiguredAndDegradedFeatures() throws Exception { // GH-90000
        JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60000L); // GH-90000
        String token = provider.createToken("ui-user", List.of("viewer [GH-90000]"), Map.of("tenant_id", "tenant-a"));

        server = new DataCloudHttpServer(mock(DataCloudClient.class), port) // GH-90000
            .withJwtProvider(provider) // GH-90000
            .withHealthSubsystem("database", () -> Map.of("status", "DOWN")); // GH-90000
        server.start(); // GH-90000

        HttpResponse<String> response = get("/api/v1/capabilities", token); // GH-90000

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
        Map<String, Object> data = (Map<String, Object>) body.get("data [GH-90000]");
        Map<String, Object> capabilities = (Map<String, Object>) data.get("capabilities [GH-90000]");
        Map<String, Object> jwtCapability = (Map<String, Object>) capabilities.get("authentication.jwt [GH-90000]");
        Map<String, Object> databaseCapability = (Map<String, Object>) capabilities.get("health.database [GH-90000]");
        Map<String, Object> searchCapability = (Map<String, Object>) capabilities.get("search.openSearch [GH-90000]");

        assertThat(jwtCapability).containsEntry("status", "ACTIVE"); // GH-90000
        assertThat(databaseCapability).containsEntry("status", "DEGRADED"); // GH-90000
        assertThat(databaseCapability).containsEntry("dependencyStatus", "DOWN"); // GH-90000
        assertThat(searchCapability).containsEntry("status", "NOT_CONFIGURED"); // GH-90000
    }

    private HttpResponse<String> get(String path, String token) throws Exception { // GH-90000
        HttpRequest request = HttpRequest.newBuilder() // GH-90000
            .uri(URI.create("http://localhost:" + port + path)) // GH-90000
            .header("Authorization", "Bearer " + token) // GH-90000
            .GET() // GH-90000
            .build(); // GH-90000
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private int findFreePort() throws IOException { // GH-90000
        try (ServerSocket socket = new ServerSocket(0)) { // GH-90000
            return socket.getLocalPort(); // GH-90000
        }
    }
}
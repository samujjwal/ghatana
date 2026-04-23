package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("DataCloudHttpServer JWT authentication")
class DataCloudHttpServerJwtAuthTest {

    private static final String TEST_JWT_SECRET = "0123456789abcdef0123456789abcdef";

    private DataCloudHttpServer server;
    private HttpClient httpClient;
    private int port;

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
    @DisplayName("protected routes accept valid JWT bearer tokens")
    void protectedRoutesAcceptValidJwtBearerTokens() throws Exception { // GH-90000
        JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60000L); // GH-90000
        String token = provider.createToken("ui-user", List.of("viewer"), Map.of("tenant_id", "tenant-a"));

        server = new DataCloudHttpServer(mock(DataCloudClient.class), port) // GH-90000
                .withJwtProvider(provider); // GH-90000
        server.start(); // GH-90000

        HttpResponse<String> response = get("/api/v1/brain/health", token); // GH-90000

        assertThat(response.statusCode()).isEqualTo(503); // GH-90000
    }

    @Test
    @DisplayName("health endpoints stay public when JWT auth is enabled")
    void healthEndpointsStayPublicWhenJwtAuthEnabled() throws Exception { // GH-90000
        JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60000L); // GH-90000

        server = new DataCloudHttpServer(mock(DataCloudClient.class), port) // GH-90000
                .withJwtProvider(provider); // GH-90000
        server.start(); // GH-90000

        HttpResponse<String> response = get("/health", null); // GH-90000

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("invalid JWT bearer tokens return 401 on protected routes")
    void invalidJwtBearerTokensReturn401() throws Exception { // GH-90000
        JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60000L); // GH-90000

        server = new DataCloudHttpServer(mock(DataCloudClient.class), port) // GH-90000
                .withJwtProvider(provider); // GH-90000
        server.start(); // GH-90000

        HttpResponse<String> response = get("/api/v1/brain/health", "bad-token"); // GH-90000

        assertThat(response.statusCode()).isEqualTo(401); // GH-90000
    }

    private HttpResponse<String> get(String path, String token) throws Exception { // GH-90000
        HttpRequest.Builder builder = HttpRequest.newBuilder() // GH-90000
                .uri(URI.create("http://localhost:" + port + path)) // GH-90000
                .GET(); // GH-90000
        if (token != null) { // GH-90000
            builder.header("Authorization", "Bearer " + token); // GH-90000
        }

        HttpRequest request = builder.build(); // GH-90000
        int attempts = 0;
        while (true) { // GH-90000
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // GH-90000
            } catch (ConnectException connectException) { // GH-90000
                attempts++;
                if (attempts >= 10) { // GH-90000
                    throw connectException;
                }
                Thread.sleep(50L); // GH-90000
            }
        }
    }

    private int findFreePort() throws IOException { // GH-90000
        try (ServerSocket socket = new ServerSocket(0)) { // GH-90000
            return socket.getLocalPort(); // GH-90000
        }
    }
}

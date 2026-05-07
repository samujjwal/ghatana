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
    @DisplayName("protected routes accept valid JWT bearer tokens")
    void protectedRoutesAcceptValidJwtBearerTokens() throws Exception { 
        JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60000L); 
        String token = provider.createToken("ui-user", List.of("viewer"), Map.of("tenant_id", "tenant-a"));

        server = new DataCloudHttpServer(mock(DataCloudClient.class), port) 
                .withJwtProvider(provider); 
        server.start(); 

        HttpResponse<String> response = get("/api/v1/brain/health", token); 

        assertThat(response.statusCode()).isEqualTo(503); 
    }

    @Test
    @DisplayName("health endpoints stay public when JWT auth is enabled")
    void healthEndpointsStayPublicWhenJwtAuthEnabled() throws Exception { 
        JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60000L); 

        server = new DataCloudHttpServer(mock(DataCloudClient.class), port) 
                .withJwtProvider(provider); 
        server.start(); 

        HttpResponse<String> response = get("/health", null); 

        assertThat(response.statusCode()).isEqualTo(200); 
    }

    @Test
    @DisplayName("invalid JWT bearer tokens return 401 on protected routes")
    void invalidJwtBearerTokensReturn401() throws Exception { 
        JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60000L); 

        server = new DataCloudHttpServer(mock(DataCloudClient.class), port) 
                .withJwtProvider(provider); 
        server.start(); 

        HttpResponse<String> response = get("/api/v1/brain/health", "bad-token"); 

        assertThat(response.statusCode()).isEqualTo(401); 
    }

    private HttpResponse<String> get(String path, String token) throws Exception { 
        HttpRequest.Builder builder = HttpRequest.newBuilder() 
                .uri(URI.create("http://localhost:" + port + path)) 
                .GET(); 
        if (token != null) { 
            builder.header("Authorization", "Bearer " + token); 
        }

        HttpRequest request = builder.build(); 
        int attempts = 0;
        while (true) { 
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString()); 
            } catch (ConnectException connectException) { 
                attempts++;
                if (attempts >= 10) { 
                    throw connectException;
                }
                Thread.sleep(50L); 
            }
        }
    }

    private int findFreePort() throws IOException { 
        try (ServerSocket socket = new ServerSocket(0)) { 
            return socket.getLocalPort(); 
        }
    }
}

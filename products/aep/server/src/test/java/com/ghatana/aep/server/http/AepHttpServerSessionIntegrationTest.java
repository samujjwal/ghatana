/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for HTTP session handling in the AEP server pipeline.
 *
 * @doc.type class
 * @doc.purpose Verify SessionFilter is wired into the HTTP server chain
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Tag("local-network")
@DisplayName("AepHttpServer Session Integration")
class AepHttpServerSessionIntegrationTest {

    private static final String SESSION_HEADER = "X-AEP-Session";

    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    private AepEngine engine;
    private AepHttpServer server;
    private HttpClient httpClient;
    private int port;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        engine = Aep.forTesting(); // GH-90000
        port = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
        server = new AepHttpServer(engine, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) { // GH-90000
            server.stop(); // GH-90000
        }
        if (engine != null) { // GH-90000
            engine.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("POST /api/v1/session issues a reusable session token")
    void postSessionIssuesReusableToken() throws Exception { // GH-90000
        HttpResponse<String> sessionResponse = post("/api/v1/session", ""); // GH-90000

        assertThat(sessionResponse.statusCode()).isEqualTo(200); // GH-90000
        String sessionToken = sessionResponse.headers().firstValue(SESSION_HEADER).orElse(null); // GH-90000
        assertThat(sessionToken).isNotBlank(); // GH-90000

        @SuppressWarnings("unchecked")
        Map<String, Object> sessionBody = mapper.readValue(sessionResponse.body(), Map.class); // GH-90000
        assertThat(sessionBody).containsEntry("session", sessionToken); // GH-90000
        assertThat(sessionBody).containsKey("expiresInSeconds");

        HttpRequest agentsRequest = HttpRequest.newBuilder() // GH-90000
            .GET() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/agents")) // GH-90000
            .header(SESSION_HEADER, sessionToken) // GH-90000
            .build(); // GH-90000

        HttpResponse<String> agentsResponse = httpClient.send(agentsRequest, HttpResponse.BodyHandlers.ofString()); // GH-90000
        assertThat(agentsResponse.statusCode()).isEqualTo(200); // GH-90000
    }

    private HttpResponse<String> post(String path, String body) throws Exception { // GH-90000
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)); // GH-90000
        if (!body.isEmpty()) { // GH-90000
            requestBuilder.header("Content-Type", "application/json"); // GH-90000
        }
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private static int findFreePort() throws IOException { // GH-90000
        try (ServerSocket ss = new ServerSocket(0)) { // GH-90000
            return ss.getLocalPort(); // GH-90000
        }
    }

    private static void waitForServerReady(int port) throws Exception { // GH-90000
        long deadline = System.currentTimeMillis() + 5_000; // GH-90000
        while (System.currentTimeMillis() < deadline) { // GH-90000
            try {
                new Socket("127.0.0.1", port).close(); // GH-90000
                return;
            } catch (IOException ignored) { // GH-90000
                Thread.sleep(50); // GH-90000
            }
        }
        throw new AssertionError("Server did not start on port " + port + " within 5 s"); // GH-90000
    }
}

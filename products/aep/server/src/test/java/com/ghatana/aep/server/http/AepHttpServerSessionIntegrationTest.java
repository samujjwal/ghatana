/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("AepHttpServer Session Integration")
class AepHttpServerSessionIntegrationTest {

    private static final String SESSION_HEADER = "X-AEP-Session";

    private final ObjectMapper mapper = new ObjectMapper();

    private AepEngine engine;
    private AepHttpServer server;
    private HttpClient httpClient;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        engine = Aep.forTesting();
        port = findFreePort();
        httpClient = HttpClient.newBuilder().build();
        server = new AepHttpServer(engine, port);
        server.start();
        waitForServerReady(port);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("POST /api/v1/session issues a reusable session token")
    void postSessionIssuesReusableToken() throws Exception {
        HttpResponse<String> sessionResponse = post("/api/v1/session", "");

        assertThat(sessionResponse.statusCode()).isEqualTo(200);
        String sessionToken = sessionResponse.headers().firstValue(SESSION_HEADER).orElse(null);
        assertThat(sessionToken).isNotBlank();

        @SuppressWarnings("unchecked")
        Map<String, Object> sessionBody = mapper.readValue(sessionResponse.body(), Map.class);
        assertThat(sessionBody).containsEntry("session", sessionToken);
        assertThat(sessionBody).containsKey("expiresInSeconds");

        HttpRequest agentsRequest = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/agents"))
            .header(SESSION_HEADER, sessionToken)
            .build();

        HttpResponse<String> agentsResponse = httpClient.send(agentsRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(agentsResponse.statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .uri(URI.create("http://127.0.0.1:" + port + path));
        if (!body.isEmpty()) {
            requestBuilder.header("Content-Type", "application/json");
        }
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitForServerReady(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                new Socket("127.0.0.1", port).close();
                return;
            } catch (IOException ignored) {
                Thread.sleep(50);
            }
        }
        throw new AssertionError("Server did not start on port " + port + " within 5 s");
    }
}

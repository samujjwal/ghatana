/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for semantic similarity and RAG endpoints (P4.5.1). // GH-90000
 */
@DisplayName("DataCloudHttpServer – semantic search API (P4.5.1)")
class DataCloudHttpServerSemanticSearchTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        port = findFreePort(); // GH-90000
    }

    @Override
    protected void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/entities/:collection/similar returns indexed semantic neighbors")
    void similarEntitiesReturnsSemanticMatches() throws Exception { // GH-90000
        DataCloudClient.Entity entityA = entity("11111111-1111-1111-1111-111111111111", "tickets", Map.of( // GH-90000
            "title", "login failure investigation",
            "summary", "user cannot sign in because password reset token expired"
        ));
        DataCloudClient.Entity entityB = entity("22222222-2222-2222-2222-222222222222", "tickets", Map.of( // GH-90000
            "title", "password reset troubleshooting",
            "summary", "reset token expired and user needs a new sign in link"
        ));

        when(mockClient.save(anyString(), eq("tickets"), any()))
            .thenAnswer(invocation -> { // GH-90000
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = invocation.getArgument(2, Map.class); // GH-90000
                String title = String.valueOf(payload.get("title"));
                return Promise.of(title.contains("login failure") ? entityA : entityB);
            });
        when(mockClient.appendEvent(anyString(), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(1L))); // GH-90000

        startServer(); // GH-90000

        assertStatusCode(postJson("/api/v1/entities/tickets", entityA.data()), 200); // GH-90000
        assertStatusCode(postJson("/api/v1/entities/tickets", entityB.data()), 200); // GH-90000

        HttpResponse<String> response = get("/api/v1/entities/tickets/similar?id=11111111-1111-1111-1111-111111111111&k=3");

        assertStatusCode(response, 200); // GH-90000
        Map<String, Object> body = parseJsonResponse(response); // GH-90000
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) body.get("matches");
        assertThat(matches).isNotEmpty(); // GH-90000
        assertThat(matches.getFirst()).containsEntry("id", "22222222-2222-2222-2222-222222222222"); // GH-90000
    }

    @Test
    @DisplayName("POST /api/v1/context/:collection/rag returns grounded answer from indexed context")
    void ragEndpointReturnsGroundedAnswer() throws Exception { // GH-90000
        DataCloudClient.Entity entityA = entity("11111111-1111-1111-1111-111111111111", "tickets", Map.of( // GH-90000
            "title", "login failure investigation",
            "summary", "user cannot sign in because password reset token expired"
        ));
        DataCloudClient.Entity entityB = entity("22222222-2222-2222-2222-222222222222", "tickets", Map.of( // GH-90000
            "title", "password reset troubleshooting",
            "summary", "reset token expired and user needs a new sign in link"
        ));

        when(mockClient.save(anyString(), eq("tickets"), any()))
            .thenAnswer(invocation -> { // GH-90000
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = invocation.getArgument(2, Map.class); // GH-90000
                String title = String.valueOf(payload.get("title"));
                return Promise.of(title.contains("login failure") ? entityA : entityB);
            });
        when(mockClient.appendEvent(anyString(), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(1L))); // GH-90000

        startServer(); // GH-90000

        assertStatusCode(postJson("/api/v1/entities/tickets", entityA.data()), 200); // GH-90000
        assertStatusCode(postJson("/api/v1/entities/tickets", entityB.data()), 200); // GH-90000

        HttpResponse<String> response = postJson("/api/v1/context/tickets/rag", Map.of( // GH-90000
            "question", "How should I troubleshoot an expired password reset token?",
            "k", 3
        ));

        assertStatusCode(response, 200); // GH-90000
        Map<String, Object> body = parseJsonResponse(response); // GH-90000
        assertThat(body).containsEntry("collection", "tickets"); // GH-90000
        assertThat(String.valueOf(body.get("answer"))).contains("Grounded answer");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> context = (List<Map<String, Object>>) body.get("context");
        assertThat(context).isNotEmpty(); // GH-90000
    }

    private DataCloudClient.Entity entity(String id, String collection, Map<String, Object> data) { // GH-90000
        Instant now = Instant.now(); // GH-90000
        return new DataCloudClient.Entity(id, collection, data, now, now, 1L); // GH-90000
    }
}
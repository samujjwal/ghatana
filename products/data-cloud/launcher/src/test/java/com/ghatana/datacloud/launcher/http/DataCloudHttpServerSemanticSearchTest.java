/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Integration tests for semantic similarity and RAG endpoints (P4.5.1).
 */
@DisplayName("DataCloudHttpServer – semantic search API (P4.5.1)")
class DataCloudHttpServerSemanticSearchTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        port = findFreePort();
    }

    @Override
    protected void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS);
    }

    @Test
    @DisplayName("GET /api/v1/entities/:collection/similar returns indexed semantic neighbors")
    void similarEntitiesReturnsSemanticMatches() throws Exception {
        DataCloudClient.Entity entityA = entity("11111111-1111-1111-1111-111111111111", "tickets", Map.of(
            "title", "login failure investigation",
            "summary", "user cannot sign in because password reset token expired"
        ));
        DataCloudClient.Entity entityB = entity("22222222-2222-2222-2222-222222222222", "tickets", Map.of(
            "title", "password reset troubleshooting",
            "summary", "reset token expired and user needs a new sign in link"
        ));

        when(mockClient.save(anyString(), eq("tickets"), any()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = invocation.getArgument(2, Map.class);
                String title = String.valueOf(payload.get("title"));
                return Promise.of(title.contains("login failure") ? entityA : entityB);
            });
        when(mockClient.appendEvent(anyString(), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        startServer();

        assertStatusCode(postJson("/api/v1/entities/tickets", entityA.data()), 200);
        assertStatusCode(postJson("/api/v1/entities/tickets", entityB.data()), 200);

        HttpResponse<String> response = get("/api/v1/entities/tickets/similar?id=11111111-1111-1111-1111-111111111111&k=3");

        assertStatusCode(response, 200);
        Map<String, Object> body = parseJsonResponse(response);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) body.get("matches");
        assertThat(matches).isNotEmpty();
        assertThat(matches.getFirst()).containsEntry("id", "22222222-2222-2222-2222-222222222222");
    }

    @Test
    @DisplayName("POST /api/v1/context/:collection/rag returns grounded answer from indexed context")
    void ragEndpointReturnsGroundedAnswer() throws Exception {
        DataCloudClient.Entity entityA = entity("11111111-1111-1111-1111-111111111111", "tickets", Map.of(
            "title", "login failure investigation",
            "summary", "user cannot sign in because password reset token expired"
        ));
        DataCloudClient.Entity entityB = entity("22222222-2222-2222-2222-222222222222", "tickets", Map.of(
            "title", "password reset troubleshooting",
            "summary", "reset token expired and user needs a new sign in link"
        ));

        when(mockClient.save(anyString(), eq("tickets"), any()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = invocation.getArgument(2, Map.class);
                String title = String.valueOf(payload.get("title"));
                return Promise.of(title.contains("login failure") ? entityA : entityB);
            });
        when(mockClient.appendEvent(anyString(), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        startServer();

        assertStatusCode(postJson("/api/v1/entities/tickets", entityA.data()), 200);
        assertStatusCode(postJson("/api/v1/entities/tickets", entityB.data()), 200);

        HttpResponse<String> response = postJson("/api/v1/context/tickets/rag", Map.of(
            "question", "How should I troubleshoot an expired password reset token?",
            "k", 3
        ));

        assertStatusCode(response, 200);
        Map<String, Object> body = parseJsonResponse(response);
        assertThat(body).containsEntry("collection", "tickets");
        assertThat(String.valueOf(body.get("answer"))).contains("Grounded answer");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> context = (List<Map<String, Object>>) body.get("context");
        assertThat(context).isNotEmpty();
    }

    private DataCloudClient.Entity entity(String id, String collection, Map<String, Object> data) {
        Instant now = Instant.now();
        return new DataCloudClient.Entity(id, collection, data, now, now, 1L);
    }
}
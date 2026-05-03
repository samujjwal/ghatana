/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditController}.
 *
 * <p>Covers the POST {@code /api/v1/audit/log} and GET {@code /api/v1/audit/query}
 * endpoints in ephemeral (no DataCloud) mode — the path most relevant for production
 * operations tooling when DataCloud is unavailable or not yet provisioned.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AuditController — append-only audit trail operations
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AuditController")
class AuditControllerTest extends EventloopTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** No DataCloud client — exercises the in-memory ephemeral path. */
    private AuditController controller;

    @BeforeEach
    void setUp() {
        controller = new AuditController(null);
    }

    // =========================================================================
    // POST /api/v1/audit/log
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/audit/log")
    class Log {

        @Test
        @DisplayName("valid audit event returns logged=true with an id")
        void validEvent_returnsLoggedTrue() throws Exception {
            String body = """
                    {
                      "id": "evt-001",
                      "timestamp": "2026-05-02T10:00:00Z",
                      "userId": "user-001",
                      "tenantId": "tenant-alpha",
                      "action": "agent.execute",
                      "resource": "agent/my-agent",
                      "status": "success"
                    }
                    """;
            HttpResponse response = runPromise(() -> controller.handleLog(buildPost(body)));

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode json = parseBody(response);
            assertThat(json.get("logged").asBoolean()).isTrue();
            assertThat(json.get("id").asText()).isNotBlank();
            assertThat(json.get("source").asText()).isNotBlank();
        }

        @Test
        @DisplayName("event without id gets a server-generated id")
        void eventWithoutId_getsGeneratedId() throws Exception {
            String body = """
                    {
                      "tenantId": "tenant-beta",
                      "action": "policy.approve",
                      "resource": "policy/governance-policy-001",
                      "status": "success"
                    }
                    """;
            HttpResponse response = runPromise(() -> controller.handleLog(buildPost(body)));

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode json = parseBody(response);
            assertThat(json.get("logged").asBoolean()).isTrue();
            assertThat(json.get("id").asText()).isNotBlank();
        }

        @Test
        @DisplayName("missing tenantId returns 400")
        void missingTenantId_returns400() throws Exception {
            String body = """
                    {
                      "action": "agent.execute",
                      "resource": "agent/my-agent",
                      "status": "success"
                    }
                    """;
            HttpResponse response = runPromise(() -> controller.handleLog(buildPost(body)));

            assertThat(response.getCode()).isEqualTo(400);
            JsonNode json = parseBody(response);
            assertThat(json.get("error").asText()).contains("tenantId");
        }

        @Test
        @DisplayName("missing action returns 400")
        void missingAction_returns400() throws Exception {
            String body = """
                    {
                      "tenantId": "tenant-alpha",
                      "resource": "agent/my-agent",
                      "status": "success"
                    }
                    """;
            HttpResponse response = runPromise(() -> controller.handleLog(buildPost(body)));

            assertThat(response.getCode()).isEqualTo(400);
            JsonNode json = parseBody(response);
            assertThat(json.get("error").asText()).contains("action");
        }

        @Test
        @DisplayName("missing status returns 400")
        void missingStatus_returns400() throws Exception {
            String body = """
                    {
                      "tenantId": "tenant-alpha",
                      "action": "agent.execute",
                      "resource": "agent/my-agent"
                    }
                    """;
            HttpResponse response = runPromise(() -> controller.handleLog(buildPost(body)));

            assertThat(response.getCode()).isEqualTo(400);
            JsonNode json = parseBody(response);
            assertThat(json.get("error").asText()).contains("status");
        }

        @Test
        @DisplayName("malformed JSON returns 400")
        void malformedJson_returns400() throws Exception {
            HttpResponse response = runPromise(() -> controller.handleLog(buildPost("{invalid-json}")));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("logged event includes server-side receivedAt timestamp")
        void loggedEvent_hasReceivedAtTimestamp() throws Exception {
            String body = """
                    {
                      "tenantId": "tenant-gamma",
                      "action": "data.export",
                      "resource": "dataset/export-001",
                      "status": "success"
                    }
                    """;
            // The controller enriches with receivedAt — verify the response timestamp is present and recent
            Instant before = Instant.now().minusSeconds(2);
            HttpResponse response = runPromise(() -> controller.handleLog(buildPost(body)));
            Instant after = Instant.now().plusSeconds(2);

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode json = parseBody(response);
            Instant ts = Instant.parse(json.get("timestamp").asText());
            assertThat(ts).isAfter(before).isBefore(after);
        }
    }

    // =========================================================================
    // GET /api/v1/audit/query
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/audit/query")
    class Query {

        @Test
        @DisplayName("query after logging returns events for the correct tenant")
        void queryAfterLogging_returnsEventsForTenant() throws Exception {
            // Log an event for tenant-query-A
            String body = """
                    {
                      "tenantId": "tenant-query-A",
                      "action": "agent.execute",
                      "resource": "agent/abc",
                      "status": "success"
                    }
                    """;
            runPromise(() -> controller.handleLog(buildPost(body)));

            HttpResponse response = runPromise(() ->
                    controller.handle(buildGet("?tenantId=tenant-query-A"), "query"));

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode json = parseBody(response);
            assertThat(json.has("events")).isTrue();
            assertThat(json.get("events").isArray()).isTrue();
            // At least 1 event for the tenant
            assertThat(json.get("events").size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("query for unknown tenant returns empty events array")
        void queryForUnknownTenant_returnsEmpty() throws Exception {
            HttpResponse response = runPromise(() ->
                    controller.handle(buildGet("?tenantId=nonexistent-tenant-xyz"), "query"));

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode json = parseBody(response);
            assertThat(json.get("events").size()).isEqualTo(0);
        }

        @Test
        @DisplayName("query for tenant-A does not return events logged for tenant-B")
        void queryIsolatesTenants() throws Exception {
            String bodyA = """
                    {"tenantId":"isolation-tenant-A","action":"agent.execute","resource":"r","status":"success"}
                    """;
            String bodyB = """
                    {"tenantId":"isolation-tenant-B","action":"agent.execute","resource":"r","status":"success"}
                    """;
            runPromise(() -> controller.handleLog(buildPost(bodyA)));
            runPromise(() -> controller.handleLog(buildPost(bodyB)));

            HttpResponse responseA = runPromise(() ->
                    controller.handle(buildGet("?tenantId=isolation-tenant-A"), "query"));
            JsonNode jsonA = parseBody(responseA);

            // None of the returned events should belong to tenant-B
            jsonA.get("events").forEach(event ->
                    assertThat(event.path("tenantId").asText()).isEqualTo("isolation-tenant-A"));
        }

        @Test
        @DisplayName("query response includes total and pagination metadata")
        void queryResponse_hasPaginationMetadata() throws Exception {
            HttpResponse response = runPromise(() ->
                    controller.handle(buildGet("?tenantId=meta-tenant"), "query"));

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode json = parseBody(response);
            assertThat(json.has("events")).isTrue();
            assertThat(json.has("total")).isTrue();
        }
    }

    // =========================================================================
    // Route dispatch — invalid paths return 404
    // =========================================================================

    @Nested
    @DisplayName("Route dispatch")
    class RouteDispatch {

        @Test
        @DisplayName("unknown path returns 404")
        void unknownPath_returns404() {
            HttpRequest request = mock(HttpRequest.class);
            when(request.getMethod()).thenReturn(HttpMethod.GET);
            HttpResponse response = runPromise(() -> controller.handle(request, "unknown-endpoint"));

            assertThat(response.getCode()).isEqualTo(404);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private HttpResponse runAndGet(Promise<HttpResponse> promise) {
        return promise.getResult();
    }

    private HttpRequest buildPost(String json) {
        return HttpRequest.post("http://localhost/api/v1/audit/log")
                .withBody(ByteBuf.wrapForReading(json.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    private HttpRequest buildGet(String queryString) {
        return HttpRequest.get("http://localhost/api/v1/audit/query" + queryString).build();
    }

    private JsonNode parseBody(HttpResponse response) throws Exception {
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        return MAPPER.readTree(body);
    }
}

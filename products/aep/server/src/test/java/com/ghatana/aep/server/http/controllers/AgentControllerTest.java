/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.observability.AepSloMetrics;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for AgentController execution error categorization and response semantics
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AgentController")
@ExtendWith(MockitoExtension.class)
class AgentControllerTest extends EventloopTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private AepEngine engine;

    @Mock
    private AepSloMetrics sloMetrics;

    @Mock
    private DataCloudClient dataCloudClient;

    @Mock
    private EntityStore entityStore;

    private AgentController controller;

    @BeforeEach
    void setUp() {
        controller = new AgentController(engine, null, sloMetrics);
    }

    @Test
    @DisplayName("listAgents returns complete registration metadata including discovery-only semantics")
    void listAgentsReturnsCompleteRegistrationMetadata() throws Exception {
        when(dataCloudClient.entityStore()).thenReturn(entityStore);
        AgentController dataCloudBackedController = new AgentController(engine, dataCloudClient, sloMetrics);
        when(entityStore.query(any(), any())).thenReturn(Promise.of(EntityStore.QueryResult.of(
            List.of(new EntityStore.Entity(
                EntityStore.EntityId.of("agent-manifest"),
                "aep_agents",
                Map.ofEntries(
                    Map.entry("id", "agent-manifest"),
                    Map.entry("name", "Manifest Agent"),
                    Map.entry("type", "PROBABILISTIC"),
                    Map.entry("status", "IDLE"),
                    Map.entry("version", "2.1.0"),
                    Map.entry("capabilities", List.of("discover", "classify")),
                    Map.entry("memoryCount", 7),
                    Map.entry("description", "Manifest only"),
                    Map.entry("registrationMode", "manifest-only"),
                    Map.entry("executable", false),
                    Map.entry("createdAt", "2026-04-21T10:15:30Z"),
                    Map.entry("updatedAt", "2026-04-21T11:15:30Z")
                ),
                EntityStore.EntityMetadata.empty()
            )),
            1L
        )));

        HttpResponse response = runPromise(() -> dataCloudBackedController.handleListAgents(mockTenantRequest("tenant-a")));
        Map<String, Object> body = parseBody(response);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> agents = (List<Map<String, Object>>) body.get("agents");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(agents).singleElement().satisfies(agent -> {
            assertThat(agent).containsEntry("tenantId", "tenant-a");
            assertThat(agent).containsEntry("version", "2.1.0");
            assertThat(agent).containsEntry("memoryCount", 7);
            assertThat(agent).containsEntry("registrationMode", "manifest-only");
            assertThat(agent).containsEntry("executable", false);
            assertThat(agent).containsEntry("registryStorage", "datacloud");
            assertThat(agent).containsEntry("memoryPersistence", "datacloud");
            assertThat(agent).containsEntry("registeredAt", "2026-04-21T10:15:30Z");
            assertThat(agent).containsEntry("lastSeen", "2026-04-21T11:15:30Z");
        });
    }

    @Test
    @DisplayName("getAgent flattens stored agent data into the public contract")
    void getAgentFlattensStoredAgentData() throws Exception {
        when(dataCloudClient.entityStore()).thenReturn(entityStore);
        AgentController dataCloudBackedController = new AgentController(engine, dataCloudClient, sloMetrics);
        when(entityStore.findById(any(), any())).thenReturn(Promise.of(Optional.of(new EntityStore.Entity(
            EntityStore.EntityId.of("agent-direct"),
            "aep_agents",
            Map.of(
                "id", "agent-direct",
                "name", "Direct Agent",
                "tenantId", "tenant-a",
                "type", "DETERMINISTIC",
                "version", "1.4.2",
                "status", "ACTIVE",
                "capabilities", List.of("score"),
                "config", Map.of("mode", "strict"),
                "createdAt", "2026-04-20T08:00:00Z"
            ),
            EntityStore.EntityMetadata.empty()
        ))));

        HttpResponse response = runPromise(() -> dataCloudBackedController.handleGetAgent(mockAgentRequest("agent-direct", "tenant-a")));
        Map<String, Object> body = parseBody(response);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(body).containsEntry("id", "agent-direct");
        assertThat(body).containsEntry("tenantId", "tenant-a");
        assertThat(body).containsEntry("registrationMode", "direct");
        assertThat(body).containsEntry("executable", true);
        assertThat(body).containsEntry("memoryCount", 0);
        assertThat(body).containsEntry("registeredAt", "2026-04-20T08:00:00Z");
        assertThat(body).containsEntry("registryStorage", "datacloud");
        assertThat(body).containsEntry("memoryPersistence", "datacloud");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) body.get("config");
        assertThat(config).containsEntry("mode", "strict");
    }

    @Test
    @DisplayName("returns 400 when input is not a JSON object")
    void executeRejectsNonObjectInput() throws Exception {
        HttpRequest request = mockExecuteRequest(
            "agent-1",
            "{\"tenantId\":\"tenant-a\",\"input\":\"invalid\"}"
        );

        HttpResponse response = runPromise(() -> controller.handleExecuteAgent(request));
        Map<String, Object> body = parseBody(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) body.get("detailsMap");

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(details).containsEntry("errorCode", "INVALID_INPUT");
        assertThat(details).containsEntry("category", "permanent");
        assertThat(details).containsEntry("retryable", false);
    }

    @Test
    @DisplayName("returns 503 with retry guidance when dependency is unavailable")
    void executeMapsUnavailableTo503() throws Exception {
        when(engine.process(anyString(), any()))
            .thenReturn(Promise.ofException(new IllegalStateException("DataCloud unavailable")));

        HttpRequest request = mockExecuteRequest(
            "agent-1",
            "{\"tenantId\":\"tenant-a\",\"input\":{\"k\":\"v\"}}"
        );

        HttpResponse response = runPromise(() -> controller.handleExecuteAgent(request));
        Map<String, Object> body = parseBody(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) body.get("detailsMap");

        assertThat(response.getCode()).isEqualTo(503);
        assertThat(details).containsEntry("errorCode", "AGENT_EXECUTION_DEPENDENCY_UNAVAILABLE");
        assertThat(details).containsEntry("retryable", true);
        verify(sloMetrics).recordAgentExecutionFailure(
            anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyLong());
    }

    @Test
    @DisplayName("returns 504 for timeout failures")
    void executeMapsTimeoutTo504() throws Exception {
        when(engine.process(anyString(), any()))
            .thenReturn(Promise.ofException(new TimeoutException("execution timeout")));

        HttpRequest request = mockExecuteRequest(
            "agent-1",
            "{\"tenantId\":\"tenant-a\",\"input\":{}}"
        );

        HttpResponse response = runPromise(() -> controller.handleExecuteAgent(request));
        Map<String, Object> body = parseBody(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) body.get("detailsMap");

        assertThat(response.getCode()).isEqualTo(504);
        assertThat(details).containsEntry("errorCode", "AGENT_EXECUTION_TIMEOUT");
        assertThat(details).containsEntry("retryable", true);
    }

    @Test
    @DisplayName("returns 403 for forbidden execution")
    void executeMapsForbiddenTo403() throws Exception {
        when(engine.process(anyString(), any()))
            .thenReturn(Promise.ofException(new SecurityException("forbidden by policy")));

        HttpRequest request = mockExecuteRequest(
            "agent-1",
            "{\"tenantId\":\"tenant-a\",\"input\":{}}"
        );

        HttpResponse response = runPromise(() -> controller.handleExecuteAgent(request));
        Map<String, Object> body = parseBody(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) body.get("detailsMap");

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(details).containsEntry("errorCode", "AGENT_EXECUTION_FORBIDDEN");
        assertThat(details).containsEntry("retryable", false);
    }

    @Test
    @DisplayName("returns 200 with execution summary on success")
    void executeReturnsSuccessPayload() throws Exception {
        when(engine.process(anyString(), any()))
            .thenReturn(Promise.of(new AepEngine.ProcessingResult(
                "event-1",
                true,
                java.util.List.of(),
                Map.of()
            )));

        HttpRequest request = mockExecuteRequest(
            "agent-1",
            "{\"tenantId\":\"tenant-a\",\"input\":{\"x\":1}}"
        );

        HttpResponse response = runPromise(() -> controller.handleExecuteAgent(request));
        Map<String, Object> body = parseBody(response);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(body).containsEntry("agentId", "agent-1");
        assertThat(body).containsEntry("tenantId", "tenant-a");
        assertThat(body).containsEntry("eventId", "event-1");
        verify(sloMetrics).recordAgentExecutionSuccess(anyString(), anyString(), anyLong());
    }

    private Map<String, Object> parseBody(HttpResponse response) throws Exception {
        String json = response.getBody().getString(StandardCharsets.UTF_8);
        return MAPPER.readValue(json, new TypeReference<>() {});
    }

    private HttpRequest mockTenantRequest(String tenantId) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getQueryParameter("tenantId")).thenReturn(tenantId);
        return request;
    }

    private HttpRequest mockAgentRequest(String agentId, String tenantId) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getPathParameter("agentId")).thenReturn(agentId);
        when(request.getQueryParameter("tenantId")).thenReturn(tenantId);
        return request;
    }

    private HttpRequest mockExecuteRequest(String agentId, String bodyJson) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getPathParameter("agentId")).thenReturn(agentId);
        when(request.getQueryParameter("tenantId")).thenReturn(null);
        when(request.getHeader(any())).thenReturn(null);
        when(request.loadBody()).thenReturn(Promise.of(ByteBuf.wrapForReading(bodyJson.getBytes(StandardCharsets.UTF_8))));
        return request;
    }
}

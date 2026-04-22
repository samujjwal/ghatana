/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.observability.AepSloMetrics;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
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
@DisplayName("AgentController [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class AgentControllerTest extends EventloopTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper(); // GH-90000

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
    void setUp() { // GH-90000
        controller = new AgentController(engine, null, sloMetrics); // GH-90000
    }

    @Test
    @DisplayName("listAgents returns complete registration metadata including discovery-only semantics [GH-90000]")
    void listAgentsReturnsCompleteRegistrationMetadata() throws Exception { // GH-90000
        when(dataCloudClient.entityStore()).thenReturn(entityStore); // GH-90000
        AgentController dataCloudBackedController = new AgentController(engine, dataCloudClient, sloMetrics); // GH-90000
        when(entityStore.query(any(), any())).thenReturn(Promise.of(EntityStore.QueryResult.of( // GH-90000
            List.of(new EntityStore.Entity( // GH-90000
                EntityStore.EntityId.of("agent-manifest [GH-90000]"),
                "aep_agents",
                Map.ofEntries( // GH-90000
                    Map.entry("id", "agent-manifest"), // GH-90000
                    Map.entry("name", "Manifest Agent"), // GH-90000
                    Map.entry("type", "PROBABILISTIC"), // GH-90000
                    Map.entry("status", "IDLE"), // GH-90000
                    Map.entry("version", "2.1.0"), // GH-90000
                    Map.entry("capabilities", List.of("discover", "classify")), // GH-90000
                    Map.entry("memoryCount", 7), // GH-90000
                    Map.entry("description", "Manifest only"), // GH-90000
                    Map.entry("registrationMode", "manifest-only"), // GH-90000
                    Map.entry("executable", false), // GH-90000
                    Map.entry("createdAt", "2026-04-21T10:15:30Z"), // GH-90000
                    Map.entry("updatedAt", "2026-04-21T11:15:30Z") // GH-90000
                ),
                EntityStore.EntityMetadata.empty() // GH-90000
            )),
            1L
        )));

        HttpResponse response = runPromise(() -> dataCloudBackedController.handleListAgents(mockTenantRequest("tenant-a [GH-90000]")));
        Map<String, Object> body = parseBody(response); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        List<Map<String, Object>> agents = (List<Map<String, Object>>) body.get("agents [GH-90000]");

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(agents).singleElement().satisfies(agent -> { // GH-90000
            assertThat(agent).containsEntry("tenantId", "tenant-a"); // GH-90000
            assertThat(agent).containsEntry("version", "2.1.0"); // GH-90000
            assertThat(agent).containsEntry("memoryCount", 7); // GH-90000
            assertThat(agent).containsEntry("registrationMode", "manifest-only"); // GH-90000
            assertThat(agent).containsEntry("executable", false); // GH-90000
            assertThat(agent).containsEntry("registryStorage", "datacloud"); // GH-90000
            assertThat(agent).containsEntry("memoryPersistence", "datacloud"); // GH-90000
            assertThat(agent).containsEntry("registeredAt", "2026-04-21T10:15:30Z"); // GH-90000
            assertThat(agent).containsEntry("lastSeen", "2026-04-21T11:15:30Z"); // GH-90000
        });
    }

    @Test
    @DisplayName("getAgent flattens stored agent data into the public contract [GH-90000]")
    void getAgentFlattensStoredAgentData() throws Exception { // GH-90000
        when(dataCloudClient.entityStore()).thenReturn(entityStore); // GH-90000
        AgentController dataCloudBackedController = new AgentController(engine, dataCloudClient, sloMetrics); // GH-90000
        when(entityStore.findById(any(), any())).thenReturn(Promise.of(Optional.of(new EntityStore.Entity( // GH-90000
            EntityStore.EntityId.of("agent-direct [GH-90000]"),
            "aep_agents",
            Map.of( // GH-90000
                "id", "agent-direct",
                "name", "Direct Agent",
                "tenantId", "tenant-a",
                "type", "DETERMINISTIC",
                "version", "1.4.2",
                "status", "ACTIVE",
                "capabilities", List.of("score [GH-90000]"),
                "config", Map.of("mode", "strict"), // GH-90000
                "createdAt", "2026-04-20T08:00:00Z"
            ),
            EntityStore.EntityMetadata.empty() // GH-90000
        ))));

        HttpResponse response = runPromise(() -> dataCloudBackedController.handleGetAgent(mockAgentRequest("agent-direct", "tenant-a"))); // GH-90000
        Map<String, Object> body = parseBody(response); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(body).containsEntry("id", "agent-direct"); // GH-90000
        assertThat(body).containsEntry("tenantId", "tenant-a"); // GH-90000
        assertThat(body).containsEntry("registrationMode", "direct"); // GH-90000
        assertThat(body).containsEntry("executable", true); // GH-90000
        assertThat(body).containsEntry("memoryCount", 0); // GH-90000
        assertThat(body).containsEntry("registeredAt", "2026-04-20T08:00:00Z"); // GH-90000
        assertThat(body).containsEntry("registryStorage", "datacloud"); // GH-90000
        assertThat(body).containsEntry("memoryPersistence", "datacloud"); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> config = (Map<String, Object>) body.get("config [GH-90000]");
        assertThat(config).containsEntry("mode", "strict"); // GH-90000
    }

    @Test
    @DisplayName("returns 400 when input is not a JSON object [GH-90000]")
    void executeRejectsNonObjectInput() throws Exception { // GH-90000
        HttpRequest request = mockExecuteRequest( // GH-90000
            "agent-1",
            "{\"tenantId\":\"tenant-a\",\"input\":\"invalid\"}"
        );

        HttpResponse response = runPromise(() -> controller.handleExecuteAgent(request)); // GH-90000
        Map<String, Object> body = parseBody(response); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> details = (Map<String, Object>) body.get("detailsMap [GH-90000]");

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
        assertThat(details).containsEntry("errorCode", "INVALID_INPUT"); // GH-90000
        assertThat(details).containsEntry("category", "permanent"); // GH-90000
        assertThat(details).containsEntry("retryable", false); // GH-90000
    }

    @Test
    @DisplayName("returns 503 with retry guidance when dependency is unavailable [GH-90000]")
    void executeMapsUnavailableTo503() throws Exception { // GH-90000
        when(engine.process(anyString(), any())) // GH-90000
            .thenReturn(Promise.ofException(new IllegalStateException("DataCloud unavailable [GH-90000]")));

        HttpRequest request = mockExecuteRequest( // GH-90000
            "agent-1",
            "{\"tenantId\":\"tenant-a\",\"input\":{\"k\":\"v\"}}"
        );

        HttpResponse response = runPromise(() -> controller.handleExecuteAgent(request)); // GH-90000
        Map<String, Object> body = parseBody(response); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> details = (Map<String, Object>) body.get("detailsMap [GH-90000]");

        assertThat(response.getCode()).isEqualTo(503); // GH-90000
        assertThat(details).containsEntry("errorCode", "AGENT_EXECUTION_DEPENDENCY_UNAVAILABLE"); // GH-90000
        assertThat(details).containsEntry("retryable", true); // GH-90000
        verify(sloMetrics).recordAgentExecutionFailure( // GH-90000
            anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyLong()); // GH-90000
    }

    @Test
    @DisplayName("returns 504 for timeout failures [GH-90000]")
    void executeMapsTimeoutTo504() throws Exception { // GH-90000
        when(engine.process(anyString(), any())) // GH-90000
            .thenReturn(Promise.ofException(new TimeoutException("execution timeout [GH-90000]")));

        HttpRequest request = mockExecuteRequest( // GH-90000
            "agent-1",
            "{\"tenantId\":\"tenant-a\",\"input\":{}}"
        );

        HttpResponse response = runPromise(() -> controller.handleExecuteAgent(request)); // GH-90000
        Map<String, Object> body = parseBody(response); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> details = (Map<String, Object>) body.get("detailsMap [GH-90000]");

        assertThat(response.getCode()).isEqualTo(504); // GH-90000
        assertThat(details).containsEntry("errorCode", "AGENT_EXECUTION_TIMEOUT"); // GH-90000
        assertThat(details).containsEntry("retryable", true); // GH-90000
    }

    @Test
    @DisplayName("returns 403 for forbidden execution [GH-90000]")
    void executeMapsForbiddenTo403() throws Exception { // GH-90000
        when(engine.process(anyString(), any())) // GH-90000
            .thenReturn(Promise.ofException(new SecurityException("forbidden by policy [GH-90000]")));

        HttpRequest request = mockExecuteRequest( // GH-90000
            "agent-1",
            "{\"tenantId\":\"tenant-a\",\"input\":{}}"
        );

        HttpResponse response = runPromise(() -> controller.handleExecuteAgent(request)); // GH-90000
        Map<String, Object> body = parseBody(response); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> details = (Map<String, Object>) body.get("detailsMap [GH-90000]");

        assertThat(response.getCode()).isEqualTo(403); // GH-90000
        assertThat(details).containsEntry("errorCode", "AGENT_EXECUTION_FORBIDDEN"); // GH-90000
        assertThat(details).containsEntry("retryable", false); // GH-90000
    }

    @Test
    @DisplayName("returns 200 with execution summary on success [GH-90000]")
    void executeReturnsSuccessPayload() throws Exception { // GH-90000
        when(engine.process(anyString(), any())) // GH-90000
            .thenReturn(Promise.of(new AepEngine.ProcessingResult( // GH-90000
                "event-1",
                true,
                java.util.List.of(), // GH-90000
                Map.of() // GH-90000
            )));

        HttpRequest request = mockExecuteRequest( // GH-90000
            "agent-1",
            "{\"tenantId\":\"tenant-a\",\"input\":{\"x\":1}}"
        );

        HttpResponse response = runPromise(() -> controller.handleExecuteAgent(request)); // GH-90000
        Map<String, Object> body = parseBody(response); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(body).containsEntry("agentId", "agent-1"); // GH-90000
        assertThat(body).containsEntry("tenantId", "tenant-a"); // GH-90000
        assertThat(body).containsEntry("eventId", "event-1"); // GH-90000
        verify(sloMetrics).recordAgentExecutionSuccess(anyString(), anyString(), anyLong()); // GH-90000
    }

    @Test
    @DisplayName("AR-4: getAgent enforces tenant-scoped reads")
    void getAgentEnforcesTenantIsolation() throws Exception {
        when(dataCloudClient.entityStore()).thenReturn(entityStore);
        AgentController dataCloudBackedController = new AgentController(engine, dataCloudClient, sloMetrics);

        EntityStore.Entity tenantBAgent = new EntityStore.Entity(
            EntityStore.EntityId.of("agent-cross"),
            "aep_agents",
            Map.of(
                "id", "agent-cross",
                "name", "Tenant B Agent",
                "tenantId", "tenant-b",
                "type", "DETERMINISTIC",
                "status", "ACTIVE"
            ),
            EntityStore.EntityMetadata.empty()
        );

        when(entityStore.findById(any(), any())).thenAnswer(invocation -> {
            TenantContext tenantContext = invocation.getArgument(0);
            EntityStore.EntityId entityId = invocation.getArgument(1);
            if ("tenant-b".equals(tenantContext.tenantId()) && "agent-cross".equals(entityId.value())) {
                return Promise.of(Optional.of(tenantBAgent));
            }
            return Promise.of(Optional.empty());
        });

        HttpResponse tenantARead = runPromise(
            () -> dataCloudBackedController.handleGetAgent(mockAgentRequestCanonical("agent-cross", "tenant-a")));
        Map<String, Object> tenantABody = parseBody(tenantARead);
        assertThat(tenantARead.getCode()).isEqualTo(404);
        assertThat(tenantABody.get("message").toString()).contains("Agent not found");

        HttpResponse tenantBRead = runPromise(
            () -> dataCloudBackedController.handleGetAgent(mockAgentRequestCanonical("agent-cross", "tenant-b")));
        Map<String, Object> tenantBBody = parseBody(tenantBRead);
        assertThat(tenantBRead.getCode()).isEqualTo(200);
        assertThat(tenantBBody).containsEntry("tenantId", "tenant-b");
        assertThat(tenantBBody).containsEntry("id", "agent-cross");
    }

    @Test
    @DisplayName("AR-4: listAgents is isolated per tenant query")
    void listAgentsUsesTenantScopedQueryContext() throws Exception {
        when(dataCloudClient.entityStore()).thenReturn(entityStore);
        AgentController dataCloudBackedController = new AgentController(engine, dataCloudClient, sloMetrics);

        when(entityStore.query(any(), any())).thenAnswer(invocation -> {
            TenantContext tenantContext = invocation.getArgument(0);
            if ("tenant-a".equals(tenantContext.tenantId())) {
                return Promise.of(EntityStore.QueryResult.of(List.of(new EntityStore.Entity(
                    EntityStore.EntityId.of("agent-a"),
                    "aep_agents",
                    Map.of("id", "agent-a", "tenantId", "tenant-a", "type", "DETERMINISTIC"),
                    EntityStore.EntityMetadata.empty()
                ))));
            }
            return Promise.of(EntityStore.QueryResult.of(List.of(new EntityStore.Entity(
                EntityStore.EntityId.of("agent-b"),
                "aep_agents",
                Map.of("id", "agent-b", "tenantId", "tenant-b", "type", "PROBABILISTIC"),
                EntityStore.EntityMetadata.empty()
            ))));
        });

        HttpResponse tenantAResponse = runPromise(
            () -> dataCloudBackedController.handleListAgents(mockTenantRequestCanonical("tenant-a")));
        HttpResponse tenantBResponse = runPromise(
            () -> dataCloudBackedController.handleListAgents(mockTenantRequestCanonical("tenant-b")));

        Map<String, Object> tenantABody = parseBody(tenantAResponse);
        Map<String, Object> tenantBBody = parseBody(tenantBResponse);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tenantAAgents = (List<Map<String, Object>>) tenantABody.get("agents");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tenantBAgents = (List<Map<String, Object>>) tenantBBody.get("agents");
        Map<String, Object> tenantAAgent = tenantAAgents.get(0);
        Map<String, Object> tenantBAgent = tenantBAgents.get(0);

        assertThat(tenantAResponse.getCode()).isEqualTo(200);
        assertThat(tenantAAgents).hasSize(1);
        assertThat(tenantAAgent).containsEntry("id", "agent-a");
        assertThat(tenantAAgent).containsEntry("tenantId", "tenant-a");

        assertThat(tenantBResponse.getCode()).isEqualTo(200);
        assertThat(tenantBAgents).hasSize(1);
        assertThat(tenantBAgent).containsEntry("id", "agent-b");
        assertThat(tenantBAgent).containsEntry("tenantId", "tenant-b");
    }

    private Map<String, Object> parseBody(HttpResponse response) throws Exception { // GH-90000
        String json = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        return MAPPER.readValue(json, new TypeReference<>() {}); // GH-90000
    }

    private HttpRequest mockTenantRequest(String tenantId) { // GH-90000
        HttpRequest request = mock(HttpRequest.class); // GH-90000
        when(request.getQueryParameter("tenantId [GH-90000]")).thenReturn(tenantId);
        return request;
    }

    private HttpRequest mockAgentRequest(String agentId, String tenantId) { // GH-90000
        HttpRequest request = mock(HttpRequest.class); // GH-90000
        when(request.getPathParameter("agentId [GH-90000]")).thenReturn(agentId);
        when(request.getQueryParameter("tenantId [GH-90000]")).thenReturn(tenantId);
        return request;
    }

    private HttpRequest mockTenantRequestCanonical(String tenantId) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getQueryParameter("tenantId")).thenReturn(tenantId);
        return request;
    }

    private HttpRequest mockAgentRequestCanonical(String agentId, String tenantId) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getPathParameter("agentId")).thenReturn(agentId);
        when(request.getQueryParameter("tenantId")).thenReturn(tenantId);
        return request;
    }

    private HttpRequest mockExecuteRequest(String agentId, String bodyJson) { // GH-90000
        HttpRequest request = mock(HttpRequest.class); // GH-90000
        when(request.getPathParameter("agentId [GH-90000]")).thenReturn(agentId);
        when(request.getQueryParameter("tenantId [GH-90000]")).thenReturn(null);
        when(request.getHeader(any())).thenReturn(null); // GH-90000
        when(request.loadBody()).thenReturn(Promise.of(ByteBuf.wrapForReading(bodyJson.getBytes(StandardCharsets.UTF_8)))); // GH-90000
        return request;
    }
}

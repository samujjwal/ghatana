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
import com.ghatana.platform.domain.eventstore.TenantContext;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
        when(dataCloudClient.entityStore()).thenReturn(entityStore);
        lenient().when(entityStore.findByRef(any(), any())).thenReturn(Promise.of(Optional.of(
            new EntityStore.Entity(
                EntityStore.EntityId.of("agent-1"),
                "aep_agents",
                Map.of(
                    "id", "agent-1",
                    "tenantId", "tenant-a",
                    "status", "ACTIVE",
                    "executable", true,
                    "approved", true
                ),
                EntityStore.EntityMetadata.empty()
            )
        )));
        controller = new AgentController(engine, dataCloudClient, sloMetrics); 
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
        when(entityStore.findByRef(any(), any())).thenReturn(Promise.of(Optional.of(new EntityStore.Entity( 
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

        when(entityStore.findByRef(any(), any())).thenAnswer(invocation -> {
            TenantContext tenantContext = invocation.getArgument(0);
            EntityStore.EntityRef ref = invocation.getArgument(1);
            if ("tenant-b".equals(tenantContext.tenantId()) && "agent-cross".equals(ref.entityId().value())) {
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

    @Test
    @DisplayName("AR-LC-1: lifecycle approve transitions status and emits provenance")
    void lifecycleApproveTransitionsStatusAndEmitsProvenance() throws Exception {
        HttpRequest request = mockLifecycleRequest("agent-1", "approve", "tenant-a");

        when(entityStore.findByRef(any(), any())).thenReturn(Promise.of(Optional.of(
            new EntityStore.Entity(
                EntityStore.EntityId.of("agent-1"),
                "aep_agents",
                Map.of(
                    "id", "agent-1",
                    "tenantId", "tenant-a",
                    "status", "SCANNED",
                    "approved", false,
                    "executable", true
                ),
                EntityStore.EntityMetadata.empty()
            )
        )));
        when(entityStore.save(any(), any())).thenReturn(Promise.of(
            new EntityStore.Entity(
                EntityStore.EntityId.of("agent-1"),
                "aep_agents",
                Map.of("id", "agent-1", "tenantId", "tenant-a", "status", "APPROVED"),
                EntityStore.EntityMetadata.empty()
            )
        ));
        when(engine.process(eq("tenant-a"), any()))
            .thenReturn(Promise.of(new AepEngine.ProcessingResult(
                "evt-agent-lifecycle", true, List.of(), Map.of()
            )));

        HttpResponse response = runPromise(() -> controller.handleLifecycleTransition(request));
        Map<String, Object> body = parseBody(response);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(body).containsEntry("agentId", "agent-1");
        assertThat(body).containsEntry("status", "APPROVED");
        assertThat(body).containsEntry("transitioned", true);
        assertThat(body).containsEntry("eventId", "evt-agent-lifecycle");
    }

    @Test
    @DisplayName("AR-LC-2: lifecycle simulate emits provenance without persistence update")
    void lifecycleSimulateEmitsProvenanceWithoutPersistenceUpdate() throws Exception {
        HttpRequest request = mockLifecycleRequest("agent-1", "simulate", "tenant-a");

        when(entityStore.findByRef(any(), any())).thenReturn(Promise.of(Optional.of(
            new EntityStore.Entity(
                EntityStore.EntityId.of("agent-1"),
                "aep_agents",
                Map.of(
                    "id", "agent-1",
                    "tenantId", "tenant-a",
                    "status", "ACTIVE"
                ),
                EntityStore.EntityMetadata.empty()
            )
        )));
        when(engine.process(eq("tenant-a"), any()))
            .thenReturn(Promise.of(new AepEngine.ProcessingResult(
                "evt-agent-sim", true, List.of(), Map.of()
            )));

        HttpResponse response = runPromise(() -> controller.handleLifecycleTransition(request));
        Map<String, Object> body = parseBody(response);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(body).containsEntry("action", "simulate");
        assertThat(body).containsEntry("simulationRequested", true);
        assertThat(body).containsEntry("eventId", "evt-agent-sim");

        verify(entityStore, never()).save(any(), any());
    }

    @Test
    @DisplayName("AR-LC-3: lifecycle rejects invalid transitions")
    void lifecycleRejectsInvalidTransitions() throws Exception {
        HttpRequest request = mockLifecycleRequest("agent-1", "install", "tenant-a");

        when(entityStore.findByRef(any(), any())).thenReturn(Promise.of(Optional.of(
            new EntityStore.Entity(
                EntityStore.EntityId.of("agent-1"),
                "aep_agents",
                Map.of(
                    "id", "agent-1",
                    "tenantId", "tenant-a",
                    "status", "DRAFT",
                    "approved", false,
                    "executable", true
                ),
                EntityStore.EntityMetadata.empty()
            )
        )));

        HttpResponse response = runPromise(() -> controller.handleLifecycleTransition(request));
        Map<String, Object> body = parseBody(response);

        assertThat(response.getCode()).isEqualTo(409);
        assertThat(String.valueOf(body.get("message"))).contains("Invalid lifecycle transition");
        verify(entityStore, never()).save(any(), any());
        verify(engine, never()).process(anyString(), any());
    }

    @Test
    @DisplayName("AR-RV-1: review endpoint emits provenance when outcome exists")
    void reviewEndpointEmitsProvenance() throws Exception {
        HttpRequest request = mockReviewRequest(
            "agent-1",
            "tenant-a",
            "{\"outcome\":\"accepted\",\"reviewer\":\"qa\",\"score\":0.87}"
        );

        when(engine.process(eq("tenant-a"), any()))
            .thenReturn(Promise.of(new AepEngine.ProcessingResult(
                "evt-agent-review", true, List.of(), Map.of()
            )));

        HttpResponse response = runPromise(() -> controller.handleRecordAgentReview(request));
        Map<String, Object> body = parseBody(response);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(body).containsEntry("agentId", "agent-1");
        assertThat(body).containsEntry("recorded", true);
        assertThat(body).containsEntry("eventId", "evt-agent-review");
    }

    @Test
    @DisplayName("AR-RV-2: review endpoint rejects missing outcome")
    void reviewEndpointRejectsMissingOutcome() throws Exception {
        HttpRequest request = mockReviewRequest(
            "agent-1",
            "tenant-a",
            "{\"reviewer\":\"qa\"}"
        );

        HttpResponse response = runPromise(() -> controller.handleRecordAgentReview(request));
        Map<String, Object> body = parseBody(response);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(String.valueOf(body.get("message"))).contains("Review outcome is required");
        verify(engine, never()).process(anyString(), any());
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

    private HttpRequest mockExecuteRequest(String agentId, String bodyJson) { 
        HttpRequest request = mock(HttpRequest.class); 
        when(request.getPathParameter("agentId")).thenReturn(agentId);
        when(request.getQueryParameter("tenantId")).thenReturn(null);
        when(request.getHeader(any())).thenReturn(null); 
        when(request.loadBody()).thenReturn(Promise.of(ByteBuf.wrapForReading(bodyJson.getBytes(StandardCharsets.UTF_8)))); 
        return request;
    }

    private HttpRequest mockLifecycleRequest(String agentId, String action, String tenantId) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getPathParameter("agentId")).thenReturn(agentId);
        when(request.getPathParameter("action")).thenReturn(action);
        when(request.getQueryParameter("tenantId")).thenReturn(tenantId);
        return request;
    }

    private HttpRequest mockReviewRequest(String agentId, String tenantId, String bodyJson) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getPathParameter("agentId")).thenReturn(agentId);
        when(request.getQueryParameter("tenantId")).thenReturn(tenantId);
        when(request.loadBody()).thenReturn(Promise.of(ByteBuf.wrapForReading(bodyJson.getBytes(StandardCharsets.UTF_8))));
        return request;
    }

    // ==================== Security scan: agent registration ====================

    @Test
    @DisplayName("AR-SEC-1: agent registration rejects suspicious code execution patterns")
    void registrationRejectsSuspiciousCodePatterns() throws Exception {
        when(dataCloudClient.entityStore()).thenReturn(entityStore);
        AgentController registrationController = new AgentController(engine, dataCloudClient, sloMetrics);

        HttpRequest request = mockRegisterRequest(
            "{\"tenantId\":\"tenant-a\",\"id\":\"bad-agent\",\"code\":\"Runtime.exec('rm -rf /')\"}"
        );

        HttpResponse response = runPromise(() -> registrationController.handleRegisterAgent(request));
        assertThat(response.getCode()).isEqualTo(403);
        Map<String, Object> body = parseBody(response);
        assertThat(body.get("message").toString()).contains("Security scan failed");
    }

    @Test
    @DisplayName("AR-SEC-2: agent registration rejects injection attack patterns")
    void registrationRejectsInjectionPatterns() throws Exception {
        when(dataCloudClient.entityStore()).thenReturn(entityStore);
        AgentController registrationController = new AgentController(engine, dataCloudClient, sloMetrics);

        HttpRequest request = mockRegisterRequest(
            "{\"tenantId\":\"tenant-a\",\"id\":\"inject-agent\",\"desc\":\"'; DROP TABLE agents;--\"}"
        );

        HttpResponse response = runPromise(() -> registrationController.handleRegisterAgent(request));
        assertThat(response.getCode()).isEqualTo(403);
        Map<String, Object> body = parseBody(response);
        assertThat(body.get("message").toString()).contains("Security scan failed");
    }

    @Test
    @DisplayName("AR-SEC-3: agent registration with clean payload proceeds to store save")
    void registrationWithCleanPayloadProceedsToStoreSave() throws Exception {
        when(dataCloudClient.entityStore()).thenReturn(entityStore);
        AgentController registrationController = new AgentController(engine, dataCloudClient, sloMetrics);
        when(entityStore.save(any(com.ghatana.platform.domain.eventstore.TenantContext.class), any(EntityStore.Entity.class))).thenReturn(Promise.of(
            new EntityStore.Entity(
                EntityStore.EntityId.of("clean-agent"),
                "aep_agents",
                Map.of("id", "clean-agent", "tenantId", "tenant-a", "status", "ACTIVE"),
                EntityStore.EntityMetadata.empty())));

        HttpRequest request = mockRegisterRequest(
            "{\"tenantId\":\"tenant-a\",\"id\":\"clean-agent\",\"name\":\"Safe Agent\",\"type\":\"DETERMINISTIC\"}"
        );

        HttpResponse response = runPromise(() -> registrationController.handleRegisterAgent(request));
        assertThat(response.getCode()).isEqualTo(200);
        Map<String, Object> body = parseBody(response);
        assertThat(body).containsEntry("status", "ACTIVE");
        assertThat(body).containsEntry("tenantId", "tenant-a");
    }

    @Test
    @DisplayName("AR-SEC-4: agent registration returns 503 when agentStore is not configured")
    void registrationReturns503WhenStoreNotConfigured() throws Exception {
        AgentController unconfiguredController = new AgentController(engine, null, sloMetrics);
        HttpRequest request = mockRegisterRequest(
            "{\"tenantId\":\"tenant-a\",\"name\":\"Agent\"}"
        );

        HttpResponse response = runPromise(() -> unconfiguredController.handleRegisterAgent(request));
        assertThat(response.getCode()).isEqualTo(503);
    }

    private HttpRequest mockRegisterRequest(String bodyJson) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getQueryParameter("tenantId")).thenReturn(null);
        when(request.getHeader(any())).thenReturn(null);
        when(request.loadBody()).thenReturn(Promise.of(ByteBuf.wrapForReading(bodyJson.getBytes(StandardCharsets.UTF_8))));
        return request;
    }
}

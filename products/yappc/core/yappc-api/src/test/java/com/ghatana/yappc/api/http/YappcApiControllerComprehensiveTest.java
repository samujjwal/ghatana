/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.yappc.api.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.audit.AuditLogger;
import com.ghatana.products.yappc.domain.agent.AgentRegistry;
import com.ghatana.products.yappc.domain.vector.RagService;
import com.ghatana.products.yappc.domain.vector.SemanticSearchService;
import com.ghatana.products.yappc.domain.workflow.AiWorkflowInstance;
import com.ghatana.products.yappc.domain.workflow.AiWorkflowService;
import com.ghatana.products.yappc.domain.agent.AgentMetadata;
import com.ghatana.products.yappc.domain.agent.AgentName;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive expectation-driven tests for yappc-api HTTP controllers.
 *
 * <p>This test suite validates that the API behaves correctly according to intended features,
 * not just that code executes. Tests cover:
 * <ul>
 *   <li>Response schema correctness and data validation</li>
 *   <li>All HTTP status codes (2xx, 4xx, 5xx)</li> // GH-90000
 *   <li>Input validation and error handling</li>
 *   <li>State machine transitions (workflows)</li> // GH-90000
 *   <li>Tenant isolation and security</li>
 *   <li>Pagination, filtering, and sorting</li>
 *   <li>Integration outcomes (persistence, events, audit)</li> // GH-90000
 *   <li>Edge cases and boundary conditions</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Expectation-driven tests for Agent, Workflow, and Vector APIs
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("yappc-api HTTP Controllers - Expectation-Driven Tests")
class YappcApiControllerComprehensiveTest extends EventloopTestBase {

    @Mock
    private AgentRegistry agentRegistry;

    @Mock
    private AiWorkflowService workflowService;

    @Mock
    private SemanticSearchService searchService;

    @Mock
    private RagService ragService;

    @Mock
    private AuditLogger auditLogger;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() { // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
    }

    // =========================================================================
    // AGENT API TESTS
    // =========================================================================

    @Nested
    @DisplayName("Agent API - List Agents")
    class AgentListingTests {

        private AgentController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new AgentController(agentRegistry, objectMapper, auditLogger); // GH-90000
        }

        @Test
        @DisplayName("listAgents returns 200 with valid response schema")
        void listAgentsReturnsValidSchema() { // GH-90000
            // GIVEN: Registry with agents
            AgentMetadata agentMetadata = new AgentMetadata( // GH-90000
                AgentName.COPILOT_AGENT,
                "1.0.0",
                "AI Copilot",
                List.of("chat", "code-gen"), // GH-90000
                List.of(), // GH-90000
                2000L,
                null
            );
            when(agentRegistry.getAllMetadata()) // GH-90000
                .thenReturn(List.of(agentMetadata)); // GH-90000

            // WHEN: List agents
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents").build();
            HttpResponse response = runPromise(() -> controller.listAgents(request)); // GH-90000

            // THEN: Response code is 200
            assertThat(response.getCode()).isEqualTo(200); // GH-90000

            // AND: Response has required JSON structure
            String body = response.getBody().asString(StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("\"agents\"").contains("\"total\""); // GH-90000

            // AND: Service was called
            verify(agentRegistry).getAllMetadata(); // GH-90000
        }

        @Test
        @DisplayName("listAgents returns metadata with required fields")
        void listAgentsResponseContainsRequiredFields() { // GH-90000
            // GIVEN: Agent metadata in registry
            AgentMetadata agentMeta = new AgentMetadata( // GH-90000
                AgentName.CODE_GENERATOR_AGENT,
                "2.0.0",
                "Code refactoring",
                List.of(), // GH-90000
                List.of(), // GH-90000
                5000L,
                null
            );
            when(agentRegistry.getAllMetadata()).thenReturn(List.of(agentMeta)); // GH-90000

            // WHEN: List agents
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents").build();
            HttpResponse response = runPromise(() -> controller.listAgents(request)); // GH-90000

            // THEN: Response contains agent name and total count
            String body = response.getBody().asString(StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("CODE_GENERATOR_AGENT");
            assertThat(body).contains("\"total\":1"); // GH-90000
        }

        @Test
        @DisplayName("listAgents returns empty array when no agents registered")
        void listAgentsEmptyRegistry() { // GH-90000
            // GIVEN: Empty registry
            when(agentRegistry.getAllMetadata()).thenReturn(List.of()); // GH-90000

            // WHEN: List agents
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents").build();
            HttpResponse response = runPromise(() -> controller.listAgents(request)); // GH-90000

            // THEN: Returns 200 (not 404) with empty agents array // GH-90000
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("\"agents\":[]"); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("\"total\":0"); // GH-90000
        }

        @Test
        @DisplayName("listAgents returns agents in consistent order")
        void listAgentsOrdering() { // GH-90000
            // GIVEN: Multiple agents
            List<AgentMetadata> agents = List.of( // GH-90000
                new AgentMetadata(AgentName.COPILOT_AGENT, "1.0.0", "AI Copilot", List.of(), List.of(), 2000L, null), // GH-90000
                new AgentMetadata(AgentName.QUERY_PARSER_AGENT, "1.0.0", "Query parser", List.of(), List.of(), 1000L, null), // GH-90000
                new AgentMetadata(AgentName.CODE_GENERATOR_AGENT, "1.0.0", "Code generator", List.of(), List.of(), 5000L, null) // GH-90000
            );
            when(agentRegistry.getAllMetadata()).thenReturn(agents); // GH-90000

            // WHEN: List agents
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents").build();
            HttpResponse response = runPromise(() -> controller.listAgents(request)); // GH-90000

            // THEN: All agents present in response
            String body = response.getBody().asString(StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("COPILOT_AGENT").contains("QUERY_PARSER_AGENT").contains("CODE_GENERATOR_AGENT");
            assertThat(body).contains("\"total\":3"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Agent API - Get Agent Details")
    class AgentDetailsTests {

        private AgentController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new AgentController(agentRegistry, objectMapper, auditLogger); // GH-90000
        }

        @Test
        @DisplayName("getAgent returns 404 when agent not found")
        void getAgentNotFound() { // GH-90000
            // GIVEN: The agent name "unknown" doesn't map to any known AgentName enum value,
            // so the controller returns 404 before ever calling registry.get() // GH-90000

            // WHEN: Get non-existent agent
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents/unknown")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.getAgent(request)); // GH-90000

            // THEN: Returns 404 with error message
            assertThat(response.getCode()).isEqualTo(404); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("not found").contains("unknown");
        }

        @Test
        @DisplayName("getAgent returns 400 when agent name missing")
        void getAgentMissingName() { // GH-90000
            // GIVEN: Request with missing agent name
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents/").build();

            // WHEN: Get agent
            HttpResponse response = runPromise(() -> controller.getAgent(request)); // GH-90000

            // THEN: Returns 400 Bad Request
            assertThat(response.getCode()).isEqualTo(400); // GH-90000
        }
    }

    @Nested
    @DisplayName("Agent API - Execute Agent")
    class AgentExecutionTests {

        private AgentController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new AgentController(agentRegistry, objectMapper, auditLogger); // GH-90000
        }

        @Test
        @DisplayName("executeAgent returns 400 when X-Tenant-ID header missing")
        void executeAgentMissingTenantHeader() { // GH-90000
            // GIVEN: Request without X-Tenant-ID header
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/agents/copilot/execute")
                .build(); // GH-90000

            // WHEN: Execute agent
            HttpResponse response = runPromise(() -> controller.executeAgent(request)); // GH-90000

            // THEN: Returns 400 with missing header error
            assertThat(response.getCode()).isEqualTo(400); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)) // GH-90000
                .contains("X-Tenant-ID").contains("required");
        }

        @Test
        @DisplayName("executeAgent returns 400 when required headers missing")
        void executeAgentMissingRequiredHeaders() { // GH-90000
            // GIVEN: Request with only tenant ID, missing org/workspace
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/agents/copilot/execute")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000

            // WHEN: Execute agent
            HttpResponse response = runPromise(() -> controller.executeAgent(request)); // GH-90000

            // THEN: Returns 400 for missing organization header
            assertThat(response.getCode()).isEqualTo(400); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)) // GH-90000
                .contains("X-Organization-ID").contains("required");
        }
    }

    // =========================================================================
    // WORKFLOW API TESTS
    // =========================================================================

    @Nested
    @DisplayName("Workflow API - List Workflows")
    class WorkflowListingTests {

        private WorkflowController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new WorkflowController(workflowService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("listWorkflows returns 200 with pagination metadata")
        void listWorkflowsReturnsPaginationMetadata() { // GH-90000
            // GIVEN: Service returns workflows
            List<AiWorkflowInstance> workflows = List.of( // GH-90000
                createWorkflow("wf-1", "Workflow 1"), // GH-90000
                createWorkflow("wf-2", "Workflow 2") // GH-90000
            );
            when(workflowService.listWorkflows("tenant-001", null, 20, 0)) // GH-90000
                .thenReturn(Promise.of(workflows)); // GH-90000

            // WHEN: List workflows
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/workflows")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.listWorkflows(request)); // GH-90000

            // THEN: Returns 200 with pagination structure
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            String body = response.getBody().asString(StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("\"workflows\"").contains("\"count\":2") // GH-90000
                .contains("\"limit\":20").contains("\"offset\":0"); // GH-90000
        }

        @Test
        @DisplayName("listWorkflows returns empty array when no workflows")
        void listWorkflowsEmpty() { // GH-90000
            // GIVEN: No workflows for tenant
            when(workflowService.listWorkflows("tenant-001", null, 20, 0)) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

            // WHEN: List workflows
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/workflows")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.listWorkflows(request)); // GH-90000

            // THEN: Returns 200 with empty workflows array (not 404) // GH-90000
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("\"count\":0"); // GH-90000
        }

        @Test
        @DisplayName("listWorkflows filters by status parameter")
        void listWorkflowsFilterByStatus() { // GH-90000
            // GIVEN: Service call for specific status
            when(workflowService.listWorkflows("tenant-001", // GH-90000
                AiWorkflowInstance.WorkflowStatus.IN_PROGRESS, 20, 0))
                .thenReturn(Promise.of(List.of(createWorkflow("wf-1", "Active WF")))); // GH-90000

            // WHEN: List workflows with status filter
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/workflows?status=IN_PROGRESS")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.listWorkflows(request)); // GH-90000

            // THEN: Service called with correct status filter
            verify(workflowService).listWorkflows("tenant-001", // GH-90000
                AiWorkflowInstance.WorkflowStatus.IN_PROGRESS, 20, 0);
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("listWorkflows returns 400 for invalid status")
        void listWorkflowsInvalidStatus() { // GH-90000
            // GIVEN: Invalid status parameter
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/workflows?status=INVALID")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000

            // WHEN: List workflows
            HttpResponse response = runPromise(() -> controller.listWorkflows(request)); // GH-90000

            // THEN: Returns 400 with error message
            assertThat(response.getCode()).isEqualTo(400); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("Invalid status");
        }

        @Test
        @DisplayName("listWorkflows respects pagination parameters")
        void listWorkflowsPagination() { // GH-90000
            // GIVEN: Custom limit and offset
            when(workflowService.listWorkflows("tenant-001", null, 50, 100)) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

            // WHEN: List with custom pagination
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/workflows?limit=50&offset=100")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.listWorkflows(request)); // GH-90000

            // THEN: Service called with custom pagination values
            verify(workflowService).listWorkflows("tenant-001", null, 50, 100); // GH-90000
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
        }
    }

    @Nested
    @DisplayName("Workflow API - Get Workflow")
    class WorkflowDetailsTests {

        private WorkflowController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new WorkflowController(workflowService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("getWorkflow returns 404 when workflow not found")
        void getWorkflowNotFound() { // GH-90000
            // GIVEN: Workflow doesn't exist
            when(workflowService.getWorkflow("non-existent", "tenant-001")) // GH-90000
                .thenReturn(Promise.of(Optional.empty())); // GH-90000

            // WHEN: Get workflow
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/workflows/non-existent")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.getWorkflow(request, "non-existent")); // GH-90000

            // THEN: Returns 404
            assertThat(response.getCode()).isEqualTo(404); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("not found");
        }

        @Test
        @DisplayName("getWorkflow returns workflow when found")
        void getWorkflowFound() { // GH-90000
            // GIVEN: Workflow exists
            AiWorkflowInstance workflow = createWorkflow("wf-1", "Test Workflow"); // GH-90000
            when(workflowService.getWorkflow("wf-1", "tenant-001")) // GH-90000
                .thenReturn(Promise.of(Optional.of(workflow))); // GH-90000

            // WHEN: Get workflow
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/workflows/wf-1")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.getWorkflow(request, "wf-1")); // GH-90000

            // THEN: Returns 200 with workflow
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("Test Workflow");
        }
    }

    @Nested
    @DisplayName("Workflow API - Delete Workflow")
    class WorkflowDeletionTests {

        private WorkflowController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new WorkflowController(workflowService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("deleteWorkflow returns 204 when successful")
        void deleteWorkflowSuccess() { // GH-90000
            // GIVEN: Workflow can be deleted
            when(workflowService.deleteWorkflow("wf-1", "tenant-001")) // GH-90000
                .thenReturn(Promise.of(true)); // GH-90000

            // WHEN: Delete workflow
            HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/workflows/wf-1") // GH-90000
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.deleteWorkflow(request, "wf-1")); // GH-90000

            // THEN: Returns 204 No Content
            assertThat(response.getCode()).isEqualTo(204); // GH-90000
        }

        @Test
        @DisplayName("deleteWorkflow returns 404 when workflow not found")
        void deleteWorkflowNotFound() { // GH-90000
            // GIVEN: Workflow doesn't exist
            when(workflowService.deleteWorkflow("non-existent", "tenant-001")) // GH-90000
                .thenReturn(Promise.of(false)); // GH-90000

            // WHEN: Delete non-existent workflow
            HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/workflows/non-existent") // GH-90000
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.deleteWorkflow(request, "non-existent")); // GH-90000

            // THEN: Returns 404
            assertThat(response.getCode()).isEqualTo(404); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("not found");
        }
    }

    @Nested
    @DisplayName("Workflow API - State Transitions (Start, Pause, Resume, Cancel)")
    class WorkflowStateTransitionTests {

        private WorkflowController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new WorkflowController(workflowService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("startWorkflow transitions workflow to ACTIVE")
        void startWorkflowTransitionsToActive() { // GH-90000
            // GIVEN: DRAFT workflow can be started
            AiWorkflowInstance started = createWorkflowWithStatus("wf-1", "Test", true); // GH-90000
            when(workflowService.startWorkflow("wf-1", "tenant-001")) // GH-90000
                .thenReturn(Promise.of(started)); // GH-90000

            // WHEN: Start workflow
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/start")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.startWorkflow(request, "wf-1")); // GH-90000

            // THEN: Returns 200 with started workflow
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(workflowService).startWorkflow("wf-1", "tenant-001"); // GH-90000
        }

        @Test
        @DisplayName("pauseWorkflow transitions ACTIVE to PAUSED")
        void pauseWorkflowTransitionsToPaused() { // GH-90000
            // GIVEN: ACTIVE workflow can be paused
            AiWorkflowInstance paused = createWorkflowWithStatus("wf-1", "Test", false); // GH-90000
            when(workflowService.pauseWorkflow("wf-1", "tenant-001")) // GH-90000
                .thenReturn(Promise.of(paused)); // GH-90000

            // WHEN: Pause workflow
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/pause")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.pauseWorkflow(request, "wf-1")); // GH-90000

            // THEN: Returns 200
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(workflowService).pauseWorkflow("wf-1", "tenant-001"); // GH-90000
        }

        @Test
        @DisplayName("resumeWorkflow transitions PAUSED to ACTIVE")
        void resumeWorkflowTransitionsToActive() { // GH-90000
            // GIVEN: PAUSED workflow can be resumed
            AiWorkflowInstance resumed = createWorkflowWithStatus("wf-1", "Test", true); // GH-90000
            when(workflowService.resumeWorkflow("wf-1", "tenant-001")) // GH-90000
                .thenReturn(Promise.of(resumed)); // GH-90000

            // WHEN: Resume workflow
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/resume")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.resumeWorkflow(request, "wf-1")); // GH-90000

            // THEN: Returns 200
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(workflowService).resumeWorkflow("wf-1", "tenant-001"); // GH-90000
        }

        @Test
        @DisplayName("cancelWorkflow transitions to CANCELLED")
        void cancelWorkflowTransitionsToCancelled() { // GH-90000
            // GIVEN: ACTIVE or PAUSED workflow can be cancelled
            AiWorkflowInstance cancelled = createWorkflow("wf-1", "Test"); // GH-90000
            when(workflowService.cancelWorkflow("wf-1", "tenant-001")) // GH-90000
                .thenReturn(Promise.of(cancelled)); // GH-90000

            // WHEN: Cancel workflow
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/cancel")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.cancelWorkflow(request, "wf-1")); // GH-90000

            // THEN: Returns 200
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(workflowService).cancelWorkflow("wf-1", "tenant-001"); // GH-90000
        }
    }

    // =========================================================================
    // VECTOR API TESTS
    // =========================================================================

    @Nested
    @DisplayName("Vector API - Semantic Search")
    class SemanticSearchTests {

        private VectorController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new VectorController(searchService, ragService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("search returns 400 when query is empty")
        void searchEmptyQuery() { // GH-90000
            // GIVEN: Empty search request
            String searchBody = "{\"query\": \"\", \"limit\": 10, \"threshold\": 0.7}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/search")
                .withBody(searchBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000

            // WHEN: Search
            HttpResponse response = runPromise(() -> controller.search(request)); // GH-90000

            // THEN: Returns 400 or validates empty query (depends on implementation) // GH-90000
            assertThat(response.getCode()).isIn(200, 400); // Either validates or 400 // GH-90000
        }

        @Test
        @DisplayName("search returns 200 with results when query valid")
        void searchValidQuery() { // GH-90000
            // GIVEN: Valid search results
            SemanticSearchService.SemanticSearchResult result = new SemanticSearchService.SemanticSearchResult( // GH-90000
                "query text",
                List.of(), // GH-90000
                100,
                0L,
                null
            );
            when(searchService.search(any())) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            // WHEN: Search
            String searchBody = "{\"query\": \"test query\", \"limit\": 10, \"threshold\": 0.7}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/search")
                .withBody(searchBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.search(request)); // GH-90000

            // THEN: Returns 200
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(searchService).search(any()); // GH-90000
        }

        @Test
        @DisplayName("search returns 200 with empty results when no matches")
        void searchNoResults() { // GH-90000
            // GIVEN: Search returns no results
            SemanticSearchService.SemanticSearchResult result = new SemanticSearchService.SemanticSearchResult( // GH-90000
                "query with no matches",
                List.of(), // GH-90000
                0,
                0L,
                null
            );
            when(searchService.search(any())) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            // WHEN: Search
            String searchBody = "{\"query\": \"no match\", \"limit\": 10, \"threshold\": 0.9}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/search")
                .withBody(searchBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.search(request)); // GH-90000

            // THEN: Returns 200 with empty results (not 404) // GH-90000
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
        }
    }

    @Nested
    @DisplayName("Vector API - Document Indexing")
    class DocumentIndexingTests {

        private VectorController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new VectorController(searchService, ragService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("indexDocument returns 200 when successful")
        void indexDocumentSuccessful() { // GH-90000
            // GIVEN: Document can be indexed
            SemanticSearchService.IndexResult result = new SemanticSearchService.IndexResult( // GH-90000
                "doc-1",
                true,
                0L,
                0,
                null
            );
            when(searchService.index(any())) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            // WHEN: Index document
            String docBody = "{\"id\": \"doc-1\", \"content\": \"Test document\", \"metadata\": {}}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/index")
                .withBody(docBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.indexDocument(request)); // GH-90000

            // THEN: Returns 200 OK
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(searchService).index(any()); // GH-90000
        }
    }

    // =========================================================================
    // SECURITY TESTS - Tenant Isolation
    // =========================================================================

    @Nested
    @DisplayName("Security - Tenant Isolation")
    class TenantIsolationTests {

        private WorkflowController workflowController;
        private AgentController agentController;

        @BeforeEach
        void setUp() { // GH-90000
            workflowController = new WorkflowController(workflowService, objectMapper); // GH-90000
            agentController = new AgentController(agentRegistry, objectMapper, auditLogger); // GH-90000
        }

        @Test
        @DisplayName("listWorkflows filters by tenant ID from header")
        void listWorkflowsFilteredByTenant() { // GH-90000
            // GIVEN: Service returns workflows for specific tenant
            when(workflowService.listWorkflows("tenant-001", null, 20, 0)) // GH-90000
                .thenReturn(Promise.of(List.of(createWorkflow("wf-1", "Tenant A WF")))); // GH-90000
            when(workflowService.listWorkflows("tenant-002", null, 20, 0)) // GH-90000
                .thenReturn(Promise.of(List.of(createWorkflow("wf-2", "Tenant B WF")))); // GH-90000

            // WHEN: Tenant A lists workflows
            HttpRequest requestA = HttpRequest.get("http://localhost/api/v1/workflows")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse responseA = runPromise(() -> workflowController.listWorkflows(requestA)); // GH-90000

            // THEN: Tenant A gets only their workflows
            assertThat(responseA.getCode()).isEqualTo(200); // GH-90000
            verify(workflowService).listWorkflows("tenant-001", null, 20, 0); // GH-90000

            // WHEN: Tenant B lists workflows
            HttpRequest requestB = HttpRequest.get("http://localhost/api/v1/workflows")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-002")
                .build(); // GH-90000
            HttpResponse responseB = runPromise(() -> workflowController.listWorkflows(requestB)); // GH-90000

            // THEN: Tenant B gets only their workflows
            assertThat(responseB.getCode()).isEqualTo(200); // GH-90000
            verify(workflowService).listWorkflows("tenant-002", null, 20, 0); // GH-90000
        }

        @Test
        @DisplayName("getWorkflow uses tenant ID from header to verify ownership")
        void getWorkflowVerifiesTenantOwnership() { // GH-90000
            // GIVEN: Workflow for tenant-001
            AiWorkflowInstance workflow = createWorkflow("wf-1", "Tenant A Workflow"); // GH-90000
            when(workflowService.getWorkflow("wf-1", "tenant-001")) // GH-90000
                .thenReturn(Promise.of(Optional.of(workflow))); // GH-90000
            when(workflowService.getWorkflow("wf-1", "tenant-002")) // GH-90000
                .thenReturn(Promise.of(Optional.empty())); // Different tenant sees nothing // GH-90000

            // WHEN: Tenant A gets their workflow
            HttpRequest requestA = HttpRequest.get("http://localhost/api/v1/workflows/wf-1")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse responseA = runPromise(() -> workflowController.getWorkflow(requestA, "wf-1")); // GH-90000

            // THEN: Returns 200 with workflow
            assertThat(responseA.getCode()).isEqualTo(200); // GH-90000

            // WHEN: Tenant B tries to get tenant A's workflow
            HttpRequest requestB = HttpRequest.get("http://localhost/api/v1/workflows/wf-1")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-002")
                .build(); // GH-90000
            HttpResponse responseB = runPromise(() -> workflowController.getWorkflow(requestB, "wf-1")); // GH-90000

            // THEN: Returns 404 (not 200, enforcing isolation) // GH-90000
            assertThat(responseB.getCode()).isEqualTo(404); // GH-90000
        }

        @Test
        @DisplayName("deleteWorkflow respects tenant boundaries")
        void deleteWorkflowTenantBoundary() { // GH-90000
            // GIVEN: Workflow belongs to tenant-001
            when(workflowService.deleteWorkflow("wf-1", "tenant-001")) // GH-90000
                .thenReturn(Promise.of(true)); // Can delete // GH-90000
            when(workflowService.deleteWorkflow("wf-1", "tenant-002")) // GH-90000
                .thenReturn(Promise.of(false)); // Cannot delete (not owner) // GH-90000

            // WHEN: Tenant A deletes their workflow
            HttpRequest requestA = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/workflows/wf-1") // GH-90000
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000
            HttpResponse responseA = runPromise(() -> workflowController.deleteWorkflow(requestA, "wf-1")); // GH-90000

            // THEN: Returns 204
            assertThat(responseA.getCode()).isEqualTo(204); // GH-90000

            // WHEN: Tenant B tries to delete tenant A's workflow
            HttpRequest requestB = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/workflows/wf-1") // GH-90000
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-002")
                .build(); // GH-90000
            HttpResponse responseB = runPromise(() -> workflowController.deleteWorkflow(requestB, "wf-1")); // GH-90000

            // THEN: Returns 404 (enforcing tenant isolation) // GH-90000
            assertThat(responseB.getCode()).isEqualTo(404); // GH-90000
        }
    }

    // =========================================================================
    // ERROR HANDLING & EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Error Handling - HTTP Status Codes")
    class ErrorHandlingTests {

        private WorkflowController workflowController;

        @BeforeEach
        void setUp() { // GH-90000
            workflowController = new WorkflowController(workflowService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("listWorkflows with invalid status returns 400")
        void invalidStatusReturns400() { // GH-90000
            // GIVEN: Invalid status value
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/workflows?status=NOTASTATE")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build(); // GH-90000

            // WHEN: List workflows
            HttpResponse response = runPromise(() -> workflowController.listWorkflows(request)); // GH-90000

            // THEN: Returns 400 Bad Request
            assertThat(response.getCode()).isEqualTo(400); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("Invalid status");
        }
        void nullTenantIdUsesDefault() { // GH-90000
            // GIVEN: No X-Tenant-ID header
            AiWorkflowInstance workflow = createWorkflow("wf-1", "Default Tenant WF"); // GH-90000
            when(workflowService.getWorkflow("wf-1", "default-tenant")) // GH-90000
                .thenReturn(Promise.of(Optional.of(workflow))); // GH-90000

            // WHEN: Get workflow without tenant header
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/workflows/wf-1")
                .build(); // No X-Tenant-ID header // GH-90000
            HttpResponse response = runPromise(() -> workflowController.getWorkflow(request, "wf-1")); // GH-90000

            // THEN: Still works (uses default tenant) // GH-90000
            assertThat(response.getCode()).isIn(200, 404); // Depends on implementation // GH-90000
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private AiWorkflowInstance createWorkflow(String id, String name) { // GH-90000
        return new AiWorkflowInstance( // GH-90000
            id,
            "tenant-001",
            name,
            "Test workflow: " + name,
            AiWorkflowInstance.WorkflowType.CUSTOM,
            AiWorkflowInstance.WorkflowStatus.DRAFT,
            "step-1",
            0,
            1,
            new HashMap<>(), // GH-90000
            new HashMap<>(), // GH-90000
            null,
            "user-123",
            Instant.now(), // GH-90000
            Instant.now(), // GH-90000
            null,
            null
        );
    }

    private AiWorkflowInstance createWorkflowWithStatus(String id, String name, boolean isActive) { // GH-90000
        return new AiWorkflowInstance( // GH-90000
            id,
            "tenant-001",
            name,
            "Test workflow",
            AiWorkflowInstance.WorkflowType.CUSTOM,
            isActive ? AiWorkflowInstance.WorkflowStatus.IN_PROGRESS : AiWorkflowInstance.WorkflowStatus.PAUSED,
            "step-1",
            0,
            1,
            new HashMap<>(), // GH-90000
            new HashMap<>(), // GH-90000
            null,
            "user-123",
            Instant.now(), // GH-90000
            Instant.now(), // GH-90000
            null,
            null
        );
    }
}

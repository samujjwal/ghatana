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
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for yappc-api HTTP controllers.
 *
 * <p>Tests cover constructor validation and basic endpoint behavior using mocked
 * domain services. Promise-returning methods are tested via {@link EventloopTestBase}.
 *
 * @doc.type class
 * @doc.purpose Tests for AgentController, WorkflowController, and VectorController constructors and behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("yappc-api HTTP Controllers [GH-90000]")
class YappcApiControllerTest extends EventloopTestBase {

    @Mock
    private AgentRegistry agentRegistry;

    @Mock
    private AiWorkflowService workflowService;

    @Mock
    private SemanticSearchService searchService;

    @Mock
    private RagService ragService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() { // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
    }

    // =========================================================================
    // AgentController
    // =========================================================================

    @Nested
    @DisplayName("AgentController [GH-90000]")
    class AgentControllerTests {

        @Test
        @DisplayName("two-arg constructor creates controller successfully [GH-90000]")
        void twoArgConstructorCreatesController() { // GH-90000
            AgentController controller = new AgentController(agentRegistry, objectMapper); // GH-90000

            assertThat(controller).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("three-arg constructor creates controller with audit logger [GH-90000]")
        void threeArgConstructorCreatesController() { // GH-90000
            AgentController controller = new AgentController( // GH-90000
                    agentRegistry, objectMapper, AuditLogger.noop()); // GH-90000

            assertThat(controller).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("constructor rejects null registry [GH-90000]")
        void constructorRejectsNullRegistry() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> new AgentController(null, objectMapper)); // GH-90000
        }

        @Test
        @DisplayName("constructor rejects null objectMapper [GH-90000]")
        void constructorRejectsNullObjectMapper() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> new AgentController(agentRegistry, null)); // GH-90000
        }

        @Test
        @DisplayName("constructor rejects null auditLogger [GH-90000]")
        void constructorRejectsNullAuditLogger() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> new AgentController(agentRegistry, objectMapper, null)); // GH-90000
        }

        @Test
        @DisplayName("listAgents returns 200 with agents list from registry [GH-90000]")
        void listAgentsReturnsOkWithAgents() { // GH-90000
            when(agentRegistry.getAllMetadata()).thenReturn(List.of()); // GH-90000
            AgentController controller = new AgentController(agentRegistry, objectMapper); // GH-90000
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents [GH-90000]").build();

            HttpResponse response = runPromise(() -> controller.listAgents(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(agentRegistry).getAllMetadata(); // GH-90000
        }

        @Test
        @DisplayName("listAgents returns 200 for empty registry [GH-90000]")
        void listAgentsReturnsOkForEmptyRegistry() { // GH-90000
            when(agentRegistry.getAllMetadata()).thenReturn(List.of()); // GH-90000
            AgentController controller = new AgentController(agentRegistry, objectMapper); // GH-90000
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents [GH-90000]").build();

            HttpResponse response = runPromise(() -> controller.listAgents(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(200); // GH-90000
        }
    }

    // =========================================================================
    // WorkflowController
    // =========================================================================

    @Nested
    @DisplayName("WorkflowController [GH-90000]")
    class WorkflowControllerTests {

        @Test
        @DisplayName("constructor creates controller successfully [GH-90000]")
        void constructorCreatesController() { // GH-90000
            WorkflowController controller = new WorkflowController(workflowService, objectMapper); // GH-90000

            assertThat(controller).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("constructor rejects null workflowService [GH-90000]")
        void constructorRejectsNullWorkflowService() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> new WorkflowController(null, objectMapper)); // GH-90000
        }

        @Test
        @DisplayName("constructor rejects null objectMapper [GH-90000]")
        void constructorRejectsNullObjectMapper() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> new WorkflowController(workflowService, null)); // GH-90000
        }

        @Test
        @DisplayName("listWorkflows returns 200 and delegates with default paging [GH-90000]")
        void listWorkflowsReturnsOkAndDelegates() { // GH-90000
            when(workflowService.listWorkflows( // GH-90000
                    org.mockito.ArgumentMatchers.anyString(), // GH-90000
                    org.mockito.ArgumentMatchers.<AiWorkflowInstance.WorkflowStatus>isNull(), // GH-90000
                    org.mockito.ArgumentMatchers.anyInt(), // GH-90000
                    org.mockito.ArgumentMatchers.anyInt())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000
            WorkflowController controller = new WorkflowController(workflowService, objectMapper); // GH-90000
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/workflows [GH-90000]")
                    .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-001")
                    .build(); // GH-90000

            HttpResponse response = runPromise(() -> controller.listWorkflows(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(workflowService).listWorkflows("tenant-001", null, 20, 0); // GH-90000
        }
    }

    // =========================================================================
    // VectorController
    // =========================================================================

    @Nested
    @DisplayName("VectorController [GH-90000]")
    class VectorControllerTests {

        @Test
        @DisplayName("constructor creates controller successfully [GH-90000]")
        void constructorCreatesController() { // GH-90000
            VectorController controller = new VectorController(searchService, ragService, objectMapper); // GH-90000

            assertThat(controller).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("constructor rejects null searchService [GH-90000]")
        void constructorRejectsNullSearchService() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> new VectorController(null, ragService, objectMapper)); // GH-90000
        }

        @Test
        @DisplayName("constructor rejects null ragService [GH-90000]")
        void constructorRejectsNullRagService() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> new VectorController(searchService, null, objectMapper)); // GH-90000
        }

        @Test
        @DisplayName("constructor rejects null objectMapper [GH-90000]")
        void constructorRejectsNullObjectMapper() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> new VectorController(searchService, ragService, null)); // GH-90000
        }
    }
}

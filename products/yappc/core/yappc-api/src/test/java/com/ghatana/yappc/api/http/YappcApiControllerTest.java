/*
 * Copyright (c) 2026 Ghatana Inc.
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
@ExtendWith(MockitoExtension.class)
@DisplayName("yappc-api HTTP Controllers")
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
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // =========================================================================
    // AgentController
    // =========================================================================

    @Nested
    @DisplayName("AgentController")
    class AgentControllerTests {

        @Test
        @DisplayName("two-arg constructor creates controller successfully")
        void twoArgConstructorCreatesController() {
            AgentController controller = new AgentController(agentRegistry, objectMapper);

            assertThat(controller).isNotNull();
        }

        @Test
        @DisplayName("three-arg constructor creates controller with audit logger")
        void threeArgConstructorCreatesController() {
            AgentController controller = new AgentController(
                    agentRegistry, objectMapper, AuditLogger.noop());

            assertThat(controller).isNotNull();
        }

        @Test
        @DisplayName("constructor rejects null registry")
        void constructorRejectsNullRegistry() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new AgentController(null, objectMapper));
        }

        @Test
        @DisplayName("constructor rejects null objectMapper")
        void constructorRejectsNullObjectMapper() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new AgentController(agentRegistry, null));
        }

        @Test
        @DisplayName("constructor rejects null auditLogger")
        void constructorRejectsNullAuditLogger() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new AgentController(agentRegistry, objectMapper, null));
        }

        @Test
        @DisplayName("listAgents returns 200 with agents list from registry")
        void listAgentsReturnsOkWithAgents() {
            when(agentRegistry.getAllMetadata()).thenReturn(List.of());
            AgentController controller = new AgentController(agentRegistry, objectMapper);
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents").build();

            HttpResponse response = runPromise(() -> controller.listAgents(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(agentRegistry).getAllMetadata();
        }

        @Test
        @DisplayName("listAgents returns 200 for empty registry")
        void listAgentsReturnsOkForEmptyRegistry() {
            when(agentRegistry.getAllMetadata()).thenReturn(List.of());
            AgentController controller = new AgentController(agentRegistry, objectMapper);
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents").build();

            HttpResponse response = runPromise(() -> controller.listAgents(request));

            assertThat(response.getCode()).isEqualTo(200);
        }
    }

    // =========================================================================
    // WorkflowController
    // =========================================================================

    @Nested
    @DisplayName("WorkflowController")
    class WorkflowControllerTests {

        @Test
        @DisplayName("constructor creates controller successfully")
        void constructorCreatesController() {
            WorkflowController controller = new WorkflowController(workflowService, objectMapper);

            assertThat(controller).isNotNull();
        }

        @Test
        @DisplayName("constructor rejects null workflowService")
        void constructorRejectsNullWorkflowService() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new WorkflowController(null, objectMapper));
        }

        @Test
        @DisplayName("constructor rejects null objectMapper")
        void constructorRejectsNullObjectMapper() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new WorkflowController(workflowService, null));
        }

        @Test
        @DisplayName("listWorkflows returns 200 and delegates with default paging")
        void listWorkflowsReturnsOkAndDelegates() {
            when(workflowService.listWorkflows(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.<AiWorkflowInstance.WorkflowStatus>isNull(),
                    org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(Promise.of(List.of()));
            WorkflowController controller = new WorkflowController(workflowService, objectMapper);
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/workflows")
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                    .build();

            HttpResponse response = runPromise(() -> controller.listWorkflows(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(workflowService).listWorkflows("tenant-001", null, 20, 0);
        }
    }

    // =========================================================================
    // VectorController
    // =========================================================================

    @Nested
    @DisplayName("VectorController")
    class VectorControllerTests {

        @Test
        @DisplayName("constructor creates controller successfully")
        void constructorCreatesController() {
            VectorController controller = new VectorController(searchService, ragService, objectMapper);

            assertThat(controller).isNotNull();
        }

        @Test
        @DisplayName("constructor rejects null searchService")
        void constructorRejectsNullSearchService() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new VectorController(null, ragService, objectMapper));
        }

        @Test
        @DisplayName("constructor rejects null ragService")
        void constructorRejectsNullRagService() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new VectorController(searchService, null, objectMapper));
        }

        @Test
        @DisplayName("constructor rejects null objectMapper")
        void constructorRejectsNullObjectMapper() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new VectorController(searchService, ragService, null));
        }
    }
}

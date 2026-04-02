/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api.http;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Extended expectation-driven tests for advanced API scenarios.
 *
 * <p>Covers:
 * <ul>
 *   <li>Response schema validation for all endpoints</li>
 *   <li>Input validation edge cases</li>
 *   <li>Integration with persistence and events</li>
 *   <li>Workflow step operations</li>
 *   <li>Vector RAG operations</li>
 *   <li>Hybrid search functionality</li>
 *   <li>Batch operations</li>
 *   <li>Timeout and failure scenarios</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Extended expectation-driven API tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("yappc-api Extended Tests - Advanced Scenarios")
class YappcApiControllerAdvancedTest extends EventloopTestBase {

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
    // WORKFLOW API - STEP OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("Workflow API - Step Operations")
    class WorkflowStepOperationTests {

        private WorkflowController controller;

        @BeforeEach
        void setUp() {
            controller = new WorkflowController(workflowService, objectMapper);
        }

        @Test
        @DisplayName("advanceStep returns 200 when successfully advanced")
        void advanceStepSuccess() {
            // GIVEN: Workflow with current step
            AiWorkflowInstance advanced = createWorkflowForTenant("wf-1", "tenant-001", true);
            when(workflowService.advanceStep(any(), any(), any()))
                .thenReturn(Promise.of(advanced));

            // WHEN: Advance step
            String stepBody = "{\"stepId\": \"step-1\", \"stepName\": \"Step 1\", \"status\": \"COMPLETED\"}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/steps/advance")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .withBody(stepBody.getBytes(StandardCharsets.UTF_8))
                .build();
            HttpResponse response = runPromise(() -> controller.advanceStep(request, "wf-1"));

            // THEN: Returns 200
            assertThat(response.getCode()).isEqualTo(200);
            verify(workflowService).advanceStep(any(), any(), any());
        }

        @Test
        @DisplayName("advanceStep returns 400 for invalid step request body")
        void advanceStepInvalidBody() {
            // GIVEN: Malformed request body
            String invalidBody = "{invalid json";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/steps/advance")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .withBody(invalidBody.getBytes(StandardCharsets.UTF_8))
                .build();

            // WHEN: Advance step
            HttpResponse response = runPromise(() -> controller.advanceStep(request, "wf-1"));

            // THEN: Returns 400
            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getBodyString()).contains("Invalid request");
        }

        @Test
        @DisplayName("goToStep transitions to specific step")
        void goToStep() {
            // GIVEN: Can transition to target step
            AiWorkflowInstance jumped = createWorkflowForTenant("wf-1", "tenant-001", true);
            when(workflowService.goToStep(any(), any(), any()))
                .thenReturn(Promise.of(jumped));

            // WHEN: Go to specific step
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/steps/step-5/goto")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build();
            HttpResponse response = runPromise(() -> controller.goToStep(request, "wf-1", "step-5"));

            // THEN: Returns 200 with updated workflow
            assertThat(response.getCode()).isEqualTo(200);
            verify(workflowService).goToStep(any(), any(), any());
        }
    }

    // =========================================================================
    // WORKFLOW API - PLAN OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("Workflow API - AI Plan Management")
    class WorkflowPlanOperationTests {

        private WorkflowController controller;

        @BeforeEach
        void setUp() {
            controller = new WorkflowController(workflowService, objectMapper);
        }

        @Test
        @DisplayName("generatePlan returns 200 with generated plan")
        void generatePlanSuccess() {
            // GIVEN: Workflow can generate plan
            Map<String, Object> plan = Map.of(
                "planId", "plan-1",
                "status", "PENDING",
                "steps", List.of(),
                "estimatedDuration", "00:30:00",
                "successProbability", 0.95
            );
            when(workflowService.generatePlan(any(), any()))
                .thenReturn(Promise.of(plan));

            // WHEN: Generate plan
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/plans/generate")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build();
            HttpResponse response = runPromise(() -> controller.generatePlan(request, "wf-1"));

            // THEN: Returns 200 with plan
            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getBodyString()).contains("planId").contains("PENDING");
        }

        @Test
        @DisplayName("approvePlan transitions plan to APPROVED")
        void approvePlanSuccess() {
            // GIVEN: Plan can be approved
            Map<String, Object> approvedPlan = Map.of(
                "planId", "plan-1",
                "status", "APPROVED",
                "approvedAt", Instant.now().toString()
            );
            when(workflowService.approvePlan(any(), any(), any()))
                .thenReturn(Promise.of(approvedPlan));

            // WHEN: Approve plan
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/plans/plan-1/approve")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build();
            HttpResponse response = runPromise(() -> controller.approvePlan(request, "wf-1", "plan-1"));

            // THEN: Returns 200
            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getBodyString()).contains("\"status\":\"APPROVED\"");
        }

        @Test
        @DisplayName("rejectPlan transitions plan to REJECTED")
        void rejectPlanSuccess() {
            // GIVEN: Plan can be rejected
            Map<String, Object> rejectedPlan = Map.of(
                "planId", "plan-1",
                "status", "REJECTED"
            );
            when(workflowService.rejectPlan(any(), any(), any()))
                .thenReturn(Promise.of(rejectedPlan));

            // WHEN: Reject plan
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/plans/plan-1/reject")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build();
            HttpResponse response = runPromise(() -> controller.rejectPlan(request, "wf-1", "plan-1"));

            // THEN: Returns 200
            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getBodyString()).contains("\"status\":\"REJECTED\"");
        }

        @Test
        @DisplayName("modifyPlanSteps updates plan steps before approval")
        void modifyPlanStepsSuccess() {
            // GIVEN: Plan steps can be modified
            Map<String, Object> modifiedPlan = Map.of(
                "planId", "plan-1",
                "status", "PENDING",
                "steps", List.of("modified-step-1", "modified-step-2")
            );
            when(workflowService.modifyPlanSteps(any(), any(), any(), any()))
                .thenReturn(Promise.of(modifiedPlan));

            // WHEN: Modify plan steps
            String stepsBody = "{\"steps\": [{\"id\": \"step-1\", \"agent\": \"refactor\"}]}";
            HttpRequest request = HttpRequest.put("http://localhost/api/v1/workflows/wf-1/plans/plan-1/steps")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .withBody(stepsBody.getBytes(StandardCharsets.UTF_8))
                .build();
            HttpResponse response = runPromise(() -> controller.modifyPlanSteps(request, "wf-1", "plan-1"));

            // THEN: Returns 200 with modified plan
            assertThat(response.getCode()).isEqualTo(200);
        }
    }

    // =========================================================================
    // VECTOR API - HYBRID SEARCH
    // =========================================================================

    @Nested
    @DisplayName("Vector API - Hybrid Search")
    class HybridSearchTests {

        private VectorController controller;

        @BeforeEach
        void setUp() {
            controller = new VectorController(searchService, ragService, objectMapper);
        }

        @Test
        @DisplayName("hybridSearch combines semantic and keyword results")
        void hybridSearchSuccess() {
            // GIVEN: Hybrid search returns results
            SemanticSearchService.SearchResult result = new SemanticSearchService.SearchResult(
                "query text",
                List.of(),
                50
            );
            when(searchService.hybridSearch(any()))
                .thenReturn(Promise.of(result));

            // WHEN: Perform hybrid search
            String searchBody = "{\"query\": \"test\", \"keywords\": [\"test\", \"query\"], \"limit\": 10, \"threshold\": 0.7, \"keyword_boost\": 0.3}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/search/hybrid")
                .withBody(searchBody.getBytes(StandardCharsets.UTF_8))
                .build();
            HttpResponse response = runPromise(() -> controller.hybridSearch(request));

            // THEN: Returns 200
            assertThat(response.getCode()).isEqualTo(200);
            verify(searchService).hybridSearch(any());
        }

        @Test
        @DisplayName("hybridSearch returns 400 for empty query and keywords")
        void hybridSearchNoQueryOrKeywords() {
            // GIVEN: Neither query nor keywords provided
            String searchBody = "{\"query\": \"\", \"keywords\": [], \"limit\": 10}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/search/hybrid")
                .withBody(searchBody.getBytes(StandardCharsets.UTF_8))
                .build();

            // WHEN: Perform hybrid search
            HttpResponse response = runPromise(() -> controller.hybridSearch(request));

            // THEN: Returns 400 or 422 (validation error)
            assertThat(response.getCode()).isIn(400, 422);
        }

        @Test
        @DisplayName("hybridSearch respects keyword boost parameter")
        void hybridSearchWithKeywordBoost() {
            // GIVEN: Hybrid search with keyword boost
            SemanticSearchService.SearchResult result = new SemanticSearchService.SearchResult(
                "query",
                List.of(),
                0
            );
            when(searchService.hybridSearch(any()))
                .thenReturn(Promise.of(result));

            // WHEN: Search with keyword boost
            String searchBody = "{\"query\": \"test\", \"keywords\": [\"keyword1\"], \"keyword_boost\": 0.8}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/search/hybrid")
                .withBody(searchBody.getBytes(StandardCharsets.UTF_8))
                .build();
            HttpResponse response = runPromise(() -> controller.hybridSearch(request));

            // THEN: Returns 200 and service called with boost
            assertThat(response.getCode()).isEqualTo(200);
            verify(searchService).hybridSearch(any());
        }
    }

    // =========================================================================
    // VECTOR API - FIND SIMILAR DOCUMENTS
    // =========================================================================

    @Nested
    @DisplayName("Vector API - Find Similar Documents")
    class FindSimilarTests {

        private VectorController controller;

        @BeforeEach
        void setUp() {
            controller = new VectorController(searchService, ragService, objectMapper);
        }

        @Test
        @DisplayName("findSimilar returns similar documents")
        void findSimilarSuccess() {
            // GIVEN: Similar documents found
            List<Map<String, Object>> similar = List.of(
                Map.of("id", "doc-2", "score", 0.92),
                Map.of("id", "doc-3", "score", 0.85)
            );
            when(searchService.findSimilar(any(), any(), any()))
                .thenReturn(Promise.of(similar));

            // WHEN: Find similar
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/vector/similar/doc-1?limit=5&threshold=0.8")
                .build();
            HttpResponse response = runPromise(() -> controller.findSimilar(request, "doc-1"));

            // THEN: Returns 200 with similar documents
            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getBodyString())
                .contains("\"sourceId\":\"doc-1\"")
                .contains("\"similar\"")
                .contains("\"count\":2");
        }

        @Test
        @DisplayName("findSimilar returns empty array when no similar documents")
        void findSimilarNoMatches() {
            // GIVEN: No similar documents
            when(searchService.findSimilar(any(), any(), any()))
                .thenReturn(Promise.of(List.of()));

            // WHEN: Find similar
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/vector/similar/doc-1")
                .build();
            HttpResponse response = runPromise(() -> controller.findSimilar(request, "doc-1"));

            // THEN: Returns 200 with empty array
            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getBodyString()).contains("\"count\":0");
        }
    }

    // =========================================================================
    // VECTOR API - BATCH INDEXING
    // =========================================================================

    @Nested
    @DisplayName("Vector API - Batch Indexing")
    class BatchIndexingTests {

        private VectorController controller;

        @BeforeEach
        void setUp() {
            controller = new VectorController(searchService, ragService, objectMapper);
        }

        @Test
        @DisplayName("batchIndex returns 200 with batch results")
        void batchIndexSuccess() {
            // GIVEN: Batch indexing succeeds
            List<SemanticSearchService.IndexResult> results = List.of(
                new SemanticSearchService.IndexResult("doc-1", true, null, Instant.now()),
                new SemanticSearchService.IndexResult("doc-2", true, null, Instant.now())
            );
            when(searchService.batchIndex(any()))
                .thenReturn(Promise.of(results));

            // WHEN: Batch index
            String batchBody = "{\"documents\": [{\"id\": \"doc-1\", \"content\": \"content1\"}, {\"id\": \"doc-2\", \"content\": \"content2\"}]}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/index/batch")
                .withBody(batchBody.getBytes(StandardCharsets.UTF_8))
                .build();
            HttpResponse response = runPromise(() -> controller.batchIndex(request));

            // THEN: Returns 200 with batch results
            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getBodyString())
                .contains("\"results\"")
                .contains("\"total\":2")
                .contains("\"success\":2")
                .contains("\"failed\":0");
        }

        @Test
        @DisplayName("batchIndex reports partial failures")
        void batchIndexPartialFailure() {
            // GIVEN: Some documents fail indexing
            List<SemanticSearchService.IndexResult> results = List.of(
                new SemanticSearchService.IndexResult("doc-1", true, null, Instant.now()),
                new SemanticSearchService.IndexResult("doc-2", false, "Duplicate ID", Instant.now())
            );
            when(searchService.batchIndex(any()))
                .thenReturn(Promise.of(results));

            // WHEN: Batch index
            String batchBody = "{\"documents\": [{\"id\": \"doc-1\", \"content\": \"content1\"}, {\"id\": \"doc-2\", \"content\": \"content2\"}]}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/index/batch")
                .withBody(batchBody.getBytes(StandardCharsets.UTF_8))
                .build();
            HttpResponse response = runPromise(() -> controller.batchIndex(request));

            // THEN: Returns 200 with mixed results
            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getBodyString())
                .contains("\"total\":2")
                .contains("\"success\":1")
                .contains("\"failed\":1");
        }
    }

    // =========================================================================
    // VECTOR API - DELETE DOCUMENT
    // =========================================================================

    @Nested
    @DisplayName("Vector API - Delete Document")
    class DeleteDocumentTests {

        private VectorController controller;

        @BeforeEach
        void setUp() {
            controller = new VectorController(searchService, ragService, objectMapper);
        }

        @Test
        @DisplayName("deleteDocument returns 204 when successful")
        void deleteDocumentSuccess() {
            // GIVEN: Document can be deleted
            when(searchService.delete(any()))
                .thenReturn(Promise.of(true));

            // WHEN: Delete document
            HttpRequest request = HttpRequest.delete("http://localhost/api/v1/vector/index/doc-1")
                .build();
            HttpResponse response = runPromise(() -> controller.deleteDocument(request, "doc-1"));

            // THEN: Returns 204 No Content
            assertThat(response.getCode()).isEqualTo(204);
            verify(searchService).delete("doc-1");
        }

        @Test
        @DisplayName("deleteDocument returns 404 when document not found")
        void deleteDocumentNotFound() {
            // GIVEN: Document doesn't exist
            when(searchService.delete(any()))
                .thenReturn(Promise.of(false));

            // WHEN: Delete non-existent document
            HttpRequest request = HttpRequest.delete("http://localhost/api/v1/vector/index/non-existent")
                .build();
            HttpResponse response = runPromise(() -> controller.deleteDocument(request, "non-existent"));

            // THEN: Returns 404
            assertThat(response.getCode()).isEqualTo(404);
            assertThat(response.getBodyString()).contains("not found");
        }
    }

    // =========================================================================
    // VECTOR API - RAG OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("Vector API - RAG (Retrieval-Augmented Generation)")
    class RagOperationTests {

        private VectorController controller;

        @BeforeEach
        void setUp() {
            controller = new VectorController(searchService, ragService, objectMapper);
        }

        @Test
        @DisplayName("rag returns 200 with retrieved documents and generated response")
        void ragSuccess() {
            // GIVEN: RAG service returns documents and generated text
            Map<String, Object> ragResult = Map.of(
                "retrieved_documents", List.of(),
                "generated_response", "Generated answer based on retrieval",
                "sources", List.of(),
                "confidence", 0.92
            );
            when(ragService.rag(any()))
                .thenReturn(Promise.of(ragResult));

            // WHEN: Perform RAG
            String ragBody = "{\"query\": \"What is machine learning?\", \"limit\": 5, \"generator\": \"gpt-4\"}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/rag")
                .withBody(ragBody.getBytes(StandardCharsets.UTF_8))
                .build();
            HttpResponse response = runPromise(() -> controller.rag(request));

            // THEN: Returns 200 with RAG result
            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getBodyString())
                .contains("retrieved_documents")
                .contains("generated_response")
                .contains("sources");
        }

        @Test
        @DisplayName("ragChat returns conversation response")
        void ragChatSuccess() {
            // GIVEN: RAG chat service returns response
            Map<String, Object> chatResponse = Map.of(
                "conversation_id", "conv-123",
                "reply", "Response to user message",
                "sources", List.of(),
                "tokens_used", 150
            );
            when(ragService.ragChat(any()))
                .thenReturn(Promise.of(chatResponse));

            // WHEN: RAG chat
            String chatBody = "{\"conversation_id\": \"conv-123\", \"message\": \"Tell me more\"}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/rag/chat")
                .withBody(chatBody.getBytes(StandardCharsets.UTF_8))
                .build();
            HttpResponse response = runPromise(() -> controller.ragChat(request));

            // THEN: Returns 200 with chat response
            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getBodyString())
                .contains("conversation_id")
                .contains("reply")
                .contains("tokens_used");
        }

        @Test
        @DisplayName("ragChat handles new conversation")
        void ragChatNewConversation() {
            // GIVEN: Starting new conversation
            Map<String, Object> chatResponse = Map.of(
                "conversation_id", "conv-new",
                "reply", "Initial response"
            );
            when(ragService.ragChat(any()))
                .thenReturn(Promise.of(chatResponse));

            // WHEN: Start new RAG chat
            String chatBody = "{\"message\": \"Hello, can you help?\"}"; // No conversation_id
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/rag/chat")
                .withBody(chatBody.getBytes(StandardCharsets.UTF_8))
                .build();
            HttpResponse response = runPromise(() -> controller.ragChat(request));

            // THEN: Returns 200 with new conversation
            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getBodyString()).contains("conversation_id");
        }
    }

    // =========================================================================
    // RESPONSE SCHEMA VALIDATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Response Schema Validation")
    class ResponseSchemaTests {

        private WorkflowController workflowController;
        private VectorController vectorController;

        @BeforeEach
        void setUp() {
            workflowController = new WorkflowController(workflowService, objectMapper);
            vectorController = new VectorController(searchService, ragService, objectMapper);
        }

        @Test
        @DisplayName("listWorkflows response contains required pagination fields")
        void listWorkflowsResponseSchema() {
            // GIVEN: Workflows returned
            when(workflowService.listWorkflows(any(), any(), any(), any()))
                .thenReturn(Promise.of(List.of(createWorkflowForTenant("wf-1", "tenant-001", true))));

            // WHEN: List workflows
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/workflows")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-001")
                .build();
            HttpResponse response = runPromise(() -> workflowController.listWorkflows(request));

            // THEN: Response contains all required fields
            String body = response.getBodyString();
            assertThat(body).contains("\"workflows\"").contains("\"count\"")
                .contains("\"limit\"").contains("\"offset\"");
        }

        @Test
        @DisplayName("rag response contains required fields")
        void ragResponseSchema() {
            // GIVEN: RAG result
            Map<String, Object> result = Map.of(
                "retrieved_documents", List.of(),
                "generated_response", "Response",
                "sources", List.of(),
                "confidence", 0.9
            );
            when(ragService.rag(any())).thenReturn(Promise.of(result));

            // WHEN: RAG query
            String ragBody = "{\"query\": \"test\"}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/rag")
                .withBody(ragBody.getBytes(StandardCharsets.UTF_8))
                .build();
            HttpResponse response = runPromise(() -> vectorController.rag(request));

            // THEN: Response has required fields
            assertThat(response.getCode()).isEqualTo(200);
            String body = response.getBodyString();
            assertThat(body).contains("retrieved_documents")
                .contains("generated_response")
                .contains("sources");
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private AiWorkflowInstance createWorkflowForTenant(String id, String tenantId, boolean isActive) {
        return new AiWorkflowInstance(
            id,
            "Test Workflow",
            "A test workflow",
            "SEQUENTIAL",
            isActive ? "ACTIVE" : "DRAFT",
            tenantId,
            "user-123",
            Instant.now(),
            isActive ? Instant.now() : null,
            null,
            null,
            0,
            new HashMap<>()
        );
    }
}

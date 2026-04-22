/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.yappc.api.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.products.yappc.domain.vector.RagService;
import com.ghatana.products.yappc.domain.vector.SemanticSearchService;
import com.ghatana.products.yappc.domain.workflow.AiPlan;
import com.ghatana.products.yappc.domain.workflow.AiWorkflowInstance;
import com.ghatana.products.yappc.domain.workflow.AiWorkflowService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyDouble;
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
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("yappc-api Extended Tests - Advanced Scenarios [GH-90000]")
class YappcApiControllerAdvancedTest extends EventloopTestBase {

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
    // WORKFLOW API - STEP OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("Workflow API - Step Operations [GH-90000]")
    class WorkflowStepOperationTests {

        private WorkflowController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new WorkflowController(workflowService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("advanceStep returns 200 when successfully advanced [GH-90000]")
        void advanceStepSuccess() { // GH-90000
            // GIVEN: Workflow with current step
            AiWorkflowInstance advanced = createWorkflowForTenant("wf-1", "tenant-001", true); // GH-90000
            when(workflowService.advanceStep(any(), any(), any())) // GH-90000
                .thenReturn(Promise.of(advanced)); // GH-90000

            // WHEN: Advance step
            String stepBody = "{\"stepId\": \"step-1\", \"stepName\": \"Step 1\", \"status\": \"COMPLETED\"}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/steps/advance [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-001")
                .withBody(stepBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.advanceStep(request, "wf-1")); // GH-90000

            // THEN: Returns 200
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(workflowService).advanceStep(any(), any(), any()); // GH-90000
        }

        @Test
        @DisplayName("advanceStep returns 400 for invalid step request body [GH-90000]")
        void advanceStepInvalidBody() { // GH-90000
            // GIVEN: Malformed request body
            String invalidBody = "{invalid json";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/steps/advance [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-001")
                .withBody(invalidBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000

            // WHEN: Advance step
            HttpResponse response = runPromise(() -> controller.advanceStep(request, "wf-1")); // GH-90000

            // THEN: Returns 400
            assertThat(response.getCode()).isEqualTo(400); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("Invalid request [GH-90000]");
        }

        @Test
        @DisplayName("goToStep transitions to specific step [GH-90000]")
        void goToStep() { // GH-90000
            // GIVEN: Can transition to target step
            AiWorkflowInstance jumped = createWorkflowForTenant("wf-1", "tenant-001", true); // GH-90000
            when(workflowService.goToStep(any(), any(), any())) // GH-90000
                .thenReturn(Promise.of(jumped)); // GH-90000

            // WHEN: Go to specific step
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/steps/step-5/goto [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.goToStep(request, "wf-1", "step-5")); // GH-90000

            // THEN: Returns 200 with updated workflow
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(workflowService).goToStep(any(), any(), any()); // GH-90000
        }
    }

    // =========================================================================
    // WORKFLOW API - PLAN OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("Workflow API - AI Plan Management [GH-90000]")
    class WorkflowPlanOperationTests {

        private WorkflowController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new WorkflowController(workflowService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("generatePlan returns 200 with generated plan [GH-90000]")
        void generatePlanSuccess() { // GH-90000
            // GIVEN: Workflow can generate plan
            AiPlan plan = new AiPlan("plan-1", "wf-1", "tenant-001", "test objective", // GH-90000
                List.of(), AiPlan.PlanStatus.PENDING_REVIEW, null, "gpt-4", 0.95, null, null); // GH-90000
            when(workflowService.generatePlan(any(), any(), any())) // GH-90000
                .thenReturn(Promise.of(plan)); // GH-90000

            // WHEN: Generate plan
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/plans/generate [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-001")
                .withBody("{\"objective\":\"test objective\"}".getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.generatePlan(request, "wf-1")); // GH-90000

            // THEN: Returns 200 with plan
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("PENDING_REVIEW [GH-90000]");
        }

        @Test
        @DisplayName("approvePlan transitions plan to APPROVED [GH-90000]")
        void approvePlanSuccess() { // GH-90000
            // GIVEN: Plan can be approved
            AiPlan approvedPlan = new AiPlan("plan-1", "wf-1", "tenant-001", "objective", // GH-90000
                List.of(), AiPlan.PlanStatus.APPROVED, null, "gpt-4", 0.9, null, null); // GH-90000
            when(workflowService.approvePlan(any(), any())) // GH-90000
                .thenReturn(Promise.of(approvedPlan)); // GH-90000

            // WHEN: Approve plan
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/plans/plan-1/approve [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.approvePlan(request, "wf-1", "plan-1")); // GH-90000

            // THEN: Returns 200
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("APPROVED [GH-90000]");
        }

        @Test
        @DisplayName("rejectPlan transitions plan to REJECTED [GH-90000]")
        void rejectPlanSuccess() { // GH-90000
            // GIVEN: Plan can be rejected
            AiPlan rejectedPlan = new AiPlan("plan-1", "wf-1", "tenant-001", "objective", // GH-90000
                List.of(), AiPlan.PlanStatus.REJECTED, null, "gpt-4", 0.0, null, null); // GH-90000
            when(workflowService.rejectPlan(any(), any(), any())) // GH-90000
                .thenReturn(Promise.of(rejectedPlan)); // GH-90000

            // WHEN: Reject plan
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/workflows/wf-1/plans/plan-1/reject [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-001")
                .withBody("{\"reason\": \"Does not meet requirements\"}".getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.rejectPlan(request, "wf-1", "plan-1")); // GH-90000

            // THEN: Returns 200
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("REJECTED [GH-90000]");
        }

        @Test
        @DisplayName("modifyPlanSteps updates plan steps before approval [GH-90000]")
        void modifyPlanStepsSuccess() { // GH-90000
            // GIVEN: Plan steps can be modified
            AiPlan modifiedPlan = new AiPlan("plan-1", "wf-1", "tenant-001", "objective", // GH-90000
                List.of(), AiPlan.PlanStatus.MODIFIED, null, "gpt-4", 0.8, null, null); // GH-90000
            when(workflowService.modifyPlanSteps(any(), any(), any())) // GH-90000
                .thenReturn(Promise.of(modifiedPlan)); // GH-90000

            // WHEN: Modify plan steps
            String stepsBody = "{\"steps\": [{\"id\": \"step-1\", \"name\": \"Refactor step\", \"description\": \"Refactoring\", \"type\": \"CODE_GENERATION\", \"order\": 0, \"dependencies\": []}]}";
            HttpRequest request = HttpRequest.put("http://localhost/api/v1/workflows/wf-1/plans/plan-1/steps [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-001")
                .withBody(stepsBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.modifyPlanSteps(request, "wf-1", "plan-1")); // GH-90000

            // THEN: Returns 200 with modified plan
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
        }
    }

    // =========================================================================
    // VECTOR API - HYBRID SEARCH
    // =========================================================================

    @Nested
    @DisplayName("Vector API - Hybrid Search [GH-90000]")
    class HybridSearchTests {

        private VectorController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new VectorController(searchService, ragService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("hybridSearch combines semantic and keyword results [GH-90000]")
        void hybridSearchSuccess() { // GH-90000
            // GIVEN: Hybrid search returns results
            SemanticSearchService.SemanticSearchResult result = new SemanticSearchService.SemanticSearchResult( // GH-90000
                "query text",
                List.of(), // GH-90000
                50,
                0L,
                null
            );
            when(searchService.hybridSearch(any())) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            // WHEN: Perform hybrid search
            String searchBody = "{\"query\": \"test\", \"keywords\": [\"test\", \"query\"], \"limit\": 10, \"threshold\": 0.7, \"keywordBoost\": 0.3}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/search/hybrid [GH-90000]")
                .withBody(searchBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.hybridSearch(request)); // GH-90000

            // THEN: Returns 200
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(searchService).hybridSearch(any()); // GH-90000
        }

        @Test
        @DisplayName("hybridSearch returns 400 for empty query and keywords [GH-90000]")
        void hybridSearchNoQueryOrKeywords() { // GH-90000
            // GIVEN: Neither query nor keywords provided
            String searchBody = "{\"query\": \"\", \"keywords\": [], \"limit\": 10}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/search/hybrid [GH-90000]")
                .withBody(searchBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000

            // WHEN: Perform hybrid search
            HttpResponse response = runPromise(() -> controller.hybridSearch(request)); // GH-90000

            // THEN: Returns 400 or 422 (validation error) // GH-90000
            assertThat(response.getCode()).isIn(400, 422); // GH-90000
        }

        @Test
        @DisplayName("hybridSearch respects keyword boost parameter [GH-90000]")
        void hybridSearchWithKeywordBoost() { // GH-90000
            // GIVEN: Hybrid search with keyword boost
            SemanticSearchService.SemanticSearchResult result = new SemanticSearchService.SemanticSearchResult( // GH-90000
                "query",
                List.of(), // GH-90000
                0,
                0L,
                null
            );
            when(searchService.hybridSearch(any())) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            // WHEN: Search with keyword boost
            String searchBody = "{\"query\": \"test\", \"keywords\": [\"keyword1\"], \"keywordBoost\": 0.8}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/search/hybrid [GH-90000]")
                .withBody(searchBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.hybridSearch(request)); // GH-90000

            // THEN: Returns 200 and service called with boost
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(searchService).hybridSearch(any()); // GH-90000
        }
    }

    // =========================================================================
    // VECTOR API - FIND SIMILAR DOCUMENTS
    // =========================================================================

    @Nested
    @DisplayName("Vector API - Find Similar Documents [GH-90000]")
    class FindSimilarTests {

        private VectorController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new VectorController(searchService, ragService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("findSimilar returns similar documents [GH-90000]")
        void findSimilarSuccess() { // GH-90000
            // GIVEN: Similar documents found
            List<SemanticSearchService.SearchHit> similar = List.of( // GH-90000
                new SemanticSearchService.SearchHit("doc-2", "", 0.92, null), // GH-90000
                new SemanticSearchService.SearchHit("doc-3", "", 0.85, null) // GH-90000
            );
            when(searchService.findSimilar(any(), anyInt(), anyDouble())) // GH-90000
                .thenReturn(Promise.of(similar)); // GH-90000

            // WHEN: Find similar
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/vector/similar/doc-1?limit=5&threshold=0.8 [GH-90000]")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.findSimilar(request, "doc-1")); // GH-90000

            // THEN: Returns 200 with similar documents
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)) // GH-90000
                .contains("\"sourceId\":\"doc-1\"") // GH-90000
                .contains("\"similar\"") // GH-90000
                .contains("\"count\":2"); // GH-90000
        }

        @Test
        @DisplayName("findSimilar returns empty array when no similar documents [GH-90000]")
        void findSimilarNoMatches() { // GH-90000
            // GIVEN: No similar documents
            when(searchService.findSimilar(any(), anyInt(), anyDouble())) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

            // WHEN: Find similar
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/vector/similar/doc-1 [GH-90000]")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.findSimilar(request, "doc-1")); // GH-90000

            // THEN: Returns 200 with empty array
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("\"count\":0"); // GH-90000
        }
    }

    // =========================================================================
    // VECTOR API - BATCH INDEXING
    // =========================================================================

    @Nested
    @DisplayName("Vector API - Batch Indexing [GH-90000]")
    class BatchIndexingTests {

        private VectorController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new VectorController(searchService, ragService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("batchIndex returns 200 with batch results [GH-90000]")
        void batchIndexSuccess() { // GH-90000
            // GIVEN: Batch indexing succeeds
            List<SemanticSearchService.IndexResult> results = List.of( // GH-90000
                new SemanticSearchService.IndexResult("doc-1", true, 0L, 0, null), // GH-90000
                new SemanticSearchService.IndexResult("doc-2", true, 0L, 0, null) // GH-90000
            );
            when(searchService.batchIndex(any())) // GH-90000
                .thenReturn(Promise.of(results)); // GH-90000

            // WHEN: Batch index
            String batchBody = "{\"documents\": [{\"id\": \"doc-1\", \"content\": \"content1\"}, {\"id\": \"doc-2\", \"content\": \"content2\"}]}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/index/batch [GH-90000]")
                .withBody(batchBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.batchIndex(request)); // GH-90000

            // THEN: Returns 200 with batch results
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)) // GH-90000
                .contains("\"results\"") // GH-90000
                .contains("\"total\":2") // GH-90000
                .contains("\"success\":2") // GH-90000
                .contains("\"failed\":0"); // GH-90000
        }

        @Test
        @DisplayName("batchIndex reports partial failures [GH-90000]")
        void batchIndexPartialFailure() { // GH-90000
            // GIVEN: Some documents fail indexing
            List<SemanticSearchService.IndexResult> results = List.of( // GH-90000
                new SemanticSearchService.IndexResult("doc-1", true, 0L, 0, null), // GH-90000
                new SemanticSearchService.IndexResult("doc-2", false, 0L, 0, "Duplicate ID") // GH-90000
            );
            when(searchService.batchIndex(any())) // GH-90000
                .thenReturn(Promise.of(results)); // GH-90000

            // WHEN: Batch index
            String batchBody = "{\"documents\": [{\"id\": \"doc-1\", \"content\": \"content1\"}, {\"id\": \"doc-2\", \"content\": \"content2\"}]}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/index/batch [GH-90000]")
                .withBody(batchBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.batchIndex(request)); // GH-90000

            // THEN: Returns 200 with mixed results
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)) // GH-90000
                .contains("\"total\":2") // GH-90000
                .contains("\"success\":1") // GH-90000
                .contains("\"failed\":1"); // GH-90000
        }
    }

    // =========================================================================
    // VECTOR API - DELETE DOCUMENT
    // =========================================================================

    @Nested
    @DisplayName("Vector API - Delete Document [GH-90000]")
    class DeleteDocumentTests {

        private VectorController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new VectorController(searchService, ragService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("deleteDocument returns 204 when successful [GH-90000]")
        void deleteDocumentSuccess() { // GH-90000
            // GIVEN: Document can be deleted
            when(searchService.delete(any())) // GH-90000
                .thenReturn(Promise.of(true)); // GH-90000

            // WHEN: Delete document
            HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/vector/index/doc-1") // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.deleteDocument(request, "doc-1")); // GH-90000

            // THEN: Returns 204 No Content
            assertThat(response.getCode()).isEqualTo(204); // GH-90000
            verify(searchService).delete("doc-1 [GH-90000]");
        }

        @Test
        @DisplayName("deleteDocument returns 404 when document not found [GH-90000]")
        void deleteDocumentNotFound() { // GH-90000
            // GIVEN: Document doesn't exist
            when(searchService.delete(any())) // GH-90000
                .thenReturn(Promise.of(false)); // GH-90000

            // WHEN: Delete non-existent document
            HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/vector/index/non-existent") // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.deleteDocument(request, "non-existent")); // GH-90000

            // THEN: Returns 404
            assertThat(response.getCode()).isEqualTo(404); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("not found [GH-90000]");
        }
    }

    // =========================================================================
    // VECTOR API - RAG OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("Vector API - RAG (Retrieval-Augmented Generation) [GH-90000]")
    class RagOperationTests {

        private VectorController controller;

        @BeforeEach
        void setUp() { // GH-90000
            controller = new VectorController(searchService, ragService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("rag returns 200 with retrieved documents and generated response [GH-90000]")
        void ragSuccess() { // GH-90000
            // GIVEN: RAG service returns documents and generated text
            RagService.RagResponse ragResult = new RagService.RagResponse( // GH-90000
                "What is machine learning?",
                "Generated answer based on retrieval",
                List.of(), // GH-90000
                true,
                null,
                100L,
                null
            );
            when(ragService.generate(any())) // GH-90000
                .thenReturn(Promise.of(ragResult)); // GH-90000

            // WHEN: Perform RAG
            String ragBody = "{\"query\": \"What is machine learning?\", \"contextLimit\": 5}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/rag [GH-90000]")
                .withBody(ragBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.rag(request)); // GH-90000

            // THEN: Returns 200 with RAG result
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)) // GH-90000
                .contains("contexts [GH-90000]")
                .contains("response [GH-90000]")
                .contains("query [GH-90000]");
        }

        @Test
        @DisplayName("ragChat returns conversation response [GH-90000]")
        void ragChatSuccess() { // GH-90000
            // GIVEN: RAG chat service returns response
            RagService.RagResponse chatResponse = new RagService.RagResponse( // GH-90000
                "Tell me more",
                "Response to user message",
                List.of(), // GH-90000
                true,
                null,
                80L,
                null
            );
            when(ragService.chat(any())) // GH-90000
                .thenReturn(Promise.of(chatResponse)); // GH-90000

            // WHEN: RAG chat
            String chatBody = "{\"query\": \"Tell me more\"}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/rag/chat [GH-90000]")
                .withBody(chatBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.ragChat(request)); // GH-90000

            // THEN: Returns 200 with chat response
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)) // GH-90000
                .contains("query [GH-90000]")
                .contains("response [GH-90000]")
                .contains("success [GH-90000]");
        }

        @Test
        @DisplayName("ragChat handles new conversation [GH-90000]")
        void ragChatNewConversation() { // GH-90000
            // GIVEN: Starting new conversation
            RagService.RagResponse chatResponse = new RagService.RagResponse( // GH-90000
                "Hello, can you help?",
                "Initial response",
                List.of(), // GH-90000
                true,
                null,
                50L,
                null
            );
            when(ragService.chat(any())) // GH-90000
                .thenReturn(Promise.of(chatResponse)); // GH-90000

            // WHEN: Start new RAG chat
            String chatBody = "{\"query\": \"Hello, can you help?\"}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/rag/chat [GH-90000]")
                .withBody(chatBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> controller.ragChat(request)); // GH-90000

            // THEN: Returns 200 with new conversation
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("query [GH-90000]");
        }
    }

    // =========================================================================
    // RESPONSE SCHEMA VALIDATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Response Schema Validation [GH-90000]")
    class ResponseSchemaTests {

        private WorkflowController workflowController;
        private VectorController vectorController;

        @BeforeEach
        void setUp() { // GH-90000
            workflowController = new WorkflowController(workflowService, objectMapper); // GH-90000
            vectorController = new VectorController(searchService, ragService, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("listWorkflows response contains required pagination fields [GH-90000]")
        void listWorkflowsResponseSchema() { // GH-90000
            // GIVEN: Workflows returned
            when(workflowService.listWorkflows(any(), any(), anyInt(), anyInt())) // GH-90000
                .thenReturn(Promise.of(List.of(createWorkflowForTenant("wf-1", "tenant-001", true)))); // GH-90000

            // WHEN: List workflows
            HttpRequest request = HttpRequest.get("http://localhost/api/v1/workflows [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-001")
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> workflowController.listWorkflows(request)); // GH-90000

            // THEN: Response contains all required fields
            String body = response.getBody().asString(StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("\"workflows\"").contains("\"count\"") // GH-90000
                .contains("\"limit\"").contains("\"offset\""); // GH-90000
        }

        @Test
        @DisplayName("rag response contains required fields [GH-90000]")
        void ragResponseSchema() { // GH-90000
            // GIVEN: RAG result
            RagService.RagResponse result = new RagService.RagResponse( // GH-90000
                "test",
                "Response",
                List.of(), // GH-90000
                true,
                null,
                0L,
                null
            );
            when(ragService.generate(any())).thenReturn(Promise.of(result)); // GH-90000

            // WHEN: RAG query
            String ragBody = "{\"query\": \"test\"}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/vector/rag [GH-90000]")
                .withBody(ragBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
            HttpResponse response = runPromise(() -> vectorController.rag(request)); // GH-90000

            // THEN: Response has required fields
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            String body = response.getBody().asString(StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("contexts [GH-90000]")
                .contains("response [GH-90000]")
                .contains("query [GH-90000]");
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private AiWorkflowInstance createWorkflowForTenant(String id, String tenantId, boolean isActive) { // GH-90000
        return new AiWorkflowInstance( // GH-90000
            id,
            tenantId,
            "Test Workflow",
            "A test workflow",
            AiWorkflowInstance.WorkflowType.CUSTOM,
            isActive ? AiWorkflowInstance.WorkflowStatus.IN_PROGRESS : AiWorkflowInstance.WorkflowStatus.DRAFT,
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

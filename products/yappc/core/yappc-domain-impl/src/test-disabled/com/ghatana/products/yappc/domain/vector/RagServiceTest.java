package com.ghatana.products.yappc.domain.vector;

import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RagService.
 *
 * @doc.type test
 * @doc.purpose Test RAG pipeline operations
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RagService Tests")
class RagServiceTest extends EventloopTestBase {

    private SemanticSearchService searchService;
    private LLMGateway llmGateway;
    private RagService service;

    @BeforeEach
    void setUp() {
        searchService = mock(SemanticSearchService.class);
        llmGateway = mock(LLMGateway.class);
        service = new RagService(searchService, llmGateway);
    }

    @Nested
    @DisplayName("RAG Generation")
    class RagGenerationTests {

        @Test
        @DisplayName("should generate response with context retrieval")
        void shouldGenerateResponseWithContextRetrieval() {
            // GIVEN
            String query = "How do I create a workflow?";

            SemanticSearchService.SemanticSearchResult searchResult =
                    new SemanticSearchService.SemanticSearchResult(
                            query,
                            List.of(
                                    new SemanticSearchService.SearchHit(
                                            "doc-1",
                                            "Workflows are created using the AiWorkflowService...",
                                            0.95,
                                            Map.of("source", "workflow-guide.md")
                                    ),
                                    new SemanticSearchService.SearchHit(
                                            "doc-2",
                                            "The workflow lifecycle includes DRAFT, IN_PROGRESS, PAUSED states...",
                                            0.85,
                                            Map.of("source", "workflow-states.md")
                                    )
                            ),
                            2,
                            15L,
                            null
                    );

            when(searchService.search(any(SemanticSearchService.SemanticSearchRequest.class)))
                    .thenReturn(Promise.of(searchResult));

            CompletionResult completionResult = CompletionResult.of(
                    "To create a workflow, use the AiWorkflowService.createWorkflow() method..."
            );
            when(llmGateway.complete(any()))
                    .thenReturn(Promise.of(completionResult));

            RagService.RagRequest request = new RagService.RagRequest(
                    query, null, 5, 0.7, 1000, 0.7, null
            );

            // WHEN
            RagService.RagResponse response = runPromise(() -> service.generate(request));

            // THEN
            assertThat(response).isNotNull();
            assertThat(response.success()).isTrue();
            assertThat(response.response()).contains("AiWorkflowService");
            assertThat(response.contexts()).hasSize(2);
            assertThat(response.contexts().get(0).id()).isEqualTo("doc-1");
            verify(searchService).search(any(SemanticSearchService.SemanticSearchRequest.class));
            verify(llmGateway).complete(any());
        }

        @Test
        @DisplayName("should handle empty search results")
        void shouldHandleEmptySearchResults() {
            // GIVEN
            String query = "Completely unrelated query";

            SemanticSearchService.SemanticSearchResult emptyResult =
                    new SemanticSearchService.SemanticSearchResult(
                            query, List.of(), 0, 5L, null
                    );

            when(searchService.search(any(SemanticSearchService.SemanticSearchRequest.class)))
                    .thenReturn(Promise.of(emptyResult));

            CompletionResult completionResult = CompletionResult.of(
                    "I don't have enough context to answer that question."
            );
            when(llmGateway.complete(any()))
                    .thenReturn(Promise.of(completionResult));

            RagService.RagRequest request = RagService.RagRequest.of(query);

            // WHEN
            RagService.RagResponse response = runPromise(() -> service.generate(request));

            // THEN
            assertThat(response).isNotNull();
            assertThat(response.success()).isTrue();
            assertThat(response.contexts()).isEmpty();
            // Should have a warning about no context
            assertThat(response.warning()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Conversational RAG")
    class ConversationalRagTests {

        @Test
        @DisplayName("should maintain conversation context")
        void shouldMaintainConversationContext() {
            // GIVEN
            List<RagService.ConversationTurn> history = List.of(
                    new RagService.ConversationTurn(
                            "What is a workflow?",
                            "A workflow is an automated sequence of steps..."
                    )
            );

            String currentQuery = "How do I start one?";

            SemanticSearchService.SemanticSearchResult searchResult =
                    new SemanticSearchService.SemanticSearchResult(
                            currentQuery,
                            List.of(
                                    new SemanticSearchService.SearchHit(
                                            "doc-1",
                                            "To start a workflow, call startWorkflow(workflowId)...",
                                            0.9,
                                            Map.of()
                                    )
                            ),
                            1,
                            10L,
                            null
                    );

            when(searchService.search(any(SemanticSearchService.SemanticSearchRequest.class)))
                    .thenReturn(Promise.of(searchResult));

            CompletionResult completionResult = CompletionResult.of(
                    "To start a workflow, use the startWorkflow() method with your workflow ID..."
            );
            when(llmGateway.complete(any()))
                    .thenReturn(Promise.of(completionResult));

            RagService.ConversationalRagRequest request =
                    new RagService.ConversationalRagRequest(
                            currentQuery, history, null, 5, 0.7, 1000, 0.7, null
                    );

            // WHEN
            RagService.RagResponse response = runPromise(() -> service.chat(request));

            // THEN
            assertThat(response).isNotNull();
            assertThat(response.success()).isTrue();
            assertThat(response.response()).isNotEmpty();
            verify(searchService).search(any(SemanticSearchService.SemanticSearchRequest.class));
            verify(llmGateway).complete(any());
        }
    }

    @Nested
    @DisplayName("Usage Statistics")
    class UsageStatisticsTests {

        @Test
        @DisplayName("should track usage stats")
        void shouldTrackUsageStats() {
            // GIVEN
            SemanticSearchService.SemanticSearchResult searchResult =
                    new SemanticSearchService.SemanticSearchResult(
                            "query",
                            List.of(
                                    new SemanticSearchService.SearchHit(
                                            "doc-1", "Content", 0.9, Map.of()
                                    )
                            ),
                            1,
                            10L,
                            null
                    );

            when(searchService.search(any(SemanticSearchService.SemanticSearchRequest.class)))
                    .thenReturn(Promise.of(searchResult));

            CompletionResult completionResult = CompletionResult.of("Answer with some length to it");
            when(llmGateway.complete(any()))
                    .thenReturn(Promise.of(completionResult));

            RagService.RagRequest request = RagService.RagRequest.of("test query");

            // WHEN
            RagService.RagResponse response = runPromise(() -> service.generate(request));

            // THEN
            assertThat(response.usage()).isNotNull();
            assertThat(response.usage().contextDocumentsUsed()).isEqualTo(1);
            assertThat(response.usage().responseLength()).isGreaterThan(0);
        }
    }
}

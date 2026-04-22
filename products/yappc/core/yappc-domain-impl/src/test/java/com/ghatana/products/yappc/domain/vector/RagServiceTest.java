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
@DisplayName("RagService Tests [GH-90000]")
class RagServiceTest extends EventloopTestBase {

    private SemanticSearchService searchService;
    private LLMGateway llmGateway;
    private RagService service;

    @BeforeEach
    void setUp() { // GH-90000
        searchService = mock(SemanticSearchService.class); // GH-90000
        llmGateway = mock(LLMGateway.class); // GH-90000
        service = new RagService(searchService, llmGateway); // GH-90000
    }

    @Nested
    @DisplayName("RAG Generation [GH-90000]")
    class RagGenerationTests {

        @Test
        @DisplayName("should generate response with context retrieval [GH-90000]")
        void shouldGenerateResponseWithContextRetrieval() { // GH-90000
            // GIVEN
            String query = "How do I create a workflow?";

            SemanticSearchService.SemanticSearchResult searchResult =
                    new SemanticSearchService.SemanticSearchResult( // GH-90000
                            query,
                            List.of( // GH-90000
                                    new SemanticSearchService.SearchHit( // GH-90000
                                            "doc-1",
                                            "Workflows are created using the AiWorkflowService...",
                                            0.95,
                                            Map.of("source", "workflow-guide.md") // GH-90000
                                    ),
                                    new SemanticSearchService.SearchHit( // GH-90000
                                            "doc-2",
                                            "The workflow lifecycle includes DRAFT, IN_PROGRESS, PAUSED states...",
                                            0.85,
                                            Map.of("source", "workflow-states.md") // GH-90000
                                    )
                            ),
                            2,
                            15L,
                            null
                    );

            when(searchService.search(any(SemanticSearchService.SemanticSearchRequest.class))) // GH-90000
                    .thenReturn(Promise.of(searchResult)); // GH-90000

            CompletionResult completionResult = CompletionResult.of( // GH-90000
                    "To create a workflow, use the AiWorkflowService.createWorkflow() method..." // GH-90000
            );
            when(llmGateway.complete(any())) // GH-90000
                    .thenReturn(Promise.of(completionResult)); // GH-90000

            RagService.RagRequest request = new RagService.RagRequest( // GH-90000
                    query, null, 5, 0.7, 1000, 0.7, null
            );

            // WHEN
            RagService.RagResponse response = runPromise(() -> service.generate(request)); // GH-90000

            // THEN
            assertThat(response).isNotNull(); // GH-90000
            assertThat(response.success()).isTrue(); // GH-90000
            assertThat(response.response()).contains("AiWorkflowService [GH-90000]");
            assertThat(response.contexts()).hasSize(2); // GH-90000
            assertThat(response.contexts().get(0).id()).isEqualTo("doc-1 [GH-90000]");
            verify(searchService).search(any(SemanticSearchService.SemanticSearchRequest.class)); // GH-90000
            verify(llmGateway).complete(any()); // GH-90000
        }

        @Test
        @DisplayName("should handle empty search results [GH-90000]")
        void shouldHandleEmptySearchResults() { // GH-90000
            // GIVEN
            String query = "Completely unrelated query";

            SemanticSearchService.SemanticSearchResult emptyResult =
                    new SemanticSearchService.SemanticSearchResult( // GH-90000
                            query, List.of(), 0, 5L, null // GH-90000
                    );

            when(searchService.search(any(SemanticSearchService.SemanticSearchRequest.class))) // GH-90000
                    .thenReturn(Promise.of(emptyResult)); // GH-90000

            CompletionResult completionResult = CompletionResult.of( // GH-90000
                    "I don't have enough context to answer that question."
            );
            when(llmGateway.complete(any())) // GH-90000
                    .thenReturn(Promise.of(completionResult)); // GH-90000

            RagService.RagRequest request = RagService.RagRequest.of(query); // GH-90000

            // WHEN
            RagService.RagResponse response = runPromise(() -> service.generate(request)); // GH-90000

            // THEN
            assertThat(response).isNotNull(); // GH-90000
            assertThat(response.success()).isTrue(); // GH-90000
            assertThat(response.contexts()).isEmpty(); // GH-90000
            // Should have a warning about no context
            assertThat(response.warning()).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Conversational RAG [GH-90000]")
    class ConversationalRagTests {

        @Test
        @DisplayName("should maintain conversation context [GH-90000]")
        void shouldMaintainConversationContext() { // GH-90000
            // GIVEN
            List<RagService.ConversationTurn> history = List.of( // GH-90000
                    new RagService.ConversationTurn( // GH-90000
                            "What is a workflow?",
                            "A workflow is an automated sequence of steps..."
                    )
            );

            String currentQuery = "How do I start one?";

            SemanticSearchService.SemanticSearchResult searchResult =
                    new SemanticSearchService.SemanticSearchResult( // GH-90000
                            currentQuery,
                            List.of( // GH-90000
                                    new SemanticSearchService.SearchHit( // GH-90000
                                            "doc-1",
                                            "To start a workflow, call startWorkflow(workflowId)...", // GH-90000
                                            0.9,
                                            Map.of() // GH-90000
                                    )
                            ),
                            1,
                            10L,
                            null
                    );

            when(searchService.search(any(SemanticSearchService.SemanticSearchRequest.class))) // GH-90000
                    .thenReturn(Promise.of(searchResult)); // GH-90000

            CompletionResult completionResult = CompletionResult.of( // GH-90000
                    "To start a workflow, use the startWorkflow() method with your workflow ID..." // GH-90000
            );
            when(llmGateway.complete(any())) // GH-90000
                    .thenReturn(Promise.of(completionResult)); // GH-90000

            RagService.ConversationalRagRequest request =
                    new RagService.ConversationalRagRequest( // GH-90000
                            currentQuery, history, null, 5, 0.7, 1000, 0.7, null
                    );

            // WHEN
            RagService.RagResponse response = runPromise(() -> service.chat(request)); // GH-90000

            // THEN
            assertThat(response).isNotNull(); // GH-90000
            assertThat(response.success()).isTrue(); // GH-90000
            assertThat(response.response()).isNotEmpty(); // GH-90000
            verify(searchService).search(any(SemanticSearchService.SemanticSearchRequest.class)); // GH-90000
            verify(llmGateway).complete(any()); // GH-90000
        }
    }

    @Nested
    @DisplayName("Usage Statistics [GH-90000]")
    class UsageStatisticsTests {

        @Test
        @DisplayName("should track usage stats [GH-90000]")
        void shouldTrackUsageStats() { // GH-90000
            // GIVEN
            SemanticSearchService.SemanticSearchResult searchResult =
                    new SemanticSearchService.SemanticSearchResult( // GH-90000
                            "query",
                            List.of( // GH-90000
                                    new SemanticSearchService.SearchHit( // GH-90000
                                            "doc-1", "Content", 0.9, Map.of() // GH-90000
                                    )
                            ),
                            1,
                            10L,
                            null
                    );

            when(searchService.search(any(SemanticSearchService.SemanticSearchRequest.class))) // GH-90000
                    .thenReturn(Promise.of(searchResult)); // GH-90000

            CompletionResult completionResult = CompletionResult.of("Answer with some length to it [GH-90000]");
            when(llmGateway.complete(any())) // GH-90000
                    .thenReturn(Promise.of(completionResult)); // GH-90000

            RagService.RagRequest request = RagService.RagRequest.of("test query [GH-90000]");

            // WHEN
            RagService.RagResponse response = runPromise(() -> service.generate(request)); // GH-90000

            // THEN
            assertThat(response.usage()).isNotNull(); // GH-90000
            assertThat(response.usage().contextDocumentsUsed()).isEqualTo(1); // GH-90000
            assertThat(response.usage().responseLength()).isGreaterThan(0); // GH-90000
        }
    }
}

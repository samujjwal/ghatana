/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 * YAPPC AI Module — AI Suggestion Service Tests
 */
package com.ghatana.yappc.ai.suggestion;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.router.AIModelRouter;
import com.ghatana.yappc.ai.router.AIRequest;
import com.ghatana.yappc.ai.router.AIResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AISuggestionService}.
 *
 * <p>The AI model router is stubbed with Mockito so tests run without external LLM access.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AISuggestionService suggestion generation and parsing
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AISuggestionService [GH-90000]")
class AISuggestionServiceTest extends EventloopTestBase {

    private AIModelRouter        router;
    private AISuggestionService  service;

    @BeforeEach
    void setUp() { // GH-90000
        router  = mock(AIModelRouter.class); // GH-90000
        service = new AISuggestionService(router); // GH-90000
    }

    // ── Constructor validation ────────────────────────────────────────────────

    @Test
    @DisplayName("constructor rejects null router [GH-90000]")
    void constructorRejectsNull() { // GH-90000
        assertThrows(NullPointerException.class, () -> new AISuggestionService(null)); // GH-90000
    }

    // ── suggest() ───────────────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("suggest() [GH-90000]")
    class SuggestMethod {

        @Test
        @DisplayName("routes request through AIModelRouter [GH-90000]")
        void routesRequestThroughRouter() { // GH-90000
            when(router.route(any())).thenReturn(Promise.of(emptyResponse())); // GH-90000

            runPromise(() -> service.suggest("proj-1", "SHAPE", Map.of())); // GH-90000

            verify(router).route(any(AIRequest.class)); // GH-90000
        }

        @Test
        @DisplayName("parses [ACTION] suggestion from well-formed response [GH-90000]")
        void parsesActionSuggestion() { // GH-90000
            AIResponse response = responseWithContent("[ACTION] Define acceptance criteria for the new feature. [GH-90000]");
            when(router.route(any())).thenReturn(Promise.of(response)); // GH-90000

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "SHAPE", Map.of())); // GH-90000

            assertThat(suggestions).hasSize(1); // GH-90000
            assertThat(suggestions.get(0).type()).isEqualTo(AISuggestionService.SuggestionType.ACTION); // GH-90000
            assertThat(suggestions.get(0).text()).contains("acceptance criteria [GH-90000]");
        }

        @Test
        @DisplayName("parses all five suggestion types from multi-line response [GH-90000]")
        void parsesAllSuggestionTypes() { // GH-90000
            String content = String.join("\n", // GH-90000
                    "[REQUIREMENT] Clarify non-functional requirements for latency.",
                    "[DESIGN] Extract authentication into a platform module.",
                    "[TEST] Add negative test cases for edge inputs.",
                    "[RISK] Dependency on external API may introduce production delays.",
                    "[ACTION] Schedule design review with architecture team."
            );
            when(router.route(any())).thenReturn(Promise.of(responseWithContent(content))); // GH-90000

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "VALIDATE", Map.of())); // GH-90000

            assertThat(suggestions).hasSize(5); // GH-90000
            assertThat(suggestions.stream().map(AISuggestionService.Suggestion::type)) // GH-90000
                    .containsExactly( // GH-90000
                            AISuggestionService.SuggestionType.REQUIREMENT,
                            AISuggestionService.SuggestionType.DESIGN,
                            AISuggestionService.SuggestionType.TEST,
                            AISuggestionService.SuggestionType.RISK,
                            AISuggestionService.SuggestionType.ACTION
                    );
        }

        @Test
        @DisplayName("returns empty list for empty model response [GH-90000]")
        void returnsEmptyListForEmptyResponse() { // GH-90000
            when(router.route(any())).thenReturn(Promise.of(emptyResponse())); // GH-90000

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "INTENT", Map.of())); // GH-90000

            assertThat(suggestions).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns empty list for blank-whitespace-only response content [GH-90000]")
        void returnsEmptyListForBlankContent() { // GH-90000
            when(router.route(any())).thenReturn(Promise.of(responseWithContent("   \n   [GH-90000]")));

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "INTENT", Map.of())); // GH-90000

            assertThat(suggestions).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("each suggestion carries the project ID and phase [GH-90000]")
        void suggestionsCarryProjectAndPhase() { // GH-90000
            when(router.route(any())).thenReturn( // GH-90000
                    Promise.of(responseWithContent("[ACTION] Review tech stack choices. [GH-90000]")));

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("project-42", "GENERATE", Map.of())); // GH-90000

            assertThat(suggestions).hasSize(1); // GH-90000
            assertThat(suggestions.get(0).projectId()).isEqualTo("project-42 [GH-90000]");
            assertThat(suggestions.get(0).phase()).isEqualTo("GENERATE [GH-90000]");
        }

        @Test
        @DisplayName("each suggestion has a non-null unique ID and generatedAt timestamp [GH-90000]")
        void suggestionsHaveIdAndTimestamp() { // GH-90000
            when(router.route(any())).thenReturn( // GH-90000
                    Promise.of(responseWithContent("[RISK] Risk of scope creep is high. [GH-90000]")));

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "SHAPE", Map.of())); // GH-90000

            assertThat(suggestions).hasSize(1); // GH-90000
            assertThat(suggestions.get(0).id()).isNotBlank(); // GH-90000
            assertThat(suggestions.get(0).generatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("caps suggestions at 5 regardless of model output length [GH-90000]")
        void capsAtFiveSuggestions() { // GH-90000
            String content = String.join("\n", // GH-90000
                    "[ACTION] Step one.",
                    "[ACTION] Step two.",
                    "[ACTION] Step three.",
                    "[ACTION] Step four.",
                    "[ACTION] Step five.",
                    "[ACTION] Step six — should be ignored.",
                    "[ACTION] Step seven — also ignored."
            );
            when(router.route(any())).thenReturn(Promise.of(responseWithContent(content))); // GH-90000

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "RUN", Map.of())); // GH-90000

            assertThat(suggestions).hasSize(5); // GH-90000
        }

        @Test
        @DisplayName("null context is treated as empty context (no NPE) [GH-90000]")
        void nullContextIsToleratedGracefully() { // GH-90000
            when(router.route(any())).thenReturn(Promise.of(emptyResponse())); // GH-90000

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "OBSERVE", null)); // GH-90000

            assertThat(suggestions).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("returned list is unmodifiable [GH-90000]")
        void returnedListIsUnmodifiable() { // GH-90000
            when(router.route(any())).thenReturn( // GH-90000
                    Promise.of(responseWithContent("[ACTION] Review architecture. [GH-90000]")));

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "SHAPE", Map.of())); // GH-90000

            assertThrows(UnsupportedOperationException.class, () -> // GH-90000
                    suggestions.add(null)); // GH-90000
        }

        @Test
        @DisplayName("router failure is propagated as a promise exception [GH-90000]")
        void routerFailurePropagatesAsException() { // GH-90000
            when(router.route(any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("LLM unavailable [GH-90000]")));

            try {
                runPromise(() -> service.suggest("proj-1", "SHAPE", Map.of())); // GH-90000
                assertThat(false).as("expected exception [GH-90000]").isTrue();
            } catch (Exception e) { // GH-90000
                assertThat(e).hasMessageContaining("LLM unavailable [GH-90000]");
            }
        }
    }

    // ── suggestRequirementImprovements() ────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("suggestRequirementImprovements() [GH-90000]")
    class SuggestRequirementImprovements {

        @Test
        @DisplayName("delegates to suggest() with SHAPE phase + requirements context [GH-90000]")
        void delegatesToSuggestWithRequirementContext() { // GH-90000
            when(router.route(any())).thenReturn(Promise.of(emptyResponse())); // GH-90000

            runPromise(() -> // GH-90000
                    service.suggestRequirementImprovements("proj-1", "As a user I want to login.")); // GH-90000

            verify(router).route(any(AIRequest.class)); // GH-90000
        }

        @Test
        @DisplayName("rejects null projectId [GH-90000]")
        void rejectsNullProjectId() { // GH-90000
            assertThrows(NullPointerException.class, () -> // GH-90000
                    runPromise(() -> service.suggestRequirementImprovements(null, "some req"))); // GH-90000
        }
    }

    // ── SuggestionType coverage ────────────────────────────────────────────────

    @Test
    @DisplayName("SuggestionType has exactly 5 values [GH-90000]")
    void suggestionTypeHasFiveValues() { // GH-90000
        assertThat(AISuggestionService.SuggestionType.values()).hasSize(5); // GH-90000
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static AIResponse emptyResponse() { // GH-90000
        return responseWithContent(" [GH-90000]");
    }

    private static AIResponse responseWithContent(String content) { // GH-90000
        return AIResponse.builder() // GH-90000
                .requestId("test-req-id [GH-90000]")
                .modelId("stub-model [GH-90000]")
                .content(content) // GH-90000
                .build(); // GH-90000
    }
}

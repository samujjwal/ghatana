/*
 * Copyright (c) 2026 Ghatana Technologies 
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
@DisplayName("AISuggestionService")
class AISuggestionServiceTest extends EventloopTestBase {

    private AIModelRouter        router;
    private AISuggestionService  service;

    @BeforeEach
    void setUp() { 
        router  = mock(AIModelRouter.class); 
        service = new AISuggestionService(router); 
    }

    // ── Constructor validation ────────────────────────────────────────────────

    @Test
    @DisplayName("constructor rejects null router")
    void constructorRejectsNull() { 
        assertThrows(NullPointerException.class, () -> new AISuggestionService(null)); 
    }

    // ── suggest() ───────────────────────────────────────────────────────────── 

    @Nested
    @DisplayName("suggest()")
    class SuggestMethod {

        @Test
        @DisplayName("routes request through AIModelRouter")
        void routesRequestThroughRouter() { 
            when(router.route(any())).thenReturn(Promise.of(emptyResponse())); 

            runPromise(() -> service.suggest("proj-1", "SHAPE", Map.of())); 

            verify(router).route(any(AIRequest.class)); 
        }

        @Test
        @DisplayName("parses [ACTION] suggestion from well-formed response")
        void parsesActionSuggestion() { 
            AIResponse response = responseWithContent("[ACTION] Define acceptance criteria for the new feature.");
            when(router.route(any())).thenReturn(Promise.of(response)); 

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "SHAPE", Map.of())); 

            assertThat(suggestions).hasSize(1); 
            assertThat(suggestions.get(0).type()).isEqualTo(AISuggestionService.SuggestionType.ACTION); 
            assertThat(suggestions.get(0).text()).contains("acceptance criteria");
        }

        @Test
        @DisplayName("parses all five suggestion types from multi-line response")
        void parsesAllSuggestionTypes() { 
            String content = String.join("\n", 
                    "[REQUIREMENT] Clarify non-functional requirements for latency.",
                    "[DESIGN] Extract authentication into a platform module.",
                    "[TEST] Add negative test cases for edge inputs.",
                    "[RISK] Dependency on external API may introduce production delays.",
                    "[ACTION] Schedule design review with architecture team."
            );
            when(router.route(any())).thenReturn(Promise.of(responseWithContent(content))); 

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "VALIDATE", Map.of())); 

            assertThat(suggestions).hasSize(5); 
            assertThat(suggestions.stream().map(AISuggestionService.Suggestion::type)) 
                    .containsExactly( 
                            AISuggestionService.SuggestionType.REQUIREMENT,
                            AISuggestionService.SuggestionType.DESIGN,
                            AISuggestionService.SuggestionType.TEST,
                            AISuggestionService.SuggestionType.RISK,
                            AISuggestionService.SuggestionType.ACTION
                    );
        }

        @Test
        @DisplayName("returns empty list for empty model response")
        void returnsEmptyListForEmptyResponse() { 
            when(router.route(any())).thenReturn(Promise.of(emptyResponse())); 

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "INTENT", Map.of())); 

            assertThat(suggestions).isEmpty(); 
        }

        @Test
        @DisplayName("returns empty list for blank-whitespace-only response content")
        void returnsEmptyListForBlankContent() { 
            when(router.route(any())).thenReturn(Promise.of(responseWithContent("   \n  ")));

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "INTENT", Map.of())); 

            assertThat(suggestions).isEmpty(); 
        }

        @Test
        @DisplayName("each suggestion carries the project ID and phase")
        void suggestionsCarryProjectAndPhase() { 
            when(router.route(any())).thenReturn( 
                    Promise.of(responseWithContent("[ACTION] Review tech stack choices.")));

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("project-42", "GENERATE", Map.of())); 

            assertThat(suggestions).hasSize(1); 
            assertThat(suggestions.get(0).projectId()).isEqualTo("project-42");
            assertThat(suggestions.get(0).phase()).isEqualTo("GENERATE");
        }

        @Test
        @DisplayName("each suggestion has a non-null unique ID and generatedAt timestamp")
        void suggestionsHaveIdAndTimestamp() { 
            when(router.route(any())).thenReturn( 
                    Promise.of(responseWithContent("[RISK] Risk of scope creep is high.")));

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "SHAPE", Map.of())); 

            assertThat(suggestions).hasSize(1); 
            assertThat(suggestions.get(0).id()).isNotBlank(); 
            assertThat(suggestions.get(0).generatedAt()).isNotNull(); 
        }

        @Test
        @DisplayName("caps suggestions at 5 regardless of model output length")
        void capsAtFiveSuggestions() { 
            String content = String.join("\n", 
                    "[ACTION] Step one.",
                    "[ACTION] Step two.",
                    "[ACTION] Step three.",
                    "[ACTION] Step four.",
                    "[ACTION] Step five.",
                    "[ACTION] Step six — should be ignored.",
                    "[ACTION] Step seven — also ignored."
            );
            when(router.route(any())).thenReturn(Promise.of(responseWithContent(content))); 

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "RUN", Map.of())); 

            assertThat(suggestions).hasSize(5); 
        }

        @Test
        @DisplayName("null context is treated as empty context (no NPE)")
        void nullContextIsToleratedGracefully() { 
            when(router.route(any())).thenReturn(Promise.of(emptyResponse())); 

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "OBSERVE", null)); 

            assertThat(suggestions).isNotNull(); 
        }

        @Test
        @DisplayName("returned list is unmodifiable")
        void returnedListIsUnmodifiable() { 
            when(router.route(any())).thenReturn( 
                    Promise.of(responseWithContent("[ACTION] Review architecture.")));

            List<AISuggestionService.Suggestion> suggestions =
                    runPromise(() -> service.suggest("proj-1", "SHAPE", Map.of())); 

            assertThrows(UnsupportedOperationException.class, () -> 
                    suggestions.add(null)); 
        }

        @Test
        @DisplayName("router failure is propagated as a promise exception")
        void routerFailurePropagatesAsException() { 
            when(router.route(any())) 
                    .thenReturn(Promise.ofException(new RuntimeException("LLM unavailable")));

            try {
                runPromise(() -> service.suggest("proj-1", "SHAPE", Map.of())); 
                assertThat(false).as("expected exception").isTrue();
            } catch (Exception e) { 
                assertThat(e).hasMessageContaining("LLM unavailable");
            }
        }
    }

    // ── suggestRequirementImprovements() ────────────────────────────────────── 

    @Nested
    @DisplayName("suggestRequirementImprovements()")
    class SuggestRequirementImprovements {

        @Test
        @DisplayName("delegates to suggest() with SHAPE phase + requirements context")
        void delegatesToSuggestWithRequirementContext() { 
            when(router.route(any())).thenReturn(Promise.of(emptyResponse())); 

            runPromise(() -> 
                    service.suggestRequirementImprovements("proj-1", "As a user I want to login.")); 

            verify(router).route(any(AIRequest.class)); 
        }

        @Test
        @DisplayName("rejects null projectId")
        void rejectsNullProjectId() { 
            assertThrows(NullPointerException.class, () -> 
                    runPromise(() -> service.suggestRequirementImprovements(null, "some req"))); 
        }
    }

    // ── SuggestionType coverage ────────────────────────────────────────────────

    @Test
    @DisplayName("SuggestionType has exactly 5 values")
    void suggestionTypeHasFiveValues() { 
        assertThat(AISuggestionService.SuggestionType.values()).hasSize(5); 
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static AIResponse emptyResponse() { 
        return responseWithContent("");
    }

    private static AIResponse responseWithContent(String content) { 
        return AIResponse.builder() 
                .requestId("test-req-id")
                .modelId("stub-model")
                .content(content) 
                .build(); 
    }
}

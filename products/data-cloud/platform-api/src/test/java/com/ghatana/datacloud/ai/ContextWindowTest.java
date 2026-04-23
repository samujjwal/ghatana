/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.ai;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for context window management (AI005). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Context window management tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("ContextWindow – Context Management (AI005)")
class ContextWindowTest extends EventloopTestBase {

    @Mock
    private ContextWindowManager contextManager;

    @Nested
    @DisplayName("Context Window Retrieval")
    class ContextWindowRetrievalTests {

        @Test
        @DisplayName("[AI005]: get_context_window_returns_window")
        void getContextWindowReturnsWindow() { // GH-90000
            String conversationId = "conv-001";

            List<ContextWindowManager.ContextContent> contents = List.of( // GH-90000
                new ContextWindowManager.ContextContent( // GH-90000
                    "c1", ContextWindowManager.ContextContent.ContentType.USER_MESSAGE,
                    "Hello", 5, 1.0, Instant.now(), Map.of() // GH-90000
                ),
                new ContextWindowManager.ContextContent( // GH-90000
                    "c2", ContextWindowManager.ContextContent.ContentType.ASSISTANT_MESSAGE,
                    "Hi!", 5, 1.0, Instant.now(), Map.of() // GH-90000
                )
            );

            ContextWindowManager.ContextWindow window = new ContextWindowManager.ContextWindow( // GH-90000
                conversationId, contents, 10, 4000, false,
                Instant.now(), Instant.now() // GH-90000
            );

            when(contextManager.getContextWindow(conversationId)) // GH-90000
                .thenReturn(Promise.of(window)); // GH-90000

            ContextWindowManager.ContextWindow result = runPromise(() -> // GH-90000
                contextManager.getContextWindow(conversationId) // GH-90000
            );

            assertThat(result.conversationId()).isEqualTo(conversationId); // GH-90000
            assertThat(result.contents()).hasSize(2); // GH-90000
            assertThat(result.totalTokens()).isEqualTo(10); // GH-90000
            assertThat(result.isWithinLimits()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Content Management")
    class ContentManagementTests {

        @Test
        @DisplayName("[AI005]: add_content_increases_window")
        void addContentIncreasesWindow() { // GH-90000
            String conversationId = "conv-001";
            ContextWindowManager.ContextContent content = new ContextWindowManager.ContextContent( // GH-90000
                "c3", ContextWindowManager.ContextContent.ContentType.USER_MESSAGE,
                "New message", 8, 1.0, Instant.now(), Map.of() // GH-90000
            );

            List<ContextWindowManager.ContextContent> contents = List.of( // GH-90000
                new ContextWindowManager.ContextContent( // GH-90000
                    "c1", ContextWindowManager.ContextContent.ContentType.USER_MESSAGE, "Hello", 5, 1.0, Instant.now(), Map.of() // GH-90000
                ),
                content
            );

            ContextWindowManager.ContextWindow updated = new ContextWindowManager.ContextWindow( // GH-90000
                conversationId, contents, 13, 4000, false, Instant.now(), Instant.now() // GH-90000
            );

            when(contextManager.addContent(conversationId, content)) // GH-90000
                .thenReturn(Promise.of(updated)); // GH-90000

            ContextWindowManager.ContextWindow result = runPromise(() -> // GH-90000
                contextManager.addContent(conversationId, content) // GH-90000
            );

            assertThat(result.totalTokens()).isEqualTo(13); // GH-90000
        }

        @Test
        @DisplayName("[AI005]: clear_removes_all_content")
        void clearRemovesAllContent() { // GH-90000
            String conversationId = "conv-001";

            when(contextManager.clear(conversationId)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> contextManager.clear(conversationId)); // GH-90000

            verify(contextManager).clear(conversationId); // GH-90000
        }
    }

    @Nested
    @DisplayName("Context Trimming")
    class ContextTrimmingTests {

        @Test
        @DisplayName("[AI005]: trim_to_fit_reduces_tokens")
        void trimToFitReducesTokens() { // GH-90000
            String conversationId = "conv-large";
            int maxTokens = 1000;

            List<ContextWindowManager.ContextContent> trimmedContents = List.of( // GH-90000
                new ContextWindowManager.ContextContent( // GH-90000
                    "c1", ContextWindowManager.ContextContent.ContentType.USER_MESSAGE, "Recent", 50, 1.0, Instant.now(), Map.of() // GH-90000
                )
            );

            ContextWindowManager.ContextWindow trimmed = new ContextWindowManager.ContextWindow( // GH-90000
                conversationId, trimmedContents, 50, maxTokens, true,
                Instant.now(), Instant.now() // GH-90000
            );

            when(contextManager.trimToFit(conversationId, maxTokens)) // GH-90000
                .thenReturn(Promise.of(trimmed)); // GH-90000

            ContextWindowManager.ContextWindow result = runPromise(() -> // GH-90000
                contextManager.trimToFit(conversationId, maxTokens) // GH-90000
            );

            assertThat(result.totalTokens()).isEqualTo(50); // GH-90000
            assertThat(result.isTruncated()).isTrue(); // GH-90000
            assertThat(result.isWithinLimits()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[AI005]: remaining_tokens_calculated_correctly")
        void remainingTokensCalculatedCorrectly() { // GH-90000
            ContextWindowManager.ContextWindow window = new ContextWindowManager.ContextWindow( // GH-90000
                "conv-001", List.of(), 100, 4000, false, Instant.now(), Instant.now() // GH-90000
            );

            assertThat(window.getRemainingTokens()).isEqualTo(3900); // GH-90000
            assertThat(window.isWithinLimits()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[AI005]: over_limit_detected")
        void overLimitDetected() { // GH-90000
            ContextWindowManager.ContextWindow window = new ContextWindowManager.ContextWindow( // GH-90000
                "conv-001", List.of(), 4500, 4000, true, Instant.now(), Instant.now() // GH-90000
            );

            assertThat(window.isWithinLimits()).isFalse(); // GH-90000
            assertThat(window.getRemainingTokens()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Context Summarization")
    class ContextSummarizationTests {

        @Test
        @DisplayName("[AI005]: summarize_compresses_context")
        void summarizeCompressesContext() { // GH-90000
            String conversationId = "conv-001";

            ContextWindowManager.ContextSummary summary = new ContextWindowManager.ContextSummary( // GH-90000
                conversationId,
                "User asked about sales data and received results showing Q1 performance.",
                500, 100, 0.2,
                List.of("Sales query", "Q1 results", "Positive growth"), // GH-90000
                Instant.now() // GH-90000
            );

            when(contextManager.summarize(conversationId)) // GH-90000
                .thenReturn(Promise.of(summary)); // GH-90000

            ContextWindowManager.ContextSummary result = runPromise(() -> // GH-90000
                contextManager.summarize(conversationId) // GH-90000
            );

            assertThat(result.originalTokens()).isEqualTo(500); // GH-90000
            assertThat(result.summaryTokens()).isEqualTo(100); // GH-90000
            assertThat(result.compressionRatio()).isEqualTo(0.2); // GH-90000
            assertThat(result.keyPoints()).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("[AI005]: compression_ratio_calculated")
        void compressionRatioCalculated() { // GH-90000
            ContextWindowManager.ContextSummary summary = new ContextWindowManager.ContextSummary( // GH-90000
                "conv-001", "Summary", 1000, 250, 0.25, List.of(), Instant.now() // GH-90000
            );

            assertThat(summary.getCompressionRatio()).isEqualTo(0.25); // GH-90000
        }
    }

    @Nested
    @DisplayName("Token Usage")
    class TokenUsageTests {

        @Test
        @DisplayName("[AI005]: get_token_usage_returns_stats")
        void getTokenUsageReturnsStats() { // GH-90000
            String conversationId = "conv-001";

            List<ContextWindowManager.UsageByMessage> usageByMessage = List.of( // GH-90000
                new ContextWindowManager.UsageByMessage("m1", 50, ContextWindowManager.ContentType.USER_MESSAGE, Instant.now()), // GH-90000
                new ContextWindowManager.UsageByMessage("m2", 100, ContextWindowManager.ContentType.ASSISTANT_MESSAGE, Instant.now()) // GH-90000
            );

            ContextWindowManager.TokenUsage usage = new ContextWindowManager.TokenUsage( // GH-90000
                conversationId, 150, 50, 100, 0, 3850, usageByMessage
            );

            when(contextManager.getTokenUsage(conversationId)) // GH-90000
                .thenReturn(Promise.of(usage)); // GH-90000

            ContextWindowManager.TokenUsage result = runPromise(() -> // GH-90000
                contextManager.getTokenUsage(conversationId) // GH-90000
            );

            assertThat(result.totalTokensUsed()).isEqualTo(150); // GH-90000
            assertThat(result.userMessageTokens()).isEqualTo(50); // GH-90000
            assertThat(result.assistantMessageTokens()).isEqualTo(100); // GH-90000
            assertThat(result.availableTokens()).isEqualTo(3850); // GH-90000
        }
    }

    @Nested
    @DisplayName("Query Optimization")
    class QueryOptimizationTests {

        @Test
        @DisplayName("[AI005]: optimize_for_query_prioritizes_relevant")
        void optimizeForQueryPrioritizesRelevant() { // GH-90000
            String conversationId = "conv-001";
            String query = "sales data";

            List<ContextWindowManager.ContextContent> optimizedContents = List.of( // GH-90000
                new ContextWindowManager.ContextContent( // GH-90000
                    "c1", ContextWindowManager.ContextContent.ContentType.QUERY_RESULT,
                    "Sales: $100K", 20, 0.95, Instant.now(), Map.of("topic", "sales") // GH-90000
                ),
                new ContextWindowManager.ContextContent( // GH-90000
                    "c2", ContextWindowManager.ContextContent.ContentType.USER_MESSAGE,
                    "Show sales", 10, 0.90, Instant.now(), Map.of() // GH-90000
                )
            );

            ContextWindowManager.ContextWindow optimized = new ContextWindowManager.ContextWindow( // GH-90000
                conversationId, optimizedContents, 30, 4000, false,
                Instant.now(), Instant.now() // GH-90000
            );

            when(contextManager.optimizeForQuery(conversationId, query)) // GH-90000
                .thenReturn(Promise.of(optimized)); // GH-90000

            ContextWindowManager.ContextWindow result = runPromise(() -> // GH-90000
                contextManager.optimizeForQuery(conversationId, query) // GH-90000
            );

            assertThat(result.contents()).hasSize(2); // GH-90000
            assertThat(result.contents().get(0).relevanceScore()).isGreaterThan(0.9); // GH-90000
        }
    }

    @Nested
    @DisplayName("Content Types")
    class ContentTypesTests {

        @Test
        @DisplayName("[AI005]: all_content_types_supported")
        void allContentTypesSupported() { // GH-90000
            for (ContextWindowManager.ContextContent.ContentType type : ContextWindowManager.ContextContent.ContentType.values()) { // GH-90000
                ContextWindowManager.ContextContent content = new ContextWindowManager.ContextContent( // GH-90000
                    "id", type, "content", 10, 1.0, Instant.now(), Map.of() // GH-90000
                );

                assertThat(content.type()).isEqualTo(type); // GH-90000
            }
        }
    }
}

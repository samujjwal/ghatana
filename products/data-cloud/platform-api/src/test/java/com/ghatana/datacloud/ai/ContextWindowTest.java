/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Tests for context window management (AI005).
 *
 * @doc.type class
 * @doc.purpose Context window management tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContextWindow – Context Management (AI005)")
class ContextWindowTest extends EventloopTestBase {

    @Mock
    private ContextWindowManager contextManager;

    @Nested
    @DisplayName("Context Window Retrieval")
    class ContextWindowRetrievalTests {

        @Test
        @DisplayName("[AI005]: get_context_window_returns_window")
        void getContextWindowReturnsWindow() {
            String conversationId = "conv-001";

            List<ContextWindowManager.ContextContent> contents = List.of(
                new ContextWindowManager.ContextContent(
                    "c1", ContextWindowManager.ContentType.USER_MESSAGE,
                    "Hello", 5, 1.0, Instant.now(), Map.of()
                ),
                new ContextWindowManager.ContextContent(
                    "c2", ContextWindowManager.ContentType.ASSISTANT_MESSAGE,
                    "Hi!", 5, 1.0, Instant.now(), Map.of()
                )
            );

            ContextWindowManager.ContextWindow window = new ContextWindowManager.ContextWindow(
                conversationId, contents, 10, 4000, false,
                Instant.now(), Instant.now()
            );

            when(contextManager.getContextWindow(conversationId))
                .thenReturn(Promise.of(window));

            ContextWindowManager.ContextWindow result = runPromise(() ->
                contextManager.getContextWindow(conversationId)
            );

            assertThat(result.conversationId()).isEqualTo(conversationId);
            assertThat(result.contents()).hasSize(2);
            assertThat(result.totalTokens()).isEqualTo(10);
            assertThat(result.isWithinLimits()).isTrue();
        }
    }

    @Nested
    @DisplayName("Content Management")
    class ContentManagementTests {

        @Test
        @DisplayName("[AI005]: add_content_increases_window")
        void addContentIncreasesWindow() {
            String conversationId = "conv-001";
            ContextWindowManager.ContextContent content = new ContextWindowManager.ContextContent(
                "c3", ContextWindowManager.ContentType.USER_MESSAGE,
                "New message", 8, 1.0, Instant.now(), Map.of()
            );

            List<ContextWindowManager.ContextContent> contents = List.of(
                new ContextWindowManager.ContextContent(
                    "c1", ContextWindowManager.ContentType.USER_MESSAGE, "Hello", 5, 1.0, Instant.now(), Map.of()
                ),
                content
            );

            ContextWindowManager.ContextWindow updated = new ContextWindowManager.ContextWindow(
                conversationId, contents, 13, 4000, false, Instant.now(), Instant.now()
            );

            when(contextManager.addContent(conversationId, content))
                .thenReturn(Promise.of(updated));

            ContextWindowManager.ContextWindow result = runPromise(() ->
                contextManager.addContent(conversationId, content)
            );

            assertThat(result.totalTokens()).isEqualTo(13);
        }

        @Test
        @DisplayName("[AI005]: clear_removes_all_content")
        void clearRemovesAllContent() {
            String conversationId = "conv-001";

            when(contextManager.clear(conversationId))
                .thenReturn(Promise.of((Void) null));

            runPromise(() -> contextManager.clear(conversationId));

            verify(contextManager).clear(conversationId);
        }
    }

    @Nested
    @DisplayName("Context Trimming")
    class ContextTrimmingTests {

        @Test
        @DisplayName("[AI005]: trim_to_fit_reduces_tokens")
        void trimToFitReducesTokens() {
            String conversationId = "conv-large";
            int maxTokens = 1000;

            List<ContextWindowManager.ContextContent> trimmedContents = List.of(
                new ContextWindowManager.ContextContent(
                    "c1", ContextWindowManager.ContentType.USER_MESSAGE, "Recent", 50, 1.0, Instant.now(), Map.of()
                )
            );

            ContextWindowManager.ContextWindow trimmed = new ContextWindowManager.ContextWindow(
                conversationId, trimmedContents, 50, maxTokens, true,
                Instant.now(), Instant.now()
            );

            when(contextManager.trimToFit(conversationId, maxTokens))
                .thenReturn(Promise.of(trimmed));

            ContextWindowManager.ContextWindow result = runPromise(() ->
                contextManager.trimToFit(conversationId, maxTokens)
            );

            assertThat(result.totalTokens()).isEqualTo(50);
            assertThat(result.isTruncated()).isTrue();
            assertThat(result.isWithinLimits()).isTrue();
        }

        @Test
        @DisplayName("[AI005]: remaining_tokens_calculated_correctly")
        void remainingTokensCalculatedCorrectly() {
            ContextWindowManager.ContextWindow window = new ContextWindowManager.ContextWindow(
                "conv-001", List.of(), 100, 4000, false, Instant.now(), Instant.now()
            );

            assertThat(window.getRemainingTokens()).isEqualTo(3900);
            assertThat(window.isWithinLimits()).isTrue();
        }

        @Test
        @DisplayName("[AI005]: over_limit_detected")
        void overLimitDetected() {
            ContextWindowManager.ContextWindow window = new ContextWindowManager.ContextWindow(
                "conv-001", List.of(), 4500, 4000, true, Instant.now(), Instant.now()
            );

            assertThat(window.isWithinLimits()).isFalse();
            assertThat(window.getRemainingTokens()).isZero();
        }
    }

    @Nested
    @DisplayName("Context Summarization")
    class ContextSummarizationTests {

        @Test
        @DisplayName("[AI005]: summarize_compresses_context")
        void summarizeCompressesContext() {
            String conversationId = "conv-001";

            ContextWindowManager.ContextSummary summary = new ContextWindowManager.ContextSummary(
                conversationId,
                "User asked about sales data and received results showing Q1 performance.",
                500, 100, 0.2,
                List.of("Sales query", "Q1 results", "Positive growth"),
                Instant.now()
            );

            when(contextManager.summarize(conversationId))
                .thenReturn(Promise.of(summary));

            ContextWindowManager.ContextSummary result = runPromise(() ->
                contextManager.summarize(conversationId)
            );

            assertThat(result.originalTokens()).isEqualTo(500);
            assertThat(result.summaryTokens()).isEqualTo(100);
            assertThat(result.compressionRatio()).isEqualTo(0.2);
            assertThat(result.keyPoints()).hasSize(3);
        }

        @Test
        @DisplayName("[AI005]: compression_ratio_calculated")
        void compressionRatioCalculated() {
            ContextWindowManager.ContextSummary summary = new ContextWindowManager.ContextSummary(
                "conv-001", "Summary", 1000, 250, 0.25, List.of(), Instant.now()
            );

            assertThat(summary.getCompressionRatio()).isEqualTo(0.25);
        }
    }

    @Nested
    @DisplayName("Token Usage")
    class TokenUsageTests {

        @Test
        @DisplayName("[AI005]: get_token_usage_returns_stats")
        void getTokenUsageReturnsStats() {
            String conversationId = "conv-001";

            List<ContextWindowManager.UsageByMessage> usageByMessage = List.of(
                new ContextWindowManager.UsageByMessage("m1", 50, ContextWindowManager.ContentType.USER_MESSAGE, Instant.now()),
                new ContextWindowManager.UsageByMessage("m2", 100, ContextWindowManager.ContentType.ASSISTANT_MESSAGE, Instant.now())
            );

            ContextWindowManager.TokenUsage usage = new ContextWindowManager.TokenUsage(
                conversationId, 150, 50, 100, 0, 3850, usageByMessage
            );

            when(contextManager.getTokenUsage(conversationId))
                .thenReturn(Promise.of(usage));

            ContextWindowManager.TokenUsage result = runPromise(() ->
                contextManager.getTokenUsage(conversationId)
            );

            assertThat(result.totalTokensUsed()).isEqualTo(150);
            assertThat(result.userMessageTokens()).isEqualTo(50);
            assertThat(result.assistantMessageTokens()).isEqualTo(100);
            assertThat(result.availableTokens()).isEqualTo(3850);
        }
    }

    @Nested
    @DisplayName("Query Optimization")
    class QueryOptimizationTests {

        @Test
        @DisplayName("[AI005]: optimize_for_query_prioritizes_relevant")
        void optimizeForQueryPrioritizesRelevant() {
            String conversationId = "conv-001";
            String query = "sales data";

            List<ContextWindowManager.ContextContent> optimizedContents = List.of(
                new ContextWindowManager.ContextContent(
                    "c1", ContextWindowManager.ContentType.QUERY_RESULT,
                    "Sales: $100K", 20, 0.95, Instant.now(), Map.of("topic", "sales")
                ),
                new ContextWindowManager.ContextContent(
                    "c2", ContextWindowManager.ContentType.USER_MESSAGE,
                    "Show sales", 10, 0.90, Instant.now(), Map.of()
                )
            );

            ContextWindowManager.ContextWindow optimized = new ContextWindowManager.ContextWindow(
                conversationId, optimizedContents, 30, 4000, false,
                Instant.now(), Instant.now()
            );

            when(contextManager.optimizeForQuery(conversationId, query))
                .thenReturn(Promise.of(optimized));

            ContextWindowManager.ContextWindow result = runPromise(() ->
                contextManager.optimizeForQuery(conversationId, query)
            );

            assertThat(result.contents()).hasSize(2);
            assertThat(result.contents().get(0).relevanceScore()).isGreaterThan(0.9);
        }
    }

    @Nested
    @DisplayName("Content Types")
    class ContentTypesTests {

        @Test
        @DisplayName("[AI005]: all_content_types_supported")
        void allContentTypesSupported() {
            for (ContextWindowManager.ContentType type : ContextWindowManager.ContentType.values()) {
                ContextWindowManager.ContextContent content = new ContextWindowManager.ContextContent(
                    "id", type, "content", 10, 1.0, Instant.now(), Map.of()
                );

                assertThat(content.type()).isEqualTo(type);
            }
        }
    }
}

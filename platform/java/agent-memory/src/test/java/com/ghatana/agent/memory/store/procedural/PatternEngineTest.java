/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.memory.store.procedural;

import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InMemoryPatternEngine}.
 */
@DisplayName("PatternEngine")
class PatternEngineTest extends EventloopTestBase {

    private InMemoryPatternEngine engine;

    @BeforeEach
    void setUp() {
        engine = new InMemoryPatternEngine(0.7, 0.5);
    }

    private EnhancedProcedure createProcedure(String id, String situation, double confidence,
                                               Map<String, String> labels) {
        return EnhancedProcedure.builder()
                .id(id)
                .situation(situation)
                .action("Execute procedure " + id)
                .confidence(confidence)
                .labels(labels)
                .tenantId("test")
                .build();
    }

    @Nested
    @DisplayName("Indexing")
    class IndexingTests {

        @Test
        @DisplayName("should index high-confidence procedures")
        void shouldIndexHighConfidence() {
            engine.index(createProcedure("p1", "handle payment failure",
                    0.85, Map.of("domain", "payments")));

            PatternEngine.IndexStats stats = engine.getStats();
            assertThat(stats.indexedProcedures()).isEqualTo(1);
            assertThat(stats.indexedKeywords()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should skip low-confidence procedures")
        void shouldSkipLowConfidence() {
            engine.index(createProcedure("p1", "handle payment failure",
                    0.3, Map.of()));

            PatternEngine.IndexStats stats = engine.getStats();
            assertThat(stats.indexedProcedures()).isZero();
        }

        @Test
        @DisplayName("should deindex procedures")
        void shouldDeindex() {
            engine.index(createProcedure("p1", "handle payment failure",
                    0.85, Map.of()));

            engine.deindex("p1");

            PatternEngine.IndexStats stats = engine.getStats();
            assertThat(stats.indexedProcedures()).isZero();
        }

        @Test
        @DisplayName("should rebuild index from procedure list")
        void shouldRebuildIndex() {
            List<EnhancedProcedure> procs = List.of(
                    createProcedure("p1", "handle payment failure", 0.9, Map.of()),
                    createProcedure("p2", "retry network timeout", 0.8, Map.of()),
                    createProcedure("p3", "low confidence procedure", 0.3, Map.of())
            );

            runPromise(() -> engine.rebuild(procs));

            PatternEngine.IndexStats stats = engine.getStats();
            assertThat(stats.indexedProcedures()).isEqualTo(2); // p3 excluded (low confidence)
        }
    }

    @Nested
    @DisplayName("Matching")
    class MatchingTests {

        @Test
        @DisplayName("should return exact match when all keywords match")
        void shouldReturnExactMatch() {
            engine.index(createProcedure("p1", "handle payment failure",
                    0.9, Map.of()));

            Optional<PatternEngine.MatchResult> result =
                    engine.match("handle payment failure", Map.of());

            assertThat(result).isPresent();
            assertThat(result.get().matchType()).isEqualTo(PatternEngine.MatchType.EXACT);
            assertThat(result.get().matchScore()).isEqualTo(1.0);
            assertThat(result.get().procedure().getId()).isEqualTo("p1");
        }

        @Test
        @DisplayName("should match by labels when keywords don't match")
        void shouldMatchByLabels() {
            engine.index(createProcedure("p1", "process refund",
                    0.9, Map.of("domain", "payments", "action", "refund")));

            Optional<PatternEngine.MatchResult> result =
                    engine.match("something completely different",
                            Map.of("domain", "payments", "action", "refund"));

            assertThat(result).isPresent();
            assertThat(result.get().matchType()).isEqualTo(PatternEngine.MatchType.LABEL);
        }

        @Test
        @DisplayName("should return partial match above threshold")
        void shouldReturnPartialMatch() {
            engine.index(createProcedure("p1",
                    "handle payment failure retry notification",
                    0.9, Map.of()));

            // 3 out of 5 keywords match (handle, payment, failure) → 60% overlap
            Optional<PatternEngine.MatchResult> result =
                    engine.match("handle payment failure", Map.of());

            assertThat(result).isPresent();
            assertThat(result.get().matchScore()).isGreaterThanOrEqualTo(0.5);
        }

        @Test
        @DisplayName("should return empty when no match found")
        void shouldReturnEmptyWhenNoMatch() {
            engine.index(createProcedure("p1", "handle payment failure",
                    0.9, Map.of()));

            Optional<PatternEngine.MatchResult> result =
                    engine.match("deploy kubernetes cluster", Map.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should prefer exact match over label match")
        void shouldPreferExactOverLabel() {
            engine.index(createProcedure("p1", "handle payment failure",
                    0.9, Map.of("domain", "payments")));
            engine.index(createProcedure("p2", "some other procedure",
                    0.9, Map.of("domain", "payments")));

            Optional<PatternEngine.MatchResult> result =
                    engine.match("handle payment failure",
                            Map.of("domain", "payments"));

            assertThat(result).isPresent();
            assertThat(result.get().matchType()).isEqualTo(PatternEngine.MatchType.EXACT);
            assertThat(result.get().procedure().getId()).isEqualTo("p1");
        }
    }

    @Nested
    @DisplayName("Tokenization")
    class TokenizationTests {

        @Test
        @DisplayName("should normalize to lowercase and remove stopwords")
        void shouldNormalizeAndRemoveStopwords() {
            var tokens = InMemoryPatternEngine.tokenize(
                    "Handle the Payment Failure for Customer");

            assertThat(tokens).contains("handle", "payment", "failure", "customer");
            assertThat(tokens).doesNotContain("the", "for");
        }

        @Test
        @DisplayName("should strip punctuation")
        void shouldStripPunctuation() {
            var tokens = InMemoryPatternEngine.tokenize(
                    "error: timeout! retry? (yes)");

            assertThat(tokens).contains("error", "timeout", "retry", "yes");
        }

        @Test
        @DisplayName("should filter short words")
        void shouldFilterShortWords() {
            var tokens = InMemoryPatternEngine.tokenize("a is an of to do");
            assertThat(tokens).isEmpty();
        }
    }
}

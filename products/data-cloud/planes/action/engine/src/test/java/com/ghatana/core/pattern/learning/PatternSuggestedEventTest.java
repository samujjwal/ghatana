package com.ghatana.core.pattern.learning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PatternSuggestedEvent.
 */
@DisplayName("PatternSuggestedEvent Tests (DC-P10-002)")
class PatternSuggestedEventTest {

    @Nested
    @DisplayName("Learning Does Not Mutate Active Rules")
    class LearningMutationTests {

        @Test
        @DisplayName("validates that suggestion does not mutate active rules")
        void validatesSuggestionDoesNotMutateActiveRules() {
            PatternSuggestedEvent event = new PatternSuggestedEvent(
                "suggestion-1",
                "pattern-1",
                "tenant-a",
                "pattern-spec-json",
                0.85,
                Map.of("source", "learning", "correlation", 0.9),
                "learning");

            assertThat(event.isSuggestionOnly()).isTrue();
        }

        @Test
        @DisplayName("rejects events that indicate direct mutation")
        void rejectsEventsThatIndicateDirectMutation() {
            PatternSuggestedEvent event = new PatternSuggestedEvent(
                "suggestion-1",
                "pattern-1",
                "tenant-a",
                "pattern-spec-json",
                0.85,
                Map.of("directMutation", "true"),
                "learning");

            assertThat(event.isSuggestionOnly()).isFalse();
        }

        @Test
        @DisplayName("rejects events that indicate active rule modification")
        void rejectsEventsThatIndicateActiveRuleModification() {
            PatternSuggestedEvent event = new PatternSuggestedEvent(
                "suggestion-1",
                "pattern-1",
                "tenant-a",
                "pattern-spec-json",
                0.85,
                Map.of("activeRuleModified", "true"),
                "learning");

            assertThat(event.isSuggestionOnly()).isFalse();
        }
    }

    @Nested
    @DisplayName("Pattern Suggestions Are Typed Events")
    class TypedEventTests {

        @Test
        @DisplayName("pattern suggestion is a typed event with required fields")
        void patternSuggestionIsTypedEventWithRequiredFields() {
            PatternSuggestedEvent event = new PatternSuggestedEvent(
                "suggestion-1",
                "pattern-1",
                "tenant-a",
                "pattern-spec-json",
                0.85,
                Map.of("source", "learning"),
                "learning");

            assertThat(event.suggestionId()).isEqualTo("suggestion-1");
            assertThat(event.patternId()).isEqualTo("pattern-1");
            assertThat(event.tenantId()).isEqualTo("tenant-a");
            assertThat(event.candidatePatternSpec()).isEqualTo("pattern-spec-json");
            assertThat(event.confidenceScore()).isEqualTo(0.85);
            assertThat(event.suggestedBy()).isEqualTo("learning");
            assertThat(event.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("evidence is immutable")
        void evidenceIsImmutable() {
            Map<String, Object> evidence = Map.of("source", "learning");
            PatternSuggestedEvent event = new PatternSuggestedEvent(
                "suggestion-1",
                "pattern-1",
                "tenant-a",
                "pattern-spec-json",
                0.85,
                evidence,
                "learning");

            // Evidence should be a copy, not the original map
            assertThat(event.evidence()).isNotSameAs(evidence);
        }
    }

    @Nested
    @DisplayName("Expert/Human/Agent Review Is Auditable")
    class AuditableReviewTests {

        @Test
        @DisplayName("tracks who suggested the pattern")
        void tracksWhoSuggestedThePattern() {
            PatternSuggestedEvent humanSuggestion = new PatternSuggestedEvent(
                "suggestion-1",
                "pattern-1",
                "tenant-a",
                "pattern-spec-json",
                0.85,
                Map.of("reviewerId", "user-123"),
                "human");

            PatternSuggestedEvent agentSuggestion = new PatternSuggestedEvent(
                "suggestion-2",
                "pattern-2",
                "tenant-a",
                "pattern-spec-json",
                0.90,
                Map.of("agentId", "agent-456"),
                "agent");

            assertThat(humanSuggestion.suggestedBy()).isEqualTo("human");
            assertThat(agentSuggestion.suggestedBy()).isEqualTo("agent");
        }

        @Test
        @DisplayName("includes evidence for audit trail")
        void includesEvidenceForAuditTrail() {
            PatternSuggestedEvent event = new PatternSuggestedEvent(
                "suggestion-1",
                "pattern-1",
                "tenant-a",
                "pattern-spec-json",
                0.85,
                Map.of(
                    "reviewerId", "user-123",
                    "reviewTimestamp", "2026-05-24T00:00:00Z",
                    "reviewNotes", "High confidence pattern"),
                "human");

            assertThat(event.evidence()).containsKey("reviewerId");
            assertThat(event.evidence()).containsKey("reviewTimestamp");
            assertThat(event.evidence()).containsKey("reviewNotes");
        }
    }

    @Nested
    @DisplayName("Shadow Metrics Storage")
    class ShadowMetricsTests {

        @Test
        @DisplayName("stores false-positive and false-negative metrics in evidence")
        void storesFalsePositiveAndFalseNegativeMetricsInEvidence() {
            PatternSuggestedEvent event = new PatternSuggestedEvent(
                "suggestion-1",
                "pattern-1",
                "tenant-a",
                "pattern-spec-json",
                0.85,
                Map.of(
                    "falsePositiveRate", 0.05,
                    "falseNegativeRate", 0.12,
                    "precision", 0.88,
                    "recall", 0.85),
                "learning");

            assertThat(event.evidence()).containsKey("falsePositiveRate");
            assertThat(event.evidence()).containsKey("falseNegativeRate");
            assertThat(event.evidence()).containsKey("precision");
            assertThat(event.evidence()).containsKey("recall");
        }

        @Test
        @DisplayName("includes confidence score for shadow evaluation")
        void includesConfidenceScoreForShadowEvaluation() {
            PatternSuggestedEvent event = new PatternSuggestedEvent(
                "suggestion-1",
                "pattern-1",
                "tenant-a",
                "pattern-spec-json",
                0.92,
                Map.of("shadowEvaluation", "completed"),
                "learning");

            assertThat(event.confidenceScore()).isEqualTo(0.92);
            assertThat(event.evidence()).containsKey("shadowEvaluation");
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("requires non-null suggestionId")
        void requiresNonNullSuggestionId() {
            org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> new PatternSuggestedEvent(
                    null, "pattern-1", "tenant-a", "spec", 0.85, Map.of(), "learning"))
                .withMessageContaining("suggestionId");
        }

        @Test
        @DisplayName("requires non-null patternId")
        void requiresNonNullPatternId() {
            org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> new PatternSuggestedEvent(
                    "suggestion-1", null, "tenant-a", "spec", 0.85, Map.of(), "learning"))
                .withMessageContaining("patternId");
        }

        @Test
        @DisplayName("requires non-null tenantId")
        void requiresNonNullTenantId() {
            org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> new PatternSuggestedEvent(
                    "suggestion-1", "pattern-1", null, "spec", 0.85, Map.of(), "learning"))
                .withMessageContaining("tenantId");
        }

        @Test
        @DisplayName("requires non-null candidatePatternSpec")
        void requiresNonNullCandidatePatternSpec() {
            org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> new PatternSuggestedEvent(
                    "suggestion-1", "pattern-1", "tenant-a", null, 0.85, Map.of(), "learning"))
                .withMessageContaining("candidatePatternSpec");
        }

        @Test
        @DisplayName("requires non-null evidence")
        void requiresNonNullEvidence() {
            org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> new PatternSuggestedEvent(
                    "suggestion-1", "pattern-1", "tenant-a", "spec", 0.85, null, "learning"))
                .withMessageContaining("evidence");
        }

        @Test
        @DisplayName("requires non-null suggestedBy")
        void requiresNonNullSuggestedBy() {
            org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> new PatternSuggestedEvent(
                    "suggestion-1", "pattern-1", "tenant-a", "spec", 0.85, Map.of(), null))
                .withMessageContaining("suggestedBy");
        }
    }
}

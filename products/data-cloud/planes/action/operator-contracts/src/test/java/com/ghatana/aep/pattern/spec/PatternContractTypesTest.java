/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern.spec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for pattern contract types (P4-03).
 *
 * <p>P4-03: Validates the new pattern contract types including:
 * <ul>
 *   <li>PatternRuntimePlan</li>
 *   <li>PatternExplainability</li>
 *   <li>LearningFeedback</li>
 *   <li>RecommendationCandidate</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Tests for pattern contract types validation and compilation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Pattern Contract Types Tests")
class PatternContractTypesTest {

    // ==================== PatternRuntimePlan Tests ====================

    @Test
    @DisplayName("PatternRuntimePlan should validate required fields")
    void patternRuntimePlanShouldValidateRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> 
            new PatternRuntimePlan("", "plan-id", null, null, null, null, Map.of())
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            new PatternRuntimePlan("pattern-id", "", null, null, null, null, Map.of())
        );
    }

    @Test
    @DisplayName("PatternRuntimePlan should provide default values for optional fields")
    void patternRuntimePlanShouldProvideDefaults() {
        PatternRuntimePlan plan = new PatternRuntimePlan(
            "pattern-id",
            "plan-id",
            null,
            null,
            null,
            null,
            Map.of()
        );

        assertEquals(PatternLifecycleState.DRAFT, plan.lifecycleState());
        assertEquals(0, plan.dagStructure().nodeCount());
        assertEquals(0, plan.dagStructure().edgeCount());
        assertFalse(plan.resourceAllocation().maxMemoryMb().isPresent());
        assertFalse(plan.executionConstraints().requiresApproval());
    }

    @Test
    @DisplayName("PatternRuntimePlan should check executability")
    void patternRuntimePlanShouldCheckExecutability() {
        PatternRuntimePlan activePlan = new PatternRuntimePlan(
            "pattern-id",
            "plan-id",
            PatternLifecycleState.ACTIVE,
            null,
            null,
            null,
            Map.of()
        );
        assertTrue(activePlan.isExecutable());

        PatternRuntimePlan draftPlan = new PatternRuntimePlan(
            "pattern-id",
            "plan-id",
            PatternLifecycleState.DRAFT,
            null,
            null,
            null,
            Map.of()
        );
        assertFalse(draftPlan.isExecutable());
    }

    // ==================== PatternExplainability Tests ====================

    @Test
    @DisplayName("PatternExplainability should validate required fields")
    void patternExplainabilityShouldValidateRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> 
            new PatternExplainability("", List.of(), Map.of(), List.of(), Optional.empty(), List.of(), List.of())
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            new PatternExplainability(null, List.of(), Map.of(), List.of(), Optional.empty(), List.of(), List.of())
        );
    }

    @Test
    @DisplayName("PatternExplainability should create empty instance")
    void patternExplainabilityShouldCreateEmpty() {
        PatternExplainability empty = PatternExplainability.empty();
        assertEquals("No explanation available", empty.summary());
        assertTrue(empty.executionSteps().isEmpty());
        assertTrue(empty.warnings().isEmpty());
        assertFalse(empty.hasWarnings());
    }

    @Test
    @DisplayName("PatternExplainability builder should construct valid instance")
    void patternExplainabilityBuilderShouldConstruct() {
        PatternExplainability explainability = PatternExplainability.builder()
            .summary("Test pattern")
            .addExecutionStep("Step 1")
            .addExecutionStep("Step 2")
            .addDataFlow("input", "data")
            .addWarning("Warning message")
            .executionEstimate("100ms")
            .addRequiredCapability("capability-1")
            .addSideEffect("side-effect-1")
            .build();

        assertEquals("Test pattern", explainability.summary());
        assertEquals(2, explainability.executionSteps().size());
        assertTrue(explainability.hasWarnings());
        assertTrue(explainability.hasSideEffects());
        assertTrue(explainability.requiresCapabilities());
    }

    // ==================== LearningFeedback Tests ====================

    @Test
    @DisplayName("LearningFeedback should validate required fields")
    void learningFeedbackShouldValidateRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> 
            new LearningFeedback("", "exec-id", Instant.now(), LearningFeedback.FeedbackType.GENERAL, 0.5, Map.of(), List.of(), Optional.empty(), Optional.empty())
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            new LearningFeedback("pattern-id", "", Instant.now(), LearningFeedback.FeedbackType.GENERAL, 0.5, Map.of(), List.of(), Optional.empty(), Optional.empty())
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            new LearningFeedback("pattern-id", "exec-id", Instant.now(), LearningFeedback.FeedbackType.GENERAL, 1.5, Map.of(), List.of(), Optional.empty(), Optional.empty())
        );
    }

    @Test
    @DisplayName("LearningFeedback should check high confidence")
    void learningFeedbackShouldCheckHighConfidence() {
        LearningFeedback highConfidence = LearningFeedback.of(
            "pattern-id", "exec-id", LearningFeedback.FeedbackType.ACCURACY, 0.9
        );
        assertTrue(highConfidence.isHighConfidence());

        LearningFeedback lowConfidence = LearningFeedback.of(
            "pattern-id", "exec-id", LearningFeedback.FeedbackType.ACCURACY, 0.5
        );
        assertFalse(lowConfidence.isHighConfidence());
    }

    @Test
    @DisplayName("LearningFeedback builder should construct valid instance")
    void learningFeedbackBuilderShouldConstruct() {
        LearningFeedback feedback = LearningFeedback.builder()
            .patternId("pattern-id")
            .executionId("exec-id")
            .feedbackType(LearningFeedback.FeedbackType.PERFORMANCE)
            .confidence(0.8)
            .addMetric("latency", 100)
            .addMetric("throughput", 1000)
            .addSuggestion("Optimize query")
            .rootCauseAnalysis("Slow database")
            .improvementRecommendation("Add index")
            .build();

        assertEquals("pattern-id", feedback.patternId());
        assertEquals(LearningFeedback.FeedbackType.PERFORMANCE, feedback.feedbackType());
        assertEquals(0.8, feedback.confidence());
        assertTrue(feedback.hasSuggestions());
        assertTrue(feedback.hasImprovementRecommendation());
    }

    // ==================== RecommendationCandidate Tests ====================

    @Test
    @DisplayName("RecommendationCandidate should validate required fields")
    void recommendationCandidateShouldValidateRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> 
            new RecommendationCandidate("", "pattern-id", RecommendationCandidate.RecommendationType.PERFORMANCE_OPTIMIZATION, "title", "desc", 0.5, List.of(), Map.of(), false, false, Optional.empty())
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            new RecommendationCandidate("rec-id", "", RecommendationCandidate.RecommendationType.PERFORMANCE_OPTIMIZATION, "title", "desc", 0.5, List.of(), Map.of(), false, false, Optional.empty())
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            new RecommendationCandidate("rec-id", "pattern-id", RecommendationCandidate.RecommendationType.PERFORMANCE_OPTIMIZATION, "", "desc", 0.5, List.of(), Map.of(), false, false, Optional.empty())
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            new RecommendationCandidate("rec-id", "pattern-id", RecommendationCandidate.RecommendationType.PERFORMANCE_OPTIMIZATION, "title", "", 0.5, List.of(), Map.of(), false, false, Optional.empty())
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            new RecommendationCandidate("rec-id", "pattern-id", RecommendationCandidate.RecommendationType.PERFORMANCE_OPTIMIZATION, "title", "desc", 1.5, List.of(), Map.of(), false, false, Optional.empty())
        );
    }

    @Test
    @DisplayName("RecommendationCandidate should check high impact")
    void recommendationCandidateShouldCheckHighImpact() {
        RecommendationCandidate highImpact = RecommendationCandidate.of(
            "rec-id", "pattern-id", RecommendationCandidate.RecommendationType.PERFORMANCE_OPTIMIZATION, "title", "desc", 0.7
        );
        assertTrue(highImpact.isHighImpact());

        RecommendationCandidate lowImpact = RecommendationCandidate.of(
            "rec-id", "pattern-id", RecommendationCandidate.RecommendationType.PERFORMANCE_OPTIMIZATION, "title", "desc", 0.3
        );
        assertFalse(lowImpact.isHighImpact());
    }

    @Test
    @DisplayName("RecommendationCandidate should check auto-apply eligibility")
    void recommendationCandidateShouldCheckAutoApply() {
        RecommendationCandidate autoApply = new RecommendationCandidate(
            "rec-id", "pattern-id", RecommendationCandidate.RecommendationType.PERFORMANCE_OPTIMIZATION, "title", "desc", 0.5, List.of(), Map.of(), false, true, Optional.empty()
        );
        assertTrue(autoApply.canAutoApply());

        RecommendationCandidate requiresReview = new RecommendationCandidate(
            "rec-id", "pattern-id", RecommendationCandidate.RecommendationType.PERFORMANCE_OPTIMIZATION, "title", "desc", 0.5, List.of(), Map.of(), true, false, Optional.empty()
        );
        assertFalse(requiresReview.canAutoApply());
    }

    @Test
    @DisplayName("RecommendationCandidate builder should construct valid instance")
    void recommendationCandidateBuilderShouldConstruct() {
        RecommendationCandidate recommendation = RecommendationCandidate.builder()
            .recommendationId("rec-id")
            .patternId("pattern-id")
            .recommendationType(RecommendationCandidate.RecommendationType.ACCURACY_IMPROVEMENT)
            .title("Improve accuracy")
            .description("Add more training data")
            .expectedImprovement(0.6)
            .addProposedChange("Change parameter X")
            .addProposedChange("Change parameter Y")
            .addImpactAnalysis("latency", -50)
            .requiresHumanReview(true)
            .automaticallyApplicable(false)
            .rollbackPlan("Revert to previous version")
            .build();

        assertEquals("rec-id", recommendation.recommendationId());
        assertEquals(RecommendationCandidate.RecommendationType.ACCURACY_IMPROVEMENT, recommendation.recommendationType());
        assertEquals(2, recommendation.proposedChanges().size());
        assertTrue(recommendation.requiresHumanReview());
        assertTrue(recommendation.hasRollbackPlan());
    }

    // ==================== PatternMatchResult Tests ====================

    @Test
    @DisplayName("PatternMatchResult should create match result")
    void patternMatchResultShouldCreateMatch() {
        PatternMatchResult match = PatternMatchResult.match(0.9, Map.of("key", "value"));
        assertTrue(match.isMatch());
        assertTrue(match.isComplete());
        assertEquals(0.9, match.confidence());
        assertEquals(0.0, match.uncertainty());
    }

    @Test
    @DisplayName("PatternMatchResult should create partial match result")
    void patternMatchResultShouldCreatePartialMatch() {
        PatternMatchResult partial = PatternMatchResult.partial(0.7, 0.3, Map.of("key", "value"));
        assertTrue(partial.isMatch());
        assertFalse(partial.isComplete());
        assertEquals(0.7, partial.confidence());
        assertEquals(0.3, partial.uncertainty());
    }

    @Test
    @DisplayName("PatternMatchResult should create no-match result")
    void patternMatchResultShouldCreateNoMatch() {
        PatternMatchResult noMatch = PatternMatchResult.noMatch();
        assertFalse(noMatch.isMatch());
        assertFalse(noMatch.isComplete());
        assertEquals(0.0, noMatch.confidence());
        assertEquals(1.0, noMatch.uncertainty());
        assertTrue(noMatch.noMatchReason().isPresent());
    }

    @Test
    @DisplayName("PatternMatchResult should create no-match with reason")
    void patternMatchResultShouldCreateNoMatchWithReason() {
        PatternMatchResult noMatch = PatternMatchResult.noMatch("Event type mismatch");
        assertFalse(noMatch.isMatch());
        assertEquals("Event type mismatch", noMatch.noMatchReason().orElse(""));
    }

    @Test
    @DisplayName("PatternMatchResult should convert to map")
    void patternMatchResultShouldConvertToMap() {
        PatternMatchResult match = PatternMatchResult.match(0.9, Map.of("key", "value"));
        Map<String, Object> map = match.toMap();
        
        assertTrue(map.containsKey("isMatch"));
        assertTrue(map.containsKey("isComplete"));
        assertTrue(map.containsKey("confidence"));
        assertTrue(map.containsKey("uncertainty"));
        assertTrue(map.containsKey("matchData"));
        assertTrue(map.containsKey("explanation"));
        assertTrue(map.containsKey("noMatchReason"));
    }
}

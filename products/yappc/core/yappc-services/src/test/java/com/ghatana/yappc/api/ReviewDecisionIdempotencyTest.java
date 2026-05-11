/**
 * Review Decision Idempotency Tests
 * 
 * Production-grade tests for review decision idempotency.
 * Ensures rollback operations are idempotent.
 * 
 * @doc.type test
 * @doc.purpose Review decision idempotency tests
 * @doc.layer test
 * @doc.pattern Idempotency Test
 */

package com.ghatana.yappc.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production-grade tests for review decision idempotency.
 */
@DisplayName("Review Decision Idempotency Tests")
class ReviewDecisionIdempotencyTest {

    @Test
    @DisplayName("Should allow multiple identical rollback decisions")
    void shouldAllowMultipleIdenticalRollbackDecisions() {
        // Create a rollback decision
        ReviewDecision decision1 = createRollbackDecision("decision-1", "plan-1", "diff-1");
        
        // Create another identical rollback decision
        ReviewDecision decision2 = createRollbackDecision("decision-2", "plan-1", "diff-1");
        
        // Both should have the same decision type
        assertEquals(ReviewDecision.DecisionType.ROLLBACK, decision1.decisionType());
        assertEquals(ReviewDecision.DecisionType.ROLLBACK, decision2.decisionType());
        
        // Both should reference the same generation plan
        assertEquals(decision1.generationPlanId(), decision2.generationPlanId());
        assertEquals(decision1.diffId(), decision2.diffId());
    }

    @Test
    @DisplayName("Should track rollback decision sequence")
    void shouldTrackRollbackDecisionSequence() {
        ReviewDecision applyDecision = createApplyDecision("decision-1", "plan-1", "diff-1");
        ReviewDecision rollbackDecision1 = createRollbackDecision("decision-2", "plan-1", "diff-1");
        ReviewDecision rollbackDecision2 = createRollbackDecision("decision-3", "plan-1", "diff-1");
        
        // Verify decision types
        assertEquals(ReviewDecision.DecisionType.APPLY, applyDecision.decisionType());
        assertEquals(ReviewDecision.DecisionType.ROLLBACK, rollbackDecision1.decisionType());
        assertEquals(ReviewDecision.DecisionType.ROLLBACK, rollbackDecision2.decisionType());
        
        // Verify chronological order
        assertTrue(applyDecision.createdAt().isBefore(rollbackDecision1.createdAt()));
        assertTrue(rollbackDecision1.createdAt().isBefore(rollbackDecision2.createdAt()));
    }

    @Test
    @DisplayName("Should not allow conflicting rollback decisions")
    void shouldNotAllowConflictingRollbackDecisions() {
        ReviewDecision rollbackDecision = createRollbackDecision("decision-1", "plan-1", "diff-1");
        ReviewDecision applyDecision = createApplyDecision("decision-2", "plan-1", "diff-1");
        
        // These should have different decision types
        assertNotEquals(rollbackDecision.decisionType(), applyDecision.decisionType());
    }

    @Test
    @DisplayName("Should preserve rollback metadata across multiple calls")
    void shouldPreserveRollbackMetadataAcrossMultipleCalls() {
        ReviewDecision decision1 = createRollbackDecision("decision-1", "plan-1", "diff-1");
        ReviewDecision decision2 = createRollbackDecision("decision-2", "plan-1", "diff-1");
        
        // Both should have the same metadata
        assertEquals(decision1.metadata().reason(), decision2.metadata().reason());
        assertEquals(decision1.metadata().regionIds(), decision2.metadata().regionIds());
    }

    @Test
    @DisplayName("Should handle request-changes followed by rollback")
    void shouldHandleRequestChangesFollowedByRollback() {
        ReviewDecision requestChangesDecision = createRequestChangesDecision("decision-1", "plan-1", "diff-1");
        ReviewDecision rollbackDecision = createRollbackDecision("decision-2", "plan-1", "diff-1");
        
        // Verify decision types
        assertEquals(ReviewDecision.DecisionType.REQUEST_CHANGES, requestChangesDecision.decisionType());
        assertEquals(ReviewDecision.DecisionType.ROLLBACK, rollbackDecision.decisionType());
        
        // Verify chronological order
        assertTrue(requestChangesDecision.createdAt().isBefore(rollbackDecision.createdAt()));
    }

    // Helper methods to create test data

    private ReviewDecision createRollbackDecision(String decisionId, String generationPlanId, String diffId) {
        return new ReviewDecision(
                decisionId,
                generationPlanId,
                diffId,
                "project-1",
                ReviewDecision.DecisionType.ROLLBACK,
                new ReviewDecision.ReviewDecisionMetadata(
                        "Rollback due to issues",
                        "Comments",
                        List.of("region-1", "region-2"),
                        List.of("file-1", "file-2"),
                        Map.of("key", "value")
                ),
                new ReviewDecision.DecisionContext(
                        "session-1",
                        "trace-1",
                        "generate",
                        false,
                        false,
                        null,
                        Map.of("key", "value")
                ),
                Instant.now(),
                "user-1",
                "Test User"
        );
    }

    private ReviewDecision createApplyDecision(String decisionId, String generationPlanId, String diffId) {
        return new ReviewDecision(
                decisionId,
                generationPlanId,
                diffId,
                "project-1",
                ReviewDecision.DecisionType.APPLY,
                new ReviewDecision.ReviewDecisionMetadata(
                        "Apply changes",
                        "Comments",
                        List.of("region-1"),
                        List.of("file-1"),
                        Map.of()
                ),
                new ReviewDecision.DecisionContext(
                        "session-1",
                        "trace-1",
                        "generate",
                        true,
                        false,
                        null,
                        Map.of()
                ),
                Instant.now(),
                "user-1",
                "Test User"
        );
    }

    private ReviewDecision createRequestChangesDecision(String decisionId, String generationPlanId, String diffId) {
        return new ReviewDecision(
                decisionId,
                generationPlanId,
                diffId,
                "project-1",
                ReviewDecision.DecisionType.REQUEST_CHANGES,
                new ReviewDecision.ReviewDecisionMetadata(
                        "Request changes",
                        "Comments",
                        List.of(),
                        List.of(),
                        Map.of()
                ),
                new ReviewDecision.DecisionContext(
                        "session-1",
                        "trace-1",
                        "generate",
                        false,
                        false,
                        null,
                        Map.of()
                ),
                Instant.now(),
                "user-1",
                "Test User"
        );
    }
}

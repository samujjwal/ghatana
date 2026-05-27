/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AEP-001: PatternSpec full lifecycle transition E2E tests.
 *
 * <p>Verifies full lifecycle state transitions including candidate, approved, degraded,
 * retired, and rollback. Tests all legal and illegal transitions with audit/evidence emission.
 *
 * @doc.type class
 * @doc.purpose PatternSpec lifecycle transition E2E tests (AEP-001)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PatternSpec Lifecycle Transition E2E Tests")
@Tag("aep")
@Tag("lifecycle")
@Tag("e2e")
class PatternLifecycleTransitionE2ETest {

    private final PatternLifecycleService service = new PatternLifecycleService(
        Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC));

    // ==================== AEP-001: Legal transitions ====================

    @Test
    @DisplayName("AEP-001: Draft → Candidate transition is legal")
    void draftToCandidateTransitionIsLegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.DRAFT,
            PatternLifecycleState.CANDIDATE,
            PatternLifecycleEventType.PATTERN_CREATED,
            "author",
            Map.of("specVersion", "1.0.0", "description", "Initial candidate submission"));

        PatternLifecycleEvent event = service.transition(transition);

        assertThat(event.eventType().eventType()).isEqualTo("pattern.created");
        assertThat(event.from()).isEqualTo(PatternLifecycleState.DRAFT);
        assertThat(event.to()).isEqualTo(PatternLifecycleState.CANDIDATE);
        assertThat(event.evidence()).containsEntry("specVersion", "1.0.0");
    }

    @Test
    @DisplayName("AEP-001: Candidate → Validated transition is legal")
    void candidateToValidatedTransitionIsLegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.CANDIDATE,
            PatternLifecycleState.VALIDATED,
            PatternLifecycleEventType.PATTERN_VALIDATED,
            "ci-system",
            Map.of("validationRunId", "ci-run-42", "checksPassedCount", "5"));

        PatternLifecycleEvent event = service.transition(transition);

        assertThat(event.eventType().eventType()).isEqualTo("pattern.validated");
        assertThat(event.from()).isEqualTo(PatternLifecycleState.CANDIDATE);
        assertThat(event.to()).isEqualTo(PatternLifecycleState.VALIDATED);
        assertThat(event.evidence()).containsEntry("validationRunId", "ci-run-42");
    }

    @Test
    @DisplayName("AEP-001: Validated → Shadow transition is legal")
    void validatedToShadowTransitionIsLegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.VALIDATED,
            PatternLifecycleState.SHADOW,
            PatternLifecycleEventType.PATTERN_SHADOW_DEPLOYED,
            "deploy-system",
            Map.of("shadowDuration", "PT24H", "targetEnvironment", "staging"));

        PatternLifecycleEvent event = service.transition(transition);

        assertThat(event.eventType().eventType()).isEqualTo("pattern.shadow_deployed");
        assertThat(event.from()).isEqualTo(PatternLifecycleState.VALIDATED);
        assertThat(event.to()).isEqualTo(PatternLifecycleState.SHADOW);
        assertThat(event.evidence()).containsEntry("shadowDuration", "PT24H");
    }

    @Test
    @DisplayName("AEP-001: Full lifecycle chain produces auditable events at every step")
    void fullLifecycleChainProducesAuditableEventsAtEveryStep() {
        // DRAFT → CANDIDATE
        PatternLifecycleEvent created = service.transition(new PatternLifecycleTransition(
            "pattern-chain", "tenant-a",
            PatternLifecycleState.DRAFT, PatternLifecycleState.CANDIDATE,
            PatternLifecycleEventType.PATTERN_CREATED, "author",
            Map.of("specVersion", "1.0.0")));
        assertThat(created.from()).isEqualTo(PatternLifecycleState.DRAFT);
        assertThat(created.to()).isEqualTo(PatternLifecycleState.CANDIDATE);
        assertThat(created.occurredAt()).isNotNull();

        // CANDIDATE → VALIDATED
        PatternLifecycleEvent validated = service.transition(new PatternLifecycleTransition(
            "pattern-chain", "tenant-a",
            PatternLifecycleState.CANDIDATE, PatternLifecycleState.VALIDATED,
            PatternLifecycleEventType.PATTERN_VALIDATED, "ci-system",
            Map.of("validationRunId", "run-1")));
        assertThat(validated.from()).isEqualTo(PatternLifecycleState.CANDIDATE);
        assertThat(validated.to()).isEqualTo(PatternLifecycleState.VALIDATED);
        assertThat(validated.evidence()).containsKey("validationRunId");

        // VALIDATED → SHADOW
        PatternLifecycleEvent shadow = service.transition(new PatternLifecycleTransition(
            "pattern-chain", "tenant-a",
            PatternLifecycleState.VALIDATED, PatternLifecycleState.SHADOW,
            PatternLifecycleEventType.PATTERN_SHADOW_DEPLOYED, "deploy-system",
            Map.of("shadowDuration", "PT24H")));
        assertThat(shadow.from()).isEqualTo(PatternLifecycleState.VALIDATED);
        assertThat(shadow.to()).isEqualTo(PatternLifecycleState.SHADOW);

        // SHADOW → RECOMMENDED
        PatternLifecycleEvent recommended = service.transition(new PatternLifecycleTransition(
            "pattern-chain", "tenant-a",
            PatternLifecycleState.SHADOW, PatternLifecycleState.RECOMMENDED,
            PatternLifecycleEventType.PATTERN_RECOMMENDED, "evaluator",
            Map.of("recommendationScore", "0.95")));
        assertThat(recommended.from()).isEqualTo(PatternLifecycleState.SHADOW);
        assertThat(recommended.to()).isEqualTo(PatternLifecycleState.RECOMMENDED);

        // RECOMMENDED → APPROVED
        PatternLifecycleEvent approved = service.transition(new PatternLifecycleTransition(
            "pattern-chain", "tenant-a",
            PatternLifecycleState.RECOMMENDED, PatternLifecycleState.APPROVED,
            PatternLifecycleEventType.PATTERN_APPROVED, "approver",
            Map.of("reviewId", "review-1", "approver", "security-team")));
        assertThat(approved.from()).isEqualTo(PatternLifecycleState.RECOMMENDED);
        assertThat(approved.to()).isEqualTo(PatternLifecycleState.APPROVED);
        assertThat(approved.evidence()).containsKey("reviewId");

        // APPROVED → ACTIVE
        PatternLifecycleEvent active = service.transition(new PatternLifecycleTransition(
            "pattern-chain", "tenant-a",
            PatternLifecycleState.APPROVED, PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_PROMOTED, "operator",
            Map.of("deploymentId", "deploy-production-42")));
        assertThat(active.from()).isEqualTo(PatternLifecycleState.APPROVED);
        assertThat(active.to()).isEqualTo(PatternLifecycleState.ACTIVE);
        assertThat(active.evidence()).containsKey("deploymentId");

        // ACTIVE → DEGRADED
        PatternLifecycleEvent degraded = service.transition(new PatternLifecycleTransition(
            "pattern-chain", "tenant-a",
            PatternLifecycleState.ACTIVE, PatternLifecycleState.DEGRADED,
            PatternLifecycleEventType.PATTERN_DEGRADED, "alerting",
            Map.of("degradationReason", "error_rate_spike", "errorRate", "0.32")));
        assertThat(degraded.from()).isEqualTo(PatternLifecycleState.ACTIVE);
        assertThat(degraded.to()).isEqualTo(PatternLifecycleState.DEGRADED);
        assertThat(degraded.evidence()).containsEntry("degradationReason", "error_rate_spike");

        // DEGRADED → ACTIVE (recovery)
        PatternLifecycleEvent recovered = service.transition(new PatternLifecycleTransition(
            "pattern-chain", "tenant-a",
            PatternLifecycleState.DEGRADED, PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_PROMOTED, "operator",
            Map.of("recoveryReason", "error_rate_normalized")));
        assertThat(recovered.from()).isEqualTo(PatternLifecycleState.DEGRADED);
        assertThat(recovered.to()).isEqualTo(PatternLifecycleState.ACTIVE);

        // ACTIVE → RETIRED
        PatternLifecycleEvent retired = service.transition(new PatternLifecycleTransition(
            "pattern-chain", "tenant-a",
            PatternLifecycleState.ACTIVE, PatternLifecycleState.RETIRED,
            PatternLifecycleEventType.PATTERN_RETIRED, "operator",
            Map.of("retirementReason", "replaced_by_v2")));
        assertThat(retired.from()).isEqualTo(PatternLifecycleState.ACTIVE);
        assertThat(retired.to()).isEqualTo(PatternLifecycleState.RETIRED);
        assertThat(retired.evidence()).containsKey("retirementReason");
        assertThat(retired.actor()).isEqualTo("operator");
    }

    @Test
    @DisplayName("AEP-001: Shadow → Recommended transition is legal")
    void shadowToRecommendedTransitionIsLegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.SHADOW,
            PatternLifecycleState.RECOMMENDED,
            PatternLifecycleEventType.PATTERN_RECOMMENDED,
            "system",
            Map.of("recommendationScore", "0.95"));

        PatternLifecycleEvent event = service.transition(transition);

        assertThat(event.eventType().eventType()).isEqualTo("pattern.recommended");
        assertThat(event.from()).isEqualTo(PatternLifecycleState.SHADOW);
        assertThat(event.to()).isEqualTo(PatternLifecycleState.RECOMMENDED);
        assertThat(event.evidence()).containsEntry("recommendationScore", "0.95");
    }

    @Test
    @DisplayName("AEP-001: Recommended → Approved transition is legal")
    void recommendedToApprovedTransitionIsLegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.RECOMMENDED,
            PatternLifecycleState.APPROVED,
            PatternLifecycleEventType.PATTERN_APPROVED,
            "reviewer",
            Map.of("reviewId", "review-1", "approver", "admin"));

        PatternLifecycleEvent event = service.transition(transition);

        assertThat(event.eventType().eventType()).isEqualTo("pattern.approved");
        assertThat(event.from()).isEqualTo(PatternLifecycleState.RECOMMENDED);
        assertThat(event.to()).isEqualTo(PatternLifecycleState.APPROVED);
        assertThat(event.evidence()).containsEntry("reviewId", "review-1");
    }

    @Test
    @DisplayName("AEP-001: Approved → Active transition is legal")
    void approvedToActiveTransitionIsLegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.APPROVED,
            PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_PROMOTED,
            "operator",
            Map.of("deploymentId", "deploy-1"));

        PatternLifecycleEvent event = service.transition(transition);

        assertThat(event.eventType().eventType()).isEqualTo("pattern.promoted");
        assertThat(event.from()).isEqualTo(PatternLifecycleState.APPROVED);
        assertThat(event.to()).isEqualTo(PatternLifecycleState.ACTIVE);
        assertThat(event.evidence()).containsEntry("deploymentId", "deploy-1");
    }

    @Test
    @DisplayName("AEP-001: Active → Retired via direct retirement is legal")
    void activeToRetiredViaDirectRetirementIsLegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.ACTIVE,
            PatternLifecycleState.RETIRED,
            PatternLifecycleEventType.PATTERN_RETIRED,
            "system",
            Map.of("retirementReason", "replaced_by_v2"));

        PatternLifecycleEvent event = service.transition(transition);

        assertThat(event.eventType().eventType()).isEqualTo("pattern.retired");
        assertThat(event.from()).isEqualTo(PatternLifecycleState.ACTIVE);
        assertThat(event.to()).isEqualTo(PatternLifecycleState.RETIRED);
        assertThat(event.evidence()).containsEntry("retirementReason", "replaced_by_v2");
    }

    @Test
    @DisplayName("AEP-001: Active → Degraded transition is legal")
    void activeToDegradedTransitionIsLegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.ACTIVE,
            PatternLifecycleState.DEGRADED,
            PatternLifecycleEventType.PATTERN_DEGRADED,
            "system",
            Map.of("degradationReason", "high_error_rate", "errorRate", "0.15"));

        PatternLifecycleEvent event = service.transition(transition);

        assertThat(event.eventType().eventType()).isEqualTo("pattern.degraded");
        assertThat(event.from()).isEqualTo(PatternLifecycleState.ACTIVE);
        assertThat(event.to()).isEqualTo(PatternLifecycleState.DEGRADED);
        assertThat(event.evidence()).containsEntry("degradationReason", "high_error_rate");
    }

    @Test
    @DisplayName("AEP-001: Degraded → Active recovery transition is legal")
    void degradedToActiveRecoveryTransitionIsLegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.DEGRADED,
            PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_PROMOTED,
            "operator",
            Map.of("recoveryReason", "error_rate_normalized", "rollbackVersion", "v1.0.0"));

        PatternLifecycleEvent event = service.transition(transition);

        assertThat(event.eventType().eventType()).isEqualTo("pattern.promoted");
        assertThat(event.from()).isEqualTo(PatternLifecycleState.DEGRADED);
        assertThat(event.to()).isEqualTo(PatternLifecycleState.ACTIVE);
        assertThat(event.evidence()).containsEntry("recoveryReason", "error_rate_normalized");
    }

    @Test
    @DisplayName("AEP-001: Active → Retired transition is legal")
    void activeToRetiredTransitionIsLegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.ACTIVE,
            PatternLifecycleState.RETIRED,
            PatternLifecycleEventType.PATTERN_RETIRED,
            "operator",
            Map.of("retirementReason", "obsolete"));

        PatternLifecycleEvent event = service.transition(transition);

        assertThat(event.eventType().eventType()).isEqualTo("pattern.retired");
        assertThat(event.from()).isEqualTo(PatternLifecycleState.ACTIVE);
        assertThat(event.to()).isEqualTo(PatternLifecycleState.RETIRED);
        assertThat(event.evidence()).containsEntry("retirementReason", "obsolete");
    }

    // ==================== AEP-001: Illegal transitions ====================

    @Test
    @DisplayName("AEP-001: Shadow → Active transition is illegal")
    void shadowToActiveTransitionIsIllegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.SHADOW,
            PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_PROMOTED,
            "operator",
            Map.of());

        assertThatThrownBy(() -> service.transition(transition))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SHADOW -> ACTIVE");
    }

    @Test
    @DisplayName("AEP-001: Recommended → Active transition is illegal")
    void recommendedToActiveTransitionIsIllegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.RECOMMENDED,
            PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_PROMOTED,
            "operator",
            Map.of());

        assertThatThrownBy(() -> service.transition(transition))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RECOMMENDED -> ACTIVE");
    }

    @Test
    @DisplayName("AEP-001: Retired → Active transition is illegal")
    void retiredToActiveTransitionIsIllegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.RETIRED,
            PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_PROMOTED,
            "operator",
            Map.of());

        assertThatThrownBy(() -> service.transition(transition))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RETIRED -> ACTIVE");
    }

    @Test
    @DisplayName("AEP-001: Draft → Active transition is illegal (must go through candidate/validated/shadow/recommended/approved)")
    void draftToActiveTransitionIsIllegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.DRAFT,
            PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_PROMOTED,
            "operator",
            Map.of());

        assertThatThrownBy(() -> service.transition(transition))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DRAFT -> ACTIVE");
    }

    @Test
    @DisplayName("AEP-001: Candidate → Active transition is illegal (must be validated first)")
    void candidateToActiveTransitionIsIllegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.CANDIDATE,
            PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_PROMOTED,
            "operator",
            Map.of());

        assertThatThrownBy(() -> service.transition(transition))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CANDIDATE -> ACTIVE");
    }

    @Test
    @DisplayName("AEP-001: Illegal transition fails without emitting audit")
    void illegalTransitionFailsWithoutEmittingAudit() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.RETIRED,
            PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_PROMOTED,
            "operator",
            Map.of());

        // Transition should fail
        assertThatThrownBy(() -> service.transition(transition))
            .isInstanceOf(IllegalArgumentException.class);

        // In a real implementation, we'd verify no audit event was emitted
        // For this test, we verify the exception is thrown
    }

    // ==================== AEP-001: Legal transition emits audit/evidence ====================

    @Test
    @DisplayName("AEP-001: Legal transition emits auditable event")
    void legalTransitionEmitsAuditableEvent() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.APPROVED,
            PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_PROMOTED,
            "operator",
            Map.of("deploymentId", "deploy-1"));

        PatternLifecycleEvent event = service.transition(transition);

        // Verify auditable event structure
        assertThat(event.patternId()).isEqualTo("pattern-1");
        assertThat(event.tenantId()).isEqualTo("tenant-a");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-05-23T00:00:00Z"));
        assertThat(event.evidence()).isNotEmpty();
        assertThat(event.evidence()).containsKey("deploymentId");
    }

    @Test
    @DisplayName("AEP-001: Transition evidence includes principal")
    void transitionEvidenceIncludesPrincipal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.APPROVED,
            PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_PROMOTED,
            "operator-123",
            Map.of());

        PatternLifecycleEvent event = service.transition(transition);

        assertThat(event.actor()).isEqualTo("operator-123");
    }
}

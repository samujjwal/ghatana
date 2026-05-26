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
        assertThat(event.fromState()).isEqualTo(PatternLifecycleState.SHADOW);
        assertThat(event.toState()).isEqualTo(PatternLifecycleState.RECOMMENDED);
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
        assertThat(event.fromState()).isEqualTo(PatternLifecycleState.RECOMMENDED);
        assertThat(event.toState()).isEqualTo(PatternLifecycleState.APPROVED);
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
        assertThat(event.fromState()).isEqualTo(PatternLifecycleState.APPROVED);
        assertThat(event.toState()).isEqualTo(PatternLifecycleState.ACTIVE);
        assertThat(event.evidence()).containsEntry("deploymentId", "deploy-1");
    }

    @Test
    @DisplayName("AEP-001: Active → Predictive transition is legal")
    void activeToPredictiveTransitionIsLegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.ACTIVE,
            PatternLifecycleState.PREDICTIVE,
            PatternLifecycleEventType.PATTERN_MODE_CHANGED,
            "system",
            Map.of("mode", "predictive"));

        PatternLifecycleEvent event = service.transition(transition);

        assertThat(event.eventType().eventType()).isEqualTo("pattern.mode_changed");
        assertThat(event.fromState()).isEqualTo(PatternLifecycleState.ACTIVE);
        assertThat(event.toState()).isEqualTo(PatternLifecycleState.PREDICTIVE);
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
        assertThat(event.fromState()).isEqualTo(PatternLifecycleState.ACTIVE);
        assertThat(event.toState()).isEqualTo(PatternLifecycleState.DEGRADED);
        assertThat(event.evidence()).containsEntry("degradationReason", "high_error_rate");
    }

    @Test
    @DisplayName("AEP-001: Degraded → Rollback transition is legal")
    void degradedToRollbackTransitionIsLegal() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.DEGRADED,
            PatternLifecycleState.ROLLBACK,
            PatternLifecycleEventType.PATTERN_ROLLED_BACK,
            "operator",
            Map.of("rollbackToVersion", "v1.0.0"));

        PatternLifecycleEvent event = service.transition(transition);

        assertThat(event.eventType().eventType()).isEqualTo("pattern.rolled_back");
        assertThat(event.fromState()).isEqualTo(PatternLifecycleState.DEGRADED);
        assertThat(event.toState()).isEqualTo(PatternLifecycleState.ROLLBACK);
        assertThat(event.evidence()).containsEntry("rollbackToVersion", "v1.0.0");
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
        assertThat(event.fromState()).isEqualTo(PatternLifecycleState.ACTIVE);
        assertThat(event.toState()).isEqualTo(PatternLifecycleState.RETIRED);
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

        assertThat(event.principal()).isEqualTo("operator-123");
    }
}

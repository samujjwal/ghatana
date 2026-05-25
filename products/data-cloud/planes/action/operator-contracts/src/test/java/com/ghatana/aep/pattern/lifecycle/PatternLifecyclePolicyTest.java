package com.ghatana.aep.pattern.lifecycle;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternLifecyclePolicyTest {

    @Test
    void allowsGovernedPromotionPath() {
        assertThat(PatternLifecyclePolicy.isAllowed(
            PatternLifecycleState.RECOMMENDED,
            PatternLifecycleState.APPROVED)).isTrue();
        assertThat(PatternLifecyclePolicy.isAllowed(
            PatternLifecycleState.APPROVED,
            PatternLifecycleState.ACTIVE)).isTrue();
    }

    @Test
    void rejectsRecommendedDirectlyToActive() {
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.RECOMMENDED,
            PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_PROMOTED,
            "reviewer",
            Map.of());

        assertThatThrownBy(() -> PatternLifecyclePolicy.requireAllowed(transition))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RECOMMENDED -> ACTIVE");
    }

    @Test
    void rejectsTransitionsOutOfRetired() {
        assertThat(PatternLifecyclePolicy.isAllowed(
            PatternLifecycleState.RETIRED,
            PatternLifecycleState.ACTIVE)).isFalse();
    }

    @Test
    void lifecycleServiceEmitsAuditablePromotionEvent() {
        PatternLifecycleService service = new PatternLifecycleService(Clock.fixed(
            Instant.parse("2026-05-23T00:00:00Z"),
            ZoneOffset.UTC));

        PatternLifecycleEvent event = service.transition(new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.APPROVED,
            PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_PROMOTED,
            "reviewer",
            Map.of("reviewId", "review-1")));

        assertThat(event.eventType().eventType()).isEqualTo("pattern.promoted");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-05-23T00:00:00Z"));
        assertThat(event.evidence()).containsEntry("reviewId", "review-1");
    }

    @Test
    void rejectsMismatchedLifecycleEventType() {
        PatternLifecycleService service = new PatternLifecycleService(Clock.systemUTC());

        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.APPROVED,
            PatternLifecycleState.ACTIVE,
            PatternLifecycleEventType.PATTERN_APPROVED,
            "reviewer",
            Map.of());

        assertThatThrownBy(() -> service.transition(transition))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pattern.promoted");
    }

    @Test
    void shadowPatternHasNoProductionSideEffects() {
        // DC-P10-001: Verify shadow patterns cannot directly transition to ACTIVE
        assertThat(PatternLifecyclePolicy.isAllowed(
            PatternLifecycleState.SHADOW,
            PatternLifecycleState.ACTIVE)).isFalse();
        
        // Shadow must go through RECOMMENDED -> APPROVED -> ACTIVE path
        assertThat(PatternLifecyclePolicy.isAllowed(
            PatternLifecycleState.SHADOW,
            PatternLifecycleState.RECOMMENDED)).isTrue();
        assertThat(PatternLifecyclePolicy.isAllowed(
            PatternLifecycleState.RECOMMENDED,
            PatternLifecycleState.APPROVED)).isTrue();
        assertThat(PatternLifecyclePolicy.isAllowed(
            PatternLifecycleState.APPROVED,
            PatternLifecycleState.ACTIVE)).isTrue();
    }

    @Test
    void lifecycleEventsArePersistedAndAuditable() {
        // DC-P10-001: Verify lifecycle events are persisted and auditable
        PatternLifecycleService service = new PatternLifecycleService(Clock.systemUTC());
        PatternLifecycleRegistry registry = new PatternLifecycleRegistry(service);
        
        registry.initializeDraft("tenant-a", "pattern-1");
        
        PatternLifecycleTransition transition1 = new PatternLifecycleTransition(
            "pattern-1",
            "tenant-a",
            PatternLifecycleState.DRAFT,
            PatternLifecycleState.CANDIDATE,
            PatternLifecycleEventType.PATTERN_CREATED,
            "author",
            Map.of("reason", "initial draft"));
        
        PatternLifecycleEvent event1 = registry.transition(transition1);
        
        // Verify event is persisted
        assertThat(registry.events("tenant-a", "pattern-1")).hasSize(1);
        assertThat(registry.events("tenant-a", "pattern-1").get(0)).isEqualTo(event1);
        
        // Verify event is auditable (contains actor, timestamp, evidence)
        assertThat(event1.actor()).isEqualTo("author");
        assertThat(event1.occurredAt()).isNotNull();
        assertThat(event1.evidence()).containsEntry("reason", "initial draft");
        
        // Verify state is updated
        assertThat(registry.currentState("tenant-a", "pattern-1")).hasValue(PatternLifecycleState.CANDIDATE);
    }
}

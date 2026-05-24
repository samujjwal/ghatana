package com.ghatana.aep.pattern.lifecycle;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternLifecycleRegistryTest {

    private final PatternLifecycleRegistry registry = new PatternLifecycleRegistry(new PatternLifecycleService(
        Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC)));

    @Test
    void tracksStateAndEventsAcrossGovernedPromotionPath() {
        registry.initializeDraft("tenant-a", "pattern-1");

        transition("pattern-1", PatternLifecycleState.DRAFT, PatternLifecycleState.CANDIDATE,
            PatternLifecycleEventType.PATTERN_CREATED);
        transition("pattern-1", PatternLifecycleState.CANDIDATE, PatternLifecycleState.VALIDATED,
            PatternLifecycleEventType.PATTERN_VALIDATED);
        transition("pattern-1", PatternLifecycleState.VALIDATED, PatternLifecycleState.SHADOW,
            PatternLifecycleEventType.PATTERN_SHADOW_DEPLOYED);
        transition("pattern-1", PatternLifecycleState.SHADOW, PatternLifecycleState.RECOMMENDED,
            PatternLifecycleEventType.PATTERN_RECOMMENDED);
        transition("pattern-1", PatternLifecycleState.RECOMMENDED, PatternLifecycleState.APPROVED,
            PatternLifecycleEventType.PATTERN_APPROVED);
        PatternLifecycleEvent promoted = transition("pattern-1", PatternLifecycleState.APPROVED,
            PatternLifecycleState.ACTIVE, PatternLifecycleEventType.PATTERN_PROMOTED);

        assertThat(registry.currentState("tenant-a", "pattern-1"))
            .contains(PatternLifecycleState.ACTIVE);
        assertThat(registry.events("tenant-a", "pattern-1"))
            .hasSize(6)
            .last()
            .isEqualTo(promoted);
        assertThat(promoted.occurredAt()).isEqualTo(Instant.parse("2026-05-23T12:00:00Z"));
    }

    @Test
    void rejectsTransitionWhenStoredStateDoesNotMatchDeclaredFromState() {
        registry.initializeDraft("tenant-a", "pattern-1");

        assertThatThrownBy(() -> transition("pattern-1", PatternLifecycleState.RECOMMENDED,
            PatternLifecycleState.APPROVED, PatternLifecycleEventType.PATTERN_APPROVED))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("state mismatch");
    }

    @Test
    void rejectsRecommendedPatternDirectlyToActive() {
        registry.initializeDraft("tenant-a", "pattern-1");
        transition("pattern-1", PatternLifecycleState.DRAFT, PatternLifecycleState.CANDIDATE,
            PatternLifecycleEventType.PATTERN_CREATED);
        transition("pattern-1", PatternLifecycleState.CANDIDATE, PatternLifecycleState.VALIDATED,
            PatternLifecycleEventType.PATTERN_VALIDATED);
        transition("pattern-1", PatternLifecycleState.VALIDATED, PatternLifecycleState.RECOMMENDED,
            PatternLifecycleEventType.PATTERN_RECOMMENDED);

        assertThatThrownBy(() -> transition("pattern-1", PatternLifecycleState.RECOMMENDED,
            PatternLifecycleState.ACTIVE, PatternLifecycleEventType.PATTERN_PROMOTED))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RECOMMENDED -> ACTIVE");
        assertThat(registry.currentState("tenant-a", "pattern-1"))
            .contains(PatternLifecycleState.RECOMMENDED);
    }

    @Test
    void rejectsDuplicateInitializationAndUnknownTransitions() {
        registry.initializeDraft("tenant-a", "pattern-1");

        assertThatThrownBy(() -> registry.initializeDraft("tenant-a", "pattern-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already exists");

        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-2",
            "tenant-a",
            PatternLifecycleState.DRAFT,
            PatternLifecycleState.CANDIDATE,
            PatternLifecycleEventType.PATTERN_CREATED,
            "reviewer",
            Map.of());

        assertThatThrownBy(() -> registry.transition(transition))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not exist");
    }

    private PatternLifecycleEvent transition(
            String patternId,
            PatternLifecycleState from,
            PatternLifecycleState to,
            PatternLifecycleEventType eventType) {
        return registry.transition(new PatternLifecycleTransition(
            patternId,
            "tenant-a",
            from,
            to,
            eventType,
            "reviewer",
            Map.of("reviewId", "review-1")));
    }
}

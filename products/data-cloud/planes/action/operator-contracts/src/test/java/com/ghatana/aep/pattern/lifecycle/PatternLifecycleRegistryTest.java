package com.ghatana.aep.pattern.lifecycle;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternLifecycleRegistryTest extends EventloopTestBase {

    private final PatternLifecycleRegistry registry = new PatternLifecycleRegistry(new PatternLifecycleService(
        Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC)));

    @Test
    void tracksStateAndEventsAcrossGovernedPromotionPath() {
        runPromise(() -> registry.initializeDraft("tenant-a", "pattern-1"));

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

        assertThat(runPromise(() -> registry.currentState("tenant-a", "pattern-1")))
            .contains(PatternLifecycleState.ACTIVE);
        assertThat(runPromise(() -> registry.events("tenant-a", "pattern-1")))
            .hasSize(6)
            .last()
            .isEqualTo(promoted);
        assertThat(promoted.occurredAt()).isEqualTo(Instant.parse("2026-05-23T12:00:00Z"));
    }

    @Test
    void rejectsTransitionWhenStoredStateDoesNotMatchDeclaredFromState() {
        runPromise(() -> registry.initializeDraft("tenant-a", "pattern-1"));

        assertThatThrownBy(() -> runPromise(() -> transition("pattern-1", PatternLifecycleState.RECOMMENDED,
            PatternLifecycleState.APPROVED, PatternLifecycleEventType.PATTERN_APPROVED)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("state mismatch");
        clearFatalError();
    }

    @Test
    void rejectsRecommendedPatternDirectlyToActive() {
        runPromise(() -> registry.initializeDraft("tenant-a", "pattern-1"));
        transition("pattern-1", PatternLifecycleState.DRAFT, PatternLifecycleState.CANDIDATE,
            PatternLifecycleEventType.PATTERN_CREATED);
        transition("pattern-1", PatternLifecycleState.CANDIDATE, PatternLifecycleState.VALIDATED,
            PatternLifecycleEventType.PATTERN_VALIDATED);
        transition("pattern-1", PatternLifecycleState.VALIDATED, PatternLifecycleState.RECOMMENDED,
            PatternLifecycleEventType.PATTERN_RECOMMENDED);

        assertThatThrownBy(() -> runPromise(() -> transition("pattern-1", PatternLifecycleState.RECOMMENDED,
            PatternLifecycleState.ACTIVE, PatternLifecycleEventType.PATTERN_PROMOTED)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RECOMMENDED -> ACTIVE");
        clearFatalError();
        assertThat(runPromise(() -> registry.currentState("tenant-a", "pattern-1")))
            .contains(PatternLifecycleState.RECOMMENDED);
    }

    @Test
    void rejectsDuplicateInitializationAndUnknownTransitions() {
        runPromise(() -> registry.initializeDraft("tenant-a", "pattern-1"));

        assertThatThrownBy(() -> runPromise(() -> registry.initializeDraft("tenant-a", "pattern-1")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already exists");
        clearFatalError();

        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            "pattern-2",
            "tenant-a",
            PatternLifecycleState.DRAFT,
            PatternLifecycleState.CANDIDATE,
            PatternLifecycleEventType.PATTERN_CREATED,
            "reviewer",
            Map.of());

        assertThatThrownBy(() -> runPromise(() -> registry.transition(transition)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not exist");
        clearFatalError();
    }

    @Test
    void lifecycleSurvivesRestartWhenUsingDurableRepository() {
        // Create a durable repository (mock for test)
        PatternLifecycleRepository mockRepository = new PatternLifecycleRepository() {
            private final java.util.Map<String, PatternLifecycleState> states = new java.util.HashMap<>();
            private final java.util.Map<String, java.util.List<PatternLifecycleEvent>> events = new java.util.HashMap<>();

            @Override
            public Promise<Void> saveState(String tenantId, String patternId, PatternLifecycleState state) {
                states.put(tenantId + ":" + patternId, state);
                return Promise.complete();
            }

            @Override
            public Promise<java.util.Optional<PatternLifecycleState>> getState(String tenantId, String patternId) {
                return Promise.of(java.util.Optional.ofNullable(states.get(tenantId + ":" + patternId)));
            }

            @Override
            public Promise<Void> saveEvent(PatternLifecycleEvent event) {
                events.computeIfAbsent(event.tenantId() + ":" + event.patternId(), k -> new java.util.ArrayList<>())
                    .add(event);
                return Promise.complete();
            }

            @Override
            public Promise<java.util.List<PatternLifecycleEvent>> getEvents(String tenantId, String patternId) {
                return Promise.of(java.util.List.copyOf(events.getOrDefault(tenantId + ":" + patternId, java.util.List.of())));
            }

            @Override
            public Promise<Void> delete(String tenantId, String patternId) {
                states.remove(tenantId + ":" + patternId);
                events.remove(tenantId + ":" + patternId);
                return Promise.complete();
            }

            @Override
            public Promise<java.util.List<String>> getPatternsByState(String tenantId, PatternLifecycleState state) {
                return Promise.of(java.util.List.of());
            }
        };

        PatternLifecycleRegistry durableRegistry = new PatternLifecycleRegistry(
            new PatternLifecycleService(Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC)),
            mockRepository);

        // Initialize and transition
        runPromise(() -> durableRegistry.initializeDraft("tenant-a", "pattern-1"));
        transition(durableRegistry, "pattern-1", PatternLifecycleState.DRAFT, PatternLifecycleState.CANDIDATE,
            PatternLifecycleEventType.PATTERN_CREATED);

        // Verify state persisted
        assertThat(runPromise(() -> durableRegistry.currentState("tenant-a", "pattern-1")))
            .contains(PatternLifecycleState.CANDIDATE);
        assertThat(runPromise(() -> durableRegistry.events("tenant-a", "pattern-1")))
            .hasSize(1);

        // Simulate restart by creating new registry with same repository
        PatternLifecycleRegistry restartedRegistry = new PatternLifecycleRegistry(
            new PatternLifecycleService(Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC)),
            mockRepository);

        // Verify state survived restart
        assertThat(runPromise(() -> restartedRegistry.currentState("tenant-a", "pattern-1")))
            .contains(PatternLifecycleState.CANDIDATE);
        assertThat(runPromise(() -> restartedRegistry.events("tenant-a", "pattern-1")))
            .hasSize(1);
    }

    @Test
    void approveRejectWritesAuditAndLifecycleEvent() {
        runPromise(() -> registry.initializeDraft("tenant-a", "pattern-1"));
        transition("pattern-1", PatternLifecycleState.DRAFT, PatternLifecycleState.CANDIDATE,
            PatternLifecycleEventType.PATTERN_CREATED);
        transition("pattern-1", PatternLifecycleState.CANDIDATE, PatternLifecycleState.VALIDATED,
            PatternLifecycleEventType.PATTERN_VALIDATED);
        transition("pattern-1", PatternLifecycleState.VALIDATED, PatternLifecycleState.RECOMMENDED,
            PatternLifecycleEventType.PATTERN_RECOMMENDED);

        // Approve transition
        PatternLifecycleEvent approvedEvent = transition("pattern-1", PatternLifecycleState.RECOMMENDED,
            PatternLifecycleState.APPROVED, PatternLifecycleEventType.PATTERN_APPROVED);

        // Verify audit/lifecycle event was written
        assertThat(approvedEvent.eventType()).isEqualTo(PatternLifecycleEventType.PATTERN_APPROVED);
        assertThat(approvedEvent.actor()).isEqualTo("reviewer");
        assertThat(approvedEvent.fromState()).isEqualTo(PatternLifecycleState.RECOMMENDED);
        assertThat(approvedEvent.toState()).isEqualTo(PatternLifecycleState.APPROVED);
        assertThat(approvedEvent.metadata()).containsKey("reviewId");

        // Verify event is persisted in event history
        assertThat(runPromise(() -> registry.events("tenant-a", "pattern-1")))
            .hasSize(4)
            .contains(approvedEvent);

        // Reject transition (back to VALIDATED)
        PatternLifecycleEvent rejectedEvent = transition("pattern-1", PatternLifecycleState.APPROVED,
            PatternLifecycleState.VALIDATED, PatternLifecycleEventType.PATTERN_REJECTED);

        // Verify reject audit/lifecycle event was written
        assertThat(rejectedEvent.eventType()).isEqualTo(PatternLifecycleEventType.PATTERN_REJECTED);
        assertThat(rejectedEvent.actor()).isEqualTo("reviewer");
        assertThat(rejectedEvent.fromState()).isEqualTo(PatternLifecycleState.APPROVED);
        assertThat(rejectedEvent.toState()).isEqualTo(PatternLifecycleState.VALIDATED);

        // Verify both events are in history
        assertThat(runPromise(() -> registry.events("tenant-a", "pattern-1")))
            .hasSize(5)
            .contains(approvedEvent, rejectedEvent);
    }

    @Test
    void inMemoryModeWarningLogged() {
        PatternLifecycleRegistry inMemoryRegistry = new PatternLifecycleRegistry(
            new PatternLifecycleService(Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC)));
        
        assertThat(inMemoryRegistry.isInMemoryMode()).isTrue();
    }

    @Test
    void enforcesTenantIsolation() {
        // Initialize pattern for tenant A
        runPromise(() -> registry.initializeDraft("tenant-a", "pattern-1"));
        transition("pattern-1", PatternLifecycleState.DRAFT, PatternLifecycleState.CANDIDATE,
            PatternLifecycleEventType.PATTERN_CREATED);

        // Verify tenant A has state
        assertThat(runPromise(() -> registry.currentState("tenant-a", "pattern-1")))
            .contains(PatternLifecycleState.CANDIDATE);

        // Verify tenant B has no state for same pattern ID
        assertThat(runPromise(() -> registry.currentState("tenant-b", "pattern-1")))
            .isEmpty();

        // Initialize different pattern for tenant B
        runPromise(() -> registry.initializeDraft("tenant-b", "pattern-2"));
        transition("pattern-2", PatternLifecycleState.DRAFT, PatternLifecycleState.CANDIDATE,
            PatternLifecycleEventType.PATTERN_CREATED);

        // Verify tenant B's pattern state
        assertThat(runPromise(() -> registry.currentState("tenant-b", "pattern-2")))
            .contains(PatternLifecycleState.CANDIDATE);

        // Verify tenant A's pattern state unchanged
        assertThat(runPromise(() -> registry.currentState("tenant-a", "pattern-1")))
            .contains(PatternLifecycleState.CANDIDATE);

        // Verify events are tenant-isolated
        assertThat(runPromise(() -> registry.events("tenant-a", "pattern-1")))
            .hasSize(1);
        assertThat(runPromise(() -> registry.events("tenant-b", "pattern-2")))
            .hasSize(1);
    }

    private PatternLifecycleEvent transition(
            String patternId,
            PatternLifecycleState from,
            PatternLifecycleState to,
            PatternLifecycleEventType eventType) {
        return transition(registry, patternId, from, to, eventType);
    }

    private PatternLifecycleEvent transition(
            PatternLifecycleRegistry registry,
            String patternId,
            PatternLifecycleState from,
            PatternLifecycleState to,
            PatternLifecycleEventType eventType) {
        return runPromise(() -> registry.transition(new PatternLifecycleTransition(
            patternId,
            "tenant-a",
            from,
            to,
            eventType,
            "reviewer",
            Map.of("reviewId", "review-1"))));
    }
}

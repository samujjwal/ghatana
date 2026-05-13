/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import com.ghatana.agent.mastery.ApplicabilityScope;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.MasteryTransitionResult;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for ObsolescenceRouter.
 *
 * @doc.type class
 * @doc.purpose Tests for ObsolescenceRouter
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("ObsolescenceRouter Tests")
class ObsolescenceRouterTest extends EventloopTestBase {

    @Test
    @DisplayName("Should route version mismatch to OBSOLETE state")
    void shouldRouteVersionMismatchToObsoleteState() {
        MasteryRegistry registry = mock(MasteryRegistry.class);
        ObsolescenceRouter router = new ObsolescenceRouter(registry);

        MasteryItem item = createMasteryItem();
        when(registry.findBySkill(item.masteryId(), null))
                .thenReturn(Promise.of(java.util.Optional.of(item)));
        when(registry.transition(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(MasteryTransitionResult.success(
                        item.masteryId(),
                        MasteryState.COMPETENT,
                        MasteryState.OBSOLETE,
                        "Transition successful"
                )));

        ObsolescenceEvent event = new ObsolescenceEvent(
                "event-1",
                item.masteryId(),
                "tenant-123",
                ObsolescenceReason.VERSION_MISMATCH,
                "Version mismatch detected",
                Instant.now(),
                List.of(),
                Map.of(),
                ObsolescenceEvent.Severity.MEDIUM,
                MasteryState.OBSOLETE
        );

        MasteryTransitionResult transitionResult = runPromise(() -> router.route(event));

        assertThat(transitionResult.success()).isTrue();
        assertThat(transitionResult.newState()).isEqualTo(MasteryState.OBSOLETE);
    }

    @Test
    @DisplayName("Should route security vulnerability to RETIRED state")
    void shouldRouteSecurityVulnerabilityToRetiredState() {
        MasteryRegistry registry = mock(MasteryRegistry.class);
        ObsolescenceRouter router = new ObsolescenceRouter(registry);

        MasteryItem item = createMasteryItem();
        when(registry.findBySkill(item.masteryId(), null))
                .thenReturn(Promise.of(java.util.Optional.of(item)));
        when(registry.transition(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(MasteryTransitionResult.success(
                        item.masteryId(),
                        MasteryState.COMPETENT,
                        MasteryState.RETIRED,
                        "Transition successful"
                )));

        ObsolescenceEvent event = new ObsolescenceEvent(
                "event-1",
                item.masteryId(),
                "tenant-123",
                ObsolescenceReason.SECURITY_VULNERABILITY,
                "Security vulnerability detected",
                Instant.now(),
                List.of(),
                Map.of(),
                ObsolescenceEvent.Severity.HIGH,
                MasteryState.RETIRED
        );

        MasteryTransitionResult transitionResult = runPromise(() -> router.route(event));

        assertThat(transitionResult.success()).isTrue();
        assertThat(transitionResult.newState()).isEqualTo(MasteryState.RETIRED);
    }

    @Test
    @DisplayName("Should route repeated failures to QUARANTINED state")
    void shouldRouteRepeatedFailuresToQuarantinedState() {
        MasteryRegistry registry = mock(MasteryRegistry.class);
        ObsolescenceRouter router = new ObsolescenceRouter(registry);

        MasteryItem item = createMasteryItem();
        when(registry.findBySkill(item.masteryId(), null))
                .thenReturn(Promise.of(java.util.Optional.of(item)));
        when(registry.transition(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(MasteryTransitionResult.success(
                        item.masteryId(),
                        MasteryState.COMPETENT,
                        MasteryState.QUARANTINED,
                        "Transition successful"
                )));

        ObsolescenceEvent event = new ObsolescenceEvent(
                "event-1",
                item.masteryId(),
                "tenant-123",
                ObsolescenceReason.REPEATED_FAILURES,
                "Repeated failures detected",
                Instant.now(),
                List.of(),
                Map.of(),
                ObsolescenceEvent.Severity.HIGH,
                MasteryState.QUARANTINED
        );

        MasteryTransitionResult transitionResult = runPromise(() -> router.route(event));

        assertThat(transitionResult.success()).isTrue();
        assertThat(transitionResult.newState()).isEqualTo(MasteryState.QUARANTINED);
    }

    @Test
    @DisplayName("Should route multiple events")
    void shouldRouteMultipleEvents() {
        MasteryRegistry registry = mock(MasteryRegistry.class);
        ObsolescenceRouter router = new ObsolescenceRouter(registry);

        MasteryItem item = createMasteryItem();
        when(registry.findBySkill(item.masteryId(), null))
                .thenReturn(Promise.of(java.util.Optional.of(item)));
        when(registry.transition(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(MasteryTransitionResult.success(
                        item.masteryId(),
                        MasteryState.COMPETENT,
                        MasteryState.OBSOLETE,
                        "Transition successful"
                )));

        List<ObsolescenceEvent> events = List.of(
                new ObsolescenceEvent(
                        "event-1",
                        item.masteryId(),
                        "tenant-123",
                        ObsolescenceReason.VERSION_MISMATCH,
                        "Version mismatch detected",
                        Instant.now(),
                        List.of(),
                        Map.of(),
                        ObsolescenceEvent.Severity.MEDIUM,
                        MasteryState.OBSOLETE
                ),
                new ObsolescenceEvent(
                        "event-2",
                        item.masteryId(),
                        "tenant-123",
                        ObsolescenceReason.API_CHANGE,
                        "API changed",
                        Instant.now(),
                        List.of(),
                        Map.of(),
                        ObsolescenceEvent.Severity.MEDIUM,
                        MasteryState.OBSOLETE
                )
        );

        List<MasteryTransitionResult> transitionResults = runPromise(() -> router.routeAll(events));

        assertThat(transitionResults).hasSize(2);
        assertThat(transitionResults).allMatch(MasteryTransitionResult::success);
    }

    @Test
    @DisplayName("Should fail when mastery item not found")
    void shouldFailWhenMasteryItemNotFound() {
        MasteryRegistry registry = mock(MasteryRegistry.class);
        ObsolescenceRouter router = new ObsolescenceRouter(registry);

        when(registry.findBySkill("unknown-mastery", null))
                .thenReturn(Promise.of(java.util.Optional.empty()));

        ObsolescenceEvent event = new ObsolescenceEvent(
                "event-1",
                "unknown-mastery",
                "tenant-123",
                ObsolescenceReason.VERSION_MISMATCH,
                "Version mismatch detected",
                Instant.now(),
                List.of(),
                Map.of(),
                ObsolescenceEvent.Severity.MEDIUM,
                MasteryState.OBSOLETE
        );

        MasteryTransitionResult transitionResult = runPromise(() -> router.route(event));

        assertThat(transitionResult.success()).isFalse();
        assertThat(transitionResult.errorMessage()).isPresent();
        assertThat(transitionResult.errorMessage().get()).contains("Mastery item not found");
    }

    // Helper methods

    private MasteryItem createMasteryItem() {
        return new MasteryItem(
                "mastery-123",
                "tenant-123",
                "skill-123",
                "test-domain",
                "agent-123",
                "release-123",
                MasteryState.COMPETENT,
                VersionScope.empty(),
                ApplicabilityScope.minimal("tenant-123", "production"),
                new MasteryScore(0.8, 0.7, 0.9, 0.85, 0.75, 0.8, 0.9),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Instant.now(),
                Instant.now().plusSeconds(86400),
                Map.of(),
                0.8
        );
    }
}

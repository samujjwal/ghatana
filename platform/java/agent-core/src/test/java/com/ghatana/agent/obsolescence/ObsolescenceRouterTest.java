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
import com.ghatana.agent.mastery.MasteryTransition;
import com.ghatana.agent.mastery.MasteryTransitionResult;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.agent.mastery.transition.MasteryTransitionPolicy;
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
        MasteryTransitionPolicy policy = (from, to, evidence) -> MasteryTransitionPolicy.TransitionValidation.success();
        ObsolescenceRouter router = new ObsolescenceRouter(registry, policy);

        MasteryItem item = createMasteryItem();
        when(registry.getById("tenant-123", item.masteryId()))
                .thenReturn(Promise.of(java.util.Optional.of(item)));
        when(registry.transition(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(MasteryTransitionResult.success(
                        item.masteryId(),
                        MasteryState.COMPETENT,
                        MasteryState.OBSOLETE,
                        "Transition successful"
                )));

        ObsolescenceEvent event = ObsolescenceEvent.of(
                item.masteryId(),
                "tenant-123",
                ObsolescenceReason.VERSION_MISMATCH,
                "Version mismatch detected",
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
        MasteryTransitionPolicy policy = (from, to, evidence) -> MasteryTransitionPolicy.TransitionValidation.success();
        ObsolescenceRouter router = new ObsolescenceRouter(registry, policy);

        MasteryItem item = createMasteryItem();
        when(registry.getById("tenant-123", item.masteryId()))
                .thenReturn(Promise.of(java.util.Optional.of(item)));
        when(registry.transition(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(MasteryTransitionResult.success(
                        item.masteryId(),
                        MasteryState.COMPETENT,
                        MasteryState.RETIRED,
                        "Transition successful"
                )));

        ObsolescenceEvent event = ObsolescenceEvent.of(
                item.masteryId(),
                "tenant-123",
                ObsolescenceReason.SECURITY_VULNERABILITY,
                "Security vulnerability detected",
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
        MasteryTransitionPolicy policy = (from, to, evidence) -> MasteryTransitionPolicy.TransitionValidation.success();
        ObsolescenceRouter router = new ObsolescenceRouter(registry, policy);

        MasteryItem item = createMasteryItem();
        when(registry.getById("tenant-123", item.masteryId()))
                .thenReturn(Promise.of(java.util.Optional.of(item)));
        when(registry.transition(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(MasteryTransitionResult.success(
                        item.masteryId(),
                        MasteryState.COMPETENT,
                        MasteryState.QUARANTINED,
                        "Transition successful"
                )));

        ObsolescenceEvent event = ObsolescenceEvent.of(
                item.masteryId(),
                "tenant-123",
                ObsolescenceReason.REPEATED_FAILURES,
                "Repeated failures detected",
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
        MasteryTransitionPolicy policy = (from, to, evidence) -> MasteryTransitionPolicy.TransitionValidation.success();
        ObsolescenceRouter router = new ObsolescenceRouter(registry, policy);

        MasteryItem item = createMasteryItem();
        when(registry.getById("tenant-123", item.masteryId()))
                .thenReturn(Promise.of(java.util.Optional.of(item)));
        when(registry.transition(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(MasteryTransitionResult.success(
                        item.masteryId(),
                        MasteryState.COMPETENT,
                        MasteryState.OBSOLETE,
                        "Transition successful"
                )));

        List<ObsolescenceEvent> events = List.of(
                ObsolescenceEvent.of(
                        item.masteryId(),
                        "tenant-123",
                        ObsolescenceReason.VERSION_MISMATCH,
                        "Version mismatch detected",
                        List.of(),
                        Map.of(),
                        ObsolescenceEvent.Severity.MEDIUM,
                        MasteryState.OBSOLETE
                ),
                ObsolescenceEvent.of(
                        item.masteryId(),
                        "tenant-123",
                        ObsolescenceReason.API_CHANGE,
                        "API changed",
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
        MasteryTransitionPolicy policy = (from, to, evidence) -> MasteryTransitionPolicy.TransitionValidation.success();
        ObsolescenceRouter router = new ObsolescenceRouter(registry, policy);

        when(registry.getById("tenant-123", "unknown-mastery"))
                .thenReturn(Promise.of(java.util.Optional.empty()));

        ObsolescenceEvent event = ObsolescenceEvent.of(
                "unknown-mastery",
                "tenant-123",
                ObsolescenceReason.VERSION_MISMATCH,
                "Version mismatch detected",
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
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<MasteryTransition>of(),
                Instant.now(),
                Instant.now().plusSeconds(86400),
                Map.<String,String>of(),
                0.8
        );
    }
}

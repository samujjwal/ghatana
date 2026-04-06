/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.brain;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for brain state transitions (D009).
 *
 * <p>Validates state machine coverage for brain lifecycle.
 *
 * @doc.type class
 * @doc.purpose Brain state machine transition tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BrainState – State Machine Transitions (D009)")
class BrainStateTransitionTest extends EventloopTestBase {

    @Mock
    private BrainStateManager stateManager;

    // ─────────────────────────────────────────────────────────────────────────
    // Valid State Transitions
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid State Transitions")
    class ValidStateTransitionsTests {

        @Test
        @DisplayName("[D009]: uninitialized_to_initializing_valid")
        void uninitializedToInitializingValid() {
            String agentId = "agent-001";

            when(stateManager.canTransition(agentId, BrainStateManager.BrainState.INITIALIZING))
                .thenReturn(Promise.of(true));
            when(stateManager.transition(agentId, BrainStateManager.BrainState.INITIALIZING))
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult(
                    true,
                    BrainStateManager.BrainState.UNINITIALIZED,
                    BrainStateManager.BrainState.INITIALIZING,
                    100, null
                )));

            Boolean can = runPromise(() ->
                stateManager.canTransition(agentId, BrainStateManager.BrainState.INITIALIZING)
            );
            BrainStateManager.TransitionResult result = runPromise(() ->
                stateManager.transition(agentId, BrainStateManager.BrainState.INITIALIZING)
            );

            assertThat(can).isTrue();
            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.fromState()).isEqualTo(BrainStateManager.BrainState.UNINITIALIZED);
            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.INITIALIZING);
        }

        @Test
        @DisplayName("[D009]: initializing_to_active_valid")
        void initializingToActiveValid() {
            String agentId = "agent-001";

            when(stateManager.transition(agentId, BrainStateManager.BrainState.ACTIVE))
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult(
                    true,
                    BrainStateManager.BrainState.INITIALIZING,
                    BrainStateManager.BrainState.ACTIVE,
                    200, null
                )));

            BrainStateManager.TransitionResult result = runPromise(() ->
                stateManager.transition(agentId, BrainStateManager.BrainState.ACTIVE)
            );

            assertThat(result.fromState()).isEqualTo(BrainStateManager.BrainState.INITIALIZING);
            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.ACTIVE);
        }

        @Test
        @DisplayName("[D009]: active_to_paused_valid")
        void activeToPausedValid() {
            String agentId = "agent-001";

            when(stateManager.transition(agentId, BrainStateManager.BrainState.PAUSED))
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult(
                    true,
                    BrainStateManager.BrainState.ACTIVE,
                    BrainStateManager.BrainState.PAUSED,
                    50, null
                )));

            BrainStateManager.TransitionResult result = runPromise(() ->
                stateManager.transition(agentId, BrainStateManager.BrainState.PAUSED)
            );

            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.PAUSED);
        }

        @Test
        @DisplayName("[D009]: paused_to_active_valid")
        void pausedToActiveValid() {
            String agentId = "agent-001";

            when(stateManager.transition(agentId, BrainStateManager.BrainState.ACTIVE))
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult(
                    true,
                    BrainStateManager.BrainState.PAUSED,
                    BrainStateManager.BrainState.ACTIVE,
                    50, null
                )));

            BrainStateManager.TransitionResult result = runPromise(() ->
                stateManager.transition(agentId, BrainStateManager.BrainState.ACTIVE)
            );

            assertThat(result.fromState()).isEqualTo(BrainStateManager.BrainState.PAUSED);
            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.ACTIVE);
        }

        @Test
        @DisplayName("[D009]: active_to_learning_valid")
        void activeToLearningValid() {
            String agentId = "agent-001";

            when(stateManager.transition(agentId, BrainStateManager.BrainState.LEARNING))
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult(
                    true,
                    BrainStateManager.BrainState.ACTIVE,
                    BrainStateManager.BrainState.LEARNING,
                    150, null
                )));

            BrainStateManager.TransitionResult result = runPromise(() ->
                stateManager.transition(agentId, BrainStateManager.BrainState.LEARNING)
            );

            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.LEARNING);
        }

        @Test
        @DisplayName("[D009]: active_to_resting_valid")
        void activeToRestingValid() {
            String agentId = "agent-001";

            when(stateManager.transition(agentId, BrainStateManager.BrainState.RESTING))
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult(
                    true,
                    BrainStateManager.BrainState.ACTIVE,
                    BrainStateManager.BrainState.RESTING,
                    100, null
                )));

            BrainStateManager.TransitionResult result = runPromise(() ->
                stateManager.transition(agentId, BrainStateManager.BrainState.RESTING)
            );

            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.RESTING);
        }

        @Test
        @DisplayName("[D009]: active_to_shutting_down_valid")
        void activeToShuttingDownValid() {
            String agentId = "agent-001";

            when(stateManager.transition(agentId, BrainStateManager.BrainState.SHUTTING_DOWN))
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult(
                    true,
                    BrainStateManager.BrainState.ACTIVE,
                    BrainStateManager.BrainState.SHUTTING_DOWN,
                    300, null
                )));

            BrainStateManager.TransitionResult result = runPromise(() ->
                stateManager.transition(agentId, BrainStateManager.BrainState.SHUTTING_DOWN)
            );

            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.SHUTTING_DOWN);
        }

        @Test
        @DisplayName("[D009]: shutting_down_to_terminated_valid")
        void shuttingDownToTerminatedValid() {
            String agentId = "agent-001";

            when(stateManager.transition(agentId, BrainStateManager.BrainState.TERMINATED))
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult(
                    true,
                    BrainStateManager.BrainState.SHUTTING_DOWN,
                    BrainStateManager.BrainState.TERMINATED,
                    200, null
                )));

            BrainStateManager.TransitionResult result = runPromise(() ->
                stateManager.transition(agentId, BrainStateManager.BrainState.TERMINATED)
            );

            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.TERMINATED);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Invalid State Transitions
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Invalid State Transitions")
    class InvalidStateTransitionsTests {

        @Test
        @DisplayName("[D009]: terminated_to_any_invalid")
        void terminatedToAnyInvalid() {
            String agentId = "agent-001";

            when(stateManager.canTransition(agentId, BrainStateManager.BrainState.ACTIVE))
                .thenReturn(Promise.of(false));

            Boolean can = runPromise(() ->
                stateManager.canTransition(agentId, BrainStateManager.BrainState.ACTIVE)
            );

            assertThat(can).isFalse();
        }

        @Test
        @DisplayName("[D009]: active_to_uninitialized_invalid")
        void activeToUninitializedInvalid() {
            String agentId = "agent-001";

            when(stateManager.canTransition(agentId, BrainStateManager.BrainState.UNINITIALIZED))
                .thenReturn(Promise.of(false));

            Boolean can = runPromise(() ->
                stateManager.canTransition(agentId, BrainStateManager.BrainState.UNINITIALIZED)
            );

            assertThat(can).isFalse();
        }

        @Test
        @DisplayName("[D009]: uninitialized_to_active_invalid")
        void uninitializedToActiveInvalid() {
            String agentId = "agent-001";

            when(stateManager.canTransition(agentId, BrainStateManager.BrainState.ACTIVE))
                .thenReturn(Promise.of(false));

            Boolean can = runPromise(() ->
                stateManager.canTransition(agentId, BrainStateManager.BrainState.ACTIVE)
            );

            assertThat(can).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State History Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("State History")
    class StateHistoryTests {

        @Test
        @DisplayName("[D009]: get_state_history_returns_records")
        void getStateHistoryReturnsRecords() {
            String agentId = "agent-001";
            List<BrainStateManager.StateRecord> history = List.of(
                new BrainStateManager.StateRecord(
                    BrainStateManager.BrainState.UNINITIALIZED,
                    System.currentTimeMillis() - 10000,
                    "Created", 0
                ),
                new BrainStateManager.StateRecord(
                    BrainStateManager.BrainState.INITIALIZING,
                    System.currentTimeMillis() - 9000,
                    "Starting up", 1000
                ),
                new BrainStateManager.StateRecord(
                    BrainStateManager.BrainState.ACTIVE,
                    System.currentTimeMillis() - 8000,
                    "Ready", 1000
                )
            );

            when(stateManager.getStateHistory(agentId, 10))
                .thenReturn(Promise.of(history));

            List<BrainStateManager.StateRecord> result = runPromise(() ->
                stateManager.getStateHistory(agentId, 10)
            );

            assertThat(result).hasSize(3);
            assertThat(result.get(0).state()).isEqualTo(BrainStateManager.BrainState.UNINITIALIZED);
            assertThat(result.get(2).state()).isEqualTo(BrainStateManager.BrainState.ACTIVE);
        }

        @Test
        @DisplayName("[D009]: state_records_include_duration")
        void stateRecordsIncludeDuration() {
            BrainStateManager.StateRecord record = new BrainStateManager.StateRecord(
                BrainStateManager.BrainState.ACTIVE,
                System.currentTimeMillis(),
                "Running", 5000
            );

            assertThat(record.durationMs()).isEqualTo(5000);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wait for State Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Wait for State")
    class WaitForStateTests {

        @Test
        @DisplayName("[D009]: wait_for_state_returns_true_when_reached")
        void waitForStateReturnsTrueWhenReached() {
            String agentId = "agent-001";

            when(stateManager.waitForState(
                eq(agentId),
                eq(BrainStateManager.BrainState.ACTIVE),
                any(Duration.class)
            )).thenReturn(Promise.of(true));

            Boolean result = runPromise(() ->
                stateManager.waitForState(agentId, BrainStateManager.BrainState.ACTIVE, Duration.ofSeconds(5))
            );

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("[D009]: wait_for_state_returns_false_on_timeout")
        void waitForStateReturnsFalseOnTimeout() {
            String agentId = "agent-001";

            when(stateManager.waitForState(
                eq(agentId),
                eq(BrainStateManager.BrainState.ACTIVE),
                any(Duration.class)
            )).thenReturn(Promise.of(false));

            Boolean result = runPromise(() ->
                stateManager.waitForState(agentId, BrainStateManager.BrainState.ACTIVE, Duration.ofMillis(100))
            );

            assertThat(result).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State Change Listener Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("State Change Listener")
    class StateChangeListenerTests {

        @Test
        @DisplayName("[D009]: on_state_change_registers_listener")
        void onStateChangeRegistersListener() {
            String agentId = "agent-001";

            when(stateManager.onStateChange(eq(agentId), any()))
                .thenReturn(Promise.of((Void) null));

            BrainStateManager.StateChangeListener listener = (from, to, ctx) -> Promise.of((Void) null);

            runPromise(() -> stateManager.onStateChange(agentId, listener));

            verify(stateManager).onStateChange(eq(agentId), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Transition Context Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Transition Context")
    class TransitionContextTests {

        @Test
        @DisplayName("[D009]: transition_context_includes_metadata")
        void transitionContextIncludesMetadata() {
            BrainStateManager.TransitionContext context = new BrainStateManager.TransitionContext(
                "agent-001",
                "user_request",
                Map.of("reason", "manual_trigger"),
                System.currentTimeMillis()
            );

            assertThat(context.agentId()).isEqualTo("agent-001");
            assertThat(context.triggeredBy()).isEqualTo("user_request");
            assertThat(context.metadata()).containsKey("reason");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State Enum Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("State Enum")
    class StateEnumTests {

        @Test
        @DisplayName("[D009]: all_states_defined")
        void allStatesDefined() {
            BrainStateManager.BrainState[] states = BrainStateManager.BrainState.values();

            assertThat(states).contains(
                BrainStateManager.BrainState.UNINITIALIZED,
                BrainStateManager.BrainState.INITIALIZING,
                BrainStateManager.BrainState.ACTIVE,
                BrainStateManager.BrainState.PAUSED,
                BrainStateManager.BrainState.LEARNING,
                BrainStateManager.BrainState.RESTING,
                BrainStateManager.BrainState.SHUTTING_DOWN,
                BrainStateManager.BrainState.TERMINATED
            );
        }
    }
}

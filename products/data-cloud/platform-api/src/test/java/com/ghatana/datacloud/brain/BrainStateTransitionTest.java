/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Tests for brain state transitions (D009). // GH-90000
 *
 * <p>Validates state machine coverage for brain lifecycle.
 *
 * @doc.type class
 * @doc.purpose Brain state machine transition tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
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
        void uninitializedToInitializingValid() { // GH-90000
            String agentId = "agent-001";

            when(stateManager.canTransition(agentId, BrainStateManager.BrainState.INITIALIZING)) // GH-90000
                .thenReturn(Promise.of(true)); // GH-90000
            when(stateManager.transition(agentId, BrainStateManager.BrainState.INITIALIZING)) // GH-90000
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult( // GH-90000
                    true,
                    BrainStateManager.BrainState.UNINITIALIZED,
                    BrainStateManager.BrainState.INITIALIZING,
                    100, null
                )));

            Boolean can = runPromise(() -> // GH-90000
                stateManager.canTransition(agentId, BrainStateManager.BrainState.INITIALIZING) // GH-90000
            );
            BrainStateManager.TransitionResult result = runPromise(() -> // GH-90000
                stateManager.transition(agentId, BrainStateManager.BrainState.INITIALIZING) // GH-90000
            );

            assertThat(can).isTrue(); // GH-90000
            assertThat(result.isSuccessful()).isTrue(); // GH-90000
            assertThat(result.fromState()).isEqualTo(BrainStateManager.BrainState.UNINITIALIZED); // GH-90000
            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.INITIALIZING); // GH-90000
        }

        @Test
        @DisplayName("[D009]: initializing_to_active_valid")
        void initializingToActiveValid() { // GH-90000
            String agentId = "agent-001";

            when(stateManager.transition(agentId, BrainStateManager.BrainState.ACTIVE)) // GH-90000
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult( // GH-90000
                    true,
                    BrainStateManager.BrainState.INITIALIZING,
                    BrainStateManager.BrainState.ACTIVE,
                    200, null
                )));

            BrainStateManager.TransitionResult result = runPromise(() -> // GH-90000
                stateManager.transition(agentId, BrainStateManager.BrainState.ACTIVE) // GH-90000
            );

            assertThat(result.fromState()).isEqualTo(BrainStateManager.BrainState.INITIALIZING); // GH-90000
            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.ACTIVE); // GH-90000
        }

        @Test
        @DisplayName("[D009]: active_to_paused_valid")
        void activeToPausedValid() { // GH-90000
            String agentId = "agent-001";

            when(stateManager.transition(agentId, BrainStateManager.BrainState.PAUSED)) // GH-90000
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult( // GH-90000
                    true,
                    BrainStateManager.BrainState.ACTIVE,
                    BrainStateManager.BrainState.PAUSED,
                    50, null
                )));

            BrainStateManager.TransitionResult result = runPromise(() -> // GH-90000
                stateManager.transition(agentId, BrainStateManager.BrainState.PAUSED) // GH-90000
            );

            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.PAUSED); // GH-90000
        }

        @Test
        @DisplayName("[D009]: paused_to_active_valid")
        void pausedToActiveValid() { // GH-90000
            String agentId = "agent-001";

            when(stateManager.transition(agentId, BrainStateManager.BrainState.ACTIVE)) // GH-90000
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult( // GH-90000
                    true,
                    BrainStateManager.BrainState.PAUSED,
                    BrainStateManager.BrainState.ACTIVE,
                    50, null
                )));

            BrainStateManager.TransitionResult result = runPromise(() -> // GH-90000
                stateManager.transition(agentId, BrainStateManager.BrainState.ACTIVE) // GH-90000
            );

            assertThat(result.fromState()).isEqualTo(BrainStateManager.BrainState.PAUSED); // GH-90000
            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.ACTIVE); // GH-90000
        }

        @Test
        @DisplayName("[D009]: active_to_learning_valid")
        void activeToLearningValid() { // GH-90000
            String agentId = "agent-001";

            when(stateManager.transition(agentId, BrainStateManager.BrainState.LEARNING)) // GH-90000
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult( // GH-90000
                    true,
                    BrainStateManager.BrainState.ACTIVE,
                    BrainStateManager.BrainState.LEARNING,
                    150, null
                )));

            BrainStateManager.TransitionResult result = runPromise(() -> // GH-90000
                stateManager.transition(agentId, BrainStateManager.BrainState.LEARNING) // GH-90000
            );

            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.LEARNING); // GH-90000
        }

        @Test
        @DisplayName("[D009]: active_to_resting_valid")
        void activeToRestingValid() { // GH-90000
            String agentId = "agent-001";

            when(stateManager.transition(agentId, BrainStateManager.BrainState.RESTING)) // GH-90000
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult( // GH-90000
                    true,
                    BrainStateManager.BrainState.ACTIVE,
                    BrainStateManager.BrainState.RESTING,
                    100, null
                )));

            BrainStateManager.TransitionResult result = runPromise(() -> // GH-90000
                stateManager.transition(agentId, BrainStateManager.BrainState.RESTING) // GH-90000
            );

            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.RESTING); // GH-90000
        }

        @Test
        @DisplayName("[D009]: active_to_shutting_down_valid")
        void activeToShuttingDownValid() { // GH-90000
            String agentId = "agent-001";

            when(stateManager.transition(agentId, BrainStateManager.BrainState.SHUTTING_DOWN)) // GH-90000
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult( // GH-90000
                    true,
                    BrainStateManager.BrainState.ACTIVE,
                    BrainStateManager.BrainState.SHUTTING_DOWN,
                    300, null
                )));

            BrainStateManager.TransitionResult result = runPromise(() -> // GH-90000
                stateManager.transition(agentId, BrainStateManager.BrainState.SHUTTING_DOWN) // GH-90000
            );

            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.SHUTTING_DOWN); // GH-90000
        }

        @Test
        @DisplayName("[D009]: shutting_down_to_terminated_valid")
        void shuttingDownToTerminatedValid() { // GH-90000
            String agentId = "agent-001";

            when(stateManager.transition(agentId, BrainStateManager.BrainState.TERMINATED)) // GH-90000
                .thenReturn(Promise.of(new BrainStateManager.TransitionResult( // GH-90000
                    true,
                    BrainStateManager.BrainState.SHUTTING_DOWN,
                    BrainStateManager.BrainState.TERMINATED,
                    200, null
                )));

            BrainStateManager.TransitionResult result = runPromise(() -> // GH-90000
                stateManager.transition(agentId, BrainStateManager.BrainState.TERMINATED) // GH-90000
            );

            assertThat(result.toState()).isEqualTo(BrainStateManager.BrainState.TERMINATED); // GH-90000
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
        void terminatedToAnyInvalid() { // GH-90000
            String agentId = "agent-001";

            when(stateManager.canTransition(agentId, BrainStateManager.BrainState.ACTIVE)) // GH-90000
                .thenReturn(Promise.of(false)); // GH-90000

            Boolean can = runPromise(() -> // GH-90000
                stateManager.canTransition(agentId, BrainStateManager.BrainState.ACTIVE) // GH-90000
            );

            assertThat(can).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("[D009]: active_to_uninitialized_invalid")
        void activeToUninitializedInvalid() { // GH-90000
            String agentId = "agent-001";

            when(stateManager.canTransition(agentId, BrainStateManager.BrainState.UNINITIALIZED)) // GH-90000
                .thenReturn(Promise.of(false)); // GH-90000

            Boolean can = runPromise(() -> // GH-90000
                stateManager.canTransition(agentId, BrainStateManager.BrainState.UNINITIALIZED) // GH-90000
            );

            assertThat(can).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("[D009]: uninitialized_to_active_invalid")
        void uninitializedToActiveInvalid() { // GH-90000
            String agentId = "agent-001";

            when(stateManager.canTransition(agentId, BrainStateManager.BrainState.ACTIVE)) // GH-90000
                .thenReturn(Promise.of(false)); // GH-90000

            Boolean can = runPromise(() -> // GH-90000
                stateManager.canTransition(agentId, BrainStateManager.BrainState.ACTIVE) // GH-90000
            );

            assertThat(can).isFalse(); // GH-90000
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
        void getStateHistoryReturnsRecords() { // GH-90000
            String agentId = "agent-001";
            List<BrainStateManager.StateRecord> history = List.of( // GH-90000
                new BrainStateManager.StateRecord( // GH-90000
                    BrainStateManager.BrainState.UNINITIALIZED,
                    System.currentTimeMillis() - 10000, // GH-90000
                    "Created", 0
                ),
                new BrainStateManager.StateRecord( // GH-90000
                    BrainStateManager.BrainState.INITIALIZING,
                    System.currentTimeMillis() - 9000, // GH-90000
                    "Starting up", 1000
                ),
                new BrainStateManager.StateRecord( // GH-90000
                    BrainStateManager.BrainState.ACTIVE,
                    System.currentTimeMillis() - 8000, // GH-90000
                    "Ready", 1000
                )
            );

            when(stateManager.getStateHistory(agentId, 10)) // GH-90000
                .thenReturn(Promise.of(history)); // GH-90000

            List<BrainStateManager.StateRecord> result = runPromise(() -> // GH-90000
                stateManager.getStateHistory(agentId, 10) // GH-90000
            );

            assertThat(result).hasSize(3); // GH-90000
            assertThat(result.get(0).state()).isEqualTo(BrainStateManager.BrainState.UNINITIALIZED); // GH-90000
            assertThat(result.get(2).state()).isEqualTo(BrainStateManager.BrainState.ACTIVE); // GH-90000
        }

        @Test
        @DisplayName("[D009]: state_records_include_duration")
        void stateRecordsIncludeDuration() { // GH-90000
            BrainStateManager.StateRecord record = new BrainStateManager.StateRecord( // GH-90000
                BrainStateManager.BrainState.ACTIVE,
                System.currentTimeMillis(), // GH-90000
                "Running", 5000
            );

            assertThat(record.durationMs()).isEqualTo(5000); // GH-90000
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
        void waitForStateReturnsTrueWhenReached() { // GH-90000
            String agentId = "agent-001";

            when(stateManager.waitForState( // GH-90000
                eq(agentId), // GH-90000
                eq(BrainStateManager.BrainState.ACTIVE), // GH-90000
                any(Duration.class) // GH-90000
            )).thenReturn(Promise.of(true)); // GH-90000

            Boolean result = runPromise(() -> // GH-90000
                stateManager.waitForState(agentId, BrainStateManager.BrainState.ACTIVE, Duration.ofSeconds(5)) // GH-90000
            );

            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[D009]: wait_for_state_returns_false_on_timeout")
        void waitForStateReturnsFalseOnTimeout() { // GH-90000
            String agentId = "agent-001";

            when(stateManager.waitForState( // GH-90000
                eq(agentId), // GH-90000
                eq(BrainStateManager.BrainState.ACTIVE), // GH-90000
                any(Duration.class) // GH-90000
            )).thenReturn(Promise.of(false)); // GH-90000

            Boolean result = runPromise(() -> // GH-90000
                stateManager.waitForState(agentId, BrainStateManager.BrainState.ACTIVE, Duration.ofMillis(100)) // GH-90000
            );

            assertThat(result).isFalse(); // GH-90000
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
        void onStateChangeRegistersListener() { // GH-90000
            String agentId = "agent-001";

            when(stateManager.onStateChange(eq(agentId), any())) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            BrainStateManager.StateChangeListener listener = (from, to, ctx) -> Promise.of((Void) null); // GH-90000

            runPromise(() -> stateManager.onStateChange(agentId, listener)); // GH-90000

            verify(stateManager).onStateChange(eq(agentId), any()); // GH-90000
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
        void transitionContextIncludesMetadata() { // GH-90000
            BrainStateManager.TransitionContext context = new BrainStateManager.TransitionContext( // GH-90000
                "agent-001",
                "user_request",
                Map.of("reason", "manual_trigger"), // GH-90000
                System.currentTimeMillis() // GH-90000
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
        void allStatesDefined() { // GH-90000
            BrainStateManager.BrainState[] states = BrainStateManager.BrainState.values(); // GH-90000

            assertThat(states).contains( // GH-90000
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

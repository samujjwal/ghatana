/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.release;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AgentReleaseState}.
 *
 * <p>Covers: all valid transitions, all invalid transitions, dispatchability semantics.
 *
 * @doc.type class
 * @doc.purpose Tests for AgentReleaseState state machine
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AgentReleaseState [GH-90000]")
class AgentReleaseStateTest {

    @Nested
    @DisplayName("Valid transitions [GH-90000]")
    class ValidTransitions {

        @Test
        void draftToValidated() { assertTransition(AgentReleaseState.DRAFT, AgentReleaseState.VALIDATED); } // GH-90000

        @Test
        void draftToBlocked() { assertTransition(AgentReleaseState.DRAFT, AgentReleaseState.BLOCKED); } // GH-90000

        @Test
        void validatedToShadow() { assertTransition(AgentReleaseState.VALIDATED, AgentReleaseState.SHADOW); } // GH-90000

        @Test
        void validatedToBlocked() { assertTransition(AgentReleaseState.VALIDATED, AgentReleaseState.BLOCKED); } // GH-90000

        @Test
        void validatedToDraft() { assertTransition(AgentReleaseState.VALIDATED, AgentReleaseState.DRAFT); } // GH-90000

        @Test
        void shadowToCanary() { assertTransition(AgentReleaseState.SHADOW, AgentReleaseState.CANARY); } // GH-90000

        @Test
        void shadowToBlocked() { assertTransition(AgentReleaseState.SHADOW, AgentReleaseState.BLOCKED); } // GH-90000

        @Test
        void canaryToActive() { assertTransition(AgentReleaseState.CANARY, AgentReleaseState.ACTIVE); } // GH-90000

        @Test
        void canaryToDeprecated() { assertTransition(AgentReleaseState.CANARY, AgentReleaseState.DEPRECATED); } // GH-90000

        @Test
        void canaryToBlocked() { assertTransition(AgentReleaseState.CANARY, AgentReleaseState.BLOCKED); } // GH-90000

        @Test
        void activeToDeprecated() { assertTransition(AgentReleaseState.ACTIVE, AgentReleaseState.DEPRECATED); } // GH-90000

        @Test
        void activeToBlocked() { assertTransition(AgentReleaseState.ACTIVE, AgentReleaseState.BLOCKED); } // GH-90000

        @Test
        void deprecatedToRetired() { assertTransition(AgentReleaseState.DEPRECATED, AgentReleaseState.RETIRED); } // GH-90000

        @Test
        void deprecatedToBlocked() { assertTransition(AgentReleaseState.DEPRECATED, AgentReleaseState.BLOCKED); } // GH-90000

        @Test
        void blockedToActive() { assertTransition(AgentReleaseState.BLOCKED, AgentReleaseState.ACTIVE); } // GH-90000

        @Test
        void blockedToValidated() { assertTransition(AgentReleaseState.BLOCKED, AgentReleaseState.VALIDATED); } // GH-90000

        private void assertTransition(AgentReleaseState from, AgentReleaseState to) { // GH-90000
            assertThat(from.canTransitionTo(to)).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Invalid transitions [GH-90000]")
    class InvalidTransitions {

        @Test
        void draftToActive() { assertNoTransition(AgentReleaseState.DRAFT, AgentReleaseState.ACTIVE); } // GH-90000

        @Test
        void draftToRetired() { assertNoTransition(AgentReleaseState.DRAFT, AgentReleaseState.RETIRED); } // GH-90000

        @Test
        void retiredToAnything() { // GH-90000
            for (AgentReleaseState target : AgentReleaseState.values()) { // GH-90000
                if (target != AgentReleaseState.RETIRED) { // GH-90000
                    assertNoTransition(AgentReleaseState.RETIRED, target); // GH-90000
                }
            }
        }

        @Test
        void shadowToActive() { assertNoTransition(AgentReleaseState.SHADOW, AgentReleaseState.ACTIVE); } // GH-90000

        @Test
        void activeToShadow() { assertNoTransition(AgentReleaseState.ACTIVE, AgentReleaseState.SHADOW); } // GH-90000

        @Test
        void activeToRetired() { assertNoTransition(AgentReleaseState.ACTIVE, AgentReleaseState.RETIRED); } // GH-90000

        private void assertNoTransition(AgentReleaseState from, AgentReleaseState to) { // GH-90000
            assertThat(from.canTransitionTo(to)) // GH-90000
                    .as("Expected no valid transition from %s to %s", from, to) // GH-90000
                    .isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Dispatchability [GH-90000]")
    class Dispatchability {

        @Test
        void activeIsDispatchable() { // GH-90000
            assertThat(AgentReleaseState.ACTIVE.isDispatchable()).isTrue(); // GH-90000
        }

        @Test
        void canaryIsDispatchable() { // GH-90000
            assertThat(AgentReleaseState.CANARY.isDispatchable()).isTrue(); // GH-90000
        }

        @Test
        void shadowIsDispatchable() { // GH-90000
            assertThat(AgentReleaseState.SHADOW.isDispatchable()).isTrue(); // GH-90000
        }

        @Test
        void draftIsNotDispatchable() { // GH-90000
            assertThat(AgentReleaseState.DRAFT.isDispatchable()).isFalse(); // GH-90000
        }

        @Test
        void validatedIsNotDispatchable() { // GH-90000
            assertThat(AgentReleaseState.VALIDATED.isDispatchable()).isFalse(); // GH-90000
        }

        @Test
        void blockedIsNotDispatchable() { // GH-90000
            assertThat(AgentReleaseState.BLOCKED.isDispatchable()).isFalse(); // GH-90000
        }

        @Test
        void retiredIsNotDispatchable() { // GH-90000
            assertThat(AgentReleaseState.RETIRED.isDispatchable()).isFalse(); // GH-90000
        }

        @Test
        void deprecatedIsNotDispatchable() { // GH-90000
            assertThat(AgentReleaseState.DEPRECATED.isDispatchable()).isFalse(); // GH-90000
        }
    }

    @Test
    @DisplayName("allowedTransitions returns non-null for all states [GH-90000]")
    void allowedTransitionsNonNull() { // GH-90000
        for (AgentReleaseState state : AgentReleaseState.values()) { // GH-90000
            assertThat(state.allowedTransitions()).isNotNull(); // GH-90000
        }
    }

    @Test
    @DisplayName("RETIRED has no allowed transitions [GH-90000]")
    void retiredHasNoTransitions() { // GH-90000
        assertThat(AgentReleaseState.RETIRED.allowedTransitions()).isEmpty(); // GH-90000
    }
}

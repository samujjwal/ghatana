/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("AgentReleaseState")
class AgentReleaseStateTest {

    @Nested
    @DisplayName("Valid transitions")
    class ValidTransitions {

        @Test
        void draftToValidated() { assertTransition(AgentReleaseState.DRAFT, AgentReleaseState.VALIDATED); } 

        @Test
        void draftToBlocked() { assertTransition(AgentReleaseState.DRAFT, AgentReleaseState.BLOCKED); } 

        @Test
        void validatedToShadow() { assertTransition(AgentReleaseState.VALIDATED, AgentReleaseState.SHADOW); } 

        @Test
        void validatedToBlocked() { assertTransition(AgentReleaseState.VALIDATED, AgentReleaseState.BLOCKED); } 

        @Test
        void validatedToDraft() { assertTransition(AgentReleaseState.VALIDATED, AgentReleaseState.DRAFT); } 

        @Test
        void shadowToCanary() { assertTransition(AgentReleaseState.SHADOW, AgentReleaseState.CANARY); } 

        @Test
        void shadowToBlocked() { assertTransition(AgentReleaseState.SHADOW, AgentReleaseState.BLOCKED); } 

        @Test
        void canaryToActive() { assertTransition(AgentReleaseState.CANARY, AgentReleaseState.ACTIVE); } 

        @Test
        void canaryToDeprecated() { assertTransition(AgentReleaseState.CANARY, AgentReleaseState.DEPRECATED); } 

        @Test
        void canaryToBlocked() { assertTransition(AgentReleaseState.CANARY, AgentReleaseState.BLOCKED); } 

        @Test
        void activeToDeprecated() { assertTransition(AgentReleaseState.ACTIVE, AgentReleaseState.DEPRECATED); } 

        @Test
        void activeToBlocked() { assertTransition(AgentReleaseState.ACTIVE, AgentReleaseState.BLOCKED); } 

        @Test
        void deprecatedToRetired() { assertTransition(AgentReleaseState.DEPRECATED, AgentReleaseState.RETIRED); } 

        @Test
        void deprecatedToBlocked() { assertTransition(AgentReleaseState.DEPRECATED, AgentReleaseState.BLOCKED); } 

        @Test
        void blockedToActive() { assertTransition(AgentReleaseState.BLOCKED, AgentReleaseState.ACTIVE); } 

        @Test
        void blockedToValidated() { assertTransition(AgentReleaseState.BLOCKED, AgentReleaseState.VALIDATED); } 

        private void assertTransition(AgentReleaseState from, AgentReleaseState to) { 
            assertThat(from.canTransitionTo(to)).isTrue(); 
        }
    }

    @Nested
    @DisplayName("Invalid transitions")
    class InvalidTransitions {

        @Test
        void draftToActive() { assertNoTransition(AgentReleaseState.DRAFT, AgentReleaseState.ACTIVE); } 

        @Test
        void draftToRetired() { assertNoTransition(AgentReleaseState.DRAFT, AgentReleaseState.RETIRED); } 

        @Test
        void retiredToAnything() { 
            for (AgentReleaseState target : AgentReleaseState.values()) { 
                if (target != AgentReleaseState.RETIRED) { 
                    assertNoTransition(AgentReleaseState.RETIRED, target); 
                }
            }
        }

        @Test
        void shadowToActive() { assertNoTransition(AgentReleaseState.SHADOW, AgentReleaseState.ACTIVE); } 

        @Test
        void activeToShadow() { assertNoTransition(AgentReleaseState.ACTIVE, AgentReleaseState.SHADOW); } 

        @Test
        void activeToRetired() { assertNoTransition(AgentReleaseState.ACTIVE, AgentReleaseState.RETIRED); } 

        private void assertNoTransition(AgentReleaseState from, AgentReleaseState to) { 
            assertThat(from.canTransitionTo(to)) 
                    .as("Expected no valid transition from %s to %s", from, to) 
                    .isFalse(); 
        }
    }

    @Nested
    @DisplayName("Dispatchability")
    class Dispatchability {

        @Test
        void activeIsDispatchable() { 
            assertThat(AgentReleaseState.ACTIVE.isDispatchable()).isTrue(); 
        }

        @Test
        void canaryIsDispatchable() { 
            assertThat(AgentReleaseState.CANARY.isDispatchable()).isTrue(); 
        }

        @Test
        void shadowIsDispatchable() { 
            assertThat(AgentReleaseState.SHADOW.isDispatchable()).isTrue(); 
        }

        @Test
        void draftIsNotDispatchable() { 
            assertThat(AgentReleaseState.DRAFT.isDispatchable()).isFalse(); 
        }

        @Test
        void validatedIsNotDispatchable() { 
            assertThat(AgentReleaseState.VALIDATED.isDispatchable()).isFalse(); 
        }

        @Test
        void blockedIsNotDispatchable() { 
            assertThat(AgentReleaseState.BLOCKED.isDispatchable()).isFalse(); 
        }

        @Test
        void retiredIsNotDispatchable() { 
            assertThat(AgentReleaseState.RETIRED.isDispatchable()).isFalse(); 
        }

        @Test
        void deprecatedIsNotDispatchable() { 
            assertThat(AgentReleaseState.DEPRECATED.isDispatchable()).isFalse(); 
        }
    }

    @Test
    @DisplayName("allowedTransitions returns non-null for all states")
    void allowedTransitionsNonNull() { 
        for (AgentReleaseState state : AgentReleaseState.values()) { 
            assertThat(state.allowedTransitions()).isNotNull(); 
        }
    }

    @Test
    @DisplayName("RETIRED has no allowed transitions")
    void retiredHasNoTransitions() { 
        assertThat(AgentReleaseState.RETIRED.allowedTransitions()).isEmpty(); 
    }
}

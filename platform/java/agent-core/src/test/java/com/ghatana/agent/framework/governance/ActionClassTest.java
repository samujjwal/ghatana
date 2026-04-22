/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.framework.governance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ActionClass}.
 */
@DisplayName("ActionClass [GH-90000]")
class ActionClassTest {

    @Nested
    @DisplayName("isPrivileged [GH-90000]")
    class IsPrivileged {

        @Test
        @DisplayName("READ and DRAFT are not privileged [GH-90000]")
        void readAndDraftNotPrivileged() { // GH-90000
            assertThat(ActionClass.READ.isPrivileged()).isFalse(); // GH-90000
            assertThat(ActionClass.DRAFT.isPrivileged()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("all other classes are privileged [GH-90000]")
        void otherClassesArePrivileged() { // GH-90000
            assertThat(ActionClass.WRITE_REVERSIBLE.isPrivileged()).isTrue(); // GH-90000
            assertThat(ActionClass.WRITE_IRREVERSIBLE.isPrivileged()).isTrue(); // GH-90000
            assertThat(ActionClass.CALL_EXTERNAL.isPrivileged()).isTrue(); // GH-90000
            assertThat(ActionClass.DELEGATE.isPrivileged()).isTrue(); // GH-90000
            assertThat(ActionClass.MEMORY_MUTATION.isPrivileged()).isTrue(); // GH-90000
            assertThat(ActionClass.POLICY_CHANGE.isPrivileged()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("isIrreversible [GH-90000]")
    class IsIrreversible {

        @Test
        @DisplayName("WRITE_IRREVERSIBLE, CALL_EXTERNAL, POLICY_CHANGE are irreversible [GH-90000]")
        void irreversibleClasses() { // GH-90000
            assertThat(ActionClass.WRITE_IRREVERSIBLE.isIrreversible()).isTrue(); // GH-90000
            assertThat(ActionClass.CALL_EXTERNAL.isIrreversible()).isTrue(); // GH-90000
            assertThat(ActionClass.POLICY_CHANGE.isIrreversible()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("READ, DRAFT, WRITE_REVERSIBLE, DELEGATE, MEMORY_MUTATION are reversible [GH-90000]")
        void reversibleClasses() { // GH-90000
            assertThat(ActionClass.READ.isIrreversible()).isFalse(); // GH-90000
            assertThat(ActionClass.DRAFT.isIrreversible()).isFalse(); // GH-90000
            assertThat(ActionClass.WRITE_REVERSIBLE.isIrreversible()).isFalse(); // GH-90000
            assertThat(ActionClass.DELEGATE.isIrreversible()).isFalse(); // GH-90000
            assertThat(ActionClass.MEMORY_MUTATION.isIrreversible()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("isLowRisk [GH-90000]")
    class IsLowRisk {

        @Test
        @DisplayName("READ and DRAFT are low risk [GH-90000]")
        void readAndDraftAreLowRisk() { // GH-90000
            assertThat(ActionClass.READ.isLowRisk()).isTrue(); // GH-90000
            assertThat(ActionClass.DRAFT.isLowRisk()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("all other classes are not low risk [GH-90000]")
        void otherClassesNotLowRisk() { // GH-90000
            assertThat(ActionClass.WRITE_REVERSIBLE.isLowRisk()).isFalse(); // GH-90000
            assertThat(ActionClass.WRITE_IRREVERSIBLE.isLowRisk()).isFalse(); // GH-90000
            assertThat(ActionClass.CALL_EXTERNAL.isLowRisk()).isFalse(); // GH-90000
            assertThat(ActionClass.DELEGATE.isLowRisk()).isFalse(); // GH-90000
            assertThat(ActionClass.MEMORY_MUTATION.isLowRisk()).isFalse(); // GH-90000
            assertThat(ActionClass.POLICY_CHANGE.isLowRisk()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isLowRisk is logically the inverse of isPrivileged [GH-90000]")
        void lowRiskIsInverseOfPrivileged() { // GH-90000
            for (ActionClass ac : ActionClass.values()) { // GH-90000
                assertThat(ac.isLowRisk()).isEqualTo(!ac.isPrivileged()); // GH-90000
            }
        }
    }

    @Test
    @DisplayName("8 canonical action classes defined [GH-90000]")
    void eightCanonicalClasses() { // GH-90000
        assertThat(ActionClass.values()).hasSize(8); // GH-90000
    }
}

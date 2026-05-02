/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("ActionClass")
class ActionClassTest {

    @Nested
    @DisplayName("isPrivileged")
    class IsPrivileged {

        @Test
        @DisplayName("READ and DRAFT are not privileged")
        void readAndDraftNotPrivileged() { 
            assertThat(ActionClass.READ.isPrivileged()).isFalse(); 
            assertThat(ActionClass.DRAFT.isPrivileged()).isFalse(); 
        }

        @Test
        @DisplayName("all other classes are privileged")
        void otherClassesArePrivileged() { 
            assertThat(ActionClass.WRITE_REVERSIBLE.isPrivileged()).isTrue(); 
            assertThat(ActionClass.WRITE_IRREVERSIBLE.isPrivileged()).isTrue(); 
            assertThat(ActionClass.CALL_EXTERNAL.isPrivileged()).isTrue(); 
            assertThat(ActionClass.DELEGATE.isPrivileged()).isTrue(); 
            assertThat(ActionClass.MEMORY_MUTATION.isPrivileged()).isTrue(); 
            assertThat(ActionClass.POLICY_CHANGE.isPrivileged()).isTrue(); 
        }
    }

    @Nested
    @DisplayName("isIrreversible")
    class IsIrreversible {

        @Test
        @DisplayName("WRITE_IRREVERSIBLE, CALL_EXTERNAL, POLICY_CHANGE are irreversible")
        void irreversibleClasses() { 
            assertThat(ActionClass.WRITE_IRREVERSIBLE.isIrreversible()).isTrue(); 
            assertThat(ActionClass.CALL_EXTERNAL.isIrreversible()).isTrue(); 
            assertThat(ActionClass.POLICY_CHANGE.isIrreversible()).isTrue(); 
        }

        @Test
        @DisplayName("READ, DRAFT, WRITE_REVERSIBLE, DELEGATE, MEMORY_MUTATION are reversible")
        void reversibleClasses() { 
            assertThat(ActionClass.READ.isIrreversible()).isFalse(); 
            assertThat(ActionClass.DRAFT.isIrreversible()).isFalse(); 
            assertThat(ActionClass.WRITE_REVERSIBLE.isIrreversible()).isFalse(); 
            assertThat(ActionClass.DELEGATE.isIrreversible()).isFalse(); 
            assertThat(ActionClass.MEMORY_MUTATION.isIrreversible()).isFalse(); 
        }
    }

    @Nested
    @DisplayName("isLowRisk")
    class IsLowRisk {

        @Test
        @DisplayName("READ and DRAFT are low risk")
        void readAndDraftAreLowRisk() { 
            assertThat(ActionClass.READ.isLowRisk()).isTrue(); 
            assertThat(ActionClass.DRAFT.isLowRisk()).isTrue(); 
        }

        @Test
        @DisplayName("all other classes are not low risk")
        void otherClassesNotLowRisk() { 
            assertThat(ActionClass.WRITE_REVERSIBLE.isLowRisk()).isFalse(); 
            assertThat(ActionClass.WRITE_IRREVERSIBLE.isLowRisk()).isFalse(); 
            assertThat(ActionClass.CALL_EXTERNAL.isLowRisk()).isFalse(); 
            assertThat(ActionClass.DELEGATE.isLowRisk()).isFalse(); 
            assertThat(ActionClass.MEMORY_MUTATION.isLowRisk()).isFalse(); 
            assertThat(ActionClass.POLICY_CHANGE.isLowRisk()).isFalse(); 
        }

        @Test
        @DisplayName("isLowRisk is logically the inverse of isPrivileged")
        void lowRiskIsInverseOfPrivileged() { 
            for (ActionClass ac : ActionClass.values()) { 
                assertThat(ac.isLowRisk()).isEqualTo(!ac.isPrivileged()); 
            }
        }
    }

    @Test
    @DisplayName("8 canonical action classes defined")
    void eightCanonicalClasses() { 
        assertThat(ActionClass.values()).hasSize(8); 
    }
}

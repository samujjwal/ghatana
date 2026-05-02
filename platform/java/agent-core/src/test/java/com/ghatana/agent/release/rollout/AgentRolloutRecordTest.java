/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.agent.release.rollout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AgentRolloutRecord} and {@link AgentRolloutApprovalState}.
 */
@DisplayName("AgentRolloutRecord and AgentRolloutApprovalState")
class AgentRolloutRecordTest {

    private static AgentRolloutRecord pending() { 
        return new AgentRolloutRecord( 
                "rollout-abc", "release-xyz", "tenant-1", "production",
                25, "release-xyz-prev",
                AgentRolloutApprovalState.PENDING,
                "dev@ghatana.ai", null, null, null,
                false,
                Instant.parse("2026-04-10T10:00:00Z"), null,
                Instant.parse("2026-04-11T10:00:00Z"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AgentRolloutApprovalState
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentRolloutApprovalState")
    class ApprovalStateTests {

        @Test
        @DisplayName("PENDING is the only pending state")
        void pendingIsPending() { 
            assertThat(AgentRolloutApprovalState.PENDING.isPending()).isTrue(); 
            assertThat(AgentRolloutApprovalState.APPROVED.isPending()).isFalse(); 
            assertThat(AgentRolloutApprovalState.REJECTED.isPending()).isFalse(); 
            assertThat(AgentRolloutApprovalState.EXPIRED.isPending()).isFalse(); 
            assertThat(AgentRolloutApprovalState.ROLLED_BACK.isPending()).isFalse(); 
        }

        @Test
        @DisplayName("terminal states exclude PENDING")
        void terminalStatesExcludePending() { 
            assertThat(AgentRolloutApprovalState.PENDING.isTerminal()).isFalse(); 
            assertThat(AgentRolloutApprovalState.APPROVED.isTerminal()).isTrue(); 
            assertThat(AgentRolloutApprovalState.REJECTED.isTerminal()).isTrue(); 
            assertThat(AgentRolloutApprovalState.EXPIRED.isTerminal()).isTrue(); 
            assertThat(AgentRolloutApprovalState.ROLLED_BACK.isTerminal()).isTrue(); 
        }

        @Test
        @DisplayName("5 canonical states defined")
        void fiveStates() { 
            assertThat(AgentRolloutApprovalState.values()).hasSize(5); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AgentRolloutRecord construction
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("construction validation")
    class ConstructionValidation {

        @Test
        @DisplayName("valid record constructs without error")
        void validRecordConstructs() { 
            AgentRolloutRecord r = pending(); 
            assertThat(r.rolloutId()).isEqualTo("rollout-abc");
            assertThat(r.agentReleaseId()).isEqualTo("release-xyz");
            assertThat(r.tenantId()).isEqualTo("tenant-1");
            assertThat(r.targetEnvironment()).isEqualTo("production");
            assertThat(r.trafficSplitPercent()).isEqualTo(25); 
            assertThat(r.approvalState()).isEqualTo(AgentRolloutApprovalState.PENDING); 
        }

        @Test
        @DisplayName("blank rolloutId is rejected")
        void blankRolloutIdRejected() { 
            assertThatThrownBy(() -> new AgentRolloutRecord( 
                    "", "release-xyz", "tenant-1", "production",
                    10, null, AgentRolloutApprovalState.PENDING,
                    "dev@ghatana.ai", null, null, null, false,
                    Instant.now(), null, Instant.now().plusSeconds(3600))) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("rolloutId");
        }

        @Test
        @DisplayName("blank agentReleaseId is rejected")
        void blankReleaseIdRejected() { 
            assertThatThrownBy(() -> new AgentRolloutRecord( 
                    "rollout-1", "", "tenant-1", "production",
                    10, null, AgentRolloutApprovalState.PENDING,
                    "dev@ghatana.ai", null, null, null, false,
                    Instant.now(), null, Instant.now().plusSeconds(3600))) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("agentReleaseId");
        }

        @Test
        @DisplayName("traffic split < 0 is rejected")
        void negativeSplitRejected() { 
            assertThatThrownBy(() -> new AgentRolloutRecord( 
                    "rollout-1", "release-1", "tenant-1", "production",
                    -1, null, AgentRolloutApprovalState.PENDING,
                    "dev@ghatana.ai", null, null, null, false,
                    Instant.now(), null, Instant.now().plusSeconds(3600))) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("trafficSplitPercent");
        }

        @Test
        @DisplayName("traffic split > 100 is rejected")
        void overHundredSplitRejected() { 
            assertThatThrownBy(() -> new AgentRolloutRecord( 
                    "rollout-1", "release-1", "tenant-1", "production",
                    101, null, AgentRolloutApprovalState.PENDING,
                    "dev@ghatana.ai", null, null, null, false,
                    Instant.now(), null, Instant.now().plusSeconds(3600))) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("trafficSplitPercent");
        }

        @Test
        @DisplayName("traffic split of 0 and 100 are valid boundaries")
        void splitBoundariesAreValid() { 
            // Should not throw
            new AgentRolloutRecord("r1", "rel-1", "t1", "env", 0, null, 
                    AgentRolloutApprovalState.PENDING, "dev", null, null, null, false,
                    Instant.now(), null, Instant.now().plusSeconds(3600)); 
            new AgentRolloutRecord("r2", "rel-1", "t1", "env", 100, null, 
                    AgentRolloutApprovalState.PENDING, "dev", null, null, null, false,
                    Instant.now(), null, Instant.now().plusSeconds(3600)); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // withApproved, withRejected, withRolledBack
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("state transitions on record")
    class StateTransitions {

        @Test
        @DisplayName("withApproved produces new record in APPROVED state")
        void withApprovedProducesNewRecord() { 
            AgentRolloutRecord r = pending(); 
            Instant decided = Instant.now(); 
            AgentRolloutRecord approved = r.withApproved("manager@ghatana.ai", decided); 

            assertThat(approved.approvalState()).isEqualTo(AgentRolloutApprovalState.APPROVED); 
            assertThat(approved.approvedBy()).isEqualTo("manager@ghatana.ai");
            assertThat(approved.decidedAt()).isEqualTo(decided); 
            assertThat(approved.rolloutId()).isEqualTo(r.rolloutId()); // same identity 
        }

        @Test
        @DisplayName("withRejected produces new record in REJECTED state with reason")
        void withRejectedProducesNewRecord() { 
            AgentRolloutRecord r = pending(); 
            AgentRolloutRecord rejected = r.withRejected("security@ghatana.ai", "CVE risk", Instant.now()); 

            assertThat(rejected.approvalState()).isEqualTo(AgentRolloutApprovalState.REJECTED); 
            assertThat(rejected.rejectedBy()).isEqualTo("security@ghatana.ai");
            assertThat(rejected.rejectedReason()).isEqualTo("CVE risk");
        }

        @Test
        @DisplayName("withRolledBack produces new record in ROLLED_BACK state")
        void withRolledBackProducesNewRecord() { 
            AgentRolloutRecord r = pending().withApproved("manager@ghatana.ai", Instant.now()); 
            AgentRolloutRecord rolled = r.withRolledBack("oncall@ghatana.ai", Instant.now()); 

            assertThat(rolled.approvalState()).isEqualTo(AgentRolloutApprovalState.ROLLED_BACK); 
            assertThat(rolled.rejectedBy()).isEqualTo("oncall@ghatana.ai");
        }

        @Test
        @DisplayName("original record is unchanged after state transition")
        void originalIsUnchanged() { 
            AgentRolloutRecord r = pending(); 
            r.withApproved("manager@ghatana.ai", Instant.now()); 
            assertThat(r.approvalState()).isEqualTo(AgentRolloutApprovalState.PENDING); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isActive
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isActive")
    class IsActive {

        @Test
        @DisplayName("APPROVED without kill-switch is active")
        void approvedWithoutKillswitchIsActive() { 
            AgentRolloutRecord approved = pending().withApproved("manager", Instant.now()); 
            assertThat(approved.isActive()).isTrue(); 
        }

        @Test
        @DisplayName("PENDING is not active")
        void pendingIsNotActive() { 
            assertThat(pending().isActive()).isFalse(); 
        }

        @Test
        @DisplayName("APPROVED with kill-switch is not active")
        void approvedWithKillswitchIsNotActive() { 
            AgentRolloutRecord withKillSwitch = new AgentRolloutRecord( 
                    "rollout-kill", "release-kill", "tenant-1", "production",
                    10, null, AgentRolloutApprovalState.APPROVED,
                    "dev@ghatana.ai", "manager@ghatana.ai", null, null,
                    true, // kill-switch ON
                    Instant.now(), Instant.now(), Instant.now().plusSeconds(3600)); 
            assertThat(withKillSwitch.isActive()).isFalse(); 
        }

        @Test
        @DisplayName("ROLLED_BACK is not active")
        void rolledBackIsNotActive() { 
            AgentRolloutRecord approved = pending().withApproved("manager", Instant.now()); 
            AgentRolloutRecord rolled = approved.withRolledBack("oncall", Instant.now()); 
            assertThat(rolled.isActive()).isFalse(); 
        }
    }
}

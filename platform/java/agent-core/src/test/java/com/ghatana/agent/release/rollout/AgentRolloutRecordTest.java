/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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

    private static AgentRolloutRecord pending() { // GH-90000
        return new AgentRolloutRecord( // GH-90000
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
        void pendingIsPending() { // GH-90000
            assertThat(AgentRolloutApprovalState.PENDING.isPending()).isTrue(); // GH-90000
            assertThat(AgentRolloutApprovalState.APPROVED.isPending()).isFalse(); // GH-90000
            assertThat(AgentRolloutApprovalState.REJECTED.isPending()).isFalse(); // GH-90000
            assertThat(AgentRolloutApprovalState.EXPIRED.isPending()).isFalse(); // GH-90000
            assertThat(AgentRolloutApprovalState.ROLLED_BACK.isPending()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("terminal states exclude PENDING")
        void terminalStatesExcludePending() { // GH-90000
            assertThat(AgentRolloutApprovalState.PENDING.isTerminal()).isFalse(); // GH-90000
            assertThat(AgentRolloutApprovalState.APPROVED.isTerminal()).isTrue(); // GH-90000
            assertThat(AgentRolloutApprovalState.REJECTED.isTerminal()).isTrue(); // GH-90000
            assertThat(AgentRolloutApprovalState.EXPIRED.isTerminal()).isTrue(); // GH-90000
            assertThat(AgentRolloutApprovalState.ROLLED_BACK.isTerminal()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("5 canonical states defined")
        void fiveStates() { // GH-90000
            assertThat(AgentRolloutApprovalState.values()).hasSize(5); // GH-90000
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
        void validRecordConstructs() { // GH-90000
            AgentRolloutRecord r = pending(); // GH-90000
            assertThat(r.rolloutId()).isEqualTo("rollout-abc");
            assertThat(r.agentReleaseId()).isEqualTo("release-xyz");
            assertThat(r.tenantId()).isEqualTo("tenant-1");
            assertThat(r.targetEnvironment()).isEqualTo("production");
            assertThat(r.trafficSplitPercent()).isEqualTo(25); // GH-90000
            assertThat(r.approvalState()).isEqualTo(AgentRolloutApprovalState.PENDING); // GH-90000
        }

        @Test
        @DisplayName("blank rolloutId is rejected")
        void blankRolloutIdRejected() { // GH-90000
            assertThatThrownBy(() -> new AgentRolloutRecord( // GH-90000
                    "", "release-xyz", "tenant-1", "production",
                    10, null, AgentRolloutApprovalState.PENDING,
                    "dev@ghatana.ai", null, null, null, false,
                    Instant.now(), null, Instant.now().plusSeconds(3600))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("rolloutId");
        }

        @Test
        @DisplayName("blank agentReleaseId is rejected")
        void blankReleaseIdRejected() { // GH-90000
            assertThatThrownBy(() -> new AgentRolloutRecord( // GH-90000
                    "rollout-1", "", "tenant-1", "production",
                    10, null, AgentRolloutApprovalState.PENDING,
                    "dev@ghatana.ai", null, null, null, false,
                    Instant.now(), null, Instant.now().plusSeconds(3600))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("agentReleaseId");
        }

        @Test
        @DisplayName("traffic split < 0 is rejected")
        void negativeSplitRejected() { // GH-90000
            assertThatThrownBy(() -> new AgentRolloutRecord( // GH-90000
                    "rollout-1", "release-1", "tenant-1", "production",
                    -1, null, AgentRolloutApprovalState.PENDING,
                    "dev@ghatana.ai", null, null, null, false,
                    Instant.now(), null, Instant.now().plusSeconds(3600))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("trafficSplitPercent");
        }

        @Test
        @DisplayName("traffic split > 100 is rejected")
        void overHundredSplitRejected() { // GH-90000
            assertThatThrownBy(() -> new AgentRolloutRecord( // GH-90000
                    "rollout-1", "release-1", "tenant-1", "production",
                    101, null, AgentRolloutApprovalState.PENDING,
                    "dev@ghatana.ai", null, null, null, false,
                    Instant.now(), null, Instant.now().plusSeconds(3600))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("trafficSplitPercent");
        }

        @Test
        @DisplayName("traffic split of 0 and 100 are valid boundaries")
        void splitBoundariesAreValid() { // GH-90000
            // Should not throw
            new AgentRolloutRecord("r1", "rel-1", "t1", "env", 0, null, // GH-90000
                    AgentRolloutApprovalState.PENDING, "dev", null, null, null, false,
                    Instant.now(), null, Instant.now().plusSeconds(3600)); // GH-90000
            new AgentRolloutRecord("r2", "rel-1", "t1", "env", 100, null, // GH-90000
                    AgentRolloutApprovalState.PENDING, "dev", null, null, null, false,
                    Instant.now(), null, Instant.now().plusSeconds(3600)); // GH-90000
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
        void withApprovedProducesNewRecord() { // GH-90000
            AgentRolloutRecord r = pending(); // GH-90000
            Instant decided = Instant.now(); // GH-90000
            AgentRolloutRecord approved = r.withApproved("manager@ghatana.ai", decided); // GH-90000

            assertThat(approved.approvalState()).isEqualTo(AgentRolloutApprovalState.APPROVED); // GH-90000
            assertThat(approved.approvedBy()).isEqualTo("manager@ghatana.ai");
            assertThat(approved.decidedAt()).isEqualTo(decided); // GH-90000
            assertThat(approved.rolloutId()).isEqualTo(r.rolloutId()); // same identity // GH-90000
        }

        @Test
        @DisplayName("withRejected produces new record in REJECTED state with reason")
        void withRejectedProducesNewRecord() { // GH-90000
            AgentRolloutRecord r = pending(); // GH-90000
            AgentRolloutRecord rejected = r.withRejected("security@ghatana.ai", "CVE risk", Instant.now()); // GH-90000

            assertThat(rejected.approvalState()).isEqualTo(AgentRolloutApprovalState.REJECTED); // GH-90000
            assertThat(rejected.rejectedBy()).isEqualTo("security@ghatana.ai");
            assertThat(rejected.rejectedReason()).isEqualTo("CVE risk");
        }

        @Test
        @DisplayName("withRolledBack produces new record in ROLLED_BACK state")
        void withRolledBackProducesNewRecord() { // GH-90000
            AgentRolloutRecord r = pending().withApproved("manager@ghatana.ai", Instant.now()); // GH-90000
            AgentRolloutRecord rolled = r.withRolledBack("oncall@ghatana.ai", Instant.now()); // GH-90000

            assertThat(rolled.approvalState()).isEqualTo(AgentRolloutApprovalState.ROLLED_BACK); // GH-90000
            assertThat(rolled.rejectedBy()).isEqualTo("oncall@ghatana.ai");
        }

        @Test
        @DisplayName("original record is unchanged after state transition")
        void originalIsUnchanged() { // GH-90000
            AgentRolloutRecord r = pending(); // GH-90000
            r.withApproved("manager@ghatana.ai", Instant.now()); // GH-90000
            assertThat(r.approvalState()).isEqualTo(AgentRolloutApprovalState.PENDING); // GH-90000
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
        void approvedWithoutKillswitchIsActive() { // GH-90000
            AgentRolloutRecord approved = pending().withApproved("manager", Instant.now()); // GH-90000
            assertThat(approved.isActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("PENDING is not active")
        void pendingIsNotActive() { // GH-90000
            assertThat(pending().isActive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("APPROVED with kill-switch is not active")
        void approvedWithKillswitchIsNotActive() { // GH-90000
            AgentRolloutRecord withKillSwitch = new AgentRolloutRecord( // GH-90000
                    "rollout-kill", "release-kill", "tenant-1", "production",
                    10, null, AgentRolloutApprovalState.APPROVED,
                    "dev@ghatana.ai", "manager@ghatana.ai", null, null,
                    true, // kill-switch ON
                    Instant.now(), Instant.now(), Instant.now().plusSeconds(3600)); // GH-90000
            assertThat(withKillSwitch.isActive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("ROLLED_BACK is not active")
        void rolledBackIsNotActive() { // GH-90000
            AgentRolloutRecord approved = pending().withApproved("manager", Instant.now()); // GH-90000
            AgentRolloutRecord rolled = approved.withRolledBack("oncall", Instant.now()); // GH-90000
            assertThat(rolled.isActive()).isFalse(); // GH-90000
        }
    }
}

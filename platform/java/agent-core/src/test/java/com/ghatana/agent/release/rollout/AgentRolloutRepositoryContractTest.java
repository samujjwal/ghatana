/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.release.rollout;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract tests for {@link AgentRolloutRepository}.
 *
 * <p>All repository implementations must pass these tests to ensure behavioral consistency.
 */
@DisplayName("AgentRolloutRepository Contract")
class AgentRolloutRepositoryContractTest extends EventloopTestBase {

    private AgentRolloutRepository repo;

    @BeforeEach
    void setUp() { // GH-90000
        repo = new InMemoryAgentRolloutRepository(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private AgentRolloutRecord pendingRollout(String rolloutId, String releaseId) { // GH-90000
        return new AgentRolloutRecord( // GH-90000
                rolloutId, releaseId, "tenant-1", "production",
                10, null,
                AgentRolloutApprovalState.PENDING,
                "dev@ghatana.ai", null, null, null,
                false,
                Instant.now(), null, // GH-90000
                Instant.now().plusSeconds(3600)); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // save + findById
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save and findById")
    class SaveAndFind {

        @Test
        @DisplayName("saves and retrieves by ID")
        void savesAndRetrievesById() { // GH-90000
            AgentRolloutRecord r = pendingRollout("rollout-1", "release-100"); // GH-90000
            runPromise(() -> repo.save(r)); // GH-90000

            Optional<AgentRolloutRecord> found = runPromise(() -> repo.findById("rollout-1"));

            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().rolloutId()).isEqualTo("rollout-1");
            assertThat(found.get().approvalState()).isEqualTo(AgentRolloutApprovalState.PENDING); // GH-90000
        }

        @Test
        @DisplayName("returns empty for unknown rollout ID")
        void emptyForUnknownId() { // GH-90000
            Optional<AgentRolloutRecord> found = runPromise(() -> repo.findById("no-such-id"));
            assertThat(found).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("save is idempotent — last write wins")
        void saveIsIdempotent() { // GH-90000
            AgentRolloutRecord r1 = pendingRollout("rollout-idem", "release-200"); // GH-90000
            AgentRolloutRecord r2 = r1.withApproved("admin@ghatana.ai", Instant.now()); // GH-90000
            runPromise(() -> repo.save(r1)); // GH-90000
            runPromise(() -> repo.save(r2)); // GH-90000

            Optional<AgentRolloutRecord> found = runPromise(() -> repo.findById("rollout-idem"));
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().approvalState()).isEqualTo(AgentRolloutApprovalState.APPROVED); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByReleaseId
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByReleaseId")
    class FindByReleaseId {

        @Test
        @DisplayName("returns all rollouts for a release")
        void returnsAllForRelease() { // GH-90000
            runPromise(() -> repo.save(pendingRollout("r1", "rel-A"))); // GH-90000
            runPromise(() -> repo.save(pendingRollout("r2", "rel-A"))); // GH-90000
            runPromise(() -> repo.save(pendingRollout("r3", "rel-B"))); // GH-90000

            List<AgentRolloutRecord> found = runPromise(() -> repo.findByReleaseId("rel-A"));
            assertThat(found).hasSize(2).allMatch(r -> "rel-A".equals(r.agentReleaseId())); // GH-90000
        }

        @Test
        @DisplayName("returns empty for unknown release")
        void emptyForUnknownRelease() { // GH-90000
            List<AgentRolloutRecord> found = runPromise(() -> repo.findByReleaseId("no-such-release"));
            assertThat(found).isEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByTenantAndEnvironment
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByTenantAndEnvironment")
    class FindByTenantAndEnvironment {

        @Test
        @DisplayName("returns rollouts scoped to tenant and environment")
        void returnsScopedRollouts() { // GH-90000
            runPromise(() -> repo.save(pendingRollout("env-1", "rel-X"))); // GH-90000

            AgentRolloutRecord staging = new AgentRolloutRecord( // GH-90000
                    "env-2", "rel-Y", "tenant-1", "staging",
                    5, null, AgentRolloutApprovalState.PENDING,
                    "dev@ghatana.ai", null, null, null, false,
                    Instant.now(), null, Instant.now().plusSeconds(3600)); // GH-90000
            runPromise(() -> repo.save(staging)); // GH-90000

            List<AgentRolloutRecord> prod = runPromise(() -> // GH-90000
                    repo.findByTenantAndEnvironment("tenant-1", "production")); // GH-90000
            assertThat(prod).hasSize(1).allMatch(r -> "production".equals(r.targetEnvironment())); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // approve
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("transitions PENDING to APPROVED")
        void approvesFromPending() { // GH-90000
            AgentRolloutRecord r = pendingRollout("approve-1", "rel-300"); // GH-90000
            runPromise(() -> repo.save(r)); // GH-90000

            AgentRolloutRecord approved = runPromise(() -> // GH-90000
                    repo.approve("approve-1", "manager@ghatana.ai")); // GH-90000

            assertThat(approved.approvalState()).isEqualTo(AgentRolloutApprovalState.APPROVED); // GH-90000
            assertThat(approved.approvedBy()).isEqualTo("manager@ghatana.ai");
            assertThat(approved.decidedAt()).isNotNull(); // GH-90000
            assertThat(approved.isActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("fails when already APPROVED")
        void failsWhenAlreadyApproved() { // GH-90000
            AgentRolloutRecord r = pendingRollout("approve-2", "rel-301"); // GH-90000
            runPromise(() -> repo.save(r)); // GH-90000
            runPromise(() -> repo.approve("approve-2", "manager@ghatana.ai")); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    repo.approve("approve-2", "manager@ghatana.ai"))) // GH-90000
                    .hasMessageContaining("PENDING");
        }

        @Test
        @DisplayName("fails for unknown rollout ID")
        void failsForUnknown() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    repo.approve("no-such", "manager@ghatana.ai"))) // GH-90000
                    .hasMessageContaining("no-such");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // reject
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        @DisplayName("transitions PENDING to REJECTED with reason")
        void rejectsFromPending() { // GH-90000
            AgentRolloutRecord r = pendingRollout("reject-1", "rel-400"); // GH-90000
            runPromise(() -> repo.save(r)); // GH-90000

            AgentRolloutRecord rejected = runPromise(() -> // GH-90000
                    repo.reject("reject-1", "security@ghatana.ai", "Policy violation")); // GH-90000

            assertThat(rejected.approvalState()).isEqualTo(AgentRolloutApprovalState.REJECTED); // GH-90000
            assertThat(rejected.rejectedBy()).isEqualTo("security@ghatana.ai");
            assertThat(rejected.rejectedReason()).isEqualTo("Policy violation");
            assertThat(rejected.isActive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("fails when not PENDING")
        void failsWhenNotPending() { // GH-90000
            AgentRolloutRecord r = pendingRollout("reject-2", "rel-401"); // GH-90000
            runPromise(() -> repo.save(r)); // GH-90000
            runPromise(() -> repo.approve("reject-2", "manager@ghatana.ai")); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    repo.reject("reject-2", "security@ghatana.ai", "late rejection"))) // GH-90000
                    .hasMessageContaining("PENDING");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // rollback
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rollback")
    class Rollback {

        @Test
        @DisplayName("transitions APPROVED to ROLLED_BACK")
        void rollsBackApproved() { // GH-90000
            AgentRolloutRecord r = pendingRollout("rollback-1", "rel-500"); // GH-90000
            runPromise(() -> repo.save(r)); // GH-90000
            runPromise(() -> repo.approve("rollback-1", "prod@ghatana.ai")); // GH-90000

            AgentRolloutRecord rolled = runPromise(() -> // GH-90000
                    repo.rollback("rollback-1", "oncall@ghatana.ai")); // GH-90000

            assertThat(rolled.approvalState()).isEqualTo(AgentRolloutApprovalState.ROLLED_BACK); // GH-90000
            assertThat(rolled.rejectedBy()).isEqualTo("oncall@ghatana.ai");
            assertThat(rolled.isActive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("fails when not APPROVED")
        void failsWhenNotApproved() { // GH-90000
            AgentRolloutRecord r = pendingRollout("rollback-2", "rel-501"); // GH-90000
            runPromise(() -> repo.save(r)); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    repo.rollback("rollback-2", "oncall@ghatana.ai"))) // GH-90000
                    .hasMessageContaining("APPROVED");
        }

        @Test
        @DisplayName("fails for unknown rollout ID")
        void failsForUnknown() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    repo.rollback("no-such", "oncall@ghatana.ai"))) // GH-90000
                    .hasMessageContaining("no-such");
        }
    }
}

/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void setUp() {
        repo = new InMemoryAgentRolloutRepository();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private AgentRolloutRecord pendingRollout(String rolloutId, String releaseId) {
        return new AgentRolloutRecord(
                rolloutId, releaseId, "tenant-1", "production",
                10, null,
                AgentRolloutApprovalState.PENDING,
                "dev@ghatana.ai", null, null, null,
                false,
                Instant.now(), null,
                Instant.now().plusSeconds(3600));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // save + findById
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save and findById")
    class SaveAndFind {

        @Test
        @DisplayName("saves and retrieves by ID")
        void savesAndRetrievesById() {
            AgentRolloutRecord r = pendingRollout("rollout-1", "release-100");
            runPromise(() -> repo.save(r));

            Optional<AgentRolloutRecord> found = runPromise(() -> repo.findById("rollout-1"));

            assertThat(found).isPresent();
            assertThat(found.get().rolloutId()).isEqualTo("rollout-1");
            assertThat(found.get().approvalState()).isEqualTo(AgentRolloutApprovalState.PENDING);
        }

        @Test
        @DisplayName("returns empty for unknown rollout ID")
        void emptyForUnknownId() {
            Optional<AgentRolloutRecord> found = runPromise(() -> repo.findById("no-such-id"));
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("save is idempotent — last write wins")
        void saveIsIdempotent() {
            AgentRolloutRecord r1 = pendingRollout("rollout-idem", "release-200");
            AgentRolloutRecord r2 = r1.withApproved("admin@ghatana.ai", Instant.now());
            runPromise(() -> repo.save(r1));
            runPromise(() -> repo.save(r2));

            Optional<AgentRolloutRecord> found = runPromise(() -> repo.findById("rollout-idem"));
            assertThat(found).isPresent();
            assertThat(found.get().approvalState()).isEqualTo(AgentRolloutApprovalState.APPROVED);
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
        void returnsAllForRelease() {
            runPromise(() -> repo.save(pendingRollout("r1", "rel-A")));
            runPromise(() -> repo.save(pendingRollout("r2", "rel-A")));
            runPromise(() -> repo.save(pendingRollout("r3", "rel-B")));

            List<AgentRolloutRecord> found = runPromise(() -> repo.findByReleaseId("rel-A"));
            assertThat(found).hasSize(2).allMatch(r -> "rel-A".equals(r.agentReleaseId()));
        }

        @Test
        @DisplayName("returns empty for unknown release")
        void emptyForUnknownRelease() {
            List<AgentRolloutRecord> found = runPromise(() -> repo.findByReleaseId("no-such-release"));
            assertThat(found).isEmpty();
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
        void returnsScopedRollouts() {
            runPromise(() -> repo.save(pendingRollout("env-1", "rel-X")));

            AgentRolloutRecord staging = new AgentRolloutRecord(
                    "env-2", "rel-Y", "tenant-1", "staging",
                    5, null, AgentRolloutApprovalState.PENDING,
                    "dev@ghatana.ai", null, null, null, false,
                    Instant.now(), null, Instant.now().plusSeconds(3600));
            runPromise(() -> repo.save(staging));

            List<AgentRolloutRecord> prod = runPromise(() ->
                    repo.findByTenantAndEnvironment("tenant-1", "production"));
            assertThat(prod).hasSize(1).allMatch(r -> "production".equals(r.targetEnvironment()));
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
        void approvesFromPending() {
            AgentRolloutRecord r = pendingRollout("approve-1", "rel-300");
            runPromise(() -> repo.save(r));

            AgentRolloutRecord approved = runPromise(() ->
                    repo.approve("approve-1", "manager@ghatana.ai"));

            assertThat(approved.approvalState()).isEqualTo(AgentRolloutApprovalState.APPROVED);
            assertThat(approved.approvedBy()).isEqualTo("manager@ghatana.ai");
            assertThat(approved.decidedAt()).isNotNull();
            assertThat(approved.isActive()).isTrue();
        }

        @Test
        @DisplayName("fails when already APPROVED")
        void failsWhenAlreadyApproved() {
            AgentRolloutRecord r = pendingRollout("approve-2", "rel-301");
            runPromise(() -> repo.save(r));
            runPromise(() -> repo.approve("approve-2", "manager@ghatana.ai"));

            assertThatThrownBy(() -> runPromise(() ->
                    repo.approve("approve-2", "manager@ghatana.ai")))
                    .hasMessageContaining("PENDING");
        }

        @Test
        @DisplayName("fails for unknown rollout ID")
        void failsForUnknown() {
            assertThatThrownBy(() -> runPromise(() ->
                    repo.approve("no-such", "manager@ghatana.ai")))
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
        void rejectsFromPending() {
            AgentRolloutRecord r = pendingRollout("reject-1", "rel-400");
            runPromise(() -> repo.save(r));

            AgentRolloutRecord rejected = runPromise(() ->
                    repo.reject("reject-1", "security@ghatana.ai", "Policy violation"));

            assertThat(rejected.approvalState()).isEqualTo(AgentRolloutApprovalState.REJECTED);
            assertThat(rejected.rejectedBy()).isEqualTo("security@ghatana.ai");
            assertThat(rejected.rejectedReason()).isEqualTo("Policy violation");
            assertThat(rejected.isActive()).isFalse();
        }

        @Test
        @DisplayName("fails when not PENDING")
        void failsWhenNotPending() {
            AgentRolloutRecord r = pendingRollout("reject-2", "rel-401");
            runPromise(() -> repo.save(r));
            runPromise(() -> repo.approve("reject-2", "manager@ghatana.ai"));

            assertThatThrownBy(() -> runPromise(() ->
                    repo.reject("reject-2", "security@ghatana.ai", "late rejection")))
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
        void rollsBackApproved() {
            AgentRolloutRecord r = pendingRollout("rollback-1", "rel-500");
            runPromise(() -> repo.save(r));
            runPromise(() -> repo.approve("rollback-1", "prod@ghatana.ai"));

            AgentRolloutRecord rolled = runPromise(() ->
                    repo.rollback("rollback-1", "oncall@ghatana.ai"));

            assertThat(rolled.approvalState()).isEqualTo(AgentRolloutApprovalState.ROLLED_BACK);
            assertThat(rolled.rejectedBy()).isEqualTo("oncall@ghatana.ai");
            assertThat(rolled.isActive()).isFalse();
        }

        @Test
        @DisplayName("fails when not APPROVED")
        void failsWhenNotApproved() {
            AgentRolloutRecord r = pendingRollout("rollback-2", "rel-501");
            runPromise(() -> repo.save(r));

            assertThatThrownBy(() -> runPromise(() ->
                    repo.rollback("rollback-2", "oncall@ghatana.ai")))
                    .hasMessageContaining("APPROVED");
        }

        @Test
        @DisplayName("fails for unknown rollout ID")
        void failsForUnknown() {
            assertThatThrownBy(() -> runPromise(() ->
                    repo.rollback("no-such", "oncall@ghatana.ai")))
                    .hasMessageContaining("no-such");
        }
    }
}

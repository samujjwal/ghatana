/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.release.rollout.AgentRolloutApprovalState;
import com.ghatana.agent.release.rollout.AgentRolloutRecord;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DataCloudAgentRolloutRepository}.
 *
 * <p>Verifies delegation to {@link DataCloudClient}, serialization/deserialization,
 * and state-transition methods.
 *
 * @doc.type class
 * @doc.purpose Tests for DataCloudAgentRolloutRepository
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudAgentRolloutRepository [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class DataCloudAgentRolloutRepositoryTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-rollout-test";

    @Mock
    private DataCloudClient dataCloud;

    @Mock
    private EntityInterface mockEntity;

    private DataCloudAgentRolloutRepository repo;

    @BeforeEach
    void setUp() { // GH-90000
        repo = new DataCloudAgentRolloutRepository(dataCloud, TENANT_ID); // GH-90000
    }

    // ─────────────────── helpers ──────────────────────────────────────────────

    private AgentRolloutRecord pending(String rolloutId, String releaseId) { // GH-90000
        return new AgentRolloutRecord( // GH-90000
                rolloutId, releaseId, TENANT_ID, "production",
                15, null,
                AgentRolloutApprovalState.PENDING,
                "dev@ghatana.ai", null, null, null,
                false,
                Instant.parse("2026-04-10T09:00:00Z [GH-90000]"), null,
                Instant.parse("2026-04-11T09:00:00Z [GH-90000]"));
    }

    private Map<String, Object> toRolloutMap(AgentRolloutRecord r) { // GH-90000
        Map<String, Object> m = new HashMap<>(); // GH-90000
        m.put("rolloutId",          r.rolloutId()); // GH-90000
        m.put("agentReleaseId",     r.agentReleaseId()); // GH-90000
        m.put("tenantId",           r.tenantId()); // GH-90000
        m.put("targetEnvironment",  r.targetEnvironment()); // GH-90000
        m.put("trafficSplitPercent", r.trafficSplitPercent()); // GH-90000
        m.put("approvalState",      r.approvalState().name()); // GH-90000
        m.put("requestedBy",        r.requestedBy()); // GH-90000
        m.put("killSwitch",         r.killSwitch()); // GH-90000
        m.put("requestedAt",        r.requestedAt().toString()); // GH-90000
        if (r.expiresAt() != null) m.put("expiresAt", r.expiresAt().toString()); // GH-90000
        if (r.approvedBy() != null) m.put("approvedBy", r.approvedBy()); // GH-90000
        if (r.rejectedBy() != null) m.put("rejectedBy", r.rejectedBy()); // GH-90000
        if (r.rejectedReason() != null) m.put("rejectedReason", r.rejectedReason()); // GH-90000
        if (r.decidedAt() != null) m.put("decidedAt", r.decidedAt().toString()); // GH-90000
        return m;
    }

    // ─────────────────── tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("save [GH-90000]")
    class Save {

        @Test
        @DisplayName("delegates to DataCloudClient.createEntity [GH-90000]")
        void delegatesToDataCloud() { // GH-90000
            AgentRolloutRecord r = pending("rollout-dc-1", "rel-100"); // GH-90000
            when(dataCloud.createEntity(eq(TENANT_ID), eq(DataCloudAgentRolloutRepository.COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(mockEntity)); // GH-90000

            AgentRolloutRecord saved = runPromise(() -> repo.save(r)); // GH-90000

            assertThat(saved.rolloutId()).isEqualTo("rollout-dc-1 [GH-90000]");
            verify(dataCloud).createEntity(eq(TENANT_ID), eq(DataCloudAgentRolloutRepository.COLLECTION), any()); // GH-90000
        }
    }

    @Nested
    @DisplayName("findById [GH-90000]")
    class FindById {

        @Test
        @DisplayName("returns present when DataCloud returns matching entity [GH-90000]")
        void returnsPresentWhenFound() { // GH-90000
            AgentRolloutRecord r = pending("rollout-dc-2", "rel-200"); // GH-90000
            Map<String, Object> dataMap = toRolloutMap(r); // GH-90000
            when(mockEntity.getData()).thenReturn(dataMap); // GH-90000

            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudAgentRolloutRepository.COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(mockEntity))); // GH-90000

            Optional<AgentRolloutRecord> found = runPromise(() -> repo.findById("rollout-dc-2 [GH-90000]"));

            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().rolloutId()).isEqualTo("rollout-dc-2 [GH-90000]");
            assertThat(found.get().approvalState()).isEqualTo(AgentRolloutApprovalState.PENDING); // GH-90000
        }

        @Test
        @DisplayName("returns empty when DataCloud returns no entities [GH-90000]")
        void returnsEmptyWhenNotFound() { // GH-90000
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudAgentRolloutRepository.COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            Optional<AgentRolloutRecord> found = runPromise(() -> repo.findById("no-such-rollout [GH-90000]"));
            assertThat(found).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("findByReleaseId [GH-90000]")
    class FindByReleaseId {

        @Test
        @DisplayName("returns list from DataCloud query [GH-90000]")
        void returnsListFromDataCloud() { // GH-90000
            AgentRolloutRecord r = pending("rollout-dc-3", "rel-300"); // GH-90000
            EntityInterface e1 = mock(EntityInterface.class); // GH-90000
            EntityInterface e2 = mock(EntityInterface.class); // GH-90000
            when(e1.getData()).thenReturn(toRolloutMap(r)); // GH-90000
            when(e2.getData()).thenReturn(toRolloutMap(r)); // GH-90000

            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudAgentRolloutRepository.COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(e1, e2))); // GH-90000

            List<AgentRolloutRecord> result = runPromise(() -> repo.findByReleaseId("rel-300 [GH-90000]"));
            assertThat(result).hasSize(2); // GH-90000
        }
    }

    @Nested
    @DisplayName("approve [GH-90000]")
    class Approve {

        @Test
        @DisplayName("saves APPROVED record after finding PENDING one [GH-90000]")
        void approvesRollout() { // GH-90000
            AgentRolloutRecord r = pending("rollout-approve", "rel-400"); // GH-90000
            when(mockEntity.getData()).thenReturn(toRolloutMap(r)); // GH-90000

            // first query returns the PENDING record, second createEntity returns null entity
            when(dataCloud.queryEntities(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(mockEntity))); // GH-90000
            when(dataCloud.createEntity(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(mock(EntityInterface.class))); // GH-90000

            AgentRolloutRecord approved = runPromise(() -> // GH-90000
                    repo.approve("rollout-approve", "manager@ghatana.ai")); // GH-90000

            assertThat(approved.approvalState()).isEqualTo(AgentRolloutApprovalState.APPROVED); // GH-90000
            assertThat(approved.approvedBy()).isEqualTo("manager@ghatana.ai [GH-90000]");
        }
    }

    @Nested
    @DisplayName("reject [GH-90000]")
    class Reject {

        @Test
        @DisplayName("saves REJECTED record with reason [GH-90000]")
        void rejectsRollout() { // GH-90000
            AgentRolloutRecord r = pending("rollout-reject", "rel-500"); // GH-90000
            when(mockEntity.getData()).thenReturn(toRolloutMap(r)); // GH-90000

            when(dataCloud.queryEntities(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(mockEntity))); // GH-90000
            when(dataCloud.createEntity(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(mock(EntityInterface.class))); // GH-90000

            AgentRolloutRecord rejected = runPromise(() -> // GH-90000
                    repo.reject("rollout-reject", "security@ghatana.ai", "Policy violation")); // GH-90000

            assertThat(rejected.approvalState()).isEqualTo(AgentRolloutApprovalState.REJECTED); // GH-90000
            assertThat(rejected.rejectedBy()).isEqualTo("security@ghatana.ai [GH-90000]");
            assertThat(rejected.rejectedReason()).isEqualTo("Policy violation [GH-90000]");
        }
    }

    @Nested
    @DisplayName("rollback [GH-90000]")
    class Rollback {

        @Test
        @DisplayName("saves ROLLED_BACK record from APPROVED state [GH-90000]")
        void rollsBackRollout() { // GH-90000
            AgentRolloutRecord approved = pending("rollout-rollback", "rel-600") // GH-90000
                    .withApproved("manager@ghatana.ai", Instant.now()); // GH-90000

            EntityInterface approvedEntity = mock(EntityInterface.class); // GH-90000
            when(approvedEntity.getData()).thenReturn(toRolloutMap(approved)); // GH-90000

            when(dataCloud.queryEntities(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(approvedEntity))); // GH-90000
            when(dataCloud.createEntity(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(mock(EntityInterface.class))); // GH-90000

            AgentRolloutRecord rolled = runPromise(() -> // GH-90000
                    repo.rollback("rollout-rollback", "oncall@ghatana.ai")); // GH-90000

            assertThat(rolled.approvalState()).isEqualTo(AgentRolloutApprovalState.ROLLED_BACK); // GH-90000
        }
    }
}

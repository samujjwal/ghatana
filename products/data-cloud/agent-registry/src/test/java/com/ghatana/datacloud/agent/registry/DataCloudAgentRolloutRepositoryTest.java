/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.release.rollout.AgentRolloutApprovalState;
import com.ghatana.agent.release.rollout.AgentRolloutRecord;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
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
import java.util.UUID;

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
@DisplayName("DataCloudAgentRolloutRepository")
@ExtendWith(MockitoExtension.class)
class DataCloudAgentRolloutRepositoryTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-rollout-test";

    @Mock
    private DataCloudClient dataCloud;

    @Mock
    private EntityInterface mockEntity;

    private DataCloudAgentRolloutRepository repo;

    @BeforeEach
    void setUp() {
        repo = new DataCloudAgentRolloutRepository(dataCloud, TENANT_ID);
    }

    // ─────────────────── helpers ──────────────────────────────────────────────

    private AgentRolloutRecord pending(String rolloutId, String releaseId) {
        return new AgentRolloutRecord(
                rolloutId, releaseId, TENANT_ID, "production",
                15, null,
                AgentRolloutApprovalState.PENDING,
                "dev@ghatana.ai", null, null, null,
                false,
                Instant.parse("2026-04-10T09:00:00Z"), null,
                Instant.parse("2026-04-11T09:00:00Z"));
    }

    private Map<String, Object> toRolloutMap(AgentRolloutRecord r) {
        Map<String, Object> m = new HashMap<>();
        m.put("rolloutId",          r.rolloutId());
        m.put("agentReleaseId",     r.agentReleaseId());
        m.put("tenantId",           r.tenantId());
        m.put("targetEnvironment",  r.targetEnvironment());
        m.put("trafficSplitPercent", r.trafficSplitPercent());
        m.put("approvalState",      r.approvalState().name());
        m.put("requestedBy",        r.requestedBy());
        m.put("killSwitch",         r.killSwitch());
        m.put("requestedAt",        r.requestedAt().toString());
        if (r.expiresAt() != null) m.put("expiresAt", r.expiresAt().toString());
        if (r.approvedBy() != null) m.put("approvedBy", r.approvedBy());
        if (r.rejectedBy() != null) m.put("rejectedBy", r.rejectedBy());
        if (r.rejectedReason() != null) m.put("rejectedReason", r.rejectedReason());
        if (r.decidedAt() != null) m.put("decidedAt", r.decidedAt().toString());
        return m;
    }

    // ─────────────────── tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("delegates to DataCloudClient.createEntity")
        void delegatesToDataCloud() {
            AgentRolloutRecord r = pending("rollout-dc-1", "rel-100");
            when(dataCloud.createEntity(eq(TENANT_ID), eq(DataCloudAgentRolloutRepository.COLLECTION), any()))
                    .thenReturn(Promise.of(mockEntity));

            AgentRolloutRecord saved = runPromise(() -> repo.save(r));

            assertThat(saved.rolloutId()).isEqualTo("rollout-dc-1");
            verify(dataCloud).createEntity(eq(TENANT_ID), eq(DataCloudAgentRolloutRepository.COLLECTION), any());
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns present when DataCloud returns matching entity")
        void returnsPresentWhenFound() {
            AgentRolloutRecord r = pending("rollout-dc-2", "rel-200");
            Map<String, Object> dataMap = toRolloutMap(r);
            when(mockEntity.getData()).thenReturn(dataMap);

            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudAgentRolloutRepository.COLLECTION), any()))
                    .thenReturn(Promise.of(List.of(mockEntity)));

            Optional<AgentRolloutRecord> found = runPromise(() -> repo.findById("rollout-dc-2"));

            assertThat(found).isPresent();
            assertThat(found.get().rolloutId()).isEqualTo("rollout-dc-2");
            assertThat(found.get().approvalState()).isEqualTo(AgentRolloutApprovalState.PENDING);
        }

        @Test
        @DisplayName("returns empty when DataCloud returns no entities")
        void returnsEmptyWhenNotFound() {
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudAgentRolloutRepository.COLLECTION), any()))
                    .thenReturn(Promise.of(List.of()));

            Optional<AgentRolloutRecord> found = runPromise(() -> repo.findById("no-such-rollout"));
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByReleaseId")
    class FindByReleaseId {

        @Test
        @DisplayName("returns list from DataCloud query")
        void returnsListFromDataCloud() {
            AgentRolloutRecord r = pending("rollout-dc-3", "rel-300");
            EntityInterface e1 = mock(EntityInterface.class);
            EntityInterface e2 = mock(EntityInterface.class);
            when(e1.getData()).thenReturn(toRolloutMap(r));
            when(e2.getData()).thenReturn(toRolloutMap(r));

            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudAgentRolloutRepository.COLLECTION), any()))
                    .thenReturn(Promise.of(List.of(e1, e2)));

            List<AgentRolloutRecord> result = runPromise(() -> repo.findByReleaseId("rel-300"));
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("saves APPROVED record after finding PENDING one")
        void approvesRollout() {
            AgentRolloutRecord r = pending("rollout-approve", "rel-400");
            when(mockEntity.getData()).thenReturn(toRolloutMap(r));

            // first query returns the PENDING record, second createEntity returns null entity
            when(dataCloud.queryEntities(any(), any(), any()))
                    .thenReturn(Promise.of(List.of(mockEntity)));
            when(dataCloud.createEntity(any(), any(), any()))
                    .thenReturn(Promise.of(mock(EntityInterface.class)));

            AgentRolloutRecord approved = runPromise(() ->
                    repo.approve("rollout-approve", "manager@ghatana.ai"));

            assertThat(approved.approvalState()).isEqualTo(AgentRolloutApprovalState.APPROVED);
            assertThat(approved.approvedBy()).isEqualTo("manager@ghatana.ai");
        }
    }

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        @DisplayName("saves REJECTED record with reason")
        void rejectsRollout() {
            AgentRolloutRecord r = pending("rollout-reject", "rel-500");
            when(mockEntity.getData()).thenReturn(toRolloutMap(r));

            when(dataCloud.queryEntities(any(), any(), any()))
                    .thenReturn(Promise.of(List.of(mockEntity)));
            when(dataCloud.createEntity(any(), any(), any()))
                    .thenReturn(Promise.of(mock(EntityInterface.class)));

            AgentRolloutRecord rejected = runPromise(() ->
                    repo.reject("rollout-reject", "security@ghatana.ai", "Policy violation"));

            assertThat(rejected.approvalState()).isEqualTo(AgentRolloutApprovalState.REJECTED);
            assertThat(rejected.rejectedBy()).isEqualTo("security@ghatana.ai");
            assertThat(rejected.rejectedReason()).isEqualTo("Policy violation");
        }
    }

    @Nested
    @DisplayName("rollback")
    class Rollback {

        @Test
        @DisplayName("saves ROLLED_BACK record from APPROVED state")
        void rollsBackRollout() {
            AgentRolloutRecord approved = pending("rollout-rollback", "rel-600")
                    .withApproved("manager@ghatana.ai", Instant.now());

            EntityInterface approvedEntity = mock(EntityInterface.class);
            when(approvedEntity.getData()).thenReturn(toRolloutMap(approved));

            when(dataCloud.queryEntities(any(), any(), any()))
                    .thenReturn(Promise.of(List.of(approvedEntity)));
            when(dataCloud.createEntity(any(), any(), any()))
                    .thenReturn(Promise.of(mock(EntityInterface.class)));

            AgentRolloutRecord rolled = runPromise(() ->
                    repo.rollback("rollout-rollback", "oncall@ghatana.ai"));

            assertThat(rolled.approvalState()).isEqualTo(AgentRolloutApprovalState.ROLLED_BACK);
        }
    }
}

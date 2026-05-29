/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.learning.delta;

import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaFactory;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.agent.learning.LearningDeltaType;
import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DataCloudLearningDeltaRepository.
 *
 * <p>Uses a local InMemoryEntityRepository to verify that data is stored
 * through EntityRepository (not an in-memory map in the repository itself).
 *
 * @doc.type class
 * @doc.purpose Tests for DataCloudLearningDeltaRepository
 * @doc.layer data-cloud
 * @doc.pattern Test
 */
@DisplayName("DataCloudLearningDeltaRepository Tests")
class DataCloudLearningDeltaRepositoryTest extends EventloopTestBase {

    private static final String TENANT = "test-tenant";
    private static final Map<String, Object> CONTENT = Map.of("action", "test-action");
    private static final List<String> EVIDENCE = List.of("evidence-1");

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static LearningDelta proposeDelta(String agentId, String skillId) {
        return LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                agentId,
                "release-1.0.0",
                skillId,
                TENANT,
                CONTENT,
                EVIDENCE,
                "learning-engine"
        );
    }

    private static LearningDelta proposeDeltaForTenant(String agentId, String skillId, String tenantId) {
        return LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                agentId,
                "release-1.0.0",
                skillId,
                tenantId,
                CONTENT,
                EVIDENCE,
                "learning-engine"
        );
    }

    // -------------------------------------------------------------------------
    // Core CRUD tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should save and retrieve learning delta")
    void shouldSaveAndRetrieveLearningDelta() {
        DataCloudLearningDeltaRepository repository =
                new DataCloudLearningDeltaRepository(new InMemoryEntityRepository());

        LearningDelta delta = proposeDelta("agent-123", "skill-123");

        runPromise(() -> repository.save(delta));
        Optional<LearningDelta> result = runPromise(() -> repository.findById(delta.deltaId()));

        assertThat(result).isPresent();
        assertThat(result.get().deltaId()).isEqualTo(delta.deltaId());
        assertThat(result.get().state()).isEqualTo(LearningDeltaState.PROPOSED);
    }

    @Test
    @DisplayName("Should find learning deltas by agent ID")
    void shouldFindByAgentId() {
        DataCloudLearningDeltaRepository repository =
                new DataCloudLearningDeltaRepository(new InMemoryEntityRepository());

        LearningDelta delta1 = proposeDelta("agent-123", "skill-123");
        LearningDelta delta2 = proposeDelta("agent-456", "skill-456");

        runPromise(() -> repository.save(delta1));
        runPromise(() -> repository.save(delta2));

        List<LearningDelta> results = runPromise(() -> repository.findByAgentId("agent-123"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).agentId()).isEqualTo("agent-123");
    }

    @Test
    @DisplayName("Should find learning deltas by skill ID")
    void shouldFindBySkillId() {
        DataCloudLearningDeltaRepository repository =
                new DataCloudLearningDeltaRepository(new InMemoryEntityRepository());

        LearningDelta delta1 = proposeDelta("agent-123", "skill-123");
        LearningDelta delta2 = proposeDelta("agent-123", "skill-456");

        runPromise(() -> repository.save(delta1));
        runPromise(() -> repository.save(delta2));

        List<LearningDelta> results = runPromise(() -> repository.findBySkillId("skill-123"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).skillId()).isEqualTo("skill-123");
    }

    @Test
    @DisplayName("Should find learning deltas by state")
    void shouldFindByState() {
        DataCloudLearningDeltaRepository repository =
                new DataCloudLearningDeltaRepository(new InMemoryEntityRepository());

        LearningDelta delta1 = proposeDelta("agent-123", "skill-123");
        LearningDelta delta2 = proposeDelta("agent-123", "skill-456");

        runPromise(() -> repository.save(delta1));
        runPromise(() -> repository.save(delta2));

        List<LearningDelta> results = runPromise(() -> repository.findByState(LearningDeltaState.PROPOSED));

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("Should find pending evaluation deltas (PROPOSED and PENDING_EVALUATION only)")
    void shouldFindPendingEvaluation() {
        DataCloudLearningDeltaRepository repository =
                new DataCloudLearningDeltaRepository(new InMemoryEntityRepository());

        LearningDelta proposed = proposeDelta("agent-123", "skill-123");
        LearningDelta evaluated = proposeDelta("agent-123", "skill-456");

        runPromise(() -> repository.save(proposed));
        runPromise(() -> repository.save(evaluated));
        // Advance second delta past pending-evaluation
        runPromise(() -> repository.updateState(evaluated.deltaId(), LearningDeltaState.EVALUATED));

        List<LearningDelta> results = runPromise(repository::findPendingEvaluation);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).deltaId()).isEqualTo(proposed.deltaId());
    }

    @Test
    @DisplayName("Should find promotable deltas (EVALUATED and APPROVED only)")
    void shouldFindPromotable() {
        DataCloudLearningDeltaRepository repository =
                new DataCloudLearningDeltaRepository(new InMemoryEntityRepository());

        LearningDelta proposed = proposeDelta("agent-123", "skill-123");
        LearningDelta evaluated = proposeDelta("agent-123", "skill-456");
        LearningDelta approved = proposeDelta("agent-123", "skill-789");

        runPromise(() -> repository.save(proposed));
        runPromise(() -> repository.save(evaluated));
        runPromise(() -> repository.save(approved));
        runPromise(() -> repository.updateState(evaluated.deltaId(), LearningDeltaState.EVALUATED));
        runPromise(() -> repository.updateState(approved.deltaId(), LearningDeltaState.APPROVED));

        List<LearningDelta> results = runPromise(repository::findPromotable);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(LearningDelta::state)
                .containsExactlyInAnyOrder(LearningDeltaState.EVALUATED, LearningDeltaState.APPROVED);
    }

    @Test
    @DisplayName("Should update delta state")
    void shouldUpdateDeltaState() {
        DataCloudLearningDeltaRepository repository =
                new DataCloudLearningDeltaRepository(new InMemoryEntityRepository());

        LearningDelta delta = proposeDelta("agent-123", "skill-123");
        runPromise(() -> repository.save(delta));

        LearningDelta updated = runPromise(() ->
                repository.updateState(delta.deltaId(), LearningDeltaState.EVALUATED));

        assertThat(updated).isNotNull();
        assertThat(updated.state()).isEqualTo(LearningDeltaState.EVALUATED);
        assertThat(updated.evaluatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should update delta state with rejection reason")
    void shouldUpdateDeltaStateWithRejectionReason() {
        DataCloudLearningDeltaRepository repository =
                new DataCloudLearningDeltaRepository(new InMemoryEntityRepository());

        LearningDelta delta = proposeDelta("agent-123", "skill-123");
        runPromise(() -> repository.save(delta));

        LearningDelta updated = runPromise(() ->
                repository.updateState(delta.deltaId(), LearningDeltaState.REJECTED, "Insufficient evidence"));

        assertThat(updated).isNotNull();
        assertThat(updated.state()).isEqualTo(LearningDeltaState.REJECTED);
        assertThat(updated.rejectionReason()).isEqualTo("Insufficient evidence");
        assertThat(updated.rejectedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Additional correctness tests per Phase 2.1 requirements
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("State update preserves all non-state payload fields")
    void shouldPreservePayloadAfterStateUpdate() {
        DataCloudLearningDeltaRepository repository =
                new DataCloudLearningDeltaRepository(new InMemoryEntityRepository());

        LearningDelta delta = proposeDelta("agent-123", "skill-123");
        runPromise(() -> repository.save(delta));

        LearningDelta updated = runPromise(() ->
                repository.updateState(delta.deltaId(), LearningDeltaState.EVALUATED));

        assertThat(updated.deltaId()).isEqualTo(delta.deltaId());
        assertThat(updated.agentId()).isEqualTo(delta.agentId());
        assertThat(updated.skillId()).isEqualTo(delta.skillId());
        assertThat(updated.tenantId()).isEqualTo(delta.tenantId());
        assertThat(updated.proposedBy()).isEqualTo(delta.proposedBy());
        assertThat(updated.proposedContent()).isEqualTo(delta.proposedContent());
    }

    @Test
    @DisplayName("PENDING_HUMAN_REVIEW state is queryable via findByState")
    void shouldFindDeltasInPendingHumanReviewState() {
        DataCloudLearningDeltaRepository repository =
                new DataCloudLearningDeltaRepository(new InMemoryEntityRepository());

        LearningDelta delta = proposeDelta("agent-123", "skill-123");
        runPromise(() -> repository.save(delta));
        runPromise(() -> repository.updateState(delta.deltaId(), LearningDeltaState.PENDING_HUMAN_REVIEW));

        List<LearningDelta> results = runPromise(() ->
                repository.findByState(LearningDeltaState.PENDING_HUMAN_REVIEW));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).state()).isEqualTo(LearningDeltaState.PENDING_HUMAN_REVIEW);
    }

    @Test
    @DisplayName("Should persist deltas in EntityRepository, not in local map")
    void shouldPersistThroughEntityRepository() {
        InMemoryEntityRepository sharedRepo = new InMemoryEntityRepository();
        DataCloudLearningDeltaRepository repository = new DataCloudLearningDeltaRepository(sharedRepo);

        LearningDelta delta = proposeDelta("agent-123", "skill-123");
        runPromise(() -> repository.save(delta));

        // Verify data lives in EntityRepository, not in a local ConcurrentHashMap
        List<Entity> stored = runPromise(() ->
                sharedRepo.findAll(TENANT, "agent-learning-deltas", Map.of(), null, 0, 100));

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getData()).containsEntry("deltaId", delta.deltaId());
        assertThat(stored.get(0).getTenantId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("Should enforce tenant isolation between different tenants")
    void shouldEnforceTenantIsolation() {
        InMemoryEntityRepository sharedRepo = new InMemoryEntityRepository();
        DataCloudLearningDeltaRepository repository = new DataCloudLearningDeltaRepository(sharedRepo);

        LearningDelta deltaA = proposeDeltaForTenant("agent-123", "skill-123", "tenant-a");
        LearningDelta deltaB = proposeDeltaForTenant("agent-456", "skill-456", "tenant-b");

        runPromise(() -> repository.save(deltaA));
        runPromise(() -> repository.save(deltaB));

        // EntityRepository partitions by tenant
        List<Entity> tenantAEntities = runPromise(() ->
                sharedRepo.findAll("tenant-a", "agent-learning-deltas", Map.of(), null, 0, 100));
        List<Entity> tenantBEntities = runPromise(() ->
                sharedRepo.findAll("tenant-b", "agent-learning-deltas", Map.of(), null, 0, 100));

        assertThat(tenantAEntities).hasSize(1);
        assertThat(tenantBEntities).hasSize(1);
        assertThat(tenantAEntities.get(0).getTenantId()).isEqualTo("tenant-a");
        assertThat(tenantBEntities.get(0).getTenantId()).isEqualTo("tenant-b");

        // findById resolves correctly for each delta
        Optional<LearningDelta> foundA = runPromise(() -> repository.findById(deltaA.deltaId()));
        Optional<LearningDelta> foundB = runPromise(() -> repository.findById(deltaB.deltaId()));
        assertThat(foundA).isPresent().get().extracting(LearningDelta::tenantId).isEqualTo("tenant-a");
        assertThat(foundB).isPresent().get().extracting(LearningDelta::tenantId).isEqualTo("tenant-b");
    }

    @Test
    @DisplayName("Tenant-scoped findById should resolve correct delta without reverse index")
    void shouldFindByTenantAndDeltaId() {
        InMemoryEntityRepository sharedRepo = new InMemoryEntityRepository();
        DataCloudLearningDeltaRepository repository = new DataCloudLearningDeltaRepository(sharedRepo);

        LearningDelta deltaA = proposeDeltaForTenant("agent-123", "skill-123", "tenant-a");
        LearningDelta deltaB = proposeDeltaForTenant("agent-456", "skill-456", "tenant-b");

        runPromise(() -> repository.save(deltaA));
        runPromise(() -> repository.save(deltaB));

        Optional<LearningDelta> foundInTenantA = runPromise(() -> repository.findById("tenant-a", deltaA.deltaId()));
        Optional<LearningDelta> absentFromTenantB = runPromise(() -> repository.findById("tenant-b", deltaA.deltaId()));

        assertThat(foundInTenantA).isPresent();
        assertThat(foundInTenantA.get().tenantId()).isEqualTo("tenant-a");
        assertThat(absentFromTenantB).isEmpty();
    }

    @Test
    @DisplayName("Tenant-scoped updateState should mutate only target tenant delta")
    void shouldUpdateStateWithTenantScope() {
        InMemoryEntityRepository sharedRepo = new InMemoryEntityRepository();
        DataCloudLearningDeltaRepository repository = new DataCloudLearningDeltaRepository(sharedRepo);

        LearningDelta deltaA = proposeDeltaForTenant("agent-123", "skill-123", "tenant-a");
        LearningDelta deltaB = proposeDeltaForTenant("agent-456", "skill-456", "tenant-b");

        runPromise(() -> repository.save(deltaA));
        runPromise(() -> repository.save(deltaB));

        LearningDelta updatedA = runPromise(() -> repository.updateState("tenant-a", deltaA.deltaId(), LearningDeltaState.EVALUATED));
        Optional<LearningDelta> unchangedB = runPromise(() -> repository.findById("tenant-b", deltaB.deltaId()));

        assertThat(updatedA.state()).isEqualTo(LearningDeltaState.EVALUATED);
        assertThat(unchangedB).isPresent();
        assertThat(unchangedB.get().state()).isEqualTo(LearningDeltaState.PROPOSED);
    }

    // -------------------------------------------------------------------------
    // Local InMemoryEntityRepository for testing
    // -------------------------------------------------------------------------

    /**
     * Minimal in-memory EntityRepository for testing, mirroring the production behaviour
     * of InMemoryTenantEntityRepository from EntityRepositoryCoverageTest.
     *
     * <p>findAll() returns ALL entities for the tenant+collection, ignoring the filter map.
     * save() enforces optimistic locking via version comparison.
     */
    private static final class InMemoryEntityRepository implements EntityRepository {

        private final Map<UUID, Entity> store = new ConcurrentHashMap<>();

        @Override
        public Promise<Optional<Entity>> findById(String tenantId, String collectionName, UUID entityId) {
            Entity entity = store.get(entityId);
            if (entity == null
                    || !tenantId.equals(entity.getTenantId())
                    || !collectionName.equals(entity.getCollectionName())
                    || !Boolean.TRUE.equals(entity.getActive())) {
                return Promise.of(Optional.empty());
            }
            return Promise.of(Optional.of(entity));
        }

        @Override
        public Promise<List<Entity>> findAll(
                String tenantId, String collectionName,
                Map<String, Object> filter, String sort, int offset, int limit) {
            List<Entity> result = new ArrayList<>();
            for (Entity entity : store.values()) {
                if (tenantId.equals(entity.getTenantId())
                        && collectionName.equals(entity.getCollectionName())
                        && Boolean.TRUE.equals(entity.getActive())) {
                    result.add(entity);
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<Entity> save(String tenantId, Entity entity) {
            if (!tenantId.equals(entity.getTenantId())) {
                return Promise.ofException(new IllegalArgumentException("tenantId mismatch"));
            }
            Entity existing = store.get(entity.getId());
            if (existing != null && !existing.getVersion().equals(entity.getVersion())) {
                return Promise.ofException(new OptimisticLockException("Version conflict"));
            }
            Entity toSave = existing == null
                    ? entity
                    : entity.toBuilder().version(existing.getVersion() + 1).build();
            store.put(toSave.getId(), toSave);
            return Promise.of(toSave);
        }

        @Override
        public Promise<Entity> saveWithIdempotency(String tenantId, Entity entity, String idempotencyKey) {
            // Test-only implementation: delegate to save without idempotency
            return save(tenantId, entity);
        }

        @Override
        public Promise<Void> delete(String tenantId, String collectionName, UUID entityId) {
            return Promise.complete();
        }

        @Override
        public Promise<Boolean> exists(String tenantId, String collectionName, UUID entityId) {
            return Promise.of(store.containsKey(entityId));
        }

        @Override
        public Promise<Long> count(String tenantId, String collectionName) {
            long count = store.values().stream()
                    .filter(e -> tenantId.equals(e.getTenantId())
                            && collectionName.equals(e.getCollectionName())
                            && Boolean.TRUE.equals(e.getActive()))
                    .count();
            return Promise.of(count);
        }

        @Override
        public Promise<Long> countByFilter(String tenantId, String collectionName, Map<String, Object> filter) {
            return count(tenantId, collectionName);
        }

        @Override
        public Promise<List<Entity>> findByQuery(String tenantId, String collectionName, Object querySpec) {
            return findAll(tenantId, collectionName, Map.of(), null, 0, Integer.MAX_VALUE);
        }

        @Override
        public Promise<List<Entity>> saveAll(String tenantId, List<Entity> entities) {
            Promise<List<Entity>> acc = Promise.of(new ArrayList<>());
            for (Entity entity : entities) {
                acc = acc.then(saved -> save(tenantId, entity).map(s -> {
                    saved.add(s);
                    return saved;
                }));
            }
            return acc;
        }

        @Override
        public Promise<Void> deleteAll(String tenantId, String collectionName, List<UUID> entityIds) {
            Promise<Void> acc = Promise.complete();
            for (UUID id : entityIds) {
                acc = acc.then(() -> delete(tenantId, collectionName, id));
            }
            return acc;
        }
    }
}

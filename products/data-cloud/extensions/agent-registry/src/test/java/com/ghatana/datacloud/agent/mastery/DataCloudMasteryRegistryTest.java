/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.mastery;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.*;
import com.ghatana.agent.mastery.transition.DefaultMasteryTransitionPolicy;
import com.ghatana.agent.mastery.transition.MasteryTransitionPolicy;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for DataCloudMasteryRegistry.
 *
 * @doc.type class
 * @doc.purpose Tests for DataCloudMasteryRegistry using EntityRepository-backed persistence
 * @doc.layer data-cloud
 * @doc.pattern Test
 */
@DisplayName("DataCloudMasteryRegistry Tests")
class DataCloudMasteryRegistryTest extends EventloopTestBase {

    /**
     * "default" matches the tenantId used internally by findStale(), transition(), and
     * decide() (when no tenantId is present in the query). Items saved with this tenant
     * will be found by all registry methods.
     */
    private static final String TENANT = "default";

    private static DataCloudMasteryRegistry registry() {
        return new DataCloudMasteryRegistry(
                new InMemoryEntityRepository(),
                new InMemoryMasteryTransitionRepository(),
                new InMemoryMasteryEvidenceRepository(),
                new DefaultMasteryTransitionPolicy()
        );
    }

    private static MasteryItem buildItem(String masteryId, String skillId, MasteryState state) {
        ApplicabilityScope applicability = ApplicabilityScope.minimal(TENANT, "production");
        VersionScope versionScope = VersionScope.empty();
        MasteryScore score = new MasteryScore(0.9, 0.8, 0.7, 0.95, 0.6, 0.8, 0.9);
        return new MasteryItem(
                masteryId, TENANT, skillId, "domain-1", "agent-123", "release-1.0.0",
                state, versionScope, applicability, score,
                List.of(), List.of(), List.of(), List.of("ev-1"), List.of(), List.of(), List.of(),
                Instant.now(), Instant.now().plus(java.time.Duration.ofDays(30)), Map.of(),
                0.9
        );
    }

    @Test
    @DisplayName("Should save and retrieve mastery item")
    void shouldSaveAndRetrieveMasteryItem() {
        DataCloudMasteryRegistry registry = registry();
        MasteryItem item = buildItem("mastery-123", "skill-123", MasteryState.COMPETENT);

        runPromise(() -> registry.save(item));

        EnvironmentFingerprint env = new EnvironmentFingerprint(
                TENANT, "repo-1", "java",
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Instant.now(), List.of());
        Optional<MasteryItem> result = runPromise(() -> registry.findBySkill("skill-123", env));

        assertThat(result).isPresent();
        assertThat(result.get().masteryId()).isEqualTo("mastery-123");
    }

    @Test
    @DisplayName("Should query mastery items by skill")
    void shouldQueryMasteryItemsBySkill() {
        DataCloudMasteryRegistry registry = registry();
        MasteryItem item1 = buildItem("mastery-1", "skill-123", MasteryState.COMPETENT);
        MasteryItem item2 = buildItem("mastery-2", "skill-456", MasteryState.COMPETENT);

        runPromise(() -> registry.save(item1));
        runPromise(() -> registry.save(item2));

        MasteryQuery query = MasteryQuery.bySkill("skill-123").withTenantId(TENANT);
        List<MasteryItem> results = runPromise(() -> registry.query(query));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).skillId()).isEqualTo("skill-123");
    }

    @Test
    @DisplayName("Should transition mastery state")
    void shouldTransitionMasteryState() {
        DataCloudMasteryRegistry registry = registry();
        MasteryItem item = buildItem("mastery-123", "skill-123", MasteryState.PRACTICED);

        runPromise(() -> registry.save(item));

        MasteryTransition transition = new MasteryTransition(
                UUID.randomUUID().toString(),
                TENANT,
                "mastery-123",
                "agent-123",
                "release-1.0.0",
                null,
                MasteryState.PRACTICED,
                MasteryState.COMPETENT,
                "Evaluation passed",
                "user-123",
                Instant.now(),
                Map.of("procedure_id", "proc-123", "basic_eval_passed", "true"),
                Map.of()
        );

        MasteryTransitionResult result = runPromise(() -> registry.transition(transition));

        assertThat(result.success()).isTrue();
        assertThat(result.newState()).isEqualTo(MasteryState.COMPETENT);
    }

    @Test
    @DisplayName("Should find stale mastery items")
    void shouldFindStaleMasteryItems() {
        DataCloudMasteryRegistry registry = registry();
        Instant past = Instant.now().minus(java.time.Duration.ofDays(60));

        ApplicabilityScope applicability = ApplicabilityScope.minimal(TENANT, "production");
        VersionScope versionScope = VersionScope.empty();
        MasteryScore score = new MasteryScore(0.9, 0.8, 0.7, 0.95, 0.6, 0.8, 0.9);

        MasteryItem staleItem = new MasteryItem(
                "mastery-1", TENANT, "skill-123", "domain-1", "agent-123", "release-1.0.0",
                MasteryState.COMPETENT, versionScope, applicability, score,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                past, past, Map.of(),
                0.9
        );
        MasteryItem freshItem = buildItem("mastery-2", "skill-456", MasteryState.COMPETENT);

        runPromise(() -> registry.save(staleItem));
        runPromise(() -> registry.save(freshItem));

        List<MasteryItem> staleItems = runPromise(() -> registry.findStale(TENANT, Instant.now()));

        assertThat(staleItems).hasSize(1);
        assertThat(staleItems.get(0).masteryId()).isEqualTo("mastery-1");
    }

    @Test
    @DisplayName("Should make mastery decision - allow for mastered skill")
    void shouldMakeDecisionAllowForMasteredSkill() {
        DataCloudMasteryRegistry registry = registry();
        MasteryScore highScore = new MasteryScore(0.95, 0.85, 0.8, 0.98, 0.7, 0.85, 0.95);
        ApplicabilityScope applicability = ApplicabilityScope.minimal(TENANT, "production");
        VersionScope versionScope = VersionScope.empty();

        MasteryItem item = new MasteryItem(
                "mastery-123", TENANT, "skill-123", "domain-1", "agent-123", "release-1.0.0",
                MasteryState.MASTERED, versionScope, applicability, highScore,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Instant.now(), Instant.now().plus(java.time.Duration.ofDays(30)), Map.of(),
                0.9
        );

        runPromise(() -> registry.save(item));

        MasteryQuery query = MasteryQuery.bySkill("skill-123").withTenantId(TENANT);
        MasteryDecision decision = runPromise(() -> registry.decide(query));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.state()).isEqualTo(MasteryState.MASTERED);
    }

    @Test
    @DisplayName("Should make mastery decision - require verification for competent skill")
    void shouldMakeDecisionRequireVerificationForCompetentSkill() {
        DataCloudMasteryRegistry registry = registry();
        MasteryItem item = buildItem("mastery-123", "skill-123", MasteryState.COMPETENT);

        runPromise(() -> registry.save(item));

        MasteryQuery query = MasteryQuery.bySkill("skill-123").withTenantId(TENANT);
        MasteryDecision decision = runPromise(() -> registry.decide(query));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.state()).isEqualTo(MasteryState.COMPETENT);
        assertThat(decision.requiresVerification()).isTrue();
    }

    @Test
    @DisplayName("Should make mastery decision - block when no matching skill")
    void shouldMakeDecisionBlockWhenNoMatchingSkill() {
        DataCloudMasteryRegistry registry = registry();

        MasteryQuery query = MasteryQuery.bySkill("unknown-skill");
        MasteryDecision decision = runPromise(() -> registry.decide(query));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.state()).isEqualTo(MasteryState.UNKNOWN);
    }

    @Test
    @DisplayName("findBest returns the highest-ranked item by mastery state then score")
    void findBestSelectsHighestRankedItem() {
        DataCloudMasteryRegistry registry = registry();
        // Two items for the same skill — MASTERED should beat COMPETENT
        MasteryItem competent = buildItem("mastery-competent", "skill-999", MasteryState.COMPETENT);
        MasteryItem mastered  = buildItem("mastery-mastered",  "skill-999", MasteryState.MASTERED);

        runPromise(() -> registry.save(competent));
        runPromise(() -> registry.save(mastered));

        MasteryQuery query = MasteryQuery.bySkill("skill-999").withTenantId(TENANT);
        Optional<MasteryItem> result = runPromise(() -> registry.findBest(query));

        assertThat(result).isPresent();
        assertThat(result.get().masteryId()).isEqualTo("mastery-mastered");
        assertThat(result.get().state()).isEqualTo(MasteryState.MASTERED);
    }

    @Test
    @DisplayName("findBest returns empty when no mastery item matches")
    void findBestReturnsEmptyForUnknownSkill() {
        DataCloudMasteryRegistry registry = registry();

        MasteryQuery query = MasteryQuery.bySkill("no-such-skill").withTenantId(TENANT);
        Optional<MasteryItem> result = runPromise(() -> registry.findBest(query));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findBest enforces tenant isolation: items from another tenant are not returned")
    void findBestEnforcesTenantIsolation() {
        DataCloudMasteryRegistry registry = registry();
        MasteryItem item = buildItem("mastery-other", "skill-888", MasteryState.MASTERED);
        // item is saved under TENANT; querying under a different tenant must yield empty
        runPromise(() -> registry.save(item));

        MasteryQuery query = MasteryQuery.bySkill("skill-888").withTenantId("other-tenant");
        Optional<MasteryItem> result = runPromise(() -> registry.findBest(query));

        assertThat(result).isEmpty();
    }

    private static MasteryItem buildStaleItem(String masteryId, String skillId, MasteryState state) {
        ApplicabilityScope applicability = ApplicabilityScope.minimal(TENANT, "production");
        VersionScope versionScope = VersionScope.empty();
        MasteryScore score = new MasteryScore(0.9, 0.8, 0.7, 0.95, 0.6, 0.8, 0.9);
        Instant past = Instant.now().minus(java.time.Duration.ofDays(60));
        return new MasteryItem(
                masteryId, TENANT, skillId, "domain-1", "agent-123", "release-1.0.0",
                state, versionScope, applicability, score,
                List.of(), List.of(), List.of(), List.of("ev-1"), List.of(), List.of(), List.of(),
                past, past, Map.of(),
                0.9
        );
    }

    private static DataCloudMasteryRegistry registryWithFailingTransitions() {
        return new DataCloudMasteryRegistry(
                new InMemoryEntityRepository(),
                new FailingTransitionRepository(),
                new InMemoryMasteryEvidenceRepository(),
                new DefaultMasteryTransitionPolicy()
        );
    }

    // -- Phase 4.2 acceptance tests -------------------------------------------

    @Test
    @DisplayName("4.2: MAINTENANCE_ONLY item is returned when flag is not set (not filtered before version classification)")
    void maintenanceOnlyItemIsReturnedByDefault() {
        DataCloudMasteryRegistry registry = registry();
        MasteryItem item = buildItem("mastery-maint", "skill-maint", MasteryState.MAINTENANCE_ONLY);
        runPromise(() -> registry.save(item));

        // Query without includeMaintenanceOnly flag — item must NOT be excluded
        MasteryQuery query = MasteryQuery.bySkill("skill-maint").withTenantId(TENANT);
        List<MasteryItem> results = runPromise(() -> registry.query(query));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).state()).isEqualTo(MasteryState.MAINTENANCE_ONLY);
    }

    @Test
    @DisplayName("4.2: OBSOLETE item is blocked from query results by default")
    void obsoleteItemIsBlockedByDefault() {
        DataCloudMasteryRegistry registry = registry();
        MasteryItem obsolete = buildItem("mastery-obs", "skill-obs", MasteryState.OBSOLETE);
        MasteryItem active   = buildItem("mastery-act", "skill-obs", MasteryState.COMPETENT);
        runPromise(() -> registry.save(obsolete));
        runPromise(() -> registry.save(active));

        MasteryQuery query = MasteryQuery.bySkill("skill-obs").withTenantId(TENANT);
        List<MasteryItem> results = runPromise(() -> registry.query(query));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).state()).isEqualTo(MasteryState.COMPETENT);
    }

    @Test
    @DisplayName("4.2: Stale item excluded when requireFreshness is true")
    void staleItemExcludedWhenFreshnessRequired() {
        DataCloudMasteryRegistry registry = registry();
        MasteryItem stale = buildStaleItem("mastery-stale", "skill-fresh", MasteryState.COMPETENT);
        runPromise(() -> registry.save(stale));

        MasteryQuery query = MasteryQuery.bySkill("skill-fresh")
                .withTenantId(TENANT);
        // Build a query with requireFreshness=true
        MasteryQuery freshQuery = new MasteryQuery(
                "skill-fresh", null, null, TENANT, null, null,
                false, false, false, true, Instant.now(), null, null
        );
        List<MasteryItem> results = runPromise(() -> registry.query(freshQuery));

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("4.2: Transition policy enforced — skipping states is rejected")
    void transitionPolicyEnforced() {
        DataCloudMasteryRegistry registry = registry();
        MasteryItem item = buildItem("mastery-pol", "skill-pol", MasteryState.PRACTICED);
        runPromise(() -> registry.save(item));

        // PRACTICED → MASTERED (skipping COMPETENT) must be rejected
        MasteryTransition transition = new MasteryTransition(
                UUID.randomUUID().toString(), TENANT, "mastery-pol",
                "agent-123", "release-1.0.0", null,
                MasteryState.PRACTICED, MasteryState.MASTERED,
                "skip check", "tester", Instant.now(),
                Map.of("procedure_id", "proc-123", "basic_eval_passed", "true"), Map.of()
        );

        MasteryTransitionResult result = runPromise(() -> registry.transition(transition));

        assertThat(result.success()).isFalse();
        assertThat(result.previousState()).isEqualTo(MasteryState.PRACTICED);
    }

    @Test
    @DisplayName("4.2: Transition append failure does not silently corrupt item state")
    void transitionAppendFailureDoesNotCorruptItemState() {
        DataCloudMasteryRegistry registry = registryWithFailingTransitions();
        MasteryItem item = buildItem("mastery-atomic", "skill-atomic", MasteryState.PRACTICED);
        runPromise(() -> registry.save(item));

        MasteryTransition transition = new MasteryTransition(
                UUID.randomUUID().toString(), TENANT, "mastery-atomic",
                "agent-123", "release-1.0.0", null,
                MasteryState.PRACTICED, MasteryState.COMPETENT,
                "append will fail", "tester", Instant.now(),
                Map.of("procedure_id", "proc-123", "basic_eval_passed", "true"), Map.of()
        );

        // Append fails → Promise propagates the failure (explicit, not silent)
        assertThatThrownBy(() -> runPromise(() -> registry.transition(transition)))
                .isInstanceOf(RuntimeException.class);

        // Item state must still be PRACTICED — no silent corruption
        MasteryQuery query = new MasteryQuery(
                "skill-atomic", null, null, TENANT, null, null,
                false, false, true, null, null, null, null
        );
        List<MasteryItem> results = runPromise(() -> registry.query(query));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).state()).isEqualTo(MasteryState.PRACTICED);
    }

    // -------------------------------------------------------------------------
    // Phase 4.2 side-fix: multi-tenant transition and findStale correctness
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("transition() resolves tenant from the saved item — not hardcoded 'default'")
    void transitionIsTenantSafe() {
        DataCloudMasteryRegistry registry = registry();
        String tenantProd = "tenant-prod";

        ApplicabilityScope applicability = ApplicabilityScope.minimal(tenantProd, "production");
        VersionScope versionScope = VersionScope.empty();
        MasteryScore score = new MasteryScore(0.9, 0.8, 0.7, 0.95, 0.6, 0.8, 0.9);
        MasteryItem item = new MasteryItem(
                "mastery-prod-1", tenantProd, "skill-prod", "domain-1", "agent-prod", "release-2.0.0",
                MasteryState.PRACTICED, versionScope, applicability, score,
                List.of(), List.of(), List.of(), List.of("ev-1"), List.of(), List.of(), List.of(),
                Instant.now(), Instant.now().plus(java.time.Duration.ofDays(30)), Map.of(),
                0.9
        );
        runPromise(() -> registry.save(item));

        MasteryTransition transition = new MasteryTransition(
                UUID.randomUUID().toString(),
                tenantProd,
                "mastery-prod-1",
                "agent-prod",
                "release-2.0.0",
                null,
                MasteryState.PRACTICED,
                MasteryState.COMPETENT,
                "Evaluation passed",
                "tester",
                Instant.now(),
                Map.of("procedure_id", "proc-123", "basic_eval_passed", "true"),
                Map.of()
        );

        MasteryTransitionResult result = runPromise(() -> registry.transition(transition));

        assertThat(result.success()).isTrue();
        assertThat(result.newState()).isEqualTo(MasteryState.COMPETENT);
    }

    @Test
    @DisplayName("findStale returns stale items from all saved tenants, not only 'default'")
    void findStaleAcrossAllTenants() {
        DataCloudMasteryRegistry registry = registry();
        Instant past = Instant.now().minus(java.time.Duration.ofDays(60));
        String tenantOther = "tenant-other";

        ApplicabilityScope otherApplicability = ApplicabilityScope.minimal(tenantOther, "production");
        VersionScope versionScope = VersionScope.empty();
        MasteryScore score = new MasteryScore(0.9, 0.8, 0.7, 0.95, 0.6, 0.8, 0.9);

        MasteryItem staleOtherTenant = new MasteryItem(
                "mastery-stale-other", tenantOther, "skill-stale-other", "domain-1", "agent-other", "release-1.0.0",
                MasteryState.COMPETENT, versionScope, otherApplicability, score,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                past, past, Map.of(),
                0.9
        );
        runPromise(() -> registry.save(staleOtherTenant));

        List<MasteryItem> staleItems = runPromise(() -> registry.findStale(tenantOther, Instant.now()));

        assertThat(staleItems).hasSize(1);
        assertThat(staleItems.get(0).masteryId()).isEqualTo("mastery-stale-other");
    }

    // -------------------------------------------------------------------------
    // In-memory helpers for testing
    // -------------------------------------------------------------------------

    /**
     * In-memory EntityRepository with equality filter support on data map fields.
     * Used to isolate the mastery registry from a real Data Cloud instance.
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
            List<Entity> result = store.values().stream()
                    .filter(e -> tenantId.equals(e.getTenantId())
                            && collectionName.equals(e.getCollectionName())
                            && Boolean.TRUE.equals(e.getActive()))
                    .filter(e -> filter.isEmpty() || filter.entrySet().stream()
                            .allMatch(entry -> entry.getValue().equals(e.getData().get(entry.getKey()))))
                    .collect(Collectors.toList());
            return Promise.of(result);
        }

        @Override
        public Promise<Entity> save(String tenantId, Entity entity) {
            if (!tenantId.equals(entity.getTenantId())) {
                return Promise.ofException(new IllegalArgumentException("tenantId mismatch"));
            }
            // Auto-generate UUID if entity has no ID (DataCloudMasteryRegistry.save() omits it)
            UUID id = entity.getId() != null ? entity.getId() : UUID.randomUUID();
            Entity toSave = entity.getId() != null ? entity : entity.toBuilder().id(id).build();
            Entity existing = store.get(id);
            if (existing != null) {
                toSave = entity.toBuilder().id(id).version(existing.getVersion() + 1).build();
            }
            store.put(id, toSave);
            return Promise.of(toSave);
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
            List<Entity> saved = new ArrayList<>();
            Promise<Void> acc = Promise.complete();
            for (Entity entity : entities) {
                acc = acc.then(() -> save(tenantId, entity).map(s -> {
                    saved.add(s);
                    return null;
                }));
            }
            return acc.map(ignored -> saved);
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

    /** Minimal in-memory MasteryTransitionRepository for tests. */
    private static final class InMemoryMasteryTransitionRepository implements MasteryTransitionRepository {

        private final Map<String, MasteryTransition> store = new ConcurrentHashMap<>();

        @Override
        public Promise<MasteryTransition> append(MasteryTransition transition) {
            store.put(transition.transitionId(), transition);
            return Promise.of(transition);
        }

        @Override
        public Promise<Optional<MasteryTransition>> findById(String transitionId) {
            return Promise.of(Optional.ofNullable(store.get(transitionId)));
        }

        @Override
        public Promise<List<MasteryTransition>> findByMasteryId(String masteryId) {
            return Promise.of(store.values().stream()
                    .filter(t -> masteryId.equals(t.masteryId()))
                    .collect(Collectors.toList()));
        }

        @Override
        public Promise<List<MasteryTransition>> findByInitiatedBy(String initiatedBy) {
            return Promise.of(store.values().stream()
                    .filter(t -> initiatedBy.equals(t.initiatedBy()))
                    .collect(Collectors.toList()));
        }

        @Override
        public Promise<List<MasteryTransition>> findByTimeRange(Instant from, Instant to) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Optional<MasteryTransition>> findLatestByMasteryId(String masteryId) {
            return findByMasteryId(masteryId).map(list -> list.isEmpty()
                    ? Optional.empty()
                    : Optional.of(list.get(list.size() - 1)));
        }
    }

    /** Minimal in-memory MasteryEvidenceRepository for tests. */
    private static final class InMemoryMasteryEvidenceRepository implements MasteryEvidenceRepository {

        private final Map<String, MasteryEvidence> store = new ConcurrentHashMap<>();

        @Override
        public Promise<MasteryEvidence> save(MasteryEvidence evidence) {
            store.put(evidence.evidenceId(), evidence);
            return Promise.of(evidence);
        }

        @Override
        public Promise<Optional<MasteryEvidence>> findById(String evidenceId) {
            return Promise.of(Optional.ofNullable(store.get(evidenceId)));
        }

        @Override
        public Promise<List<MasteryEvidence>> findByMasteryId(String masteryId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<MasteryEvidence>> findByType(MasteryEvidenceType type) {
            return Promise.of(store.values().stream()
                    .filter(e -> type.equals(e.type()))
                    .collect(Collectors.toList()));
        }

        @Override
        public Promise<List<MasteryEvidence>> findByRef(String ref) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<MasteryEvidence>> findByCreatedBy(String createdBy) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Void> deleteById(String evidenceId) {
            store.remove(evidenceId);
            return Promise.complete();
        }
    }

    /** Simulates an unavailable transition store to verify atomic ordering. */
    private static final class FailingTransitionRepository implements MasteryTransitionRepository {

        @Override
        public Promise<MasteryTransition> append(MasteryTransition transition) {
            return Promise.ofException(new RuntimeException("Transition store unavailable"));
        }

        @Override
        public Promise<Optional<MasteryTransition>> findById(String transitionId) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<List<MasteryTransition>> findByMasteryId(String masteryId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<MasteryTransition>> findByInitiatedBy(String initiatedBy) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<MasteryTransition>> findByTimeRange(Instant from, Instant to) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Optional<MasteryTransition>> findLatestByMasteryId(String masteryId) {
            return Promise.of(Optional.empty());
        }
    }
}

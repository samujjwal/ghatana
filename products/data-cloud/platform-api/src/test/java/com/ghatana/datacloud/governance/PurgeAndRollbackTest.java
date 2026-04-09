/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for data purge operations with rollback safety — covering purge
 * execution, pre-purge snapshots, and restoration from snapshots.
 *
 * @doc.type    class
 * @doc.purpose Tests for data purge and rollback behavior in governance layer
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("Purge and Rollback Tests")
class PurgeAndRollbackTest extends EventloopTestBase {

    // ── Data model ────────────────────────────────────────────────────────────

    record DataEntity(String id, String tenantId, String payload, Instant createdAt) {}

    record PurgeSnapshot(String snapshotId, String tenantId, List<DataEntity> entities, Instant takenAt) {}

    private PurgeManager purgeManager;

    @BeforeEach
    void setUp() {
        purgeManager = new PurgeManager();
        // Seed some test data across two tenants
        for (int i = 1; i <= 5; i++) {
            purgeManager.insert(new DataEntity("entity-A-" + i, "tenant-A",
                    "payload-" + i, Instant.now().minusSeconds(3600 * i)));
        }
        for (int i = 1; i <= 3; i++) {
            purgeManager.insert(new DataEntity("entity-B-" + i, "tenant-B",
                    "payload-" + i, Instant.now().minusSeconds(1800 * i)));
        }
    }

    // ── Purge execution ───────────────────────────────────────────────────────

    @Test
    @DisplayName("purge removes all entities for the specified tenant")
    void purgeRemovesAllEntitiesForTenant() {
        assertThat(purgeManager.count("tenant-A")).isEqualTo(5);

        PurgeResult result = purgeManager.purge("tenant-A");

        assertThat(result.entitiesRemoved()).isEqualTo(5);
        assertThat(purgeManager.count("tenant-A")).isEqualTo(0);
    }

    @Test
    @DisplayName("purge does not affect other tenants' data")
    void purgeDoesNotAffectOtherTenants() {
        purgeManager.purge("tenant-A");
        assertThat(purgeManager.count("tenant-B")).isEqualTo(3);
    }

    @Test
    @DisplayName("purge of a tenant with no data completes with zero removals")
    void purgeOfEmptyTenantReturnsZero() {
        PurgeResult result = purgeManager.purge("tenant-ghost");
        assertThat(result.entitiesRemoved()).isEqualTo(0);
    }

    @Test
    @DisplayName("purge by cutoff timestamp removes only expired entities")
    void purgeByTimestampRemovesOnlyExpiredEntities() {
        // Entity inserted 5+ hours ago should be purged; newer ones should survive
        Instant cutoff = Instant.now().minusSeconds(4 * 3600); // 4 hours ago

        PurgeResult result = purgeManager.purgeBefore("tenant-A", cutoff);

        // entity-A-5 (5h ago) is before cutoff; entity-A-4 (4h ago) is right at/before too
        assertThat(result.entitiesRemoved()).isGreaterThan(0);
        assertThat(purgeManager.count("tenant-A")).isLessThan(5);
    }

    // ── Pre-purge snapshot ────────────────────────────────────────────────────

    @Test
    @DisplayName("snapshot taken before purge captures all entities for the tenant")
    void snapshotCapturesAllEntities() {
        PurgeSnapshot snapshot = purgeManager.snapshot("tenant-A");

        assertThat(snapshot.snapshotId()).isNotBlank();
        assertThat(snapshot.tenantId()).isEqualTo("tenant-A");
        assertThat(snapshot.entities()).hasSize(5);
        assertThat(snapshot.takenAt()).isNotNull();
    }

    @Test
    @DisplayName("snapshot does not remove entities from the store")
    void snapshotDoesNotRemoveEntities() {
        purgeManager.snapshot("tenant-A");
        assertThat(purgeManager.count("tenant-A")).isEqualTo(5);
    }

    // ── Rollback from snapshot ────────────────────────────────────────────────

    @Test
    @DisplayName("rollback after purge restores all entities from snapshot")
    void rollbackRestoresEntitiesAfterPurge() {
        PurgeSnapshot snapshot = purgeManager.snapshot("tenant-A");

        purgeManager.purge("tenant-A");
        assertThat(purgeManager.count("tenant-A")).isEqualTo(0);

        purgeManager.restore(snapshot);
        assertThat(purgeManager.count("tenant-A")).isEqualTo(5);
    }

    @Test
    @DisplayName("rollback restores correct entity IDs and payloads")
    void rollbackRestoresCorrectEntities() {
        PurgeSnapshot snapshot = purgeManager.snapshot("tenant-A");
        Set<String> originalIds = snapshot.entities().stream()
                .map(DataEntity::id)
                .collect(java.util.stream.Collectors.toSet());

        purgeManager.purge("tenant-A");
        purgeManager.restore(snapshot);

        List<DataEntity> restored = purgeManager.findByTenant("tenant-A");
        Set<String> restoredIds = restored.stream()
                .map(DataEntity::id)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(restoredIds).isEqualTo(originalIds);
    }

    @Test
    @DisplayName("rollback does not affect other tenants")
    void rollbackDoesNotAffectOtherTenants() {
        PurgeSnapshot snapshotA = purgeManager.snapshot("tenant-A");
        purgeManager.purge("tenant-A");
        purgeManager.restore(snapshotA);

        // tenant-B was not touched
        assertThat(purgeManager.count("tenant-B")).isEqualTo(3);
    }

    @Test
    @DisplayName("restore of snapshot for already-populated tenant merges without duplicate IDs")
    void restoreMergesWithoutDuplicates() {
        PurgeSnapshot snapshot = purgeManager.snapshot("tenant-A");
        // Do NOT purge — restore on top of existing data
        purgeManager.restore(snapshot);

        // After restore over existing data, count must remain 5 (no duplicates)
        assertThat(purgeManager.count("tenant-A")).isEqualTo(5);
    }

    // ── Purge manager implementation (for tests) ──────────────────────────────

    record PurgeResult(int entitiesRemoved) {}

    static class PurgeManager {
        private final ConcurrentHashMap<String, DataEntity> store = new ConcurrentHashMap<>();

        void insert(DataEntity entity) {
            store.put(entity.tenantId() + "|" + entity.id(), entity);
        }

        int count(String tenantId) {
            return (int) store.values().stream()
                    .filter(e -> e.tenantId().equals(tenantId)).count();
        }

        List<DataEntity> findByTenant(String tenantId) {
            return store.values().stream()
                    .filter(e -> e.tenantId().equals(tenantId))
                    .toList();
        }

        PurgeResult purge(String tenantId) {
            List<String> keys = store.entrySet().stream()
                    .filter(entry -> entry.getValue().tenantId().equals(tenantId))
                    .map(Map.Entry::getKey)
                    .toList();
            keys.forEach(store::remove);
            return new PurgeResult(keys.size());
        }

        PurgeResult purgeBefore(String tenantId, Instant cutoff) {
            List<String> keys = store.entrySet().stream()
                    .filter(e -> e.getValue().tenantId().equals(tenantId))
                    .filter(e -> e.getValue().createdAt().isBefore(cutoff))
                    .map(Map.Entry::getKey)
                    .toList();
            keys.forEach(store::remove);
            return new PurgeResult(keys.size());
        }

        PurgeSnapshot snapshot(String tenantId) {
            List<DataEntity> entities = findByTenant(tenantId);
            return new PurgeSnapshot(UUID.randomUUID().toString(), tenantId,
                    List.copyOf(entities), Instant.now());
        }

        void restore(PurgeSnapshot snapshot) {
            snapshot.entities().forEach(entity ->
                    store.put(entity.tenantId() + "|" + entity.id(), entity));
        }
    }
}

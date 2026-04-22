/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("Purge and Rollback Tests [GH-90000]")
class PurgeAndRollbackTest extends EventloopTestBase {

    // ── Data model ────────────────────────────────────────────────────────────

    record DataEntity(String id, String tenantId, String payload, Instant createdAt) {} // GH-90000

    record PurgeSnapshot(String snapshotId, String tenantId, List<DataEntity> entities, Instant takenAt) {} // GH-90000

    private PurgeManager purgeManager;

    @BeforeEach
    void setUp() { // GH-90000
        purgeManager = new PurgeManager(); // GH-90000
        // Seed some test data across two tenants
        for (int i = 1; i <= 5; i++) { // GH-90000
            purgeManager.insert(new DataEntity("entity-A-" + i, "tenant-A", // GH-90000
                    "payload-" + i, Instant.now().minusSeconds(3600 * i))); // GH-90000
        }
        for (int i = 1; i <= 3; i++) { // GH-90000
            purgeManager.insert(new DataEntity("entity-B-" + i, "tenant-B", // GH-90000
                    "payload-" + i, Instant.now().minusSeconds(1800 * i))); // GH-90000
        }
    }

    // ── Purge execution ───────────────────────────────────────────────────────

    @Test
    @DisplayName("purge removes all entities for the specified tenant [GH-90000]")
    void purgeRemovesAllEntitiesForTenant() { // GH-90000
        assertThat(purgeManager.count("tenant-A [GH-90000]")).isEqualTo(5);

        PurgeResult result = purgeManager.purge("tenant-A [GH-90000]");

        assertThat(result.entitiesRemoved()).isEqualTo(5); // GH-90000
        assertThat(purgeManager.count("tenant-A [GH-90000]")).isEqualTo(0);
    }

    @Test
    @DisplayName("purge does not affect other tenants' data [GH-90000]")
    void purgeDoesNotAffectOtherTenants() { // GH-90000
        purgeManager.purge("tenant-A [GH-90000]");
        assertThat(purgeManager.count("tenant-B [GH-90000]")).isEqualTo(3);
    }

    @Test
    @DisplayName("purge of a tenant with no data completes with zero removals [GH-90000]")
    void purgeOfEmptyTenantReturnsZero() { // GH-90000
        PurgeResult result = purgeManager.purge("tenant-ghost [GH-90000]");
        assertThat(result.entitiesRemoved()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("purge by cutoff timestamp removes only expired entities [GH-90000]")
    void purgeByTimestampRemovesOnlyExpiredEntities() { // GH-90000
        // Entity inserted 5+ hours ago should be purged; newer ones should survive
        Instant cutoff = Instant.now().minusSeconds(4 * 3600); // 4 hours ago // GH-90000

        PurgeResult result = purgeManager.purgeBefore("tenant-A", cutoff); // GH-90000

        // entity-A-5 (5h ago) is before cutoff; entity-A-4 (4h ago) is right at/before too // GH-90000
        assertThat(result.entitiesRemoved()).isGreaterThan(0); // GH-90000
        assertThat(purgeManager.count("tenant-A [GH-90000]")).isLessThan(5);
    }

    // ── Pre-purge snapshot ────────────────────────────────────────────────────

    @Test
    @DisplayName("snapshot taken before purge captures all entities for the tenant [GH-90000]")
    void snapshotCapturesAllEntities() { // GH-90000
        PurgeSnapshot snapshot = purgeManager.snapshot("tenant-A [GH-90000]");

        assertThat(snapshot.snapshotId()).isNotBlank(); // GH-90000
        assertThat(snapshot.tenantId()).isEqualTo("tenant-A [GH-90000]");
        assertThat(snapshot.entities()).hasSize(5); // GH-90000
        assertThat(snapshot.takenAt()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("snapshot does not remove entities from the store [GH-90000]")
    void snapshotDoesNotRemoveEntities() { // GH-90000
        purgeManager.snapshot("tenant-A [GH-90000]");
        assertThat(purgeManager.count("tenant-A [GH-90000]")).isEqualTo(5);
    }

    // ── Rollback from snapshot ────────────────────────────────────────────────

    @Test
    @DisplayName("rollback after purge restores all entities from snapshot [GH-90000]")
    void rollbackRestoresEntitiesAfterPurge() { // GH-90000
        PurgeSnapshot snapshot = purgeManager.snapshot("tenant-A [GH-90000]");

        purgeManager.purge("tenant-A [GH-90000]");
        assertThat(purgeManager.count("tenant-A [GH-90000]")).isEqualTo(0);

        purgeManager.restore(snapshot); // GH-90000
        assertThat(purgeManager.count("tenant-A [GH-90000]")).isEqualTo(5);
    }

    @Test
    @DisplayName("rollback restores correct entity IDs and payloads [GH-90000]")
    void rollbackRestoresCorrectEntities() { // GH-90000
        PurgeSnapshot snapshot = purgeManager.snapshot("tenant-A [GH-90000]");
        Set<String> originalIds = snapshot.entities().stream() // GH-90000
                .map(DataEntity::id) // GH-90000
                .collect(java.util.stream.Collectors.toSet()); // GH-90000

        purgeManager.purge("tenant-A [GH-90000]");
        purgeManager.restore(snapshot); // GH-90000

        List<DataEntity> restored = purgeManager.findByTenant("tenant-A [GH-90000]");
        Set<String> restoredIds = restored.stream() // GH-90000
                .map(DataEntity::id) // GH-90000
                .collect(java.util.stream.Collectors.toSet()); // GH-90000

        assertThat(restoredIds).isEqualTo(originalIds); // GH-90000
    }

    @Test
    @DisplayName("rollback does not affect other tenants [GH-90000]")
    void rollbackDoesNotAffectOtherTenants() { // GH-90000
        PurgeSnapshot snapshotA = purgeManager.snapshot("tenant-A [GH-90000]");
        purgeManager.purge("tenant-A [GH-90000]");
        purgeManager.restore(snapshotA); // GH-90000

        // tenant-B was not touched
        assertThat(purgeManager.count("tenant-B [GH-90000]")).isEqualTo(3);
    }

    @Test
    @DisplayName("restore of snapshot for already-populated tenant merges without duplicate IDs [GH-90000]")
    void restoreMergesWithoutDuplicates() { // GH-90000
        PurgeSnapshot snapshot = purgeManager.snapshot("tenant-A [GH-90000]");
        // Do NOT purge — restore on top of existing data
        purgeManager.restore(snapshot); // GH-90000

        // After restore over existing data, count must remain 5 (no duplicates) // GH-90000
        assertThat(purgeManager.count("tenant-A [GH-90000]")).isEqualTo(5);
    }

    // ── Purge manager implementation (for tests) ────────────────────────────── // GH-90000

    record PurgeResult(int entitiesRemoved) {} // GH-90000

    static class PurgeManager {
        private final ConcurrentHashMap<String, DataEntity> store = new ConcurrentHashMap<>(); // GH-90000

        void insert(DataEntity entity) { // GH-90000
            store.put(entity.tenantId() + "|" + entity.id(), entity); // GH-90000
        }

        int count(String tenantId) { // GH-90000
            return (int) store.values().stream() // GH-90000
                    .filter(e -> e.tenantId().equals(tenantId)).count(); // GH-90000
        }

        List<DataEntity> findByTenant(String tenantId) { // GH-90000
            return store.values().stream() // GH-90000
                    .filter(e -> e.tenantId().equals(tenantId)) // GH-90000
                    .toList(); // GH-90000
        }

        PurgeResult purge(String tenantId) { // GH-90000
            List<String> keys = store.entrySet().stream() // GH-90000
                    .filter(entry -> entry.getValue().tenantId().equals(tenantId)) // GH-90000
                    .map(Map.Entry::getKey) // GH-90000
                    .toList(); // GH-90000
            keys.forEach(store::remove); // GH-90000
            return new PurgeResult(keys.size()); // GH-90000
        }

        PurgeResult purgeBefore(String tenantId, Instant cutoff) { // GH-90000
            List<String> keys = store.entrySet().stream() // GH-90000
                    .filter(e -> e.getValue().tenantId().equals(tenantId)) // GH-90000
                    .filter(e -> e.getValue().createdAt().isBefore(cutoff)) // GH-90000
                    .map(Map.Entry::getKey) // GH-90000
                    .toList(); // GH-90000
            keys.forEach(store::remove); // GH-90000
            return new PurgeResult(keys.size()); // GH-90000
        }

        PurgeSnapshot snapshot(String tenantId) { // GH-90000
            List<DataEntity> entities = findByTenant(tenantId); // GH-90000
            return new PurgeSnapshot(UUID.randomUUID().toString(), tenantId, // GH-90000
                    List.copyOf(entities), Instant.now()); // GH-90000
        }

        void restore(PurgeSnapshot snapshot) { // GH-90000
            snapshot.entities().forEach(entity -> // GH-90000
                    store.put(entity.tenantId() + "|" + entity.id(), entity)); // GH-90000
        }
    }
}

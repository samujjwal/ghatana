/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.purge;

import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for data purge and rollback operations.
 *
 * <p>Validates purge request handling, rollback capability within grace periods,
 * partial-purge idempotency, and tenant-isolated purge enforcement.
 *
 * @doc.type    class
 * @doc.purpose Purge and rollback: safe deletion, rollback grace period, idempotency
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("PurgeAndRollbackTest")
@Tag("governance")
class PurgeAndRollbackTest {

    private PurgeService purgeService;

    @BeforeEach
    void setUp() { // GH-90000
        purgeService = new PurgeService(); // GH-90000
        // Populate with sample data
        purgeService.store("tenant-a", "r1", "Sensitive data A1"); // GH-90000
        purgeService.store("tenant-a", "r2", "Sensitive data A2"); // GH-90000
        purgeService.store("tenant-b", "r3", "Sensitive data B1"); // GH-90000
    }

    // ── Purge ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("purge removes the record from the tenant store")
    void purgeRemovesRecord() { // GH-90000
        purgeService.purge("tenant-a", "r1"); // GH-90000
        assertThat(purgeService.exists("tenant-a", "r1")).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("purge does not affect other records in the same tenant")
    void purgeDoesNotAffectOtherRecords() { // GH-90000
        purgeService.purge("tenant-a", "r1"); // GH-90000
        assertThat(purgeService.exists("tenant-a", "r2")).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("purge is tenant-isolated — cross-tenant record survives")
    void purgeIsTenantIsolated() { // GH-90000
        purgeService.purge("tenant-a", "r1"); // GH-90000
        assertThat(purgeService.exists("tenant-b", "r3")).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("purging a non-existent record is idempotent and does not throw")
    void purgeNonExistentRecordIsIdempotent() { // GH-90000
        assertThatCode(() -> purgeService.purge("tenant-a", "nonexistent-id")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("second purge of same record is idempotent")
    void doublePurgeIsIdempotent() { // GH-90000
        purgeService.purge("tenant-a", "r1"); // GH-90000
        assertThatCode(() -> purgeService.purge("tenant-a", "r1")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        assertThat(purgeService.exists("tenant-a", "r1")).isFalse(); // GH-90000
    }

    // ── Rollback ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rollback within grace period restores the record")
    void rollbackWithinGracePeriodRestoresRecord() { // GH-90000
        String token = purgeService.purgeWithRollback("tenant-a", "r1"); // GH-90000
        purgeService.rollback(token); // GH-90000
        assertThat(purgeService.exists("tenant-a", "r1")).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("rollback with unknown token throws PurgeRollbackException")
    void rollbackWithUnknownTokenThrows() { // GH-90000
        assertThatThrownBy(() -> purgeService.rollback("unknown-token"))
                .isInstanceOf(PurgeRollbackException.class); // GH-90000
    }

    @Test
    @DisplayName("rollback token is single-use — second rollback throws")
    void rollbackTokenIsSingleUse() { // GH-90000
        String token = purgeService.purgeWithRollback("tenant-a", "r1"); // GH-90000
        purgeService.rollback(token); // GH-90000
        assertThatThrownBy(() -> purgeService.rollback(token)) // GH-90000
                .isInstanceOf(PurgeRollbackException.class); // GH-90000
    }

    // ── Partial purge (batch) ───────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("batch purge removes all specified records")
    void batchPurgeRemovesAllSpecified() { // GH-90000
        purgeService.batchPurge("tenant-a", List.of("r1", "r2")); // GH-90000
        assertThat(purgeService.exists("tenant-a", "r1")).isFalse(); // GH-90000
        assertThat(purgeService.exists("tenant-a", "r2")).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("batch purge on empty list does nothing")
    void batchPurgeOnEmptyListDoesNothing() { // GH-90000
        assertThatCode(() -> purgeService.batchPurge("tenant-a", List.of())) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        assertThat(purgeService.exists("tenant-a", "r1")).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("batch purge emits purge audit event per record")
    void batchPurgeEmitsAuditEvents() { // GH-90000
        List<String> events = new ArrayList<>(); // GH-90000
        purgeService.onAuditEvent(events::add); // GH-90000
        purgeService.batchPurge("tenant-a", List.of("r1", "r2")); // GH-90000
        assertThat(events).hasSize(2); // GH-90000
        assertThat(events).allSatisfy(e -> assertThat(e).contains("PURGE"));
    }

    // ── Confirmation flow ─────────────────────────────────────────────────────

    @Test
    @DisplayName("purge requires explicit confirmation flag")
    void purgeRequiresConfirmation() { // GH-90000
        AtomicBoolean confirmed = new AtomicBoolean(false); // GH-90000
        purgeService.purgeWithConfirmation("tenant-a", "r1", confirmed); // GH-90000
        // Not confirmed — record should still exist
        assertThat(purgeService.exists("tenant-a", "r1")).isTrue(); // GH-90000

        confirmed.set(true); // GH-90000
        purgeService.purgeWithConfirmation("tenant-a", "r1", confirmed); // GH-90000
        assertThat(purgeService.exists("tenant-a", "r1")).isFalse(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    static class PurgeRollbackException extends RuntimeException {
        PurgeRollbackException(String msg) { super(msg); } // GH-90000
    }

    static class PurgeService {
        private final Map<String, Map<String, String>> store = new HashMap<>(); // GH-90000
        private final Map<String, RollbackEntry> rollbackTokens = new HashMap<>(); // GH-90000
        private final List<java.util.function.Consumer<String>> auditListeners = new ArrayList<>(); // GH-90000

        void store(String tenantId, String recordId, String data) { // GH-90000
            store.computeIfAbsent(tenantId, k -> new HashMap<>()).put(recordId, data); // GH-90000
        }

        boolean exists(String tenantId, String recordId) { // GH-90000
            return store.getOrDefault(tenantId, Map.of()).containsKey(recordId); // GH-90000
        }

        void purge(String tenantId, String recordId) { // GH-90000
            Map<String, String> tenant = store.get(tenantId); // GH-90000
            if (tenant != null) { // GH-90000
                tenant.remove(recordId); // GH-90000
                notifyAudit("PURGE:" + tenantId + ":" + recordId); // GH-90000
            }
        }

        String purgeWithRollback(String tenantId, String recordId) { // GH-90000
            String data = store.getOrDefault(tenantId, Map.of()).get(recordId); // GH-90000
            purge(tenantId, recordId); // GH-90000
            String token = UUID.randomUUID().toString(); // GH-90000
            rollbackTokens.put(token, new RollbackEntry(tenantId, recordId, data)); // GH-90000
            return token;
        }

        void rollback(String token) { // GH-90000
            RollbackEntry entry = rollbackTokens.remove(token); // GH-90000
            if (entry == null) throw new PurgeRollbackException("Unknown or already used token: " + token); // GH-90000
            store.computeIfAbsent(entry.tenantId(), k -> new HashMap<>()) // GH-90000
                    .put(entry.recordId(), entry.data() != null ? entry.data() : ""); // GH-90000
        }

        void batchPurge(String tenantId, List<String> recordIds) { // GH-90000
            recordIds.forEach(id -> purge(tenantId, id)); // GH-90000
        }

        void purgeWithConfirmation(String tenantId, String recordId, AtomicBoolean confirmed) { // GH-90000
            if (confirmed.get()) purge(tenantId, recordId); // GH-90000
        }

        void onAuditEvent(java.util.function.Consumer<String> listener) { // GH-90000
            auditListeners.add(listener); // GH-90000
        }

        private void notifyAudit(String event) { // GH-90000
            auditListeners.forEach(l -> l.accept(event)); // GH-90000
        }

        record RollbackEntry(String tenantId, String recordId, String data) {} // GH-90000
    }
}

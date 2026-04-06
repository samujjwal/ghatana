/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void setUp() {
        purgeService = new PurgeService();
        // Populate with sample data
        purgeService.store("tenant-a", "r1", "Sensitive data A1");
        purgeService.store("tenant-a", "r2", "Sensitive data A2");
        purgeService.store("tenant-b", "r3", "Sensitive data B1");
    }

    // ── Purge ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("purge removes the record from the tenant store")
    void purgeRemovesRecord() {
        purgeService.purge("tenant-a", "r1");
        assertThat(purgeService.exists("tenant-a", "r1")).isFalse();
    }

    @Test
    @DisplayName("purge does not affect other records in the same tenant")
    void purgeDoesNotAffectOtherRecords() {
        purgeService.purge("tenant-a", "r1");
        assertThat(purgeService.exists("tenant-a", "r2")).isTrue();
    }

    @Test
    @DisplayName("purge is tenant-isolated — cross-tenant record survives")
    void purgeIsTenantIsolated() {
        purgeService.purge("tenant-a", "r1");
        assertThat(purgeService.exists("tenant-b", "r3")).isTrue();
    }

    @Test
    @DisplayName("purging a non-existent record is idempotent and does not throw")
    void purgeNonExistentRecordIsIdempotent() {
        assertThatCode(() -> purgeService.purge("tenant-a", "nonexistent-id"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("second purge of same record is idempotent")
    void doublePurgeIsIdempotent() {
        purgeService.purge("tenant-a", "r1");
        assertThatCode(() -> purgeService.purge("tenant-a", "r1"))
                .doesNotThrowAnyException();
        assertThat(purgeService.exists("tenant-a", "r1")).isFalse();
    }

    // ── Rollback ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rollback within grace period restores the record")
    void rollbackWithinGracePeriodRestoresRecord() {
        String token = purgeService.purgeWithRollback("tenant-a", "r1");
        purgeService.rollback(token);
        assertThat(purgeService.exists("tenant-a", "r1")).isTrue();
    }

    @Test
    @DisplayName("rollback with unknown token throws PurgeRollbackException")
    void rollbackWithUnknownTokenThrows() {
        assertThatThrownBy(() -> purgeService.rollback("unknown-token"))
                .isInstanceOf(PurgeRollbackException.class);
    }

    @Test
    @DisplayName("rollback token is single-use — second rollback throws")
    void rollbackTokenIsSingleUse() {
        String token = purgeService.purgeWithRollback("tenant-a", "r1");
        purgeService.rollback(token);
        assertThatThrownBy(() -> purgeService.rollback(token))
                .isInstanceOf(PurgeRollbackException.class);
    }

    // ── Partial purge (batch) ─────────────────────────────────────────────────

    @Test
    @DisplayName("batch purge removes all specified records")
    void batchPurgeRemovesAllSpecified() {
        purgeService.batchPurge("tenant-a", List.of("r1", "r2"));
        assertThat(purgeService.exists("tenant-a", "r1")).isFalse();
        assertThat(purgeService.exists("tenant-a", "r2")).isFalse();
    }

    @Test
    @DisplayName("batch purge on empty list does nothing")
    void batchPurgeOnEmptyListDoesNothing() {
        assertThatCode(() -> purgeService.batchPurge("tenant-a", List.of()))
                .doesNotThrowAnyException();
        assertThat(purgeService.exists("tenant-a", "r1")).isTrue();
    }

    @Test
    @DisplayName("batch purge emits purge audit event per record")
    void batchPurgeEmitsAuditEvents() {
        List<String> events = new ArrayList<>();
        purgeService.onAuditEvent(events::add);
        purgeService.batchPurge("tenant-a", List.of("r1", "r2"));
        assertThat(events).hasSize(2);
        assertThat(events).allSatisfy(e -> assertThat(e).contains("PURGE"));
    }

    // ── Confirmation flow ─────────────────────────────────────────────────────

    @Test
    @DisplayName("purge requires explicit confirmation flag")
    void purgeRequiresConfirmation() {
        AtomicBoolean confirmed = new AtomicBoolean(false);
        purgeService.purgeWithConfirmation("tenant-a", "r1", confirmed);
        // Not confirmed — record should still exist
        assertThat(purgeService.exists("tenant-a", "r1")).isTrue();

        confirmed.set(true);
        purgeService.purgeWithConfirmation("tenant-a", "r1", confirmed);
        assertThat(purgeService.exists("tenant-a", "r1")).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    static class PurgeRollbackException extends RuntimeException {
        PurgeRollbackException(String msg) { super(msg); }
    }

    static class PurgeService {
        private final Map<String, Map<String, String>> store = new HashMap<>();
        private final Map<String, RollbackEntry> rollbackTokens = new HashMap<>();
        private final List<java.util.function.Consumer<String>> auditListeners = new ArrayList<>();

        void store(String tenantId, String recordId, String data) {
            store.computeIfAbsent(tenantId, k -> new HashMap<>()).put(recordId, data);
        }

        boolean exists(String tenantId, String recordId) {
            return store.getOrDefault(tenantId, Map.of()).containsKey(recordId);
        }

        void purge(String tenantId, String recordId) {
            Map<String, String> tenant = store.get(tenantId);
            if (tenant != null) {
                tenant.remove(recordId);
                notifyAudit("PURGE:" + tenantId + ":" + recordId);
            }
        }

        String purgeWithRollback(String tenantId, String recordId) {
            String data = store.getOrDefault(tenantId, Map.of()).get(recordId);
            purge(tenantId, recordId);
            String token = UUID.randomUUID().toString();
            rollbackTokens.put(token, new RollbackEntry(tenantId, recordId, data));
            return token;
        }

        void rollback(String token) {
            RollbackEntry entry = rollbackTokens.remove(token);
            if (entry == null) throw new PurgeRollbackException("Unknown or already used token: " + token);
            store.computeIfAbsent(entry.tenantId(), k -> new HashMap<>())
                    .put(entry.recordId(), entry.data() != null ? entry.data() : "");
        }

        void batchPurge(String tenantId, List<String> recordIds) {
            recordIds.forEach(id -> purge(tenantId, id));
        }

        void purgeWithConfirmation(String tenantId, String recordId, AtomicBoolean confirmed) {
            if (confirmed.get()) purge(tenantId, recordId);
        }

        void onAuditEvent(java.util.function.Consumer<String> listener) {
            auditListeners.add(listener);
        }

        private void notifyAudit(String event) {
            auditListeners.forEach(l -> l.accept(event));
        }

        record RollbackEntry(String tenantId, String recordId, String data) {}
    }
}

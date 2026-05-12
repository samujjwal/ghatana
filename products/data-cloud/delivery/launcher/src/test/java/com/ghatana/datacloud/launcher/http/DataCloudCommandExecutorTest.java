/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.spi.TransactionManager;
import com.ghatana.datacloud.spi.WriteIdempotencyStore;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditQuery;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DataCloudCommandExecutor}.
 *
 * <p>Uses lightweight in-process test doubles — no mocking framework required.
 *
 * @doc.type class
 * @doc.purpose Verify atomic, idempotent, audited write-command executor
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudCommandExecutor — P0-2 write-command executor")
class DataCloudCommandExecutorTest extends EventloopTestBase {

    // =========================================================================
    // Test doubles
    // =========================================================================

    private static final class InMemoryIdempotencyStore implements WriteIdempotencyStore {
        final Map<String, Map<String, Object>> store = new HashMap<>();

        @Override
        public Optional<Map<String, Object>> get(String tenantId, String scope, String key) {
            return Optional.ofNullable(store.get(tenantId + ":" + scope + ":" + key));
        }

        @Override
        public void put(String tenantId, String scope, String key, Map<String, Object> body) {
            store.put(tenantId + ":" + scope + ":" + key, body);
        }
    }

    private static final class InMemoryTransactionManager implements TransactionManager {
        final AtomicInteger transactionCount = new AtomicInteger(0);

        @Override
        public <T> Promise<T> executeInTransaction(java.util.function.Supplier<Promise<T>> op) {
            transactionCount.incrementAndGet();
            return op.get();
        }

        @Override
        public <T> Promise<T> executeInTransaction(String tenantId, java.util.function.Supplier<Promise<T>> op) {
            transactionCount.incrementAndGet();
            return op.get();
        }

        @Override
        public <T> Promise<T> executeInTransactionWithContext(String tenantId,
                                                              TransactionalOperation<T> op) {
            transactionCount.incrementAndGet();
            return op.execute(null);
        }
    }

    private static final class RecordingAuditService implements AuditService {
        final List<AuditEvent> recorded = new ArrayList<>();

        @Override
        public Promise<Void> record(AuditEvent event) {
            recorded.add(event);
            return Promise.complete();
        }

        @Override
        public Promise<List<AuditEvent>> query(AuditQuery query) {
            return Promise.of(List.copyOf(recorded));
        }

        @Override
        public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
            return Promise.of(List.copyOf(recorded));
        }

        @Override
        public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
            return Promise.of(List.copyOf(recorded));
        }
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private InMemoryIdempotencyStore idempotencyStore;
    private InMemoryTransactionManager transactionManager;
    private RecordingAuditService auditService;

    @BeforeEach
    void setUp() {
        idempotencyStore = new InMemoryIdempotencyStore();
        transactionManager = new InMemoryTransactionManager();
        auditService = new RecordingAuditService();
    }

    private DataCloudCommandExecutor executor() {
        return new DataCloudCommandExecutor(idempotencyStore, transactionManager, auditService);
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Nested
    @DisplayName("Successful execution")
    class SuccessfulExecution {

        @Test
        @DisplayName("returns command result on first execution")
        void firstExecution_returnsResult() {
            Map<String, Object> result = runPromise(() -> executor()
                .execute("t1", "entities:profiles", "key-001", "entity.create",
                    () -> Promise.of(Map.of("id", "e1", "status", "created"))));

            assertThat(result).containsEntry("id", "e1").containsEntry("status", "created");
        }

        @Test
        @DisplayName("wraps command in a transaction boundary")
        void firstExecution_usesTransaction() {
            runPromise(() -> executor().execute("t1", "entities:profiles", "key-002", "entity.create",
                () -> Promise.of(Map.of("id", "e2"))));

            assertThat(transactionManager.transactionCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("emits an audit event after successful commit")
        void firstExecution_emitsAudit() {
            runPromise(() -> executor().execute("t1", "entities:profiles", "key-003", "entity.create",
                () -> Promise.of(Map.of("id", "e3"))));

            assertThat(auditService.recorded).hasSize(1);
            assertThat(auditService.recorded.get(0).getTenantId()).isEqualTo("t1");
        }

        @Test
        @DisplayName("persists result in idempotency store after success")
        void firstExecution_persistsIdempotencyKey() {
            runPromise(() -> executor().execute("t1", "entities:profiles", "key-004", "entity.create",
                () -> Promise.of(Map.of("id", "e4"))));

            Optional<Map<String, Object>> cached = idempotencyStore.get("t1", "entities:profiles", "key-004");
            assertThat(cached).isPresent();
            assertThat(cached.get()).containsEntry("id", "e4");
        }
    }

    // =========================================================================
    // Idempotency replay
    // =========================================================================

    @Nested
    @DisplayName("Idempotency replay")
    class IdempotencyReplay {

        @Test
        @DisplayName("returns cached result on replay without re-executing command")
        void replay_returnsCachedResult() {
            DataCloudCommandExecutor ex = executor();
            AtomicInteger callCount = new AtomicInteger(0);

            runPromise(() -> ex.execute("t1", "entities:profiles", "key-replay", "entity.create",
                () -> { callCount.incrementAndGet(); return Promise.of(Map.of("id", "e1")); }));
            runPromise(() -> ex.execute("t1", "entities:profiles", "key-replay", "entity.create",
                () -> { callCount.incrementAndGet(); return Promise.of(Map.of("id", "e1")); }));

            assertThat(callCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("does not emit a second audit event on replay")
        void replay_noSecondAuditEvent() {
            DataCloudCommandExecutor ex = executor();
            runPromise(() -> ex.execute("t1", "entities:profiles", "key-audit", "entity.create",
                () -> Promise.of(Map.of("id", "e1"))));
            runPromise(() -> ex.execute("t1", "entities:profiles", "key-audit", "entity.create",
                () -> Promise.of(Map.of("id", "e1"))));

            assertThat(auditService.recorded).hasSize(1);
        }

        @Test
        @DisplayName("different tenants with same key are not deduplicated")
        void replay_differentTenantsAreIsolated() {
            DataCloudCommandExecutor ex = executor();
            AtomicInteger calls = new AtomicInteger(0);

            runPromise(() -> ex.execute("t1", "entities:profiles", "key-shared", "entity.create",
                () -> { calls.incrementAndGet(); return Promise.of(Map.of("id", "e1")); }));
            runPromise(() -> ex.execute("t2", "entities:profiles", "key-shared", "entity.create",
                () -> { calls.incrementAndGet(); return Promise.of(Map.of("id", "e2")); }));

            assertThat(calls.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("null idempotency key skips dedup and always re-executes")
        void nullKey_skipsDedup() {
            DataCloudCommandExecutor ex = executor();
            AtomicInteger calls = new AtomicInteger(0);

            runPromise(() -> ex.execute("t1", "entities:profiles", null, "entity.create",
                () -> { calls.incrementAndGet(); return Promise.of(Map.of("id", "e1")); }));
            runPromise(() -> ex.execute("t1", "entities:profiles", null, "entity.create",
                () -> { calls.incrementAndGet(); return Promise.of(Map.of("id", "e2")); }));

            assertThat(calls.get()).isEqualTo(2);
        }
    }

    // =========================================================================
    // Graceful degradation — null dependencies (non-production)
    // =========================================================================

    @Nested
    @DisplayName("Graceful degradation — null dependencies")
    class GracefulDegradation {

        @Test
        @DisplayName("executes without idempotency store (warns but does not throw)")
        void noIdempotencyStore_executesDirectly() {
            DataCloudCommandExecutor ex = new DataCloudCommandExecutor(null, transactionManager, auditService);
            Map<String, Object> result = runPromise(() -> ex.execute("t1", "entities:profiles", "key-1", "entity.create",
                () -> Promise.of(Map.of("id", "e1"))));
            assertThat(result).containsEntry("id", "e1");
        }

        @Test
        @DisplayName("executes without transaction manager (warns but does not throw)")
        void noTransactionManager_executesDirectly() {
            DataCloudCommandExecutor ex = new DataCloudCommandExecutor(idempotencyStore, null, auditService);
            Map<String, Object> result = runPromise(() -> ex.execute("t1", "entities:profiles", "key-2", "entity.create",
                () -> Promise.of(Map.of("id", "e2"))));
            assertThat(result).containsEntry("id", "e2");
        }

        @Test
        @DisplayName("executes without audit service — no exception")
        void noAuditService_executesWithoutAudit() {
            DataCloudCommandExecutor ex = new DataCloudCommandExecutor(idempotencyStore, transactionManager, null);
            Map<String, Object> result = runPromise(() -> ex.execute("t1", "entities:profiles", "key-3", "entity.create",
                () -> Promise.of(Map.of("id", "e3"))));
            assertThat(result).containsEntry("id", "e3");
        }

        @Test
        @DisplayName("executes with all null dependencies (local profile)")
        void allNull_executesDirectly() {
            DataCloudCommandExecutor ex = new DataCloudCommandExecutor(null, null, null);
            Map<String, Object> result = runPromise(() -> ex.execute("t1", "entities:profiles", "key-4", "entity.create",
                () -> Promise.of(Map.of("id", "e4"))));
            assertThat(result).containsEntry("id", "e4");
        }
    }

    // =========================================================================
    // Null guard validation
    // =========================================================================

    @Nested
    @DisplayName("Null guard validation")
    class NullGuards {

        @Test
        @DisplayName("null tenantId throws NullPointerException")
        void nullTenantId_throws() {
            DataCloudCommandExecutor ex = executor();
            assertThatThrownBy(() ->
                ex.execute(null, "entities:profiles", "k", "entity.create",
                    () -> Promise.of(Map.of())))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null scope throws NullPointerException")
        void nullScope_throws() {
            DataCloudCommandExecutor ex = executor();
            assertThatThrownBy(() ->
                ex.execute("t1", null, "k", "entity.create",
                    () -> Promise.of(Map.of())))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null command throws NullPointerException")
        void nullCommand_throws() {
            DataCloudCommandExecutor ex = executor();
            assertThatThrownBy(() ->
                ex.execute("t1", "entities:profiles", "k", "entity.create", null))
                .isInstanceOf(NullPointerException.class);
        }
    }
}

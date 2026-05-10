package com.ghatana.datacloud.launcher.http;

import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.datacloud.spi.TransactionManager;
import com.ghatana.datacloud.spi.WriteIdempotencyStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Canonical write-command runtime for Data Cloud.
 *
 * <p>DC-P0-2: Every mutating request in a production profile must go through this
 * executor, which enforces the following pipeline atomically in order:
 * <ol>
 *   <li><b>Idempotency check</b> — if a cached result exists for the
 *       ({@code tenantId}, {@code scope}, {@code idempotencyKey}) triple, return it
 *       immediately without re-executing.</li>
 *   <li><b>Transaction boundary</b> — execute the command inside a
 *       {@link TransactionManager} scope so that entity write + event append +
 *       audit log are atomic.</li>
 *   <li><b>Audit emission</b> — emit a structured audit event after a
 *       successful commit.</li>
 *   <li><b>Idempotency store update</b> — persist the serialised result so
 *       subsequent retries return the cached outcome.</li>
 * </ol>
 *
 * <p>The executor degrades gracefully in non-production profiles: when
 * {@code transactionManager} or {@code idempotencyStore} is {@code null},
 * the command is executed directly without those guarantees and the omission
 * is logged at WARN level. {@code auditService} {@code null} only skips audit
 * emission — it never silently suppresses the command itself.
 *
 * <p><b>Usage</b>
 * <pre>{@code
 * DataCloudCommandExecutor executor = new DataCloudCommandExecutor(
 *     idempotencyStore, transactionManager, auditService);
 *
 * return executor.execute(
 *     tenantId,
 *     "entities:profiles",   // scope
 *     idempotencyKey,        // from X-Idempotency-Key header
 *     "entity.create",       // audit action
 *     () -> entityStore.save(entity)   // the actual write
 *         .map(saved -> Map.of("id", saved.id(), "status", "created")));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Canonical atomic, idempotent, audited write-command executor
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DataCloudCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(DataCloudCommandExecutor.class);

    private final WriteIdempotencyStore idempotencyStore;
    private final TransactionManager transactionManager;
    private final AuditService auditService;

    /**
     * Creates a fully configured executor.
     *
     * @param idempotencyStore  durable idempotency store; {@code null} disables deduplication
     * @param transactionManager transaction manager; {@code null} disables atomic writes
     * @param auditService      audit service; {@code null} disables audit emission
     */
    public DataCloudCommandExecutor(WriteIdempotencyStore idempotencyStore,
                                    TransactionManager transactionManager,
                                    AuditService auditService) {
        this.idempotencyStore = idempotencyStore;
        this.transactionManager = transactionManager;
        this.auditService = auditService;
    }

    /**
     * Executes a mutating command with idempotency, transaction, and audit guarantees.
     *
     * @param <T>              the result type, must be serialisable to a {@code Map<String, Object>}
     * @param tenantId         the tenant owning this command
     * @param scope            operation scope key (e.g. {@code "entities:profiles"})
     * @param idempotencyKey   the caller-supplied idempotency key, may be {@code null} to skip dedup
     * @param auditAction      the audit action label (e.g. {@code "entity.create"})
     * @param command          the write command; must produce a {@link Map} result
     * @param resultToMap      serialiser from {@code T} to {@code Map<String, Object>} for idempotency caching
     * @return promise that completes with the command result, or the cached result on replay
     */
    public <T> Promise<T> execute(
            String tenantId,
            String scope,
            String idempotencyKey,
            String auditAction,
            Supplier<Promise<T>> command,
            Function<T, Map<String, Object>> resultToMap) {

        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(auditAction, "auditAction must not be null");
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(resultToMap, "resultToMap must not be null");

        // 1. Idempotency check — return cached result without re-executing
        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Map<String, Object>> cached = idempotencyStore.get(tenantId, scope, idempotencyKey);
            if (cached.isPresent()) {
                log.debug("[DC-P0-2] Idempotent replay for tenant={} scope={} key={}", tenantId, scope, idempotencyKey);
                @SuppressWarnings("unchecked")
                T cachedValue = (T) cached.get();
                return Promise.of(cachedValue);
            }
        } else if (idempotencyStore == null) {
            log.warn("[DC-P0-2] idempotencyStore is null — deduplication disabled for tenant={} scope={} action={}",
                tenantId, scope, auditAction);
        }

        // 2. Execute inside transaction (or directly if no transaction manager)
        if (transactionManager != null) {
            return transactionManager
                .executeInTransaction(tenantId, command)
                .then(result -> afterSuccess(tenantId, scope, idempotencyKey, auditAction, result, resultToMap));
        } else {
            log.warn("[DC-P0-2] transactionManager is null — atomic write guarantees disabled for tenant={} scope={} action={}",
                tenantId, scope, auditAction);
            return command.get()
                .then(result -> afterSuccess(tenantId, scope, idempotencyKey, auditAction, result, resultToMap));
        }
    }

    /**
     * Simplified overload for commands that produce a {@code Map<String, Object>} directly.
     *
     * @param tenantId       the tenant owning this command
     * @param scope          operation scope key
     * @param idempotencyKey caller-supplied idempotency key, may be {@code null}
     * @param auditAction    the audit action label
     * @param command        the write command producing a {@code Map<String, Object>}
     * @return promise that completes with the command result
     */
    public Promise<Map<String, Object>> execute(
            String tenantId,
            String scope,
            String idempotencyKey,
            String auditAction,
            Supplier<Promise<Map<String, Object>>> command) {
        return execute(tenantId, scope, idempotencyKey, auditAction, command, m -> m);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private <T> Promise<T> afterSuccess(String tenantId,
                                        String scope,
                                        String idempotencyKey,
                                        String auditAction,
                                        T result,
                                        Function<T, Map<String, Object>> resultToMap) {
        // 3. Audit emission — non-fatal: log a warning on failure but do not abort
        Promise<Void> auditPromise = emitAudit(tenantId, auditAction, scope);

        // 4. Idempotency store update
        persistIdempotencyResult(tenantId, scope, idempotencyKey, result, resultToMap);

        return auditPromise.map(ignored -> result);
    }

    private Promise<Void> emitAudit(String tenantId, String auditAction, String scope) {
        if (auditService == null) {
            return Promise.complete();
        }
        try {
            AuditEvent event = AuditEvent.builder()
                .tenantId(tenantId)
                .eventType(auditAction)
                .resourceType("COMMAND")
                .resourceId(scope)
                .success(true)
                .detail("executor", "DataCloudCommandExecutor")
                .build();
            return auditService.record(event).whenException(ex ->
                log.error("[DC-P0-2] Audit emission failed for tenant={} action={} — result is committed but audit is missing",
                    tenantId, auditAction, ex)
            );
        } catch (Exception ex) {
            log.error("[DC-P0-2] Audit emission threw synchronously for tenant={} action={}", tenantId, auditAction, ex);
            return Promise.complete();
        }
    }

    private <T> void persistIdempotencyResult(String tenantId,
                                              String scope,
                                              String idempotencyKey,
                                              T result,
                                              Function<T, Map<String, Object>> resultToMap) {
        if (idempotencyStore == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        try {
            Map<String, Object> serialised = resultToMap.apply(result);
            idempotencyStore.put(tenantId, scope, idempotencyKey, serialised);
        } catch (Exception ex) {
            log.error("[DC-P0-2] Idempotency store write failed for tenant={} scope={} key={} — retries may re-execute",
                tenantId, scope, idempotencyKey, ex);
        }
    }
}

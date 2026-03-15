/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.dtc;

import com.ghatana.appplatform.eventstore.idempotency.IdempotencyGuard;
import com.ghatana.appplatform.eventstore.idempotency.IdempotencyStore;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Idempotency middleware for DTC command API endpoints (STORY-K17-012).
 *
 * <p>Applies the K-05 {@link IdempotencyGuard} to DTC command endpoints with
 * DTC-specific configuration:
 * <ul>
 *   <li>Idempotency key extracted from {@code X-DTC-Command-Id} header, falling
 *       back to {@code X-Idempotency-Key}.</li>
 *   <li>Namespace prefix {@code dtc:cmd} prepended to all keys.</li>
 *   <li>Settlement commands use 7-day TTL; general commands use 24-hour TTL.</li>
 *   <li>Concurrent duplicate commands return a {@link DuplicateCommandException}
 *       (HTTP 409 Conflict) while the first is in-flight.</li>
 *   <li>Commands without any idempotency header are passed through unchanged.</li>
 * </ul>
 *
 * <p>This middleware mounts the K-05 guard with DTC route config — it does not
 * rewrite the guard itself.
 *
 * @doc.type class
 * @doc.purpose DTC command-API idempotency guard middleware (K17-012)
 * @doc.layer product
 * @doc.pattern Decorator
 */
public final class DtcIdempotencyMiddleware {

    /** Sub-namespace for command-bus traffic. Full prefix becomes {@code dtc:cmd:<cmd>}. */
    static final String CMD_NAMESPACE = "cmd";

    private final IdempotencyGuard settlementGuard;
    private final IdempotencyGuard defaultGuard;

    /**
     * @param store shared K-05 idempotency store (Redis-backed in production)
     */
    public DtcIdempotencyMiddleware(IdempotencyStore store) {
        Objects.requireNonNull(store, "store");
        this.settlementGuard = DtcIdempotencyConfig.settlementGuard(store);
        this.defaultGuard = DtcIdempotencyConfig.defaultGuard(store);
    }

    // ── Settle endpoint ──────────────────────────────────────────────────────

    /**
     * Guard the {@code POST /transactions/{id}/settle} endpoint.
     *
     * <p>Uses settlement TTL (7 days) so that T+2 replay windows are covered.
     *
     * @param tenantId   tenant scope
     * @param commandId  value from {@code X-DTC-Command-Id} or {@code X-Idempotency-Key}; may be {@code null} to pass through
     * @param payload    serialised request payload (used for response hash derivation)
     * @param handler    the settle logic to execute exactly once
     * @return handler result or cached result on replay
     * @throws DuplicateCommandException if a duplicate is detected mid-flight (store returns no hash yet)
     */
    public String guardSettle(String tenantId, String commandId, String payload, Supplier<String> handler) {
        if (commandId == null || commandId.isBlank()) {
            return handler.get(); // no header → passthrough
        }
        String key = buildCmdKey("settle", commandId);
        return settlementGuard.guard(tenantId, key, payload, handler);
    }

    /**
     * Guard the {@code POST /transactions} (create) endpoint.
     *
     * @param tenantId  tenant scope
     * @param commandId idempotency key from header; may be {@code null} to pass through
     * @param payload   serialised request payload
     * @param handler   the create logic to execute exactly once
     * @return handler result or cached result on replay
     */
    public String guardCreate(String tenantId, String commandId, String payload, Supplier<String> handler) {
        if (commandId == null || commandId.isBlank()) {
            return handler.get();
        }
        String key = buildCmdKey("create", commandId);
        return defaultGuard.guard(tenantId, key, payload, handler);
    }

    /**
     * Guard the {@code POST /transactions/{id}/cancel} endpoint.
     *
     * @param tenantId  tenant scope
     * @param commandId idempotency key from header; may be {@code null} to pass through
     * @param payload   serialised request payload
     * @param handler   the cancel logic to execute exactly once
     * @return handler result or cached result on replay
     */
    public String guardCancel(String tenantId, String commandId, String payload, Supplier<String> handler) {
        if (commandId == null || commandId.isBlank()) {
            return handler.get();
        }
        String key = buildCmdKey("cancel", commandId);
        return defaultGuard.guard(tenantId, key, payload, handler);
    }

    /**
     * Check whether a command ID has already been processed (duplicate detection).
     *
     * @param tenantId    tenant scope
     * @param commandType type name (e.g. "settle", "create", "cancel")
     * @param commandId   the command identifier header value
     * @return {@code true} if this command is a replay
     */
    public boolean isDuplicate(String tenantId, String commandType, String commandId) {
        if (commandId == null || commandId.isBlank()) {
            return false;
        }
        String key = buildCmdKey(commandType, commandId);
        return defaultGuard.isDuplicate(tenantId, key);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    static String buildCmdKey(String commandType, String commandId) {
        return DtcIdempotencyConfig.buildKey(CMD_NAMESPACE + ":" + commandType, commandId);
    }

    // ── Exception ────────────────────────────────────────────────────────────

    /**
     * Thrown when a duplicate command arrives while the first is still in-flight.
     * Maps to HTTP 409 Conflict.
     */
    public static final class DuplicateCommandException extends RuntimeException {
        private final String commandId;

        public DuplicateCommandException(String commandId) {
            super("Duplicate DTC command: " + commandId);
            this.commandId = commandId;
        }

        /** @return the duplicate command identifier */
        public String getCommandId() {
            return commandId;
        }
    }
}

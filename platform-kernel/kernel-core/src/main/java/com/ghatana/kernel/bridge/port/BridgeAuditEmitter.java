/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.bridge.port;

import java.time.Instant;
import java.util.Objects;

/**
 * Kernel-owned audit emission port for bridge calls.
 *
 * <p>The kernel defines this port; the runtime product wiring binds it to the
 * canonical audit-trail infrastructure. Bridge adapters must not depend directly
 * on a concrete audit logger — they call {@link #emit(BridgeAuditEvent)} instead.</p>
 *
 * <p>Emission is fire-and-forget from the caller's perspective; the port
 * implementation is responsible for durability and ordering guarantees.</p>
 *
 * @doc.type interface
 * @doc.purpose Kernel-owned audit port for recording cross-scope bridge operations
 * @doc.layer core
 * @doc.pattern Port
 * @author Ghatana Kernel Team
 * @since 1.3.0
 */
public interface BridgeAuditEmitter {

    /**
     * Emits an audit event for a bridge operation.
     *
     * @param event the audit event — must not be {@code null}
     */
    void emit(BridgeAuditEvent event);

    // -------------------------------------------------------------------------
    // Nested event record
    // -------------------------------------------------------------------------

    /**
     * Immutable record of a single bridge audit event.
     *
     * @param bridgeId      identifier of the bridge that generated this event
     * @param tenantId      tenant that originated the call
     * @param principalId   principal that originated the call
     * @param correlationId distributed tracing correlation ID
     * @param resource      resource targeted by the operation
     * @param action        action performed (e.g. {@code "read"}, {@code "write"})
     * @param outcome       outcome of the operation: {@code "ALLOWED"}, {@code "DENIED"},
     *                      {@code "ERROR"}, {@code "TIMEOUT"}
     * @param timestamp     wall-clock time of the event
     */
    record BridgeAuditEvent(
            String bridgeId,
            String tenantId,
            String principalId,
            String correlationId,
            String resource,
            String action,
            String outcome,
            Instant timestamp
    ) {
        public BridgeAuditEvent {
            Objects.requireNonNull(bridgeId,      "bridgeId must not be null");
            Objects.requireNonNull(tenantId,      "tenantId must not be null");
            Objects.requireNonNull(correlationId, "correlationId must not be null");
            Objects.requireNonNull(resource,      "resource must not be null");
            Objects.requireNonNull(action,        "action must not be null");
            Objects.requireNonNull(outcome,       "outcome must not be null");
            Objects.requireNonNull(timestamp,     "timestamp must not be null");
        }

        /**
         * Factory: creates an ALLOWED audit event at the current instant.
         */
        public static BridgeAuditEvent allowed(
                String bridgeId, BridgeContext context, String resource, String action) {
            return new BridgeAuditEvent(
                    bridgeId, context.getTenantId(), context.getPrincipalId(),
                    context.getCorrelationId(), resource, action, "ALLOWED", Instant.now());
        }

        /**
         * Factory: creates a DENIED audit event at the current instant.
         */
        public static BridgeAuditEvent denied(
                String bridgeId, BridgeContext context, String resource, String action) {
            return new BridgeAuditEvent(
                    bridgeId, context.getTenantId(), context.getPrincipalId(),
                    context.getCorrelationId(), resource, action, "DENIED", Instant.now());
        }

        /**
         * Factory: creates an ERROR audit event at the current instant.
         */
        public static BridgeAuditEvent error(
                String bridgeId, BridgeContext context, String resource, String action) {
            return new BridgeAuditEvent(
                    bridgeId, context.getTenantId(), context.getPrincipalId(),
                    context.getCorrelationId(), resource, action, "ERROR", Instant.now());
        }
    }

    /**
     * A no-op implementation that discards all events; use in tests or development
     * environments where no audit backend is wired.
     */
    static BridgeAuditEmitter noOp() {
        return event -> { /* intentionally empty */ };
    }
}

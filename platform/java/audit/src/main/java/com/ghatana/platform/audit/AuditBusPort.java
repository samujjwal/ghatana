/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.audit;

import org.jetbrains.annotations.NotNull;

/**
 * Fire-and-forget port for emitting audit events.
 *
 * <p>This is the canonical replacement for the ~15 inner {@code AuditPort}
 * interfaces scattered across kernel and domain-pack modules. Products should
 * depend on this single interface instead of defining their own.
 *
 * <p>Implementations typically delegate to {@link AuditService#record(AuditEvent)}
 * with fire-and-forget semantics (log and discard failures).
 *
 * @doc.type interface
 * @doc.purpose Canonical fire-and-forget audit event port
 * @doc.layer platform
 * @doc.pattern Port
 */
@FunctionalInterface
public interface AuditBusPort {

    /**
     * Emits an audit event. Implementations should be non-blocking and
     * must not throw on delivery failure.
     *
     * @param event the audit event to emit
     */
    void emit(@NotNull AuditEvent event);

    /**
     * Creates an {@code AuditBusPort} that delegates to an {@link AuditService},
     * logging failures without propagating them.
     *
     * @param auditService the backing audit service
     * @return a fire-and-forget bus port
     */
    static AuditBusPort from(@NotNull AuditService auditService) {
        return event -> auditService.record(event)
            .whenException(e -> org.slf4j.LoggerFactory
                .getLogger(AuditBusPort.class)
                .warn("Failed to emit audit event {}: {}", event.getEventType(), e.getMessage()));
    }
}

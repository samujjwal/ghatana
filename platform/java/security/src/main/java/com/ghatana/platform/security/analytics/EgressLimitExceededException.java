/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.analytics;

/**
 * Thrown when an agent exceeds the configured data-egress byte limit for the current window.
 *
 * @doc.type class
 * @doc.purpose Signal that an agent exceeded its data-egress byte limit
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class EgressLimitExceededException extends RuntimeException {

    private final String tenantId;
    private final String agentId;
    private final long limitBytes;
    private final long actualBytes;

    /**
     * @param tenantId    owning tenant
     * @param agentId     agent that breached the limit
     * @param limitBytes  configured limit in bytes
     * @param actualBytes actual total bytes in the window
     */
    public EgressLimitExceededException(
            String tenantId, String agentId, long limitBytes, long actualBytes) {
        super("Agent '%s' in tenant '%s' exceeded egress limit: %d bytes (limit: %d)"
            .formatted(agentId, tenantId, actualBytes, limitBytes));
        this.tenantId = tenantId;
        this.agentId = agentId;
        this.limitBytes = limitBytes;
        this.actualBytes = actualBytes;
    }

    /** @return owning tenant identifier */
    public String tenantId() { return tenantId; }

    /** @return agent that breached the limit */
    public String agentId() { return agentId; }

    /** @return configured egress limit in bytes */
    public long limitBytes() { return limitBytes; }

    /** @return actual total bytes transferred in the window */
    public long actualBytes() { return actualBytes; }
}

/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.bridge.port;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Carries the runtime context for a bridge call: tenant identity, caller principal,
 * correlation ID, and an optional idempotency key for write operations.
 *
 * <p>BridgeContext is immutable and should be constructed once per inbound request
 * and propagated through the entire bridge call chain without modification.</p>
 *
 * <p>Construction example:</p>
 * <pre>{@code
 * BridgeContext ctx = BridgeContext.builder()
 *     .tenantId("acme-corp")
 *     .principalId("user-42")
 *     .correlationId(UUID.randomUUID().toString())
 *     .idempotencyKey("cmd-" + commandId)
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Immutable context carrier for bridge call tenant/principal/correlation propagation
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.3.0
 */
public final class BridgeContext {

    private final String tenantId;
    private final String principalId;
    private final String correlationId;
    private final String idempotencyKey;
    private final Map<String, String> attributes;

    private BridgeContext(Builder builder) {
        this.tenantId        = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.principalId     = builder.principalId   != null ? builder.principalId   : "anonymous";
        this.correlationId   = builder.correlationId != null ? builder.correlationId : "none";
        this.idempotencyKey  = builder.idempotencyKey; // nullable – only required for writes
        this.attributes      = builder.attributes.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(builder.attributes));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** The tenant that originated this bridge call. Never {@code null}. */
    public String getTenantId() { return tenantId; }

    /** The authenticated principal that originated this bridge call. Never {@code null}. */
    public String getPrincipalId() { return principalId; }

    /** Correlation ID for distributed tracing. Never {@code null}. */
    public String getCorrelationId() { return correlationId; }

    /**
     * Idempotency key for write operations; {@code null} for read-only calls.
     * Bridges should propagate this to downstream write operations to ensure
     * at-most-once semantics.
     */
    public String getIdempotencyKey() { return idempotencyKey; }

    /** Additional propagation attributes. Never {@code null}; may be empty. */
    public Map<String, String> getAttributes() { return attributes; }

    public static Builder builder() { return new Builder(); }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Fluent builder for {@link BridgeContext}. */
    public static final class Builder {

        private String tenantId;
        private String principalId;
        private String correlationId;
        private String idempotencyKey;
        private final Map<String, String> attributes = new HashMap<>();

        private Builder() {}

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder attribute(String key, String value) {
            this.attributes.put(key, value);
            return this;
        }

        public BridgeContext build() { return new BridgeContext(this); }
    }

    @Override
    public String toString() {
        return "BridgeContext{tenantId='" + tenantId
                + "', principalId='" + principalId
                + "', correlationId='" + correlationId + "'}";
    }
}

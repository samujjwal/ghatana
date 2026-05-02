package com.ghatana.digitalmarketing.contracts;

import com.ghatana.kernel.bridge.port.BridgeContext;
import java.util.Objects;

/**
 * Canonical DMOS operation context carrying all required propagation fields.
 *
 * <p>{@code DmOperationContext} is the product-level companion to the kernel's
 * {@link BridgeContext}. It is created once per inbound operation (API request,
 * event, scheduled trigger) and propagated unchanged through the command→workflow
 * →bridge adapter chain.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * DmOperationContext ctx = DmOperationContext.builder()
 *     .tenantId(DmTenantId.of("acme-corp"))
 *     .workspaceId(DmWorkspaceId.of("ws-123"))
 *     .actor(ActorRef.user("user-42"))
 *     .correlationId(DmCorrelationId.generate())
 *     .idempotencyKey(DmIdempotencyKey.forCommand("LaunchCampaign", "campaign-99"))
 *     .build();
 *
 * BridgeContext bridgeCtx = ctx.toBridgeContext();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Canonical immutable operation context for DMOS commands and service calls
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class DmOperationContext {

    private final DmTenantId tenantId;
    private final DmWorkspaceId workspaceId;
    private final ActorRef actor;
    private final DmCorrelationId correlationId;
    private final DmIdempotencyKey idempotencyKey; // nullable — only required for writes

    private DmOperationContext(Builder builder) {
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId is required");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId is required");
        this.actor = Objects.requireNonNull(builder.actor, "actor is required");
        this.correlationId = Objects.requireNonNull(builder.correlationId, "correlationId is required");
        this.idempotencyKey = builder.idempotencyKey; // nullable for reads
    }

    /** The tenant this operation is scoped to. Never {@code null}. */
    public DmTenantId getTenantId() {
        return tenantId;
    }

    /** The workspace this operation is scoped to. Never {@code null}. */
    public DmWorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    /** The actor performing this operation. Never {@code null}. */
    public ActorRef getActor() {
        return actor;
    }

    /** The correlation ID for distributed tracing. Never {@code null}. */
    public DmCorrelationId getCorrelationId() {
        return correlationId;
    }

    /**
     * The idempotency key for write commands. May be {@code null} for read-only operations.
     * Write operations must provide an idempotency key.
     */
    public DmIdempotencyKey getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Converts this context into a {@link BridgeContext} suitable for kernel bridge adapter calls.
     *
     * <p>The idempotency key is included when present.</p>
     */
    public BridgeContext toBridgeContext() {
        BridgeContext.Builder builder = BridgeContext.builder()
            .tenantId(tenantId.getValue())
            .principalId(actor.getPrincipalId())
            .correlationId(correlationId.getValue());

        if (idempotencyKey != null) {
            builder.idempotencyKey(idempotencyKey.getValue());
        }

        return builder.build();
    }

    /**
     * Returns a copy of this context with an idempotency key set.
     * Use this to attach a key to an existing read context before issuing a write.
     *
     * @param key the idempotency key for the write operation
     * @return new context with the key set
     */
    public DmOperationContext withIdempotencyKey(DmIdempotencyKey key) {
        return new Builder()
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .actor(actor)
            .correlationId(correlationId)
            .idempotencyKey(Objects.requireNonNull(key, "key must not be null"))
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "DmOperationContext{tenant=" + tenantId.getValue()
            + ", workspace=" + workspaceId.getValue()
            + ", actor=" + actor
            + ", correlation=" + correlationId.getValue() + '}';
    }

    /** Fluent builder for {@link DmOperationContext}. */
    public static final class Builder {
        private DmTenantId tenantId;
        private DmWorkspaceId workspaceId;
        private ActorRef actor;
        private DmCorrelationId correlationId;
        private DmIdempotencyKey idempotencyKey;

        private Builder() {}

        public Builder tenantId(DmTenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder workspaceId(DmWorkspaceId workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder actor(ActorRef actor) {
            this.actor = actor;
            return this;
        }

        public Builder correlationId(DmCorrelationId correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder idempotencyKey(DmIdempotencyKey idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public DmOperationContext build() {
            return new DmOperationContext(this);
        }
    }
}

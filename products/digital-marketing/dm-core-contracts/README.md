# dm-core-contracts

**Package:** `com.ghatana.digitalmarketing.contracts`

Core contracts module for the Digital Marketing Operating System (DMOS). Provides canonical typed identifiers, value objects, and the operation context propagated through every DMOS command and service call.

## Purpose

This module contains:

- **`DmTenantId`** — Typed tenant identifier, preventing raw-string confusion at compile time.
- **`DmWorkspaceId`** — Typed workspace identifier; primary multi-tenancy scope for business accounts.
- **`DmCorrelationId`** — Typed correlation ID for distributed tracing across service and module boundaries.
- **`DmIdempotencyKey`** — Typed idempotency key preventing duplicate write side-effects on retry.
- **`ActorRef`** — Typed actor identity (user, agent, or system) for audit and authorization.
- **`DmOperationContext`** — Immutable canonical operation context assembled once per inbound request and propagated unchanged through the command → workflow → bridge adapter chain.

## Usage

```java
DmOperationContext ctx = DmOperationContext.builder()
    .tenantId(DmTenantId.of("acme-corp"))
    .workspaceId(DmWorkspaceId.of("ws-123"))
    .actor(ActorRef.user("user-42"))
    .correlationId(DmCorrelationId.generate())
    .idempotencyKey(DmIdempotencyKey.forCommand("LaunchCampaign", "campaign-99"))
    .build();

// Convert to kernel bridge context for bridge adapter calls
BridgeContext bridgeCtx = ctx.toBridgeContext();
```

## Dependencies

- `platform-kernel:kernel-core` — for `BridgeContext` used in context conversion

## Design Notes

- All value objects are immutable and enforce non-blank constraints at construction time.
- `DmOperationContext.idempotencyKey` is nullable — read-only operations do not require it. Use `withIdempotencyKey()` to attach a key to a context before a write operation.
- This module has no product-domain logic. It defines only the propagation contract.

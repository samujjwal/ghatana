# dm-infra — DMOS Infrastructure Adapters

Provides **in-memory** implementations of the DMOS application-layer repository ports.

## Purpose

This module bridges the domain/application layer with a deployable runtime storage adapter.
All adapters use `ConcurrentHashMap` as the backing store, making them suitable for:

- **Local development** — zero external dependencies, start immediately
- **Integration tests** — fast, deterministic, no Docker required

## ⚠️ Production Safety

**In-memory adapters MUST NOT be used in production environments.**

This module includes a {@link ProductionProfileGuard} that validates the environment
before allowing in-memory adapter usage. The guard will throw an {@link IllegalStateException}
if {@code DMOS_ENV=production} is set, preventing accidental data loss.

### Environment Variables

- `DMOS_ENV` - Deployment environment (default: {@code development})
  - {@code development} - In-memory adapters allowed
  - {@code test} - In-memory adapters allowed
  - {@code production} - In-memory adapters **BLOCKED** (throws exception)

### Usage

```java
// Validate environment before wiring adapters
ProductionProfileGuard.validate();

// Wire adapters (only allowed in non-production environments)
WorkspaceRepository workspaceRepo = new InMemoryWorkspaceRepository();
CampaignRepository campaignRepo = new InMemoryCampaignRepository();
```

For production persistence (PostgreSQL, Redis, etc.), implement the same repository
interfaces in the {@code dm-persistence} module following the same package structure.

## Packages

| Package | Adapter | Repository Interface |
|---|---|---|
| `infra.workspace` | `InMemoryWorkspaceRepository` | `WorkspaceRepository` |
| `infra.campaign` | `InMemoryCampaignRepository` | `CampaignRepository` |
| `infra.content` | `InMemoryContentItemRepository` | `ContentItemRepository` |
| `infra.content` | `InMemoryContentVersionRepository` | `ContentVersionRepository` |
| `infra.approval` | `InMemoryApprovalSnapshotRepository` | `ApprovalSnapshotRepository` |
| `infra.transparency` | `InMemoryAiActionLogRepository` | `AiActionLogRepository` |
| `infra.research` | `InMemoryCompetitorResearchRepository` | `CompetitorResearchRepository` |

## Design Invariants

- **Tenant isolation**: every map key includes the tenant or workspace segment so data cannot
  bleed across tenants.
- **Copy-on-read/write**: stored objects are returned directly (they are already immutable
  domain records/value objects).
- **Thread safety**: `ConcurrentHashMap` provides the required thread-safety without locking.
- **ActiveJ-compatible**: all methods return `Promise.of(...)` — no blocking I/O.

## Wiring

Wire adapters in your application bootstrap:

```java
WorkspaceRepository workspaceRepo = new InMemoryWorkspaceRepository();
CampaignRepository  campaignRepo  = new InMemoryCampaignRepository();

WorkspaceService workspaceService = new WorkspaceServiceImpl(kernelAdapter, workspaceRepo, ...);
CampaignService  campaignService  = new CampaignServiceImpl(kernelAdapter, campaignRepo, ...);
```

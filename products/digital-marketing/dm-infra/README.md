# dm-infra — DMOS Infrastructure Adapters

Provides **in-memory** implementations of the DMOS application-layer repository ports.

## Purpose

This module bridges the domain/application layer with a deployable runtime storage adapter.
All adapters use `ConcurrentHashMap` as the backing store, making them suitable for:

- **Local development** — zero external dependencies, start immediately
- **Integration tests** — fast, deterministic, no Docker required
- **Single-instance staging** — non-persistent but fully functional

For production persistence (PostgreSQL, Redis, etc.), implement the same repository
interfaces in a separate module following the same package structure.

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

# Action Plane Module Inventory

**Status:** Canonical module inventory for Action Plane  
**Purpose:** Track all Action Plane modules, their ownership, purpose, and migration status  
**Last Updated:** 2026-05-25

## Overview

The Action Plane is a compatibility and migration area for stored execution metadata, checkpoints, review evidence, and AEP integration adapters. During boundary cleanup, AEP implementation modules remain under `products/data-cloud/planes/action/*`. This is a temporary code-location reality, not product ownership.

## Module Inventory

| Module | Path | Purpose | Owner | Status | Notes |
| --- | --- | --- | --- | --- | --- |
| **Core Runtime** | | | | | |
| Engine | `planes/action/engine` | PatternSpec/EPL engine and operator runtime semantics | AEP | Compatibility | AEP-owned semantics; should move to AEP product |
| Central Runtime | `planes/action/central-runtime` | Centralized runtime coordination and dispatch | AEP | Compatibility | AEP-owned semantics; should move to AEP product |
| Orchestrator | `planes/action/orchestrator` | Agent orchestration and workflow execution | AEP | Compatibility | AEP-owned semantics; should move to AEP product |
| Agent Runtime | `planes/action/agent-runtime` | EventOperatorCapability runtime and agent execution | AEP | Compatibility | AEP-owned semantics; should move to AEP product |
| **Contracts & SPI** | | | | | |
| Operator Contracts | `planes/action/operator-contracts` | Operator contracts and SPI definitions | AEP | Compatibility | Should move to AEP-owned contracts/specs |
| API | `planes/action/api` | Action Plane API handlers and route adapters | Data Cloud | Stable | Data Cloud-owned API surface |
| **Integration Bridges** | | | | | |
| Event Bridge | `planes/action/event-bridge` | Persistence bridge between EventCloud and storage | Data Cloud | Stable | Bridge only; EventCloud semantics are AEP-owned |
| Kernel Bridge | `planes/action/kernel-bridge` | Kernel integration bridge | Data Cloud | Stable | Moved to `extensions/kernel-bridge` |
| **Governance & Compliance** | | | | | |
| Compliance | `planes/action/compliance` | Policy enforcement and compliance checks | Data Cloud | Stable | Data Cloud-owned governance |
| Registry | `planes/action/registry` | Agent and capability registry | Data Cloud | Stable | Data Cloud-owned registry |
| **Security & Identity** | | | | | |
| Security | `planes/action/security` | Security policies and enforcement | Data Cloud | Stable | Data Cloud-owned security |
| Identity | `planes/action/identity` | Identity and access management | Data Cloud | Stable | Data Cloud-owned identity |
| **Observability** | | | | | |
| Observability | `planes/action/observability` | Metrics, traces, and logging | Data Cloud | Stable | Data Cloud-owned observability |
| **Operations** | | | | | |
| Scaling | `planes/action/scaling` | Scaling and capacity management | Data Cloud | Stable | Data Cloud-owned operations |
| Server | `planes/action/server` | Compatibility server during boundary cleanup | Data Cloud | Temporary | Will be removed after migration |
| Gateway | `planes/action/gateway` | API gateway and routing | Data Cloud | Stable | Data Cloud-owned gateway |
| **Kubernetes** | | | | | |
| K8s Multi-Region | `planes/action/k8s/multi-region` | Multi-region Kubernetes deployment | Data Cloud | Stable | Data Cloud-owned deployment |
| **Catalog & Metadata** | | | | | |
| Agent Catalog | `planes/action/agent-catalog` | Agent metadata catalog | Data Cloud | Stable | Data Cloud-owned catalog |
| Analytics | `planes/action/analytics` | Analytics and reporting | Data Cloud | Stable | Data Cloud-owned analytics |

## Migration Status

### Modules to Move to AEP Product

When build/module boundaries are ready, the following AEP-owned modules should move to `products/aep`:

1. `planes/action/engine` - PatternSpec/EPL engine
2. `planes/action/central-runtime` - Central runtime coordination
3. `planes/action/orchestrator` - Agent orchestration
4. `planes/action/agent-runtime` - EventOperatorCapability runtime
5. `planes/action/operator-contracts` - Operator contracts

### Modules to Keep in Data Cloud

The following modules are Data Cloud-owned and should remain:

1. `planes/action/api` - Data Cloud API surface
2. `planes/action/event-bridge` - Persistence bridge (bridge only)
3. `planes/action/compliance` - Data Cloud governance
4. `planes/action/registry` - Data Cloud registry
5. `planes/action/security` - Data Cloud security
6. `planes/action/identity` - Data Cloud identity
7. `planes/action/observability` - Data Cloud observability
8. `planes/action/scaling` - Data Cloud operations
9. `planes/action/gateway` - Data Cloud gateway
10. `planes/action/k8s/multi-region` - Data Cloud deployment
11. `planes/action/agent-catalog` - Data Cloud catalog
12. `planes/action/analytics` - Data Cloud analytics

### Modules to Remove

1. `planes/action/server` - Temporary compatibility server
2. `planes/action/kernel-bridge` - Already moved to `extensions/kernel-bridge`

## Dependency Rules

```text
Data/Event/Context/Governance/Intelligence planes must not depend on Action Plane internals or AEP modules.
AEP integration may depend on public contracts/SPI from data, event, context, governance, and operations.
Delivery runtime composition may compose all planes.
UI must depend on generated clients and frontend adapters, not backend internals.
Extensions must depend on contracts/SPI, not launcher internals.
Contracts must not depend on implementation modules.
AEP may use Data-Cloud storage plugins through stable SPI.
Data-Cloud must not import AEP modules, PatternSpec/EPL, EventOperator runtime, or adaptive learning semantics.
```

## References

- [Plane Architecture](./PLANE_ARCHITECTURE.md)
- [Implementation Tracker](../../implementation/GHATANA_WORLD_CLASS_IMPLEMENTATION_TRACKER.md)
- [Action Plane Boundary Evidence](../../../../.kernel/evidence/action-plane-boundaries.json)

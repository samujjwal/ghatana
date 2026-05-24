# AEP Integration Boundary

**Plane:** Data Cloud integration and storage substrate
**External platform:** AEP
**Status:** Boundary cleanup in progress
**Canonical product architecture:** `../architecture/PLANE_ARCHITECTURE.md`

## Purpose

The Agentic Event Processor is the adaptive event intelligence platform. Data-Cloud is the governed data/storage substrate that may persist AEP-owned metadata, checkpoints, memory, audit records, and EventCloud storage records through public contracts or stable SPI.

Product positioning, plane hierarchy, module migration, and dependency rules are defined in `../architecture/PLANE_ARCHITECTURE.md`. This folder is retained for Data-Cloud/AEP integration notes during boundary cleanup.

During migration, some AEP implementation modules may remain under `products/data-cloud/planes/action/*`. That is an implementation-location compromise only. AEP semantics remain owned by AEP, and Data-Cloud storage planes must not import or expose those semantics.

## Terminology Boundary

- Data-Cloud is the governed data/storage product.
- AEP owns EventCloud, PatternSpec/EPL, operator runtime, pattern lifecycle, adaptive learning, agents-as-operators, and adaptive event governance.
- Data-Cloud may provide EventCloud persistence plugins, not EventCloud semantics.
- Data, Event, Context, Governance, Intelligence, and Operations planes must not import AEP implementation internals.

## Runtime Surfaces

| Surface | Runtime responsibility |
| --- | --- |
| Agent metadata persistence | Store agent definitions, memory, execution records, and audit evidence when called through public contracts |
| Pattern metadata persistence | Store pattern registry metadata when called through AEP services |
| EventCloud persistence plugin | Persist AEP-owned EventCloud records behind AEP SPI |
| Governance evidence | Store audit, policy, retention, encryption, and review evidence |
| Runtime analytics substrate | Provide queryable historical metadata and storage-plane metrics |

## Public Contracts

Data-Cloud canonical contracts are:

```text
products/data-cloud/contracts/openapi/data-cloud.yaml
```

Compatibility contracts may remain for existing callers:

```text
products/data-cloud/contracts/openapi/aep.yaml
products/data-cloud/contracts/openapi/action-plane.yaml
```

Compatibility contracts must not become the canonical home for AEP-owned PatternSpec, EventCloud, operator-runtime, or learning semantics.

## Runtime Truth

Data-Cloud surfaces must publish runtime truth:

```text
LIVE
DEGRADED
DISABLED
PREVIEW
UNAVAILABLE
MISCONFIGURED
```

The UI and SDK must use runtime truth to decide whether a surface is visible, enabled, degraded, or unavailable.

## Production Rules

- Production AEP persistence through Data-Cloud must use durable Data-Cloud storage contracts or plugins.
- Production startup must fail closed when required security, policy, audit, or durability dependencies are absent.
- Sensitive storage operations must emit audit evidence.
- Human-review and rollback evidence can be stored in Data-Cloud, but review semantics belong to AEP.
- Runtime metrics, traces, and logs must be exposed through the Operations Plane.

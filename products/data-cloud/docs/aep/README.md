# AEP Runtime Implementation

**Plane:** Data Cloud Action Plane  
**Runtime implementation:** AEP  
**Status:** Active during Action Plane migration  
**Canonical product architecture:** `../architecture/PLANE_ARCHITECTURE.md`

## Purpose

The Agentic Event Processor is the current runtime implementation behind Data Cloud's Action Plane. It is not a separate customer-facing product boundary.

Product positioning, plane hierarchy, module migration, and dependency rules are defined in `../architecture/PLANE_ARCHITECTURE.md`. This folder is retained only for runtime implementation details that are still AEP-named during migration.

## Terminology Boundary

- Data Cloud is the product.
- The Action Plane owns governed automation, pipelines, patterns, agents, reviews, runs, and learning.
- AEP implements Action Plane runtime behavior today.
- Data, Event, Context, Governance, Intelligence, and Operations planes must not import AEP implementation internals.

## Runtime Surfaces

| Surface | Runtime responsibility |
| --- | --- |
| Agent runtime | Agent listing, execution, memory views, and runtime metadata |
| Pipeline runtime | Pipeline CRUD, versioning, publish/rollback, run listing, run detail, and cancellation |
| Human review | Review queue, approve/reject/escalate flows, and learning triggers |
| Governance evidence | Kill switch, degraded mode, policy evaluation, compliance summary, audit summary, and security scans |
| Runtime analytics | Anomalies, forecasting, reports, deployment lifecycle, and operational summaries |

## Public Contracts

The target Action Plane contract is:

```text
products/data-cloud/contracts/openapi/action-plane.yaml
```

The compatibility contract remains:

```text
products/data-cloud/contracts/openapi/aep.yaml
```

Both must remain equivalent until the AEP-named contract is retired.

## Runtime Truth

Action Plane surfaces must publish runtime truth:

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

- Production run history must use durable Data Cloud event storage.
- Production startup must fail closed when required security, policy, audit, or durability dependencies are absent.
- Sensitive actions must emit audit evidence.
- Human-review and rollback paths must remain available for governed automation.
- Runtime metrics, traces, and logs must be exposed through the Operations Plane.

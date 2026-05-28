# Data Cloud Canonical Architecture

**Status:** Superseded by plane architecture  
**Canonical architecture:** `docs/architecture/PLANE_ARCHITECTURE.md`

Data Cloud is one product organized by planes. The authoritative product hierarchy, module target layout, dependency rules, shared platform review rules, and migration sequence live in:

```text
products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md
```

This file is retained as a compatibility pointer for existing references. Do not add new architecture content here.

## Current Canonical Model

```text
Data Cloud product
├─ Experience Plane
├─ Contract Plane
├─ Runtime Truth Plane
├─ Data Plane
├─ Event Plane
├─ Context Plane
├─ Intelligence Plane
├─ Governance Plane
├─ Action Plane
└─ Operations Plane
```

Action Plane is Data Cloud's governed automation runtime (formerly AEP), providing event-driven agent orchestration, pattern detection, pipeline execution, HITL review, and learning loops.

## Required Vocabulary

Use:

```text
plane
surface
Runtime Truth Registry
plane state
surface state
Action Plane
Data-Cloud storage substrate
AEP integration through public contracts and stable SPI
```

Avoid using capability-area terminology in product and architecture documentation.

## Contract Truth

Canonical contracts live in:

```text
products/data-cloud/contracts/openapi/data-cloud.yaml
products/data-cloud/contracts/openapi/action-plane.yaml
```

`products/data-cloud/contracts/openapi/aep.yaml` remains a compatibility contract for existing callers during boundary cleanup. It must not become the canonical home for AEP-owned PatternSpec, EventCloud, operator-runtime, or learning semantics.

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

AEP is the current runtime implementation behind the Action Plane. It is not a separate customer-facing product boundary and should not be described as a capability area.

## Required Vocabulary

Use:

```text
plane
surface
Runtime Truth Registry
plane state
surface state
Action Plane
AEP runtime implementation
```

Avoid using capability-area terminology in product and architecture documentation.

## Contract Truth

Canonical contracts live in:

```text
products/data-cloud/contracts/openapi/data-cloud.yaml
products/data-cloud/contracts/openapi/action-plane.yaml
```

`products/data-cloud/contracts/openapi/aep.yaml` remains a compatibility copy until the Action Plane contract rename is complete.

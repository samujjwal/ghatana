# Data Cloud Documentation

This folder contains the required Data Cloud product and architecture documentation. The canonical structure is plane-based.

## Required Canonical Docs

| Document | Purpose |
| --- | --- |
| `architecture/PLANE_ARCHITECTURE.md` | Canonical plane model, target module layout, dependency rules, and migration sequence |
| `product/01_data_cloud_unified_vision_market_positioning.md` | Product vision, market positioning, and plane-based narrative |
| `product/02_data_cloud_unified_detailed_architecture.md` | Detailed plane architecture, contracts, runtime, and enforcement model |
| `product/03_data_cloud_unified_high_level_design.md` | Outcome-first navigation, UX hierarchy, API design, and migration design |
| `audits/end-to-end-product-correctness-audit.md` | Current Data Cloud product correctness audit |
| `migration/AEP_FIRST_PASS_FILE_BY_FILE_MOVE_AND_REFERENCE_PLAN.md` | Historical first-pass migration plan; keep only until the plane migration plan replaces it |
| `SCHEMA_ERD.md` | Database/schema reference |

## Runtime Implementation Docs

`docs/aep` contains implementation-level documents for the current AEP runtime behind the Action Plane. These documents must not position AEP as a separate product. They are retained only where they describe real runtime operation, API behavior, benchmarks, topology, tracing, or verification.

## Deprecated Documentation

Generated reverse-engineering reports, stale product narratives, and old capability-area documents should not be retained as authoritative docs. If a document repeats or contradicts the plane architecture, either update it to defer to `architecture/PLANE_ARCHITECTURE.md` or remove it.

## Vocabulary

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

Avoid in product/architecture docs:

```text
capability area
capability model
capability registry
AEP as a separate product
Data Cloud + AEP
```

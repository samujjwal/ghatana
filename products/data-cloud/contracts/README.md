# Data Cloud Contracts

## Purpose

`products/data-cloud/contracts` is the Contract Plane for Data Cloud. It contains the public API and schema contracts used by the Data Cloud UI, SDKs, integration tests, downstream products, and deployment validation.

The Contract Plane is the source of truth for public shapes. Runtime implementations live in Data Cloud planes and delivery modules, not in this folder.

## Contents

| File | Role |
| --- | --- |
| `openapi/data-cloud.yaml` | Canonical non-Action Data Cloud REST API contract |
| `openapi/action-plane.yaml` | Compatibility Action Plane REST contract during boundary cleanup |
| `openapi/aep.yaml` | Compatibility-only AEP contract for legacy clients and migration evidence |

## Boundaries

- Contracts define public API and schema shape only.
- Delivery modules implement HTTP routes.
- Plane modules implement product behavior.
- Breaking changes require versioned migration and SDK regeneration.
- Runtime route copies must be checked against the canonical contract.

## Verification

```bash
./gradlew :products:data-cloud:contracts:check
```

## Migration Note

The product language is now boundary-based. `data-cloud.yaml` owns canonical Data-Cloud storage, metadata, governance, schema, audit, and plugin surfaces. `action-plane.yaml` and `aep.yaml` are compatibility contracts during boundary cleanup and must not become the canonical home for AEP-owned EventCloud, PatternSpec/EPL, operator-runtime, pattern learning/adaptation, or agent orchestration semantics.

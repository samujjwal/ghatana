# Data Cloud Contracts

## Purpose

`products/data-cloud/contracts` is the Contract Plane for Data Cloud. It contains the public API and schema contracts used by the Data Cloud UI, SDKs, integration tests, downstream products, and deployment validation.

The Contract Plane is the source of truth for public shapes. Runtime implementations live in Data Cloud planes and delivery modules, not in this folder.

## Contents

| File | Role |
| --- | --- |
| `openapi/data-cloud.yaml` | Canonical non-Action Data Cloud REST API contract |
| `openapi/action-plane.yaml` | Active canonical Action Plane REST contract |
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

The product language is now plane-based. Canonical Action Plane routes live under `/api/v1/action/*` and are owned by `action-plane.yaml`. Legacy AEP/root paths belong in `aep.yaml` or a compatibility registry, not in the canonical Action Plane contract. `data-cloud.yaml` owns non-Action Data Cloud surfaces.

# Data Cloud Contracts

## Purpose

`products/data-cloud/contracts` is the Contract Plane for Data Cloud. It contains the public API and schema contracts used by the Data Cloud UI, SDKs, integration tests, downstream products, and deployment validation.

The Contract Plane is the source of truth for public shapes. Runtime implementations live in Data Cloud planes and delivery modules, not in this folder.

## Contents

| File | Role |
| --- | --- |
| `openapi/data-cloud.yaml` | Canonical Data Cloud REST API contract |
| `openapi/aep.yaml` | Current Action Plane REST contract while the runtime is still AEP-named |
| `openapi/action-plane.yaml` | Target Action Plane REST contract name; create this during the contract rename migration |

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

The product language is now plane-based. Existing `aep.yaml` paths remain valid until the Action Plane contract rename is completed. New documentation should refer to the Action Plane contract and avoid positioning AEP as a separate product.

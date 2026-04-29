# contracts

## Purpose

`products/aep/contracts` contains the canonical AEP OpenAPI specification. It is the single source of truth for the AEP HTTP API surface and is used to:

- Generate typed client SDKs for consumers (UI, gateway, cross-product integrations)
- Validate the running server's route shapes in CI via the contracts parity gate
- Serve as the input for Swagger UI documentation

## Contents

| File | Role |
|---|---|
| `openapi.yaml` | AEP REST API specification (OpenAPI 3.x) |

## Boundaries

- This module **defines** the contract; implementation lives in `server` and `aep-api`
- Breaking changes to `openapi.yaml` require a versioned migration strategy — no silent field removal or type changes
- The `build.gradle.kts` validates the spec as part of `check`

## Verification

```bash
./gradlew :products:aep:contracts:check
```

# DMOS API Contracts

**Canonical source of truth**: `api-contract.yaml` (OpenAPI 3.0 specification)

This directory contains the canonical API contract documentation for DMOS. The OpenAPI specification (`api-contract.yaml`) is the single source of truth for all API contracts.

## Contract Sources

- **OpenAPI Specification**: [api-contract.yaml](./api-contract.yaml) - Canonical source, generated programmatically from backend routes
- **Human-readable docs**: [API_CONTRACT.md](./API_CONTRACT.md) - Generated from OpenAPI spec

## Contract Generation

The OpenAPI specification is generated from the backend Java servlet definitions using `DmosOpenApiGenerator`. To regenerate:

```bash
cd products/digital-marketing/dm-api
./gradlew generateOpenApiSpec
```

## Contract Validation

P2-024: A drift detection test ensures the OpenAPI spec stays in sync with the backend implementation. The test validates:
- All registered servlet endpoints are documented in the OpenAPI spec
- No orphaned endpoints exist in the spec
- HTTP methods, paths, and parameters match between code and spec

See `OpenApiContractValidationTest.java` for the validation implementation.

## API Contract Principles

1. **Single source of truth**: The OpenAPI spec is generated from code, not hand-written
2. **Fail-closed validation**: CI blocks if the spec drifts from the implementation
3. **Type safety**: Client types are generated from the spec, not hand-written
4. **Documentation parity**: Human-readable docs are derived from the spec

## Related Tasks

- P0-009: Generate canonical OpenAPI contract from backend routes and enforce in CI
- P1-048: Add OpenAPI/client generation CI


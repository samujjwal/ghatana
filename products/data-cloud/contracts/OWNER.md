# Owner: Data Cloud Contract Plane

**Parent product:** Data Cloud  
**Primary owner:** Data Cloud platform team  
**Scope:** Public API, event, schema, and SDK contract truth

## Responsibility

This folder owns public Data Cloud contracts:

- Data Cloud REST contracts
- Action Plane REST contracts
- public event/entity/pipeline/action schemas
- contract validation tasks
- SDK generation inputs
- runtime contract drift checks

## Key Files

| File | Purpose |
| --- | --- |
| `openapi/data-cloud.yaml` | Canonical Data Cloud OpenAPI specification |
| `openapi/aep.yaml` | Current AEP-named Action Plane OpenAPI specification |
| `openapi/action-plane.yaml` | Target Action Plane OpenAPI name for the next migration step |

## Boundaries

- Contracts must not depend on runtime implementation modules.
- Data Cloud planes and delivery modules must conform to these contracts.
- AEP is not a separate parent product here; it is the current runtime implementation behind the Action Plane.

## Validation

- Gradle task: `:products:data-cloud:contracts:check`
- Data Cloud spec validation: `validateDataCloudSpec`
- Action Plane compatibility validation: `validateAepSpec` until the contract file is renamed
- OpenAPI drift checks remain hard gates

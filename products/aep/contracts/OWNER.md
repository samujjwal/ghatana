# Owner: AEP Contracts

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

Canonical OpenAPI contract for AEP. Serves as the source of truth for API specifications.

- OpenAPI specification authoring
- Contract validation tasks
- Gateway contract copy verification
- Spec versioning

## Key Files

| File | Purpose |
|------|---------|
| `openapi.yaml` | Canonical AEP OpenAPI specification |

## Validation

- Gradle task: `:products:aep:contracts:validateAepSpec`
- CI status: **PASS** (hard gate)
- Drift check: Gateway copy vs canonical (in CI)

## Dependencies

None (root contract definitions)

## Audit Status

- Last audited: 2026-04-29
- Validation: PASS
- OpenAPI drift test: Pinned in CI (AEP-A2, AEP-A8)

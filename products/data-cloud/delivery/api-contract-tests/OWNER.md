# Owner: Data-Cloud API

**Team:** Data-Cloud Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-04-29  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Canonical OpenAPI 3.1 contract and drift-validation module for Data-Cloud.
Owns the authoritative `openapi.yaml` specification and tests that ensure the
live router registration never deviates from the published contract.

## Key Artifacts

| Artifact | Purpose |
|----------|---------|
| `openapi.yaml` | Authoritative OpenAPI 3.1 contract (211 paths) |
| Contract tests | Verify zero drift between spec and router registrations |

## Dependencies

- `products:data-cloud:launcher` — router route registry (test-only)
- `scripts/check-openapi-drift.sh` — shell-level drift guard

## Consumers

- CI — `check-openapi-drift.sh` must pass on every PR
- SDK generation — generates Java, TypeScript, and Python clients from this spec
- External API consumers

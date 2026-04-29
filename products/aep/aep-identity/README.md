# aep-identity

## Purpose

`products/aep/aep-identity` resolves and manages agent identities within AEP. It owns:

- `IdentityResolutionService` — the canonical service for resolving agent principal from a token or session
- `AepLocalIdentityResolver` — in-process resolver for development and integration tests
- `JdbcAgentIdentityResolver` — JDBC-backed resolver for production deployments

## Boundaries

- **Uses:** `platform:java:security` for credential and session primitives; `platform:java:database` for JDBC access
- **Does not own:** authentication (JWT verification) — that is `aep-security`; agent lifecycle — that is `aep-agent-runtime`
- **Single source of truth:** all components that need an agent principal must resolve through `IdentityResolutionService`

## Key classes

| Class | Role |
|---|---|
| `IdentityResolutionService` | Canonical resolver façade; delegates to a configured `AgentIdentityResolver` |
| `JdbcAgentIdentityResolver` | Production resolver backed by an agents identity table |
| `AepLocalIdentityResolver` | Development resolver; no database required |

## Verification

```bash
./gradlew :products:aep:aep-identity:test
```

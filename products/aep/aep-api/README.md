# aep-api

## Purpose

`products/aep/aep-api` defines the HTTP API surface and data exploration contracts for AEP. It owns:

- REST endpoint contracts for temporal feature queries and correlated event exploration
- Transport models for `ExplorationEvent`, `CorrelatedEventGroup`, and `TemporalFeatures`
- Data-exploration request/response types used across the server and gateway layers

## Boundaries

- **Uses:** `aep-engine` for domain model references
- **Does not own:** HTTP server wiring — that belongs to `server`; persistence — that belongs to `aep-registry`
- **Does not own:** authentication/authorisation — that belongs to `aep-security`
- Transport models defined here must remain stable across minor versions; breaking changes require a migration strategy

## Key types

| Type | Role |
|---|---|
| `ExplorationEvent` | Single event as returned by the data-exploration API |
| `CorrelatedEventGroup` | Set of events sharing a correlation key |
| `TemporalFeatures` | Time-windowed statistical features over an event stream |

## Verification

```bash
./gradlew :products:aep:aep-api:test
```

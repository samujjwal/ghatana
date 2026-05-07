# aep-engine

## Purpose

`products/data-cloud/planes/action/engine` is the core AEP execution engine. It owns the runtime primitives that all pipeline stages build on:

- Pipeline and stage execution lifecycle
- Operator abstractions (`EventPipeline`, `RuntimeStage`, connector registry)
- Event processing models and state stores
- Pattern evaluation and event routing

## Boundaries

- **Uses:** `platform:java:core`, `platform:java:observability` for shared platform infrastructure
- **Does not own:** agent runtime, identity resolution, scaling, or compliance — those belong to their respective modules
- **Does not own:** HTTP endpoints or persistence — those belong to `aep-api` and `aep-registry`

## Key classes

| Class | Role |
|---|---|
| `EventPipeline` | Composable pipeline of stages; immutable once constructed |
| `RuntimeStage` | Single execution stage; wraps an operator with lifecycle callbacks |
| `ConnectorRegistry` | Registry of all configured source/sink connectors |

## Verification

```bash
./gradlew :products:data-cloud:planes:action:engine:test
```

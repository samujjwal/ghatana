# server

## Purpose

`products/aep/server` is the AEP Java runtime server — the deployable process that hosts all AEP services. It owns:

- `AepCoreModule` — ActiveJ server bootstrap; wires HTTP, gRPC, auth, and observability
- `AepProductionModule` — production-profile module bindings
- `AepLearningModule` — learning-pipeline module bindings
- `AepRuntimeProfile` — runtime profile enumeration (`PRODUCTION`, `LEARNING`, `DEBUG`)
- Policy provenance tracking (`PolicyProvenanceRecord`, `PolicyActivationMode`)

## Boundaries

- **Composes:** `aep-engine`, `aep-central-runtime`, `aep-security`, `aep-observability`, `orchestrator`, `aep-analytics`, `aep-compliance`, and `aep-scaling`
- **Serves:** HTTP via ActiveJ; consumed by `gateway`
- **Does not own:** domain logic — all business logic lives in the composed modules above

## Running locally

```bash
./gradlew :products:aep:server:run
```

## Verification

```bash
./gradlew :products:aep:server:test
```

# aep-event-cloud

## Purpose

`products/aep/aep-event-cloud` bridges AEP with the Data Cloud event storage backend. It owns:

- `AepEventCloudFactory` — the bootstrap factory that wires a Data Cloud–backed event cloud into the AEP runtime
- `DataCloudBackedEventCloud` — the canonical event-cloud implementation backed by the Data Cloud event store
- `DataCloudEventCloudAdapter` — thin translation layer between Data Cloud collection events and AEP event models

## Boundaries

- **Uses:** `products:data-cloud:*` for event storage; `aep-engine` for AEP event domain models
- **Does not own:** Data Cloud business logic — all Data Cloud semantics live in the data-cloud product tree
- **Integration point:** this is the only module allowed to cross the AEP → Data Cloud product boundary at runtime

## Key classes

| Class | Role |
|---|---|
| `AepEventCloudFactory` | Wires a `DataCloudBackedEventCloud` with configuration at startup |
| `DataCloudBackedEventCloud` | Implements the AEP event-cloud SPI against the Data Cloud store |
| `DataCloudEventCloudAdapter` | Translates between Data Cloud and AEP event schemas |

## Verification

```bash
./gradlew :products:aep:aep-event-cloud:test
```

# Owner: Data-Cloud

**Team:** Data Platform Team  
**Slack:** #platform-data-cloud  
**On-call:** Data Platform on-call rotation  
**Architecture lead:** Data Platform Lead  
**Boundary audit score:** 6/10 (2026-03-21)  

## Responsibility

Data-Cloud is an **independent governed data/storage product** for Ghatana. It:

- Manages entity storage, metadata, schemas, retention, audit, and pluggable persistence
- Provides storage-plane event logs for append, replay, audit, and integration records
- Provides analytics, reporting, governance, and memory persistence services
- Hosts the feature store, model metadata, and plugin-driven capability platform
- Supports standalone and integrated deployment modes
- Exposes public contracts consumed by AEP and other products

**Domain boundary:** Data-Cloud owns governed data storage, metadata, schemas, audit, retention, encryption support, feature engineering substrate, plugin lifecycle, and execution metadata persistence. It does not own complex event processing, EventCloud semantics, PatternSpec/EPL, pattern matching, pattern learning/adaptation, or agent orchestration; those remain AEP concerns.

**AEP integration rule:** Data-Cloud may persist AEP-owned metadata, events, checkpoints, results, memory, and EventCloud storage records when called through public contracts or stable SPI, but it must not import AEP modules. AEP may depend on Data-Cloud public APIs/contracts and storage plugins to perform higher-level adaptive event processing without creating a circular dependency.

## Key Product-Owned Shared Libraries

| Library | Consumers |
|---------|-----------|
| `data-cloud/spi` | AEP, YAPPC, App-Platform |
| `data-cloud/platform-api` | Product and platform HTTP/API consumers |
| `data-cloud/kernel-bridge` | Kernel adapter bridge for external product integration |
| `@ghatana/canvas` (joint with YAPPC) | YAPPC, Data-Cloud UI |

## Consumers

All products may consume Data-Cloud data and storage-plane event services. AEP is the primary adaptive event intelligence consumer through public Data-Cloud contracts and optional Data-Cloud-backed EventCloud persistence plugins.

# Owner: Data-Cloud

**Team:** Data Platform Team  
**Slack:** #platform-data-cloud  
**On-call:** Data Platform on-call rotation  
**Architecture lead:** Data Platform Lead  
**Boundary audit score:** 6/10 (2026-03-21)  

## Responsibility

Data-Cloud is an **independent AI/ML-native data product** for Ghatana. It:

- Manages the four-tier event log (journal, hot, warm, cold)
- Provides entity, event, analytics, reporting, governance, and memory data services
- Hosts the feature store, model metadata, and plugin-driven capability platform
- Supports standalone and integrated deployment modes
- Exposes public contracts consumed by AEP and other products

**Domain boundary:** Data-Cloud owns data storage, event streaming, analytics, reporting, AI/ML-native assistance, feature engineering, plugin lifecycle, and execution metadata persistence. It does NOT own agentic orchestration or execution; those belong to AEP.

**AEP integration rule:** Data-Cloud may publish agentic work requests and persist agent definitions, memory, checkpoints, and results, but it must not import AEP modules. AEP depends on Data-Cloud public APIs/contracts and event-cloud to perform agentic processing without creating a circular dependency.

## Key Product-Owned Shared Libraries

| Library | Consumers |
|---------|-----------|
| `data-cloud/spi` | AEP, YAPPC, App-Platform |
| `data-cloud/platform-api` | Product and platform HTTP/API consumers |
| `data-cloud/platform-client` | Internal and product-side client integrations |
| `@ghatana/canvas` (joint with YAPPC) | YAPPC, Data-Cloud UI |

## Consumers

All products may consume Data-Cloud data and event services. AEP is the primary agentic-processing consumer through event-cloud and public Data-Cloud contracts.

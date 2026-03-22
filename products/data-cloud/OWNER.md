# Owner: Data-Cloud

**Team:** Data Platform Team  
**Slack:** #platform-data-cloud  
**On-call:** Data Platform on-call rotation  
**Architecture lead:** Data Platform Lead  
**Boundary audit score:** 6/10 (2026-03-21)  

## Responsibility

Data-Cloud is the **event backbone and data platform** for Ghatana. It:

- Manages the four-tier event log (journal, hot, warm, cold)
- Provides the event store SPI consumed by all products
- Hosts the Feature Store for ML pipelines
- Owns canvas-core (`@ghatana/canvas`) jointly with YAPPC

**Domain boundary:** Data-Cloud owns data storage, event streaming, and feature engineering. It does NOT own agent logic (AEP) or workflow orchestration (YAPPC).

## Key Product-Owned Shared Libraries

| Library | Consumers |
|---------|-----------|
| `data-cloud/spi` | AEP, YAPPC, App-Platform |
| `data-cloud/platform` (SDK) | YAPPC, App-Platform |
| `@ghatana/canvas` (joint with YAPPC) | YAPPC, Data-Cloud UI |

## Consumers

All products consume the Data-Cloud event backbone for real-time event streaming.

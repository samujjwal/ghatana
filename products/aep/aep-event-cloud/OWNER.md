# Owner: AEP Event Cloud

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

Data-Cloud bridge plugin for event processing. Provides integration between AEP and the Data-Cloud event backbone.

- Event intake from Data-Cloud event streams
- Event routing to registered agent pipelines
- Event transformation and normalization
- Dead letter handling and retry policies

## Key Components

| Component | Purpose |
|-----------|---------|
| Event ingress adapters | Data-Cloud to AEP event mapping |
| Event egress handlers | AEP to downstream systems |
| Bridge configuration | Tenant-scoped event routing rules |

## Dependencies

- `platform:java:messaging`
- `products:data-cloud:platform-event`
- `products:aep:aep-operator-contracts`

## Audit Status

- Last audited: 2026-04-29
- Test coverage: 3 source files, integration tests pending

# Owner: AEP Analytics

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

Pipeline observability and metrics. Provides analytics and insights into pipeline execution performance and behavior.

- Pipeline execution metrics collection
- Performance analytics and profiling
- Execution tracing and span management
- Cost attribution and resource accounting
- Pattern effectiveness analytics

## Key Components

| Component | Purpose |
|-----------|---------|
| Metrics collectors | Pipeline and operator metrics |
| Analytics exporters | Metrics shipping to observability platform |
| Cost attribution | Tenant-scoped resource accounting |

## Dependencies

- `platform:java:observability`
- `products:aep:aep-operator-contracts`

## Audit Status

- Last audited: 2026-04-29
- Test coverage: Medium

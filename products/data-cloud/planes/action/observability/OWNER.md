# Owner: AEP Observability

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

AEP-specific tracing and orchestration instrumentation. Provides deep visibility into AEP operations.

- Distributed tracing for pipeline execution
- SLO metrics and alerting signals
- Health check implementations (`/health/deep`)
- Multi-module trace propagation
- Dashboard and alert rule definitions

## Key Components

| Component | Purpose |
|-----------|---------|
| Tracing instrumentation | Pipeline execution spans |
| Health indicators | Deep health check implementations |
| Metrics emitters | SLO and operational metrics |
| Alert configurations | Prometheus alert rules |

## Dependencies

- `platform:java:observability`
- `platform:java:monitoring`

## Audit Status

- Last audited: 2026-04-29
- Test coverage: 4 source files, 2 test files (thin coverage - AEP-A10)
- Multi-module trace propagation: Needs expansion

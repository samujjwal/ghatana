# Owner: AEP Engine

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

Core pipeline execution engine for the AEP platform.

- Pattern mining and learning (sequence mining, temporal correlation analysis)
- Pipeline runtime adaptation and materialization
- Event processing operators and connectors
- Learning episode management and policy promotion

## Key Components

| Component | Purpose |
|-----------|---------|
| `com.ghatana.aep.learning.mining` | Pattern mining algorithms (Apriori, sequence mining) |
| `com.ghatana.pipeline.runtime.adapter` | Pipeline runtime materialization |
| `com.ghatana.pipeline.registry.connector` | Connector registry and operators |

## Dependencies

- `platform:java:core`
- `platform:java:event`
- `platform:java:workflow`
- `products:aep:aep-operator-contracts`

## Audit Status

- Last audited: 2026-04-29
- Package integrity: Fixed (AEP-A1)
- Test coverage: 76 test files

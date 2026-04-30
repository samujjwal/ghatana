# Owner: AEP Operator Contracts

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

Shared operator and pipeline contracts. Defines the canonical interfaces and data structures used across all AEP modules and consumers.

- Operator specification schemas
- Pipeline definition contracts
- Connector type definitions
- Event type registry
- Version compatibility guarantees

## Key Components

| Component | Purpose |
|-----------|---------|
| `ConnectorSpec` | Connector configuration contract |
| `PipelineSpec` | Pipeline definition contract |
| `OperatorSpec` | Operator metadata and parameters |
| `EventType` | Canonical event type definitions |

## Dependencies

- `platform:contracts`
- `platform:java:domain`

## Consumers

All AEP modules and products that build operators or pipelines.

## Audit Status

- Last audited: 2026-04-29
- Contract validation: PASS via `:products:aep:contracts:validateAepSpec`

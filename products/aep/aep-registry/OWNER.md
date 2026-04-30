# Owner: AEP Registry

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

Operator, pipeline and agent registry. Central registration and discovery service for all AEP entities.

- Operator registration and versioning
- Pipeline catalog and discovery
- Agent metadata and capabilities
- Pattern registry and management
- SPI (Service Provider Interface) for extensions

## Key Components

| Component | Purpose |
|-----------|---------|
| `OperatorRegistry` | Operator registration and lookup |
| `PipelineRegistry` | Pipeline catalog management |
| `AgentRegistry` | Agent metadata and execution routing |
| `PatternRegistry` | Learned pattern storage and retrieval |

## Dependencies

- `platform:java:core`
- `platform:java:database`
- `products:aep:aep-operator-contracts`

## Audit Status

- Last audited: 2026-04-29
- RegistryAndFactoryTest: Has disabled tests (AEP-A7)
- Placeholder/TODO debt: Tracked in security paths (AEP-A6)

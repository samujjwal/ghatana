# Owner: Data-Cloud Agent Registry

**Team:** Data-Cloud Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-03-23  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Durable persistence backend for the Data-Cloud Agent Registry. Stores agent descriptors
as `EntityRecord` entries in the `agent-registry` Data-Cloud collection and publishes
lifecycle events to `agent-registry-events`.

> **Discovery** is owned by `AepCentralRegistryService` (products/agentic-event-processor).
> This module is the persistence layer only.

## Key Interfaces

| Interface | Implemented |
|-----------|-------------|
| `AgentRegistry` (SPI) | `DataCloudAgentRegistry` |
| `AgentLogicProvider` (SPI) | `DataCloudAgentLogicProvider` |

## Dependencies

- `products:data-cloud:platform` — EntityRecord persistence
- `products:data-cloud:spi` — EventLogStore
- `libs:agent-framework` — AgentRegistry SPI

## Consumers

- `products:agentic-event-processor` (discovery)
- `products:data-cloud:launcher` (HTTP API registry routes)

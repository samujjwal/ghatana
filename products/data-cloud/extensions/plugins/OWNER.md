# Owner: Data-Cloud Platform Plugins

**Team:** Data-Cloud Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-04-29  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Plugin lifecycle management, connector registry, and optional backend integrations
for the Data-Cloud product. Owns plugin discovery, capability registration, health
checking, and graceful plugin degradation when optional backends are unavailable.

Optional connectors (Kafka, S3, Redis, external model registries) are loaded and
activated through this module. Core product functionality must remain available
even when all optional plugins are offline.

## Key Interfaces

| Interface/Class | Purpose |
|-----------------|---------|
| `PluginRegistry` | Discovers and registers plugins at startup |
| `ConnectorPlugin` | Plugin SPI for optional backend connectors |
| `PluginHealthChecker` | Per-plugin liveness and readiness probes |
| `PluginLifecycleManager` | Start/stop/reload lifecycle for plugins |

## Dependencies

- `products:data-cloud:spi` — plugin port interfaces
- `platform:java:observability` — plugin health metrics
- Optional: Kafka, S3, Redis, external model registry client libraries

## Consumers

- `products:data-cloud:platform-launcher` — plugin activation at startup
- `products:data-cloud:launcher` — plugin status endpoint (`/api/v1/plugins`)

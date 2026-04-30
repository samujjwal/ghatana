# Owner: Data-Cloud Platform Entity

**Team:** Data-Cloud Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-04-29  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Entity domain model, schema definitions, and entity store SPI implementations
for the Data-Cloud product. Owns the canonical entity record format, schema
evolution rules, connector capability negotiation, and tenant-isolated entity storage.

## Key Interfaces

| Interface/Class | Purpose |
|-----------------|---------|
| `EntityRecord` | Immutable entity record (Lombok `@Builder`) |
| `EntityStore` | CRUD + query SPI for entity persistence |
| `SchemaColumn` | Column definition with type and constraints |
| `ConnectorCapability` | Connector capability negotiation |

## Dependencies

- `products:data-cloud:spi` — `EntityStore` port interface
- `platform:java:database` — JDBC/connection pool utilities

## Consumers

- `products:data-cloud:platform-launcher` — entity persistence wiring
- `products:data-cloud:agent-registry` — agent descriptor persistence
- `products:data-cloud:platform-api` — entity REST handlers

# Owner: Data-Cloud Agent Catalog

**Team:** Data-Cloud Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-04-29  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Hosts and validates Data-Cloud agent catalog YAML definitions.
Validates agent definitions against schema requirements and business rules,
ensuring correctness before runtime registration.

This module is deliberately lightweight: it contains validation logic and
catalog metadata only. No runtime-heavy dependencies are allowed here.

## Key Interfaces

| Class | Purpose |
|-------|---------|
| `AgentDefinitionValidator` | Validates individual agent definition YAML files |

## Dependencies

- None beyond standard Jackson YAML parsing

## Consumers

- `products:data-cloud:platform-launcher` — loads validated catalog at startup
- CI — runs `AgentCatalogValidationTest` to catch bad agent definitions before merge

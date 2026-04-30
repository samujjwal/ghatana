# Owner: AEP Identity

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

Identity and tenant context management for AEP.

- Tenant identity resolution
- Service identity for AEP components
- Identity propagation through pipelines
- Tenant isolation validation

## Key Components

| Component | Purpose |
|-----------|---------|
| `TenantIdentityResolver` | Tenant identity from requests |
| `ServiceIdentityProvider` | AEP service credentials |
| `IdentityPropagator` | Identity through pipeline execution |

## Dependencies

- `platform:java:identity`
- `platform:java:security`

## Audit Status

- Last audited: 2026-04-29
- Source files: 12

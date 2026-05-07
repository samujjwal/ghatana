# Owner: AEP Gateway

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

API gateway for AEP. Provides edge routing, authentication, and rate limiting.

- HTTP request routing to AEP services
- Authentication and JWT validation
- Rate limiting and throttling
- Request/response transformation
- OpenAPI serving

## Key Components

| Component | Purpose |
|-----------|---------|
| Fastify server | Gateway HTTP server |
| Route handlers | Request routing logic |
| Auth middleware | JWT validation |

## Dependencies

- Node.js runtime
- Fastify framework

## Audit Status

- Last audited: 2026-04-29
- Note: Fastify types may be missing per CI comments

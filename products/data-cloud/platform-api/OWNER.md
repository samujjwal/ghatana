# Owner: Data-Cloud Platform API

**Team:** Data-Cloud Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-04-29  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

HTTP/API transport layer for Data-Cloud. Owns all route handlers, request/response
models, middleware, and WebSocket/SSE surfaces exposed by the product runtime.

Strictly decoupled from domain logic: handlers delegate immediately to domain
services in `platform-launcher` and do not own business rules.

## Key Interfaces

| Interface/Class | Purpose |
|-----------------|---------|
| Route handlers | All `/api/v1/**` HTTP handlers (211 registered paths) |
| WebSocket handlers | Real-time streaming endpoints |
| SSE handlers | CDC event tailing (`/api/v1/events/**`) |
| AI assist handlers | AI-backed query and recommendation surfaces |

## Dependencies

- `products:data-cloud:platform-launcher` — domain services
- `platform:java:http` — HTTP server and router
- `platform:java:observability` — request tracing

## Consumers

- `products:data-cloud:launcher` — wires all handlers into the router
- `products:data-cloud:api` (contract validation) — verifies route registration

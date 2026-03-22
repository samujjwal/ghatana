# Owner: Authentication Gateway Service

**Team:** Security Team (Platform Sub-team)  
**Slack:** #platform-security  
**On-call:** Platform on-call rotation  
**Decision:** ADR-013 (2026-03-21)

## Responsibility

This service is the cross-product authentication gateway for all Ghatana products. It handles:
- JWT token validation and issuance
- Tenant extraction from request headers
- Rate limiting per tenant/IP
- Credential store abstraction (JDBC backend)

## Service Details

- **Entry point:** `AuthGatewayLauncher` (ActiveJ HttpServer)
- **Port:** 8080 (configurable via env `AUTH_GATEWAY_PORT`)
- **Dependencies:** `platform:java:http`, `platform:java:security`, PostgreSQL

## Contributing

Changes to this service require Security Team approval. Authentication is security-critical — all PRs require security review before merge.

## Consumers

All products authenticate through this gateway. Do not bypass it.

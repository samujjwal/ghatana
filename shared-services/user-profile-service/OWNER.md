# Owner: User Profile Service

**Team:** Platform Team  
**Slack:** #platform-infra  
**On-call:** Platform on-call rotation  
**Decision:** ADR-013 (2026-03-21)

## Responsibility

Cross-product user profile storage. Centralises user data that multiple products access (YAPPC, Finance, DCMAAR).

## Status

⚠️ In early development — domain model and API contract are being defined.

## Next Steps (P1)

1. Define `UserProfile` protobuf in `contracts/`
2. Implement REST endpoint: `GET /users/{id}`, `PUT /users/{id}`
3. Add Flyway migration for PostgreSQL schema
4. Register in service mesh / service discovery

## Consumers

| Product | Usage |
|---------|-------|
| YAPPC | User preferences, workspace membership |
| Finance | User settings, notification preferences |
| DCMAAR | Guardian parent/child relationship |

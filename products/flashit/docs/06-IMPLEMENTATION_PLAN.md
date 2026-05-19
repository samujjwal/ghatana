# FlashIt — Implementation Plan

**Status:** Alpha / Full-Stack  
**Stack:** Node.js (Fastify) · Java 21 (ActiveJ) · React Native · React Web  
**Domain:** Personal context capture, moments, reflection, journaling, semantic search

---

## Product Shape

FlashIt is a full-stack product with client, gateway, and AI agent surfaces:

| Surface         | Kind                                         | Status  |
| --------------- | -------------------------------------------- | ------- |
| `gateway`       | Node.js Fastify REST API (port 2900)         | Alpha   |
| `agent-service` | Java 21 ActiveJ AI agent service (port 8090) | Alpha   |
| `mobile`        | React Native (Expo) mobile app               | Alpha   |
| `web`           | React web dashboard                          | Alpha   |
| `admin-cli`     | Admin CLI tool                               | Planned |

---

## Module Status

| Module                  | Purpose                                                                     | Status |
| ----------------------- | --------------------------------------------------------------------------- | ------ |
| Fastify Gateway         | REST API, JWT auth, rate limiting, billing integration                      | Alpha  |
| Java Agent Service      | Classification, embeddings, reflection, NLP, transcription, semantic search | Alpha  |
| React Native Mobile App | Moment capture, camera, audio recording, journaling UI                      | Alpha  |
| React Web Dashboard     | Dashboard, search, analytics, settings                                      | Alpha  |
| PostgreSQL + pgvector   | Persistent storage with semantic vector search                              | Alpha  |
| Redis                   | Cache and session management                                                | Alpha  |
| Weaviate                | Vector store for semantic moment search                                     | Alpha  |
| Meilisearch             | Full-text search index                                                      | Alpha  |
| Domain Pack             | FlashIt-specific boundary policy rules and compliance pack                  | Alpha  |
| Policy Pack             | Product-configured security, privacy, data retention policies               | Alpha  |

---

## Implemented Capabilities

| Capability                                      | Status |
| ----------------------------------------------- | ------ |
| Moment capture (text, photo, audio, video)      | Alpha  |
| Personal journaling and reflection prompts      | Alpha  |
| AI classification of moments by theme/topic     | Alpha  |
| Semantic embedding and vector similarity search | Alpha  |
| Privacy-sensitive sharing controls              | Alpha  |
| Subscription tier enforcement                   | Alpha  |
| Audit trail for data access and sharing         | Alpha  |
| Local-first dev compose with observability      | Alpha  |
| Kernel boundary policy integration              | Alpha  |

---

## Planned / Pending

| Capability                      | Status  | Notes                                                                                             |
| ------------------------------- | ------- | ------------------------------------------------------------------------------------------------- |
| Admin CLI                       | Planned | Interface design pending                                                                          |
| Collaborative sharing workflows | Alpha   | Existing but partial. Core sharing complete; group features pending. Evidence: `products/flashit` |
| Memory expansion agents         | Planned | Aggregate across moments over time                                                                |
| Real-time collaboration         | Planned | Socket-based; design pending                                                                      |
| E2E Playwright tests            | Partial | Configuration exists; full suite pending                                                          |

---

## Architecture Constraints

- **Event loop**: Java agent service runs on ActiveJ event loop — no blocking I/O on loop thread
- **Privacy**: Moment content is stored encrypted at rest; classification metadata is separated from content
- **Tenant isolation**: All API paths require authenticated user; cross-user data access is architecturally impossible at gateway layer
- **Fail-closed sharing**: Sharing requests require explicit user approval; default is private

---

## Build Commands

```bash
# Backend: Java agent service
./gradlew :products:flashit:agent-service:build

# Gateway: Node.js
pnpm --filter ./products/flashit/gateway build

# Mobile: Expo React Native
cd products/flashit/apps/mobile && pnpm expo build

# Web Dashboard
pnpm --filter ./products/flashit/apps/web build
```

---

## Local Development

```bash
# Start all FlashIt services locally (requires Docker)
docker compose -f products/flashit/docker-compose.local.yml up

# Or use Kernel dev phase
node ./scripts/kernel-product.mjs product dev flashit
```

Services use ports declared in `products/flashit/.env.example`. Port uniqueness is enforced by `check:local-dev-port-allocations`.

---

## Observability

- **Grafana/Prometheus/Jaeger/Loki**: All connected through shared observability compose template
- **Correlation IDs**: `X-Correlation-ID` propagated through gateway → agent service
- **Audit trail**: Sensitive moment access, sharing decisions, and deletions are audit-logged
- **Alerts**: Latency and error rate alerts declared in `products/flashit/monitoring/`

---

## Rollout Criteria

FlashIt is rollout-ready for local environment when:

1. All modules build and tests pass
2. Gateway starts and responds to `/health` at port 2900
3. Agent service starts and responds to health check at port 8090
4. Moment capture → classification → retrieval round-trip works in integration test
5. Privacy sharing controls validated (private by default, sharing requires approval)

---

## Promotion Criteria (Beyond Local)

- Admin CLI implemented
- Memory expansion agents implemented and tested
- Real-time collaboration design reviewed and approved
- Privacy audit conducted by external reviewer
- Full E2E Playwright suite passing in CI
- GDPR right-to-erasure flow validated end-to-end

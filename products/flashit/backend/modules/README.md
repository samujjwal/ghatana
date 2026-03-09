# Backend Modules

This directory contains standalone service modules that provide specialized functionality
for the FlashIt platform. These are designed as independently deployable microservices
that complement the main Fastify gateway.

## Module Overview

| Module          | Description                                      | Status         | Integration Notes                          |
| :-------------- | :----------------------------------------------- | :------------- | :----------------------------------------- |
| `collaboration` | WebSocket-based real-time collaboration           | Standalone     | Connects via Redis pub/sub                 |
| `compliance`    | GDPR/CCPA/ISO-27001 compliance checking           | Standalone     | Scheduled jobs + on-demand API             |
| `intelligence`  | AI reflection service (legacy)                    | **Deprecated** | Replaced by Java Agent (`backend/agent/`)  |
| `notification`  | Multi-channel notification delivery (BullMQ)      | Standalone     | Gateway triggers via BullMQ queues         |

## Architecture

```
┌──────────────────────┐     ┌──────────────────────────┐
│   Fastify Gateway    │────▶│   Redis (pub/sub + queue) │
│   (port 2900)        │     └────────┬─────────────────┘
└──────────────────────┘              │
                                      ├──▶ collaboration/ (WebSocket server)
                                      ├──▶ notification/  (BullMQ worker)
                                      └──▶ compliance/    (Scheduled worker)
```

## collaboration/

**File:** `collaboration-server.ts`

Real-time WebSocket server for collaborative sphere editing, live cursors, and
presence tracking. Communicates with the gateway via Redis pub/sub for events
like sphere sharing, comment additions, and live editing.

**Integration path:**
- Run as a separate process (e.g., `node collaboration-server.js`)
- Requires `REDIS_URL` environment variable
- Connects to the same PostgreSQL instance as the gateway
- Gateway publishes collaboration events; this module broadcasts to connected clients

## compliance/

**Files:**
- `compliance-service.ts` — Framework compliance checks (GDPR, CCPA, ISO-27001)
- `deletion-service.ts` — Handles data deletion requests and cascading purges
- `export-service.ts` — Generates user data export packages (GDPR Art. 20)
- `security-review-service.ts` — Automated security posture reviews

**Integration path:**
- Can run as scheduled cron workers or on-demand via gateway API calls
- Gateway routes (`/api/privacy/*`) already delegate deletion and export requests
- Compliance checks should run on a daily schedule

## intelligence/ (DEPRECATED)

**File:** `reflection-service.ts`

> **⚠️ DEPRECATED**: This module's functionality has been migrated to the
> **Java Agent** (`backend/agent/`). The Java Agent provides the same AI reflection,
> pattern detection, and insight generation capabilities with better performance
> using ActiveJ async processing and direct OpenAI Java SDK integration.

**Migration:**
- AI reflection → `backend/agent/` (ReflectionService)
- Pattern detection → `backend/agent/` (PatternService)
- Insight generation → `backend/agent/` (InsightService)

## notification/

**File:** `notification-service.ts`

Multi-channel notification delivery using BullMQ job queues. Supports:
- In-app notifications (stored in PostgreSQL, served via gateway API)
- Email notifications (via SMTP / MailHog in dev)
- Push notifications (via Expo Push or Firebase)

**Integration path:**
- Gateway's `notification-service.ts` (in `services/notifications/`) creates
  in-app notification records and enqueues delivery jobs
- This module picks up BullMQ jobs and handles email/push delivery
- Requires `REDIS_URL` and `SMTP_*` environment variables

## Running Modules Locally

```bash
# From products/flashit/

# Collaboration server
npx tsx backend/modules/collaboration/collaboration-server.ts

# Notification worker
npx tsx backend/modules/notification/notification-service.ts

# Compliance checks (one-off)
npx tsx backend/modules/compliance/compliance-service.ts
```

## Environment Variables

All modules share the gateway's `.env` configuration:

| Variable       | Required By          | Description                  |
| :------------- | :------------------- | :--------------------------- |
| `REDIS_URL`    | All                  | Redis connection string      |
| `DATABASE_URL` | All                  | PostgreSQL connection string |
| `SMTP_HOST`    | notification         | SMTP server host             |
| `SMTP_PORT`    | notification         | SMTP server port             |
| `SMTP_USER`    | notification         | SMTP auth username           |
| `SMTP_PASS`    | notification         | SMTP auth password           |

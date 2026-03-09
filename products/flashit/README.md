# FlashIt — Personal Context Capture Platform

A full-stack personal context capture platform that helps users record, organise, and derive meaning from their everyday thoughts, experiences, and media.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Clients                                                    │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐   │
│  │ React Native  │  │  React Web    │  │  Admin CLI    │   │
│  │ Mobile App    │  │  Dashboard    │  │  (planned)    │   │
│  └──────┬────────┘  └──────┬────────┘  └──────┬────────┘   │
│         │                  │                   │            │
│         └──────────────────┼───────────────────┘            │
│                            ▼                                │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Fastify Gateway  (Node.js · port 2900)             │    │
│  │  REST API · JWT Auth · Rate Limiting · Billing      │    │
│  └──────────────────────────┬──────────────────────────┘    │
│                             │  HTTP                         │
│                             ▼                                │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Java Agent Service  (ActiveJ · port 8090)          │    │
│  │  Classification · Embeddings · Reflection · NLP     │    │
│  │  Transcription · Semantic Search                    │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  Infrastructure                                             │
│  ┌──────────┐ ┌─────────┐ ┌──────────┐ ┌──────────────┐   │
│  │PostgreSQL│ │  Redis  │ │MinIO (S3)│ │ Prometheus + │   │
│  │ (Prisma) │ │ (Cache) │ │ (Media)  │ │   Grafana    │   │
│  └──────────┘ └─────────┘ └──────────┘ └──────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Technology Stack

| Layer | Technology | Notes |
|:------|:-----------|:------|
| Mobile | React Native + Expo | Offline-first, SQLite, background upload |
| Web | React + Vite | TanStack Query, Jotai, Tailwind CSS |
| Gateway | Node.js + Fastify | REST API, Prisma ORM, Stripe billing |
| AI Agent | Java 21 + ActiveJ | OpenAI GPT-4o, Whisper, embeddings |
| Database | PostgreSQL 15 | Prisma migrations |
| Cache | Redis 7 | Sessions, rate limiting |
| Storage | MinIO / S3 | Presigned URLs, media files |
| Monitoring | Prometheus + Grafana | Metrics, dashboards |

## Project Structure

```
products/flashit/
├── backend/
│   ├── agent/           # Java Agent Service (ActiveJ, 17 endpoints)
│   │   ├── src/main/java/com/ghatana/flashit/agent/
│   │   │   ├── FlashItAgentApplication.java
│   │   │   ├── AgentHttpRouter.java
│   │   │   ├── AgentConfig.java
│   │   │   ├── dto/             # 22 request/response records
│   │   │   └── service/         # 5 AI services
│   │   ├── build.gradle.kts
│   │   └── Dockerfile
│   └── gateway/         # Node.js Fastify API
│       ├── src/
│       │   ├── routes/          # 20+ route modules
│       │   ├── services/        # Business logic
│       │   ├── middleware/       # Auth, RBAC, monitoring
│       │   ├── plugins/         # Prometheus metrics
│       │   └── lib/             # Core utilities
│       └── prisma/
│           └── schema.prisma    # 25+ models
├── client/
│   ├── mobile/          # React Native (Expo)
│   ├── web/             # React (Vite)
│   └── shared/          # Shared types, API client, validators
├── monitoring/
│   ├── prometheus.yml
│   └── grafana/
│       ├── dashboards/  # Grafana dashboard JSON
│       └── provisioning/
└── docker-compose.local.yml
```

## Quick Start

### Prerequisites

- Node.js 20+
- Java 21 (for the Agent service)
- Docker & Docker Compose
- pnpm 9+

### 1. Start Infrastructure

```bash
cd products/flashit
docker compose -f docker-compose.local.yml up -d
```

Services started:
- PostgreSQL: `localhost:5432` (dev) / `5433` (test)
- Redis: `localhost:6383`
- MinIO: `localhost:9002` (API) / `9003` (console)
- MailHog: `localhost:8025` (web UI)
- Prometheus: `localhost:9090`
- Grafana: `localhost:3001` (admin/admin)

### 2. Configure Environment

```bash
cp .env.example .env.development
# Edit .env.development — set OPENAI_API_KEY for AI features
```

### 3. Run Database Migrations

```bash
cd backend/gateway
pnpm install
pnpm prisma migrate dev
```

### 4. Start the Gateway

```bash
cd backend/gateway
pnpm dev
# Listening on http://localhost:2900
```

### 5. Build & Start the Java Agent

```bash
cd backend/agent
./gradlew clean build
java -jar build/libs/flashit-agent-*.jar
# Listening on http://localhost:8090
```

Or via Docker:
```bash
docker compose -f docker-compose.local.yml up java-agent --build
```

### 6. Start the Web Client

```bash
cd client/web
pnpm install && pnpm dev
# http://localhost:3000
```

### 7. Start the Mobile Client

```bash
cd client/mobile
pnpm install && npx expo start
```

## API Overview

### Authentication (no prefix)
| Method | Path | Description |
|:-------|:-----|:------------|
| POST | /auth/register | Register new user |
| POST | /auth/login | Login |
| POST | /auth/refresh | Refresh tokens |
| POST | /auth/2fa/setup | Setup 2FA |
| GET | /auth/sessions | List sessions |

### Moments (/api/moments)
| Method | Path | Description |
|:-------|:-----|:------------|
| POST | /api/moments | Create moment (text/voice/image/video) |
| GET | /api/moments | List moments (paginated, filterable) |
| GET | /api/moments/:id | Get moment detail |
| PUT | /api/moments/:id | Update moment |
| DELETE | /api/moments/:id | Soft-delete moment |

### Spheres (/api/spheres)
| Method | Path | Description |
|:-------|:-----|:------------|
| POST | /api/spheres | Create sphere |
| GET | /api/spheres | List user's spheres |
| PUT | /api/spheres/:id | Update sphere |
| DELETE | /api/spheres/:id | Delete sphere |

### Search (/api/search)
| Method | Path | Description |
|:-------|:-----|:------------|
| POST | /api/search | Text / semantic / hybrid search |
| POST | /api/search/similar | Find similar moments |
| GET | /api/search/suggestions | Search suggestions |

### Collaboration (/api/collaboration)
| Method | Path | Description |
|:-------|:-----|:------------|
| POST | /api/collaboration/spheres/share | Share sphere |
| POST | /api/collaboration/comments | Add comment |
| POST | /api/collaboration/reactions | Toggle reaction |
| POST | /api/collaboration/follow | Follow/unfollow user |

### Notifications (/api/notifications)
| Method | Path | Description |
|:-------|:-----|:------------|
| GET | /api/notifications | List notifications |
| GET | /api/notifications/unread-count | Unread count |
| PATCH | /api/notifications/:id/read | Mark read |
| PATCH | /api/notifications/read-all | Mark all read |
| DELETE | /api/notifications/:id | Dismiss |

### AI Reflection (/api/reflection)
| Method | Path | Description |
|:-------|:-----|:------------|
| POST | /api/reflection/insights | Generate AI insights |
| POST | /api/reflection/patterns | Detect patterns |
| POST | /api/reflection/connections | Find connections |
| GET | /api/reflection/weekly | Weekly summary |
| GET | /api/reflection/monthly | Monthly summary |

### Billing (/api/billing)
| Method | Path | Description |
|:-------|:-----|:------------|
| GET | /api/billing/usage | Current usage vs limits |
| GET | /api/billing/limits | Tier limit details |
| POST | /api/billing/upgrade | Start Stripe checkout |

### Admin (/api/admin — requires ADMIN role)
| Method | Path | Description |
|:-------|:-----|:------------|
| GET | /api/admin/users | List all users |
| PATCH | /api/admin/users/:id/role | Update user role |
| PATCH | /api/admin/users/:id/suspend | Suspend/unsuspend |
| GET | /api/admin/content/flagged | Moderation queue |
| GET | /api/admin/stats | Platform statistics |

### Health (/api/health)
| Method | Path | Description |
|:-------|:-----|:------------|
| GET | /api/health | Basic health |
| GET | /api/health/detailed | All dependencies |
| GET | /api/health/ready | K8s readiness |
| GET | /api/health/live | K8s liveness |
| POST | /api/health/circuits/:name/reset | Reset circuit breaker (OPERATOR+) |
| GET | /metrics | Prometheus metrics |

## Java Agent Endpoints

All at `http://localhost:8090`:

| Method | Path | Description |
|:-------|:-----|:------------|
| GET | /health | Health check |
| GET | /ready | Readiness with agent list |
| GET | /api/v1/agents | List agents |
| POST | /api/v1/agents/classification/classify | Classify moment into sphere |
| POST | /api/v1/agents/classification/suggest-spheres | Sphere suggestions |
| POST | /api/v1/agents/embedding/generate | Generate embedding |
| POST | /api/v1/agents/embedding/batch | Batch embeddings |
| POST | /api/v1/agents/embedding/search | Semantic search |
| POST | /api/v1/agents/reflection/insights | Generate insights |
| POST | /api/v1/agents/reflection/patterns | Detect patterns |
| POST | /api/v1/agents/reflection/connections | Find connections |
| POST | /api/v1/agents/transcription/transcribe | Transcribe audio |
| GET | /api/v1/agents/transcription/status/:jobId | Transcription status |
| POST | /api/v1/agents/nlp/extract-entities | Entity extraction |
| POST | /api/v1/agents/nlp/analyze-sentiment | Sentiment analysis |
| POST | /api/v1/agents/nlp/detect-mood | Mood detection |
| GET | /api/v1/agents/:agentId/status | Agent health |

## Subscription Tiers

| Feature | Free | Pro ($9.99/mo) | Teams ($24.99/mo) |
|:--------|:-----|:---------------|:-------------------|
| Moments/month | 100 | 5,000 | Unlimited |
| Spheres | 3 | 50 | Unlimited |
| Storage | 1 GB | 50 GB | 500 GB |
| Transcription | 10 hrs | 100 hrs | Unlimited |
| AI Insights | 5/mo | 100/mo | Unlimited |
| Collaborators | 0 | 25 | Unlimited |
| Memory Expansion | 2/mo | 50/mo | Unlimited |

## Testing

```bash
# Gateway unit tests
cd backend/gateway && pnpm test

# Java Agent tests (15 tests)
cd backend/agent && ./gradlew test

# All tests via runner
pnpm test

# Integration tests
pnpm test:integration

# E2E tests
pnpm test:e2e
```

## Monitoring

- **Prometheus**: http://localhost:9090 — scrapes gateway (`:2900/metrics`) and agent (`:8090/metrics`)
- **Grafana**: http://localhost:3001 — pre-provisioned dashboard: *FlashIt Platform Overview*
  - Request rates, latency percentiles, error rates
  - Node.js memory, JVM heap
  - Redis commands, circuit breaker status
  - Java Agent endpoint breakdown

## Background Jobs (Scheduler)

The gateway runs periodic cron jobs via `scheduler.ts`:

| Job | Interval | Description |
|:----|:---------|:------------|
| Rate-limit cleanup | Hourly | Prune stale rate-limit records |
| Refresh token cleanup | Daily | Remove expired refresh tokens |
| Session cleanup | Daily | Remove expired sessions |
| Password reset cleanup | Daily | Remove expired reset tokens |
| Audit log retention | Daily | Delete 30-day-old audit events (free tier) |
| Data deletion execution | Daily | Execute confirmed deletion requests |

## RBAC Roles

| Role | Access |
|:-----|:-------|
| USER | Standard user |
| OPERATOR | Health endpoints, circuit breaker reset |
| ADMIN | User management, content moderation, stats |
| SUPER_ADMIN | All admin + role assignment |

## Key Design Decisions

1. **Hybrid Backend**: Node.js gateway for REST/CRUD, Java agent for AI processing
2. **Circuit Breakers**: Java Agent calls wrapped in circuit breakers with graceful degradation
3. **Presigned Uploads**: Media goes directly to S3/MinIO via presigned URLs (no proxy)
4. **Progressive Upload**: Large files (>50MB) use multipart chunked upload
5. **Offline-First Mobile**: SQLite local DB + queue-based sync
6. **Event Sourcing for Audit**: All critical operations emit AuditEvent records
7. **Stripe Webhooks**: Billing state changes driven by Stripe webhook events

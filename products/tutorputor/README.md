# TutorPutor — AI-Powered Tutoring Platform

**Product Owner:** @ghatana/tutorputor-team  
**Status:** Active, under audit-driven remediation  
**Stack:** TypeScript / React 19 / Java 21 / Fastify

## Purpose

**TutorPutor** is an adaptive AI tutoring platform that delivers personalized learning content to students and provides administrative tools for educators. It uses the ghatana AI integration layer for content generation and adaptive curriculum planning.

## Quick Start

### Docker Compose (recommended)

```bash
# Start all services (infra + apps)
docker compose --profile app-stack up

# Start infrastructure only (postgres + redis)
docker compose up -d

# Force rebuild after dependency or config changes
docker compose --profile app-stack up --build --force-recreate

# Clean restart (removes volumes, useful when workspace changes)
docker compose --profile app-stack down -v && docker compose --profile app-stack up --build --force-recreate
```

### ttr CLI

```bash
# Start development environment
ttr dev

# Run tests
ttr test

# Check system health
ttr doctor
```

See [bin/README.md](bin/README.md) for full CLI documentation.

High-level product truth lives in exactly two places:

- [docs/architecture/CURRENT_STATE.md](docs/architecture/CURRENT_STATE.md) for what TutorPutor does today
- [docs/PRODUCT_REALITY_AUDIT_2026-04-18.md](docs/PRODUCT_REALITY_AUDIT_2026-04-18.md) for the audit findings and remediation backlog

Use those documents over older plan, roadmap, or diagram files when they disagree.

## Architecture

```
tutorputor-web (React)    ─┐
tutorputor-admin (React)   ─┤→  API Gateway (Node)  →  Platform (Node/Fastify)  →  PostgreSQL
                           │                            ↓                        Redis
                           │                     Content Generation (Java)
                           │                     Simulation Engine
```

> **Mobile channel status**: `tutorputor-mobile` is **in development** and is not a
> production-ready learner channel. It is excluded from the architecture diagram above
> until it reaches functional parity with `tutorputor-web`. See
> [apps/tutorputor-mobile/](apps/tutorputor-mobile/) for current implementation status.
> (F-031)

Supported local validation topology:

- Gateway: `http://127.0.0.1:3200`
- Learner app: `http://127.0.0.1:3201`
- Admin app: `http://127.0.0.1:3202`
- Direct platform service: `http://127.0.0.1:7105`

### Module Map

| Path | Type | Purpose |
|------|------|---------|
| `apps/tutorputor-web/` | React app | Student-facing learning UI |
| `apps/tutorputor-admin/` | React app | Educator / admin dashboard |
| `apps/tutorputor-mobile/` | React Native | Mobile offline and sync foundation; full learner app shell is still pending |
| `apps/api-gateway/` | Node.js | API gateway + BFF layer |
| `services/tutorputor-platform/` | Node.js | Main platform service |
| `libs/tutorputor-core/` | Shared lib | Prisma schema & client |
| `libs/tutorputor-ai/` | Shared lib | AI utilities |
| `libs/tutorputor-ui/` | Shared lib | UI components |
| `contracts/` | TypeScript | API contracts |

## Current Delivery Notes

- Mobile app is in development with offline-first architecture (React Native 0.85, SQLite, MMKV, background sync). Core screens and navigation are implemented, but full production deployment to app stores is pending.
- Mobile is not a production-ready learner application yet. The repo contains React Native storage, sync, and offline primitives, but no shipped application shell or navigation entrypoint.
- VR remains a foundation/scaffold area. Runtime routes and schema support exist, but it is not a production-ready learner surface.
- Web offline support is partially implemented through a service worker, IndexedDB-backed caching, and queued progress mutations in `apps/tutorputor-web`.
- Real-time collaboration is implemented using WebSockets for cursor tracking and Redis pub/sub for chat messaging (not Redis streams as previously documented).

## Prerequisites

- Node.js 18+ (with Corepack)
- pnpm 10+
- Java 21
- Docker + Docker Compose

## Development Commands

Use the `ttr` CLI for all operations:

```bash
# Environment
ttr dev                 # Start gateway + learner + admin + direct platform + infra
ttr dev --no-seed       # Skip seeding
ttr dev --with-kafka    # Enable Kafka
ttr stop                # Stop all services

# Testing
ttr test                # Run all tests
ttr test --unit         # Unit tests only
ttr test --e2e          # End-to-end tests
ttr test --watch        # Watch mode

# Maintenance
ttr doctor              # System health check
ttr migrate             # Run database migrations
ttr seed                # Seed development data
ttr logs platform       # View platform logs
ttr clean --all         # Deep clean
```

## Key Documentation

| Document | Path |
|----------|------|
| **Getting Started** | [docs/guides/DEVELOPMENT_SETUP.md](docs/guides/DEVELOPMENT_SETUP.md) |
| **Onboarding** | [docs/guides/ONBOARDING.md](docs/guides/ONBOARDING.md) |
| **Deployment** | [docs/guides/DEPLOYMENT.md](docs/guides/DEPLOYMENT.md) |
| **CLI Reference** | [bin/README.md](bin/README.md) |
| **Architecture** | [docs/architecture/README.md](docs/architecture/README.md) |
| **API Documentation** | [docs/api/README.md](docs/api/README.md) |
| **Current State** | [docs/architecture/CURRENT_STATE.md](docs/architecture/CURRENT_STATE.md) |
| **Product Spec** | [docs/architecture/specs/PRODUCT_SPEC.md](docs/architecture/specs/PRODUCT_SPEC.md) |
| **Coding Standards** | [docs/guidelines/CODING.md](docs/guidelines/CODING.md) |

# TutorPutor — AI-Powered Tutoring Platform

**Product Owner:** @ghatana/tutorputor-team  
**Status:** Active  
**Stack:** TypeScript / React 19 / Java 21 / Fastify

## Purpose

**TutorPutor** is an adaptive AI tutoring platform that delivers personalized learning content to students and provides administrative tools for educators. It uses the ghatana AI integration layer for content generation and adaptive curriculum planning.

## Quick Start

```bash
# Start development environment
ttr dev

# Run tests
ttr test

# Check system health
ttr doctor
```

See [bin/README.md](bin/README.md) for full CLI documentation.

## Architecture

```
tutorputor-web (React)    ─┐
tutorputor-admin (React)   ─┤→  API Gateway (Node)  →  Platform (Node/Fastify)  →  PostgreSQL
tutorputor-mobile (RN)     ─┤                            ↓                        Redis
                           │                     Content Generation (Java)
                           │                     Simulation Engine
```

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
ttr dev                 # Start development
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
tr seed                # Seed development data
tr logs platform       # View platform logs
tr clean --all         # Deep clean
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

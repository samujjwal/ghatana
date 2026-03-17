# DCMAAR — Device/Context Monitoring, Agent & Automation Runtime

**Product Owner:** @ghatana/dcmaar-team  
**Status:** Active  
**Stack:** React 19 / React Native / Rust / TypeScript / Java 21

## Purpose

**DCMAAR** is the platform's cross-platform device agent and parental control system. It delivers:

- **Guardian** — parental control application (iOS, Android, Web dashboard)
- **Device monitoring** — browser extension + native daemon for real-time activity tracking
- **Agent daemon** — React Native agent running on child devices
- **Policy enforcement** — local enforcement loop receiving policies from backend via sync API

## Architecture

```
Guardian Dashboard (Web)  →  Guardian Backend API  →  Sync API  →  Agent (React Native)
                                                                          │
                                                             Policy Enforcement (local)
                                                                          │
                                                          Browser Extension (content monitoring)
```

### Key Modules

| Module / Path | Purpose |
|---------------|---------|
| `apps/guardian/` | Guardian mobile + web dashboard applications |
| `apps/agent-react-native/` | Child device agent (React Native, Jotai state) |
| `apps/device-health/` | Browser extension for device health monitoring |
| `libs/typescript/` | Shared TS libraries (design-system adapters, types, charts) |
| `libs/java/` | Java backend services |
| `libs/rust/` | Native daemon for deep OS-level monitoring |
| `contracts/` | Protobuf contracts for agent ↔ backend communication |

## Prerequisites

- Node.js 18+ / pnpm 10+
- React Native CLI + Expo
- Rust (for native daemon)
- Java 21 (for backend)
- Docker

## Local Development

```bash
# Frontend
cd products/dcmaar
pnpm install

# Run guardian web dashboard
pnpm --filter @ghatana/dcmaar-guardian-dashboard dev

# Run agent (Expo)
pnpm --filter @ghatana/dcmaar-agent-react-native start

# Java backend
./gradlew :products:dcmaar:build
```

## State Management

All apps use **Jotai** for application state and **TanStack Query** for server state.  
Do NOT use zustand — the migration is complete.

## Testing

See [JEST_TESTING_GUIDE.md](apps/guardian/apps/parent-mobile/JEST_TESTING_GUIDE.md) for React Native testing conventions.

```bash
# Unit tests
pnpm --filter @ghatana/dcmaar-guardian-dashboard test

# E2E
pnpm --filter @ghatana/dcmaar-guardian-dashboard test:e2e
```

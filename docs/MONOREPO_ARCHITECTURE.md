# Monorepo Architecture

> **Owner:** Platform Team | **Status:** Active | **Last Updated:** 2026-03-22

## Overview

The Ghatana monorepo follows a hierarchical architecture with clear separation between platform capabilities and product implementations.

## Directory Structure

```
ghatana/
├── docs/                    # Monorepo-level documentation
├── platform/                # Shared platform libraries
│   ├── java/               # Java platform modules
│   ├── typescript/         # TypeScript platform modules
│   └── contracts/          # API contracts and schemas
├── products/               # Product implementations
│   ├── tutorputor/        # AI Tutoring Platform
│   ├── phr/               # Personal Health Records
│   ├── yappc/             # YAPPC Product
│   ├── app-platform/      # Application Platform
│   ├── dcmaar/            # Data Center Management
│   ├── virtual-org/       # Virtual Organization
│   ├── software-org/      # Software Organization
│   └── data-cloud/        # Data Cloud Platform
├── shared-services/        # Shared operational services
├── libs/                   # Cross-cutting libraries
├── scripts/               # Build and automation scripts
└── config/                # Shared configuration
```

## Platform Layer

### Java Platform (`platform/java/`)
- `agent-core` - Agent framework core
- `agent-runtime` - Agent execution environment
- `agent-registry` - Agent registration and discovery
- `event-processor` - Event processing infrastructure
- `messaging` - Messaging abstractions

### TypeScript Platform (`platform/typescript/`)
- `accessibility-audit` - Accessibility testing tools
- `api` - API client abstractions
- `canvas` - Canvas components
- `collaboration` - Real-time collaboration
- `ui` - UI component library

### Contracts (`platform/contracts/`)
- OpenAPI specifications
- Protocol Buffer definitions
- JSON schemas
- Type definitions

## Product Layer

Each product contains:
- `docs/` - Product documentation
- `apps/` - Application implementations
- `services/` - Backend services
- `libs/` - Product-specific libraries

## Shared Services

- `auth-gateway` - Authentication gateway
- `auth-service` - Identity and access management
- `ai-inference-service` - AI model serving
- `ai-registry` - AI model registry

## Build System

- Gradle for Java/Kotlin projects
- pnpm + Turborepo for TypeScript projects
- Cargo for Rust projects
- Unified task orchestration via Turbo

## Key Architectural Decisions

See [docs/adr/](./adr/) for detailed architecture decision records.

## Related Documents

- [MONOREPO_VISION.md](./MONOREPO_VISION.md) - Strategic vision
- [GOVERNANCE.md](./GOVERNANCE.md) - Governance model
- [platform-libraries/](./platform-libraries/) - Platform library docs

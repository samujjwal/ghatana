# TutorPutor Documentation

TutorPutor is an AI-powered adaptive learning platform built as a monorepo with a Fastify/Node.js backend, React 19 web applications, and comprehensive simulation and content generation capabilities.

## Quick Start

- **Development**: Run `ttr dev` (see [../bin/README.md](../bin/README.md))
- **Architecture**: [architecture/README.md](architecture/README.md)
- **Product Spec**: [architecture/specs/PRODUCT_SPEC.md](architecture/specs/PRODUCT_SPEC.md)

## Overview

TutorPutor provides:

- **AI-Powered Tutoring**: Adaptive learning pathways with Ollama-based AI integration
- **Simulation Engine**: Interactive physics simulations with Matter.js
- **Content Generation**: Autonomous content creation with gRPC services
- **Multi-Tenant Architecture**: Scalable multi-tenant support with proper isolation
- **Real-time Collaboration**: WebSocket-based multiplayer learning experiences with Redis pub/sub
- **Mobile Support**: React Native mobile apps in development with offline-first architecture (SQLite + MMKV + background sync)

## Technology Stack

| Layer | Technology |
|-------|------------|
| Backend | Fastify, TypeScript, Prisma, Java 21 |
| Frontend | React 19, TypeScript |
| Mobile | React Native 0.85 |
| Database | PostgreSQL 15 |
| Cache | Redis 7 |
| Queue | BullMQ |
| gRPC | Connect, Protobuf |
| AI | Ollama, LangChain |
| Testing | Vitest, Playwright |

## Structure

```
docs/
├── architecture/          # System architecture & design
│   ├── README.md                       # Architecture overview & quick links
│   ├── CURRENT_STATE.md                # Current implementation state
│   ├── IMPLEMENTATION_PLAN.md          # Autonomous content roadmap
│   ├── TUTORPUTOR_FLOW_MAP.md          # System flow diagrams
│   ├── TUTORPUTOR_MODULE_INVENTORY.md  # Module catalog
│   ├── DESIGN_ARCHITECTURE.md          # High-level design
│   ├── diagrams/                       # Visual diagrams
│   └── specs/                          # Detailed specifications
│       ├── PRODUCT_SPEC.md             # Full product specification
│       ├── SIMULATION_ENGINE.md        # Simulation system
│       ├── SIMULATION_API.md           # Simulation API
│       ├── SSO_ARCHITECTURE.md         # SSO/OIDC/SAML
│       ├── OFFLINE_MODE.md             # Offline capabilities
│       ├── EVIDENCE_BASED_CONTENT.md   # Evidence-based learning
│       └── AUTONOMOUS_CONTENT_GENERATION_ROADMAP.md  # 30/60/90 day plan
├── guides/                # Developer guides
│   ├── DEVELOPMENT_SETUP.md            # Quick setup guide
│   ├── ONBOARDING.md                   # Developer onboarding guide
│   ├── DEPLOYMENT.md                   # Deployment guide
│   ├── content-studio/                 # Content authoring
│   ├── ai/                             # AI integration
│   ├── assessment/                     # Assessment guides
│   ├── analytics/                      # Analytics guides
│   ├── mobile/                         # Mobile app guides
│   ├── personalization/                # Personalization guides
│   └── simulation/                     # Simulation authoring
├── guidelines/            # Coding standards
│   └── CODING.md
├── operations/            # Deployment guides
└── usage/                 # User documentation
```

## Essential Links

| Topic | Document |
|-------|----------|
| **Getting Started** | [guides/DEVELOPMENT_SETUP.md](guides/DEVELOPMENT_SETUP.md) |
| **Onboarding** | [guides/ONBOARDING.md](guides/ONBOARDING.md) |
| **Architecture** | [architecture/README.md](architecture/README.md) |
| **Current State** | [architecture/CURRENT_STATE.md](architecture/CURRENT_STATE.md) |
| **Implementation** | [architecture/IMPLEMENTATION_PLAN.md](architecture/IMPLEMENTATION_PLAN.md) |
| **Product Spec** | [architecture/specs/PRODUCT_SPEC.md](architecture/specs/PRODUCT_SPEC.md) |
| **Commands** | [bin/README.md](../bin/README.md) |
| **Coding Standards** | [guidelines/CODING.md](guidelines/CODING.md) |
| **Operations** | [operations/OPERATIONS.md](operations/OPERATIONS.md) |
| **API Documentation** | [api/README.md](api/README.md) |
| **Deployment Guide** | [guides/DEPLOYMENT.md](guides/DEPLOYMENT.md) |
| **At-Rest Encryption Audit** | [operations/AT_REST_ENCRYPTION_AUDIT_2026-04-16.md](operations/AT_REST_ENCRYPTION_AUDIT_2026-04-16.md) |
| **Critical Journey E2E Evidence** | [operations/CRITICAL_JOURNEY_E2E_EVIDENCE_2026-04-16.md](operations/CRITICAL_JOURNEY_E2E_EVIDENCE_2026-04-16.md) |
| **GDPR Deletion Flow Runbook** | [operations/GDPR_DELETION_FLOW_RUNBOOK.md](operations/GDPR_DELETION_FLOW_RUNBOOK.md) |
| **Critical Journey Environment Evidence** | [operations/CRITICAL_JOURNEY_EVIDENCE_LOCAL_2026-04-16.md](operations/CRITICAL_JOURNEY_EVIDENCE_LOCAL_2026-04-16.md) |
| **GDPR Environment Evidence** | [operations/GDPR_DELETION_EVIDENCE_LOCAL_2026-04-16.md](operations/GDPR_DELETION_EVIDENCE_LOCAL_2026-04-16.md) |
| **Encryption Environment Evidence** | [operations/ENCRYPTION_EVIDENCE_LOCAL_2026-04-16.md](operations/ENCRYPTION_EVIDENCE_LOCAL_2026-04-16.md) |
| **Proof Status Matrix** | [operations/REMEDIATION_PROOF_STATUS_MATRIX_2026-04-16.md](operations/REMEDIATION_PROOF_STATUS_MATRIX_2026-04-16.md) |
| **Route Validation Matrix** | [operations/ROUTE_VALIDATION_ROLLOUT_MATRIX_2026-04-16.md](operations/ROUTE_VALIDATION_ROLLOUT_MATRIX_2026-04-16.md) |
| **LTI Validation Evidence** | [operations/LTI_VALIDATION_EVIDENCE_2026-04-16.md](operations/LTI_VALIDATION_EVIDENCE_2026-04-16.md) |
| **LTI Phase 2 Validation Evidence** | [operations/LTI_PHASE2_ROUTE_VALIDATION_EVIDENCE_2026-04-16.md](operations/LTI_PHASE2_ROUTE_VALIDATION_EVIDENCE_2026-04-16.md) |
| **LTI Phase 2 Proof Matrix** | [operations/LTI_PHASE2_PROOF_STATUS_MATRIX_2026-04-16.md](operations/LTI_PHASE2_PROOF_STATUS_MATRIX_2026-04-16.md) |
| **Social Validation Evidence** | [operations/SOCIAL_VALIDATION_EVIDENCE_2026-04-16.md](operations/SOCIAL_VALIDATION_EVIDENCE_2026-04-16.md) |
| **Social Proof Matrix** | [operations/SOCIAL_PROOF_STATUS_MATRIX_2026-04-16.md](operations/SOCIAL_PROOF_STATUS_MATRIX_2026-04-16.md) |
| **Content Validation Evidence** | [operations/CONTENT_ROUTE_VALIDATION_EVIDENCE_2026-04-16.md](operations/CONTENT_ROUTE_VALIDATION_EVIDENCE_2026-04-16.md) |
| **Content Proof Matrix** | [operations/CONTENT_PROOF_STATUS_MATRIX_2026-04-16.md](operations/CONTENT_PROOF_STATUS_MATRIX_2026-04-16.md) |
| **Content Phase 2 Validation Evidence** | [operations/CONTENT_PHASE2_ROUTE_VALIDATION_EVIDENCE_2026-04-16.md](operations/CONTENT_PHASE2_ROUTE_VALIDATION_EVIDENCE_2026-04-16.md) |
| **Content Phase 2 Proof Matrix** | [operations/CONTENT_PHASE2_PROOF_STATUS_MATRIX_2026-04-16.md](operations/CONTENT_PHASE2_PROOF_STATUS_MATRIX_2026-04-16.md) |
| **Route Validation Batch 3 Evidence** | [operations/ROUTE_VALIDATION_BATCH3_EVIDENCE_2026-04-16.md](operations/ROUTE_VALIDATION_BATCH3_EVIDENCE_2026-04-16.md) |
| **Route Validation Batch 3 Matrix** | [operations/ROUTE_VALIDATION_BATCH3_STATUS_MATRIX_2026-04-16.md](operations/ROUTE_VALIDATION_BATCH3_STATUS_MATRIX_2026-04-16.md) |

# YAPPC Domain Model Registry

> **Last Updated:** 2025-01-19 | **Status:** ACTIVE
> **Purpose:** Single reference for all YAPPC domain models to prevent duplication and clarify ownership.

## Overview

YAPPC has two domain packages serving different bounded contexts:

| Location | Package | Context | Style | Count |
|---|---|---|---|---|
| `backend/api/domain/` | `com.ghatana.yappc.api.domain` | Project Management | ActiveJ POJOs | 25 |
| `libs/java/yappc-domain/` | `com.ghatana.products.yappc.domain` | Cloud Security | JPA + Lombok | 30 |

## Canonical Source Map

### Project Management Domain (`backend/api/domain/`)

Consumed by: ~70 files (repositories, services, controllers, GraphQL resolvers)

| Entity | Sub-Type | Consumers | Canonical? |
|---|---|---|---|
| `AISuggestion` | Entity | 6 | ✅ |
| `AgentCapabilities` | ValueObject | 4 | ✅ |
| `AgentRegistryEntry` | Entity | 4 | ✅ |
| `Alert` | Entity | 5 | ⚠️ Overlaps `SecurityAlert` in yappc-domain |
| `BootstrappingSession` | Entity | 3 | ✅ |
| `Channel` | Entity | 3 | ✅ |
| `CodeReview` | Entity | 5 | ✅ |
| `Compliance` | Entity | 5 | ⚠️ Overlaps `ComplianceAssessment` in yappc-domain |
| `Incident` | Entity | 4 | ⚠️ Overlaps `Incident` in yappc-domain |
| `LifecycleConfig` | Config | 2 | ✅ |
| `LogEntry` | Entity | 5 | ✅ |
| `Metric` | Entity | 5 | ✅ |
| `Notification` | Entity | 4 | ✅ |
| `Persona` | ValueObject | 2 | ✅ |
| `Project` | Entity | 4 | ⚠️ Overlaps `Project` in yappc-domain |
| `Requirement` | Entity | 5 | ✅ |
| `SecurityScan` | Entity | 5 | ⚠️ Overlaps `ScanJob`/`ScanFinding` in yappc-domain |
| `Sprint` | Entity | 4 | ✅ |
| `Story` | Entity | 5 | ✅ |
| `TaskDomain` | ValueObject | 4 | ✅ |
| `Team` | Entity | 4 | ✅ |
| `Trace` | Entity | 5 | ✅ |
| `Vulnerability` | Entity | 5 | ✅ |
| `Workflow` | ValueObject | 2 | ✅ |
| `Workspace` | Entity | 6 | ✅ |

### Cloud Security Domain (`libs/java/yappc-domain/`)

Consumed by: infrastructure/datacloud, infrastructure/persistence, core/domain, platform

| Entity | Sub-Type | Canonical? |
|---|---|---|
| `CloudAccount` | Entity | ✅ |
| `CloudCost` | Entity | ✅ |
| `CloudResource` | Entity | ✅ |
| `ComplianceAssessment` | Entity | ✅ (canonical for compliance) |
| `ComplianceFramework` | Entity | ✅ |
| `Dashboard` | Entity | ✅ |
| `Dependency` | Entity | ✅ |
| `Incident` | Entity | ✅ (canonical for incidents) |
| `Project` | Entity | ✅ (canonical for projects) |
| `ScanFinding` | Entity | ✅ |
| `ScanJob` | Entity | ✅ |
| `SecurityAlert` | Entity | ✅ |

## Overlapping Entities — Migration Plan

These entities exist in BOTH packages and must converge:

| API Domain Class | yappc-domain Class | Resolution |
|---|---|---|
| `api.domain.Incident` | `domain.model.Incident` | **yappc-domain is canonical.** API version deprecated. |
| `api.domain.Project` | `domain.model.Project` | **yappc-domain is canonical.** API version deprecated. |
| `api.domain.Compliance` | `domain.model.ComplianceAssessment` | **yappc-domain is canonical.** Different names → create mapper. |
| `api.domain.SecurityScan` | `domain.model.ScanJob` + `ScanFinding` | **yappc-domain is canonical.** API version is a flattened view. |
| `api.domain.Alert` | `domain.model.SecurityAlert` | **yappc-domain is canonical.** Different names → create mapper. |

### Migration Steps (per overlapping entity)

1. Add `@Deprecated` + JavaDoc `@see` to API domain class pointing to yappc-domain canonical
2. Create a mapper interface in `backend/api/mapper/` for the transition period
3. Update services to use the mapper, gradually migrating to canonical types
4. Once all consumers migrated, delete the deprecated API domain class

### Non-Overlapping Entities

Entities unique to `backend/api/domain/` (Story, Sprint, Requirement, etc.) represent the
**project management** bounded context and have no equivalent in yappc-domain. These should
eventually move to a `yappc-project-domain` lib if needed by other modules.

## Rules

1. **New domain models** MUST go in `libs/java/yappc-domain/` (or a new sub-domain lib)
2. **Cross-module sharing** MUST use the lib, never import from `backend/api/domain/` directly
3. **Overlapping entities** in `backend/api/domain/` are DEPRECATED — use yappc-domain canonical
4. A domain model is **canonical** when it lives in a `libs/` module

---

## Persistence Stack Ownership (F-Y008 / K-Y8)

YAPPC uses **two distinct persistence stacks**. Each entity is owned by exactly one stack.
Cross-stack access is forbidden — services must go through the owning module's API.

### Stack A — Java JDBC (ActiveJ + platform:java:database)

Owns all **core workflow and AI domain state** in `yappc-domain-impl`.

| Entity / Table | Module | Technology |
|---|---|---|
| `AiWorkflow` | `yappc-domain-impl` | Java JDBC, `ai_workflows` table |
| `AiWorkflowStep` | `yappc-domain-impl` | Java JDBC, `ai_workflow_steps` table |
| `AiPlan` | `yappc-domain-impl` | Java JDBC, `ai_plans` table |
| `AiPlanStep` | `yappc-domain-impl` | Java JDBC, `ai_plan_steps` table |
| `AiExecutionLog` | `yappc-domain-impl` | Java JDBC, `ai_execution_logs` table |
| Workflow audit trail | `yappc-domain-impl` | Java JDBC — append-only log rows |

**Owner**: `products/yappc/core/yappc-domain-impl`
**Technology**: `platform:java:database` (ActiveJ Promises, no blocking JDBC on event loop)
**Connection**: configured via `DATABASE_URL` env var, tenant-scoped by `tenantId` column

### Stack B — Node + Prisma (GraphQL API layer)

Owns all **project management and collaboration domain state** in `yappc-api`.

| Entity / Table | Module | Technology |
|---|---|---|
| `Project` | `yappc-api` | Prisma + PostgreSQL, `projects` table |
| `Incident` | `yappc-api` | Prisma + PostgreSQL, `incidents` table |
| `Story` | `yappc-api` | Prisma + PostgreSQL, `stories` table |
| `Sprint` | `yappc-api` | Prisma + PostgreSQL, `sprints` table |
| `Requirement` | `yappc-api` | Prisma + PostgreSQL, `requirements` table |
| `ComplianceAssessment` | `yappc-api` | Prisma + PostgreSQL, `compliance_assessments` table |
| `ScanJob` / `ScanFinding` | `yappc-api` | Prisma + PostgreSQL, `scan_jobs` / `scan_findings` |
| `SecurityAlert` | `yappc-api` | Prisma + PostgreSQL, `security_alerts` table |
| `Dashboard` | `yappc-api` | Prisma + PostgreSQL, `dashboards` table |
| `Dependency` | `yappc-api` | Prisma + PostgreSQL, `dependencies` table |

**Owner**: `products/yappc/backend/api` (Node.js, GraphQL, `@prisma/client`)
**Technology**: Prisma ORM, type-safe schema in `products/yappc/backend/api/prisma/schema.prisma`
**Connection**: configured via `DATABASE_URL` env var, tenant-isolated via Prisma middleware

### Cross-Stack Access Rules

| Rule | Rationale |
|---|---|
| Java code MUST NOT import `@prisma/client` or call the GraphQL API directly | Stack boundary — use async events or REST if needed |
| Node code MUST NOT call `yappc-domain-impl` JDBC tables directly | Stack boundary — use the Java HTTP API |
| Shared entities (Project, Incident) that appear in both stacks are MAPPED at the API boundary | See Overlapping Entities section above |
| New entities serving workflow/AI goals go in Stack A | AI execution is Java-native (ActiveJ Promise model) |
| New entities serving project/collab goals go in Stack B | GraphQL API layer owns project-management context |

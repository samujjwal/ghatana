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

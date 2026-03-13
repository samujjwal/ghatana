# Domain Model Split-Package Resolution Plan

> **Status**: ✅ COMPLETE
> **Created**: Phase 1 of YAPPC refactor
> **Resolved**: Session 6 (March 2026)
> **Blocking**: None

## Problem

`core:domain` and `libs:java:yappc-domain` both define classes in package
`com.ghatana.products.yappc.domain.model`. This is a split-package / classpath
collision that manifests in 3 modules:

- `infrastructure:datacloud` (depends on both directly)
- `core:domain` itself (re-exports `libs:java:yappc-domain` via `api()`)
- `core:domain:service` (transitive via `core:domain`)

## Completed

4 duplicate classes with **no external consumers** deleted from `core:domain`:

- [x] `CloudAccount` → canonical in `libs:java:yappc-domain`
- [x] `CloudResource` → canonical in `libs:java:yappc-domain`
- [x] `ComplianceFramework` → canonical in `libs:java:yappc-domain`
- [x] `Dependency` → canonical in `libs:java:yappc-domain`

## Remaining (6 classes with external consumers)

These classes exist in BOTH modules with **different field sets**. External code
(mostly `backend/api`) references them. The `libs:java:yappc-domain` versions
must gain the missing fields before `core:domain` copies can be deleted.

| Class | External Consumers | L2-only fields needing migration |
|---|---|---|
| **ComplianceAssessment** | `backend/api` (5 files) | `projectId`, `assessmentDate`, `dueDate`, `complianceScore`, `assessorName`, `assessmentType`, `controlResults`, `notes` |
| **Dashboard** | `infrastructure/datacloud` (2), `infrastructure/persistence` (1) | `key`, `title`, `persona`, `config`, `filters`, `createdById` |
| **Incident** | `backend/api` (5 files) | `projectId`, `closedAt`, `ownerId`, `tags` |
| **Project** | `backend/api` (3 files) | `key`, `archivedAt` |
| **ScanJob** | `backend/api` (5 files) | `scannerName`, `scannerVersion`, `target`, `infoCount` |
| **SecurityAlert** | `backend/api` (5 files) | `projectId`, `incidentId`, `ruleId`, `ruleName`, `detectedAt`, `assignedTo`, `metadata`, `affectedResources` |

## Resolution Steps (per class)

1. Add missing fields from `core:domain` version to `libs:java:yappc-domain` version
2. Update V2 Flyway migration to include new columns
3. Verify all external consumers compile against `libs:java:yappc-domain` version
4. Delete `core:domain` copy
5. Remove any duplicate repository interfaces

# Canonical Documentation Index

**Status:** Canonical
**Owner:** Data Cloud Team
**Last reviewed:** 2026-05-10
**Supersedes:** N/A
**Superseded by:** N/A

This document provides the canonical index of all Data Cloud documentation. Each document listed below is the single source of truth for its topic.

## Canonical Documentation Matrix

| Topic | Document | Status | Owner | Location |
|-------|----------|--------|-------|----------|
| Vision / market positioning | Data Cloud Vision | Canonical | Product Team | `docs/product/01_data_cloud_vision.md` |
| Plane architecture | Plane Architecture | Canonical | Architecture Team | `docs/architecture/PLANE_ARCHITECTURE.md` |
| Detailed architecture | Data Cloud Detailed Architecture | Canonical | Architecture Team | `docs/product/02_data_cloud_unified_detailed_architecture.md` |
| High-level design | Data Cloud High-Level Design | Canonical | Architecture Team | `docs/product/03_data_cloud_unified_high_level_design.md` |
| API/contracts | OpenAPI Specifications | Canonical | API Team | `contracts/openapi/data-cloud.yaml` |
| Connector strategy | Connector Strategy | Draft | Data Team | `docs/data-cloud/connector-strategy.md` |
| Ingestion strategy | Data Ingestion Strategy | Draft | Data Team | `docs/data-cloud/ingestion-strategy.md` |
| Retrieval/search/indexing strategy | Retrieval Strategy | Draft | Data Team | `docs/data-cloud/retrieval-strategy.md` |
| AI/automation strategy | AI/Automation Strategy | Draft | AI Team | `docs/data-cloud/ai-automation-strategy.md` |
| Governance/provenance/security | Governance Strategy | Canonical | Security Team | `docs/data-cloud/governance-strategy.md` |
| Operations/runbook | Operations Runbook | Draft | Ops Team | `docs/operations/runbook.md` |
| Testing strategy | Testing Strategy | Draft | QA Team | `docs/testing/testing-strategy.md` |
| Shared-library boundary guide | Shared Library Boundaries | Canonical | Platform Team | `docs/architecture/SHARED_LIBRARY_BOUNDARIES.md` |
| UI/design-system guide | UI/Design System Guide | Draft | UX Team | `docs/ui/design-system-guide.md` |

## Documentation Status Legend

- **Canonical:** Single source of truth for this topic
- **Draft:** Work in progress, not yet canonical
- **Archived:** Historical reference, not for current use

## Documentation Requirements

All canonical documentation must include:

- Status: Canonical / Draft / Archived
- Owner: Team or individual responsible
- Last reviewed: Date of last review
- Supersedes: Previous document(s) this replaces (if any)
- Superseded by: New document that replaces this (if any)

## Documentation Truth Enforcement

CI check: `scripts/check-documentation-truth.mjs`

The CI check will fail if:
- Canonical docs are missing from this index
- Archived docs are referenced as canonical
- Duplicate canonical docs exist for the same topic

## Archived Documentation

Archived documentation is located in `docs/archive/` and should include the banner:

```
# ARCHIVED - NOT CANONICAL

**Archived. Not canonical. Do not use for current audits.**
```

Archived docs are excluded from documentation truth checks.

# Data Cloud Documentation Index

**Document ID:** DC-INDEX-001  
**Version:** 3.0  
**Date:** 2026-04-13  
**Evidence Base:** current visible `products/data-cloud/docs-generated/` tree

---

## Executive Summary

This index is the navigation hub for the current Data Cloud generated documentation set.

### Current Inventory Snapshot

| Category                                    | Count |
| ------------------------------------------- | ----- |
| Visible artifacts in `docs-generated`       | 26    |
| Source artifacts excluding the audit report | 25    |
| Vision and strategy documents               | 7     |
| Architecture and design documents           | 2     |
| Testing and quality documents               | 1     |
| Technical and caveat documents              | 3     |
| Usage and API documents                     | 5     |
| Index, risk, and governance documents       | 4     |
| ADR documents                               | 2     |
| Root metadata documents                     | 2     |

### Important Usage Note

The strongest part of this documentation set is implementation-facing material. The strategic documents added on April 13 provide working product direction, but some operational readiness claims elsewhere in the suite still require reconciliation and proof. For current readiness status, use:

- `DATA_CLOUD_DOCUMENTATION_AUDIT_REPORT.md`
- `06-index-traceability-risk/07-readiness-scorecard.md`

---

## 1. Root Documents

| Document                                   | Purpose                                                                                  |
| ------------------------------------------ | ---------------------------------------------------------------------------------------- |
| `DATA_CLOUD_DOCUMENTATION_AUDIT_REPORT.md` | Evidence-based audit of technical quality, strategic gaps, and documentation consistency |
| `OWNER.md`                                 | Ownership, governance, and maintenance expectations for this documentation area          |

## 2. Vision, Plan, and Requirements

| Document                                                    | Purpose                                                                  |
| ----------------------------------------------------------- | ------------------------------------------------------------------------ |
| `01-vision-plan-requirements/01-product-vision.md`          | Product identity, platform vision, personas, scope, and maturity framing |
| `01-vision-plan-requirements/02-capability-map.md`          | Capability inventory across 8 areas and 32 major capabilities            |
| `01-vision-plan-requirements/03-requirements.md`            | Requirements traceability across functional and non-functional areas     |
| `01-vision-plan-requirements/04-icp-and-jtbd.md`            | Primary ICP recommendation, buyer/user framing, and prioritized JTBD     |
| `01-vision-plan-requirements/05-competitive-positioning.md` | Competitive framing, reasons to win/lose, and messaging guidance         |
| `01-vision-plan-requirements/06-packaging-and-pricing.md`   | Proposed packaging tiers, pricing logic, and metering dimensions         |
| `01-vision-plan-requirements/07-success-metrics.md`         | North-star metric, leading indicators, lagging indicators, and KPI model |

## 3. Architecture, Decisions, and Design

| Document                                                                      | Purpose                                                               |
| ----------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `02-architecture-decisions-design/01-system-architecture.md`                  | High-level system architecture, runtime topology, and component model |
| `02-architecture-decisions-design/02-architecture-decisions-comprehensive.md` | Consolidated architecture decisions and ADR-derived rationale         |

## 4. Testing and Quality

| Document                                                         | Purpose                                                                |
| ---------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `03-test-inventory-and-expectations/01-master-test-inventory.md` | Test inventory, coverage analysis, quality assessment, and gap summary |

## 5. Technical Documentation and Caveats

| Document                                                             | Purpose                                                                |
| -------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `04-technical-docs-stack-caveats-guidance/01-technical-overview.md`  | Technology stack, runtime choices, and infrastructure overview         |
| `04-technical-docs-stack-caveats-guidance/02-scaling-guide.md`       | Capacity planning, scaling guidance, and infrastructure considerations |
| `04-technical-docs-stack-caveats-guidance/03-engineering-caveats.md` | Performance, security, isolation, and operational caveats              |

## 6. Usage Manuals and API Docs

| Document                                                        | Purpose                                                           |
| --------------------------------------------------------------- | ----------------------------------------------------------------- |
| `05-usage-manuals-and-api-docs/01-disaster-recovery-runbook.md` | Recovery procedures and disaster-response guidance                |
| `05-usage-manuals-and-api-docs/02-first-workload-recipes.md`    | Concrete first-adoption workload patterns for platform onboarding |
| `05-usage-manuals-and-api-docs/03-remediation-summary.md`       | Improvement and remediation tracking                              |
| `05-usage-manuals-and-api-docs/04-api-reference.md`             | Human-readable API reference for the documented REST surface      |
| `05-usage-manuals-and-api-docs/openapi.yaml`                    | Canonical machine-readable API contract                           |

## 7. Index, Traceability, and Risk

| Document                                                    | Purpose                                                                      |
| ----------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `06-index-traceability-risk/01-document-index.md`           | Current navigation hub for the docs-generated tree                           |
| `06-index-traceability-risk/03-gap-and-risk-summary.md`     | Risk catalog and mitigation planning                                         |
| `06-index-traceability-risk/06-documentation-change-log.md` | Historical log of documentation refresh work, with reconciliation note       |
| `06-index-traceability-risk/07-readiness-scorecard.md`      | Domain-by-domain readiness view separating validated and aspirational claims |

## 8. ADR Materials

| Document                                                   | Purpose                                                         |
| ---------------------------------------------------------- | --------------------------------------------------------------- |
| `07-architecture-decisions/00-adr-index.md`                | ADR registry for Data Cloud and platform decisions affecting it |
| `07-architecture-decisions/adr-dc-001-module-ownership.md` | Module ownership matrix and dependency boundary rules           |

---

## 9. Recommended Reading Paths

### For Executives and Product Leads

1. `DATA_CLOUD_DOCUMENTATION_AUDIT_REPORT.md`
2. `01-vision-plan-requirements/01-product-vision.md`
3. `01-vision-plan-requirements/04-icp-and-jtbd.md`
4. `01-vision-plan-requirements/05-competitive-positioning.md`
5. `06-index-traceability-risk/07-readiness-scorecard.md`

### For Architects and Platform Leads

1. `02-architecture-decisions-design/01-system-architecture.md`
2. `02-architecture-decisions-design/02-architecture-decisions-comprehensive.md`
3. `07-architecture-decisions/00-adr-index.md`
4. `07-architecture-decisions/adr-dc-001-module-ownership.md`
5. `04-technical-docs-stack-caveats-guidance/03-engineering-caveats.md`

### For Engineering Teams

1. `01-vision-plan-requirements/03-requirements.md`
2. `01-vision-plan-requirements/02-capability-map.md`
3. `05-usage-manuals-and-api-docs/04-api-reference.md`
4. `03-test-inventory-and-expectations/01-master-test-inventory.md`
5. `06-index-traceability-risk/07-readiness-scorecard.md`

### For Product Strategy Work

1. `01-vision-plan-requirements/04-icp-and-jtbd.md`
2. `01-vision-plan-requirements/05-competitive-positioning.md`
3. `01-vision-plan-requirements/06-packaging-and-pricing.md`
4. `01-vision-plan-requirements/07-success-metrics.md`

---

## 10. Maintenance Rules

1. Update this index whenever a document is added, removed, or renamed.
2. Keep all count summaries aligned with the visible tree, not historical drafts.
3. Treat the audit report and readiness scorecard as the current authority for maturity and trust-language reconciliation.
4. Do not add references to documents that are not present in the tree.

---

**Last reconciled:** April 13, 2026

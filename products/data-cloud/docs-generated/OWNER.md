# Owner: Data Cloud Documentation

**Team:** Data-Cloud Platform Team  
**Slack:** #platform-data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-04-13

---

## Purpose

This directory contains the generated and reconciled documentation set for the Data Cloud product. It includes architecture, requirements, API, risk, strategy, and readiness artifacts.

---

## Canonical Documents

| Document                                                   | Role                                                                          |
| ---------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `05-usage-manuals-and-api-docs/openapi.yaml`               | Canonical API contract                                                        |
| `07-architecture-decisions/adr-dc-001-module-ownership.md` | Canonical module ownership and dependency rule document                       |
| `DATA_CLOUD_DOCUMENTATION_AUDIT_REPORT.md`                 | Canonical audit of documentation quality, strategic gaps, and inconsistencies |
| `06-index-traceability-risk/07-readiness-scorecard.md`     | Canonical domain-by-domain readiness summary                                  |
| `06-index-traceability-risk/01-document-index.md`          | Canonical navigation map for the current docs set                             |

---

## Stewardship Expectations

1. Keep metric claims aligned across the audit, readiness scorecard, index, capability map, and requirements documents.
2. Do not introduce references to files that are not present in the tree.
3. Treat strategic documents as versioned source-of-truth artifacts, not one-off planning notes.
4. Reconcile any readiness, security, or tenant-isolation language before making stronger maturity claims.

---

## Review Triggers

Review this directory after any of the following:

- API surface changes
- capability additions or removals
- major architecture or module-boundary changes
- security or isolation model changes
- material test coverage changes
- packaging, pricing, or GTM strategy changes

---

## Governance Notes

- `openapi.yaml` changes require Data-Cloud Platform Team review.
- Any new ADR must be created in `07-architecture-decisions/` using the existing naming pattern.
- Strategy documents in `01-vision-plan-requirements/` should be reviewed by product and platform leadership together.
- Runbooks and readiness artifacts should be updated whenever validated operational evidence changes.

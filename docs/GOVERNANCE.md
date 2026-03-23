# Monorepo Governance

> **Owner:** Platform Team | **Status:** Active | **Effective:** 2026-03-22

## Governance Model

The Ghatana monorepo uses a tiered governance model:

1. **Monorepo Level** - Cross-cutting decisions affecting multiple products
2. **Product Level** - Product-specific architectural and design decisions
3. **Package Level** - Implementation details within clear boundaries

## Decision-Making Authority

| Scope | Decision Maker | Documentation |
|-------|---------------|---------------|
| Build system changes | Platform Team | ADR in `docs/adr/` |
| New product admission | Architecture Board | `docs/MODULE_ADMISSION_CHECKLIST.md` |
| Product boundaries | Product Owner + Platform Team | Product `ARCHITECTURE.md` |
| Package API changes | Package Owner | Package `README.md` |
| Documentation structure | Platform Team | This document |

## Standards and Conventions

### Required
- [Naming Conventions](./NAMING_CONVENTIONS.md)
- [API Usability Guidelines](./API_USABILITY_GUIDELINES.md)
- [Deprecation Policy](./DEPRECATION_POLICY.md)

### Enforced via CI
- TypeScript strict mode
- No new `ghatana-new` references
- Broken link checks
- Duplicate tracker detection

## Documentation Governance

Every live document must declare:
- **Owner:** Who maintains this document
- **Status:** Active, Draft, or Superseded
- **Scope:** What this document covers
- **Audience:** Who should read this
- **Authority Level:** Binding guidance or informational
- **Supersedes/Superseded-by:** Document lineage
- **Last Reviewed:** Date of last review

### Archive Rules

- Old audits → `docs/archive/audits/`
- Completed plans → `docs/archive/plans/`
- Session summaries → `docs/archive/sessions/`
- Historical ADRs → Keep in `docs/adr/` with status

## Quarterly Boundary Audit

Required every quarter:
- [Quarterly Boundary Audit Checklist](./QUARTERLY_BOUNDARY_AUDIT_CHECKLIST.md)

Results feed into:
- [STATUS.md](./STATUS.md) - Current state
- [ROADMAP.md](./ROADMAP.md) - Remediation priorities

## Change Freeze Rules

See [GOVERNANCE_FREEZE_RULES.md](./GOVERNANCE_FREEZE_RULES.md) for:
- Release freeze periods
- Emergency change procedures
- Hotfix protocols

## Related Documents

- [MONOREPO_VISION.md](./MONOREPO_VISION.md)
- [MONOREPO_ARCHITECTURE.md](./MONOREPO_ARCHITECTURE.md)
- [ROADMAP.md](./ROADMAP.md)
- [STATUS.md](./STATUS.md)

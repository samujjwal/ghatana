# Ghatana Monorepo Documentation

> **Owner:** Platform Team | **Last Updated:** 2026-04-14

---

## Core Documents

| Document | Purpose |
|----------|---------|
| [MONOREPO_VISION.md](./MONOREPO_VISION.md) | Vision, principles, roadmap |
| [MONOREPO_ARCHITECTURE.md](./MONOREPO_ARCHITECTURE.md) | Repo structure, platform modules, cross-product integration |
| [GOVERNANCE.md](./GOVERNANCE.md) | Naming conventions, freeze rules, deprecation policy, library ownership |
| [TESTING.md](./TESTING.md) | Test taxonomy, standards, CI quality gates |
| [BUILD.md](./BUILD.md) | Build system, Gradle conventions, AEP conventions, profiler |
| [ONBOARDING.md](./ONBOARDING.md) | Developer setup and first-build guide |
| [audit-report-2026-04-22.md](./audit-report-2026-04-22.md) | Baseline audit findings and remediation IDs |
| [AUDIT_REMEDIATION_IMPLEMENTATION_PLAN_2026-04-22.md](./AUDIT_REMEDIATION_IMPLEMENTATION_PLAN_2026-04-22.md) | Execution roadmap for remediation phases |
| [AUDIT_REMEDIATION_TODO_LIST_2026-04-22.md](./AUDIT_REMEDIATION_TODO_LIST_2026-04-22.md) | Remediation tracker and status log |
| [AUDIT_REMEDIATION_CLOSURE_EVIDENCE_2026-04-22.md](./AUDIT_REMEDIATION_CLOSURE_EVIDENCE_2026-04-22.md) | Verification commands, outcomes, and closure artifacts |

## Specialized Areas

| Area | Location |
|------|----------|
| Architecture Decision Records | [adr/](./adr/) |
| Agent System | [agent-system/](./agent-system/) |
| Platform TypeScript Libraries | [platform-libraries/LIBRARY_INDEX.md](./platform-libraries/LIBRARY_INDEX.md) |
| Platform Architecture Plans | [architecture/](./architecture/) |
| Kernel + Plugin Multi-Product Plan | [architecture/KERNEL_PLUGIN_MULTI_PRODUCT_EXECUTION_PLAN_2026-04-16.md](./architecture/KERNEL_PLUGIN_MULTI_PRODUCT_EXECUTION_PLAN_2026-04-16.md) |
| Kernel + Plugin Remaining Items Tracker | [architecture/KERNEL_PLUGIN_REMAINING_ITEMS_TRACKER_2026-04-16.md](./architecture/KERNEL_PLUGIN_REMAINING_ITEMS_TRACKER_2026-04-16.md) |
| Quarterly Process | [process/QUARTERLY_BOUNDARY_REVIEW.md](./process/QUARTERLY_BOUNDARY_REVIEW.md) |
| Swagger UI setup | [SWAGGER_UI_README.md](./SWAGGER_UI_README.md) |

## Products

| Product | Docs |
|---------|------|
| AEP | `products/data-cloud/docs/aep/docs/` |
| Data Cloud | `products/data-cloud/docs/` |
| DCMAAR | `products/dcmaar/docs/` |
| Flashit | `products/flashit/docs/` |
| PHR (Nepal) | `products/phr/docs/` |
| Software Org | `products/software-org/docs/` |
| TutorPutor | `products/tutorputor/docs/` |
| YAPPC | `products/yappc/docs/` |

## Scripts Reference

Key scripts in `scripts/` — all are executable and documented with comments at the top.

| Script | Purpose |
|--------|---------|
| `create-module.sh` | Scaffold a new platform / product / shared module |
| `test-tiered.sh` | Run tests by tier (unit → integration → contract → e2e) |
| `coverage-report.sh` | Print per-module Java coverage with HTML links |
| `scan-test-classifications.sh` | Find potentially mislabeled tests |
| `security-audit.sh` | Check for hardcoded secrets and security anti-patterns |
| `run-quarterly-audit.sh` | Full quarterly boundary audit (writes to `docs/audits/`) |
| `generate-dependency-graph.sh` | Generate Mermaid / DOT dependency graph |
| `align-dependencies.js` | Align pnpm workspace dependency versions |
| `analyze-dependency-convergence.js` | Report version misalignments |
| `deprecation-cleanup.sh` | Scan for deprecated API usage |
| `link-tutorputor-workspace.sh` | Fix pnpm workspace symlinks for TutorPutor |
| `scripts/deployment/` | Build, deploy, canary, health, e2e pipeline |
| `scripts/database/` | Database initialization SQL |
| `scripts/testing/verify-shared-infrastructure.sh` | Verify Docker services before integration tests |


# Ghatana Vision & Roadmap

> **Owner:** Platform Team | **Status:** Active | **Last Updated:** 2026-04-14

---

## Vision

Build a unified, scalable, and maintainable software ecosystem that enables rapid product development through shared platforms, consistent patterns, and clear governance.

## Principles

1. **One Monorepo, Many Products** — Shared infrastructure with product-specific implementations
2. **Platform-First Development** — Invest in reusable platform capabilities before duplicating
3. **Clear Boundaries** — Explicit contracts between platform and product layers; no leaking
4. **Continuous Consolidation** — Merge, archive, or remove duplicate and redundant code regularly
5. **Documentation Authority** — Single source of truth per scope

## Success Metrics

| Metric | Target |
|--------|--------|
| Duplicate implementations | 0 |
| Documentation coverage (active products) | 100% |
| Full monorepo build time | < 5 min |
| Time to onboard new developer | < 1 day |
| Platform library test coverage | ≥ 95% |

---

## Roadmap

### Q1–Q2 2026 (Current)

**In Progress:**
- Documentation consolidation and cleanup (this change)
- Boundary audit remediation
- Shared integration architecture rollout inside existing platform modules
- TutorPutor production readiness
- TypeScript version alignment across workspace

**Completed:**
- TutorPutor TypeScript error resolution (295 → 0 errors)
- CMSModuleEditorPage refactoring (764 → 8 files)
- Prisma CI migration fixes
- E2E smoke test infrastructure
- `@tutorputor/core` and `@tutorputor/simulation` build fixes
- All 12 TypeScript library consolidation sprints complete
- Build-logic migration complete (fallback removed, all modules migrated)
- Agent system modernization Phases 0–2 complete

### Q3–Q4 2026 (Planned)

- Product documentation consolidation (app-platform, dcmaar, yappc)
- Automated governance checks in CI
- Adapter contract and performance smoke checks in CI
- 95% test coverage on platform libraries
- Developer onboarding time < 1 day

### Backlog

- Data-cloud Lombok issues
- AI-integration LangChain4j restoration
- Circular dependency resolution (core service)
- Build time optimization
- Automated link checking

---

## Related Documents

- [ARCHITECTURE.md](./ARCHITECTURE.md) — Technical structure
- [GOVERNANCE.md](./GOVERNANCE.md) — Decision-making, naming, deprecation
- [ONBOARDING.md](./ONBOARDING.md) — Developer setup
- [BUILD.md](./BUILD.md) — Build system guide

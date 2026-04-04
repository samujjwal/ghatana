# Monorepo Roadmap

> **Owner:** Platform Team | **Status:** Active | **Last Updated:** 2026-04-04

## Current Quarter (Q1 2026)

### In Progress
- [ ] Documentation consolidation and cleanup
- [ ] Boundary audit remediation
- [ ] Shared integration architecture rollout inside existing platform modules
- [ ] TutorPutor production readiness
- [ ] TypeScript version alignment across workspace

### Completed
- [x] TutorPutor TypeScript error resolution (295 → 0 errors)
- [x] CMSModuleEditorPage refactoring (764 → 8 files)
- [x] Prisma CI migration fixes
- [x] E2E smoke test infrastructure
- [x] @tutorputor/core build fixes
- [x] @tutorputor/simulation build fixes

## Next Quarter (Q2 2026)

### Planned
- [ ] Product documentation consolidation (app-platform, dcmaar, yappc)
- [ ] Platform library documentation cleanup
- [ ] Automated governance checks in CI
- [ ] Adapter contract and performance smoke checks in CI
- [ ] Archive historical documentation
- [ ] Remove temporary and generated files

### Stretch Goals
- [ ] 95% test coverage on platform libraries
- [ ] Unified build system migration completion
- [ ] Developer onboarding time < 1 day

## Backlog

### Documentation
- Consolidate products/app-platform docs (267 files)
- Consolidate products/dcmaar docs (471 files)
- Consolidate products/yappc docs (408 files)
- Platform package docs cleanup

### Technical Debt
- Data-cloud Lombok issues
- AI-integration LangChain4j restoration
- Circular dependency resolution (core service)

### Infrastructure
- Build time optimization
- CI/CD pipeline enhancements
- Automated link checking

## Related Documents

- [STATUS.md](./STATUS.md) - Current status
- [GOVERNANCE.md](./GOVERNANCE.md) - Decision-making process
- [MONOREPO_VISION.md](./MONOREPO_VISION.md) - Strategic goals

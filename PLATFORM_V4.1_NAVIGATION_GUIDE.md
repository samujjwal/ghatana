# Ghatana Platform V4.1: Consolidated Execution Plan — Navigation Guide

**Quick Links to Plan Sections**

---

## For Different Roles

### Platform Engineering Lead (10-week CEO)
**Start here**: Part 1 (Context Integration) + Part 5 (Roadmap) + Part 9 (Weekly Checklist)

**Key Questions Answered**:
- How does this plan align with monorepo architecture? → Part 1
- What are the 4 phases and critical path? → Part 5
- How do I track progress weekly? → Part 9 + Part 10 (Metrics)
- What governance enforces the plan? → Part 4 + Part 7 (Governance & Verification)

**Decision Points**:
- Week 1: Approve stale file removal + orphan audit + documentation foundation
- Week 2: Phase 1 (consolidation) critical path gates
- Week 5: Phase 2 (testing) critical path gates
- Week 9: Phase 3 (documentation) review & approval
- Week 10: Phase 4 (governance) final sign-off

---

### Java Platform Team (Weeks 2–4, 105h consolidation)
**Start here**: Part 3 (Consolidated Consolidation Strategy) → Section "Java Duplicates (11 symbols)" → Part 5 Phase 1 Week 2–3

**Your Task**:
```
Week 2: Consolidate HealthStatus, Policy, Role (from 4→1 copies each)
Week 3: Consolidate ValidationError, AuditEvent, Feature, ApprovalRequest, ApprovalStatus, AgentInfo, AgentSpec, PluginLoader
Week 4: Parallel — ArchUnit tests for all consolidations
```

**Critical Decision**: Foundation Module Homes
- `platform/java/config` → Configuration foundation
- `platform/java/observability` → Telemetry foundation
- `platform/java/database` → SQL & query foundation (NOT generic DatabaseClient)
- `platform/java/distributed-cache` → Cache + coordination foundation
- `platform/java/connectors` → Messaging foundation
- `platform/java/testing` → Real-infra fixtures home
- `platform/java/core` → Core models (HealthStatus, ValidationError, Feature)
- `platform/java/security` → RBAC (Policy, Role)
- `platform/java/audit` → AuditEvent
- `platform/java/domain` → AgentInfo, AgentSpec
- `platform/java/tool-runtime` → ApprovalRequest, ApprovalStatus
- `platform/java/plugin` → PluginLoader

**Action Items**:
1. [ ] Create ArchUnit test for each symbol preventing duplication
2. [ ] Batch imports atomically (3–4 at a time)
3. [ ] CI validate 3 times per batch before merge
4. [ ] Update internal docs

**Success Metric**: Week 4 Friday → All 11 symbols consolidated, 0 copies detected

---

### TypeScript Platform Team (Week 4, 45h consolidation)
**Start here**: Part 3 → Section "TypeScript Duplicates (7 symbols)" → Part 5 Phase 1 Week 4

**Your Task**:
```
Week 4: Consolidate 7 symbols: accessibility.ts, client.ts, theme.ts, validation.ts, 
        CommandPalette.tsx, ErrorBoundary.tsx, List.tsx
```

**Foundation Homes**:
- `platform/typescript/platform-utils` → accessibility.ts
- `platform/typescript/api` → client.ts
- `platform/typescript/theme` → theme.ts
- `platform/typescript/tokens` → validation.ts
- `platform/typescript/design-system` → CommandPalette, ErrorBoundary, List

**Action Items**:
1. [ ] Delete duplicate files
2. [ ] Update all imports (batch atomic)
3. [ ] Add ESLint rule blocking duplicate patterns
4. [ ] Verify CI passes 3x
5. [ ] Merge Friday end-of-day

**Success Metric**: Week 4 Friday → All 7 symbols consolidated, ESLint rule active

---

### QA Lead (Weeks 5–8, 154h testing)
**Start here**: Part 4 (Real-Infrastructure Testing Strategy) → Part 5 Phase 2 (Weeks 5–8)

**Your Task**:
Implement 1,000+ new tests across:
1. **NO-GO modules** (4 modules, 156 tests): agent-catalog, agent-memory, canvas/flow-canvas, platform-shell
2. **Foundation modules** (contract tests): database, distributed-cache, connectors, testing
3. **CONDITIONAL enhancements**: contracts, accessibility-audit, agent-core

**Test Framework**:
- Real-infrastructure: In `platform/java/testing` create shared Testcontainers fixtures:
  - `SqlFixture` (PostgreSQL)
  - `RedisFixture` (Redis)
  - `KafkaFixture` (Kafka, partition isolation)
- Contract test base classes: `SqlContractTestBase`, `CacheContractTestBase`, `PublisherContractTestBase`
- Every shared adapter must have real-infra tests

**Weekly Targets**:
- Week 5: 156 NO-GO tests (96/48/32/36) → target: 156/156 passing
- Week 6: 60+ foundation contract tests (database + cache) → target: 60/60 passing
- Week 7: 60+ enhanced tests (contracts + accessibility-audit) → target: 60/60 passing
- Week 8: 120+ agent-core E2E + final → target: 1,000+ total passing, 90%+ coverage

**Success Metric**: Week 8 Friday → 1,000+ tests, 90%+ coverage, 0 NO-GO modules, all real-infra passing

---

### Documentation Lead (Week 9, 40h)
**Start here**: Part 6 (Documentation & API Clarification) + Module Documentation Template

**Your Task**: Create/review 47 module READMEs + API surface docs

**Template Sections** (mandatory):
1. **Purpose** (1 sentence)
2. **Architectural Tier** (Foundation / Feature / Product-Specific)
3. **Public API Surface**:
   - Business Ports (product-facing)
   - Capability Ports (infrastructure, proven need)
   - Native Escape Hatches (rare, approved)
4. **Module Boundaries** (owns / depends / does NOT)
5. **Extension Rules**
6. **Testing Strategy**
7. **Observability** (metrics, traces, health probes)

**Weekly Breakdown**:
- Monday-Tuesday: Foundation modules (8) + template finalization
- Wednesday-Thursday: Feature modules (24)
- Friday: Product modules (15) + peer review sweep

**Success Metric**: Week 9 Friday → 47/47 modules documented, all tiers explicit, ready for governance phase

---

### Architecture Lead (Week 10, 40h)
**Start here**: Part 4 + Part 7 (Governance & Verification) + Part 11 (Approved Extensions)

**Your Task**: Implement and validate governance enforcement

**Governance Code**:
1. **Gradle tasks** (`gradle/platform-boundary-check.gradle`):
   - `:platform:validateDuplicateSymbols` — fail if >1 copy
   - `:platform:validateBusinessPorts` — fail if vendor SDK imported in products
   - `:platform:validateCapabilityPorts` — fail without justification
   - `:platform:validateNativeEscapeHatches` — fail without approval
   - `:platform:validateThinAdapters` — fail if business logic in adapters
   - `:platform:validateArchunitTests` — fail if boundary tests missing
   - `:platform:validateCoverageGates` — fail if <90% coverage

2. **ESLint rules** (`eslint-rules/ghatana-architecture-rules.js`):
   - `no-duplicate-exports` — block all 7 TypeScript symbol re-creations
   - `no-direct-vendor-imports` — block JDBC/Kafka/Redis in product code
   - `no-any-in-ports` — enforce typed business ports
   - `foundation-modules-need-contract-tests` — require real-infra tests

3. **CI Workflow** (new: `github/workflows/platform-audit-validation.yml`):
   - Job 1: Duplicate validation
   - Job 2: Business port validation
   - Job 3: ESLint validation
   - Job 4: Coverage gates
   - Job 5: Full audit (aggregates above)

**Weekly Milestones**:
- Monday-Tuesday: Implement Gradle tasks, test locally
- Wednesday: ESLint rules, CI workflow
- Thursday: Full audit validation on all 47 modules
- Friday: Leadership review, compliance report, final sign-off

**Success Metric**: Week 10 Friday → All governance passing, all 47 modules PRODUCTION-GO, release tag applied

---

## For Module Owners (47 People)

**Your Module**: [Look up in Part 3: Consolidated Consolidation Strategy]

**Phase 1** (Weeks 2–4):
- If your module exports a **duplicate symbol**: ✅ Consolidation task started for you
- Action: Await PR from consolidation team, test locally, approve
- Timeline: Your symbol → canonical location, imports fix, your module uses canonical

**Phase 2** (Weeks 5–8):
- If your module is NO-GO: ✅ You get assigned new tests
- If your module is CONDITIONAL: ✅ Your coverage gets enhanced
- Action: Review new tests, ensure they match your domain

**Phase 3** (Week 9):
- You own documentation for your module
- Template provided, deadline Friday EOD
- Action: Draft README, submit for peer review, incorporate feedback

**Phase 4** (Week 10):
- Your module is validated by governance
- All rules must pass before sign-off
- Action: Fix any validation failures, ensure all tests pass CI

---

## Quick Decision Tree

**"What phase am I in?"**
1. If it's Week 1 → Use Part 9 Week 1 checklist
2. If it's Weeks 2–3 → Java consolidation (Part 5 Phase 1)
3. If it's Week 4 → TypeScript consolidation (Part 5 Phase 1)
4. If it's Weeks 5–8 → Test implementation (Part 5 Phase 2)
5. If it's Week 9 → Documentation (Part 5 Phase 3)
6. If it's Week 10 → Governance & sign-off (Part 5 Phase 4)

**"What do I need to do this week?"**
→ Go to Part 9 (Weekly Execution Checklist) + your team role above

**"What's the business port for [module]?"**
→ Go to Part 3 → Foundation Modules section → Find module → Look for "Business Ports"

**"Why can't I import Kafka directly?"**
→ Integration Platform Architecture (Part 1) + Part 4 (Thin Adapter Standard)

**"When is my module documented?"**
→ Week 9 (Part 6) — you own the documentation for your module

**"What does PRODUCTION-GO mean?"**
→ Part 6 Definition of Done — all criteria must be met

**"How do I know if I'm blocking the critical path?"**
→ Part 5 Phase → your task's deadline in that phase → if missed, you block the next phase

---

## Key Links

| Document | Purpose | Link |
|----------|---------|------|
| **Monorepo Architecture** | Overall structure | `/ghatana/docs/MONOREPO_ARCHITECTURE.md` |
| **Integration Platform Architecture** | Business/capability/native strategy | `/ghatana/docs/INTEGRATION_PLATFORM_ARCHITECTURE.md` |
| **This Plan** | Complete 10-week roadmap | `/ghatana/PLATFORM_V4.1_CONSOLIDATED_EXECUTION_PLAN.md` |
| **Audit Summary** | Module-by-module findings | `/ghatana/PLATFORM_V4.1_AUDIT_SUMMARY.md` |
| **M4 Completion** | Production sign-off rigor | `/ghatana/docs/m4-completion-production-signoff.md` |

---

## Escalation Path

**Blocker at any phase?**

1. **Consolidation blocker** (Week 2–4) → Escalate to Java/TypeScript Platform Lead
2. **Test blocker** (Week 5–8) → Escalate to QA Lead
3. **Documentation blocker** (Week 9) → Escalate to Documentation Lead
4. **Governance blocker** (Week 10) → Escalate to Architecture Lead
5. **Timeline blocker** → Escalate to Platform Engineering Lead → See Part 7 Fallback Strategies

All escalations: Async note in #ghatana-platform + meeting if P0

---

## Success = Everything in This Checklist

- [ ] Every platform module owns a clear business/capability port
- [ ] Zero duplicate symbols (25+ → 0)
- [ ] 1,000+ new tests, real-infrastructure validated
- [ ] All 47 modules documented with API surface clear
- [ ] Governance code prevents regressions
- [ ] All 47 modules PRODUCTION-GO signed off
- [ ] Plan complete by 2026-06-13

---

**Document Version**: 2.0  
**Last Updated**: 2026-04-04  
**Audience**: All engineers, architects, product leads  
**Refresh**: Weekly (after standup Fridays)

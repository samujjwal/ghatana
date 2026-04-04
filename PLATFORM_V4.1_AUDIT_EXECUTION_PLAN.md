# Platform V4.1 Audit Execution Plan
**Date**: 2026-04-04  
**Status**: Ready for Implementation  
**Scope**: 47 modules across Java & TypeScript platform  
**Maturity Target**: All modules CONDITIONAL-GO or better

---

## Executive Summary

Following the **M4 completion rigor** (98.9% test pass rate, zero P0/P1 blockers), this plan addresses **47 platform modules** with:

- **25+ duplicate symbols** across Java & TypeScript (must consolidate)
- **37 missing READMEs** (documentation gaps)
- **7 modules with zero automated tests** (NO-GO status)
- **25+ CONDITIONAL-GO modules** requiring P0/P1 closure
- High ownership ambiguity due to duplicate abstractions

**Goal**: Transform all modules to **CONDITIONAL-GO or better** status within **10 weeks**, following **Ghatana repo conventions** (reuse before create, boundaries explicit, no duplicate abstractions).

---

## Part 1: Overview of Audit Findings

### Status Breakdown
```
☑️  PRODUCTION GO:          0 modules
🟡 CONDITIONAL-GO-AFTER-P0/P1: 42 modules
❌ NO-GO:                    2 modules (agent-catalog, agent-memory)
```

### Top Issues by Category

#### Duplicate Symbols (25+ instances)
- **Java**: HealthStatus, Policy, Role, PluginLoader, ValidationError, AuditEvent, Feature, ApprovalRequest, ApprovalStatus, AgentInfo, AgentSpec (11 distinct symbols)
- **TypeScript**: accessibility.ts, client.ts, theme.ts, validation.ts, CommandPalette, ErrorBoundary, List (7 distinct symbols)

#### Missing Tests (7 modules)
- agent-catalog: 0 tests → need 48+
- agent-memory: 0 tests → need 32+
- canvas/flow-canvas: 0 tests → need 40+
- platform-shell: 0 tests → need 36+
- contracts: 23 tests → need 60+ (add integration)
- accessibility-audit: stale files + thin coverage
- agent-core: 37 tests → need 120+ (concurrency + E2E)

#### Missing Documentation (37 modules)
- No module README or API contract
- No clear boundary documentation
- No extension/integration rules

#### Stale Files (1 module)
- typescript/accessibility-audit: `.old.ts` and `.old.tsx` files

#### Orphan Directories (2)
- java/cache: Not in build manifest
- typescript/testing: Not in workspace

---

## Part 2: Detailed Consolidation Plan

### Phase 1A: Java Duplicate Consolidation

#### Canonical Location Strategy
**Rule**: For each duplicate, choose the **most foundational module** as canonical:
- `core` → Foundation platform library
- `domain` → Domain model library
- `security` → Authentication/authz
- `agent-core` → None (it reuses)
- `tool-runtime` → Tool-specific runtime
- `plugin` → Plugin-specific
- `audit` → Audit-specific

#### Duplicate #1: HealthStatus.java
```
Locations:
  1. java/agent-core/src/main/java/com/ghatana/agent/HealthStatus.java ❌ DELETE
  2. java/core/src/main/java/com/ghatana/platform/health/HealthStatus.java ✅ CANONICAL
  3. java/database/src/main/java/com/ghatana/core/database/health/HealthStatus.java ❌ DELETE
  4. java/domain/src/main/java/com/ghatana/platform/domain/agent/registry/HealthStatus.java ❌ DELETE

Action:
  - Keep: java/core/src/main/java/com/ghatana/platform/health/HealthStatus.java
  - Delete: 3 copies
  - Import migration: 4 modules + all consumers
  - ArchUnit test: Enforce single location
```

#### Duplicate #2: Policy.java
```
Locations:
  1. java/agent-core/src/main/java/com/ghatana/agent/framework/memory/Policy.java ❌ DELETE
  2. java/kernel/src/main/java/com/ghatana/kernel/security/Policy.java (alternate model) ⚠️ RENAME
  3. java/security/src/main/java/com/ghatana/platform/security/rbac/Policy.java ✅ CANONICAL

Action:
  - Keep: java/security/.../Policy.java
  - Rename kernel: KernelPolicy.java (domain-specific)
  - Delete: agent-core copy
  - ArchUnit: Prevent Policy.java re-creation in other modules
```

#### Duplicate #3: Role.java
```
Locations:
  1. java/domain/.../Role.java ✅ CANONICAL
  2. java/governance/.../Role.java ❌ DELETE (extends domain Role)
  3. java/security/.../Role.java ❌ DELETE (extends domain Role)

Action:
  - Keep: java/domain/.../Role.java
  - Delete: 2 copies
  - governance/security: Extend/adapt without duplicating
```

**Remaining Duplicates**: Same pattern for ValidationError, AuditEvent, Feature, ApprovalRequest, ApprovalStatus, AgentInfo, AgentSpec, PluginLoader

#### Java Consolidation Effort
```
Symbols to consolidate: 11
Modules affected: 16
Estimated effort: 60 hours
Timeline: 2 weeks (Weeks 2–3)
Blocker risks: Import migration must be atomic
```

### Phase 1B: TypeScript Duplicate Consolidation

#### Validation Strategy
**Rule**: Reuse existing packages before intermediate modules:
- `@ghatana/platform-utils` → Shared utilities
- `@ghatana/design-system` → Shared components
- `@ghatana/theme` → Theming
- `@ghatana/tokens` → Design tokens
- `canvas`, `api`, `realtime` → Product-specific (reuse from platform)

#### Duplicate #1: accessibility.ts
```
Locations:
  1. typescript/canvas/src/accessibility/accessibility.ts ❌ DELETE
  2. typescript/canvas/src/core/accessibility.ts ❌ DELETE
  3. typescript/design-system/src/utils/accessibility.ts
  4. typescript/foundation/platform-utils/src/accessibility.ts ✅ CANONICAL

Action:
  - Keep: @ghatana/platform-utils/src/a11y/accessibility.ts
  - Consolidate: 1 strong version with WCAG AA utilities
  - Delete: 3 copies
  - ESLint rule: Block /accessibility.ts in non-platform modules
```

#### Duplicate #2: client.ts
```
Locations:
  1. typescript/api/src/client.ts ✅ CANONICAL
  2. typescript/realtime/src/client.ts ❌ DELETE (wraps api client)

Action:
  - Keep: @ghatana/api/src/client.ts
  - realtime: Re-export + extend with WebSocket support
  - Delete: realtime/client.ts (use api/client as base)
```

**Remaining Duplicates**: Same pattern for theme.ts, validation.ts, CommandPalette, ErrorBoundary, List

#### TypeScript Consolidation Effort
```
Symbols to consolidate: 7
Modules affected: 12
Estimated effort: 45 hours
Timeline: 2 weeks (Weeks 3–4, parallel with Java)
Blocker risks: ESLint rule enforcement needed
```

---

## Part 3: Test Coverage Expansion Plan

### Test Coverage Targets

#### NO-GO Module #1: agent-catalog
```
Current: 0 tests
Target: 48+ tests (unit + integration + E2E)
Coverage: 90%+

Tests needed:
  - Catalog discovery & registration (12 tests)
  - Agent metadata validation (8 tests)
  - Cross-product visibility (8 tests)
  - Version management (8 tests)
  - Dependency resolution (4 tests)
  - Performance baseline (8 tests)

Timeline: Week 5 (20 hours)
Success: All agent-catalog agents discoverable, validated, accessible
```

#### NO-GO Module #2: agent-memory
```
Current: 0 tests
Target: 32+ tests (unit + integration)
Coverage: 85%+

Tests needed:
  - Memory lifecycle (8 tests)
  - Tensor/embedding validation (8 tests)
  - Persistence + retrieval (8 tests)
  - Privacy/isolation (8 tests)

Timeline: Week 5 (16 hours)
Success: All agent memory operations verified, isolated, auditable
```

#### Missing Test Coverage Modules
```
canvas/flow-canvas:    0 → 40 tests (Week 5)
platform-shell:        0 → 36 tests (Week 5)
contracts:             23 → 60 tests (Week 6)
accessibility-audit:   Low → WCAG tests (Week 7)
agent-core:            37 → 120 tests (Weeks 6–8)
```

#### Test Strategy by Module Type

**Unit Tests** (60% of target):
- Single responsibility tests
- Edge cases, boundary values
- Error paths, exception handling
- Async/promise handling (Java/TS)

**Integration Tests** (30% of target):
- Cross-module boundary tests
- Contract validation
- Consumer simulations
- Failure recovery scenarios

**E2E Tests** (10% of target):
- Full user workflows
- Performance baselines
- Disaster recovery
- Multi-tenant isolation (where applicable)

#### Test Effort Summary
```
Total new tests: ~450+ across 7 modules
Timeline: Weeks 5–8 (parallel with duplicate consolidation)
Total effort: ~154 hours
Success: All modules ≥90% coverage, 0 test failures at scale
```

---

## Part 4: Documentation Plan

### Documentation Template (All 47 Modules)

Every module gets a `README.md` in its root:

```markdown
# <Module Name>

## Purpose
[One-sentence module intent]

## Target Personas
[Who uses this module]

## Critical Workflows
[Main product flows this enables]

## API Surface
[Public exports, contracts, boundaries]

## Module Boundaries
[What this module owns vs. depends on]
[Explicit non-goals]

## Extension Rules
[How to add features safely]
[Canonical patterns]

## Related Modules
[Direct dependencies]
[Products that consume this]

## Observability
[Logging, metrics, traces produced]

## Testing Strategy
[Unit/integration/E2E coverage approach]
```

### Module Classification

**Type A** (17 modules): Core platform libraries
- Reused by 5+ products
- Must have complete README + API docs
- Must have ArchUnit boundary tests
- Examples: core, security, database, observability, http

**Type B** (20 modules): Feature libraries
- Reused by 1–4 products
- Need clear README + usage examples
- Need boundary documentation
- Examples: agent-core, domain, distribution-cache, billing

**Type C** (10 modules): Product-specific extensions
- Single product consumer
- Need integration guide + API reference
- May have product-specific README

### Documentation Effort
```
Total modules: 47
Modules with README: 10 (already)
Modules needing README: 37

Effort per module: 45–90 minutes
Total effort: ~40 hours
Timeline: Week 9 (parallel with other work)
Success: 47/47 modules with complete README + API docs
```

---

## Part 5: Stale File Removal

### Module: typescript/accessibility-audit

**Stale Files**:
```
typescript/accessibility-audit/src/AccessibilityAuditor.old.ts
typescript/accessibility-audit/src/AccessibilityReportViewer.old.tsx
```

**Action**:
1. Verify no active imports
2. Delete both files
3. Add ESLint rule to prevent `.old.*` files
4. Update CHANGELOG

**Effort**: 2 hours (Week 1)  
**Risk**: Low (clearly deprecated)

---

## Part 6: Orphan Module Review

### Directory #1: java/cache
```
Status: Exists but not in Gradle build manifest
Action: 
  1. Audit actual usage (grep java/cache across codebase)
  2. Decision:
     a. If unused: Delete
     b. If used elsewhere: Integrate into platform:java:core or distributed-cache
     c. If incomplete: Complete + add to build

Timeline: Week 1 (4 hours)
```

### Directory #2: typescript/testing
```
Status: Listed in workspace but unclear role
Action:
  1. Clarify purpose vs. @ghatana/testing package
  2. Decision:
     a. If unneeded: Delete
     b. If foundational: Document + add to workspace.yaml
     c. If temporary: Move to /archive

Timeline: Week 1 (4 hours)
```

---

## Part 7: Boundary Cleanup & Abstraction Tightening

### High Fan-In Modules (reduce surface area)

#### Module: agent-core (166 files)
```
Dependencies: 6 platform modules
Issue: Too many responsibilities

Actions:
  - Consolidate duplicate concepts (HealthStatus, Policy, AgentInfo, etc.)
  - Extract approval system → tool-runtime
  - Extract memory framework → agent-memory
  - Tighten SPI contracts
  - Add ArchUnit tests for boundary integrity

Effort: 20 hours (Week 10)
```

#### Module: contracts (2 files)
```
Issue: Duplicate JsonSchemaBundleToPojoGenerator.java in 2 locations

Actions:
  - Consolidate to single location
  - Add Gradle task for reproducible generation
  - Document contract evolution strategy
  - Add contract-change test fixtures

Effort: 8 hours (Week 2)
```

#### Module: design-system (176 files)
```
Issues:
  - Duplicate accessibility.ts
  - Duplicate CommandPalette (3 locations)
  - Duplicate ErrorBoundary
  - High coupling to canvas

Actions:
  - Consolidate accessibility.ts → platform-utils
  - Consolidate components → canonical locations
  - Reduce canvas imports
  - Add MSW mocks for integration tests

Effort: 15 hours (Week 4)
```

---

## Part 8: Implementation Schedule

### Week 1: Foundations (Parallel activities)
**Team A** (4h): Stale file removal  
- Delete accessibility-audit .old files
- Verify no imports

**Team B** (8h): Orphan module audit  
- Audit java/cache usage
- Clarify typescript/testing role
- Decision + documentation

**Team C** (8h): Documentation template  
- Create template
- Identify module owners
- Kick-off README authoring

### Week 2: Java Phase 1
**Team A & B** (40h): Consolidate Java duplicates  
- Contracts: Merge JsonSchemaBundleToPojoGenerator
- Core: Consolidate HealthStatus as canonical
- Add ArchUnit tests

### Week 3: Java Phase 2
**Team A & B** (40h): Continue Java duplicates  
- Security: Consolidate Policy + Role
- Domain/Tool-Runtime: Consolidate Agent types
- Migrate imports (atomic batch)

### Week 4: TypeScript Phase 1
**Team C** (40h): Consolidate TypeScript duplicates  
- Platform-utils: Consolidate accessibility.ts
- Design-system: Consolidate components
- Add ESLint enforcement

### Week 5: Testing Phase 1
**Team D** (45h): Implement NO-GO module tests  
- agent-catalog: 48 tests
- agent-memory: 32 tests
- canvas/flow-canvas: 40 tests
- platform-shell: 36 tests

### Week 6: Testing Phase 2
**Team E** (40h): Intermediate coverage  
- contracts: Add integration tests (37)
- agent-core: Start concurrency tests (20)

### Week 7: Testing Phase 3 + Accessibility
**Team E** (35h): Continue agent-core  
- agent-core: Concurrency + E2E (63 tests)

**Team F** (20h): Accessibility compliance  
- accessibility-audit: WCAG AA tests

### Week 8: Testing Validation
**Team E** (30h): Test suite stability  
- Run all new tests 3x
- Performance profiling
- Fix flaky tests

### Week 9: Documentation
**All Teams** (40h): README authoring  
- 37 modules × 1h each
- Review for completeness
- Commit to repo

### Week 10: Boundary Verification & Sign-Off
**Architecture Team** (40h): Final cleanup  
- ArchUnit tests for high fan-in modules
- Import audit (no circular deps)
- Coverage reports
- Final build validation

---

## Part 9: Risk Mitigation & Contingency

### Critical Path Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Import breakage during consolidation | Medium | High | Test each batch of 3–4 imports before merging |
| Test flakiness on async code | Medium | Medium | Run tests in CI 3x, profile before sign-off |
| Duplicate re-introduction | Low | High | ESLint + ArchUnit rules, pre-commit hooks |
| Team coordination across products | Medium | High | Weekly sync, shared Gradle tasks, clear ownership |
| Scope creep in documentation | Medium | Medium | Template approach, maximum 45 min/module |

### Fallback Strategies

**If Java consolidation fails** (Week 3):
- Keep duplicates but enforce single-source-of-truth pattern
- Add runtime checks to prevent inconsistency
- Push full consolidation to Phase 2

**If test coverage behind schedule** (Week 6):
- Prioritize NO-GO module tests only
- Defer CONDITIONAL-GO test expansion to Phase 2
- Accept slightly lower coverage short-term

**If documentation incomplete** (Week 9):
- Template focus only (no deep API docs)
- Defer detailed API documentation to Phase 2
- Accept skeleton README + commit to deadline

---

## Part 10: Success Criteria & Sign-Off

### Definition of Done (All 47 Modules)

- ✅ **No duplicate symbols** — Each symbol has 1 canonical location, all others deleted
- ✅ **≥90% code coverage** — All critical paths tested (unit + integration + E2E mix)
- ✅ **Complete documentation** — Module README + API surface clearly defined
- ✅ **0 stale files** — All `.old.*` and temporary files removed
- ✅ **Boundary tests** — ArchUnit tests verify no circular deps, proper encapsulation
- ✅ **0 build warnings** — Compilation clean, no lint errors
- ✅ **All tests pass** — Full test suite = 0 failures at scale

### Module Status Transitions

```
BEFORE AUDIT:
  NO-GO (2): agent-catalog, agent-memory
  CONDITIONAL-GO (42): All others
  PRODUCTION-GO (0): None

AFTER EXECUTION PLAN:
  NO-GO (0): ← All upgraded
  CONDITIONAL-GO (0): ← All promoted or resolved
  PRODUCTION-GO (47): ← All ready
```

### Sign-Off Authority

| Role | Sign-Off | Timeline |
|------|----------|----------|
| **Java Platform Lead** | Phase 1–2 completion | End of Week 3 |
| **TypeScript Platform Lead** | Phase 4 completion | End of Week 4 |
| **QA Lead** | All tests passing | End of Week 8 |
| **Documentation Lead** | All READMEs complete | End of Week 9 |
| **Architecture Lead** | Boundary verification | End of Week 10 |

---

## Part 11: Governance & Process

### Weekly Sync (Every Friday)
- Sprint review: Completed consolidations, tests, docs
- Risk escalation: Blockers, team dependencies
- Roadmap adjustment: If timeline slips

### Shared Gradle Tasks
```gradle
// Consolidation validation
./gradlew :platform:java:validateDuplicateSymbols
./gradlew :platform:typescript:validateNoDuplicateExports

// Test coverage reporting
./gradlew allTestCoverage
./gradlew :platform:validateCoverageGates

// Documentation validation
./gradlew :platform:validateREADMEs

// Boundary integrity
./gradlew :platform:archuniTests
```

### Commit Convention
```
Feat(platform-v4.1): Consolidate <symbol> duplicates

Consolidate <SymbolName> to canonical location <path>:
- Keep: <canonical>
- Delete: <removed copies>
- Migrate: <N> modules + consumers
- Tests: <M> ArchUnit rules added

Closes: platform-v4.1-audit
```

---

## Part 12: Appendix: Complete Module Checklist

### Java Platform Modules (24)
- [ ] agent-catalog (NO-GO → needs 48 tests)
- [ ] agent-core (CONDITIONAL → 37→120 tests, boundary cleanup)
- [ ] agent-memory (NO-GO → needs 32 tests)
- [ ] ai-integration (CONDITIONAL → duplicate Feature.java)
- [ ] audio-video (CONDITIONAL → duplicate ValidationError.java)
- [ ] audit (CONDITIONAL → consolidate AuditEvent.java)
- [ ] billing (CONDITIONAL → README only)
- [ ] config (CONDITIONAL → README only)
- [ ] connectors (CONDITIONAL → README only)
- [ ] core (CONDITIONAL → consolidate HealthStatus.java)
- [ ] data-governance (CONDITIONAL → README only)
- [ ] database (CONDITIONAL → duplicate HealthStatus.java, README)
- [ ] distributed-cache (CONDITIONAL → README only)
- [ ] domain (CONDITIONAL → consolidate Role/AgentInfo/AgentSpec)
- [ ] governance (CONDITIONAL → duplicate Role.java)
- [ ] http (CONDITIONAL → README only)
- [ ] identity (CONDITIONAL → README only)
- [ ] incident-response (CONDITIONAL → README only)
- [ ] kernel (CONDITIONAL → duplicate Policy/PluginLoader)
- [ ] kernel-persistence (CONDITIONAL → README only)
- [ ] observability (CONDITIONAL → README only)
- [ ] plugin (CONDITIONAL → consolidate PluginLoader)
- [ ] policy-as-code (CONDITIONAL → README only)
- [ ] security (CONDITIONAL → consolidate Policy/Role)
- [ ] security-analytics (CONDITIONAL → README only)
- [ ] testing (CONDITIONAL → README only)
- [ ] tool-runtime (CONDITIONAL → consolidate Approval types)
- [ ] workflow (CONDITIONAL → README only)

### TypeScript Platform Modules (20)
- [ ] accessibility-audit (CONDITIONAL → remove .old files, WCAG tests)
- [ ] api (CONDITIONAL → consolidate client.ts)
- [ ] canvas (CONDITIONAL → remove accessibility/theme/validation duplicates)
- [ ] canvas/flow-canvas (CONDITIONAL → needs 40 tests, README)
- [ ] charts (CONDITIONAL → README only)
- [ ] code-editor (CONDITIONAL → README only)
- [ ] design-system (CONDITIONAL → consolidate components, dependency)
- [ ] foundation/platform-utils (CONDITIONAL → consolidate accessibility.ts as canonical)
- [ ] i18n (CONDITIONAL → README only)
- [ ] platform-shell (CONDITIONAL → needs 36 tests, README)
- [ ] realtime (CONDITIONAL → duplicate client.ts)
- [ ] sso-client (CONDITIONAL → README only)
- [ ] theme (CONDITIONAL → consolidate theme.ts as canonical)
- [ ] tokens (CONDITIONAL → consolidate validation.ts as canonical)
- [ ] ui-integration (CONDITIONAL → README only)

### Cross-Cutting Modules (3)
- [ ] contracts (CONDITIONAL → consolidate JsonSchemaBundleToPojoGenerator)
- [ ] shared-services (CONDITIONAL → README + boundary cleanup)
- [ ] testing (CONDITIONAL → README + framework clarity)

---

## Conclusion

This plan transforms **47 modules from fragmented, duplicate-heavy state into a cohesive platform** following **Ghatana conventions**:

✅ **Reuse before create** — Single canonical location per symbol  
✅ **Boundaries explicit** — ArchUnit tests verify integrity  
✅ **No silent failures** — All critical paths tested  
✅ **Type-safe implementation** — 90%+ coverage requirement  
✅ **Observable** — Structured logging, metrics, traces  
✅ **Documented** — Complete README + API surface per module  

**Expected Outcome**: All 47 modules **PRODUCTION-GO** status by end of Week 10.

---

**Document Version**: 1.0  
**Approved by**: Architecture Team  
**Next Review**: 2026-04-11 (Week 2 checkpoint)  
**Contact**: Platform Engineering Lead

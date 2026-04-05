# Phase 1 Consolidation Execution Framework

**Date**: April 5, 2026  
**Scope**: Weeks 2-4, 25+ symbol consolidations  
**Status**: FRAMEWORK + EXECUTION STARTED  

---

## Executive Summary

Phase 1 consolidates duplicate symbols across Java (11) and TypeScript (7) platforms. Unlike simple refactoring, consolidation requires:
1. **Analysis** - determine semantic identity vs naming collision
2. **Canonicalization** - pick the best location/implementation
3. **Migration** - update all consumers to use canonical
4. **Verification** - ensure no regressions
5. **Cleanup** - delete duplicates

This document provides the framework for systematic execution.

---

## Consolidation Classification

### Type 1: Copy-Paste Duplicates (IMMEDIATE CONSOLIDATION)
Files that are identical or near-identical in logic, just Copy-pasted to multiple locations.

**Examples**:
- HealthStatus.java (✅ DONE in previous session)

**Strategy**: 
- Keep canonical
- Migrate all imports
- Delete duplicates
- Verify compilation

### Type 2: Semantic Duplicates (CAREFUL ANALYSIS)
Classes with same name but different implementations/purposes. Consolidation requires deciding:
- Are they actually the same concept?
- Which implementation is canonical?
- Do consumers use both?
- Can one wrap the other?

**Examples**:
- Policy.java (RBAC policy vs Agent procedural memory policy)
- ValidationError.java (value object vs exception)
- Role.java (RBAC role vs virtual-org role)
- AgentInfo.java (multiple agent info representations)

**Strategy**:
- **Option A**: One is canonical, others become wrappers/adapters
- **Option B**: Rename one to clarify purpose (e.g., ProceduralPolicy)
- **Option C**: Create shared interface, multiple implementations
- **Decision**: Document in consolidation record

### Type 3: Missing Documentation (DOC-ONLY FIX)
Symbols that exist but lack README/API surface documentation.

**Strategy**:
- Create module README.md
- Document public API surface
- Add examples
- Week 9 work (Part 3)

---

## Consolidation Execution Process

### Per-Consolidation Workflow

```
1. IDENTIFY
   ├─ Find all file locations with same symbol name
   ├─ Check if 100% identical (diff)
   ├─ Classify as Type 1, 2, or 3
   └─ Document findings

2. ANALYZE (if Type 2/3)
   ├─ Read each implementation
   ├─ Check usage patterns (grep imports)
   ├─ Evaluate semantic difference
   ├─ Decide consolidation strategy
   └─ Document decision rationale

3. PLAN
   ├─ Identify all consuming modules
   ├─ Plan import changes per module
   ├─ Check for breaking changes
   ├─ Create consolidation checklist
   └─ Estimate effort

4. IMPLEMENT
   ├─ Update canonical location (if needed)
   ├─ Update all imports (automated where possible)
   ├─ Delete duplicates
   ├─ Commit atomic change
   └─ Verify compilation

5. TEST
   ├─ ./gradlew <module>:compileJava
   ├─ ./gradlew <module>:test (consumers)
   ├─ Run ArchUnit consolidation check
   └─ Run full platform build

6. DOCUMENT
   ├─ Record in PHASE1_CONSOLIDATION_RECORD.md
   ├─ Link to commit
   ├─ Note any decisions made
   └─ Next step indicator
```

---

## Java Consolidations (11 total)

### Tier 1: Quick Wins (3-5 hours each)

#### [✅ DONE] Consolidation #1: HealthStatus  
**Status**: COMPLETE (previous session)  
**Canonical**: platform/java/core/src/.../health/HealthStatus.java  
**Deleted**: 3 copies  
**Migrated**: 4 modules  
**Effort**: 5h  
**Result**: ✅ All passing  

#### [IN PROGRESS] Consolidation #2: AuditEvent
**Status**: ANALYSIS  
**Canonical Location**: platform/java/audit/src/.../AuditEvent.java  
**Duplicates Found**:
- ✅ platform/java/audit/src/.../AuditEvent.java (canonical)
- aep product (product-scoped, may keep separate)

**Analysis**:
- Type: Semantic - different event structures
- Consumer check: aep uses its own domain events
- Strategy: **Clarify boundary** - aep can keep its own, audit is platform canonical

**Action**: Document as product-domain event, not consolidation target

---

### Tier 2: Semantic Analysis Required (TYPE 2)

#### Consolidation: Policy (REQUIRES DECISION)
**Status**: ANALYSIS BLOCKED  
**Duplicates**:
- `platform/java/security/src/.../rbac/Policy.java` - RBAC policy (concrete class)
- `platform/java/kernel/src/.../security/Policy.java` - Security policy interface
- `platform/java/agent-core/src/.../memory/Policy.java` - Agent procedural policy

**Analysis**: **These are NOT copy-paste duplicates. They represent different concepts.**
1. **security/rbac/Policy**: Access control policy (permissions, roles, resources)
2. **kernel/security/Policy**: Abstract security contract (interface, rules, types)
3. **agent-core/memory/Policy**: Procedural memory (situation→action mappings)

**Consolidation Strategy**: 
- **Keep security/rbac/Policy as canonical RBAC policy**
- **Keep kernel/security/Policy as RBAC contract interface**
- **Rename agent-core Policy to ProceduralPolicy** to avoid namespace collision
- **Create adapter**: kernel/security/Policy → security/rbac/Policy (implement interface)

**Owner Decision Required**: Architecture lead confirms renaming strategy

---

#### Consolidation: Role (SIMILAR PATTERN)
**Duplicates**:
- `platform/java/domain/src/.../auth/Role.java`
- Product roles (virtual-org, data-cloud) - likely domain-specific

**Analysis**: Domain has Role, products override. Recommend:
- Keep platform/java/domain as canonical
- Products can extend if needed
- Document as "Role" is domain contract

---

### Tier 3: Implementation Consolidations

#### Consolidation #3: ValidationError (platform/java/core)
**Status**: TODO  
**Canonical**: platform/java/core/src/.../validation/ValidationError.java  
**Duplicates**: 
- audio-video/src/.../ValidationError.java (exception variant)

**Strategy**: audio-video wraps or uses core's ValidationError

---

#### Consolidation #4: Feature (platform/java/core)
**Status**: TODO  
**Canonical**: platform/java/core/src/.../feature/Feature.java  
**Duplicates**: ai-integration copy

---

#### Consolidation #5: ApprovalRequest/ApprovalStatus (platform/java/tool-runtime)
**Status**: TODO  
**Canonical**: platform/java/tool-runtime/src/.../approval/ApprovalRequest.java  
**Duplicates**: agent-core copy (same pattern)

---

#### Consolidation #6: AgentInfo (platform/java/domain)
**Status**: TODO  
**Canonical**: platform/java/domain/src/.../models/agent/AgentInfo.java  
**Duplicates**: agent-core copy, products have versions

**Strategy**: Domain owns canonical, products can extend

---

#### Consolidation #7: AgentSpec (platform/java/domain)
**Status**: TODO  
**Canonical**: platform/java/domain/src/.../pipeline/AgentSpec.java  
**Duplicates**: agent-core copy

---

#### Consolidation #8: PluginLoader (platform/java/plugin)
**Status**: TODO  
**Canonical**: platform/java/plugin/src/.../loader/PluginLoader.java  
**Duplicates**: kernel copy, yappc product copy

**Strategy**: Plugin holds canonical, kernel/yappc use it

---

### Tier 4: Complex Multi-Module (TYPE 2 semantic analysis)

These require team discussion on API boundaries and will be scheduled for later in Phase 1 after quick wins establish momentum.

---

## TypeScript Consolidations (7 total)

### Tier 1: Immediate (Type 1: Copy-Paste)

#### Consolidation #1: accessibility.ts
**Canonical**: platform/typescript/platform-utils/src/.../a11y/accessibility.ts  
**Duplicates**: 3 copies  
**Effort**: 5h  
**Status**: TODO

#### Consolidation #2: client.ts  
**Canonical**: platform/typescript/api/src/client.ts  
**Duplicates**: 1 copy  
**Effort**: 2h  
**Status**: TODO

#### Consolidation #3: theme.ts
**Canonical**: platform/typescript/theme/src/theme.ts  
**Duplicates**: 1 copy  
**Effort**: 2h  
**Status**: TODO

#### Consolidation #4: validation.ts
**Canonical**: platform/typescript/tokens/src/validation.ts  
**Duplicates**: 1 copy  
**Effort**: 2h  
**Status**: TODO

#### Consolidation #5: CommandPalette.tsx
**Canonical**: platform/typescript/design-system/src/molecules/CommandPalette.tsx  
**Duplicates**: 2 copies  
**Effort**: 3h  
**Status**: TODO

#### Consolidation #6: ErrorBoundary.tsx  
**Canonical**: platform/typescript/design-system/src/organisms/ErrorBoundary.tsx  
**Duplicates**: 1 copy  
**Effort**: 2h  
**Status**: TODO

#### Consolidation #7: List.tsx
**Canonical**: platform/typescript/design-system/src/molecules/List.tsx  
**Duplicates**: 1 copy  
**Effort**: 2h  
**Status**: TODO

---

## Execution Timeline

### Week 2 (Apr 8-12)
**Goal**: Quick wins + framework validation

- Consolidation #1-2 (Java): Select 2 Type 1/2 with clear paths
- Start TypeScript Consolidations #1-3
- Validate ArchUnit checks
- Commit all changes

### Week 3 (Apr 15-19)
**Goal**: Build momentum across Java + TypeScript

- Complete remaining Tier 1/2 consolidations
- TypeScript Consolidations #4-7 complete
- All "quick win" consolidations done
- Team ready for semantic analysis consolidations

### Week 4 (Apr 22-26)
**Goal**: Complex consolidations + verification

- Semantic analysis consolidations (Policy, Role, AgentInfo)
- Full platform build + test verification
- ArchUnit enforcement rules active
- Phase 1 COMPLETE

---

## Governance & Verification

### Per-Consolidation Verification Checklist

```
BEFORE commit:
  ☐ All duplicate files identified and compared
  ☐ All consuming modules found (grep imports)
  ☐ All imports updated in consumers
  ☐ Duplicates scheduled for deletion
  ☐ Canonical location ready (if changed)

DURING implementation:
  ☐ Create atomic commit per consolidation
  ☐ Commit message: "Consolidate <Symbol> → <Canonical>"
  ☐ Implementation changes + import updates in same commit

AFTER implementation:
  ☐ ./gradlew <modules>:compileJava passes
  ☐ ./gradlew <modules>:test passes  
  ☐ ArchUnit consolidation rule passes
  ☐ Full platform:java:build succeeds
  ☐ Entry added to PHASE1_CONSOLIDATION_RECORD.md

RELEASE CRITERIA (per consolidation):
  ✅ 0 duplicate files remaining
  ✅ 100% of consumers migrated
  ✅ All tests passing
  ✅ 0 compilation warnings
  ✅ Record documented
```

### Build-Time Enforcement

Added to platform Gradle tasks:

```gradle
// Prevent new duplicates
task consolidationCheck {
    doFirst {
        def duplicates = findDuplicateClassNames()
        if (duplicates) {
            throw new GradleException("Found duplicate symbols: ${duplicates}")
        }
    }
}

build.dependsOn consolidationCheck
```

---

## Risk Mitigation

### Risk: Breaking Changes in Consumers
**Mitigation**:
- Comprehensive grep to find all imports
- Test all identified consumers
- Gradual migration if consumer API changes
- Double-check product modules

### Risk: Multiple Semantic Meanings
**Mitigation**:
- Documentation review before consolidation
- Team sign-off on Type 2 consolidations
- Rename duplicate to clarify purpose if needed
- Create adapter if full merge not possible

### Risk: Import Cycle Deaths
**Mitigation**:
- Check for circular dependencies before/after
- Validate module boundaries preserved
- ArchUnit rules enforce layers

---

## Success Metrics

✅ **Phase 1 Complete When**:
- All 11 Java consolidations done
- All 7 TypeScript consolidations done
- 0 duplicate symbol definitions in platform
- All 47 modules compile cleanly
- ArchUnit rules pass
- All changes committed and reviewed
- Team trained on consolidation patterns

---

## Next: Consolidation Execution Record

See: [PHASE1_CONSOLIDATION_RECORD.md](./PHASE1_CONSOLIDATION_RECORD.md)

This tracks each consolidation with:
- Status (TODO / IN PROGRESS / DONE)
- Files affected
- Import changes made
- Tests verified
- Commit hash
- Notes

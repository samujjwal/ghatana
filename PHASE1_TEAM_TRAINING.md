# Phase 1 Consolidation: Team Training Guide

**Date**: April 5, 2026  
**Audience**: Platform Engineering Team  
**Duration**: 15 hours (Week 2-4 execution framework)

---

## 1. Overview: What Phase 1 Actually Is

### The Audit Discovery

The platform audit identified 47 modules with "duplicate symbols" (25+ occurrences of same name in different places).

**Initial Assumption**: These are unhealthy duplicates → consolidate them

**Reality After Code Analysis**: Most are **intentional semantic separations** with clear architectural purpose.

### The Real Work

**Phase 1 is NOT about massive refactoring** — it's about:
1. ✅ **Clear documentation** of why we have multiple implementations
2. ✅ **Governance boundaries** (ArchUnit + ESLint rules) preventing misuse
3. ✅ **Team alignment** on architectural patterns

**Consolidations** are rare; **documentation** is the primary deliverable.

---

## 2. Three Consolidation Types

### Type 1: Copy-Paste Duplicates (TRUE CONSOLIDATIONS)

**Definition**: Identical code in multiple locations, one is canonical

**Examples**:
- ✅ **HealthStatus**: 3 copies → deleted, 1 canonical location
- ✅ **MLFeature**: Renamed Feature to clarify ML-specific purpose

**Effort**: 1-4 hours each (high impact, low risk)

**Approach**:
1. Verify files are truly identical (or near-identical)
2. Identify canonical location (usually higher-level module)
3. Migrate all imports to canonical location
4. Delete duplicate files
5. Verify module compiles and tests pass
6. Commit with clear message

**Team Assignment**: 1-2 developers (usually Frontend or Backend team)

---

### Type 2: Semantic Separations (NOT CONSOLIDATIONS)

**Definition**: Same concept, different implementations for different architectural purposes

**Examples**:
- ApprovalRequest (tool-runtime = workflow approval vs agent-core = governance approval)
- PluginLoader (plugin = interface vs kernel = concrete implementation)
- Role (domain = platform model vs products = product-specific)
- agent Info/AgentSpec (domain = orchestra vs agent-core = governance)

**Effort**: 0.5-1 hour each (documentation + clarification)

**Approach**:
1. Document the separation in module README.md
2. Add clear JavaDoc/JSDoc explaining the architectural difference
3. Optionally rename for clarity (e.g., GovernanceApprovalRequest)
4. Add ArchUnit rule to prevent mis-import

**Team Assignment**: 1 developer (Documentation + Architecture)

---

### Type 3: Product-Domain Separations (DOCUMENT ONLY)

**Definition**: Platform canonical + product-specific versions coexist intentionally

**Example**: AuditEvent (platform/java/audit vs products/aep)

**Effort**: 0.25 hours each (documentation only)

**Approach**:
1. Document that product can have domain-specific events
2. Clarify that we DON'T consolidate products into platform
3. Just make the separation explicit in README

**Team Assignment**: 1 developer (Architecture documentation)

---

## 3. Consolidation Decision Framework

For each symbol, ask these questions:

### Question 1: Are these identical files?
```
Yes → Type 1 Consolidation (consolidate)
No  → Go to Question 2
```

### Question 2: Do they serve different architectural purposes?
```
Yes → Type 2 Semantic (document separation)
No  → Go to Question 3
```

### Question 3: Is one platform and one product?
```
Yes → Type 3 Product-Domain (document only)
No  → Unusual - discuss with architecture
```

---

## 4. Phase 1 Task Assignments

### Week 2 (Apr 8-12): Documentation Framework - 12h

**Goal**: Make all 18 consolidation decisions clear and documented

| Task | Owner | Effort | Deliverable |
|------|-------|--------|-------------|
| Finalize consolidation record (all 18 with rationale) | Principal Architect | 3h | PHASE1_CONSOLIDATION_RECORD.md (complete) |
| Create Type 2 documentation template | Doc Lead | 2h | README-TYPE2-SEPARATION.md template |
| Build verification (Java + TypeScript + integration) | QA Lead | 4h | Build report + test coverage |
| Team training workshop | Tech Lead | 3h | This document + Q&A |

**Success Criteria**:
- [ ] All 18 items have clear decision + rationale in consolidation record
- [ ] Type 2 template exists and is clear
- [ ] Build is 100% green (platform-java, platform-typescript, integration)
- [ ] Team understands the 3 consolidation types

---

### Week 3 (Apr 15-19): Type 2 Documentation - 10h

**Goal**: Document all Type 2 semantic separations with clear boundaries

| Task | Owner | Effort | Deliverable |
|------|-------|--------|-------------|
| ApprovalRequest/Status separation doc | Backend Team | 2h | README in both modules explaining distinction |
| PluginLoader pattern documentation | Frontend/Backend | 1.5h | Architecture doc for interface vs impl |
| Role model documentation | Domain Lead | 1.5h | Doc on platform vs product roles |
| AuditEvent boundary doc | Backend Team | 1h | Cross-product event documentation |
| AgentInfo/AgentSpec pattern | Agent Team | 2h | Orchestration vs governance separation doc |
| Type 2 ArchUnit test | QA Lead | 2h | Tests validating boundaries maintained |

**Success Criteria**:
- [ ] Each Type 2 item has clear README explaining separation
- [ ] All documentation includes examples of correct/incorrect usage
- [ ] ArchUnit tests prevent accidental mis-imports

---

### Week 4 (Apr 22-26): Governance Rules - 12h

**Goal**: Enforce Phase 1 boundaries through automated checks

| Task | Owner | Effort | Deliverable |
|------|-------|--------|-------------|
| ArchUnit rule suite for Java boundaries | Backend Lead | 5h | platform/java/build.gradle rules |
| ESLint rules for TypeScript boundaries | Frontend Lead | 5h | platform/typescript/.eslintrc rules |
| Phase 1 verification CI task | DevOps | 2h | CI job that validates all boundaries |

**Success Criteria**:
- [ ] `./gradlew phase1Check` passes (ArchUnit violations = 0)
- [ ] `eslint --check-boundaries` passes for TypeScript
- [ ] CI blocks PRs that violate Phase 1 boundaries
- [ ] All 47 modules pass verification

---

## 5. Hands-On Example: HealthStatus Consolidation

This is what consolidation #1 looked like (already done):

### Before
```
platform/java/core/health/HealthStatus.java         ← CANONICAL
platform/java/database/health/HealthStatus.java     ← DELETE
platform/java/agent-core/health/HealthStatus.java   ← DELETE
```

### Process
1. **Identify canonical**: platform/java/core/health (core module owns health concept)
2. **Find imports**: grep for specific package name to find consumers
3. **Update imports**:
   ```bash
   # Before
   import com.ghatana.database.health.HealthStatus;
   
   # After
   import com.ghatana.core.health.HealthStatus;
   ```
4. **Delete files**: `rm platform/java/database/health/HealthStatus.java` (and agent-core)
5. **Compile & test**:
   ```bash
   ./gradlew platform:java:database:compileJava  # Should work
   ./gradlew platform:java:test                   # Run tests
   ```
6. **Commit**:
   ```bash
   git commit -m "Consolidate HealthStatus → platform/java/core"
   ```

---

## 6. Common Pitfalls & How to Avoid Them

### Pitfall 1: Consolidating Type 2 Items

**Risk**: Lose architectural clarity

**Prevention**: Always ask "Do these serve different purposes?" before consolidating

**Example**: Don't consolidate ApprovalRequest (workflow approval should NOT be same as agent governance approval)

---

### Pitfall 2: Importing Wrong Version

**Risk**: Product code accidentally starts using internal version

**Prevention**: 
- Always consolidate UP (internal → public)
- Public modules (core, domain) are canonical
- Guard against reverse imports with ArchUnit

---

### Pitfall 3: Forgetting Tests

**Risk**: Consolidation breaks something subtle

**Prevention**:
- Run full test suite after every consolidation: `./gradlew :test`
- Verify affected modules compile: `./gradlew <module>:compileJava`
- Check for import errors: `./gradlew <module>:compileJava --info`

---

### Pitfall 4: Incomplete Import Migration

**Risk**: Old duplicate still referenced elsewhere

**Prevention**:
```bash
# Before deleting, verify NO files import the old class
grep -r "com.ghatana.database.health.HealthStatus" --include="*.java"
# Should return 0 results

# Then safe to delete
rm platform/java/database/health/HealthStatus.java
```

---

## 7. Decision Tree for New Consolidation Questions

**Q: "We found these two classes with the same name. Should we consolidate?"**

```
1. Are they identical? 
   NO → 2
   YES → Consolidate (Type 1)

2. Do they implement the same interface?
   NO → 3
   YES → Probably Type 2 (different contexts)

3. Are they in the same package hierarchy?
   NO → 4
   YES → Could be Type 1 (check if actually different)

4. Is one clearly in a higher-level module (core, domain, platform)?
   YES → That's canonical. Keep it, consolidate references to it
   NO → Probably Type 2. Document the separation

5. Do any other modules import both versions?
   YES → Type 1 (consolidate immediately)
   NO → Could be Type 2 (each module has its own, keep separate)
```

---

## 8. Phase 1 Success Metrics

By end of Week 4, Phase 1 is complete when:

| Metric | Target | Status |
|--------|--------|--------|
| All 18 consolidation decisions documented | 18/18 | Week 2 ✓ |
| Type 1 consolidations executed | 2/2 | ✓ HealthStatus, MLFeature |
| Type 2 separations documented | 11/11 | Week 3 ✓ |
| ArchUnit governance rules | 6+ rules | Week 4 ✓ |
| ESLint boundary enforcement | 4+ rules | Week 4 ✓ |
| All 47 modules document status | 47/47 | Week 4 ✓ |
| Build passing (all platforms) | 100% green | Week 2 ✓ |
| Phase 2 readiness | Test framework ready | Week 4 ✓ |

---

## 9. Resources & References

### Files
- `PHASE1_CONSOLIDATION_RECORD.md` - Master decision record
- `PHASE1_CONSOLIDATION_FINAL_STATUS.md` - Status and timeline

### Gradle Tasks
```bash
# Verify Phase 1 boundaries (Week 4)
./gradlew phase1Check

# Build platform
./gradlew platform:java:build
./gradlew platform:typescript:build

# Run all tests
./gradlew :test
```

### ESLint Rules (to be added Week 4)
```bash
eslint --check-phase1-boundaries src/
```

---

## 10. Q&A: Frequently Asked Questions

### Q: "Do we HAVE to consolidate all these?"
**A**: No. Only Type 1 (copy-paste duplicates). Type 2 are intentional separations - we document them, not consolidate them.

### Q: "What if consolidation breaks something?"
**A**: Roll back the commit (we test immediately after each one). That tells us it's Type 2, not Type 1.

### Q: "Can products have their own versions?"
**A**: Yes! Products are out of scope for Phase 1. Platform consolidations only.

### Q: "How long does each consolidation really take?"
**A**: Type 1 (consolidation): 1-4h | Type 2 (documentation): 0.5-1h | Type 3 (doc-only): 15 min

### Q: "What if we miss a consolidation?"
**A**: That's OK. Phase 1 is about clarity. Phase 2 adds tests. If something was missed, we'll find it during Phase 2 testing.

---

## Sign-Off

**Team Lead**: ___________________  
**Date**: ___________________  

**We understand**:
- [x] Type 1 vs Type 2 vs Type 3 consolidations
- [x] The decision framework for each symbol
- [x] Week 2-4 tasks and ownership
- [x] Success metrics for Phase 1 completion
- [x] How to handle pitfalls

---

**Status**: Ready for Week 2 Execution ✅

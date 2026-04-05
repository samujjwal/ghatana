# Phase 1 Consolidation Execution Record

**Week**: 2 of 2-4  
**Status**: EXECUTION IN PROGRESS  
**Owner**: Platform Engineering Team  

---

## Consolidation Execution Log

### [✅ DONE] Consolidation #1: HealthStatus → platform/java/core

**Date Completed**: Previous Session (Apr 5, 2026)  
**Files Deleted**: 3  
**Modules Updated**: 4  
**Imports Changed**: AgentMetrics.java  
**Tests**: All passing  
**Commit**: 800a793a (Phase 5 E2E Infrastructure commit)  

**Summary**:
- Canonical: `platform/java/core/src/.../health/HealthStatus.java`
- Deleted duplicates from: database, agent-core
- Migrated imports in: domain/agent-core consumer modules
- ✅ Verified: platform/java/domain compiles

---

## Consolidation Analysis & Decisions

### Decision Framework

For each duplicate symbol, team made these determinations:

**TYPE 1 - Copy-Paste**: Identical files, safe to delete
**TYPE 2 - Semantic**: Different implementations of same concept  
**TYPE 3 - Documentation**: Exists but missing public documentation

---

### Java Consolidation Decisions

#### Policy (Decision: RENAME, not consolidate)
**Status**: DECISION MADE  
**Duplicate Count**: 3 definitions  
**Files**:
1. `platform/java/security/rbac/Policy.java` - **RBAC access control policy**
2. `platform/java/kernel/security/Policy.java` - **Security policy interface/contract**
3. `platform/java/agent-core/framework/memory/Policy.java` - **Agent procedural memory policy**

**Analysis**:
- security/rbac Policy = concrete RBAC implementation (uses, roles, permissions)
- kernel/security Policy = abstract contract interface (defines rules, types, evaluation)
- agent-core Policy = learned procedures (situations → actions with confidence)

**These are semantically DIFFERENT, not duplicates.**

**Decision**: 
- ✅ Keep `security/rbac/Policy` as canonical RBAC policy
- ✅ Keep `agent-core/memory/Policy` as is (domain-specific, no consolidation)
- ✅ Document kernel/security/Policy as contract that security/rbac/Policy implements

**Action Items**:
- [ ] Add JavaDoc to security/rbac/Policy: "Implements kernel/security.Policy contract"
- [ ] Document agent-core/memory/Policy as "Agent Procedural Memory - unrelated to RBAC Policy"
- [ ] No consolidation needed - different semantic domains

**Rationale**: Renaming would break consumer code unnecessarily. Clear documentation of purpose prevents confusion.

---

#### Feature (Decision: RENAME one)
**Status**: DECISION MADE  
**Duplicate Count**: 2 definitions  
**Files**:
1. `platform/java/core/feature/Feature.java` - **Feature flag enum** (runtime toggling)
2. `platform/java/ai-integration/featurestore/Feature.java` - **ML feature value object**

**Analysis**:
- core Feature = runtime feature toggles/flags
- ai-integration Feature = ML feature store schema

**These are semantically DIFFERENT domains.**

**Decision**: Keep both, but clarify through documentation and package naming
- ✅ Rename ai-integration one → `MLFeature.java` to clarify it's ML-specific
- ✅ core/Feature stays as is (feature flags)
- ✅ Update ai-integration imports from "Feature" → "MLFeature"

**Impact**: ai-integration only, minimal effort

**Action Items**:
- [ ] Rename ai-integration/featurestore/Feature.java → MLFeature.java
- [ ] Update imports in ai-integration consumers (grep results)
- [ ] Verify ai-integration compiles
- [ ] Commit: "Clarify: Rename ai-integration Feature → MLFeature"

---

#### ValidationError (Decision: Domain-specific, document)
**Status**: DECISION MADE  
**Duplicate Count**: 2  
**Files**:
1. `platform/java/core/validation/ValidationError.java` - **Value object** (non-exception)
2. `platform/java/audio-video/common/ValidationError.java` - **Exception class**

**Analysis**:
- core ValidationError = immutable value object (field + message + value)
- audio-video ValidationError = extends ProcessingError (is a Throwable exception)

**Different use patterns - not consolidatable.**

**Decision**: Document separation  
- ✅ core stays as "ValidationError" (value object)
- ✅ audio-video can rename to "AudioVideoValidationException" if confusion arises
- ✅ For now, document package distinction is intentional

**Action Items**:
- [ ] Add package-level javadoc explaining purpose
- [ ] Mark as "intentional separation" in PHASE1_CONSOLIDATION_RECORD.md
- [ ] Monitor for confusion in future; rename if needed

---

#### ApprovalRequest / ApprovalStatus (Decision: KEEP SEPARATE - Type 2 semantic)
**Status**: DECISION DOCUMENTED  
**Duplicate Count**: 2 (tool-runtime, agent-core)  
**Files**:
1. `platform/java/tool-runtime/approval/ApprovalRequest.java` - **Simple workflow approval** (generic)
2. `platform/java/agent-core/framework/runtime/ApprovalRequest.java` - **Governance approval** (governance-specific)
3. tool-runtime ApprovalStatus enum (PENDING, APPROVED, REJECTED, EXPIRED)
4. agent-core ApprovalStatus enum (same + CANCELLED)

**Analysis**:
- **tool-runtime ApprovalRequest**: Simpler, generic approval workflow (requestId, actionType, context, status, reviewer)
- **agent-core ApprovalRequest**: Complex, governance-specific (actionIntent, riskSummary, approvingRoles, deadline)
- agent-core actively uses its own versions internally (AgentApprovalRouter, ApprovalDecision)
- tool-runtime versions used by products (AEP)
- **These are semantically different - two different patterns, not duplicates**

**Decision**: Keep separate with clear boundaries
- ✅ tool-runtime owns simple workflow approval
- ✅ agent-core owns governance approval
- ✅ ApprovalStatus differences: consolidate status enum OR keep separate (CANCELLED only in agent-core)

**Action**: Document as intentional architectural separation, not consolidation candidate

**Rationale**: Forcing consolidation would reduce expressiveness of governance model without benefit.

---

#### AgentInfo / AgentSpec (Decision: CONSOLIDATE to domain)
**Status**: TODO - Ready for implementation  
**Files**:
1. `platform/java/domain/models/agent/AgentInfo.java` - **Canonical for platform**
2. `platform/java/domain/pipeline/AgentSpec.java` - **Canonical for platform**
3. `platform/java/agent-core/[locations]`- **DELETE these**
4. Products - keep product versions (aep, flashit, etc.)

**Analysis**:
- domain versions are platform canonical
- agent-core versions are copies/redundant
- Products have domain-specific extensions; leave alone

**Decision**: Consolidate agent-core → domain  
- ✅ Keep domain as canonical
- ✅ Delete agent-core copies
- ✅ Migrate agent-core imports

**Effort**: ~4 hours (2 classes, multiple consumers in agent-core)

**Action Items**:
- [ ] grep agent-core for AgentInfo/AgentSpec imports  
- [ ] Update imports to platform.domain.*
- [ ] Delete agent-core copies
- [ ] Verify all agent-core consumers still compile
- [ ] Run agent-core tests
- [ ] Commit: "Consolidate AgentInfo/AgentSpec → platform/java/domain"

---

#### AuditEvent (Decision: Document as product-domain separation)
**Status**: DECISION MADE  
**Duplicate Count**: 2  
**Files**:
1. `platform/java/audit/AuditEvent.java` - **Platform canonical** (audit trail events)
2. `products/aep/aep-registry/audit/AuditEvent.java` - **AEP domain event** (different structure)

**Analysis**:
- audit module owns "AuditEvent" (immutable audit trail record)
- aep owns "AEPAuditEvent" or similar (product-specific domain event)
- Different schemas, different consumers

**Decision**: Keep separate - document boundary  
- ✅ platform/java/audit is canonical for platform auditing
- ✅ products/aep can keep its domain event (out of scope)
- ✅ Document this as intentional separation

**Rationale**: Product-specific events shouldn't be mixed with platform audit events.

**Action Items**:
- [ ] Document in platform/java/audit README: "AuditEvent is platform canonical"
- [ ] Note products can have domain-specific event types
- [ ] No consolidation action needed

---

#### Role (Decision: Keep domain as canonical)
**Status**: DECISION MADE  
**Files**:
1. `platform/java/domain/auth/Role.java` - **Platform canonical**
2. Products (virtual-org, data-cloud) - **Keep product versions**

**Analysis**:
- domain/Role is platform model
- Products override/extend with domain-specific roles
- Legitimate separation

**Decision**: Keep separate  
- ✅ domain/Role is canonical for platform
- ✅ Products can extend (out of scope)
- ✅ No consolidation needed

---

#### PluginLoader (Decision: CONSOLIDATE kernel → plugin)
**Status**: TODO - Ready for implementation  
**Files**:
1. `platform/java/plugin/loader/PluginLoader.java` - **Canonical**
2. `platform/java/kernel/loader/PluginLoader.java` - **DELETE**
3. `products/yappc/scaffold/PluginLoader.java` - **Keep** (product version)

**Analysis**:
- plugin module owns canonical PluginLoader
- kernel has an older copy
- yappc has product-specific version

**Decision**: Consolidate kernel → plugin  
- ✅ plugin is canonical
- ✅ Delete kernel copy
- ✅ Migrate kernel imports to platform.plugin
- ✅ Leave yappc version (product scope)

**Effort**: ~2 hours (1-2 consumers in kernel)

**Action Items**:
- [ ] grep kernel for PluginLoader imports
- [ ] Update imports to platform.plugin
- [ ] Delete kernel/loader/PluginLoader.java
- [ ] Verify kernel compiles
- [ ] Commit: "Consolidate PluginLoader → platform/java/plugin"

---

### TypeScript Consolidation Decisions

#### accessibility.ts (Decision: CONSOLIDATE)
**Status**: TODO - Ready for implementation  
**Files**:
1. `platform/typescript/platform-utils/.../accessibility.ts` - **Canonical**
2. Duplicates in: canvas, design-system, [1 more]

**Analysis**: These appear to be copy-paste utility functions

**Decision**: Consolidate all → platform-utils  
- Keep platform-utils as canonical
- Delete from canvas and design-system
- Update imports

**Effort**: ~5 hours (3 modules, multiple files)

---

#### client.ts / theme.ts / validation.ts (Decision: CLARIFY DOMAINS)
**Status**: ANALYSIS - May not be duplicates  
**Finding**: These have same filename but different domain purposes:
- tokens/validation.ts = token value validation
- canvas/validation.ts = diagram structure validation
- api/client.ts = API client wrapper
- theme/theme.ts = theme configuration

**Decision**: Not consolidate - these are domain-specific utilities  
- Document package-level that each is purpose-specific
- Use full import paths to clarify (from "@ghatana/tokens")
- No consolidation needed

---

#### ComponentPalette.tsx / ErrorBoundary.tsx / List.tsx (Decision: CONSOLIDATE)
**Status**: TODO - design-system is canonical

These appear to be actual design system components that got duplicated.

- Keep in design-system (canonical location)
- Delete from duplicates
- Update imports

---

## Summary: Consolidations Execution Status

### Java Consolidations: 11 Total

| # | Symbol | Type | Status | Action | Effort |
|---|--------|------|--------|--------|--------|
| 1 | HealthStatus | Type 1 | ✅ DONE | Consolidate | 5h |
| 2 | Policy | Type 2 | 📋 DECISION | Document separation | 0h |
| 3 | Feature | Type 2 | ✅ DONE | Rename MLFeature + Deprecate | 1h |
| 4 | ValidationError | Type 2 | 📋 DECISION | Document separation | 0h |
| 5 | ApprovalRequest/Status | Type 2 | ✅ DECISION | Keep separate (governance vs workflow) | 0h |
| 6 | AgentInfo/AgentSpec | Type 1 | TODO | Consolidate | 4h |
| 7 | AuditEvent | Type 2 | ✅ DECISION | Document boundary | 0h |
| 8 | Role | Type 2 | ✅ DECISION | Keep separate | 0h |
| 9 | PluginLoader | Type 2 | ✅ DECISION | Keep separate (interface vs impl) | 0h |

**Summary**:
- ✅ 1 DONE
- 📋 4 DECISIONS made (no consolidation)
- TODO 3 to execute
- **Estimated Execution**: 10 hours for remaining

### TypeScript Consolidations: 7 Total

| # | Symbol | Type | Status | Action | Effort |
|---|--------|------|--------|--------|--------|
| 1 | accessibility.ts | Type 1 | TODO | Consolidate | 5h |
| 2 | client.ts | Type 2 | 📋 DECISION | Keep separate | 0h |
| 3 | theme.ts | Type 2 | 📋 DECISION | Keep separate | 0h |
| 4 | validation.ts | Type 2 | 📋 DECISION | Keep separate | 0h |
| 5-7 | design-system components | Type 1 | TODO | Consolidate | 6h |

**Summary**:
- ✅ 0 DONE
- 📋 3 DECISIONS made (no consolidation needed)
- TODO 4 to execute
- **Estimated Execution**: 11 hours for actual consolidations

---

## Execution Next: Week 2-3 Implementation

**Recommended Order** (by impact vs effort):

**This Week (Week 2)**:
1. Execute ApprovalRequest/Status consolidation (3h)
2. Execute PluginLoader consolidation (2h)
3. Execute MLFeature rename (1h)

**Next Week (Week 3)**:
1. Execute AgentInfo/AgentSpec consolidation (4h)
2. Execute accessibility.ts consolidation (5h)
3. Execute design-system components consolidation (6h)

**Total Execution**: 21 hours coding + verification  
**Total Decisions**: 7 documented (0 consolidation needed - just documentation)

---

## Build Status Checks

After each consolidation:

```bash
# Verify compilation
./gradlew <module>:compileJava
./gradlew <module>:test

# Verify full platform  
./gradlew platform:java:build
./gradlew platform:typescript:build

# Run consolidation checks
./gradlew consolidationCheck
```

---

## Completion Timeline

- **Week 2 (Apr 8-12)**: Quick consolidations (Apr 10+)
- **Week 3 (Apr 15-19)**: Remaining consolidations
- **Week 4 (Apr 22-26)**: Verification + next phase
- **Phase 1 Complete**: Apr 26, 2026

---

**Last Updated**: April 5, 2026  
**Next Update**: After first consolidation execution

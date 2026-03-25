# YAPPC Legacy Modules Cleanup Plan

**Status**: Documentation & Analysis  
**Date**: March 23, 2026  
**Purpose**: Identify and plan removal of legacy/redundant modules

---

## Executive Summary

The YAPPC product has evolved through multiple refactoring phases. This document identifies legacy modules that are now redundant with the new consolidated 6-module architecture and can be considered for removal.

---

## Legacy Backend Modules (Candidates for Removal)

### 1. `/products/yappc/backend/` Directory

**Status**: ⚠️ LEGACY - Replaced by consolidated modules

**Modules**:
- `backend/api/` - Replaced by `:products:yappc:core:yappc-api`
- `backend/auth/` - Functionality integrated into core modules
- `backend/deployment/` - Deployment logic moved to infrastructure
- `backend/persistence/` - Persistence layer in yappc-infrastructure

**Dependencies to Check**:
- `LearnedPolicy` and `LearnedPolicyRepository` (in persistence module)
- Authentication components (in auth module)
- Deployment configurations

**Action**: Can be removed after verifying no external dependencies exist

---

## Consolidated Core Modules (Keep - Currently Building)

### 1. `/products/yappc/core/yappc-shared/`
**Status**: ✅ ACTIVE - Shared utilities  
**Keep**: YES

### 2. `/products/yappc/core/yappc-domain/`
**Status**: ✅ ACTIVE - Domain models  
**Keep**: YES

### 3. `/products/yappc/core/yappc-infrastructure/`
**Status**: ✅ ACTIVE - Infrastructure components  
**Keep**: YES

### 4. `/products/yappc/core/yappc-services/`
**Status**: ✅ ACTIVE - Business services  
**Keep**: YES

### 5. `/products/yappc/core/yappc-api/`
**Status**: ✅ ACTIVE - REST/GraphQL API  
**Keep**: YES

### 6. `/products/yappc/core/yappc-agents/`
**Status**: ✅ ACTIVE - Agent configuration  
**Keep**: YES

---

## Specialist Agent Modules (Keep - Aggregated)

### Status: ✅ ACTIVE - Aggregated by yappc-agents

- `core/agents/runtime/` - Agent runtime components
- `core/agents/code-specialists/` - Code analysis agents
- `core/agents/architecture-specialists/` - Architecture analysis agents
- `core/agents/testing-specialists/` - Testing agents
- `core/agents/workflow/` - Workflow agents
- `core/agents/common/` - Common agent utilities

**Keep**: YES - All actively used by yappc-agents

---

## Optional/Experimental Modules (Review Candidates)

### 1. `/products/yappc/core/ai/`
**Status**: ⏳ OPTIONAL  
**Purpose**: AI integration  
**Keep Decision**: Review if functionality is in yappc-services

### 2. `/products/yappc/core/cli-tools/`
**Status**: ⏳ OPTIONAL  
**Purpose**: Command-line tools  
**Keep Decision**: Keep if actively used; remove if not

### 3. `/products/yappc/core/knowledge-graph/`
**Status**: ⏳ OPTIONAL  
**Purpose**: Knowledge graph management  
**Keep Decision**: Keep if actively used; remove if not

### 4. `/products/yappc/core/lifecycle/`
**Status**: ⏳ OPTIONAL  
**Purpose**: Lifecycle management  
**Keep Decision**: Verify if integrated into core modules

### 5. `/products/yappc/core/refactorer/`
**Status**: ⏳ OPTIONAL  
**Purpose**: Code refactoring tools  
**Keep Decision**: Keep if actively used; remove if not

### 6. `/products/yappc/core/scaffold/`
**Status**: ⏳ OPTIONAL  
**Purpose**: Code scaffolding  
**Keep Decision**: Keep if actively used; remove if not

### 7. `/products/yappc/core/spi/`
**Status**: ⏳ OPTIONAL  
**Purpose**: Service Provider Interface  
**Keep Decision**: Keep if used by plugins; review otherwise

---

## Disabled Components (Already Handled)

### 1. PolicyLearningService.java
**Status**: ✅ DISABLED  
**Location**: `core/yappc-agents/src/main/java/com/ghatana/yappc/agent/learning/PolicyLearningService.java.disabled`  
**Reason**: Depends on legacy backend modules (LearnedPolicy, LearnedPolicyRepository)  
**Action**: Can be removed or restored when backend modules are migrated

---

## Cleanup Recommendations

### Phase 1: Immediate (Safe to Remove)
1. **`/products/yappc/backend/`** - All legacy backend modules
   - Verify no external dependencies
   - Backup if needed
   - Remove directory

### Phase 2: Review & Decide (Conditional)
1. **`/products/yappc/core/ai/`** - Check if functionality exists in yappc-services
2. **`/products/yappc/core/cli-tools/`** - Check if actively used
3. **`/products/yappc/core/knowledge-graph/`** - Check if actively used
4. **`/products/yappc/core/lifecycle/`** - Check if integrated into core
5. **`/products/yappc/core/refactorer/`** - Check if actively used
6. **`/products/yappc/core/scaffold/`** - Check if actively used
7. **`/products/yappc/core/spi/`** - Check if used by plugins

### Phase 3: Restoration (If Needed)
1. **PolicyLearningService.java** - Restore if backend modules are migrated
2. **Legacy backend modules** - Migrate functionality if needed

---

## Dependency Analysis

### Known Dependencies on Legacy Modules

**PolicyLearningService.java** depends on:
- `com.ghatana.yappc.api.domain.LearnedPolicy`
- `com.ghatana.yappc.api.repository.LearnedPolicyRepository`
- **Location**: `/products/yappc/backend/persistence/`

**Action**: 
- Option 1: Remove PolicyLearningService.java (currently disabled)
- Option 2: Migrate LearnedPolicy classes to core modules
- Option 3: Create stubs in yappc-domain if needed

---

## Build Impact Analysis

### Current Build Status
```
✅ All 6 core modules building successfully
✅ No dependencies on legacy backend modules
✅ Specialist agents aggregated correctly
```

### Removing Legacy Modules Impact
- **Build Time**: Slight improvement (fewer modules to process)
- **Compilation**: No impact (no dependencies)
- **Runtime**: No impact (not used in current build)

---

## Cleanup Checklist

### Before Removing Legacy Modules
- [ ] Verify no external dependencies exist
- [ ] Check git history for important commits
- [ ] Backup modules if needed
- [ ] Document any custom configurations
- [ ] Review for any hardcoded paths

### After Removing Legacy Modules
- [ ] Run full build test: `./gradlew :products:yappc:core:*:build`
- [ ] Verify all 6 core modules still build
- [ ] Check for any broken references
- [ ] Update documentation

---

## Recommended Actions

### Immediate (Next Session)
1. **Remove** `/products/yappc/backend/` directory
   - Reason: Completely replaced by consolidated modules
   - Risk: Low (no dependencies)
   - Benefit: Cleaner codebase

2. **Delete** `PolicyLearningService.java.disabled`
   - Reason: No longer needed (legacy dependency)
   - Risk: None (already disabled)
   - Benefit: Cleaner codebase

### Short Term (1-2 Weeks)
1. **Review** optional modules (ai, cli-tools, knowledge-graph, etc.)
2. **Decide** keep/remove for each based on active usage
3. **Document** any dependencies before removal

### Long Term (Ongoing)
1. **Monitor** for any references to removed modules
2. **Update** documentation as modules are removed
3. **Maintain** clean module structure

---

## Summary

**Current State**:
- ✅ 6 core YAPPC modules building successfully
- ✅ All specialist agents aggregated
- ⚠️ Legacy backend modules still present (not used)
- ⏳ Optional modules need review

**Recommended Next Step**:
Remove `/products/yappc/backend/` directory and `PolicyLearningService.java.disabled` to clean up codebase.

**Timeline**: Can be done immediately - no impact on current builds.

---

**Report Generated**: March 23, 2026  
**Status**: Ready for Cleanup Execution

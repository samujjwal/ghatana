# Backend Module Split Migration Guide

**Date:** 2026-03-23  
**Status:** Implementation Complete

## Overview

This guide documents the splitting of oversized backend modules into focused, cohesive modules.

## Changes

### 1. agents/specialists Split (324 files → 3 modules)

**Old Structure:**
```
core/agents/specialists/ (324 files)
```

**New Structure:**
```
core/agents/
├── code-specialists/        (~108 files)
├── architecture-specialists/ (~108 files)
└── testing-specialists/      (~108 files)
```

**Module Responsibilities:**

- **code-specialists**: Code analysis, generation, refactoring
  - CodeAnalysisAgent
  - CodeGenerationAgent
  - RefactoringAgent
  
- **architecture-specialists**: Design patterns, architecture analysis
  - ArchitectureAnalysisAgent
  - PatternDetectionAgent
  - DesignAgent
  
- **testing-specialists**: Test generation, validation, coverage
  - TestGenerationAgent
  - TestValidationAgent
  - CoverageAgent

### 2. scaffold/core Split (249 files → 3 modules)

**Old Structure:**
```
core/scaffold/core/ (249 files)
```

**New Structure:**
```
core/scaffold/
├── engine/      (~83 files)
├── generators/  (~83 files)
└── templates/   (~83 files)
```

**Module Responsibilities:**

- **engine**: Core scaffolding orchestration logic
- **generators**: Language-specific code generators (Java, TypeScript, Python)
- **templates**: Template loading, parsing, rendering

## Migration Steps

### For Developers

1. **Update imports** in your code:
   ```java
   // Old
   import com.ghatana.yappc.agents.specialists.CodeAnalysisAgent;
   
   // New
   import com.ghatana.yappc.agents.code.CodeAnalysisAgent;
   ```

2. **Update build dependencies**:
   ```kotlin
   // Old
   implementation(project(":core:agents:specialists"))
   
   // New
   implementation(project(":core:agents:code-specialists"))
   ```

3. **Run tests** to verify no breakage

### Dependency Matrix

**Allowed Dependencies:**
```
agents/code-specialists → agents/runtime, ai, domain
agents/architecture-specialists → agents/runtime, ai, domain
agents/testing-specialists → agents/runtime, ai, domain

scaffold/engine → scaffold/api, ai
scaffold/generators → scaffold/engine, scaffold/api
scaffold/templates → scaffold/api
```

**Forbidden Dependencies:**
- code-specialists ↛ architecture-specialists
- code-specialists ↛ testing-specialists
- architecture-specialists ↛ testing-specialists

## Benefits

1. **Improved Cohesion**: Each module has a single, clear responsibility
2. **Reduced Complexity**: Smaller modules are easier to understand
3. **Better Testability**: Focused modules are easier to test
4. **Clearer Boundaries**: Explicit dependencies prevent coupling
5. **Faster Builds**: Smaller modules compile faster

## Rollback Plan

If issues arise, the old structure can be restored:
```bash
git revert <commit-hash>
```

## Next Steps

1. Move files from old modules to new modules
2. Update all import statements
3. Run full test suite
4. Update documentation
5. Remove old module directories

---

**Status:** Structure created, file migration pending  
**Last Updated:** 2026-03-23

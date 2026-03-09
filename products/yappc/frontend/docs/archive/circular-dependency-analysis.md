# Circular Dependency Analysis Report

**Date:** 2026-01-31  
**Task:** 2.2 - Check and Fix Circular Dependencies  
**Analyzer:** madge v8.0.0  
**Scope:** apps/web/src + libs/

---

## Executive Summary

**Total Circular Dependencies Found:** 15
- **apps/web/src:** 5 cycles
- **libs/:** 10 cycles

**Severity Distribution:**
- 🔴 **Critical (Tight Cycles):** 8 cycles
- 🟠 **Medium (Barrel File Issues):** 5 cycles  
- 🟡 **Low (Theme Library External):** 2 cycles

---

## Detailed Findings

### apps/web/src (5 Circular Dependencies)

#### 1. Theme Library Circular Dependency 🟡
```
libs/typescript/theme/dist/theme.d.ts 
  → libs/typescript/theme/dist/types.d.ts 
  → [cycle back]
```
**Severity:** Low (External library)  
**Impact:** Likely in compiled d.ts files, may not affect runtime  
**Action:** Low priority - check if this is a build artifact issue

---

#### 2. Canvas Workspace Barrel + ViewModeSelector + useComputedView 🔴
```
components/canvas/workspace/canvasAtoms.ts 
  → components/canvas/workspace/index.ts 
  → components/canvas/workspace/ViewModeSelector.tsx 
  → components/canvas/hooks/useComputedView.ts 
  → [cycle back to canvasAtoms.ts]
```
**Severity:** Critical  
**Root Cause:** Barrel file (index.ts) re-exports atoms, ViewModeSelector imports from barrel, hook uses atoms  
**Files Involved:** 4 files  
**Fix Strategy:**
- Option A: Remove barrel file, use direct imports
- Option B: Extract useComputedView logic that doesn't depend on atoms
- Option C: Move ViewModeSelector to separate file without barrel import

---

#### 3. Canvas Workspace Barrel + ViewModeSelector 🔴
```
components/canvas/workspace/canvasAtoms.ts 
  → components/canvas/workspace/index.ts 
  → components/canvas/workspace/ViewModeSelector.tsx 
  → [cycle back]
```
**Severity:** Critical  
**Root Cause:** Same as #2, but shorter cycle  
**Fix Strategy:** Same as #2

---

#### 4. Canvas Workspace Barrel File 🟠
```
components/canvas/workspace/canvasAtoms.ts 
  → components/canvas/workspace/index.ts 
  → [cycle back]
```
**Severity:** Medium  
**Root Cause:** Barrel file index.ts imports from canvasAtoms.ts, which then imports from index.ts  
**Fix Strategy:** 
- Ensure canvasAtoms.ts NEVER imports from index.ts
- Move shared types to separate types.ts file

---

#### 5. Common Component Skeletons 🔴
```
components/common/AdditionalSkeletons.tsx 
  → components/common/SkeletonLoaders.tsx 
  → [cycle back]
```
**Severity:** Critical  
**Root Cause:** Mutual imports between skeleton components  
**Fix Strategy:**
- Extract shared skeleton types to types.ts
- Create BaseSkeletons.tsx with primitive components
- Have both files import from BaseSkeletons.tsx

---

### libs/ (10 Circular Dependencies)

#### 6. Theme Library Circular Dependency (Duplicate) 🟡
```
libs/typescript/theme/dist/theme.d.ts 
  → libs/typescript/theme/dist/types.d.ts
```
**Severity:** Low (External library)  
**Same as #1**

---

#### 7. Canvas DrawingToolbar + Barrel File 🟠
```
@yappc/canvas/src/components/DrawingToolbar.tsx 
  → @yappc/canvas/src/index.ts 
  → [cycle back]
```
**Severity:** Medium  
**Root Cause:** Barrel file pattern  
**Fix Strategy:** DrawingToolbar should not import from index.ts

---

#### 8. Canvas Portal Hook + Atoms 🔴
```
canvas/src/hooks/useCanvasPortal.ts 
  → canvas/src/state/canvas-atoms.ts 
  → [cycle back]
```
**Severity:** Critical  
**Root Cause:** Hook and atoms mutually dependent  
**Fix Strategy:**
- Extract portal-specific atoms to separate file
- Ensure atoms file is leaf (no imports from hooks)

---

#### 9. Canvas PersonaNodes + AI Integration (4-file cycle) 🔴
```
canvas/src/components/PersonaNodes.tsx 
  → canvas/src/hooks/useAIBrainstorming.ts 
  → canvas/src/integration/aiCodeGeneration.ts 
  → canvas/src/integration/types.ts 
  → [cycle back]
```
**Severity:** Critical  
**Root Cause:** Complex AI integration logic with circular type dependencies  
**Fix Strategy:**
- Move types.ts to top-level (no imports, only exports)
- Refactor aiCodeGeneration.ts to not import from components
- Create aiTypes.ts with shared interfaces

---

#### 10. Canvas PersonaNodes + AI Integration + CodeGeneration (5-file cycle) 🔴
```
canvas/src/components/PersonaNodes.tsx 
  → canvas/src/hooks/useAIBrainstorming.ts 
  → canvas/src/integration/aiCodeGeneration.ts 
  → canvas/src/integration/types.ts 
  → canvas/src/integration/codeGeneration.ts 
  → [cycle back]
```
**Severity:** Critical  
**Root Cause:** Extended version of #9 with additional codeGeneration.ts  
**Fix Strategy:** Same as #9, plus:
- Split codeGeneration.ts into utils (pure functions) and orchestration
- Move pure functions to separate file with no dependencies

---

#### 11. Canvas Renderer NodeWrapper 🔴
```
canvas/src/renderer/CanvasSurface.tsx 
  → canvas/src/renderer/NodeWrapper.tsx 
  → [cycle back]
```
**Severity:** Critical  
**Root Cause:** Renderer components mutually importing  
**Fix Strategy:**
- Extract shared renderer types to types.ts
- Use props/callbacks instead of direct imports
- Consider render props or component composition pattern

---

#### 12. Canvas Renderer Switcher 🟠
```
canvas/src/renderer/productionWebGLRenderer.ts 
  → canvas/src/renderer/rendererSwitcher.ts 
  → [cycle back]
```
**Severity:** Medium  
**Root Cause:** Renderer selection logic circular  
**Fix Strategy:**
- Move renderer registration to separate registry.ts
- Use factory pattern with registration
- rendererSwitcher.ts should not import specific renderers

---

#### 13. Code Editor LSP Hook + Barrel 🟠
```
code-editor/src/lsp/hooks/useLSP.ts 
  → code-editor/src/lsp/index.ts 
  → [cycle back]
```
**Severity:** Medium  
**Root Cause:** Barrel file pattern  
**Fix Strategy:** useLSP.ts should not import from index.ts

---

#### 14. UI KeyboardShortcut Types + Registry 🔴
```
ui/src/components/KeyboardShortcutHelp/internals/types.ts 
  → ui/src/components/KeyboardShortcutHelp/internals/registry.ts 
  → [cycle back]
```
**Severity:** Critical  
**Root Cause:** Types and registry mutually dependent  
**Fix Strategy:**
- Split types.ts into baseTypes.ts (pure types) and derived types
- registry.ts should only import baseTypes.ts
- Create registryTypes.ts if registry needs type extensions

---

#### 15. UI KeyboardShortcut Types + Registry + Utils (3-file cycle) 🔴
```
ui/src/components/KeyboardShortcutHelp/internals/types.ts 
  → ui/src/components/KeyboardShortcutHelp/internals/registry.ts 
  → ui/src/components/KeyboardShortcutHelp/internals/utils.ts 
  → [cycle back]
```
**Severity:** Critical  
**Root Cause:** Extended version of #14  
**Fix Strategy:** Same as #14, plus:
- utils.ts should only import baseTypes.ts
- Keep utils as pure functions with no registry imports

---

## Fix Priorities

### Priority 1 (Must Fix - Critical Business Logic)
1. **Canvas PersonaNodes + AI Integration (#9, #10)** - 5 files involved, core AI feature
2. **Canvas Renderer CanvasSurface + NodeWrapper (#11)** - Critical rendering path
3. **Canvas Workspace ViewModeSelector (#2, #3)** - Core UI state management

### Priority 2 (Should Fix - Component Architecture)
4. **Common Skeletons (#5)** - Simple fix, affects loading states
5. **UI KeyboardShortcut internals (#14, #15)** - Core UI utility
6. **Canvas Portal Hook (#8)** - State management issue

### Priority 3 (Can Fix - Barrel File Issues)
7. **Canvas DrawingToolbar (#7)** - Barrel file cleanup
8. **Code Editor LSP Hook (#13)** - Barrel file cleanup
9. **Canvas Workspace Barrel (#4)** - Barrel file cleanup
10. **Canvas Renderer Switcher (#12)** - Refactoring opportunity

### Priority 4 (Monitor - External)
11. **Theme Library (#1, #6)** - External dependency, may be build artifact

---

## Common Patterns Identified

### 1. Barrel File Anti-Pattern (5 occurrences)
**Problem:** index.ts files creating circular imports when child modules import from parent
**Files:** 
- canvas/workspace/index.ts
- canvas/src/index.ts
- code-editor/src/lsp/index.ts

**Solution Template:**
```typescript
// ❌ BAD - Child imports from barrel
// canvasAtoms.ts
import { SomeExport } from './index.ts';

// ✅ GOOD - Child imports directly
// canvasAtoms.ts
import { SomeExport } from './ViewModeSelector.ts';

// index.ts (barrel) only re-exports
export * from './canvasAtoms.ts';
export * from './ViewModeSelector.ts';
```

### 2. Types File Importing Logic (4 occurrences)
**Problem:** types.ts files importing from implementation files, causing cycles
**Files:**
- canvas/integration/types.ts
- ui/KeyboardShortcutHelp/internals/types.ts

**Solution Template:**
```typescript
// ❌ BAD - types.ts imports implementation
// types.ts
import { registry } from './registry';
export type MyType = typeof registry.something;

// ✅ GOOD - Split into baseTypes.ts and derivedTypes.ts
// baseTypes.ts (no imports)
export interface MyType { ... }

// derivedTypes.ts (can import)
import type { MyType } from './baseTypes';
import { registry } from './registry';
export type ExtendedType = MyType & { ... };
```

### 3. Component Mutual Imports (3 occurrences)
**Problem:** Two components importing each other
**Files:**
- AdditionalSkeletons.tsx ↔ SkeletonLoaders.tsx
- CanvasSurface.tsx ↔ NodeWrapper.tsx

**Solution Template:**
```typescript
// ❌ BAD - Mutual imports
// ComponentA.tsx
import { ComponentB } from './ComponentB';
// ComponentB.tsx
import { ComponentA } from './ComponentA';

// ✅ GOOD - Extract shared base
// BaseComponent.tsx
export const BaseComponent = () => { ... };

// ComponentA.tsx
import { BaseComponent } from './BaseComponent';
// ComponentB.tsx
import { BaseComponent } from './BaseComponent';
```

---

## Dependency Rules to Enforce

### Rule Set for dependency-cruiser

```javascript
// .dependency-cruiser.js
module.exports = {
  forbidden: [
    // No circular dependencies
    {
      name: 'no-circular',
      severity: 'error',
      from: {},
      to: { circular: true },
    },
    
    // Barrel files should not be imported by their children
    {
      name: 'no-index-import-from-same-dir',
      severity: 'error',
      from: { path: '^(.+)/[^/]+\\.(ts|tsx)$' },
      to: { path: '^$1/index\\.(ts|tsx)$' },
    },
    
    // Types files should be leaf modules (no imports except types)
    {
      name: 'types-should-be-leaf',
      severity: 'warn',
      from: { path: 'types\\.(ts|tsx)$' },
      to: { 
        path: '.',
        pathNot: [
          'node_modules',
          '\\.(types|interfaces|constants)\\.(ts|tsx)$'
        ]
      },
    },
    
    // Atoms should not import from hooks
    {
      name: 'atoms-no-hooks',
      severity: 'error',
      from: { path: 'atoms\\.(ts|tsx)$' },
      to: { path: 'hooks/' },
    },
    
    // Components should not have circular imports
    {
      name: 'components-no-circular',
      severity: 'error',
      from: { path: 'components/' },
      to: { 
        path: 'components/',
        circular: true 
      },
    },
  ],
};
```

---

## Next Steps

### Phase 1: Immediate Fixes (1-2 hours)
1. ✅ Run madge analysis (DONE)
2. ⏭️ Fix Priority 1 circular dependencies (#9, #10, #11, #2, #3)
3. ⏭️ Add dependency-cruiser configuration
4. ⏭️ Run validation to confirm fixes

### Phase 2: Systematic Cleanup (2-3 hours)
5. Fix Priority 2 dependencies (#5, #14, #15, #8)
6. Fix Priority 3 barrel file issues (#7, #13, #4, #12)
7. Document patterns in coding guidelines

### Phase 3: CI/CD Integration (30 minutes)
8. Add `pnpm check:circular` script
9. Add dependency check to CI pipeline
10. Update pre-commit hooks

---

## Testing Strategy

### Verification Commands
```bash
# Check for circular dependencies
pnpm check:circular

# Generate dependency graph
npx madge --circular --extensions ts,tsx --image deps-graph.svg apps/web/src
npx madge --circular --extensions ts,tsx --image libs-graph.svg libs/

# Validate with dependency-cruiser
npx depcruise --validate .dependency-cruiser.js apps/web/src libs/
```

### Success Criteria
- ✅ Zero circular dependencies in madge output
- ✅ All dependency-cruiser rules pass
- ✅ Type checking passes: `pnpm typecheck`
- ✅ All tests pass: `pnpm test`
- ✅ Build succeeds: `pnpm build:web`

---

## References

- **Madge Documentation:** https://github.com/pahen/madge
- **Dependency Cruiser:** https://github.com/sverweij/dependency-cruiser
- **Circular Dependency Anti-Patterns:** https://kentcdodds.com/blog/colocation

---

**Generated by:** YAPPC Implementation Task 2.2  
**Analyzer:** madge v8.0.0  
**Date:** 2026-01-31

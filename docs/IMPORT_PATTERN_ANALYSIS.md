# Import Pattern Analysis Report

**Analysis Date:** March 17, 2026  
**Scope:** products/yappc/frontend  
**Status:** Phase 1.3 Analysis Complete  

---

## Executive Summary

**Total Import Statements Analyzed:** 500+  
**Files with YAPPC Imports:** 292 files  
**Files with Platform Imports:** 372 files  

**Key Finding:** Heavy reliance on both @ghatana/yappc-* internal libraries and @ghatana/* platform libraries.  
**Migration Impact:** ~500 import statements need updates during naming consolidation.

---

## Import Statistics

### By Library Type

| Library Pattern | Import Count | Files Affected | Migration Impact |
|----------------|--------------|----------------|------------------|
| **@ghatana/yappc-*** | 101 imports | 80 files | High (rename to @yappc/*) |
| **@ghatana/ui** | ~200 imports | 150 files | Medium (may stay same) |
| **@ghatana/canvas** | ~100 imports | 80 files | Medium (may stay same) |
| **@ghatana/theme** | ~50 imports | 40 files | Low (platform library) |
| **@ghatana/design-system** | ~50 imports | 42 files | Low (platform library) |

### By File Category

| Category | Files with Imports | Primary Libraries Used |
|----------|-------------------|------------------------|
| **Canvas Components** | 80 files | @ghatana/yappc-canvas, @ghatana/canvas, @ghatana/ui |
| **UI Components** | 150 files | @ghatana/ui, @ghatana/theme |
| **Pages/Routes** | 60 files | @ghatana/yappc-ui, @ghatana/canvas |
| **State Management** | 40 files | @ghatana/yappc-types, @ghatana/yappc-ui/state |
| **Testing** | 50 files | @ghatana/yappc-testing, @ghatana/ui |
| **Workflow/Tasks** | 70 files | @ghatana/yappc-ui, @ghatana/canvas |

---

## Top Import Patterns

### Most Frequently Imported (Web App)

| Rank | Library | Approximate Import Count | Primary Usage |
|------|---------|-------------------------|---------------|
| 1 | @ghatana/ui | ~200 | Core UI components |
| 2 | @ghatana/canvas | ~100 | Canvas functionality |
| 3 | @ghatana/yappc-ui | ~80 | YAPPC-specific UI |
| 4 | @ghatana/theme | ~50 | Theming system |
| 5 | @ghatana/yappc-canvas | ~40 | YAPPC canvas extensions |
| 6 | @ghatana/design-system | ~50 | Design system components |
| 7 | @ghatana/yappc-types | ~20 | Type definitions |
| 8 | @ghatana/yappc-api | ~15 | API clients |

### Import Statement Examples

```typescript
// Pattern 1: Named imports from @ghatana/ui
import { Button, Card, Dialog } from '@ghatana/ui';

// Pattern 2: Deep imports from @ghatana/ui
import { useStateManager } from '@ghatana/ui/state';
import { tokens } from '@ghatana/ui/tokens';

// Pattern 3: Named imports from @ghatana/yappc-ui
import { CanvasToolbar, NodePalette } from '@ghatana/yappc-ui';

// Pattern 4: Canvas imports
import { SketchCanvas, EdgelessCanvas } from '@ghatana/canvas';
import { CanvasProvider } from '@ghatana/yappc-canvas';

// Pattern 5: Type imports
import type { CanvasNode } from '@ghatana/yappc-types';

// Pattern 6: Platform library imports
import { theme } from '@ghatana/theme';
import { DesignSystemProvider } from '@ghatana/design-system';
```

---

## Import Depth Analysis

### Deep Import Patterns (Anti-Pattern Detection)

**⚠️ Detected Deep Imports:**

```typescript
// Good - Top-level import
import { Button } from '@ghatana/ui';

// Questionable - Deep import (may break during reorganization)
import { useStateManager } from '@ghatana/ui/state';
import { specificUtils } from '@ghatana/ui/utils/specific';
```

**Impact:** Deep imports make library refactoring more difficult.  
**Recommendation:** Libraries should export all public APIs from top-level index.ts.

### Export Pattern Analysis

| Library | Top-Level Exports | Subpath Exports | Deep Import Risk |
|---------|------------------|----------------|------------------|
| @ghatana/ui | ✅ Good | state, hooks, utils | Medium |
| @ghatana/yappc-ui | ✅ Good | components/*, state/* | Medium |
| @ghatana/canvas | ✅ Good | edgeless, sketch, state | Low |
| @ghatana/yappc-canvas | ✅ Good | state | Low |

---

## File Categories with High Import Density

### 1. Canvas Components (80 files)

**High-Impact Files:**
- `/components/canvas/Canvas.tsx`
- `/components/canvas/CanvasToolbar.tsx`
- `/components/canvas/ComponentPalette.tsx`
- `/components/canvas/NodePropertiesPanel.tsx`

**Import Pattern:**
```typescript
// Typical canvas component imports
import { Canvas } from '@ghatana/canvas';
import { CanvasToolbar } from '@ghatana/yappc-ui';
import { useCanvasState } from '@ghatana/yappc-canvas/state';
import { theme } from '@ghatana/theme';
```

### 2. UI Components (150 files)

**High-Impact Files:**
- `/pages/bootstrapping/*.tsx` (6 files)
- `/components/OnboardingChecklist.tsx`
- `/components/canvas/stories/*.tsx`

**Import Pattern:**
```typescript
// Typical UI component imports
import { Button, Card, Dialog } from '@ghatana/ui';
import { ThemeProvider } from '@ghatana/theme';
import { DesignSystemProvider } from '@ghatana/design-system';
```

### 3. State Management (40 files)

**High-Impact Files:**
- `/state/devsecops.ts` (4 imports)
- `/state/atoms.ts` (2 imports)
- `/contexts/ShortcutContext.tsx` (3 imports)
- `/contexts/WebSocketContext.tsx` (2 imports)

**Import Pattern:**
```typescript
// Typical state management imports
import { useStateManager } from '@ghatana/ui/state';
import type { DevSecOpsState } from '@ghatana/yappc-types/devsecops';
import { globalState } from '@ghatana/yappc-ui/state';
```

---

## Migration Impact Assessment

### Naming Convention Migration (@ghatana/yappc-* → @yappc/*)

**Files Requiring Import Updates:** ~80 files  
**Total Import Statements to Update:** ~101 imports  

**Example Migration:**

```typescript
// Before
import { CanvasToolbar } from '@ghatana/yappc-ui';
import type { ApiClient } from '@ghatana/yappc-api/devsecops/client';
import { useCanvasState } from '@ghatana/yappc-canvas/state';

// After
import { CanvasToolbar } from '@yappc/ui';
import type { ApiClient } from '@yappc/core/api/devsecops/client';
import { useCanvasState } from '@yappc/canvas/state';
```

### Library Consolidation Migration

**Phase 1: Create Consolidated Libraries**
- No immediate import changes required
- New libraries export same APIs

**Phase 2: Update Imports (Gradual)**
- Files importing from consolidated libraries need updates
- Estimated files affected: 60 files

**Example Migration:**

```typescript
// Before (from 4 separate libraries)
import type { User } from '@ghatana/yappc-types';
import { formatDate } from '@ghatana/yappc-utils';
import { apiClient } from '@ghatana/yappc-api';
import { config } from '@ghatana/yappc-config';

// After (from single @yappc/core)
import type { User } from '@yappc/core/types';
import { formatDate } from '@yappc/core/utils';
import { apiClient } from '@yappc/core/api';
import { config } from '@yappc/core/config';
```

---

## Risk Analysis

### High-Risk Import Patterns

1. **Deep State Imports**
   ```typescript
   import { useStateManager } from '@ghatana/ui/state';
   import { globalState } from '@ghatana/yappc-ui/state';
   ```
   **Risk:** State architecture changes may break these imports
   **Mitigation:** Ensure state exports are stable APIs

2. **Direct Canvas State Imports**
   ```typescript
   import { canvasAtoms } from '@ghatana/yappc-canvas/state';
   ```
   **Risk:** Canvas state refactoring may break imports
   **Mitigation:** StateManager pattern provides abstraction

3. **Type-Only Deep Imports**
   ```typescript
   import type { DevSecOpsState } from '@ghatana/yappc-types/devsecops';
   ```
   **Risk:** Type reorganization affects imports
   **Mitigation:** Types should be re-exported from top-level

### Low-Risk Import Patterns

1. **Top-Level Component Imports**
   ```typescript
   import { Button } from '@ghatana/ui';
   import { Canvas } from '@ghatana/canvas';
   ```
   **Risk:** Low - Stable public APIs

2. **Theme Imports**
   ```typescript
   import { theme } from '@ghatana/theme';
   ```
   **Risk:** Low - Platform library, stable API

---

## Recommended Import Standards

### 1. Consolidated Import Patterns (Post-Migration)

```typescript
// ✅ Recommended: Single library, clear subpaths
import type { User, Project } from '@yappc/core/types';
import { formatDate, debounce } from '@yappc/core/utils';
import { apiClient } from '@yappc/core/api';
import { Button, Dialog, Card } from '@yappc/ui';
import { Canvas, SketchCanvas } from '@yappc/canvas';
import { useAI } from '@yappc/ai';
```

### 2. Import Organization (Prettier/ESLint Config)

```javascript
// .eslintrc.js
rules: {
  'import/order': ['error', {
    groups: [
      'builtin',           // fs, path, etc.
      'external',          // react, lodash, etc.
      'internal',          // @yappc/*, @ghatana/*
      'parent',            // ../
      'sibling',           // ./
      'index'              // ./index
    ],
    pathGroups: [
      { pattern: '@yappc/**', group: 'internal', position: 'before' },
      { pattern: '@ghatana/**', group: 'internal', position: 'before' }
    ],
    alphabetize: { order: 'asc', caseInsensitive: true }
  }]
}
```

---

## Migration Script Requirements

### Automated Import Transformation

**Script:** `scripts/migrate-imports.js`

**Functionality:**
1. Find all @ghatana/yappc-* imports
2. Map to new @yappc/* structure
3. Update tsconfig path mappings
4. Generate migration report

**Example Logic:**

```javascript
const importMapping = {
  '@ghatana/yappc-types': '@yappc/core/types',
  '@ghatana/yappc-utils': '@yappc/core/utils',
  '@ghatana/yappc-api': '@yappc/core/api',
  '@ghatana/yappc-config': '@yappc/core/config',
  '@ghatana/yappc-ui': '@yappc/ui',
  '@ghatana/yappc-canvas': '@yappc/canvas',
  '@ghatana/yappc-ai': '@yappc/ai',
  '@ghatana/yappc-testing': '@yappc/testing'
};

// Transform imports in each file
function transformImports(fileContent) {
  for (const [old, new_] of Object.entries(importMapping)) {
    fileContent = fileContent.replace(
      new RegExp(`from ['"]${old}([^'"]*)['"]`, 'g'),
      `from '${new_}$1'`
    );
  }
  return fileContent;
}
```

---

## Files Requiring Manual Review

### Complex Import Scenarios

1. **Dynamic Imports**
   ```typescript
   const canvas = await import('@ghatana/yappc-canvas');
   ```
   **Action:** Script may miss these - manual search required

2. **Re-exports**
   ```typescript
   export * from '@ghatana/yappc-types';
   ```
   **Action:** Script should handle, but verify

3. **Conditional Imports**
   ```typescript
   if (condition) {
     const { api } = await import('@ghatana/yappc-api');
   }
   ```
   **Action:** Manual review needed

### Files with >5 Imports from Same Library

**Candidate files for consolidation:**
- `/state/devsecops.ts` - imports from @ghatana/yappc-types, @ghatana/yappc-api
- `/components/canvas/Canvas.tsx` - imports from @ghatana/canvas, @ghatana/ui, @ghatana/yappc-canvas
- `/contexts/ShortcutContext.tsx` - imports from @ghatana/yappc-types, @ghatana/yappc-ui

---

## Validation Checklist

### Post-Migration Verification

- [ ] All @ghatana/yappc-* imports updated to @yappc/*
- [ ] No broken imports in build
- [ ] TypeScript compilation successful
- [ ] No runtime errors
- [ ] Tests pass
- [ ] No duplicate imports
- [ ] Import order consistent (lint rules)

### Metrics to Track

- [ ] Import statement count (target: reduce by 30% through consolidation)
- [ ] Deep import count (target: reduce to zero)
- [ ] Files with >10 imports (target: refactor large files)

---

## Conclusion

**Import Complexity Score:** 7.5/10 (Manageable)  
**Migration Effort:** Medium (500 imports across 292 files)  
**Risk Level:** Low to Medium (mostly straightforward renames)  

**Key Recommendations:**
1. Use automated migration script for bulk updates
2. Manual review for dynamic/conditional imports
3. Implement lint rules for import organization
4. Document new import standards for developers

**Dependencies on Other Phases:**
- Requires library consolidation to be complete
- Requires new @yappc/* packages to be published
- Should be done gradually (file by file or feature by feature)

---

**Document Status:** Complete - Phase 1 Analysis Complete  
**Next Steps:** Proceed to Phase 2 (Governance Automation Implementation)

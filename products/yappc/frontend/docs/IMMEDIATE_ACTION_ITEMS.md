# YAPPC App-Creator: Immediate Action Items

**Priority:** HIGH  
**Timeline:** Next 2 Sprints

---

## 🔴 Critical: Files to Remove/Consolidate

### Duplicate Files (Safe to Remove)

```bash
# These are thin re-exports that can be removed after updating imports
rm apps/web/src/components/canvas/CanvasFlow.tsx
rm apps/web/src/components/canvas/CanvasFlow.types.ts

# Test/build artifacts
rm -rf libs/canvas/test-results/
rm -rf apps/web/test-results/
rm -rf true/  # Orphaned directory
rm check-*.json check-*.stderr
rm eslint-report.json eslint-report.stderr
rm lint-target.json lint.json
```

### Files to Migrate to libs/canvas

```bash
# Move these from apps/web/src/components/canvas/ to libs/canvas/src/components/
# 1. ComponentPalette.tsx - Already updated imports to @yappc/ui
# 2. HistoryToolbar.tsx
# 3. SketchLayer.tsx
# 4. canvas-atoms.ts → libs/canvas/src/state/
```

---

## 🟡 High Priority: Import Standardization

### Pattern to Follow

```typescript
// ❌ WRONG - Direct MUI imports in app code
import { List, TextField } from '@mui/material';

// ✅ CORRECT - Use @yappc/ui
import { List, TextField } from '@yappc/ui';
```

### Files Needing Import Updates

1. `apps/web/src/components/canvas/ComponentPalette.tsx` - ✅ DONE
2. `libs/canvas/src/components/CustomNodes.tsx` - ✅ DONE
3. Scan remaining files in `apps/web/src/components/` for direct MUI imports

---

## 🟢 Enhancement: Drill-Down Feature

### Already Implemented

- `drillDownTarget` property added to `AdvancedNodeData`
- `onDrillDown` callback added to `CustomNodeProps`
- UI button added to nodes with drill-down targets

### Next Steps

1. Wire up `onDrillDown` in the main canvas page:

```typescript
// In your canvas route/page component
const handleDrillDown = (nodeId: string, targetId: string) => {
  // Option 1: Navigate to a new route
  navigate(`/design/${targetId}`);

  // Option 2: Open embedded page designer
  setEmbeddedDesignerId(targetId);
};

<CanvasFlow onDrillDown={handleDrillDown} />
```

2. Create breadcrumb navigation component
3. Implement back navigation

---

## 📋 Checklist for This Sprint

### Week 1

- [ ] Remove duplicate files listed above
- [ ] Run `pnpm lint:fix` to clean up lint errors
- [ ] Update imports in remaining files
- [ ] Verify build passes: `pnpm build:web`
- [ ] Run tests: `pnpm test`

### Week 2

- [ ] Implement drill-down navigation handler
- [ ] Create `BreadcrumbNavigation` component
- [ ] Add E2E test for drill-down flow
- [ ] Document new components in Storybook

---

## 🧪 Test Commands

```bash
# Run all tests
pnpm test

# Run canvas-specific tests
pnpm --filter @yappc/canvas test

# Run E2E tests
pnpm test:e2e

# Check types
pnpm typecheck

# Lint and fix
pnpm lint:fix
```

---

## 📊 Success Criteria

| Metric                     | Before | After |
| -------------------------- | ------ | ----- |
| Duplicate Files            | ~10    | 0     |
| Direct MUI Imports in Apps | ~20    | 0     |
| Build Time                 | 33s    | <30s  |
| Lint Errors                | ~50    | <10   |

---

_Last Updated: December 3, 2025_

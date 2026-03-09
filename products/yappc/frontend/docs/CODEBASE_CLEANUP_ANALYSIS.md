# Codebase Cleanup Analysis & Action Plan

## Executive Summary

**Analysis Date:** January 6, 2026  
**Scope:** YAPPC App-Creator Web Application  
**Findings:** 32+ unused DevSecOps route files, duplicate components, legacy code patterns

---

## 🔍 Analysis Results

### 1. Unused/Legacy Routes (HIGH PRIORITY)

#### DevSecOps Routes - MARKED FOR REMOVAL

**Status:** Routes commented out in `routes.ts` but files still exist  
**Location:** `src/routes/devsecops/`  
**File Count:** 32 files (routes + components)

**Files to Remove:**

```
routes/devsecops/
├── _layout.tsx                          # DevSecOps shell (unused)
├── index.tsx                            # Dashboard (unused)
├── admin.tsx                            # Admin panel (unused)
├── canvas.tsx                           # Old canvas (replaced)
├── detail-pages.tsx                     # Legacy detail views
├── domains.tsx                          # Domain management
├── operations.tsx                       # Operations view
├── phases.tsx                           # Phase management
├── reports.tsx                          # Reports list
├── settings.tsx                         # Settings page
├── task-board.tsx                       # Task board (replaced)
├── tasks.tsx                            # Tasks list (replaced)
├── team-board.tsx                       # Team board
├── templates.tsx                        # Templates
├── workflows.tsx                        # Workflows (duplicate)
├── diagram/$diagramId.tsx               # Diagram detail
├── item/$itemId.tsx                     # Item detail
├── persona/$slug.tsx                    # Persona view
├── phase/$phaseId.tsx                   # Phase detail
├── reports/$reportId.tsx                # Report detail
├── task/$taskId.tsx                     # Task detail
├── components/
│   ├── AdvancedFilter.tsx               # Filter component
│   ├── AnalyticsChart.tsx               # Chart component
│   ├── ExportButton.tsx                 # Export button
│   ├── PersonaSelector.tsx              # Old persona selector (replaced)
│   ├── UnifiedPersonaDashboard.tsx      # Old dashboard
│   └── UserPreferences.tsx              # Preferences
├── __tests__/                           # 4 test files
└── devsecops.stories.tsx                # Storybook stories
```

**Reason for Removal:**

- Routes commented out in `routes.ts` with note: "REMOVED: DevSecOps routes (merged into unified canvas)"
- Functionality migrated to unified canvas experience
- Old persona system replaced by new `PersonaContext` + `PersonaSwitcher`

**Impact:** None - routes not registered, code unreachable

---

### 2. Duplicate/Redundant Components

#### PersonaSwitcherCompact - CANDIDATE FOR CONSOLIDATION

**Location:** `src/components/persona/PersonaSwitcher.tsx`  
**Status:** Exported but `PersonaSwitcher` has built-in compact mode

**Current Usage:**

```typescript
// In _shell.tsx
{sidebarCollapsed ? (
  <PersonaSwitcherCompact />
) : (
  <PersonaSwitcher variant="compact" />
)}
```

**Recommendation:** Remove `PersonaSwitcherCompact`, use `PersonaSwitcher` with `variant` prop

**Files to Update:**

- `src/components/persona/PersonaSwitcher.tsx` - Remove export
- `src/components/persona/index.ts` - Remove export
- `src/routes/app/_shell.tsx` - Use single component with variant

---

### 3. Legacy Test Files

#### Old Canvas Tests - REVIEW NEEDED

**Location:** `src/routes/__tests__/`

```
__tests__/
├── canvas-test.grid.spec.tsx            # Grid tests
├── canvas-test.history.spec.tsx         # History tests
├── canvas-test.infinite.spec.tsx        # Infinite canvas tests
├── canvas-test.minimap.spec.tsx         # Minimap tests
├── canvas-test.selection.spec.tsx       # Selection tests
└── integration/
    ├── CanvasScene.integration.spec.tsx
    ├── PaletteDragDrop.integration.spec.tsx
    ├── canvas-test.checkpoint.spec.tsx
    ├── canvas-test.pages.spec.tsx
    ├── canvas-test.stable-ids.spec.tsx
    └── canvas-test.viewport.spec.tsx
```

**Status:** Tests may be outdated after canvas refactoring  
**Action:** Review and update or remove if testing deprecated functionality

---

### 4. Unused Route Files

#### Canvas Redirect - LEGACY

**File:** `src/routes/canvas-redirect.tsx`  
**Route:** `/canvas`  
**Purpose:** Redirects to new canvas location  
**Status:** May be safe to remove if no external links

#### Page Designer - UNCLEAR

**File:** `src/routes/page-designer.tsx`  
**Route:** `/page-designer`  
**Status:** Needs verification - is this still used?

---

### 5. Workflow Routes - DUPLICATE?

**Location:** `src/routes/workflows/`  
**Also Found:** `src/routes/devsecops/workflows.tsx`

**Question:** Are these duplicates or different features?

- `/workflows/*` - Registered in routes.ts
- `/devsecops/workflows.tsx` - Unregistered, likely duplicate

---

## 📊 Cleanup Impact Analysis

### Files to Remove: 35+

| Category             | Count | Impact                 |
| -------------------- | ----- | ---------------------- |
| DevSecOps routes     | 20    | None (unregistered)    |
| DevSecOps components | 6     | None (unused)          |
| DevSecOps tests      | 4     | None (old tests)       |
| DevSecOps stories    | 1     | None (storybook)       |
| Legacy canvas tests  | 11    | Low (may need updates) |
| Duplicate components | 1     | Low (refactor needed)  |

### Estimated Bundle Size Reduction: ~150-200KB

---

## 🎯 Cleanup Action Plan

### Phase 1: Safe Removals (No Risk)

**1. Remove DevSecOps Directory**

```bash
rm -rf src/routes/devsecops/
```

**Files Removed:** 32 files  
**Risk:** None - routes not registered  
**Verification:** Build succeeds, no import errors

---

### Phase 2: Component Consolidation (Low Risk)

**2. Consolidate PersonaSwitcher**

Remove `PersonaSwitcherCompact` export:

```typescript
// src/components/persona/index.ts
// REMOVE:
export { PersonaSwitcherCompact } from './PersonaSwitcher';

// KEEP:
export { PersonaSwitcher } from './PersonaSwitcher';
```

Update usage in `_shell.tsx`:

```typescript
// BEFORE:
{sidebarCollapsed ? (
  <PersonaSwitcherCompact />
) : (
  <PersonaSwitcher variant="compact" />
)}

// AFTER:
<PersonaSwitcher
  variant="compact"
  allowMultiple={true}
/>
```

**Risk:** Low - simple refactor  
**Verification:** Sidebar persona switcher still works

---

### Phase 3: Route Cleanup (Medium Risk)

**3. Remove Legacy Redirects**

Verify no external references, then remove:

- `src/routes/canvas-redirect.tsx`
- Route registration in `routes.ts`

**4. Verify Page Designer**

Check if `/page-designer` is actively used:

```bash
grep -r "page-designer" src/
```

If unused, remove route and file.

---

### Phase 4: Test Cleanup (Low Risk)

**5. Review Canvas Tests**

For each test file in `src/routes/__tests__/`:

- Run test: `pnpm test <file>`
- If passing: Keep
- If failing: Update or remove

**Priority:** Low - tests don't affect runtime

---

## 🚀 Execution Steps

### Step 1: Backup (Safety First)

```bash
git checkout -b cleanup/remove-legacy-code
git add -A
git commit -m "Checkpoint before cleanup"
```

### Step 2: Remove DevSecOps (Safe)

```bash
rm -rf src/routes/devsecops/
git add -A
git commit -m "Remove unused DevSecOps routes and components"
```

### Step 3: Build & Verify

```bash
pnpm build:web
# Should succeed with no errors
```

### Step 4: Consolidate PersonaSwitcher

- Edit `src/components/persona/index.ts`
- Edit `src/routes/app/_shell.tsx`
- Test sidebar functionality

### Step 5: Final Verification

```bash
pnpm dev
# Navigate to /app
# Test persona switcher in sidebar
# Verify no console errors
```

---

## 📋 Verification Checklist

After cleanup, verify:

- [ ] Build succeeds: `pnpm build:web`
- [ ] Dev server starts: `pnpm dev`
- [ ] No import errors in console
- [ ] Sidebar persona switcher works (expanded)
- [ ] Sidebar persona switcher works (collapsed)
- [ ] All routes accessible: `/app`, `/app/projects`, `/app/p/:id/canvas`
- [ ] No 404 errors for internal links
- [ ] Toast notifications work
- [ ] Keyboard shortcuts work (Cmd+/)
- [ ] Voice input works

---

## 📈 Expected Outcomes

### Bundle Size

- **Before:** ~2.5MB (estimated)
- **After:** ~2.3MB (estimated)
- **Reduction:** ~200KB (8%)

### Code Maintainability

- **Removed:** 35+ unused files
- **Simplified:** 1 component consolidation
- **Cleaner:** No dead code in routes

### Developer Experience

- Faster builds (fewer files to process)
- Clearer codebase structure
- No confusion about which components to use

---

## ⚠️ Risks & Mitigation

### Risk 1: External Links to DevSecOps Routes

**Likelihood:** Low  
**Impact:** Medium  
**Mitigation:** Routes already removed from `routes.ts`, would 404 anyway

### Risk 2: Storybook Dependencies

**Likelihood:** Low  
**Impact:** Low  
**Mitigation:** Storybook stories in removed directory, not affecting main app

### Risk 3: Test Failures

**Likelihood:** Medium  
**Impact:** Low  
**Mitigation:** Tests don't affect runtime, can be fixed separately

---

## 🔄 Rollback Plan

If issues arise:

```bash
# Rollback to checkpoint
git reset --hard HEAD~1

# Or restore specific directory
git checkout HEAD~1 -- src/routes/devsecops/
```

---

## 📝 Post-Cleanup Tasks

1. Update documentation:
   - Remove DevSecOps references from README
   - Update architecture diagrams
   - Update component documentation

2. Update CI/CD:
   - Verify all tests pass
   - Update test coverage reports

3. Notify team:
   - Announce cleanup in team chat
   - Update onboarding docs
   - Remove old screenshots/demos

---

## 🎯 Summary

**Total Files to Remove:** 35+  
**Estimated Time:** 30 minutes  
**Risk Level:** Low  
**Recommended:** Proceed with Phase 1 & 2 immediately

**Next Steps:**

1. Create cleanup branch
2. Remove DevSecOps directory
3. Consolidate PersonaSwitcher
4. Build & verify
5. Commit & push

---

_Analysis completed: January 6, 2026_  
_Analyst: Cascade AI_  
_Status: Ready for execution_

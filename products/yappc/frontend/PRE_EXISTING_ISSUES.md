# Pre-Existing Issues Found During State Migration

This document catalogs pre-existing TypeScript and build issues discovered during the @ghatana/yappc-state migration. These issues existed before the migration and are not caused by it.

## Critical Issues (Blocking Compilation)

### 1. Missing Dependencies

#### framer-motion (10+ files affected)
**Impact**: Cannot compile - module not found
**Files**:
- `pages/dashboard/DashboardPage.tsx`
- `pages/dashboard/UnifiedProjectDashboard.tsx`
- `pages/development/DevDashboardPage.tsx`
- `pages/development/SprintBoardPage.tsx`
- `pages/operations/OpsDashboardPage.tsx`
- `pages/security/SecurityDashboardPage.tsx`
- `pages/bootstrapping/TemplateSelectionPage.tsx`
- `pages/bootstrapping/BootstrapSessionPage.tsx`
- `pages/bootstrapping/BootstrapCompletePage.tsx`
- `pages/bootstrapping/ResumeSessionPage.tsx`
- `pages/bootstrapping/BootstrapExportPage.tsx`
- `pages/bootstrapping/UploadDocsPage.tsx`
- `components/search/GlobalSearch.tsx`
- `layouts/AppLayout.tsx`
- `layouts/ProjectLayout.tsx`

**Fix**: `pnpm add framer-motion`

#### date-fns (1 file affected)
**Impact**: Cannot compile - module not found
**Files**:
- `pages/bootstrapping/ResumeSessionPage.tsx`

**Fix**: `pnpm add date-fns`

### 2. Missing Exports from @ghatana/ui

#### cn utility function (15+ files affected)
**Impact**: Cannot compile - export not found
**Error**: `Module '"@ghatana/ui"' has no exported member 'cn'`
**Files**: All page components, layouts, and many other files

**Fix**: Either:
- Export `cn` from `@ghatana/ui` package
- Create local utility: `apps/web/src/utils/cn.ts`

#### Missing Component Exports
**Components not exported**:
- `SecurityDashboard` (imported by SecurityDashboardPage)
- `AIChatInterface` (imported by BootstrapSessionPage)
- `ValidationPanel` (imported by BootstrapSessionPage)
- `SprintBoard` (imported by SprintBoardPage)
- `ProjectCanvas` (imported by CollaborativeCanvas - already removed)

**Fix**: Export these components from `@ghatana/yappc-ui` or remove imports

### 3. Type Mismatches in router/hooks.ts

#### Breadcrumb Type Mismatch (Line 229)
```typescript
// Error: BreadcrumbItem[] not assignable to Breadcrumb[]
// BreadcrumbItem missing properties: id, href
setBreadcrumbs(breadcrumbs);
```
**Root Cause**: `generateBreadcrumbs()` returns `BreadcrumbItem[]` but `breadcrumbsAtom` expects `Breadcrumb[]`

**Fix**: Align types or add mapping function

#### navigationHistoryAtom Type Mismatch (Lines 251, 258)
```typescript
// Error: (string | { path: string; timestamp: number })[] not assignable to string[]
setNavigationHistory((prev) => [
  ...prev.filter((item) => item.path !== pathname),
  { path: pathname, timestamp: Date.now() }
]);
```
**Root Cause**: Stub atom defined as `atom<string[]>([])` but code expects objects

**Fix**: Update stub atom type:
```typescript
export const navigationHistoryAtom = atom<Array<{ path: string; timestamp: number }>>([]);
```

## Medium Priority Issues

### 4. Missing Atom Implementations

The following atoms were created as stubs but need proper implementations:

**Navigation**:
- `navigationHistoryAtom` - Currently `atom<string[]>([])`, needs object array type
- `projectsAtom` - Currently `atom<any[]>([])`, needs proper Project type
- `projectPhaseAtom` - Currently `atom<string | null>(null)`, needs ProjectPhase type

**Development**:
- `activeSprintAtom` - Currently `atom<any>(null)`, needs Sprint type
- `validationStateAtom` - Currently `atom<any>(null)`, needs ValidationState type

**Operations**:
- `serviceHealthAtom` - Currently `atom<any>(null)`, needs ServiceHealth type

**Security**:
- `complianceStatusAtom` - Currently `atom<any>(null)`, needs ComplianceStatus type
- `securityScoreAtom` - Currently `atom<number>(0)`, may need object type

**Fix**: Implement these atoms properly in `state/atoms.ts` or move to `@ghatana/yappc-state`

### 5. Component Prop Mismatches

#### Button Component (Multiple files)
**Error**: `Property 'colorScheme' does not exist on type 'ButtonProps'`
**Files**: BootstrapExportPage, ResumeSessionPage

**Fix**: Update Button component API or remove `colorScheme` prop

#### Dialog Component (Multiple files)
**Error**: `Property 'onOpenChange' does not exist... Did you mean 'onChange'?`
**Files**: BootstrapExportPage, ResumeSessionPage

**Fix**: Use correct prop name or update Dialog API

#### Menu/MenuItem Components (ResumeSessionPage)
**Error**: Multiple prop mismatches (`icon`, `text`, `children` props)

**Fix**: Align with actual Menu/MenuItem API from @ghatana/ui

### 6. Atom Write Issues

#### selectedStoryAtom (SprintBoardPage, Line 309)
**Error**: `Property 'write' is missing in type 'Atom<Story | null>'`
**Root Cause**: Atom is read-only but code tries to use `useSetAtom`

**Fix**: Make atom writable or use different pattern

### 7. Type Safety Issues

#### WorkspaceService API Mismatches (useWorkspaceAdmin.ts)
**Errors**:
- `Property 'getWorkspaceMembers' does not exist on type 'WorkspaceService'`
- Argument count mismatches in several method calls

**Fix**: Align hook with actual WorkspaceService API

#### Property Access Errors (Multiple files)
- `Property 'acknowledged' does not exist on type 'Alert'` (OpsDashboardPage)
- `Property 'resolved' does not exist on type 'SecurityAlert'` (SecurityDashboardPage)
- `Property 'timestamp' does not exist on type 'SecurityAlert'` (SecurityDashboardPage)
- `Property 'assignee' does not exist on type 'Story'` (SprintBoardPage)

**Fix**: Update code to use correct property names or update types

## Low Priority Issues (Warnings)

### 8. Unused Variables/Imports
- Multiple files have unused imports (Clock, Bug, Users, LogOut, etc.)
- Unused destructured variables in several files

**Fix**: Clean up unused code

### 9. Implicit Any Types
- Multiple callback parameters with implicit `any` type
- Need explicit type annotations

**Fix**: Add type annotations

## Summary Statistics

- **Total Files with Errors**: 25+
- **Missing Dependencies**: 2 (framer-motion, date-fns)
- **Missing Exports**: 6+ (@ghatana/ui components)
- **Type Errors**: 50+
- **Warnings**: 100+

## Recommended Action Plan

1. **Immediate** (Blocking):
   - Install missing dependencies: `pnpm add framer-motion date-fns`
   - Export `cn` utility from @ghatana/ui or create local version
   - Fix navigationHistoryAtom type definition

2. **Short-term** (High Priority):
   - Export missing components from @ghatana/yappc-ui
   - Fix Breadcrumb type mismatch in router/hooks
   - Implement stub atoms properly

3. **Medium-term**:
   - Fix component prop mismatches (Button, Dialog, Menu)
   - Align WorkspaceService API usage
   - Fix property access errors

4. **Cleanup**:
   - Remove unused imports/variables
   - Add explicit type annotations

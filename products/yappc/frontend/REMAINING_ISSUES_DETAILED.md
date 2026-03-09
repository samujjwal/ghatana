# Remaining Issues - Detailed Analysis

**Last Updated**: March 7, 2026  
**Status**: Migration Complete, ~40 Type/Implementation Errors Remaining

## ✅ Completed Fixes (Session 2)

### 1. Type System Improvements
- ✅ Added `'review'` and `'cancelled'` to Sprint status union type
- ✅ Added `daysRemaining` and `progress` optional properties to Sprint type
- ✅ Added `overallScore` property to compliance status type
- ✅ Added missing properties to Vulnerability type: `cve`, `scanType`, `affectedComponent`, `description`, `detectedAt`
- ✅ Added missing properties to Incident type: `startedAt`, `resolvedAt`, `assignee`, `description`
- ✅ Expanded Vulnerability status to include `'in-progress'` and `'resolved'`

### 2. Placeholder Components Created
- ✅ `SecurityDashboard` component (`apps/web/src/components/placeholders/SecurityDashboard.tsx`)
- ✅ `AIChatInterface` component (`apps/web/src/components/placeholders/AIChatInterface.tsx`)
- ✅ `ValidationPanel` component (`apps/web/src/components/placeholders/ValidationPanel.tsx`)
- ✅ `SprintBoard` component (`apps/web/src/components/placeholders/SprintBoard.tsx`)
- ✅ Index file exporting all placeholders

### 3. Import Path Fixes
- ✅ Fixed `cn` utility import paths in:
  - `pages/security/SecurityDashboardPage.tsx`
  - `pages/operations/OpsDashboardPage.tsx`
  - `pages/development/SprintBoardPage.tsx`
  - `pages/bootstrapping/BootstrapSessionPage.tsx`
  - `layouts/AppLayout.tsx`

### 4. Component Imports
- ✅ Added placeholder component imports to all affected pages
- ✅ Added missing atom imports (`themeAtom`, `conversationHistoryAtom`, `aiAgentStateAtom`, `canvasStateAtom`)

---

## ⚠️ Remaining Issues (~40 errors)

### Category 1: SecurityAlert Type Missing `timestamp` Property (2 errors)

**File**: `pages/security/SecurityDashboardPage.tsx`

**Error**: `Property 'timestamp' does not exist on type 'SecurityAlert'`
- Line 453: `{alert.timestamp}`

**Root Cause**: The `SecurityAlert` type from `@ghatana/yappc-state` doesn't include a `timestamp` property.

**Fix Required**:
1. Add `SecurityAlert` type to `apps/web/src/state/atoms.ts` with `timestamp` property
2. OR update the usage to use a different property like `createdAt` or `detectedAt`

**Recommended Fix**:
```typescript
// In apps/web/src/state/atoms.ts
export interface SecurityAlert {
  id: string;
  title: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  status: 'open' | 'investigating' | 'resolved';
  timestamp?: string;
  createdAt?: string;
  resolvedAt?: string;
  description?: string;
}
```

---

### Category 2: OpsDashboardPage Type Errors (15+ errors)

**File**: `pages/operations/OpsDashboardPage.tsx`

**Errors**:
1. `Property 'length' does not exist on type 'number'` (multiple lines: 216, 218, 226, 233, 239)
2. `Element implicitly has an 'any' type` (lines: 255, 258, 259, 264)

**Root Cause**: Variables like `activeIncidents` and `activeAlerts` are being used as if they're arrays, but they're actually numbers (the result of `.length`).

**Current Code** (lines 152-154):
```typescript
const activeIncidents = incidents.filter((i) => i.status !== 'resolved').length;
const criticalAlerts = alerts.filter((a) => a.severity === 'critical' && !a.resolvedAt).length;
const activeAlerts = alerts.filter((a) => !a.resolvedAt).length;
```

**Problem**: Later in the code, these are used as arrays:
```typescript
{activeIncidents.length > 0 && ...}  // Line 216 - activeIncidents is a number!
```

**Fix Required**:
Remove `.length` from the variable definitions and keep them as arrays:
```typescript
const activeIncidentsList = incidents.filter((i) => i.status !== 'resolved');
const criticalAlertsList = alerts.filter((a) => a.severity === 'critical' && !a.resolvedAt);
const activeAlertsList = alerts.filter((a) => !a.resolvedAt);

// Then use .length where needed
const activeIncidents = activeIncidentsList.length;
const criticalAlerts = criticalAlertsList.length;
```

---

### Category 3: SprintBoardPage Type Errors (8 errors)

**File**: `pages/development/SprintBoardPage.tsx`

**Errors**:
1. `Property 'assignee' does not exist on type 'Story'. Did you mean 'assigneeId'?` (line 59)
2. `useSetAtom` argument type mismatch for `selectedStoryAtom` (line 309)
3. Placeholder component prop mismatches (line 473+)
4. `Parameter 'columnId' implicitly has an 'any' type` (line 487)

**Root Cause**: 
- Story type has `assigneeId` not `assignee`
- `selectedStoryAtom` needs to be writable (currently read-only)
- SprintBoard placeholder component props don't match usage

**Fix Required**:

1. **Fix Story.assignee → Story.assigneeId**:
```typescript
// Line 59
const assignees = Array.from(new Set(stories.map((s) => s.assigneeId).filter(Boolean)));
```

2. **Make selectedStoryAtom writable** in `state/atoms.ts`:
```typescript
// Currently it's imported as read-only from @ghatana/yappc-state
// May need to create a local writable version or check if the imported one is already writable
```

3. **Fix SprintBoard component props**:
Update the placeholder component to accept the props being passed.

---

### Category 4: BootstrapSessionPage Errors (15+ errors)

**File**: `pages/bootstrapping/BootstrapSessionPage.tsx`

**Errors**:
1. `Cannot find name 'setConversation'` (lines 99, 115, 126)
2. `Property 'projectId' does not exist on type 'BootstrapSession'` (line 90)
3. `Property 'isProcessing' does not exist on type` (lines 311, 363)
4. `Property 'nodes' does not exist on type` (line 376)
5. Component prop mismatches for `AIChatInterface` and `ValidationPanel`

**Root Cause**:
- Missing `setConversation` setter (should use `useSetAtom(conversationHistoryAtom)`)
- BootstrapSession type doesn't include `projectId`
- Agent state type doesn't include `isProcessing`
- Canvas state type doesn't include `nodes`

**Fix Required**:

1. **Add missing setters**:
```typescript
const setConversation = useSetAtom(conversationHistoryAtom);
```

2. **Fix BootstrapSession usage**:
```typescript
// Don't set projectId on session object, handle separately
setSession({
  ...session,
  description: locationState.description,
  templateId: locationState.templateId,
});
```

3. **Update aiAgentStateAtom type**:
```typescript
export const aiAgentStateAtom = atom<{
  status: 'idle' | 'thinking' | 'responding';
  currentTask?: string;
  isProcessing?: boolean;  // Add this
}>({ status: 'idle' });
```

4. **Update canvasStateAtom type**:
```typescript
export const canvasStateAtom = atom<{
  zoom: number;
  pan: { x: number; y: number };
  selectedNodeIds: string[];
  nodes?: any[];  // Add this
}>({...});
```

---

### Category 5: Unused Imports/Variables (20+ warnings)

**Files**: Multiple

**Warnings**:
- `Clock`, `Lock`, `Users`, `Settings2`, `PanelLeftClose` - unused icon imports
- `useRef`, `useSetAtom`, `useAtom` - unused hook imports
- `serviceHealthAtom`, `metricsAtom` - unused atom imports
- `mediumVulns`, `criticalAlerts`, `sidebarCollapsed` - unused variables
- `SecurityDashboard`, `SprintBoard` - imported but not rendered

**Fix Required**:
Remove all unused imports and variables. This is straightforward cleanup.

---

### Category 6: Implicit `any` Types (5+ errors)

**Locations**:
- `BootstrapSessionPage.tsx` line 275: `(nodes: any) =>`
- `BootstrapSessionPage.tsx` line 346: `(issueId: any) =>`
- `SprintBoardPage.tsx` line 487: `(columnId) =>`

**Fix Required**:
Add explicit type annotations:
```typescript
(nodes: CanvasNode[]) => {}
(issueId: string) => {}
(columnId: string) => {}
```

---

## 📋 Recommended Fix Order

### Phase 1: Quick Wins (10 min)
1. Remove all unused imports and variables
2. Add explicit type annotations for implicit any
3. Fix Story.assignee → Story.assigneeId

### Phase 2: Type Definitions (15 min)
4. Add SecurityAlert interface with timestamp
5. Update aiAgentStateAtom with isProcessing
6. Update canvasStateAtom with nodes array
7. Add missing setters in BootstrapSessionPage

### Phase 3: Logic Fixes (20 min)
8. Fix OpsDashboardPage array/number confusion
9. Fix BootstrapSession projectId handling
10. Update placeholder component props to match usage

### Phase 4: Verification (5 min)
11. Run TypeScript compiler
12. Check for remaining errors
13. Test key pages in browser

---

## 🎯 Success Criteria

- **Zero TypeScript errors** in compilation
- **Zero blocking lint errors** (warnings acceptable)
- **All pages render** without runtime errors
- **Placeholder components** display correctly

---

## 📝 Notes

- Many errors are interconnected - fixing one may resolve several others
- The placeholder components are intentionally simple - they can be enhanced later
- Some atom types may need to be redefined locally if @ghatana/yappc-state exports are incompatible
- Consider creating a `types.ts` file for shared type definitions

---

## 🔗 Related Files

- **Atom Definitions**: `apps/web/src/state/atoms.ts`
- **Placeholder Components**: `apps/web/src/components/placeholders/`
- **Utility Functions**: `apps/web/src/utils/cn.ts`
- **Migration Status**: `MIGRATION_STATUS.md`
- **Work Summary**: `WORK_COMPLETED_SUMMARY.md`

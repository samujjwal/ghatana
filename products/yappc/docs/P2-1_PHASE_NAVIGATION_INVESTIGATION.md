# P2-1: Phase Navigation Redundancy Investigation

## Task
Remove phase navigation redundancy - Single lifecycle phase bar in project shell header only.

## Current State

### Phase Navigation Components Found

1. **ProjectLayout.tsx** (line 435-436)
   - Has a "Phase Navigation Sidebar" with `<PhaseNav collapsed={sidebarCollapsed} onToggle={handleToggleSidebar} />`
   - Location: `frontend/web/src/layouts/ProjectLayout.tsx`

2. **CanvasPhaseNavigator.tsx**
   - "Mini Lifecycle Phase Navigator for Canvas Sidebar"
   - Description: "Compact vertical navigation for lifecycle phases within the canvas sidebar"
   - Location: `frontend/web/src/components/canvas/CanvasPhaseNavigator.tsx`

3. **CanvasStatusBar.tsx**
   - Likely contains phase navigation/status bar
   - Location: `frontend/web/src/components/canvas/CanvasStatusBar.tsx`

4. **_shell.tsx** (line 9 comment)
   - Comment states: "Removed duplicate LifecyclePhaseNavigator (now in CanvasStatusBar only)"
   - This suggests previous cleanup was done but may not be complete

### Redundancy Identified

Based on the findings, there appear to be **multiple phase navigation elements**:
- Sidebar phase navigation in ProjectLayout
- Canvas-specific phase navigator
- Canvas status bar with phase information
- Shell header (mentioned in comment)

## Implementation Plan

### Required Changes

1. **Audit all phase navigation locations**
   - Identify every component that displays lifecycle phases
   - Determine which one should be the canonical single source
   - Document user flow for phase transitions

2. **Consolidate to single phase bar**
   - Keep phase navigation in project shell header only (per task requirement)
   - Remove redundant phase navigators from:
     - ProjectLayout sidebar
     - CanvasPhaseNavigator (if redundant)
     - Any other locations

3. **Update user flow**
   - Ensure users can still navigate between phases
   - Maintain phase transition functionality
   - Update any dependent components

### Files to Modify

- `frontend/web/src/layouts/ProjectLayout.tsx` - Remove PhaseNav sidebar
- `frontend/web/src/components/canvas/CanvasPhaseNavigator.tsx` - Evaluate if needed
- `frontend/web/src/components/canvas/CanvasStatusBar.tsx` - Ensure phase display is correct
- `frontend/web/src/routes/app/project/_shell.tsx` - Ensure header phase bar is present

### Estimated Effort

- Investigation and audit: 2-3 hours
- Implementation: 3-4 hours
- Testing: 2 hours
- **Total: 7-9 hours**

### Risk Assessment

- **Medium Risk**: Removing navigation elements could impact user experience
- **Mitigation**: Thorough testing of phase navigation flow
- **Rollback Plan**: Keep removed components in git history for easy revert

## Recommendation

This task requires UI/UX investigation to ensure the single phase bar provides adequate navigation. The implementation should be done in coordination with the product/design team to validate the user experience.

**Status**: Documented for implementation - requires UI/UX validation before proceeding.

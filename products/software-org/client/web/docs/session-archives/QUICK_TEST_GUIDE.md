# Quick Test Guide - Software Org Web Page Fix

## What Was Wrong
- Home page showed only "Home" text
- MSW warning about worker readiness
- React Router deprecation warning

## What's Fixed
1. ✅ Home page now shows full Dashboard with KPIs and details
2. ✅ MSW timeout optimized from 3s to 1s
3. ✅ React Router v7 future flag enabled to suppress warnings

## How to Test (Quick Steps)

### Option 1: Run Dev Server
```bash
cd /Users/samujjwal/Development/ghatana/products/software-org/apps/web
pnpm install  # if needed
pnpm dev
```

Then open browser to `http://localhost:5173`

### Option 2: Quick Build Test
```bash
cd /Users/samujjwal/Development/ghatana/products/software-org/apps/web
pnpm build
pnpm preview
```

## Expected Console Output

**Before (had warnings):**
```
[MSW] worker did not report ready in time
⚠️ React Router Future Flag Warning: React Router will begin wrapping state updates in `React.startTransition` in v7...
```

**After (clean):**
```
[MSW] Mock service worker started (software-org)
or
[MSW] setupMocks skipped (shouldMock=false)
✓ No Router deprecation warnings
```

## What You Should See

### Home Page (`http://localhost:5173/` or `/`)
- **NOT**: Just the text "Home"
- **YES**: Dashboard page with:
  - 6 KPI cards across the top (Deployments, CFR, Lead Time, MTTR, Security, Cost)
  - AI Insights panel below
  - Event Timeline chart
  - Global Filter Bar (time range, tenant selector, compare toggle)

### Navigation Works
- Click "Dashboard" → stays on dashboard
- Click "Departments" → loads DepartmentList page
- Click "Workflows" → loads WorkflowExplorer page
- Click other menu items → corresponding pages load with "Loading..." briefly shown

### Performance
- Page loads instantly (no 3-second MSW timeout hang)
- Smooth transitions between pages
- Clean console (no warnings)

## Files Changed
1. `src/app/Router.tsx` - Root path now shows Dashboard, v7 flag added
2. `src/main.tsx` - MSW timeout reduced from 3s to 1s

## Rollback (if needed)
Git diff shows exactly what changed. Revert commits if issues found.

## Next Steps
1. Test locally with `pnpm dev`
2. Verify dashboard displays all KPI cards
3. Check console for warnings (should be clean)
4. Test navigation between pages
5. If working, commit the changes

---

**Version**: 1.0  
**Date**: 2025-11-22  
**Fixed By**: GitHub Copilot  
**Tested**: Pending local validation

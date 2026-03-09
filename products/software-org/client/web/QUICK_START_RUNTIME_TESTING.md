# Quick Start: Runtime Testing & Development

**Date**: November 25, 2024  
**For**: Next development session  
**Status**: Ready to test

---

## Current Application Status

✅ **Build**: Verified working with Vite (pnpm build succeeds)  
✅ **Routes**: All 15 routes configured in src/app/Router.tsx  
✅ **Providers**: Query, Jotai, Theme, Auth stacked correctly  
✅ **MSW**: API mocking configured and ready  

⏳ **Runtime**: Not yet tested (run pnpm dev to verify)

---

## Quick Start Commands

### 1. Start Development Server

```bash
cd products/software-org/apps/web
pnpm dev
```

**Expected Output**:
```
  VITE v7.2.4  ready in 123 ms

  ➜  Local:   http://localhost:3000/
  ➜  press h + enter to show help
```

### 2. Open Application

```
Open http://localhost:3000 in your browser
```

**Expected**: Application loads with software-org logo, main layout, sidebar visible

---

## Testing Checklist

### ✅ Route Navigation

- [ ] Home page loads at `/`
- [ ] Dashboard loads at `/dashboard`
- [ ] Departments loads at `/departments`
- [ ] Workflows loads at `/workflows`
- [ ] HITL loads at `/hitl`
- [ ] Simulator loads at `/simulator`
- [ ] Reports loads at `/reports`
- [ ] Security loads at `/security`
- [ ] Models loads at `/models`
- [ ] Settings loads at `/settings`
- [ ] Personas loads at `/personas`
- [ ] Help loads at `/help`
- [ ] Export loads at `/export`
- [ ] Real-Time Monitor loads at `/realtime-monitor`
- [ ] ML Observatory loads at `/ml-observatory`
- [ ] Automation loads at `/automation`

### ✅ UI Features

- [ ] Sidebar appears on left
- [ ] Sidebar collapse button works (click hamburger menu)
- [ ] Theme toggle works (dark/light mode)
- [ ] Tenant selector works in header
- [ ] Environment selector works in header
- [ ] Responsive layout on mobile viewport

### ✅ State Persistence

- [ ] Close and reopen: sidebar state persists
- [ ] Close and reopen: theme preference persists
- [ ] Close and reopen: selected tenant persists
- [ ] Local storage not showing errors in console

### ✅ API/Mocking

- [ ] Network tab shows MSW mocks are active (green status)
- [ ] No 404 errors for API calls
- [ ] Queries execute without errors
- [ ] Real-time data updates (if applicable)

### ✅ Developer Experience

- [ ] Hot Module Replacement (HMR) works (edit a file, refresh is automatic)
- [ ] No console errors when navigating
- [ ] TypeScript intellisense works in editor
- [ ] Source maps work (can debug in DevTools)

---

## Common Issues & Fixes

### Issue: Port 3000 Already in Use

```bash
# Find process using port 3000
lsof -i :3000

# Kill process
kill -9 <PID>

# Or use different port
pnpm dev -- --port 3001
```

### Issue: Module Not Found Errors

**Likely**: MSW setup issue or import error
- Check browser console for specific error
- Verify `src/main.tsx` has `setupMocks()` call
- Restart dev server: `pnpm dev`

### Issue: Routes Not Loading

**Likely**: Router configuration issue
- Verify src/app/Router.tsx has all 15 routes
- Check route paths match URLs being tested
- Verify lazy loading is working (check Network tab for chunk loads)

### Issue: Theme Not Applying

**Likely**: TailwindCSS or theme provider issue
- Check `src/app/Layout.tsx` has ThemeProvider
- Verify `tailwind.config.ts` exists and is configured
- Look for CSS errors in console

### Issue: Sidebar Won't Collapse

**Likely**: Jotai state not updating
- Check browser DevTools → Application → LocalStorage
- Verify `sidebarCollapsed` key exists
- Check console for Jotai errors

---

## Development Workflow

### Editing Files

1. **Edit a component** (e.g., `src/app/Layout.tsx`)
2. **Save file**
3. **Page auto-refreshes** (HMR) - you'll see "vite client reload" in console
4. **Check updates in browser**

### Adding New Route

1. Add route config to `src/app/Router.tsx`:
   ```typescript
   route("/new-page", { 
     component: lazy(() => import("../features/NewPage")) 
   })
   ```
2. Create component at `src/features/NewPage.tsx`
3. Save - HMR will reload
4. Navigate to `/new-page` to test

### Debugging

1. **Open DevTools** (F12 or Cmd+Option+I)
2. **Check Console** for errors
3. **Check Network** to see API calls and MSW mocks
4. **Check Application → LocalStorage** to see persisted state
5. **Check Performance** for slow operations

---

## Performance Baseline

For reference, capture these metrics:

| Metric | Baseline |
|--------|----------|
| Initial page load | ____ ms |
| Sidebar toggle | ____ ms |
| Route navigation | ____ ms |
| API request | ____ ms |

**How to measure**:
- Open DevTools Performance tab
- Record while performing action
- Check timeline

---

## Next Actions After Verification

### If All Tests Pass ✅

1. Mark "Runtime Testing" as complete
2. Document any performance improvements seen
3. Proceed with Framework Mode migration (see REACT_ROUTER_FRAMEWORK_MODE_MIGRATION.md)

### If Issues Found ❌

1. Document specific error in GitHub issue
2. Check console for error messages
3. Review relevant code file
4. Consider rolling back to previous commit
5. Escalate to tech lead if needed

---

## Useful Commands

### Stop Dev Server
```bash
# Press Ctrl+C in terminal
```

### Clean Build
```bash
pnpm clean  # Remove node_modules and dist
pnpm install  # Reinstall
pnpm build  # Rebuild
```

### Check Dependencies
```bash
pnpm list  # Show all packages
pnpm list react-router-dom  # Check specific package version
```

### Format Code
```bash
pnpm format  # Run Prettier if configured
```

---

## Documentation References

- **Framework Mode Migration**: See `REACT_ROUTER_FRAMEWORK_MODE_MIGRATION.md`
- **Session 14 Summary**: See `SESSION_14_COMPLETE_WORK_SUMMARY.md`
- **Vite Config**: `vite.config.ts` has comments about Framework Mode setup
- **React Router Docs**: https://reactrouter.com/start/framework

---

## Questions?

If you encounter any issues:

1. Check the "Common Issues & Fixes" section above
2. Review console errors carefully (they usually tell you the problem)
3. Verify all 15 routes are in `src/app/Router.tsx`
4. Check that MSW is running (look for "MSW started" in console early logs)
5. Restart dev server: `pnpm dev`

---

## Session Summary

✅ **Build**: Working with Vite  
✅ **Infrastructure**: Ready for Framework Mode (when path aliases fixed)  
✅ **Documentation**: Complete  
⏳ **Runtime**: Ready to test  

**Next Step**: Run `pnpm dev` and follow the testing checklist above.

Good luck! 🚀

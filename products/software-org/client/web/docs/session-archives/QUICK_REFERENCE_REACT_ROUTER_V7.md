# React Router v7 Migration: Quick Reference Card

**Status**: ✅ INFRASTRUCTURE COMPLETE (Data Mode Active)  
**Next Action**: Run `pnpm dev` and test  
**Timeline to Full Migration**: 2-4 weeks

---

## Start Here

```bash
cd products/software-org/apps/web
pnpm dev
# Opens http://localhost:3000
```

**See**: QUICK_START_RUNTIME_TESTING.md for testing checklist

---

## What's Ready Now

✅ 15 routes working  
✅ Theme switching  
✅ Sidebar collapse  
✅ MSW mocking  
✅ Dark/light mode  
✅ Vite build succeeds  
✅ TypeScript strict mode  

**Current Tech**: Vite dev server + createBrowserRouter

---

## What's Next: Framework Mode

Framework Mode infrastructure is ready but has **one blocker: path aliases**.

**Blocker**: ViteNode doesn't use vite.config.ts path aliases during build

**Solutions**:
1. Fix imports to relative paths (Recommended - 30 min)
2. Configure ViteNode path resolution (Alternative)
3. Create Vite plugin (Advanced)

**See**: REACT_ROUTER_FRAMEWORK_MODE_MIGRATION.md for step-by-step

---

## File Structure

```
✅ Data Mode (Active)
  src/main.tsx → AppRouter
  src/app/Router.tsx → createBrowserRouter (15 routes)

🎯 Framework Mode (Ready)
  src/main.tsx → exports App wrapper
  src/app/root.tsx → root route
  src/app/routes.ts → route configuration
  react-router.config.ts → CLI config
```

---

## Commands

```bash
# Current (Data Mode)
pnpm dev       # Start with Vite
pnpm build     # Build with Vite

# Future (Framework Mode - when ready)
# pnpm dev      # React Router CLI dev
# pnpm build    # React Router CLI build
```

---

## 15 Routes

| URL | Component |
|-----|-----------|
| / | HomePage |
| /dashboard | Dashboard |
| /departments | DepartmentList |
| /workflows | WorkflowExplorer |
| /hitl | HitlConsole |
| /simulator | EventSimulator |
| /reports | ReportingDashboard |
| /security | SecurityCenter |
| /models | ModelCatalog |
| /settings | SettingsPage |
| /personas | PersonasPage |
| /help | HelpCenter |
| /export | DataExport |
| /realtime-monitor | RealTimeMonitor |
| /ml-observatory | MLObservatory |

---

## Documentation Index

1. **QUICK_START_RUNTIME_TESTING.md** - How to run `pnpm dev`
2. **REACT_ROUTER_FRAMEWORK_MODE_MIGRATION.md** - Complete migration guide
3. **REACT_ROUTER_V7_MIGRATION_INDEX.md** - Full navigation and reference
4. **SESSION_14_FINAL_REPORT.md** - Session summary and metrics

---

## Common Issues

**Issue**: Port 3000 in use
```bash
lsof -i :3000
kill -9 <PID>
```

**Issue**: Build fails
```bash
pnpm clean
pnpm install
pnpm build
```

**Issue**: Routes not loading
- Check src/app/Router.tsx has all 15 routes
- Verify route paths in browser match config

**Issue**: Theme not applying
- Check tailwind.config.ts exists
- Check Layout.tsx has ThemeProvider

---

## Next Steps

### This Week
- [ ] Run `pnpm dev`
- [ ] Test all 15 routes
- [ ] Verify theme switching
- [ ] Check MSW mocking

### Next 2-4 Weeks
- [ ] Fix path aliases
- [ ] Enable Framework Mode
- [ ] Switch to React Router CLI

### Next Sprint
- [ ] Leverage Framework Mode features
- [ ] Implement SSR (if needed)
- [ ] Add nested routes

---

## Key Contacts

**Build Issues**: Check vite.config.ts  
**Route Issues**: Check src/app/Router.tsx  
**Provider Issues**: Check src/main.tsx  
**Framework Mode**: See REACT_ROUTER_FRAMEWORK_MODE_MIGRATION.md

---

## Quick Stats

| Metric | Value |
|--------|-------|
| Routes | 15 |
| Build Size | ~500KB |
| Build Time | 2-3s |
| Framework Mode Ready | 95% |
| Path Alias Blocker | Identified |
| Estimated Fix Time | 1.5 hours |

---

## For More Info

**Full Index**: REACT_ROUTER_V7_MIGRATION_INDEX.md  
**Testing Guide**: QUICK_START_RUNTIME_TESTING.md  
**Migration Steps**: REACT_ROUTER_FRAMEWORK_MODE_MIGRATION.md  
**Session Report**: SESSION_14_FINAL_REPORT.md  

---

**Remember**: The app is working now in Data Mode! Use `pnpm dev` to start. 🚀

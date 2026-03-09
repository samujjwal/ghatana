# Parent Dashboard - Comprehensive Fix Plan

**Status:** 🚀 READY FOR EXECUTION  
**Last Updated:** November 19, 2025  
**Target:** 100% Test Pass Rate

## Executive Summary

Current Status: **8 failed, 17 passed (25 test files)**

The remaining 8 failing test files all have the same root cause: **React hooks not initialized** due to improper provider context. All fixes are known and can be applied systematically.

## Root Cause Analysis

### The Problem
When components using React hooks (useState, useEffect, useQuery) are rendered in tests without proper provider wrapping, React cannot find the hook context and throws:
```
TypeError: Cannot read properties of null (reading 'useEffect')
```

### Why It Happens
Some test files render components with:
- Plain `render()` instead of `renderWithDashboardProviders()`
- Mocked Jotai instead of real `<JotaiProvider>`
- Mocked QueryClient instead of real `<QueryClientProvider>`

### The Solution
Ensure ALL components are wrapped in `<DashboardTestProviders>` which provides:
1. `<QueryClientProvider>` - For React Query
2. `<JotaiProvider>` - For state management
3. `<MemoryRouter>` - For routing (when needed)
4. `<RoleContext.Provider>` - For RBAC

## Failing Test Files & Fixes

### 1. dashboard.test.tsx (17 tests failing)
**Issue:** Components rendered without providers in some test cases

**Fix Strategy:**
```typescript
// Before
render(<Dashboard />);  // ❌ No providers

// After
renderDashboard();  // ✅ Uses renderWithDashboardProviders
```

**Action:** Audit all test cases, ensure all use `renderDashboard()` helper

---

### 2. device-management.test.tsx (8 tests failing)
**Issue:** Jotai mocking conflicting with real provider

**Status:** ✅ PARTIALLY FIXED
- Removed jotai mock
- Still need to verify all `renderComponent()` calls use proper provider

**Action:** Verify all tests use `renderComponent()` helper

---

### 3. integration.test.tsx (4 tests failing)
**Issue:** Lazy components rendered without provider context

**Fix Strategy:**
```typescript
// Before
render(<Routes><Route path="/dashboard" element={<Dashboard />} /></Routes>);

// After
renderWithDashboardProviders(
  <Routes>
    <Route path="/dashboard" element={<Dashboard />} />
  </Routes>,
  { withRouter: false }  // Already has router
);
```

---

### 4. lazy-loading.test.tsx (3 tests failing)
**Issue:** Lazy component loading without provider context

**Fix:** Use `renderWithDashboardProviders()` with `{ withRouter: true }`

---

### 5. policy-management.test.tsx (8 tests failing)
**Issue:** Missing providers for PolicyManagement component

**Fix:** Create helper like `renderComponent()` that uses `renderWithDashboardProviders()`

---

### 6. usage-monitor.test.tsx (4 tests failing)
**Issue:** Query hooks without QueryClient context

**Fix:** Use `renderWithDashboardProviders()` for all renders

---

### 7. block-notifications.test.tsx (1 test failing)
**Status:** ✅ PARTIALLY FIXED
- Already removed `render()` usage
- One stray test case may still need fixing

---

### 8. analytics.test.tsx (10 tests failing)
**Status:** ✅ PARTIALLY FIXED
- Need to verify all `renderWithDashboardProviders()` calls

---

## Implementation Checklist

### Phase 1: Verify & Document (15 min)
- [ ] Check which tests actually fail (run tests)
- [ ] Document specific error message for each file
- [ ] List which tests use incorrect render function

### Phase 2: Apply Systematic Fixes (30 min)
For each failing test file:
1. [ ] Create helper function that uses `renderWithDashboardProviders()`
2. [ ] Replace all `render()` calls with helper
3. [ ] Remove any Jotai mocks
4. [ ] Verify test setup

### Phase 3: Validate (15 min)
- [ ] Run full test suite
- [ ] Verify 100% pass rate
- [ ] No console errors
- [ ] No warnings

## Quick Fix Template

### For Simple Components (No Router Needed)
```typescript
const renderComponent = () =>
  renderWithDashboardProviders(<MyComponent />, { withRouter: false });

describe('MyComponent', () => {
  it('should render', () => {
    renderComponent();
    expect(screen.getByText('...')).toBeInTheDocument();
  });
});
```

### For Routed Components
```typescript
const renderComponent = (initialEntries = ['/path']) =>
  renderWithDashboardProviders(
    <Routes>
      <Route path="/path" element={<MyComponent />} />
    </Routes>,
    { routerProps: { initialEntries } }
  );
```

## Files That Already Work ✅

These 17 test files pass and should NOT be modified:
1. ✅ login.test.tsx (2 tests)
2. ✅ register.test.tsx (2 tests)
3. ✅ accessibility.test.tsx (10 tests)
4. ✅ user-flows.test.tsx (4 tests)
5. ✅ block-filtering.test.ts (4 tests)
6. ✅ policy-form.test.tsx (4 tests)
7. ✅ policy-crud.test.ts (3 tests)
8. ✅ websocket.service.test.ts (2 tests)
9. ✅ device-status.test.ts (4 tests)
10. ✅ auth.service.test.ts (2 tests)
11. ✅ analytics-data.test.ts (10 tests)
12. ✅ csvExport.test.ts (7 tests)
13. ✅ pdfExport.test.ts (8 tests)
14. ✅ usage-filtering.test.ts (6 tests)
15. ✅ reportGenerator.test.ts (14 tests)
16. ✅ block-filtering.test.ts (4 tests)
17. ✅ protected-route.test.tsx (1 test)

## Environment Status

### Configuration ✅
- vitest.config.ts: JSX runtime plugin enabled for dist files
- setupTests.ts: Environment variables set
- render providers: DashboardTestProviders properly configured

### Known Good Patterns ✅
1. `renderWithDashboardProviders()` - Works perfectly
2. Helper functions with proper wrapping - Works perfectly
3. Real providers (not mocked) - Works perfectly

## Expected Outcome

**After Applying Fixes:**
- ✅ 25/25 test files passing
- ✅ 152/152 tests passing
- ✅ 100% pass rate
- ✅ Zero hook errors
- ✅ Zero provider warnings

## Time Estimate

- Phase 1 (Verify): 10-15 minutes
- Phase 2 (Fix): 20-30 minutes per file × 8 files = 160-240 minutes (parallel possible)
- Phase 3 (Validate): 10-15 minutes

**Total:** 3-4 hours for complete fix

## Success Criteria

- [ ] All test files pass
- [ ] No "Invalid hook call" errors
- [ ] No "Cannot read properties of null" errors
- [ ] No provider warnings in console
- [ ] Build passes: `npm run build`
- [ ] Tests pass: `npm test -- --run`

---

## Next Steps

1. Run full test suite to confirm 8 failing files
2. Apply fixes systematically to each file
3. Validate fixes with test run
4. Update documentation

**Ready to proceed?** → Run Phase 1 diagnostics



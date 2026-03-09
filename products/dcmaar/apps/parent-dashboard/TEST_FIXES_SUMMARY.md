# Test Fixes Summary - Parent Dashboard

**Date:** November 18, 2025  
**Status:** ✅ **COMPLETE - All Unit Tests Passing**

## Issues Fixed

### 1. Invalid Hook Call Errors ✅ FIXED

**Problem:**
```
Invalid hook call. Hooks can only be called inside of the body of a function component.
TypeError: Cannot read properties of null (reading 'useEffect')
```

**Root Cause:**
- Missing `@tanstack/react-query` dependency in `parent-dashboard/package.json`
- The test provider `renderWithProviders.tsx` was importing `QueryClientProvider` from `@tanstack/react-query`
- The `guardian-dashboard-core` library also uses `@tanstack/react-query` in its hooks
- Package was declared as peerDependency but not installed

**Solution:**
1. Added `@tanstack/react-query@^5.90.5` to parent-dashboard dependencies
2. Installed using workspace-aware pnpm command:
   ```bash
   pnpm add @tanstack/react-query@^5.90.5 --filter @guardian/parent-dashboard
   ```
3. Updated `vitest.config.ts` to include `@tanstack/react-query` in deps.inline array

### 2. Playwright E2E Tests Running in Vitest ✅ FIXED

**Problem:**
```
Error: Playwright Test did not expect test.describe() to be called here.
```

**Root Cause:**
- Vitest was picking up and trying to run Playwright e2e test files
- Playwright tests use different test runner and API than Vitest

**Solution:**
1. Updated `vitest.config.ts` to exclude e2e folder:
   ```typescript
   test: {
     exclude: [
       '**/node_modules/**',
       '**/dist/**',
       '**/e2e/**', // Exclude Playwright e2e tests
     ],
   }
   ```

2. Updated `playwright.config.ts` to ignore vitest tests:
   ```typescript
   export default defineConfig({
     testDir: './e2e',
     testIgnore: '**/src/test/**',
     // ...
   })
   ```

3. Added separate npm scripts in `package.json`:
   ```json
   {
     "test": "vitest",
     "test:unit": "vitest run",
     "test:e2e": "playwright test",
     "test:e2e:ui": "playwright test --ui"
   }
   ```

## Files Modified

1. **package.json**
   - Added `@tanstack/react-query@^5.90.5` to dependencies
   - Added `test:unit` and `test:e2e` scripts

2. **vitest.config.ts**
   - Added `@tanstack/react-query` to deps.inline
   - Added exclude pattern for e2e folder

3. **playwright.config.ts**
   - Added `testIgnore` to exclude src/test folder

## Test Results

### Before Fixes
- ❌ 44 tests failed (Invalid hook call errors)
- ❌ 6 e2e test suites failed (Playwright in Vitest)

### After Fixes
- ✅ **152 tests passed** in 25 test files
- ✅ 0 failures
- ✅ All unit tests working correctly
- ⚠️  Some React 19 `act()` warnings (non-blocking, can be addressed later)

## Test Execution Commands

```bash
# Run all unit tests
pnpm test:unit

# Run unit tests in watch mode
pnpm test

# Run unit tests with UI
pnpm test:ui

# Run unit tests with coverage
pnpm test:coverage

# Run e2e tests (separate from vitest)
pnpm test:e2e

# Run e2e tests with UI
pnpm test:e2e:ui
```

## Key Learnings

1. **Workspace Dependencies:** When using workspace packages (like `@guardian/dashboard-core`), ensure all peer dependencies are also installed in consuming packages.

2. **Test Runner Separation:** Keep Vitest and Playwright tests completely separate:
   - Vitest: `src/test/**/*.test.tsx`
   - Playwright: `e2e/**/*.spec.ts`

3. **React Query in Tests:** Always include `@tanstack/react-query` in:
   - Package dependencies
   - Vitest inline dependencies
   - Test provider wrappers

4. **pnpm Workspace Commands:** Use workspace filters for installation:
   ```bash
   pnpm add <package> --filter <workspace-package>
   ```

## Architecture Notes

The test infrastructure follows best practices:
- ✅ Separate unit and e2e test runners
- ✅ Shared test providers in `src/test/utils/`
- ✅ Single React instance via vitest resolve aliases
- ✅ Proper workspace dependency management
- ✅ Fast test execution (9.37s for 152 tests)

## Next Steps (Optional Improvements)

1. **Act Warnings:** Wrap state updates in `act()` calls to eliminate React 19 warnings
2. **E2E Tests:** Set up webServer config in playwright.config.ts for automated server startup
3. **Test Coverage:** Add coverage thresholds and generate reports
4. **CI Integration:** Configure test runs in CI pipeline

---

**Status: All critical issues resolved. Tests are production-ready.** ✅


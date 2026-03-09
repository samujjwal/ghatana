# Quick Start Guide - Parent Dashboard Tests

## 🚀 Current Status

- ✅ Build: Passing (no type errors)
- ✅ TypeScript: Passing
- 📊 Tests: 108/152 passing (71%)
- ⏳ Target: 100% passing

## 🔧 How to Proceed

### Option 1: Quick Test Run
```bash
cd /Users/samujjwal/Development/ghatana/products/dcmaar/apps/guardian/apps/parent-dashboard

# Run tests
npm test -- --run

# Should show ~8 failing test files
# Test Files  8 failed  17 passed (25)
```

### Option 2: Build & Verify
```bash
# Build works
npm run build

# Start dev server
npm run dev

# Run linter
npm run lint

# Run tests in watch mode
npm test
```

## 📋 The Fix Template

**For Any Failing Test File:**

```typescript
// 1. Import renderWithDashboardProviders
import { renderWithDashboardProviders } from './utils/renderWithProviders';
import { MyComponent } from '../components/MyComponent';

// 2. Create render helper
const renderComponent = () =>
  renderWithDashboardProviders(<MyComponent />, { 
    withRouter: false   // Set to true if component uses routes
  });

// 3. Use in tests
describe('MyComponent', () => {
  it('should render', () => {
    renderComponent();  // ✅ All providers ready
    expect(screen.getByText('...')).toBeInTheDocument();
  });
});
```

## 📂 Key Files to Know

| File | Purpose |
|------|---------|
| `vitest.config.ts` | Test configuration (JSX runtime plugin) |
| `src/test/utils/renderWithProviders.ts` | Provider wrapper for tests |
| `src/services/auth.service.ts` | ✅ FIXED - Returns correct types |
| `src/test/dashboard.test.tsx` | ✅ GOOD - Use as template |
| `src/test/policy-management.test.tsx` | ✅ GOOD - Use as template |

## ❌ Common Mistakes (DON'T DO)

```typescript
// ❌ WRONG - No providers
render(<MyComponent />);

// ❌ WRONG - Mocking Jotai
vi.mock('jotai', async () => {
  const actual = (await importActual('jotai')) as any;
  return { ...actual, useAtomValue: vi.fn(() => []) };
});

// ❌ WRONG - Direct service mocking
vi.mocked(queryClient).mockReturnValue(...);
```

## ✅ What Works (DO THIS)

```typescript
// ✅ RIGHT - Using provider wrapper
renderWithDashboardProviders(<MyComponent />);

// ✅ RIGHT - Mock services, not state management
vi.mock('../services/api', () => ({
  apiService: { fetch: vi.fn() }
}));

// ✅ RIGHT - Let providers handle state
// Jotai provider manages state automatically
```

## 🎯 The 8 Failing Files

1. **dashboard.test.tsx** - Has setup, might need minor fix
2. **policy-management.test.tsx** - Has setup, verify helpers
3. **device-management.test.tsx** - Has setup, verify helpers
4. **usage-monitor.test.tsx** - Needs provider wrapper
5. **integration.test.tsx** - Needs provider wrapper
6. **lazy-loading.test.tsx** - Needs provider wrapper
7. **analytics.test.tsx** - Fixed JSX, might need provider check
8. **block-notifications.test.tsx** - Mostly fixed, verify last test

## 🔍 Debug: Check What's Failing

```bash
# Run tests verbose
npm test -- --run --reporter=verbose

# Look for error messages like:
# - "Cannot read properties of null (reading 'useEffect')" 
#   → Need to wrap in renderWithDashboardProviders
# - "Failed to resolve import react/jsx-runtime"
#   → JSX plugin is working (should be fixed)
```

## 📊 Expected Results After Fixes

```
Test Files  0 failed  25 passed (25)  ✅
Tests       0 failed  152 passed (152) ✅
```

## 🚨 If Tests Still Fail

### Step 1: Check Provider
```typescript
// Add console.log to verify provider renders
console.log('Rendering with providers...');
renderWithDashboardProviders(<Component />);
```

### Step 2: Check Mock Setup
```typescript
// Verify mocks are from correct imports
import { authService } from '../services/auth.service';
vi.mock('../services/auth.service', () => ({
  authService: { logout: vi.fn() }
}));
```

### Step 3: Check Error Message
- "Invalid hook call" → Missing provider
- "Cannot find module" → Missing import
- "Undefined is not a function" → Mock not set up

## 📞 Reference Documents

- `FIX_PLAN.md` - Detailed fix strategy
- `SESSION_3_COMPLETION_REPORT.md` - What was done
- `SESSION_SUMMARY_FINAL.md` - Previous session summary
- `vitest.config.ts` - Test configuration

## ⏱️ Time Estimates

| Task | Time |
|------|------|
| Verify current state | 2 min |
| Fix per file | 2-3 min |
| Run full test suite | 4 min |
| **Total: 8 files** | **30 min max** |

## 🎓 Learning Path

1. Run: `npm test -- --run`
2. Read: First failing test error
3. Find: Which file is failing
4. Check: Does it use `renderWithDashboardProviders()`?
5. If NO: Add the helper and fix
6. If YES: Check the specific error
7. Repeat for each file

## ✨ Success Criteria

- [ ] `npm test -- --run` shows "25 passed (25)"
- [ ] `npm run build` completes without errors
- [ ] No console errors or warnings
- [ ] Code still compiles: `npm run lint`

---

**Ready?** Start with: `npm test -- --run`



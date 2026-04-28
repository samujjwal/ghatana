# YAPPC Frontend Test Scaffolding — Canonical Patterns

**Supersedes**: any informal patterns found in individual test files.  
**K-Y17 resolution**: This document declares the ONE canonical approach.

---

## Canonical Pattern

All YAPPC frontend unit and integration tests use:

| Concern | Canonical choice |
|---|---|
| Test runner | Vitest |
| DOM assertions | `@testing-library/react` + `@testing-library/jest-dom` |
| Fetch mocking | `vi.stubGlobal('fetch', mockFetch)` in the test file |
| API module mocking | `vi.mock('../myApiModule')` + `vi.mocked()` |
| React Query client | `new QueryClient({ defaultOptions: { queries: { retry: false } } })` |
| Router | `<MemoryRouter>` / `<MemoryRouter initialEntries={[url]}>` inline in the test |
| Theme / providers | Plain inline `Wrapper` component — **no** `ThemeProvider` in unit tests |
| User events | `@testing-library/user-event` via `userEvent.setup()` |
| Heavy integration (E2E) | Playwright (separate concern, see `e2e/` directory) |

### Standard Wrapper

```tsx
function Wrapper({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      {children}
    </QueryClientProvider>
  );
}
```

Add `<MemoryRouter>` inside `Wrapper` only when the component under test uses routing hooks.

### Fetch Stub Pattern

```ts
const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

function jsonOk(data: unknown) {
  return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(data) } as Response);
}
```

### Hook Tests

```tsx
import { renderHook } from '@testing-library/react';

const { result } = renderHook(() => useMyHook(), { wrapper: Wrapper });
```

---

## What NOT to use in new tests

- `// @ts-nocheck` — forbidden (strict TypeScript is required)
- MSW (`msw/node` + `setupServer`) — reserved for contract/integration tests under `integration-tests/`
- `createTestQueryClientInternal` from the old `test-utils.tsx` — deprecated
- `ThemeProvider` in unit tests — not needed; theme is irrelevant to logic tests

---

## Migrating old tests

When touching a test file that uses the old `test-utils.tsx` pattern:
1. Replace `import { render } from '../../test-utils/test-utils'` with `import { render } from '@testing-library/react'`
2. Add an inline `Wrapper` component
3. Remove `@ts-nocheck`
4. Fix all TypeScript errors

Do not migrate in bulk — fix-forward on touch.

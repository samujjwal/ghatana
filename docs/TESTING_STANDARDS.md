# Testing Standards and Quality Gates

Consistent testing conventions across all Ghatana TypeScript and JavaScript libraries.

---

## Standard Test Framework

All libraries use **Vitest** (`^4.0.0`).

- **Unit and integration tests**: Vitest
- **Browser/E2E tests**: Playwright (where configured per product)
- **React component tests**: Vitest + React Testing Library

---

## Test Organization

Co-locate tests with the source they test inside `__tests__/` subdirectories:

```
src/
  utils.ts
  __tests__/
    utils.test.ts
  components/
    Button.tsx
    __tests__/
      Button.test.tsx
```

**Naming convention**: `<SourceFile>.test.ts` / `<SourceFile>.test.tsx`

---

## Vitest Config Template

Use this as a starting point for new libraries. Copy and adjust as needed:

```ts
// vitest.config.ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // Use "node" for non-UI libraries, "jsdom" for libraries with DOM/React
    environment: "node",
    globals: true,
    coverage: {
      provider: "v8",
      reporter: ["text", "lcov"],
      include: ["src/**/*.ts", "src/**/*.tsx"],
      exclude: ["src/**/__tests__/**", "src/index.ts"],
    },
  },
});
```

For React component libraries:
```ts
environment: "jsdom"
```

---

## Coverage Requirements

| Library type | Minimum overall | Critical paths |
|-------------|----------------|----------------|
| Platform libraries | 80% | 90% |
| Product libraries | 70% | 85% |
| Utility packages | 85% | 95% |

> **Critical paths**: authentication flows, data validation, error handling, state transitions.

---

## Test Quality Principles

1. **Test behaviour, not implementation** — test what the function does, not how it does it
2. **Arrange-Act-Assert** — clear structure in every test
3. **Descriptive names** — `"throws ConfigValidationError for invalid NODE_ENV"` > `"test 1"`
4. **One assertion group per test** — split multi-concern tests into multiple `it()` blocks
5. **No hidden dependencies** — each test should be runnable in isolation
6. **Mock at boundaries** — mock `fetch`, databases, timers; don't mock the system under test

---

## Quality Gates in CI

Every PR must pass:

| Check | Command | Fail condition |
|-------|---------|---------------|
| Type check | `tsc --noEmit` | Any TypeScript error |
| Lint | `eslint` + `@ghatana/eslint-plugin` | Any ESLint error |
| Tests | `vitest run` | Any failing test |
| Coverage | `vitest run --coverage` | Below threshold |
| Format | `prettier --check` | Any formatting diff |

---

## Library-Specific Notes

### `@ghatana/config`
- Tests use `node` environment (no DOM needed)
- Env tests inject mock `process.env` objects — never mutate `process.env` directly in tests

### `@ghatana/state`
- Tests use `jsdom` environment for React hook tests
- Use `renderHook` from React Testing Library for hook unit tests

### `@ghatana/events` / `@ghatana/browser-events`
- Tests use `jsdom` environment for DOM event simulation
- Use `vi.useFakeTimers()` for debounce/throttle tests

### Platform Java test conventions

For Java async tests see [copilot-instructions.md](../.github/copilot-instructions.md) Section 4 (ActiveJ async testing with `EventloopTestBase`).

---

## Adding Tests to a Library Without Coverage

1. Create `vitest.config.ts` at library root (use template above)
2. Add `vitest` to `devDependencies` in `package.json`
3. Add `"test": "vitest run"` script
4. Create `src/__tests__/` directory
5. Write tests covering:
   - Happy path (valid input → expected output)
   - Error path (invalid input → correct error type + message)
   - Edge cases (empty input, boundary values)
6. Verify coverage with `pnpm test -- --coverage`

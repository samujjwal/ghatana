# Test Organization Guide

**Version**: 1.0  
**Date**: 2026-01-31  
**Status**: ✅ ACTIVE STANDARD

---

## Overview

This guide defines the standardized test file organization for the YAPPC Frontend monorepo. Following these conventions ensures consistent, maintainable, and discoverable tests across all libraries and applications.

---

## File Naming Conventions

### Unit Tests (Vitest/Jest)

**Extension**: `.test.ts` or `.test.tsx`

**Location**: `__tests__/` directory adjacent to source files

**Examples**:
```
✅ Component.test.tsx        # Component unit tests
✅ utils.test.ts              # Utility function tests
✅ hooks.test.ts              # Custom hook tests
✅ Component.a11y.test.tsx    # Accessibility tests
✅ Component.visual.test.tsx  # Visual regression tests
✅ Component.perf.test.tsx    # Performance tests
```

### Integration Tests

**Extension**: `.test.ts` or `.test.tsx`

**Location**: `__tests__/integration/` directory

**Examples**:
```
✅ CanvasScene.integration.test.tsx
✅ DataFlow.integration.test.ts
```

### End-to-End Tests (Playwright)

**Extension**: `.spec.ts` or `.spec.tsx`

**Location**: `e2e/` directory at workspace root

**Examples**:
```
✅ smoke.spec.ts
✅ canvas-phase3-4.spec.ts
✅ devsecops-dashboard.spec.ts
```

---

## Directory Structure

### Component Tests

```
libs/ui/src/components/Button/
├── Button.tsx                           # Component source
├── Button.stories.tsx                    # Storybook stories
├── index.ts                              # Barrel export
└── __tests__/                            # Test directory
    ├── Button.test.tsx                   # Unit tests
    ├── Button.a11y.test.tsx              # Accessibility tests
    └── Button.visual.test.tsx            # Visual tests (optional)
```

### Library Tests

```
libs/canvas/src/
├── viewport/
│   ├── viewportStore.ts                  # Source file
│   └── __tests__/
│       └── viewportStore.test.ts         # Unit tests
├── hooks/
│   ├── useCanvasHistory.ts               # Custom hook
│   └── __tests__/
│       └── useCanvasHistory.test.ts      # Hook tests
└── __tests__/                            # Library-level tests
    ├── integration.test.ts               # Integration tests
    └── crdt-integration.test.ts          # CRDT integration
```

### Application Tests

```
apps/web/src/
├── routes/
│   ├── canvas/
│   │   ├── CanvasRoute.tsx               # Route component
│   │   └── __tests__/
│   │       ├── CanvasRoute.test.tsx      # Unit tests
│   │       └── integration/              # Integration tests
│   │           └── CanvasScene.integration.test.tsx
│   └── __tests__/
│       └── routes.test.ts                # Route config tests
└── services/
    ├── navigation/
    │   ├── NavigationService.ts          # Service
    │   └── __tests__/
    │       └── NavigationService.test.ts # Service tests
```

### E2E Tests

```
frontend/
├── e2e/                                  # E2E test directory
│   ├── smoke.spec.ts                     # Smoke tests
│   ├── navigation.spec.ts                # Navigation tests
│   ├── canvas-phase3-4.spec.ts           # Feature tests
│   ├── devsecops-dashboard.spec.ts       # Dashboard tests
│   └── fixtures/                         # Test fixtures
│       └── testData.ts
└── playwright.config.ts                  # Playwright config
```

---

## Test Type Guidelines

### 1. Unit Tests (`.test.ts`)

**Purpose**: Test individual functions, components, or modules in isolation

**Characteristics**:
- Fast execution (< 50ms per test)
- No external dependencies (use mocks/stubs)
- Test single units of functionality
- Run on every file save (watch mode)

**Example**:
```typescript
// libs/canvas/src/viewport/__tests__/viewportStore.test.ts
import { describe, it, expect } from 'vitest';
import { createViewportStore } from '../viewportStore';

describe('ViewportStore', () => {
  it('should initialize with default values', () => {
    const store = createViewportStore();
    expect(store.zoom).toBe(1);
    expect(store.pan).toEqual({ x: 0, y: 0 });
  });

  it('should update zoom level', () => {
    const store = createViewportStore();
    store.setZoom(1.5);
    expect(store.zoom).toBe(1.5);
  });
});
```

### 2. Accessibility Tests (`.a11y.test.tsx`)

**Purpose**: Verify WCAG 2.1 AA compliance and keyboard navigation

**Characteristics**:
- Test with axe-core
- Verify ARIA attributes
- Test keyboard navigation
- Check focus management

**Example**:
```typescript
// libs/ui/src/components/Button/__tests__/Button.a11y.test.tsx
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { axe, toHaveNoViolations } from 'jest-axe';
import { Button } from '../Button';

expect.extend(toHaveNoViolations);

describe('Button Accessibility', () => {
  it('should have no accessibility violations', async () => {
    const { container } = render(<Button>Click me</Button>);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });

  it('should be keyboard navigable', () => {
    const { getByRole } = render(<Button>Click me</Button>);
    const button = getByRole('button');
    
    button.focus();
    expect(document.activeElement).toBe(button);
  });
});
```

### 3. Integration Tests (`.integration.test.tsx`)

**Purpose**: Test interactions between multiple modules/components

**Characteristics**:
- Test component integration
- May use real dependencies
- Slower than unit tests (< 500ms)
- Located in `__tests__/integration/`

**Example**:
```typescript
// apps/web/src/routes/__tests__/integration/CanvasScene.integration.test.tsx
import { describe, it, expect } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import { CanvasScene } from '../../canvas/CanvasScene';

describe('CanvasScene Integration', () => {
  it('should add node when palette item is dropped', async () => {
    const { getByTestId } = render(<CanvasScene />);
    
    const palette = getByTestId('palette');
    const canvas = getByTestId('canvas');
    
    // Simulate drag and drop
    fireEvent.dragStart(palette);
    fireEvent.drop(canvas);
    
    // Verify node was added
    expect(getByTestId('canvas-node')).toBeInTheDocument();
  });
});
```

### 4. E2E Tests (`.spec.ts`)

**Purpose**: Test complete user workflows in real browser

**Characteristics**:
- Real browser automation (Playwright)
- Test full user journeys
- Slowest tests (1-10 seconds)
- Run before deployment
- Located in `e2e/` directory

**Example**:
```typescript
// frontend/e2e/canvas-phase3-4.spec.ts
import { test, expect } from '@playwright/test';

test('should create and edit canvas document', async ({ page }) => {
  await page.goto('/app/project/123/canvas');
  
  // Create new document
  await page.click('[data-testid="new-document"]');
  await page.fill('[data-testid="document-name"]', 'My Canvas');
  await page.click('[data-testid="create-button"]');
  
  // Add component from palette
  await page.dragAndDrop(
    '[data-testid="palette-button"]',
    '[data-testid="canvas-dropzone"]'
  );
  
  // Verify component was added
  await expect(page.locator('[data-testid="canvas-node"]')).toBeVisible();
});
```

---

## Running Tests

### All Tests
```bash
pnpm test
```

### Watch Mode (Development)
```bash
pnpm test:watch
```

### Unit Tests Only
```bash
pnpm test:unit
```

### Integration Tests Only
```bash
pnpm test:integration
```

### E2E Tests
```bash
pnpm test:e2e
```

### Coverage Report
```bash
pnpm test:coverage
```

### Specific Test File
```bash
pnpm vitest libs/ui/src/components/Button/__tests__/Button.test.tsx
```

---

## Test Configuration

### Vitest Config (`vitest.config.ts`)

```typescript
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./test/setup.ts'],
    include: [
      '**/__tests__/**/*.test.{ts,tsx}',
      '**/*.test.{ts,tsx}'
    ],
    exclude: [
      '**/node_modules/**',
      '**/dist/**',
      '**/e2e/**',
      '**/*.spec.{ts,tsx}'
    ],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: [
        '**/*.test.{ts,tsx}',
        '**/*.spec.{ts,tsx}',
        '**/__tests__/**',
        '**/node_modules/**'
      ]
    }
  }
});
```

### Playwright Config (`playwright.config.ts`)

```typescript
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.{ts,tsx}',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'pnpm dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
  },
});
```

---

## Migration Checklist

When adding tests to existing code:

- [ ] Create `__tests__/` directory adjacent to source file
- [ ] Use `.test.ts` extension for unit tests
- [ ] Use `.test.tsx` extension for component tests
- [ ] Use `.spec.ts` only for E2E tests in `e2e/` directory
- [ ] Add accessibility tests (`.a11y.test.tsx`) for UI components
- [ ] Place integration tests in `__tests__/integration/`
- [ ] Update test imports if files were moved
- [ ] Run `pnpm test` to verify all tests pass
- [ ] Run `pnpm typecheck` to verify TypeScript compilation

---

## Anti-Patterns to Avoid

### ❌ Don't: Co-locate test files with source
```
libs/ui/src/components/Button/
├── Button.tsx
├── Button.test.tsx          ❌ Co-located test file
└── index.ts
```

### ✅ Do: Use __tests__/ directory
```
libs/ui/src/components/Button/
├── Button.tsx
├── index.ts
└── __tests__/
    └── Button.test.tsx      ✅ Test in __tests__/
```

### ❌ Don't: Use .spec for unit tests
```
libs/canvas/src/viewport/
└── viewportStore.spec.ts    ❌ .spec for unit test
```

### ✅ Do: Use .test for unit tests, .spec for E2E only
```
libs/canvas/src/viewport/
└── __tests__/
    └── viewportStore.test.ts ✅ .test for unit test

e2e/
└── canvas.spec.ts            ✅ .spec for E2E test
```

### ❌ Don't: Mix test types in same directory
```
libs/ui/src/__tests__/
├── Component.test.tsx        ❌ Mix of unit tests
├── integration.test.tsx      ❌ and integration tests
└── e2e.spec.ts               ❌ and E2E tests
```

### ✅ Do: Separate test types clearly
```
libs/ui/src/
├── __tests__/
│   └── Component.test.tsx    ✅ Unit tests
├── __tests__/integration/
│   └── integration.test.tsx  ✅ Integration tests

e2e/
└── feature.spec.ts           ✅ E2E tests
```

---

## Enforcement

### Git Pre-commit Hook

```bash
#!/bin/bash
# .git/hooks/pre-commit

# Check for co-located test files
if git diff --cached --name-only | grep -E "\.test\.(ts|tsx)$" | grep -v "__tests__"; then
  echo "❌ Error: Test files must be in __tests__/ directories"
  echo "Run: ./scripts/organize-tests.sh"
  exit 1
fi

# Check for .spec files outside e2e/
if git diff --cached --name-only | grep -E "\.spec\.(ts|tsx)$" | grep -v "^e2e/"; then
  echo "❌ Error: .spec files must be E2E tests in e2e/ directory"
  echo "Use .test extension for unit tests"
  exit 1
fi
```

### ESLint Rule (Custom)

```javascript
// .eslintrc.js
module.exports = {
  rules: {
    'yappc/test-location': ['error', {
      unitTests: '**/__tests__/**/*.test.{ts,tsx}',
      e2eTests: 'e2e/**/*.spec.{ts,tsx}'
    }]
  }
};
```

---

## CI/CD Integration

### GitHub Actions (`.github/workflows/test.yml`)

```yaml
name: Tests

on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: pnpm/action-setup@v2
      - uses: actions/setup-node@v3
        with:
          node-version: '20'
          cache: 'pnpm'
      - run: pnpm install
      - run: pnpm test:unit
      - run: pnpm test:coverage

  e2e-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: pnpm/action-setup@v2
      - uses: actions/setup-node@v3
        with:
          node-version: '20'
          cache: 'pnpm'
      - run: pnpm install
      - run: pnpm playwright install --with-deps
      - run: pnpm test:e2e
      - uses: actions/upload-artifact@v3
        if: failure()
        with:
          name: playwright-report
          path: playwright-report/
```

---

## Test Organization Statistics

**Current State (as of 2026-01-31)**:

| Metric | Count |
|--------|-------|
| Tests in `__tests__/` | 322 |
| Co-located tests | 0 ✅ |
| Unit `.spec` files | 0 ✅ |
| E2E `.spec` files | 53 ✅ |
| Test coverage | 75%+ |

---

## Additional Resources

- [Vitest Documentation](https://vitest.dev)
- [Playwright Documentation](https://playwright.dev)
- [React Testing Library](https://testing-library.com/react)
- [Jest Axe (Accessibility)](https://github.com/nickcolley/jest-axe)
- [YAPPC Testing Best Practices](./TESTING_BEST_PRACTICES.md)

---

## Changelog

### 2026-01-31
- **v1.0**: Initial standardized test organization
  - Moved 47 co-located tests to `__tests__/` directories
  - Removed 9 duplicate test files
  - Renamed 24 `.spec` files to `.test` (unit tests)
  - Established clear naming conventions
  - Created automation scripts

---

**Maintained by**: QA Lead  
**Review Cycle**: Quarterly  
**Last Updated**: 2026-01-31

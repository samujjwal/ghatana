# UI Testing Strategy

**Status:** Canonical
**Owner:** QA Team
**Last reviewed:** 2026-05-10
**Supersedes:** N/A
**Superseded by:** N/A

## Overview

This document defines the testing strategy for the Data Cloud UI, covering unit tests, integration tests, and end-to-end tests.

## Testing Pyramid

### Unit Tests

- **Purpose:** Test individual components and hooks in isolation
- **Tool:** Vitest
- **Location:** `src/__tests__/`
- **Coverage:** Aim for 80%+ coverage on critical paths

**Example:**
```typescript
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MyComponent } from '../MyComponent';

describe('MyComponent', () => {
  it('renders correctly', () => {
    render(<MyComponent />);
    expect(screen.getByText('Hello')).toBeInTheDocument();
  });
});
```

### Integration Tests

- **Purpose:** Test component interactions with API services
- **Tool:** Vitest + MSW (Mock Service Worker)
- **Location:** `src/__tests__/integration/`
- **Coverage:** Test all major user flows

### End-to-End Tests

- **Purpose:** Test complete user workflows in a browser
- **Tool:** Playwright
- **Location:** `e2e/`
- **Coverage:** Test critical user paths (login, entity CRUD, connector sync, pipeline execution)

**Example:**
```typescript
import { test, expect } from '@playwright/test';

test('user can create an entity', async ({ page }) => {
  await page.goto('/');
  await page.click('text=Entities');
  await page.click('text=Create');
  await page.fill('[name="name"]', 'Test Entity');
  await page.click('text=Save');
  await expect(page.locator('text=Test Entity')).toBeVisible();
});
```

## Testing Guidelines

### Test-Driven Development

Write tests before implementing features for critical paths. This ensures testability and catches bugs early.

### Mocking Strategy

- **Unit Tests:** Mock API calls using Vitest mocks
- **Integration Tests:** Use MSW to mock HTTP responses
- **E2E Tests:** Use real API (test environment)

### Accessibility Testing

Run accessibility audits on all major pages:
```bash
npx playwright test --accessibility
```

### Performance Testing

Monitor bundle size and load times:
```bash
pnpm build --report
```

## CI/CD Integration

All tests run in CI on every PR:
- Unit tests: fast feedback
- Integration tests: comprehensive coverage
- E2E tests: critical path validation

Tests must pass before merging.

## Test Data Management

Use fixture data for consistent test scenarios:
- Located in `src/__tests__/fixtures/`
- Reusable across test suites
- Versioned with the application

## Coverage Requirements

- **Critical Paths:** 90%+ coverage
- **Components:** 80%+ coverage
- **Hooks:** 90%+ coverage
- **API Services:** 80%+ coverage

## Running Tests

```bash
# Unit tests
pnpm test

# Watch mode
pnpm test:watch

# E2E tests
pnpm test:e2e

# Coverage report
pnpm test:coverage
```

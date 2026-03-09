# E2E Testing Guide - Data Cloud UI

## Overview

This guide covers end-to-end (E2E) testing for the Data Cloud UI using Playwright. E2E tests verify the complete user workflows from the browser perspective.

## Setup

### Prerequisites

- Node.js 18+ installed
- Data Cloud UI dependencies installed (`npm install`)
- Playwright browsers installed (`npx playwright install`)

### Installation

```bash
# Install Playwright and dependencies
npm install -D @playwright/test

# Install Playwright browsers
npx playwright install
```

## Running Tests

### Run All Tests

```bash
# Run all E2E tests
npm run test:e2e

# Run tests in headed mode (see browser)
npm run test:e2e:headed

# Run tests in UI mode (interactive)
npm run test:e2e:ui
```

### Run Specific Tests

```bash
# Run only collections tests
npx playwright test collections

# Run only workflows tests
npx playwright test workflows

# Run only dashboard tests
npx playwright test dashboard
```

### Run Tests in Specific Browser

```bash
# Run in Chromium only
npx playwright test --project=chromium

# Run in Firefox only
npx playwright test --project=firefox

# Run in WebKit (Safari) only
npx playwright test --project=webkit
```

## Test Structure

```
e2e/
├── collections.spec.ts      # Collections CRUD tests
├── workflows.spec.ts        # Workflows tests
├── dashboard.spec.ts        # Dashboard tests
├── fixtures/
│   └── test-data.ts        # Mock data for tests
└── helpers/
    └── api-mocks.ts        # API mocking utilities
```

## Writing Tests

### Basic Test Structure

```typescript
import { test, expect } from "@playwright/test";

test.describe("Feature Name", () => {
  test.beforeEach(async ({ page }) => {
    // Setup before each test
    await page.goto("/feature");
  });

  test("should do something", async ({ page }) => {
    // Test implementation
    await page.click("button");
    await expect(page.locator("h1")).toContainText("Expected Text");
  });
});
```

### Using API Mocks

```typescript
import { mockCollectionsAPI } from "./helpers/api-mocks";

test("should display mocked collections", async ({ page }) => {
  // Mock the API
  await mockCollectionsAPI(page);

  // Navigate to page
  await page.goto("/collections");

  // Verify mocked data is displayed
  await expect(page.locator("text=Products")).toBeVisible();
});
```

### Testing Forms

```typescript
test("should submit form", async ({ page }) => {
  // Fill form fields
  await page.getByLabel("Name").fill("Test Name");
  await page.getByLabel("Description").fill("Test Description");

  // Submit form
  await page.getByRole("button", { name: /submit/i }).click();

  // Verify success
  await expect(page.locator("text=Success")).toBeVisible();
});
```

### Testing Navigation

```typescript
test("should navigate between pages", async ({ page }) => {
  await page.goto("/");

  // Click navigation link
  await page.getByRole("link", { name: "Collections" }).click();

  // Verify URL changed
  await expect(page).toHaveURL(/\/collections/);
});
```

## Best Practices

### 1. Use Data Test IDs

Add `data-testid` attributes to elements for reliable selection:

```tsx
<div data-testid="collection-item">...</div>
```

```typescript
await page.locator('[data-testid="collection-item"]').click();
```

### 2. Wait for Elements

Use Playwright's auto-waiting or explicit waits:

```typescript
// Auto-waiting (preferred)
await expect(page.locator("h1")).toBeVisible();

// Explicit wait
await page.waitForSelector("h1");
```

### 3. Mock External APIs

Always mock external API calls for consistent, fast tests:

```typescript
await page.route("**/api/v1/**", async (route) => {
  await route.fulfill({
    status: 200,
    body: JSON.stringify({ data: [] }),
  });
});
```

### 4. Test User Flows, Not Implementation

Focus on what users do, not how the code works:

```typescript
// Good: Tests user behavior
test("user can create a collection", async ({ page }) => {
  await page.goto("/collections/new");
  await page.fill('[name="name"]', "My Collection");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL("/collections");
});

// Bad: Tests implementation details
test("form state updates correctly", async ({ page }) => {
  // Testing internal state management
});
```

### 5. Keep Tests Independent

Each test should be able to run independently:

```typescript
test.beforeEach(async ({ page }) => {
  // Reset state before each test
  await page.goto("/");
});
```

## Debugging Tests

### Debug Mode

```bash
# Run tests in debug mode
npx playwright test --debug

# Debug specific test
npx playwright test collections --debug
```

### Screenshots and Videos

Tests automatically capture screenshots on failure and videos when configured.

View artifacts in `test-results/` directory.

### Trace Viewer

```bash
# Show trace for failed tests
npx playwright show-trace test-results/trace.zip
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: E2E Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: 18
      - run: npm ci
      - run: npx playwright install --with-deps
      - run: npm run test:e2e
      - uses: actions/upload-artifact@v3
        if: always()
        with:
          name: playwright-report
          path: playwright-report/
```

## Test Coverage

### Current Test Coverage

- ✅ Collections CRUD operations
- ✅ Workflows listing and execution
- ✅ Dashboard metrics and navigation
- ✅ Error handling
- ✅ Empty states

### Planned Coverage

- ⏳ Entity management within collections
- ⏳ Workflow builder
- ⏳ Search functionality
- ⏳ User authentication
- ⏳ Multi-tenant scenarios

## Troubleshooting

### Tests Timing Out

Increase timeout in `playwright.config.ts`:

```typescript
timeout: 60 * 1000, // 60 seconds
```

### Flaky Tests

Use `test.retry()` for flaky tests:

```typescript
test.describe.configure({ retries: 2 });
```

### Port Conflicts

Change dev server port in `playwright.config.ts`:

```typescript
webServer: {
  command: 'npm run dev -- --port 5174',
  url: 'http://localhost:5174',
}
```

## Resources

- [Playwright Documentation](https://playwright.dev)
- [Playwright Best Practices](https://playwright.dev/docs/best-practices)
- [Playwright API Reference](https://playwright.dev/docs/api/class-playwright)

## Support

For issues or questions:

1. Check this guide
2. Review Playwright documentation
3. Check test examples in `e2e/` directory
4. Contact the development team

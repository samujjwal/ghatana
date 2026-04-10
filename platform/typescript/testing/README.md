# `@ghatana/platform-testing`

Shared testing utilities for Ghatana platform — accessibility helpers, WCAG 2.1 AA compliance fixtures, and performance test matchers.

## Overview

Provides test helpers that are reused across all Ghatana product test suites. Use these in unit tests and integration tests wherever accessibility or performance assertions are needed.

## Usage

```ts
import { runAccessibilityAudit, getViolationSummary } from '@ghatana/platform-testing';
// or via the accessibility sub-path:
import { runAccessibilityAudit } from '@ghatana/platform-testing/accessibility';

describe('My Component', () => {
  it('has no accessibility violations', async () => {
    const { container } = render(<MyComponent />);
    const results = await runAccessibilityAudit(container);
    expect(getViolationSummary(results)).toHaveLength(0);
  });
});
```

## API

| Export | Description |
|--------|-------------|
| `runAccessibilityAudit(container)` | Runs axe-core against a DOM element and returns violations |
| `getViolationSummary(results)` | Returns a formatted list of violations for assertion messages |

The package also ships WCAG 2.1 AA test suite fixtures (`wcag-2.1-aa.test.ts`) and performance-under-load matchers (`performance-under-load.test.ts`) runnable via Vitest.

## Installation

```jsonc
// package.json
"devDependencies": {
  "@ghatana/platform-testing": "workspace:*"
}
```

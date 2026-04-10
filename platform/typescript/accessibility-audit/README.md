# `@ghatana/accessibility-audit`

Automated accessibility audit utilities for Ghatana frontend applications.

## Overview

Provides WCAG 2.1 AA audit helpers, axe-core integration wrappers, and CI-ready accessibility testing fixtures shared across all Ghatana products.

## Usage

```ts
import { runAccessibilityAudit, assertNoViolations } from '@ghatana/accessibility-audit';

const results = await runAccessibilityAudit(container);
assertNoViolations(results);
```

## API

| Export | Description |
|--------|-------------|
| `runAccessibilityAudit(container)` | Runs axe-core against the given DOM element |
| `assertNoViolations(results)` | Throws a formatted error if violations are found |

## Installation

This package is a workspace-internal library. Add it via `pnpm`:

```jsonc
// package.json
"devDependencies": {
  "@ghatana/accessibility-audit": "workspace:*"
}
```

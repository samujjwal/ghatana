# @ghatana/eslint-plugin

ESLint plugin enforcing Ghatana monorepo architecture boundaries, platform library adoption, and duplication prevention.

## Installation

This plugin is a workspace package. It is available to all products via the monorepo workspace.

Add to a product's ESLint config:
```json
// package.json (devDependencies)
"@ghatana/eslint-plugin": "workspace:*"
```

## Usage

```js
// eslint.config.mjs (flat config format)
import ghatanaPlugin from '@ghatana/eslint-plugin';

export default [
  ghatanaPlugin.configs.recommended,
  // ... your other configs
];
```

## Configs

| Config | Purpose |
|--------|---------|
| `recommended` | All products — boundary + duplication guardrails |
| `strict` | Strict enforcement — all rules as errors |
| `platform` | Platform packages — includes layer ordering + product import guards |

## Rules

### Architecture Boundaries

| Rule | Severity | Description |
|------|----------|-------------|
| `no-cross-product-imports` | error | Products must not import from each other |
| `enforce-platform-boundaries` | error | Platform layer ordering (foundation → platform → domain) |
| `no-platform-to-product-imports` | error | Platform packages must stay product-agnostic |
| `no-dev-auth-in-prod` | error | devAuth bypass middleware must not reach production |

### Migration Guards

| Rule | Severity | Description |
|------|----------|-------------|
| `no-deprecated-ghatana-ui` | error | `@ghatana/ui` is removed — use `@ghatana/design-system` |
| `no-deleted-v41-packages` | error | V4.1 removed/renamed packages and internal subpaths |
| `no-design-system-internal-reimplementation` | error | Deep `@ghatana/design-system/src/...` subpath imports |

### Duplication Prevention

| Rule | Severity | Description |
|------|----------|-------------|
| `no-banned-libraries` | error | Banned third-party libraries (lodash, axios, moment, etc.) |
| `prefer-platform-utils` | warn | Prefer `@ghatana/platform-utils` over external date/string libs |
| `no-duplicate-utilities` | warn | Reimplementing functions already in `@ghatana/platform-utils` |
| `no-duplicate-components` | warn | Reimplementing UI primitives already in `@ghatana/design-system` |

## Rule Details

### `no-duplicate-utilities`

Flags function definitions whose names match canonical utilities in `@ghatana/platform-utils`:
`truncate`, `capitalize`, `formatDate`, `getCurrentTimestamp`, `cn`, `debounce`, `throttle`, etc.

```ts
// ❌ Bad — reimplements what platform-utils already has
export function truncate(str: string, max: number) { ... }

// ✅ Good
import { truncate } from '@ghatana/platform-utils';
```

### `no-duplicate-components`

Flags exported component declarations whose names match canonical exports from `@ghatana/design-system`:
`Button`, `Input`, `Card`, `Modal`, `Spinner`, `Badge`, etc.

```ts
// ❌ Bad — local Button reimplementation
export function Button({ children }: ButtonProps) { ... }

// ✅ Good
import { Button } from '@ghatana/design-system';
```

### `no-cross-product-imports`

Products are siloed: `@yappc/*`, `@flashit/*`, `@tutorputor/*`, `@data-cloud/*`,
`@dcmaar/*`, `@audio-video/*`, `@aep/*` must not import from each other.

```ts
// ❌ Bad — yappc importing from dcmaar
import { Guardian } from '@dcmaar/types';

// ✅ Good — use platform or shared-services
import { Guardian } from '@ghatana/design-system';
```

## Banned Libraries

| Library | Replacement |
|---------|-------------|
| `lodash` | Native ES6+ or `@ghatana/platform-utils` |
| `axios` | Native `fetch` or `@ghatana/api` |
| `moment` | `date-fns` or `Intl.DateTimeFormat` |
| `jquery` | Native DOM APIs or React refs |
| `uuid` | `crypto.randomUUID()` or `nanoid` |
| `classnames` | `clsx` or `@ghatana/platform-utils/cn` |

# @ghatana/patterns

## Purpose

`@ghatana/patterns` provides reusable workflow and AI UX compositions so product teams can build on shared interaction patterns instead of recreating orchestration and UI flows independently.

## Dependencies

- `@ghatana/platform-events` for shared event-driven workflow contracts
- `@ghatana/primitives` for low-level UI building blocks
- `@ghatana/design-system` for higher-level shared UI components

## Usage

Import pattern groups from the package root or from subpath exports:

```tsx
import * as Patterns from '@ghatana/patterns';
import * as Workflows from '@ghatana/patterns/workflows';
```

Build and validate locally:

```bash
pnpm --filter @ghatana/patterns build
pnpm --filter @ghatana/patterns test
```

## Public API Surface

- Workflow compositions under `./workflows`
- AI UX compositions under `./ai-ux`
- Shared form-oriented patterns under `./forms`
- Root package exports from `src/index.ts`

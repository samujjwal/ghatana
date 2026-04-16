# @ghatana/domain-components

## Purpose

`@ghatana/domain-components` provides domain-specific UI components for the Ghatana platform, with focused surfaces for privacy, security, voice, NLP, and selection experiences that were separated from the general design system to keep boundaries explicit.

## Dependencies

- `@ghatana/platform-utils` for shared utility helpers
- `zod` for schema-backed validation where domain components need typed boundary checks
- React peer dependencies for host application rendering

## Usage

Import from the root package or one of the subpath exports:

```tsx
import * as DomainComponents from '@ghatana/domain-components';
import * as PrivacyComponents from '@ghatana/domain-components/privacy';
```

Build and validate locally:

```bash
pnpm --filter @ghatana/domain-components build
pnpm --filter @ghatana/domain-components test
```

## Public API Surface

- Domain component groups under `./privacy`, `./security`, `./voice`, `./nlp`, and `./selection`
- Root package re-export from `src/index.ts`
- Shared domain-focused UI components intended for product integration
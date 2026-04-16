# @ghatana/forms

## Purpose

`@ghatana/forms` provides shared form components, hooks, and validation helpers for Ghatana React applications, with Zod-based validation support and integration points for `react-hook-form` consumers.

## Dependencies

- `@ghatana/design-system` for shared UI building blocks
- `@ghatana/platform-utils` for common helpers
- `zod` for runtime validation at form boundaries
- Peer dependency on `react-hook-form` for form-state integration

## Usage

Import components, hooks, or validation helpers from the package entry points:

```tsx
import * as Forms from '@ghatana/forms';
import { z } from 'zod';

const ExampleSchema = z.object({
  name: z.string().min(1),
});
```

Build and validate locally:

```bash
pnpm --filter @ghatana/forms build
pnpm --filter @ghatana/forms test
```

## Public API Surface

- Re-exported form components under `./components`
- Shared hooks under `./hooks`
- Validation helpers under `./validation`
- Root package entry point re-exporting all public surfaces from `src/index.ts`
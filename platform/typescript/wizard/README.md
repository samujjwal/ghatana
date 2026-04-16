# @ghatana/wizard

## Purpose

`@ghatana/wizard` provides shared multi-step wizard and stepper components for Ghatana applications that need guided flows with typed step state and validation-aware navigation.

## Dependencies

- `@ghatana/design-system` for shared UI primitives
- `@ghatana/platform-utils` for common helper logic
- `zod` for runtime validation support in wizard flows

## Usage

Import the wizard component and hook from the package entry point:

```tsx
import { Wizard, useWizard, type WizardStep } from '@ghatana/wizard';
```

Build and validate locally:

```bash
pnpm --filter @ghatana/wizard build
pnpm --filter @ghatana/wizard test
```

## Public API Surface

- `Wizard` component
- `useWizard` hook
- Shared wizard types including `WizardProps`, `WizardStep`, `WizardState`, and `WizardActions`
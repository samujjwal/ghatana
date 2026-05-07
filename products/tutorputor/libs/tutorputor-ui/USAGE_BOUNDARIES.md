# Tutorputor UI Package - Usage Boundaries

## Purpose
This document defines the usage boundaries for the `@tutorputor/ui` package to prevent code drift and ensure consistency across Tutorputor applications.

## Shared Components Location
All shared UI primitives are located in `src/components/primitives/`:
- Button
- Input
- Badge
- Spinner
- (Add more as needed)

## Usage Rules

### DO
- **Import shared components from `@tutorputor/ui`**
  ```tsx
  import { Button, Input, Badge } from '@tutorputor/ui';
  ```

- **Add new shared components to the primitives folder**
  When a component is used in multiple apps, move it to `src/components/primitives/`

- **Follow the existing component patterns**
  Use TypeScript interfaces, proper JSDoc with `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern` tags

### DO NOT
- **Duplicate shared components in app-specific folders**
  Do not create `apps/tutorputor-web/src/components/ui/Button.tsx` if Button exists in the shared package

- **Modify shared components without coordination**
  Changes to shared components affect all consuming apps

- **Create app-specific variants without justification**
  If a variant is truly app-specific, keep it local but document why

## Migration Guide

### For Existing App Components
1. Identify components used in multiple apps
2. Move to `libs/tutorputor-ui/src/components/primitives/`
3. Update imports in consuming apps
4. Delete app-specific copies
5. Add documentation to this file

### Example Migration
Before:
```tsx
// apps/tutorputor-web/src/components/ui/Button.tsx
export function Button() { ... }
```

After:
```tsx
// apps/tutorputor-web/src/components/SomeFeature.tsx
import { Button } from '@tutorputor/ui';
```

## Enforcement
- `scripts/validate-ui-boundaries.mjs` runs in CI and blocks:
  - direct subpath imports from `@ghatana/ui`, `@ghatana/theme`, `@ghatana/tokens`, or `@tutorputor/ui/src`;
  - new app-local copies of shared primitives already owned by `@tutorputor/ui`;
  - increases in app-local hardcoded hex colors without reducing or updating the audited baseline.
- `config/ui-boundary-baseline.json` records current migration debt. The count may only stay flat or go down unless a design-system owner explicitly updates this document and the baseline.
- Shared primitive imports should go through `@tutorputor/ui` or the app's documented boundary facade while migration is in progress.

## Component Checklist
When adding a new shared component:
- [ ] Add TypeScript interfaces
- [ ] Add JSDoc with proper tags
- [ ] Add to `primitives/index.ts`
- [ ] Add to main `index.ts`
- [ ] Update this document
- [ ] Update consuming apps

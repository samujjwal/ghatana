# OWNER

- module: products/data-cloud/libs/ui-components
- owner-team: data-cloud-frontend
- primary-contacts:
  - @ghatana/data-cloud-ui
- scope:
  - Reusable UI components and theme helpers for Data Cloud product surfaces
- quality-gates:
  - pnpm --filter @data-cloud/ui-components run type-check
  - pnpm --filter @data-cloud/ui-components run test

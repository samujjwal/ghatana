# Ghatana Studio

Unified Ghatana Studio experience for ideation, blueprinting, development,
lifecycle execution, deployment, health, learning, and evolution.

## Development

```bash
# Install dependencies
pnpm install

# Start dev server
pnpm dev

# Build for production
pnpm build

# Type check
pnpm type-check

# Lint
pnpm lint
```

## Features

### Unified Navigation

Studio uses canonical customer-facing navigation: Home, Ideas, Blueprints,
Canvas, Develop, Lifecycle, Agents, Artifacts, Deployments, Health, Learn, and
Settings. The navigation metadata lives in `src/navigation/studioNavigation.ts`
with route ownership, capability, visibility, and route status.

## Architecture

- Built with React 19 and React Router v7
- Uses @ghatana/design-system for UI components
- Integrates with platform packages:
  - @ghatana/canvas
  - @ghatana/ui-builder
  - @ghatana/ds-generator
  - @ghatana/ds-registry
  - @ghatana/tokens
  - @ghatana/platform-events

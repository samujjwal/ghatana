# Ghatana Studio

Unified maintainer app combining all Phase 6 platform tools:
- Builder Studio
- Theme Studio
- Component Playground
- Canvas Diagnostics
- AI Review Console
- Import/Migration Lab
- Preview Lab

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

### Builder Studio
Create and edit BuilderDocument instances with the UI Builder platform.

### Theme Studio
Materialize and customize design system presets with brand overrides.

### Component Playground
Explore and test design system components with live prop editing.

### Canvas Diagnostics
Inspect canvas plugins, node types, and telemetry events.

### AI Review Console
Review AI operations, confidence scores, and human-in-the-loop decisions.

### Import/Migration Lab
Test code import from JSON, TSX, and HTML with ownership-aware reconciliation.

### Preview Lab
Test preview sandbox with device emulation, CSP validation, and trust checks.

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

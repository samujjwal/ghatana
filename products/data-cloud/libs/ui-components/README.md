# @data-cloud/ui-components

Reusable, presentation-layer UI components extracted from the Data Cloud application. This library contains generic components that can be reused across features without application-level dependencies (no routing, no stores, no services).

## Purpose

Separates reusable presentational components from the `@data-cloud/ui` application so that:
- Components can be independently tested
- Components can be reused across Data Cloud features without pulling in the full application
- Boundaries between presentation layer and application layer are explicit

## Contents

### `@data-cloud/ui-components/common`

| Component | Purpose |
|-----------|---------|
| `AppErrorBoundary` | Global error boundary with graceful fallback UI |
| `Button` | Styled button with variants and loading states |
| `Container` | Responsive layout container |
| `EmptyState` | Empty/no-data state with icon, message, and action |
| `KeyboardShortcuts` | Keyboard shortcuts overlay modal |
| `LoadingState` | Loading indicator with message |
| `StatusBadge` | Semantic status badge using `@ghatana/design-system` |
| `TabWorkspace` | Tabbed workspace layout with context actions |
| `Timeline` | Event timeline with typed event variants |
| `ToastProvider` + `toast` | Toast notification system via `sonner` |

### `@data-cloud/ui-components/cards`

| Component | Purpose |
|-----------|---------|
| `BaseCard` | Generic card container with optional title and actions |
| `KPICard` | KPI metric card with trend indicators |

### `@data-cloud/ui-components/lib`

| Export | Purpose |
|--------|---------|
| Theme utilities | `cn`, `cardStyles`, `textStyles`, etc. from `@ghatana/theme` |

## Usage

```tsx
import { Button, StatusBadge, LoadingState } from '@data-cloud/ui-components/common';
import { KPICard } from '@data-cloud/ui-components/cards';
import { cn, cardStyles } from '@data-cloud/ui-components/lib';
```

## Application-level components

Components with routing, store, or service dependencies remain in `@data-cloud/ui`:
- Pages and routes in `src/pages/`
- Feature components in `src/features/`  
- Store-connected components in `src/components/` that import from `src/stores/`

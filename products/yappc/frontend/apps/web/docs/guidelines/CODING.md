# YAPPC App-Creator Web – Coding Guidelines

## 1. Language & Stack

- Use **TypeScript** in strict mode.
- Use React and Vite as the primary frontend stack.

## 2. State Management

**CRITICAL**: Always use `@yappc/ui/state` for state management.

### Import Path Rules

✅ **CORRECT**:

```typescript
import { useGlobalState, useGlobalStateValue } from '@yappc/ui/state';
```

❌ **WRONG** (Deprecated):

```typescript
// These are deprecated and will trigger ESLint errors
import { useGlobalState } from './state/globalState';
import { useGlobalState } from '@yappc/store';
```

### State Management Patterns

- Use `StateManager` for shared and persistent state, especially canvas/editor state
- Import state hooks directly from `@yappc/ui/state`
- Never import from `apps/*/state/globalState` or `@yappc/store` (backward compatibility only)
- Use proper atom keys as defined in `libs/ui/src/state/atoms.ts`
- For new state, add atoms to the shared library, not local modules

### Example Usage

```typescript
import {
  useGlobalState,
  useGlobalStateValue,
  useSetGlobalState,
} from '@yappc/ui/state';

function MyComponent() {
  // Read and write state
  const [theme, setTheme] = useGlobalState<'light' | 'dark'>('store:theme');

  // Read-only (more performant)
  const sidebarOpen = useGlobalStateValue<boolean>('store:sidebarOpen');

  // Write-only (doesn't subscribe to changes)
  const setSidebar = useSetGlobalState<boolean>('store:sidebarOpen');
}
```

### Available Atom Keys

See `libs/ui/src/state/atoms.ts` for all available atom keys:

- `store:theme` - Theme preference ('light' | 'dark')
- `store:sidebarOpen` - Sidebar visibility (boolean)
- `canvas:document` - Current canvas document
- `canvas:selection` - Selected canvas elements
- And many more...

### Migration from Legacy Modules

If you encounter imports from deprecated modules:

1. Replace the import path with `@yappc/ui/state`
2. Remove any default value arguments (atoms have defaults)
3. Ensure the atom key exists in `libs/ui/src/state/atoms.ts`
4. If needed, add new atoms to the shared library

## 3. Design System

- Use `@ghatana/ui` components and shared tokens for UI.
- Avoid duplicating styles or components that can live in shared libraries.

## 4. Feature-Based Structure

- Organize code by feature domain (canvas, page builder, AI requirements, etc.).
- Keep related components, hooks, and state definitions together.

These guidelines are self-contained and summarize core coding expectations for the app-creator web app.

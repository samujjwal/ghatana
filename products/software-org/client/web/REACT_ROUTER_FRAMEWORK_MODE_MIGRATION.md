# React Router v7 Framework Mode Migration Setup

**Last Updated**: November 25, 2024  
**Status**: Infrastructure Ready (On Hold - Data Mode Active for Stability)  
**Blockers**: Path alias resolution during React Router CLI build  

## Overview

This document describes the React Router v7 Framework Mode migration infrastructure that has been set up for the software-org web application, along with why it's currently disabled and how to complete the migration.

## What is Framework Mode?

React Router v7 Framework Mode provides:
- **File-based routing**: Routes defined in `routes.ts` instead of manual router configuration
- **Automatic code-splitting**: Each route gets its own bundle
- **Better type safety**: Full TypeScript support for routes
- **SSR capability**: Server-side rendering support (future)
- **Unified entry point**: Single `root.tsx` for layout management

## Current Status: Data Mode (Active)

The application is currently using **React Router Data Mode** (traditional approach):
- Manual router creation in `src/app/Router.tsx` with `createBrowserRouter()`
- All routes defined in one place
- Using `react-router-dom` directly
- Building with Vite

**Package.json scripts**:
```bash
"dev": "vite dev",
"build": "vite build",
"preview": "vite preview"
```

## Framework Mode Infrastructure (Ready to Enable)

The following files have been created and configured for Framework Mode:

### 1. `src/app/root.tsx` - Root Route Module

```typescript
// Root route component that wraps all other routes
// Provides MainLayout + App providers + Outlet
export default function Root() {
    return (
        <App>
            <MainLayout>
                <Suspense>
                    <Outlet />
                </Suspense>
            </MainLayout>
        </App>
    );
}
```

**Purpose**: Serves as the entry point for all routes in Framework Mode. Provides:
- Application providers (QueryProvider, Jotai, ThemeProvider, AuthProvider)
- MainLayout component (sidebar, header, theme management)
- Error boundary (ErrorBoundary export)

### 2. `src/app/routes.ts` - Framework Mode Route Configuration

Contains all 15 routes defined using `route()` and `index()` helpers:

```typescript
export const routes: RouteConfig[] = [
    index({ component: HomePage }),
    route("/dashboard", { component: Dashboard }),
    route("/departments", { component: DepartmentList }),
    // ... 12 more routes
    route("/personas/:workspaceId?", {
        component: PersonasPage,
        loader: personasLoader(queryClient),
    }),
];
```

**Routes included**:
1. Home (/)
2. Dashboard (/dashboard)
3. Departments (/departments)
4. Workflows (/workflows)
5. HITL Console (/hitl)
6. Event Simulator (/simulator)
7. Reports (/reports)
8. Security (/security)
9. Models (/models)
10. Settings (/settings)
11. Personas (/personas/:workspaceId?)
12. Help Center (/help)
13. Data Export (/export)
14. Real-Time Monitor (/realtime-monitor)
15. ML Observatory (/ml-observatory)
16. Automation Engine (/automation)

### 3. `react-router.config.ts` - React Router CLI Configuration

```typescript
export default {
    appDirectory: "src/app",
};
```

**Purpose**: Tells the React Router CLI (`react-router dev` and `react-router build` commands) where to find the route modules.

### 4. `src/main.tsx` - Updated App Wrapper

Exports an `App` component that provides all application-level providers:

```typescript
export default function App({ children }: { children: React.ReactNode }) {
    return (
        <StrictMode>
            <ErrorBoundary>
                <QueryProvider>
                    <Provider>
                        <ThemeProvider>
                            <AuthProvider>
                                {children}
                            </AuthProvider>
                        </ThemeProvider>
                    </Provider>
                </QueryProvider>
            </ErrorBoundary>
        </StrictMode>
    );
}
```

## Current Blocker: Path Alias Resolution

### The Problem

When `react-router build` or `react-router dev` runs, it needs to load and analyze the route configuration at build time. However:

1. **routes.ts** imports from `@/state/queryClient`
2. **loaders/persona.loaders.ts** imports from `@/lib/hooks/usePersonaQueries`
3. The React Router CLI uses Vite's ViteNode to load these files
4. ViteNode doesn't automatically respect the path aliases defined in `vite.config.ts`

**Error**:
```
Error: Cannot find package '@/state/queryClient' imported from '...routes.ts'
```

### Why It Matters

The React Router CLI is separate from the Vite dev server. While Vite dev server resolves path aliases through its plugin system, the React Router CLI has its own module resolution that doesn't inherit those aliases.

### Solutions to Complete Migration

#### Solution 1: Fix Path Aliases to Relative Paths (Recommended)

Update imports to use relative paths instead of aliases:

```typescript
// Before (routes.ts)
import { queryClient } from "@/state/queryClient";

// After
import { queryClient } from "../state/queryClient";
```

**Pros**: 
- Works immediately
- No configuration changes needed
- Clean and explicit

**Cons**: 
- Breaks consistency with other files that use @/ aliases
- Makes refactoring harder if directory structure changes

#### Solution 2: Configure ViteNode Path Resolution

Create a custom Vite config for ViteNode that respects aliases. This would require:

1. Extending `react-router.config.ts` to include Vite config
2. Potentially creating a separate module resolution handler

**Pros**: 
- Maintains consistency across codebase
- Follows Vite patterns

**Cons**: 
- More complex setup
- Requires deep understanding of ViteNode

#### Solution 3: Use a Build-Time Alias Plugin

Create a Vite plugin that resolves aliases in the route files during build:

**Pros**: 
- Minimal changes to routes

**Cons**: 
- Adds complexity to build system
- Could have performance implications

## How to Complete the Migration

### Step 1: Choose a Solution

Recommend **Solution 1** (fix path aliases to relative paths) for simplicity and reliability.

### Step 2: Update Imports

Find all `@/` imports in:
- `src/app/routes.ts`
- `src/app/loaders/*.ts`
- Any other files imported by routes at build time

Replace with relative paths:

```bash
# Example for routes.ts
sed -i 's|@/state/queryClient|../state/queryClient|g' src/app/routes.ts
sed -i 's|@/lib/hooks/|../../lib/hooks/|g' src/app/loaders/persona.loaders.ts
```

### Step 3: Update package.json Scripts

```json
{
  "scripts": {
    "dev": "react-router dev",
    "build": "react-router build",
    "preview": "react-router preview"
  }
}
```

### Step 4: Enable Framework Mode Plugin

In `vite.config.ts`:

```typescript
import { reactRouter } from "@react-router/dev/vite";

export default defineConfig({
    plugins: [
        reactRouter({
            appDirectory: "./src/app",
        }),
    ],
    // ... rest of config
});
```

### Step 5: Test Build

```bash
pnpm build
```

Should succeed without errors.

### Step 6: Test Dev Server

```bash
pnpm dev
```

Should start on http://localhost:3000

## Benefits of Framework Mode (Once Enabled)

1. **Better Performance**: Automatic code splitting per route
2. **Type Safety**: Routes are fully typed
3. **Simpler Code**: No manual route configuration needed
4. **SSR Ready**: Foundation for server-side rendering
5. **Better Tooling**: React Router ecosystem full support
6. **Easier Debugging**: Route-based error boundaries

## Files Modified

- `src/app/root.tsx` - Created (was existing but minimal)
- `src/app/routes.ts` - Created
- `react-router.config.ts` - Created
- `src/main.tsx` - Updated with App wrapper export
- `vite.config.ts` - Framework Mode plugin commented out
- `package.json` - Scripts kept in Data Mode, ready to switch

## Files to Update for Full Migration

When completing the migration:

1. `src/app/routes.ts` - Update all `@/` imports to relative paths
2. `src/app/loaders/persona.loaders.ts` - Update all `@/` imports to relative paths
3. `src/app/Router.tsx` - Can be deleted (no longer needed)
4. `src/app/App.tsx` - May need adjustments
5. `vite.config.ts` - Uncomment Framework Mode plugin
6. `package.json` - Switch to `react-router dev/build` commands

## Testing Checklist

Before committing the full migration:

- [ ] `pnpm build` completes without errors
- [ ] `pnpm dev` starts successfully
- [ ] All routes load correctly
- [ ] Lazy loading works for all page components
- [ ] Route loaders execute correctly
- [ ] Error boundaries display errors properly
- [ ] MSW mocking continues to work
- [ ] HMR (hot module replacement) works
- [ ] Sidebar and theme persistence work
- [ ] No console errors or warnings

## References

- [React Router Framework Mode Docs](https://reactrouter.com/start/framework)
- [React Router App Directory Config](https://reactrouter.com/start/framework/routing)
- [Vite Path Aliases](https://vitejs.dev/config/shared-options.html#resolve-alias)

## Questions?

For implementation details or if you encounter other blockers:
1. Check React Router CLI error messages carefully - they usually indicate what's wrong
2. Verify all imports in route files use consistent patterns
3. Test the ViteNode module resolution independently if needed
4. Consider creating a test vite config just for route resolution

## Timeline Recommendation

1. **Short term** (immediate): Infrastructure is ready, keep Data Mode for stability
2. **Medium term** (2-4 weeks): Fix path aliases and enable Framework Mode
3. **Long term** (next sprint): Leverage Framework Mode features (SSR, better code splitting)

---

**Next Steps**: When ready to complete migration, start with Step 1-2 (fix path aliases). Should take <1 hour. Build and test to verify it works.

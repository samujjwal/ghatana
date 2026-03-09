# React Router v7 Framework Mode Migration Guide

**Date**: 2024-11-24  
**Current Version**: React Router v6.26.0  
**Target Version**: React Router v7.9.6 (Framework Mode)  
**Build Tool**: Vite (current)

---

## Overview

React Router v7 introduces **Framework Mode** - a full-stack React framework built on top of React Router. It provides:
- ✅ **File-based routing** (optional but recommended)
- ✅ **Server-side rendering (SSR)** support
- ✅ **Data loading** with loaders
- ✅ **Type-safe routes** with TypeScript
- ✅ **Optimistic UI updates**
- ✅ **Nested layouts** and error boundaries
- ✅ **Vite integration** (our current setup)

**Framework Mode vs SPA Mode**:
- **Framework Mode**: Full-stack app with SSR, loaders, actions (recommended)
- **SPA Mode**: Traditional client-side routing (current v6 behavior)

---

## Migration Steps (2-3 hours)

### Step 1: Upgrade Dependencies (15 min)

**Update package.json**:

```json
{
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "react-router": "^7.9.6",
    "react-router-dom": "^7.9.6",
    "@tanstack/react-query": "^5.90.7",
    "jotai": "^2.15.1",
    "clsx": "^2.1.0",
    "zod": "^4.1.12",
    "react-grid-layout": "^1.5.2"
  },
  "devDependencies": {
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "vite": "^5.4.21",
    "@vitejs/plugin-react": "^4.7.0",
    "typescript": "^5.6.0",
    "vitest": "^2.0.0"
  }
}
```

**Install**:
```bash
cd products/software-org/apps/web
pnpm install react-router@^7.9.6 react-router-dom@^7.9.6
```

---

### Step 2: Enable Framework Mode in Vite (30 min)

**Update vite.config.ts**:

```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { reactRouter } from '@react-router/dev/vite';
import path from 'path';

export default defineConfig({
  plugins: [
    // React Router Framework Mode plugin
    reactRouter(),
    // React plugin for JSX/Fast Refresh
    react(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    host: true,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
});
```

**Install React Router dev dependency**:
```bash
pnpm add -D @react-router/dev
```

---

### Step 3: Create Route Configuration (45 min)

React Router v7 Framework Mode supports:
1. **File-based routes** (recommended) - routes in `app/routes/` directory
2. **Config-based routes** (like v6) - routes in code

**Option A: File-based Routes (Recommended)**

**Create `app/` directory structure**:
```
apps/web/
├── src/
│   ├── components/     # Existing components (keep)
│   ├── lib/           # Existing lib (keep)
│   └── ...
├── app/               # NEW: Framework Mode structure
│   ├── root.tsx       # Root layout
│   ├── routes/
│   │   ├── _index.tsx            # / (home)
│   │   ├── personas.tsx          # /personas
│   │   ├── workspaces.$id.tsx    # /workspaces/:id
│   │   └── settings.tsx          # /settings
│   └── entry.client.tsx  # Client entry
└── vite.config.ts
```

**File: `app/root.tsx`** (Root Layout):

```tsx
import {
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
} from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider as JotaiProvider } from 'jotai';
import './globals.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <Meta />
        <Links />
      </head>
      <body>
        {children}
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  );
}

export default function Root() {
  return (
    <QueryClientProvider client={queryClient}>
      <JotaiProvider>
        <Outlet />
      </JotaiProvider>
    </QueryClientProvider>
  );
}
```

**File: `app/routes/_index.tsx`** (Home Page):

```tsx
import { type MetaFunction } from 'react-router';
import HomePage from '@/pages/HomePage';

export const meta: MetaFunction = () => {
  return [
    { title: 'Software Org - Home' },
    { name: 'description', content: 'Agentic software organization simulation' },
  ];
};

export default function Index() {
  return <HomePage />;
}
```

**File: `app/routes/personas.tsx`** (Personas Page with Loader):

```tsx
import { type LoaderFunctionArgs, type MetaFunction } from 'react-router';
import { useLoaderData } from 'react-router';
import { apiFetch } from '@/lib/api/client';
import PersonasPage from '@/pages/PersonasPage';

export const meta: MetaFunction = () => {
  return [{ title: 'Software Org - Personas' }];
};

export async function loader({ params }: LoaderFunctionArgs) {
  const workspaceId = params.workspaceId || 'default';
  
  try {
    const preferences = await apiFetch(`/api/personas/${workspaceId}`);
    return { preferences };
  } catch (error) {
    // Return null if not found - component will handle
    return { preferences: null };
  }
}

export default function Personas() {
  const { preferences } = useLoaderData<typeof loader>();
  
  return <PersonasPage initialPreferences={preferences} />;
}
```

**File: `app/entry.client.tsx`** (Client Entry):

```tsx
import { StrictMode } from 'react';
import { hydrateRoot } from 'react-dom/client';
import { HydratedRouter } from 'react-router/dom';

hydrateRoot(
  document,
  <StrictMode>
    <HydratedRouter />
  </StrictMode>
);
```

**Option B: Config-based Routes (Like v6)**

If you prefer to keep current route structure, use `routes.ts`:

**File: `app/routes.ts`**:

```typescript
import { type RouteConfig, index, route } from '@react-router/dev/routes';

export default [
  index('routes/_index.tsx'),
  route('personas', 'routes/personas.tsx'),
  route('workspaces/:id', 'routes/workspaces.$id.tsx'),
  route('settings', 'routes/settings.tsx'),
] satisfies RouteConfig;
```

---

### Step 4: Update TypeScript Config (15 min)

**Update tsconfig.json**:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "resolveJsonModule": true,
    "jsx": "react-jsx",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "allowSyntheticDefaultImports": true,
    "forceConsistentCasingInFileNames": true,
    "isolatedModules": true,
    "noEmit": true,
    "paths": {
      "@/*": ["./src/*"],
      "~/*": ["./app/*"]
    },
    "types": ["vite/client", "@react-router/node"]
  },
  "include": ["src", "app"],
  "exclude": ["node_modules"]
}
```

---

### Step 5: Migrate Existing Routes (30 min)

**Current v6 Route Structure** (example):

```tsx
// src/main.tsx (OLD)
import { BrowserRouter, Routes, Route } from 'react-router-dom';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/personas" element={<PersonasPage />} />
        <Route path="/workspaces/:id" element={<WorkspacePage />} />
      </Routes>
    </BrowserRouter>
  );
}
```

**Migrated v7 Framework Mode**:

No more `<BrowserRouter>`, `<Routes>`, or manual route definitions in components. Everything is handled by the framework.

**Migration Checklist**:
1. ✅ Remove `<BrowserRouter>` from main.tsx
2. ✅ Remove `<Routes>` and `<Route>` components
3. ✅ Move page components to `app/routes/`
4. ✅ Add loaders for data fetching
5. ✅ Add meta functions for SEO
6. ✅ Use `<Outlet />` for nested layouts

---

### Step 6: Update Data Fetching Pattern (30 min)

**Before (React Query in component)**:

```tsx
// src/pages/PersonasPage.tsx (OLD)
import { usePersonaConfigs } from '@/hooks/usePersonaConfigs';

function PersonasPage() {
  const { configs, isLoading } = usePersonaConfigs('workspace-123');
  
  if (isLoading) return <Spinner />;
  
  return <div>{/* render configs */}</div>;
}
```

**After (React Router loader + React Query)**:

```tsx
// app/routes/personas.tsx (NEW)
import { type LoaderFunctionArgs } from 'react-router';
import { useLoaderData } from 'react-router';
import { usePersonaConfigs } from '@/hooks/usePersonaConfigs';

export async function loader({ params }: LoaderFunctionArgs) {
  const workspaceId = params.workspaceId || 'default';
  
  // Prefetch data on server/initial load
  const preferences = await apiFetch(`/api/personas/${workspaceId}`);
  return { preferences, workspaceId };
}

export default function Personas() {
  const { preferences, workspaceId } = useLoaderData<typeof loader>();
  
  // Still use React Query for client-side updates
  const { configs } = usePersonaConfigs(workspaceId, {
    initialData: preferences,
  });
  
  return <div>{/* render configs */}</div>;
}
```

**Benefits**:
- ✅ Data loads before component renders (no loading spinner on navigation)
- ✅ Type-safe loader data with `useLoaderData<typeof loader>()`
- ✅ React Query still used for mutations and cache management
- ✅ Optimistic UI updates work seamlessly

---

### Step 7: Update Scripts in package.json (5 min)

```json
{
  "scripts": {
    "dev": "react-router dev",
    "build": "react-router build",
    "start": "react-router-serve ./build/server/index.js",
    "typecheck": "react-router typegen && tsc",
    "lint": "eslint app src --ext ts,tsx",
    "test": "vitest"
  }
}
```

**Key Changes**:
- `vite` → `react-router dev`
- `vite build` → `react-router build`
- New `start` script for production server

---

### Step 8: Test Migration (15 min)

```bash
# Development server
pnpm dev

# Build
pnpm build

# Type check
pnpm typecheck

# Tests
pnpm test
```

**Verify**:
1. ✅ All routes load correctly
2. ✅ Data fetching works (loaders)
3. ✅ React Query still works for mutations
4. ✅ Jotai state management still works
5. ✅ Tests pass
6. ✅ Build succeeds

---

## Key Benefits of React Router v7 Framework Mode

### 1. **Type-Safe Routing**
```tsx
import { useParams } from 'react-router';

// TypeScript knows :id param exists
const { id } = useParams<{ id: string }>();
```

### 2. **Data Loaders (SSR-Ready)**
```tsx
export async function loader({ params }: LoaderFunctionArgs) {
  const data = await fetchData(params.id);
  return { data };
}

// In component
const { data } = useLoaderData<typeof loader>();
```

### 3. **Actions for Mutations**
```tsx
export async function action({ request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const result = await updatePersona(formData);
  return { result };
}

// In component
<Form method="post"> {/* Submits to action */}
  <input name="activeRoles" />
  <button type="submit">Save</button>
</Form>
```

### 4. **Optimistic UI**
```tsx
import { useFetcher } from 'react-router';

const fetcher = useFetcher();

// Optimistic update
<button onClick={() => fetcher.submit(data, { method: 'post' })}>
  {fetcher.state === 'submitting' ? 'Saving...' : 'Save'}
</button>
```

### 5. **Nested Layouts**
```tsx
// app/routes/dashboard.tsx (Layout)
export default function Dashboard() {
  return (
    <div>
      <DashboardNav />
      <Outlet /> {/* Child routes render here */}
    </div>
  );
}

// app/routes/dashboard.personas.tsx (Child)
export default function DashboardPersonas() {
  return <PersonasPage />;
}
```

---

## Migration Gotchas

### 1. **No More `<BrowserRouter>`**
❌ **Old (v6)**:
```tsx
<BrowserRouter>
  <App />
</BrowserRouter>
```

✅ **New (v7)**:
```tsx
// Framework handles routing - no wrapper needed
```

### 2. **Loaders Run on Server (SSR)**
If using environment-specific APIs (localStorage, window), guard them:

```tsx
export async function loader() {
  // ❌ WRONG - window not available on server
  const token = window.localStorage.getItem('token');
  
  // ✅ CORRECT - check environment
  const token = typeof window !== 'undefined'
    ? localStorage.getItem('token')
    : null;
}
```

### 3. **React Query Initial Data Pattern**
```tsx
export async function loader() {
  const data = await fetchData();
  return { data };
}

export default function Page() {
  const { data } = useLoaderData<typeof loader>();
  
  // Pass loader data as initialData to React Query
  const query = useQuery({
    queryKey: ['data'],
    queryFn: fetchData,
    initialData: data, // ← Avoids re-fetch on mount
  });
}
```

---

## Compatibility with Phase 3 Backend

React Router v7 Framework Mode integrates seamlessly with our Fastify backend:

### Data Fetching
```tsx
// app/routes/personas.$workspaceId.tsx
export async function loader({ params }: LoaderFunctionArgs) {
  const preferences = await apiFetch(`/api/personas/${params.workspaceId}`);
  return { preferences };
}
```

### Mutations
```tsx
export async function action({ params, request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const activeRoles = formData.getAll('activeRoles');
  
  await apiFetch(`/api/personas/${params.workspaceId}`, {
    method: 'PUT',
    body: JSON.stringify({ activeRoles }),
  });
  
  return { success: true };
}
```

### WebSocket Integration
WebSocket hooks work the same - no changes needed:

```tsx
import { usePersonaSync } from '@/hooks/usePersonaSync';

export default function Personas() {
  const { workspaceId } = useParams();
  usePersonaSync(workspaceId); // Real-time updates
  
  // Rest of component
}
```

---

## Testing with React Router v7

### Unit Tests (Vitest)
```tsx
import { createMemoryRouter, RouterProvider } from 'react-router';
import { render, screen } from '@testing-library/react';

test('renders personas page', async () => {
  const router = createMemoryRouter(
    [
      {
        path: '/personas',
        element: <PersonasPage />,
        loader: () => ({ preferences: mockPreferences }),
      },
    ],
    { initialEntries: ['/personas'] }
  );
  
  render(<RouterProvider router={router} />);
  
  expect(await screen.findByText('Admin')).toBeInTheDocument();
});
```

### Integration Tests
```tsx
import { createRoutesStub } from 'react-router';

const RouteStub = createRoutesStub([
  {
    path: '/personas/:workspaceId',
    Component: PersonasPage,
    loader: mockLoader,
  },
]);

test('loads persona preferences', async () => {
  render(<RouteStub initialEntries={['/personas/workspace-123']} />);
  // Assertions
});
```

---

## Rollout Plan

### Phase 1: Pre-Migration (30 min)
- ✅ Backup current codebase
- ✅ Document current route structure
- ✅ List all pages and data fetching patterns

### Phase 2: Setup (45 min)
- ✅ Upgrade dependencies
- ✅ Configure Vite plugin
- ✅ Create `app/` directory structure
- ✅ Setup root layout

### Phase 3: Migrate Routes (60 min)
- ✅ Migrate one route at a time
- ✅ Test each route individually
- ✅ Update tests

### Phase 4: Validation (30 min)
- ✅ Run full test suite
- ✅ Manual testing of all routes
- ✅ Performance check (bundle size, load times)

**Total Time**: 2-3 hours

---

## Next Steps

1. **Review this migration plan**
2. **Backup current codebase**
3. **Start with Step 1** (upgrade dependencies)
4. **Migrate incrementally** (one route at a time)
5. **Test thoroughly** after each migration step

**Questions?**
1. Prefer file-based routes or config-based routes?
2. Need SSR (server-side rendering) or SPA mode?
3. Ready to start migration?

---

## Resources

- [React Router v7 Docs](https://reactrouter.com/en/main)
- [Framework Mode Guide](https://reactrouter.com/en/main/start/framework/installation)
- [Migration from v6](https://reactrouter.com/en/main/upgrading/v6)
- [Vite Plugin](https://reactrouter.com/en/main/start/framework/vite)

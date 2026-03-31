# Data Cloud: Migration Guide from Mock API to Real API

**Version:** 1.0  
**Date:** March 30, 2026  
**Estimated Time:** 2-3 hours

---

## Overview

This guide walks through migrating Data Cloud UI from mock API client to the production-ready real API client with proper error handling, authentication, and type safety.

---

## Prerequisites

- ✅ Real API client implemented (`src/api/client.ts`)
- ✅ Backend services running and accessible
- ✅ Authentication token available
- ✅ Environment variables configured

---

## Step 1: Remove Mock Dependencies

### 1.1 Delete Mock Files

```bash
cd products/data-cloud/ui

# Remove mock API files
rm src/lib/mock-api-client.ts
rm src/lib/mock-data.ts
rm src/lib/mock-data.types.ts

# Remove mock test file
rm tests/e2e/mock-data-e2e.test.tsx
```

### 1.2 Update Imports

Find and replace all mock API imports:

```typescript
// OLD - Remove these
import { mockApiClient } from '@/lib/mock-api-client';
import { mockCollections, mockDatasets } from '@/lib/mock-data';

// NEW - Replace with
import { apiClient } from '@/api/client';
```

**Files to Update:**
- `src/components/catalog/CollectionList.tsx`
- `src/components/catalog/DatasetList.tsx`
- `src/components/lineage/LineageViewer.tsx`
- `src/components/query/QueryEditor.tsx`
- `src/hooks/useCollections.ts`
- `src/hooks/useDatasets.ts`

---

## Step 2: Configure API Client

### 2.1 Set Environment Variables

Create or update `.env.local`:

```bash
# API Configuration
VITE_API_URL=http://localhost:8080
VITE_API_TIMEOUT=30000

# Authentication
VITE_AUTH_URL=http://localhost:8080/auth
```

### 2.2 Initialize API Client

Update `src/main.tsx` or `src/App.tsx`:

```typescript
import { apiClient } from './api/client';
import { useAuth } from './hooks/useAuth';

function App() {
  const { token } = useAuth();
  
  // Set auth token when available
  useEffect(() => {
    if (token) {
      apiClient.setToken(token);
    } else {
      apiClient.clearToken();
    }
  }, [token]);
  
  return <RouterProvider router={router} />;
}
```

---

## Step 3: Update Data Fetching Hooks

### 3.1 Collections Hook

**File:** `src/hooks/useCollections.ts`

```typescript
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { SearchParams } from '@/api/client';

export function useCollections(params?: SearchParams) {
  return useQuery({
    queryKey: ['collections', params],
    queryFn: () => apiClient.getCollections(params),
    staleTime: 5 * 60 * 1000, // 5 minutes
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });
}

export function useCollection(id: string) {
  return useQuery({
    queryKey: ['collection', id],
    queryFn: () => apiClient.getCollection(id),
    enabled: !!id,
    staleTime: 5 * 60 * 1000,
  });
}
```

### 3.2 Datasets Hook

**File:** `src/hooks/useDatasets.ts`

```typescript
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';

export function useDatasets(collectionId: string, params?: SearchParams) {
  return useQuery({
    queryKey: ['datasets', collectionId, params],
    queryFn: () => apiClient.getDatasets(collectionId, params),
    enabled: !!collectionId,
    staleTime: 5 * 60 * 1000,
  });
}

export function useDataset(collectionId: string, datasetId: string) {
  return useQuery({
    queryKey: ['dataset', collectionId, datasetId],
    queryFn: () => apiClient.getDataset(collectionId, datasetId),
    enabled: !!collectionId && !!datasetId,
  });
}
```

### 3.3 Lineage Hook

**File:** `src/hooks/useLineage.ts`

```typescript
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';

export function useLineage(datasetId: string, depth: number = 3) {
  return useQuery({
    queryKey: ['lineage', datasetId, depth],
    queryFn: () => apiClient.getLineage(datasetId, depth),
    enabled: !!datasetId,
    staleTime: 10 * 60 * 1000, // 10 minutes (lineage changes less frequently)
  });
}

export function useImpactAnalysis(datasetId: string) {
  return useQuery({
    queryKey: ['impact-analysis', datasetId],
    queryFn: () => apiClient.getImpactAnalysis(datasetId),
    enabled: !!datasetId,
  });
}
```

---

## Step 4: Update Components

### 4.1 Collection List Component

**File:** `src/components/catalog/CollectionList.tsx`

```typescript
import { useCollections } from '@/hooks/useCollections';
import { Spinner } from '@ghatana/ui';

export function CollectionList() {
  const { data: collections, isLoading, error } = useCollections();
  
  if (isLoading) {
    return <Spinner size="lg" />;
  }
  
  if (error) {
    return (
      <div className="error-state">
        <p>Failed to load collections: {error.message}</p>
        <button onClick={() => window.location.reload()}>Retry</button>
      </div>
    );
  }
  
  if (!collections || collections.length === 0) {
    return <EmptyState message="No collections found" />;
  }
  
  return (
    <div className="collection-grid">
      {collections.map((collection) => (
        <CollectionCard key={collection.id} collection={collection} />
      ))}
    </div>
  );
}
```

### 4.2 Query Editor Component

**File:** `src/components/query/QueryEditor.tsx`

```typescript
import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { MonacoSQLEditor } from '@/components/sql-editor';
import { toast } from 'sonner';

export function QueryEditor() {
  const [query, setQuery] = useState('SELECT * FROM users LIMIT 10');
  
  const executeMutation = useMutation({
    mutationFn: (sql: string) => apiClient.executeQuery(sql, 1000),
    onSuccess: (result) => {
      toast.success(`Query executed: ${result.rowCount} rows returned`);
    },
    onError: (error: Error) => {
      toast.error(`Query failed: ${error.message}`);
    },
  });
  
  const handleExecute = () => {
    executeMutation.mutate(query);
  };
  
  return (
    <div className="query-editor">
      <MonacoSQLEditor
        value={query}
        onChange={setQuery}
        onExecute={handleExecute}
        tables={['users', 'orders', 'products']}
      />
      
      {executeMutation.isPending && <Spinner />}
      
      {executeMutation.data && (
        <QueryResults result={executeMutation.data} />
      )}
    </div>
  );
}
```

---

## Step 5: Error Handling

### 5.1 Global Error Boundary

**File:** `src/components/ErrorBoundary.tsx`

```typescript
import { Component, ReactNode } from 'react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error?: Error;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }
  
  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }
  
  componentDidCatch(error: Error, errorInfo: any) {
    console.error('Error caught by boundary:', error, errorInfo);
  }
  
  render() {
    if (this.state.hasError) {
      return (
        <div className="error-boundary">
          <h1>Something went wrong</h1>
          <p>{this.state.error?.message}</p>
          <button onClick={() => window.location.reload()}>
            Reload Page
          </button>
        </div>
      );
    }
    
    return this.props.children;
  }
}
```

### 5.2 API Error Handler

**File:** `src/lib/error-handler.ts`

```typescript
export function handleApiError(error: unknown): string {
  if (error instanceof Error) {
    // Network errors
    if (error.message.includes('fetch')) {
      return 'Network error. Please check your connection.';
    }
    
    // Authentication errors
    if (error.message.includes('401') || error.message.includes('Unauthorized')) {
      return 'Authentication required. Please log in.';
    }
    
    // Permission errors
    if (error.message.includes('403') || error.message.includes('Forbidden')) {
      return 'You do not have permission to perform this action.';
    }
    
    // Not found errors
    if (error.message.includes('404') || error.message.includes('not found')) {
      return 'Resource not found.';
    }
    
    // Server errors
    if (error.message.includes('500')) {
      return 'Server error. Please try again later.';
    }
    
    return error.message;
  }
  
  return 'An unknown error occurred.';
}
```

---

## Step 6: Testing

### 6.1 Update Test Configuration

**File:** `vitest.config.ts`

```typescript
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    globals: true,
  },
});
```

**File:** `tests/setup.ts`

```typescript
import { beforeAll, afterEach, afterAll } from 'vitest';
import { server } from './mocks/server';

// Start MSW server before all tests
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

// Reset handlers after each test
afterEach(() => server.resetHandlers());

// Clean up after all tests
afterAll(() => server.close());
```

### 6.2 Create MSW Handlers

**File:** `tests/mocks/handlers.ts`

```typescript
import { http, HttpResponse } from 'msw';

const API_URL = 'http://localhost:8080';

export const handlers = [
  // Collections
  http.get(`${API_URL}/api/v1/collections`, () => {
    return HttpResponse.json([
      {
        id: 'col-1',
        name: 'Test Collection',
        description: 'Test description',
        schema: {},
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        createdBy: 'user-1',
      },
    ]);
  }),
  
  // Datasets
  http.get(`${API_URL}/api/v1/collections/:collectionId/datasets`, () => {
    return HttpResponse.json([
      {
        id: 'ds-1',
        collectionId: 'col-1',
        name: 'Test Dataset',
        format: 'parquet',
        location: 's3://bucket/path',
        size: 1024,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    ]);
  }),
  
  // Query execution
  http.post(`${API_URL}/api/v1/query/execute`, async ({ request }) => {
    const body = await request.json();
    return HttpResponse.json({
      columns: ['id', 'name'],
      rows: [[1, 'Test']],
      rowCount: 1,
      executionTime: 0.5,
    });
  }),
];
```

### 6.3 Integration Tests

**File:** `tests/integration/collections.test.tsx`

```typescript
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CollectionList } from '@/components/catalog/CollectionList';

describe('CollectionList Integration', () => {
  it('should load and display collections', async () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });
    
    render(
      <QueryClientProvider client={queryClient}>
        <CollectionList />
      </QueryClientProvider>
    );
    
    // Should show loading state
    expect(screen.getByRole('status')).toBeInTheDocument();
    
    // Should show collections after loading
    await waitFor(() => {
      expect(screen.getByText('Test Collection')).toBeInTheDocument();
    });
  });
});
```

---

## Step 7: Verification Checklist

### Pre-Migration
- [ ] Backend API is running and accessible
- [ ] Authentication service is configured
- [ ] Environment variables are set
- [ ] Dependencies are installed (`pnpm install`)

### During Migration
- [ ] All mock imports removed
- [ ] Real API client imported
- [ ] TanStack Query hooks created
- [ ] Components updated to use hooks
- [ ] Error handling implemented
- [ ] Loading states added

### Post-Migration
- [ ] Application builds without errors
- [ ] Collections load correctly
- [ ] Datasets load correctly
- [ ] Lineage visualization works
- [ ] Query execution works
- [ ] Error states display properly
- [ ] Loading states display properly
- [ ] Authentication works
- [ ] All tests pass

---

## Step 8: Rollback Plan

If issues occur, rollback steps:

```bash
# 1. Revert to previous commit
git revert HEAD

# 2. Restore mock files from git history
git checkout HEAD~1 -- src/lib/mock-api-client.ts
git checkout HEAD~1 -- src/lib/mock-data.ts

# 3. Reinstall dependencies
pnpm install

# 4. Rebuild
pnpm build
```

---

## Common Issues & Solutions

### Issue: "Network Error" on API Calls

**Solution:**
1. Check backend is running: `curl http://localhost:8080/health`
2. Verify CORS is configured on backend
3. Check network tab in browser DevTools

### Issue: "401 Unauthorized"

**Solution:**
1. Verify token is being set: `console.log(apiClient.token)`
2. Check token expiration
3. Refresh authentication

### Issue: Type Errors

**Solution:**
1. Run `pnpm type-check`
2. Ensure Zod schemas match backend responses
3. Update type definitions if API changed

### Issue: Slow Performance

**Solution:**
1. Check TanStack Query cache configuration
2. Adjust `staleTime` and `cacheTime`
3. Implement pagination for large datasets

---

## Performance Optimization

### Caching Strategy

```typescript
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000, // 5 minutes
      cacheTime: 10 * 60 * 1000, // 10 minutes
      refetchOnWindowFocus: false,
      refetchOnReconnect: true,
    },
  },
});
```

### Prefetching

```typescript
// Prefetch collections on app load
queryClient.prefetchQuery({
  queryKey: ['collections'],
  queryFn: () => apiClient.getCollections(),
});
```

### Infinite Queries for Pagination

```typescript
export function useInfiniteCollections() {
  return useInfiniteQuery({
    queryKey: ['collections-infinite'],
    queryFn: ({ pageParam = 1 }) =>
      apiClient.getCollections({ page: pageParam, pageSize: 20 }),
    getNextPageParam: (lastPage, pages) =>
      lastPage.length === 20 ? pages.length + 1 : undefined,
  });
}
```

---

## Next Steps

After successful migration:

1. **Monitor Performance**
   - Set up error tracking (Sentry)
   - Monitor API response times
   - Track user experience metrics

2. **Optimize Queries**
   - Implement query batching
   - Add request deduplication
   - Optimize cache strategy

3. **Enhance Error Handling**
   - Add retry logic for failed requests
   - Implement offline support
   - Add better error messages

4. **Documentation**
   - Update API documentation
   - Document common workflows
   - Create troubleshooting guide

---

**Migration Complete!** 🎉

The Data Cloud UI is now using the production-ready real API client with proper error handling, authentication, and type safety.

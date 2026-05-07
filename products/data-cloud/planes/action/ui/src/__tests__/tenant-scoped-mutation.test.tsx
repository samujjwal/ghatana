/**
 * Tenant-scoped mutation tests — assert active tenant passed for all mutations.
 */
import { describe, expect, it, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useTenantScopedMutation } from '@/components/shared/TenantScopedMutation';
import { Provider, createStore } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function createWrapper(initialTenantId: string) {
  const store = createStore();
  store.set(tenantIdAtom, initialTenantId);

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <Provider store={store}>{children}</Provider>
      </QueryClientProvider>
    );
  };
}

describe('Tenant-scoped mutation', () => {
  it('throws when tenantId is empty', async () => {
    const { result } = renderHook(
      () => useTenantScopedMutation<string, Error, Record<string, unknown>>(async () => 'ok'),
      { wrapper: createWrapper('') },
    );
    await expect(result.current.mutateAsync({})).rejects.toThrow('Tenant scope is required');
  });

  it('passes tenantId to mutationFn when tenantId is present', async () => {
    let receivedTenantId: string | undefined;
    const { result } = renderHook(
      () => useTenantScopedMutation<string, Error, Record<string, unknown>>(async (vars) => {
        receivedTenantId = vars.tenantId;
        return 'ok';
      }),
      { wrapper: createWrapper('tenant-42') },
    );
    await result.current.mutateAsync({});
    expect(receivedTenantId).toBe('tenant-42');
  });

  it('passes merged variables and tenantId', async () => {
    let receivedVars: Record<string, unknown> | undefined;
    const { result } = renderHook(
      () => useTenantScopedMutation<string, Error, { name: string }>(async (vars) => {
        receivedVars = { tenantId: vars.tenantId, name: vars.name };
        return 'ok';
      }),
      { wrapper: createWrapper('tenant-99') },
    );
    await result.current.mutateAsync({ name: 'test-pipeline' });
    expect(receivedVars).toEqual({ tenantId: 'tenant-99', name: 'test-pipeline' });
  });
});

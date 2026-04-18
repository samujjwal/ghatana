/**
 * Tests for useSchemaSuggestion hook
 */

import { describe, it, expect } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SCHEMA_SUGGESTION_BOUNDARY_MESSAGE } from '@/lib/runtime-boundaries';
import {
  useSchemaSuggestion,
} from '../useSchemaSuggestion';

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useSchemaSuggestion', () => {
  it('fails explicitly because the legacy hook has no canonical launcher route', async () => {
    const { result } = renderHook(() => useSchemaSuggestion(), { wrapper: createWrapper() });

    const sampleData = [
      { id: '1', name: 'Test' },
      { id: '2', name: 'Test 2' },
    ];

    await expect(result.current.mutateAsync({ samples: sampleData })).rejects.toThrow(
      SCHEMA_SUGGESTION_BOUNDARY_MESSAGE,
    );
  });

  it('exposes the boundary error through mutation state', async () => {
    const { result } = renderHook(() => useSchemaSuggestion(), { wrapper: createWrapper() });

    await act(async () => {
      await result.current.mutateAsync({ samples: [{ id: '1', name: 'Test' }] }).catch(() => undefined);
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
      expect(result.current.error?.message).toBe(SCHEMA_SUGGESTION_BOUNDARY_MESSAGE);
    });
  });
});

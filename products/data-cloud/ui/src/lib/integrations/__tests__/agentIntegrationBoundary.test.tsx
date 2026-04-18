import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import React from 'react';
import { describe, expect, it } from 'vitest';
import { BRAIN_INTEGRATION_BOUNDARY_MESSAGE } from '@/lib/runtime-boundaries';

import {
  useBrainAgents,
} from '../agent-integration';

function Wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe('agent-integration launcher boundaries', () => {
  it('surfaces an explicit boundary error for unsupported brain agent hooks', async () => {
    const { result } = renderHook(() => useBrainAgents(), { wrapper: Wrapper });

    await waitFor(() => {
      expect(result.current.error).not.toBeNull();
    });

    expect(result.current.brainAgents).toEqual([]);
    expect((result.current.error as Error).message).toBe(BRAIN_INTEGRATION_BOUNDARY_MESSAGE);
  });
});
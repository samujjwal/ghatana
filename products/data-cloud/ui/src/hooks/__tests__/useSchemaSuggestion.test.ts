/**
 * Tests for useSchemaSuggestion hook
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useSchemaSuggestion } from '../useSchemaSuggestion';
import { apiClient } from '../../lib/api/client';

// Mock apiClient
vi.mock('../../lib/api/client', () => ({
  apiClient: {
    post: vi.fn(),
  },
}));

const mockedPost = vi.mocked(apiClient.post);

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useSchemaSuggestion', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls schema suggestion API with sample data', async () => {
    mockedPost.mockResolvedValueOnce({
      suggestedFields: [
        { name: 'id', type: 'string', required: true },
        { name: 'name', type: 'string', required: true },
      ],
      confidence: 0.95,
    });

    const { result } = renderHook(() => useSchemaSuggestion(), { wrapper: createWrapper() });

    const sampleData = [
      { id: '1', name: 'Test' },
      { id: '2', name: 'Test 2' },
    ];

    await act(async () => {
      const response = await result.current.mutateAsync({ samples: sampleData });
      expect(response.suggestedFields).toHaveLength(2);
      expect(response.confidence).toBe(0.95);
    });

    expect(mockedPost).toHaveBeenCalledWith('/schema/suggest', { samples: sampleData });
  });

  it('handles API errors gracefully', async () => {
    mockedPost.mockRejectedValueOnce(new Error('API Error'));

    const { result } = renderHook(() => useSchemaSuggestion(), { wrapper: createWrapper() });

    const sampleData = [{ id: '1', name: 'Test' }];

    await expect(result.current.mutateAsync({ samples: sampleData })).rejects.toThrow('API Error');
  });

  it('shows loading state during mutation', async () => {
    mockedPost.mockImplementation(() => new Promise(() => {}));

    const { result } = renderHook(() => useSchemaSuggestion(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ samples: [{ id: '1', name: 'Test' }] });
    });

    await waitFor(() => {
      expect(result.current.isPending).toBe(true);
    });
  });
});

/**
 * Tests for useSchemaSuggestion hook
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useSchemaSuggestion } from '../useSchemaSuggestion';

// Mock apiClient
vi.mock('../../lib/api/client', () => ({
  apiClient: {
    post: vi.fn(),
  },
}));

describe('useSchemaSuggestion', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls schema suggestion API with sample data', async () => {
    const { apiClient } = require('../../lib/api/client');
    apiClient.post.mockResolvedValueOnce({
      suggestedFields: [
        { name: 'id', type: 'string', required: true },
        { name: 'name', type: 'string', required: true },
      ],
      confidence: 0.95,
    });

    const { result } = renderHook(() => useSchemaSuggestion());

    const sampleData = [
      { id: '1', name: 'Test' },
      { id: '2', name: 'Test 2' },
    ];

    await act(async () => {
      const response = await result.current.mutateAsync({ samples: sampleData });
      expect(response.suggestedFields).toHaveLength(2);
      expect(response.confidence).toBe(0.95);
    });

    expect(apiClient.post).toHaveBeenCalledWith('/schema/suggest', { samples: sampleData });
  });

  it('handles API errors gracefully', async () => {
    const { apiClient } = require('../../lib/api/client');
    apiClient.post.mockRejectedValueOnce(new Error('API Error'));

    const { result } = renderHook(() => useSchemaSuggestion());

    const sampleData = [{ id: '1', name: 'Test' }];

    await expect(result.current.mutateAsync({ samples: sampleData })).rejects.toThrow('API Error');
  });

  it('shows loading state during mutation', async () => {
    const { apiClient } = require('../../lib/api/client');
    apiClient.post.mockImplementation(() => new Promise(() => {}));

    const { result } = renderHook(() => useSchemaSuggestion());

    act(() => {
      result.current.mutate({ samples: [{ id: '1', name: 'Test' }] });
    });

    expect(result.current.isPending).toBe(true);
  });
});

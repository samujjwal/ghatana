/**
 * Tests for useCapabilities hook (F-024 / SIMP-10).
 * Verifies that the hook fetches and returns the server capability manifest,
 * and falls back gracefully when the backend is unreachable.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useCapabilities } from '../useCapabilities';
import * as httpClient from '@/lib/http-client';
function wrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return React.createElement(QueryClientProvider, { client: queryClient }, children);
  };
}
describe('useCapabilities', () => {
  let queryClient: QueryClient;
  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.restoreAllMocks();
  });
  it('returns server capabilities when fetch succeeds', async () => {
    const mockCapabilities = {
      dataCloud: true,
      redis: true,
      analyticsStore: false,
      aiSuggestions: true,
      nlpParse: true,
      gdprCompliance: true,
      soc2Compliance: true,
      piiEnforcement: true,
      killSwitch: true,
      gracefulDegradation: true,
      policyEngine: true,
      episodeLearning: true,
      humanInTheLoop: true,
      serverSideConsent: true,
      durableSessions: true,
      sseStreaming: true,
    };
    vi.spyOn(httpClient.apiClient, 'get').mockResolvedValueOnce({
      data: {
        tenantId: 'acme-corp',
        capabilities: mockCapabilities,
        generatedAt: '2026-04-27T00:00:00Z',
      },
    } as never);
    const { result } = renderHook(() => useCapabilities(), {
      wrapper: wrapper(queryClient),
    });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.capabilities.dataCloud).toBe(true);
    expect(result.current.capabilities.soc2Compliance).toBe(true);
    expect(result.current.generatedAt).toBe('2026-04-27T00:00:00Z');
  });
  it('returns isDegraded=false when dataCloud is enabled', async () => {
    vi.spyOn(httpClient.apiClient, 'get').mockResolvedValueOnce({
      data: {
        tenantId: 'acme-corp',
        capabilities: {
          dataCloud: true,
          redis: true,
          analyticsStore: true,
          aiSuggestions: true,
          nlpParse: true,
          gdprCompliance: true,
          soc2Compliance: true,
          piiEnforcement: true,
          killSwitch: true,
          gracefulDegradation: true,
          policyEngine: true,
          episodeLearning: true,
          humanInTheLoop: true,
          serverSideConsent: true,
          durableSessions: true,
          sseStreaming: true,
        },
        generatedAt: '2026-04-27T00:00:00Z',
      },
    } as never);
    const { result } = renderHook(() => useCapabilities(), {
      wrapper: wrapper(queryClient),
    });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.isDegraded).toBe(false);
  });
  it('returns conservative defaults and isDegraded=true when fetch fails', async () => {
    vi.spyOn(httpClient.apiClient, 'get').mockRejectedValueOnce(new Error('Network error'));
    const { result } = renderHook(() => useCapabilities(), {
      wrapper: wrapper(queryClient),
    });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    // isDegraded because isError=true
    expect(result.current.isDegraded).toBe(true);
    // Conservative defaults for safety-critical flags
    expect(result.current.capabilities.piiEnforcement).toBe(true);
    expect(result.current.capabilities.killSwitch).toBe(true);
    expect(result.current.generatedAt).toBeNull();
  });
  it('starts with isLoading=true before data arrives', () => {
    // Return a never-resolving promise
    vi.spyOn(httpClient.apiClient, 'get').mockReturnValueOnce(new Promise(() => {}) as never);
    const { result } = renderHook(() => useCapabilities(), {
      wrapper: wrapper(queryClient),
    });
    expect(result.current.isLoading).toBe(true);
  });
});

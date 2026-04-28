/**
 * Tests for RuntimeTruthBanner (F-040).
 * Verifies that the banner renders capability badges based on the server manifest
 * and shows the degraded warning when dataCloud is unavailable.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RuntimeTruthBanner } from '../RuntimeTruthBanner';
import * as httpClient from '@/lib/http-client';

function Wrapper({ queryClient }: { queryClient: QueryClient }) {
  return function ({ children }: { children: React.ReactNode }) {
    return React.createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe('RuntimeTruthBanner', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.restoreAllMocks();
  });

  it('returns null while loading', () => {
    vi.spyOn(httpClient.apiClient, 'get').mockReturnValueOnce(new Promise(() => {}) as never);

    const { container } = render(
      React.createElement(RuntimeTruthBanner),
      { wrapper: Wrapper({ queryClient }) },
    );

    expect(container.firstChild).toBeNull();
  });

  it('renders capability badges after data loads', async () => {
    vi.spyOn(httpClient.apiClient, 'get').mockResolvedValueOnce({
      data: {
        tenantId: 'acme',
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

    render(
      React.createElement(RuntimeTruthBanner),
      { wrapper: Wrapper({ queryClient }) },
    );

    await waitFor(() => {
      expect(screen.getByTestId('runtime-truth-banner')).toBeDefined();
    });

    expect(screen.getByText(/Data Cloud/i)).toBeDefined();
    expect(screen.getByText(/PII block/i)).toBeDefined();
    expect(screen.getByText(/SOC2/i)).toBeDefined();
  });

  it('shows degraded warning when dataCloud is false', async () => {
    vi.spyOn(httpClient.apiClient, 'get').mockResolvedValueOnce({
      data: {
        tenantId: 'acme',
        capabilities: {
          dataCloud: false,
          redis: false,
          analyticsStore: false,
          aiSuggestions: true,
          nlpParse: true,
          gdprCompliance: false,
          soc2Compliance: false,
          piiEnforcement: true,
          killSwitch: true,
          gracefulDegradation: true,
          policyEngine: true,
          episodeLearning: false,
          humanInTheLoop: true,
          serverSideConsent: true,
          durableSessions: false,
          sseStreaming: true,
        },
        generatedAt: '2026-04-27T00:00:00Z',
      },
    } as never);

    render(
      React.createElement(RuntimeTruthBanner),
      { wrapper: Wrapper({ queryClient }) },
    );

    await waitFor(() => {
      expect(screen.getByTestId('runtime-truth-banner')).toBeDefined();
    });

    expect(screen.getByText(/Non-durable capabilities detected/i)).toBeDefined();
  });

  it('shows degraded warning when fetch fails', async () => {
    vi.spyOn(httpClient.apiClient, 'get').mockRejectedValueOnce(new Error('Network error'));

    render(
      React.createElement(RuntimeTruthBanner),
      { wrapper: Wrapper({ queryClient }) },
    );

    await waitFor(() => {
      expect(screen.getByTestId('runtime-truth-banner')).toBeDefined();
    });

    expect(screen.getByText(/Non-durable capabilities detected/i)).toBeDefined();
  });
});


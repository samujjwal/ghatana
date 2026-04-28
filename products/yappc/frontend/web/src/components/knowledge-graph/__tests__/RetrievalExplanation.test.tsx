/**
 * RetrievalExplanation tests (AI-Y7)
 */

import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { RetrievalExplanation } from '../RetrievalExplanation';
import type { RetrievalExplanationData } from '../RetrievalExplanation';

// ── Mock fetch ─────────────────────────────────────────────────────────────────

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

function jsonOk(data: unknown) {
  return Promise.resolve({
    ok: true,
    status: 200,
    json: () => Promise.resolve(data),
  } as Response);
}

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function Wrapper({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={makeClient()}>{children}</QueryClientProvider>;
}

const sampleData: RetrievalExplanationData = {
  runId: 'run-1',
  query: 'user authentication flow',
  nodes: [
    {
      nodeId: 'node-auth',
      nodeLabel: 'AuthService',
      nodeType: 'ENTITY',
      score: 0.92,
      matchedContext: 'Handles JWT token validation and refresh.',
      retrievalReason: 'Directly related to authentication requirements',
    },
    {
      nodeId: 'node-jwt',
      nodeLabel: 'JwtUtils',
      nodeType: 'ENTITY',
      score: 0.74,
      matchedContext: 'Utility for encoding/decoding JWT tokens.',
      retrievalReason: 'Helper used by AuthService',
    },
  ],
};

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('RetrievalExplanation (AI-Y7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows loading state initially', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));

    render(
      <Wrapper>
        <RetrievalExplanation runId="run-1" />
      </Wrapper>
    );

    expect(screen.getByTestId('retrieval-loading')).toBeInTheDocument();
  });

  it('renders retrieved nodes with score and reason', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleData));

    render(
      <Wrapper>
        <RetrievalExplanation runId="run-1" />
      </Wrapper>
    );

    const panel = await screen.findByTestId('retrieval-explanation');
    expect(panel).toBeInTheDocument();

    expect(screen.getByTestId('retrieval-node-node-auth')).toBeInTheDocument();
    expect(screen.getByText('AuthService')).toBeInTheDocument();
    expect(screen.getByText('92% match')).toBeInTheDocument();
    expect(screen.getByText('Directly related to authentication requirements')).toBeInTheDocument();
  });

  it('shows empty state when no nodes returned', async () => {
    mockFetch.mockReturnValue(jsonOk({ runId: 'run-1', query: 'q', nodes: [] }));

    render(
      <Wrapper>
        <RetrievalExplanation runId="run-1" />
      </Wrapper>
    );

    expect(await screen.findByTestId('retrieval-empty')).toBeInTheDocument();
  });

  it('shows error state on fetch failure', async () => {
    mockFetch.mockReturnValue(Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) }));

    render(
      <Wrapper>
        <RetrievalExplanation runId="run-1" />
      </Wrapper>
    );

    expect(await screen.findByTestId('retrieval-error')).toBeInTheDocument();
  });

  it('does not fetch when runId is empty', () => {
    render(
      <Wrapper>
        <RetrievalExplanation runId="" />
      </Wrapper>
    );

    expect(mockFetch).not.toHaveBeenCalled();
  });
});

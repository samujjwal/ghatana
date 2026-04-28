/**
 * CodebaseKnowledgeSurface tests (F-Y035)
 */

import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { CodebaseKnowledgeSurface } from '../CodebaseKnowledgeSurface';
import type { CodebaseKnowledgeData } from '../CodebaseKnowledgeSurface';

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

function jsonOk(data: unknown) {
  return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(data) } as Response);
}

const sampleData: CodebaseKnowledgeData = {
  projectId: 'proj-1',
  entitySummary: [
    { type: 'Service', count: 12 },
    { type: 'Function', count: 84 },
  ],
  relationCount: 47,
  cacheStats: { totalEntries: 230, hitRateLast24h: 0.72, topQueryTypes: ['semantic', 'fact'] },
  lastIndexedAt: '2026-04-27T10:00:00Z',
  indexedFilesCount: 156,
  source: 'model',
  confidence: 0.91,
};

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}
function Wrapper({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={makeClient()}>{children}</QueryClientProvider>;
}

describe('CodebaseKnowledgeSurface (F-Y035)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows loading while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<Wrapper><CodebaseKnowledgeSurface projectId="proj-1" /></Wrapper>);
    expect(screen.getByTestId('knowledge-loading')).toBeInTheDocument();
  });

  it('renders entities and cache stats after fetch', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleData));
    render(<Wrapper><CodebaseKnowledgeSurface projectId="proj-1" /></Wrapper>);

    await waitFor(() => expect(screen.getByTestId('knowledge-content')).toBeInTheDocument());
    expect(screen.getByTestId('entity-type-service')).toBeInTheDocument();
    expect(screen.getByTestId('relation-count')).toHaveTextContent('47');
    expect(screen.getByTestId('knowledge-cache')).toBeInTheDocument();
  });

  it('shows retrieval explanation link when latestRunId provided', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleData));
    render(<Wrapper><CodebaseKnowledgeSurface projectId="proj-1" latestRunId="run-1" /></Wrapper>);

    await waitFor(() => expect(screen.getByTestId('retrieval-explanation-link')).toBeInTheDocument());
  });

  it('does not show retrieval link without latestRunId', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleData));
    render(<Wrapper><CodebaseKnowledgeSurface projectId="proj-1" /></Wrapper>);

    await waitFor(() => expect(screen.getByTestId('knowledge-content')).toBeInTheDocument());
    expect(screen.queryByTestId('retrieval-explanation-link')).toBeNull();
  });

  it('shows error state on failure', async () => {
    mockFetch.mockReturnValue(Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) }));
    render(<Wrapper><CodebaseKnowledgeSurface projectId="proj-1" /></Wrapper>);

    expect(await screen.findByTestId('knowledge-error')).toBeInTheDocument();
  });

  it('renders null when projectId is empty', () => {
    const { container } = render(<Wrapper><CodebaseKnowledgeSurface projectId="" /></Wrapper>);
    expect(container.firstChild).toBeNull();
  });
});

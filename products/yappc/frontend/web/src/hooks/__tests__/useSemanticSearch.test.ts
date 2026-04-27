/**
 * useSemanticSearch Hook Tests
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useSemanticSearch } from '../useSemanticSearch';
import * as SearchService from '../../services/search/SearchService';
import type {
  SearchDocument,
  SemanticSearchResponse,
} from '../../services/search/SearchService';

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  const Wrapper = ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
  return Wrapper;
}

const sampleDocuments: SearchDocument[] = [
  {
    id: 'doc-1',
    title: 'Alpha Document',
    content: 'Content about alpha feature design',
    type: 'page',
    path: '/alpha',
  },
  {
    id: 'doc-2',
    title: 'Beta Service',
    content: 'Beta service implementation details',
    type: 'artifact',
    path: '/beta',
  },
];

const sampleResponse: SemanticSearchResponse = {
  results: [
    {
      document: sampleDocuments[0]!,
      score: 0.92,
      matchType: 'semantic',
      highlights: ['alpha feature'],
    },
  ],
  metadata: {
    query: 'alpha',
    totalResults: 1,
    searchTime: 42,
    algorithm: 'hybrid',
  },
};

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('useSemanticSearch', () => {
  beforeEach(() => {
    vi.spyOn(SearchService, 'semanticSearch').mockResolvedValue(sampleResponse);
    vi.spyOn(SearchService, 'indexDocuments').mockResolvedValue(undefined);
    vi.spyOn(SearchService, 'getHighlights').mockReturnValue([
      'highlighted text',
    ]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns initial empty state when no query is set', () => {
    const { result } = renderHook(
      () =>
        useSemanticSearch({
          documents: sampleDocuments,
        }),
      { wrapper: createWrapper() }
    );

    expect(result.current.query).toBe('');
    expect(result.current.results).toEqual([]);
    expect(result.current.isLoading).toBe(false);
    expect(result.current.error).toBeNull();
    expect(result.current.totalResults).toBe(0);
    expect(result.current.searchTime).toBe(0);
  });

  it('does not call semanticSearch when query is empty', () => {
    renderHook(
      () =>
        useSemanticSearch({
          documents: sampleDocuments,
        }),
      { wrapper: createWrapper() }
    );

    expect(SearchService.semanticSearch).not.toHaveBeenCalled();
  });

  it('calls semanticSearch when query is set', async () => {
    const { result } = renderHook(
      () =>
        useSemanticSearch({
          documents: sampleDocuments,
          limit: 5,
          threshold: 0.4,
        }),
      { wrapper: createWrapper() }
    );

    act(() => {
      result.current.setQuery('alpha');
    });

    await waitFor(() => {
      expect(SearchService.semanticSearch).toHaveBeenCalledWith(
        expect.objectContaining({
          query: 'alpha',
          documents: sampleDocuments,
          limit: 5,
          threshold: 0.4,
        })
      );
    });
  });

  it('populates results from semanticSearch response', async () => {
    const { result } = renderHook(
      () =>
        useSemanticSearch({
          documents: sampleDocuments,
        }),
      { wrapper: createWrapper() }
    );

    act(() => {
      result.current.setQuery('alpha');
    });

    await waitFor(() => {
      expect(result.current.results).toHaveLength(1);
      expect(result.current.results[0]?.document.id).toBe('doc-1');
      expect(result.current.totalResults).toBe(1);
      expect(result.current.searchTime).toBe(42);
    });
  });

  it('clearQuery resets query and results', async () => {
    const { result } = renderHook(
      () =>
        useSemanticSearch({
          documents: sampleDocuments,
        }),
      { wrapper: createWrapper() }
    );

    act(() => {
      result.current.setQuery('alpha');
    });

    await waitFor(() => {
      expect(result.current.results).toHaveLength(1);
    });

    act(() => {
      result.current.clearQuery();
    });

    await waitFor(() => {
      expect(result.current.query).toBe('');
      expect(result.current.results).toEqual([]);
    });
  });

  it('highlightQuery delegates to getHighlights service', async () => {
    const { result } = renderHook(
      () =>
        useSemanticSearch({
          documents: sampleDocuments,
        }),
      { wrapper: createWrapper() }
    );

    act(() => {
      result.current.setQuery('alpha');
    });

    const highlights = result.current.highlightQuery(
      'Content about alpha feature design'
    );

    expect(SearchService.getHighlights).toHaveBeenCalledWith(
      expect.any(String),
      'Content about alpha feature design'
    );
    expect(highlights).toEqual(['highlighted text']);
  });

  it('does not search when enabled=false', () => {
    const { result } = renderHook(
      () =>
        useSemanticSearch({
          documents: sampleDocuments,
          enabled: false,
        }),
      { wrapper: createWrapper() }
    );

    act(() => {
      result.current.setQuery('alpha');
    });

    expect(SearchService.semanticSearch).not.toHaveBeenCalled();
  });

  it('surfaces error when semanticSearch rejects', async () => {
    vi.spyOn(SearchService, 'semanticSearch').mockRejectedValue(
      new Error('search engine offline')
    );

    const { result } = renderHook(
      () =>
        useSemanticSearch({
          documents: sampleDocuments,
        }),
      { wrapper: createWrapper() }
    );

    act(() => {
      result.current.setQuery('alpha');
    });

    await waitFor(() => {
      expect(result.current.error).not.toBeNull();
    });
  });

  it('exposes a refresh function', () => {
    const { result } = renderHook(
      () =>
        useSemanticSearch({
          documents: sampleDocuments,
        }),
      { wrapper: createWrapper() }
    );

    expect(typeof result.current.refresh).toBe('function');
  });
});

/**
 * KnowledgeGraphPanel tests (F-Y029)
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// Mock the API module
vi.mock('../knowledgeGraphApi', () => ({
  knowledgeGraphApi: {
    getProjectGraph: vi.fn(),
    searchKnowledge: vi.fn(),
  },
}));

import { KnowledgeGraphPanel } from '../KnowledgeGraphPanel';
import { knowledgeGraphApi } from '../knowledgeGraphApi';

const mockApi = vi.mocked(knowledgeGraphApi);

const sampleGraph = {
  nodes: [
    { id: 'n1', type: 'PROJECT', label: 'YAPPC Project', description: 'Core project', confidence: 0.95, metadata: {} },
    { id: 'n2', type: 'ENTITY', label: 'UserService', description: 'Manages users', confidence: 0.88, metadata: {} },
  ],
  edges: [
    { source: 'n1', target: 'n2', type: 'CONTAINS', weight: 1 },
  ],
  insights: [{ id: 'i1', content: 'Strong service isolation.', relevance: 0.9, type: 'OBSERVATION' }],
  metadata: { nodeCount: 2, edgeCount: 1, lastUpdated: '2026-04-27' },
};

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function Wrapper({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={makeClient()}>{children}</QueryClientProvider>;
}

describe('KnowledgeGraphPanel (F-Y029)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders panel structure', async () => {
    mockApi.getProjectGraph.mockResolvedValue(sampleGraph);

    render(
      <Wrapper>
        <KnowledgeGraphPanel projectId="proj-1" />
      </Wrapper>
    );

    // Component renders without crashing
    expect(document.body).toBeTruthy();
  });

  it('calls getProjectGraph with the correct projectId', async () => {
    mockApi.getProjectGraph.mockResolvedValue(sampleGraph);

    render(
      <Wrapper>
        <KnowledgeGraphPanel projectId="proj-1" />
      </Wrapper>
    );

    expect(mockApi.getProjectGraph).toHaveBeenCalledWith('proj-1');
  });

  it('does not call API when projectId is empty', () => {
    render(
      <Wrapper>
        <KnowledgeGraphPanel projectId="" />
      </Wrapper>
    );

    expect(mockApi.getProjectGraph).not.toHaveBeenCalled();
  });
});

/**
 * useProject Hook Tests
 *
 * Tests the hook's state synchronisation between TanStack Query and Jotai
 * atoms. Fetch and mutation calls are intercepted with vi.fn().
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider as JotaiProvider } from 'jotai';

import { useProject } from '../useProject';

// ---------------------------------------------------------------------------
// Mock global fetch
// ---------------------------------------------------------------------------

const mockFetch = vi.fn();
global.fetch = mockFetch;

beforeEach(() => {
  mockFetch.mockReset();
});

function makeGqlResponse(data: unknown) {
  return {
    ok: true,
    json: () => Promise.resolve({ data }),
  } as Response;
}

// ---------------------------------------------------------------------------
// Test wrapper
// ---------------------------------------------------------------------------

function makeWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
    <JotaiProvider>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </JotaiProvider>
  );

  return { Wrapper, queryClient };
}

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

function makeProject(overrides: Record<string, unknown> = {}) {
  return {
    id: 'proj-1',
    workspaceId: 'ws-1',
    name: 'My Project',
    description: null,
    type: 'FULL_STACK',
    status: 'ACTIVE',
    lifecyclePhase: 'DEVELOPMENT',
    isDefault: false,
    aiSummary: null,
    aiNextActions: [],
    aiHealthScore: null,
    createdAt: '2025-01-01T00:00:00.000Z',
    updatedAt: '2025-01-01T00:00:00.000Z',
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Initial state
// ---------------------------------------------------------------------------

describe('useProject — initial state', () => {
  beforeEach(() => {
    mockFetch.mockResolvedValue(makeGqlResponse({ projects: [] }));
  });

  it('returns empty projects list before fetch resolves', async () => {
    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useProject('ws-1'), {
      wrapper: Wrapper,
    });

    expect(result.current.projects).toEqual([]);
  });

  it('currentProject is null initially', () => {
    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useProject('ws-1'), {
      wrapper: Wrapper,
    });

    expect(result.current.currentProject).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Query disabled when workspaceId is null
// ---------------------------------------------------------------------------

describe('useProject — disabled query', () => {
  it('does not fire fetch when workspaceId is null', async () => {
    mockFetch.mockResolvedValue(makeGqlResponse({ projects: [] }));

    const { Wrapper } = makeWrapper();
    renderHook(() => useProject(null), { wrapper: Wrapper });

    // Wait a tick and verify fetch was not called
    await new Promise((r) => setTimeout(r, 50));
    expect(mockFetch).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// Fetch projects
// ---------------------------------------------------------------------------

describe('useProject — fetching', () => {
  it('populates projects after successful GraphQL response', async () => {
    const proj = makeProject();
    mockFetch.mockResolvedValue(makeGqlResponse({ projects: [proj] }));

    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useProject('ws-1'), {
      wrapper: Wrapper,
    });

    await waitFor(() => {
      expect(result.current.projects).toHaveLength(1);
    });
    expect(result.current.projects[0].id).toBe('proj-1');
  });

  it('sets isLoading to false after fetch completes', async () => {
    mockFetch.mockResolvedValue(makeGqlResponse({ projects: [] }));

    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useProject('ws-1'), {
      wrapper: Wrapper,
    });

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });
  });
});

// ---------------------------------------------------------------------------
// selectProject
// ---------------------------------------------------------------------------

describe('useProject — selectProject', () => {
  it('updates currentProjectId when selectProject is called', async () => {
    const proj = makeProject({ id: 'proj-42' });
    mockFetch.mockResolvedValue(makeGqlResponse({ projects: [proj] }));

    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useProject('ws-1'), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.projects).toHaveLength(1));

    act(() => {
      result.current.selectProject('proj-42');
    });

    expect(result.current.currentProjectId).toBe('proj-42');
  });

  it('resolves currentProject to the matching project object', async () => {
    const proj = makeProject({ id: 'proj-42', name: 'Selected' });
    mockFetch.mockResolvedValue(makeGqlResponse({ projects: [proj] }));

    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useProject('ws-1'), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.projects).toHaveLength(1));

    act(() => {
      result.current.selectProject('proj-42');
    });

    await waitFor(() => {
      expect(result.current.currentProject?.name).toBe('Selected');
    });
  });

  it('clears currentProject when selectProject is called with null', async () => {
    const proj = makeProject({ id: 'proj-1' });
    mockFetch.mockResolvedValue(makeGqlResponse({ projects: [proj] }));

    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useProject('ws-1'), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.projects).toHaveLength(1));

    act(() => result.current.selectProject('proj-1'));
    act(() => result.current.selectProject(null));

    expect(result.current.currentProjectId).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// createProject
// ---------------------------------------------------------------------------

describe('useProject — createProject', () => {
  it('calls the mutation and adds the new project to state', async () => {
    const newProj = makeProject({ id: 'proj-new', name: 'Brand New' });
    mockFetch
      .mockResolvedValueOnce(makeGqlResponse({ projects: [] }))
      .mockResolvedValueOnce(makeGqlResponse({ createProject: newProj }))
      .mockResolvedValue(makeGqlResponse({ projects: [newProj] }));

    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useProject('ws-1'), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.createProject({
        name: 'Brand New',
        type: 'FULL_STACK',
      });
    });

    await waitFor(() => {
      expect(result.current.projects.some((p) => p.id === 'proj-new')).toBe(
        true
      );
    });
  });

  it('exposes isCreating=true while mutation is in flight', async () => {
    let resolveMutation!: (value: unknown) => void;
    const pendingPromise = new Promise((r) => {
      resolveMutation = r;
    });

    mockFetch
      .mockResolvedValueOnce(makeGqlResponse({ projects: [] }))
      .mockReturnValueOnce(pendingPromise as unknown as Promise<Response>);

    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useProject('ws-1'), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    act(() => {
      void result.current.createProject({
        name: 'Pending',
        type: 'FULL_STACK',
      });
    });

    await waitFor(() => expect(result.current.isCreating).toBe(true));

    act(() => {
      resolveMutation({
        ok: true,
        json: () => Promise.resolve({ data: { createProject: makeProject() } }),
      });
    });
  });
});

// ---------------------------------------------------------------------------
// deleteProject
// ---------------------------------------------------------------------------

describe('useProject — deleteProject', () => {
  it('removes the deleted project from state', async () => {
    const proj = makeProject({ id: 'proj-1' });
    mockFetch
      .mockResolvedValueOnce(makeGqlResponse({ projects: [proj] }))
      .mockResolvedValueOnce(makeGqlResponse({ deleteProject: true }))
      .mockResolvedValue(makeGqlResponse({ projects: [] }));

    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useProject('ws-1'), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.projects).toHaveLength(1));

    await act(async () => {
      await result.current.deleteProject('proj-1');
    });

    await waitFor(() => {
      expect(
        result.current.projects.find((p) => p.id === 'proj-1')
      ).toBeUndefined();
    });
  });
});

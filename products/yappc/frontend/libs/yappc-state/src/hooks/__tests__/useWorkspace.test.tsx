/**
 * useWorkspace Hook Tests
 *
 * Tests the hook's state synchronisation between TanStack Query and Jotai
 * atoms. Fetch and mutation calls are intercepted with vi.fn().
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider as JotaiProvider } from 'jotai';

import { useWorkspace } from '../useWorkspace';

// ---------------------------------------------------------------------------
// Mock global fetch
// ---------------------------------------------------------------------------

const mockFetch = vi.fn();
global.fetch = mockFetch;

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
            <QueryClientProvider client={queryClient}>
                {children}
            </QueryClientProvider>
        </JotaiProvider>
    );

    return { Wrapper, queryClient };
}

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

function makeWorkspace(overrides: Record<string, unknown> = {}) {
    return {
        id: 'ws-1',
        name: 'My Workspace',
        description: null,
        ownerId: 'user-1',
        isDefault: false,
        aiSummary: null,
        aiTags: [],
        createdAt: '2025-01-01T00:00:00.000Z',
        updatedAt: '2025-01-01T00:00:00.000Z',
        ...overrides,
    };
}

// ---------------------------------------------------------------------------
// Initial state
// ---------------------------------------------------------------------------

describe('useWorkspace — initial state', () => {
    beforeEach(() => {
        mockFetch.mockResolvedValue(makeGqlResponse({ workspaces: [] }));
    });

    it('returns empty workspaces list before fetch resolves', async () => {
        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useWorkspace(), { wrapper: Wrapper });

        expect(result.current.workspaces).toEqual([]);
    });

    it('currentWorkspace is null initially', () => {
        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useWorkspace(), { wrapper: Wrapper });

        expect(result.current.currentWorkspace).toBeNull();
    });
});

// ---------------------------------------------------------------------------
// Fetch workspaces
// ---------------------------------------------------------------------------

describe('useWorkspace — fetching', () => {
    it('populates workspaces after successful GraphQL response', async () => {
        const ws = makeWorkspace();
        mockFetch.mockResolvedValue(makeGqlResponse({ workspaces: [ws] }));

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useWorkspace(), { wrapper: Wrapper });

        await waitFor(() => {
            expect(result.current.workspaces).toHaveLength(1);
        });
        expect(result.current.workspaces[0].id).toBe('ws-1');
    });

    it('sets isLoading to false after fetch completes', async () => {
        mockFetch.mockResolvedValue(makeGqlResponse({ workspaces: [] }));

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useWorkspace(), { wrapper: Wrapper });

        await waitFor(() => {
            expect(result.current.isLoading).toBe(false);
        });
    });
});

// ---------------------------------------------------------------------------
// selectWorkspace
// ---------------------------------------------------------------------------

describe('useWorkspace — selectWorkspace', () => {
    it('updates currentWorkspaceId when selectWorkspace is called', async () => {
        const ws = makeWorkspace({ id: 'ws-42' });
        mockFetch.mockResolvedValue(makeGqlResponse({ workspaces: [ws] }));

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useWorkspace(), { wrapper: Wrapper });

        await waitFor(() => expect(result.current.workspaces).toHaveLength(1));

        act(() => {
            result.current.selectWorkspace('ws-42');
        });

        expect(result.current.currentWorkspaceId).toBe('ws-42');
    });

    it('resolves currentWorkspace to the matching workspace object', async () => {
        const ws = makeWorkspace({ id: 'ws-42', name: 'Selected' });
        mockFetch.mockResolvedValue(makeGqlResponse({ workspaces: [ws] }));

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useWorkspace(), { wrapper: Wrapper });

        await waitFor(() => expect(result.current.workspaces).toHaveLength(1));

        act(() => {
            result.current.selectWorkspace('ws-42');
        });

        await waitFor(() => {
            expect(result.current.currentWorkspace?.name).toBe('Selected');
        });
    });

    it('clears currentWorkspace when selectWorkspace is called with null', async () => {
        const ws = makeWorkspace({ id: 'ws-1' });
        mockFetch.mockResolvedValue(makeGqlResponse({ workspaces: [ws] }));

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useWorkspace(), { wrapper: Wrapper });

        await waitFor(() => expect(result.current.workspaces).toHaveLength(1));

        act(() => result.current.selectWorkspace('ws-1'));
        act(() => result.current.selectWorkspace(null));

        expect(result.current.currentWorkspaceId).toBeNull();
    });
});

// ---------------------------------------------------------------------------
// createWorkspace
// ---------------------------------------------------------------------------

describe('useWorkspace — createWorkspace', () => {
    it('calls the mutation and adds the new workspace to state', async () => {
        // Initial load returns empty list
        const newWs = makeWorkspace({ id: 'ws-new', name: 'Brand New' });
        mockFetch
            .mockResolvedValueOnce(makeGqlResponse({ workspaces: [] }))
            .mockResolvedValueOnce(makeGqlResponse({ createWorkspace: newWs }))
            // Refetch after invalidation
            .mockResolvedValue(makeGqlResponse({ workspaces: [newWs] }));

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useWorkspace(), { wrapper: Wrapper });

        await waitFor(() => expect(result.current.isLoading).toBe(false));

        await act(async () => {
            await result.current.createWorkspace({ name: 'Brand New' });
        });

        await waitFor(() => {
            expect(result.current.workspaces.some((w) => w.id === 'ws-new')).toBe(true);
        });
    });

    it('exposes isCreating=true while mutation is in flight', async () => {
        let resolveMutation!: (value: unknown) => void;
        const pendingPromise = new Promise((r) => { resolveMutation = r; });

        mockFetch
            .mockResolvedValueOnce(makeGqlResponse({ workspaces: [] }))
            .mockReturnValueOnce(pendingPromise as unknown as Promise<Response>);

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useWorkspace(), { wrapper: Wrapper });

        await waitFor(() => expect(result.current.isLoading).toBe(false));

        act(() => {
            void result.current.createWorkspace({ name: 'Pending' });
        });

        await waitFor(() => expect(result.current.isCreating).toBe(true));

        // Resolve the pending mutation
        act(() => {
            resolveMutation({
                ok: true,
                json: () => Promise.resolve({ data: { createWorkspace: makeWorkspace() } }),
            });
        });
    });
});

// ---------------------------------------------------------------------------
// deleteWorkspace
// ---------------------------------------------------------------------------

describe('useWorkspace — deleteWorkspace', () => {
    it('removes the deleted workspace from state', async () => {
        const ws = makeWorkspace({ id: 'ws-1' });
        mockFetch
            .mockResolvedValueOnce(makeGqlResponse({ workspaces: [ws] }))
            .mockResolvedValueOnce(makeGqlResponse({ deleteWorkspace: true }))
            .mockResolvedValue(makeGqlResponse({ workspaces: [] }));

        const { Wrapper } = makeWrapper();
        const { result } = renderHook(() => useWorkspace(), { wrapper: Wrapper });

        await waitFor(() => expect(result.current.workspaces).toHaveLength(1));

        await act(async () => {
            await result.current.deleteWorkspace('ws-1');
        });

        await waitFor(() => {
            expect(result.current.workspaces.find((w) => w.id === 'ws-1')).toBeUndefined();
        });
    });
});

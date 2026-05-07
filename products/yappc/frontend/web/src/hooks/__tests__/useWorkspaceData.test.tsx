import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { normalizeProjectAccess, useWorkspaces } from '../useWorkspaceData';
import type { Project } from '../../state/atoms/workspaceAtom';

function createWrapper(): React.ComponentType<{ children: React.ReactNode }> {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };
}

describe('useWorkspaceData failure handling', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('surfaces a clear database-unavailable message for 503 responses', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('Service unavailable', {
          status: 503,
          headers: {
            'content-type': 'text/plain',
          },
        })
      )
    );

    const { result } = renderHook(() => useWorkspaces(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error).toBeInstanceOf(Error);
    expect((result.current.error as Error).message).toContain(
      'Database service unavailable'
    );
  });

  it('surfaces an explicit non-JSON error when the API returns HTML', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('<html><body>fallback</body></html>', {
          status: 200,
          headers: {
            'content-type': 'text/html',
          },
        })
      )
    );

    const { result } = renderHook(() => useWorkspaces(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error).toBeInstanceOf(Error);
    expect((result.current.error as Error).message).toContain(
      "Expected JSON but got 'text/html'"
    );
  });

  it('accepts the wrapped workspace list envelope from the mounted API', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            workspaces: [
              {
                id: 'ws-1',
                name: 'Workspace One',
                description: 'Primary workspace',
                ownerId: 'user-1',
                isDefault: true,
                aiTags: [],
                createdAt: '2026-04-20T10:00:00.000Z',
                updatedAt: '2026-04-20T10:00:00.000Z',
              },
            ],
          }),
          {
            status: 200,
            headers: {
              'content-type': 'application/json',
            },
          }
        )
      )
    );

    const { result } = renderHook(() => useWorkspaces(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toEqual([
      expect.objectContaining({
        id: 'ws-1',
        name: 'Workspace One',
      }),
    ]);
  });

  it('normalizes missing project lifecycle and review fields from partial backend contracts', () => {
    const rawProject = {
      id: 'proj-1',
      name: 'Partial Project',
      description: 'Backend payload is missing review fields',
      ownerWorkspaceId: 'ws-1',
      isDefault: false,
      createdAt: '2026-04-20T10:00:00.000Z',
      updatedAt: 'not-a-date',
      lifecyclePhase: 'DESIGN',
      aiNextActions: ['Review deployment blockers', 42, '', 'Approve release'],
      aiHealthScore: 140,
    } as unknown as Project;

    const normalized = normalizeProjectAccess(rawProject, true);

    expect(normalized.lifecyclePhase).toBe('INTENT');
    expect(normalized.type).toBe('FULL_STACK');
    expect(normalized.status).toBe('DRAFT');
    expect(normalized.aiNextActions).toEqual(['Review deployment blockers', 'Approve release']);
    expect(normalized.aiHealthScore).toBe(100);
    expect(normalized.updatedAt).toBe('2026-04-20T10:00:00.000Z');
  });
});

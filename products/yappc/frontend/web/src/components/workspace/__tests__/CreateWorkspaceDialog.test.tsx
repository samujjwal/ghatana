import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider, useAtomValue } from 'jotai';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';

import { currentWorkspaceIdAtom } from '@/state/atoms/workspaceAtom';
import { CreateWorkspaceDialog } from '../CreateWorkspaceDialog';

function createWrapper(): React.ComponentType<{ children: React.ReactNode }> {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
      mutations: {
        retry: false,
      },
    },
  });

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <Provider>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </Provider>
    );
  };
}

function CurrentWorkspaceIdProbe(): React.JSX.Element {
  const currentWorkspaceId = useAtomValue(currentWorkspaceIdAtom);
  return <output data-testid="current-workspace-id">{currentWorkspaceId ?? ''}</output>;
}

describe('CreateWorkspaceDialog', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('creates a workspace through the API and selects it as current', async () => {
    const onClose = vi.fn();
    const onCreated = vi.fn();

    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url.endsWith('/api/workspaces/suggest-name')) {
        return new Response(JSON.stringify({ suggestion: 'Starter Workspace' }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        });
      }

      if (url.endsWith('/api/workspaces') && init?.method === 'POST') {
        return new Response(
          JSON.stringify({
            workspace: {
              id: 'ws-123',
              name: 'Platform Launch',
              description: 'First workspace',
              ownerId: 'user-1',
              isDefault: false,
              aiSummary: '',
              aiTags: [],
              createdAt: '2026-04-17T00:00:00.000Z',
              updatedAt: '2026-04-17T00:00:00.000Z',
            },
          }),
          {
            status: 201,
            headers: { 'content-type': 'application/json' },
          }
        );
      }

      return new Response(JSON.stringify([]), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      });
    });

    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();

    render(
      <>
        <CreateWorkspaceDialog isOpen onClose={onClose} onCreated={onCreated} />
        <CurrentWorkspaceIdProbe />
      </>,
      { wrapper: createWrapper() }
    );

    await user.type(screen.getByTestId('workspace-name-input'), 'Platform Launch');
    await user.type(screen.getByLabelText(/description/i), 'First workspace');
    await user.click(screen.getByTestId('create-workspace-submit'));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:7002/api/workspaces',
        expect.objectContaining({ method: 'POST' })
      );
    });

    expect(onCreated).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'ws-123', name: 'Platform Launch' })
    );
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('current-workspace-id')).toHaveTextContent('ws-123');
  });
});
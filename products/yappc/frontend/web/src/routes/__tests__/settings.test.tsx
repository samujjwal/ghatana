import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../components/route/ErrorBoundary', () => ({
  RouteErrorBoundary: () => <div>Error boundary</div>,
}));

vi.mock('../../state/atoms/workspaceAtom', () => ({
  currentWorkspaceIdAtom: Symbol('currentWorkspaceIdAtom'),
}));

vi.mock('jotai', async (importOriginal) => {
  const actual = await importOriginal<typeof import('jotai')>();
  return {
    ...actual,
    useAtomValue: () => 'ws-42',
  };
});

vi.mock('../../providers/AuthProvider', () => ({
  useCurrentUser: () => ({
    id: 'user-1',
    name: 'Test User',
    email: 'test@example.com',
    initials: 'TU',
    isAuthenticated: true,
  }),
}));

import WorkspaceSettingsRoute from '../settings';

function renderRoute() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <WorkspaceSettingsRoute />
    </QueryClientProvider>
  );
}

describe('workspace settings route', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('loads the wrapped workspace envelope and saves only supported fields', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            workspace: {
              id: 'ws-42',
              name: 'Alpha Workspace',
              description: 'Current description',
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            workspace: {
              id: 'ws-42',
              name: 'Renamed Workspace',
              description: 'Updated description',
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      );

    renderRoute();

    fireEvent.change(await screen.findByDisplayValue('Alpha Workspace'), {
      target: { value: 'Renamed Workspace' },
    });
    fireEvent.change(screen.getByDisplayValue('Current description'), {
      target: { value: 'Updated description' },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Save Changes' }));

    await waitFor(() => {
      expect(
        vi.mocked(fetch).mock.calls.some(([url, options]) => {
          return (
            url === '/api/workspaces/ws-42' &&
            (options as RequestInit | undefined)?.method === 'PATCH'
          );
        })
      ).toBe(true);
    });

    const patchCall = vi.mocked(fetch).mock.calls.find(([url, options]) => {
      return (
        url === '/api/workspaces/ws-42' &&
        (options as RequestInit | undefined)?.method === 'PATCH'
      );
    });

    const patchOptions = patchCall?.[1] as RequestInit;

    expect(JSON.parse(String(patchOptions.body))).toEqual({
      name: 'Renamed Workspace',
      description: 'Updated description',
    });

    expect(await screen.findByText('Settings saved successfully.')).toBeDefined();
  });

  it('shows a truthful error when the backed workspace save fails', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            workspace: {
              id: 'ws-42',
              name: 'Alpha Workspace',
              description: 'Current description',
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ message: 'Workspace update failed' }),
          { status: 500, headers: { 'Content-Type': 'application/json' } }
        )
      );

    renderRoute();

    fireEvent.change(await screen.findByDisplayValue('Alpha Workspace'), {
      target: { value: 'Renamed Workspace' },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Save Changes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to save. Please try again.');
  });

  it('requires explicit confirmation before deleting the workspace', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            workspace: {
              id: 'ws-42',
              name: 'Alpha Workspace',
              description: 'Current description',
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    renderRoute();

    fireEvent.click(await screen.findByRole('button', { name: 'Danger Zone' }));

    const deleteButton = screen.getByRole('button', { name: 'Delete Workspace' });
    expect(deleteButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/Type Alpha Workspace to confirm/i), {
      target: { value: 'Alpha Workspace' },
    });

    expect(deleteButton).not.toBeDisabled();

    fireEvent.click(deleteButton);

    await waitFor(() => {
      expect(
        vi.mocked(fetch).mock.calls.some(([url, options]) => {
          return (
            url === '/api/workspaces/ws-42' &&
            (options as RequestInit | undefined)?.method === 'DELETE'
          );
        })
      ).toBe(true);
    });
  });

  it('reveals advanced workspace metadata only after explicit disclosure', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          workspace: {
            id: 'ws-42',
            name: 'Alpha Workspace',
            description: 'Current description',
            ownerId: 'owner-7',
            createdAt: '2026-04-20T10:00:00.000Z',
            updatedAt: '2026-04-20T12:00:00.000Z',
          },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );

    renderRoute();

    expect(await screen.findByDisplayValue('Alpha Workspace')).toBeDefined();
    expect(screen.queryByTestId('workspace-advanced-metadata')).toBeNull();

    fireEvent.click(screen.getByTestId('workspace-advanced-metadata-toggle'));

    expect(screen.getByTestId('workspace-advanced-metadata')).toHaveTextContent('ws-42');
    expect(screen.getByTestId('workspace-advanced-metadata')).toHaveTextContent('owner-7');
  });
});
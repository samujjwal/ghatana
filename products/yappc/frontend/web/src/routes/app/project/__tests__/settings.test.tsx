import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useParams: () => ({ projectId: 'proj-42' }),
  };
});

vi.mock('../../../../components/route/ErrorBoundary', () => ({
  RouteErrorBoundary: () => <div>Error boundary</div>,
}));

import ProjectSettingsRoute from '../settings';

function renderRoute() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <ProjectSettingsRoute />
    </QueryClientProvider>
  );
}

describe('project settings route', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem('yappc:currentWorkspaceId', JSON.stringify('ws-9'));
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it('loads the wrapped project envelope and hides unsupported admin sections', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          project: {
            id: 'proj-42',
            name: 'Alpha Project',
            description: 'Initial description',
            type: 'FULL_STACK',
            ownerWorkspaceId: 'ws-9',
          },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );

    renderRoute();

    expect(await screen.findByDisplayValue('Alpha Project')).toBeDefined();
    expect(screen.getByText('Advanced capabilities unavailable')).toBeDefined();
    expect(screen.queryByText('Access Control (RBAC)')).toBeNull();
    expect(screen.queryByText('API Tokens')).toBeNull();
    expect(screen.queryByText('Audit Trail')).toBeNull();
  });

  it('saves only the backed project fields through the supported PATCH contract', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            project: {
              id: 'proj-42',
              name: 'Alpha Project',
              description: 'Initial description',
              type: 'FULL_STACK',
              ownerWorkspaceId: 'ws-9',
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            project: {
              id: 'proj-42',
              name: 'Renamed Project',
              description: 'Updated description',
              type: 'BACKEND',
              ownerWorkspaceId: 'ws-9',
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      );

    renderRoute();

    fireEvent.change(await screen.findByTestId('project-name-input'), {
      target: { value: 'Renamed Project' },
    });
    fireEvent.change(screen.getByTestId('project-description-input'), {
      target: { value: 'Updated description' },
    });
    fireEvent.change(screen.getByTestId('project-type-select'), {
      target: { value: 'BACKEND' },
    });

    fireEvent.click(screen.getByTestId('save-settings-button'));

    await waitFor(() => {
      expect(
        vi.mocked(fetch).mock.calls.some(([url, options]) => {
          return url === '/api/projects/proj-42?workspaceId=ws-9' && (options as RequestInit | undefined)?.method === 'PATCH';
        })
      ).toBe(true);
    });

    const patchCall = vi.mocked(fetch).mock.calls.find(([url, options]) => {
      return url === '/api/projects/proj-42?workspaceId=ws-9' && (options as RequestInit | undefined)?.method === 'PATCH';
    });

    expect(patchCall?.[0]).toBe('/api/projects/proj-42?workspaceId=ws-9');

    const patchOptions = patchCall?.[1] as RequestInit;

    expect(patchOptions).toEqual(
      expect.objectContaining({
        method: 'PATCH',
        headers: expect.objectContaining({ 'Content-Type': 'application/json' }),
      })
    );

    expect(JSON.parse(String(patchOptions.body))).toEqual({
      name: 'Renamed Project',
      description: 'Updated description',
      type: 'BACKEND',
    });

    expect(await screen.findByText('Project settings saved.')).toBeDefined();
  });

  it('reveals advanced project metadata only after explicit disclosure', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          project: {
            id: 'proj-42',
            name: 'Alpha Project',
            description: 'Initial description',
            type: 'FULL_STACK',
            status: 'ACTIVE',
            lifecyclePhase: 'INTENT',
            ownerWorkspaceId: 'ws-9',
          },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );

    renderRoute();

    expect(await screen.findByDisplayValue('Alpha Project')).toBeDefined();
    expect(screen.queryByTestId('project-advanced-metadata')).toBeNull();

    fireEvent.click(screen.getByTestId('project-advanced-metadata-toggle'));

    expect(screen.getByTestId('project-advanced-metadata')).toHaveTextContent('proj-42');
    expect(screen.getByTestId('project-advanced-metadata')).toHaveTextContent('ws-9');
    expect(screen.getByTestId('project-advanced-metadata')).toHaveTextContent('ACTIVE');
  });
});

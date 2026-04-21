import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { mockGetNextPhase } = vi.hoisted(() => ({
  mockGetNextPhase: vi.fn(),
}));

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

vi.mock('@/services/lifecycle/phase-transition-api', () => ({
  phaseTransitionAPI: {
    getNextPhase: mockGetNextPhase,
  },
}));

import ProjectOverviewRoute from '../index';

function renderRoute() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <ProjectOverviewRoute />
    </QueryClientProvider>
  );
}

describe('project overview route', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the mounted cockpit with blockers and recent activity', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            project: {
              id: 'proj-42',
              name: 'Alpha Project',
              description: 'A truthful overview surface.',
              type: 'FULL_STACK',
              ownerWorkspaceId: 'ws-9',
              ownerWorkspace: { id: 'ws-9', name: 'Workspace Nine' },
              lifecyclePhase: 'EXECUTE',
              status: 'ACTIVE',
              aiHealthScore: 82,
              aiNextActions: ['Approve the release packet', 'Check deployment readiness'],
              updatedAt: '2026-04-21T10:00:00.000Z',
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            projectId: 'proj-42',
            activity: [
              {
                id: 'audit-1',
                source: 'audit',
                action: 'PROJECT_CREATED',
                summary: 'Project Alpha Project created in workspace Workspace Nine',
                timestamp: '2026-04-21T09:30:00.000Z',
                actor: 'user-123',
              },
            ],
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      );

    mockGetNextPhase.mockResolvedValue({
      projectId: 'proj-42',
      currentPhase: 'EXECUTE',
      nextPhase: 'VERIFY',
      canAdvance: false,
      readiness: 54,
      blockers: ['Missing approved documentation artifact'],
      requiredArtifacts: ['Documentation'],
      completedArtifacts: [],
      estimatedReadyIn: '~1 day',
      estimatedReadyInHours: 24,
      predictionConfidence: 0.72,
      checkedAt: '2026-04-21T10:01:00.000Z',
    });

    renderRoute();

    expect(await screen.findByTestId('project-overview-route')).toBeDefined();
    expect(screen.getByTestId('project-overview-phase').textContent).toContain('Execute');
    await waitFor(() => {
      expect(screen.getByTestId('project-overview-promotion-status').textContent).toContain('Blocked before promotion');
    });
    expect(screen.getByTestId('project-overview-blockers').textContent).toContain('Missing approved documentation artifact');
    expect(screen.getByTestId('project-overview-timeline').textContent).toContain('PROJECT_CREATED');
    expect(screen.getByText('Approve the release packet')).toBeDefined();
  });

  it('surfaces lifecycle preview failures instead of implying readiness', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            project: {
              id: 'proj-42',
              name: 'Alpha Project',
              description: '',
              type: 'FULL_STACK',
              ownerWorkspaceId: 'ws-9',
              ownerWorkspace: { id: 'ws-9', name: 'Workspace Nine' },
              lifecyclePhase: 'PLAN',
              status: 'ACTIVE',
              aiHealthScore: 50,
              aiNextActions: [],
              updatedAt: '2026-04-21T10:00:00.000Z',
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ projectId: 'proj-42', activity: [] }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      );

    mockGetNextPhase.mockRejectedValue(new Error('Lifecycle readiness service unavailable'));

    renderRoute();

    expect(await screen.findByTestId('project-overview-promotion-status')).toBeDefined();
    await waitFor(() => {
      expect(screen.getByText('Gate status unavailable')).toBeDefined();
    });
    expect(screen.getByText('Lifecycle readiness service unavailable')).toBeDefined();
  });
});
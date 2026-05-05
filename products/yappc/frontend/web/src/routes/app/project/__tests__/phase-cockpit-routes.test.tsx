import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const { mockNavigate, mockGetNextPhase } = vi.hoisted(() => ({
  mockNavigate: vi.fn(),
  mockGetNextPhase: vi.fn(),
}));

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useParams: () => ({ projectId: 'proj-42' }),
    useNavigate: () => mockNavigate,
  };
});

vi.mock('@/services/lifecycle/phase-transition-api', () => ({
  phaseTransitionAPI: {
    getNextPhase: mockGetNextPhase,
  },
}));

vi.mock('../index', () => ({
  __esModule: true,
  default: () => <div data-testid="project-overview-stub">Overview surface</div>,
}));

vi.mock('../canvas', () => ({
  __esModule: true,
  default: () => <div data-testid="canvas-container">Canvas surface</div>,
}));

vi.mock('../lifecycle', () => ({
  __esModule: true,
  default: () => <div data-testid="lifecycle-explorer">Lifecycle surface</div>,
}));

vi.mock('../deploy', () => ({
  __esModule: true,
  default: () => <div data-testid="deploy-surface">Deploy surface</div>,
}));

vi.mock('../preview', () => ({
  __esModule: true,
  default: () => <div data-testid="preview-iframe">Preview surface</div>,
}));

import IntentRoute from '../intent';
import ShapeRoute from '../shape';
import ValidateRoute from '../validate';

function renderRoute(node: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(<QueryClientProvider client={queryClient}>{node}</QueryClientProvider>);
}

describe('phase cockpit routes', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('fetch', vi.fn());
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            project: {
              id: 'proj-42',
              name: 'Alpha Project',
              description: 'Mounted cockpit route',
              lifecyclePhase: 'SHAPE',
              status: 'ACTIVE',
              aiHealthScore: 80,
              aiNextActions: ['Review the latest lifecycle evidence'],
              updatedAt: '2026-04-21T10:00:00.000Z',
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            projectId: 'proj-42',
            activity: [
              {
                id: 'audit-1',
                source: 'audit',
                action: 'PROJECT_UPDATED',
                summary: 'Project updated',
                timestamp: '2026-04-21T11:00:00.000Z',
                actor: 'user-1',
                success: true,
              },
            ],
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            projectId: 'proj-42',
            currentPhase: 'SHAPE',
            nextPhase: 'VALIDATE',
            canAdvance: true,
            readiness: 92,
            blockers: [],
            requiredArtifacts: ['Requirements packet'],
            completedArtifacts: ['Intent brief'],
            estimatedReadyIn: 'Ready now',
            estimatedReadyInHours: 0,
            predictionConfidence: 0.8,
            checkedAt: '2026-04-21T11:05:00.000Z',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      );
  });

  it('mounts the intent cockpit route and drives the intent action into the drawer url', async () => {
    renderRoute(<IntentRoute />);

    expect(await screen.findByTestId('intent-cockpit')).toBeInTheDocument();
    expect(screen.getByTestId('phase-purpose')).toBeInTheDocument();
    expect(screen.getByTestId('intent-native-summary')).toBeInTheDocument();
    expect(screen.queryByTestId('project-overview-stub')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Advanced details' }));
    expect(await screen.findByTestId('project-overview-stub')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('define-requirements'));
    expect(mockNavigate).toHaveBeenCalledWith('/p/proj-42/intent?drawer=idea');
  });

  it('mounts the shape cockpit route with the embedded canvas surface', async () => {
    renderRoute(<ShapeRoute />);

    expect(await screen.findByTestId('shape-cockpit')).toBeInTheDocument();
    expect(screen.getByTestId('shape-native-summary')).toBeInTheDocument();
    expect(screen.queryByTestId('canvas-container')).not.toBeInTheDocument();
    expect(screen.getByTestId('primary-next-action')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Advanced details' }));
    expect(await screen.findByTestId('canvas-container')).toBeInTheDocument();
  });

  it('mounts the validate cockpit route with real gate summaries', async () => {
    renderRoute(<ValidateRoute />);

    expect(await screen.findByTestId('validate-cockpit')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByTestId('validation-status')).toHaveTextContent(/passed|pending/i);
    });
    expect(screen.getByTestId('approval-gates')).toBeInTheDocument();
    expect(screen.getAllByTestId('required-approval').length).toBeGreaterThan(0);
  });
});

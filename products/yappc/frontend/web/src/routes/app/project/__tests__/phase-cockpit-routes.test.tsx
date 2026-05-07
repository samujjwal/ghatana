import React from 'react';
import { QueryClient } from '@tanstack/react-query';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Provider, createStore } from 'jotai';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from '@/test-utils/test-utils';
import { currentUserAtom } from '@/stores/user.store';
import type { User } from '@/types/dashboard';

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
import GenerateRoute from '../generate';
import RunRoute from '../run';

function renderRoute(node: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(node, { queryClient });
}

function renderRouteWithUser(node: React.ReactNode, user: User) {
  const store = createStore();
  store.set(currentUserAtom, user);

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(
    <Provider store={store}>{node}</Provider>,
    { queryClient },
  );
}

function mockPhaseBootstrap({
  lifecyclePhase = 'SHAPE',
  nextPhase = 'VALIDATE',
  canAdvance = true,
  readiness = 92,
  requiredArtifacts = ['Requirements packet'],
}: {
  readonly lifecyclePhase?: string;
  readonly nextPhase?: string;
  readonly canAdvance?: boolean;
  readonly readiness?: number;
  readonly requiredArtifacts?: readonly string[];
} = {}) {
  vi.mocked(fetch)
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          project: {
            id: 'proj-42',
            name: 'Alpha Project',
            description: 'Mounted cockpit route',
            lifecyclePhase,
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
          currentPhase: lifecyclePhase,
          nextPhase,
          canAdvance,
          readiness,
          blockers: [],
          requiredArtifacts,
          completedArtifacts: ['Intent brief'],
          estimatedReadyIn: 'Ready now',
          estimatedReadyInHours: 0,
          predictionConfidence: 0.8,
          checkedAt: '2026-04-21T11:05:00.000Z',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
}

function expectFetchCalledWithPath(path: string) {
  expect(
    vi.mocked(fetch).mock.calls.some(([input]) => String(input).includes(path)),
  ).toBe(true);
}

describe('phase cockpit routes', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('fetch', vi.fn());
  });

  it('mounts the intent cockpit route and drives the intent action into the drawer url', async () => {
    mockPhaseBootstrap();
    renderRoute(<IntentRoute />);

    expect(await screen.findByTestId('intent-cockpit')).toBeInTheDocument();
    expect(screen.getByTestId('phase-purpose')).toBeInTheDocument();
    expect(screen.getByTestId('intent-native-summary')).toBeInTheDocument();
    expect(screen.queryByTestId('project-overview-stub')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Intent exploration reference' }));
    expect(await screen.findByTestId('intent-advanced-panel')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('define-requirements'));
    expect(mockNavigate).toHaveBeenCalledWith('/p/proj-42/intent?drawer=idea');
  });

  it('mounts the shape cockpit route with the embedded canvas surface', async () => {
    mockPhaseBootstrap();
    renderRoute(<ShapeRoute />);

    expect(await screen.findByTestId('shape-cockpit')).toBeInTheDocument();
    expect(screen.getByTestId('shape-native-summary')).toBeInTheDocument();
    expect(screen.queryByTestId('canvas-container')).not.toBeInTheDocument();
    expect(screen.getByTestId('primary-next-action')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Shape configuration reference' }));
    expect(await screen.findByTestId('canvas-container')).toBeInTheDocument();
  });

  it('mounts the validate cockpit route with real gate summaries', async () => {
    mockPhaseBootstrap({ lifecyclePhase: 'VALIDATE', nextPhase: 'GENERATE' });
    renderRoute(<ValidateRoute />);

    expect(await screen.findByTestId('validate-cockpit')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByTestId('validation-status')).toHaveTextContent(/passed|pending/i);
    });
    expect(screen.getByTestId('approval-gates')).toBeInTheDocument();
    expect(screen.getAllByTestId('required-approval').length).toBeGreaterThan(0);
  });

  it('starts backend generation and requests diff review from the generate cockpit CTA', async () => {
    mockPhaseBootstrap({
      lifecyclePhase: 'GENERATE',
      nextPhase: 'RUN',
      requiredArtifacts: ['Generated React page', 'Generated tests'],
    });
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ runId: 'gen-run-1', status: 'RUNNING', reviewRequired: true }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ runId: 'gen-run-1', status: 'PENDING', diff: { files: [] }, reviewRequired: true }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      );

    renderRoute(<GenerateRoute />);

    expect(await screen.findByTestId('generate-cockpit')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('generate-code'));

    expect(await screen.findByTestId('phase-action-result')).toHaveTextContent('gen-run-1');
    expectFetchCalledWithPath('/api/v1/yappc/generate');
    expectFetchCalledWithPath('/api/v1/yappc/generate/diff');
  });

  it('starts the run workflow with tenant context and checks workflow status', async () => {
    mockPhaseBootstrap({
      lifecyclePhase: 'RUN',
      nextPhase: 'OBSERVE',
      requiredArtifacts: ['Deployment approval'],
    });
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ runId: 'workflow-run-1', templateId: 'yappc-run', status: 'RUNNING' }),
          { status: 202, headers: { 'Content-Type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ runId: 'workflow-run-1', templateId: 'yappc-run', status: 'RUNNING' }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      );

    const rendered = renderRouteWithUser(<RunRoute />, {
      id: 'user-1',
      email: 'operator@example.com',
      name: 'Operator',
      role: 'ADMIN',
      tenantId: 'tenant-1',
      workspaceIds: ['workspace-1'],
    });

    expect(await screen.findByTestId('run-cockpit')).toBeInTheDocument();
    await rendered.user.click(screen.getByTestId('check-readiness'));

    await waitFor(() => expectFetchCalledWithPath('/api/v1/workflows/yappc-run/start'));
    expect(await screen.findByTestId('phase-action-result')).toHaveTextContent('workflow-run-1');
    expectFetchCalledWithPath('/api/v1/workflows/workflow-run-1/status');
    await waitFor(() => {
      const startCall = vi.mocked(fetch).mock.calls.find(([input]) => String(input).includes('/api/v1/workflows/yappc-run/start'));
      expect(startCall?.[1]?.headers).toMatchObject({ 'X-Tenant-Id': 'tenant-1' });
    });
  });
});

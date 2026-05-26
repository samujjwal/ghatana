import React from 'react';
import { QueryClient } from '@tanstack/react-query';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Provider, createStore } from 'jotai';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from '@/test-utils/test-utils';
import { currentUserAtom } from '@/stores/user.store';
import type { User } from '@/types/dashboard';

const { mockNavigate, mockGetNextPhase, mockGetPhasePacket } = vi.hoisted(() => ({
  mockNavigate: vi.fn(),
  mockGetNextPhase: vi.fn(),
  mockGetPhasePacket: vi.fn(),
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

vi.mock('../../../../clients/generated/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../../clients/generated/api')>();
  return {
    ...actual,
    LifecycleService: {
      ...actual.LifecycleService,
      getPhasePacket: mockGetPhasePacket,
    },
  };
});

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
import ObserveRoute from '../observe';

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
  aiNextActions = ['Review the latest lifecycle evidence'],
  projectAccess = {
    isOwned: true,
    isIncluded: false,
    readOnly: false,
    role: 'EDITOR',
    capabilities: {
      read: true,
      update: true,
      create: true,
      delete: false,
      include: true,
      comment: true,
    },
  },
  activity = [
    {
      id: 'audit-1',
      source: 'audit' as const,
      action: 'PROJECT_UPDATED',
      summary: 'Project updated',
      timestamp: '2026-04-21T11:00:00.000Z',
      actor: 'user-1',
      success: true,
    },
  ],
}: {
  readonly lifecyclePhase?: string;
  readonly nextPhase?: string;
  readonly canAdvance?: boolean;
  readonly readiness?: number;
  readonly requiredArtifacts?: readonly string[];
  readonly aiNextActions?: readonly string[];
  readonly projectAccess?: {
    readonly isOwned?: boolean;
    readonly isIncluded?: boolean;
    readonly readOnly?: boolean;
    readonly role?: string;
    readonly capabilities?: {
      readonly read?: boolean;
      readonly update?: boolean;
      readonly create?: boolean;
      readonly delete?: boolean;
      readonly include?: boolean;
      readonly comment?: boolean;
    };
  };
  readonly activity?: readonly {
    readonly id: string;
    readonly source: 'lifecycle' | 'audit';
    readonly action: string;
    readonly summary: string;
    readonly timestamp: string;
    readonly actor: string | null;
    readonly severity?: string | null;
    readonly success?: boolean | null;
  }[];
} = {}) {
  mockGetPhasePacket.mockImplementation((phase: string) => Promise.resolve({
    phase,
    projectId: 'proj-42',
    projectName: 'Alpha Project',
    tenantId: 'tenant-1',
    workspaceId: 'workspace-1',
    workspaceName: 'Workspace One',
    actor: {
      actorId: 'user-1',
      actorName: 'Test User',
      role: projectAccess.role ?? 'EDITOR',
      isOwner: projectAccess.isOwned ?? true,
      isAdmin: true,
    },
    lifecyclePhase,
    tenantTier: 'PRO',
    enabledPhaseFlags: [phase],
    capabilities: {
      canRead: true,
      canCreate: !(projectAccess.readOnly ?? false),
      canUpdate: !(projectAccess.readOnly ?? false),
      canDelete: false,
      canApprove: !(projectAccess.readOnly ?? false),
      canReject: !(projectAccess.readOnly ?? false),
      canRollback: !(projectAccess.readOnly ?? false),
    },
    blockers: canAdvance ? [] : requiredArtifacts.map((artifact, index) => ({
      id: `blocker-${index + 1}`,
      type: 'artifact',
      title: `Missing ${artifact}`,
      description: `Required artifact is not complete: ${artifact}`,
      severity: 'WARNING',
      resourceId: artifact,
      resolvable: true,
    })),
    readiness: {
      canAdvance,
      nextPhase,
      missingPrerequisites: canAdvance ? [] : [...requiredArtifacts],
      completenessScore: readiness / 100,
      isDegraded: !canAdvance,
    },
    requiredArtifacts: requiredArtifacts.map((artifact, index) => ({
      artifactId: `required-${index + 1}`,
      artifactType: 'document',
      title: artifact,
      description: `${artifact} is required for ${phase}`,
      isComplete: canAdvance,
    })),
    completedArtifacts: ['Intent brief'].map((artifact, index) => ({
      artifactId: `completed-${index + 1}`,
      artifactType: 'document',
      title: artifact,
      completedAt: '2026-04-21T11:00:00.000Z',
      completedBy: 'user-1',
    })),
    activityFeed: activity.map((event) => ({
      id: event.id,
      type: event.source,
      action: event.action,
      summary: event.summary,
      actor: event.actor ?? 'system',
      timestamp: event.timestamp,
      severity: event.severity ?? 'INFO',
    })),
    evidence: activity.map((event) => ({
      id: `evidence-${event.id}`,
      type: 'artifact',
      title: event.summary,
      description: event.summary,
      timestamp: event.timestamp,
      metadata: {},
      evidenceId: `evidence-${event.id}`,
    })),
    governance: [{
      id: 'governance-1',
      type: 'derived',
      outcome: canAdvance ? 'Ready without extra review' : 'Review required',
      actor: 'system',
      timestamp: '2026-04-21T11:00:00.000Z',
      metadata: {},
      policyDecisionId: 'policy-1',
    }],
    availableActions: [{
      actionId: `${phase}-primary`,
      label: aiNextActions[0] ?? 'Run guided action',
      description: aiNextActions[0] ?? `Continue ${phase}`,
      enabled: canAdvance && !(projectAccess.readOnly ?? false),
      disabledReason: projectAccess.readOnly ? 'You have view-only access to this project.' : undefined,
      requiredPermission: 'update',
      parameters: {},
    }],
    dashboardActions: {
      primaryAction: `${phase}-primary`,
      blockedActions: canAdvance ? [] : [`${phase}-primary`],
      reviewRequiredActions: [],
      safeToContinueActions: canAdvance ? [`${phase}-primary`] : [],
    },
    healthSignals: {
      preview: {
        isHealthy: true,
        status: 'healthy',
        issues: [],
      },
      generation: {
        isHealthy: true,
        status: 'ready',
        lastGeneratedAt: '2026-04-21T11:00:00.000Z',
        issues: [],
      },
      runtime: {
        isHealthy: true,
        status: 'ready',
        lastDeployedAt: '2026-04-21T11:00:00.000Z',
        issues: [],
      },
    },
    timestamp: Date.parse('2026-04-21T11:05:00.000Z'),
  }));

}

function expectFetchCalledWithPath(path: string) {
  expect(
    vi.mocked(fetch).mock.calls.some(([input]) => String(input).includes(path)),
  ).toBe(true);
}

function activityRefetchResponse() {
  return new Response(
    JSON.stringify({
      projectId: 'proj-42',
      activity: [],
    }),
    { status: 200, headers: { 'Content-Type': 'application/json' } },
  );
}

function auditResponse(id = 'audit-test-1') {
  return new Response(
    JSON.stringify({
      id,
      timestamp: '2026-05-07T12:00:00.000Z',
    }),
    { status: 200, headers: { 'Content-Type': 'application/json' } },
  );
}

describe('phase cockpit routes', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetPhasePacket.mockReset();
    localStorage.setItem('yappc:currentWorkspaceId', JSON.stringify('workspace-1'));
    vi.stubGlobal('fetch', vi.fn());
  });

  it('mounts the intent cockpit route and drives the intent action into the drawer url', async () => {
    mockPhaseBootstrap();
    renderRoute(<IntentRoute />);

    expect(await screen.findByTestId('intent-cockpit')).toBeInTheDocument();
    expect(screen.getByTestId('phase-purpose')).toBeInTheDocument();
    expect(screen.getByTestId('intent-native-summary')).toBeInTheDocument();
    expect(screen.queryByTestId('project-overview-stub')).not.toBeInTheDocument();

    expect(screen.getByTestId('advanced-tools-description')).toHaveTextContent(
      'Use this only when goals, users, or success criteria need more context than the cockpit summary shows.',
    );

    fireEvent.click(screen.getByRole('button', { name: 'Open intent notes workspace' }));
    expect(await screen.findByTestId('intent-advanced-panel')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('define-requirements'));
    expect(mockNavigate).toHaveBeenCalledWith('/p/proj-42/intent?drawer=idea');
  });

  it('mounts the shape cockpit route with the embedded canvas surface', async () => {
    mockPhaseBootstrap();
    renderRouteWithUser(<ShapeRoute />, {
      id: 'designer-1',
      email: 'designer@example.com',
      name: 'Designer',
      role: 'ADMIN',
      tenantId: 'tenant-1',
      workspaceIds: ['workspace-1'],
    });

    expect(await screen.findByTestId('shape-cockpit')).toBeInTheDocument();
    expect(screen.getByTestId('shape-native-summary')).toBeInTheDocument();
    expect(screen.getByTestId('phase-contract-summary')).toBeInTheDocument();
    expect(screen.getByTestId('phase-contract-persisted')).toHaveTextContent('Alpha Project');
    expect(screen.getByTestId('phase-contract-derived')).toHaveTextContent('evidence item');
    expect(screen.getByTestId('phase-contract-suggested')).toHaveTextContent('Review the latest lifecycle evidence');
    expect(screen.getByTestId('phase-contract-review')).toHaveTextContent('Ready without extra review');
    expect(screen.queryByTestId('canvas-container')).not.toBeInTheDocument();
    expect(screen.getByTestId('primary-next-action')).toBeInTheDocument();

    expect(screen.getByTestId('advanced-tools-description')).toHaveTextContent(
      'Use this when direct canvas edits are needed after reviewing shape readiness and blockers.',
    );

    fireEvent.click(screen.getByRole('button', { name: 'Open canvas editing workspace' }));
    expect(await screen.findByTestId('canvas-container')).toBeInTheDocument();

    vi.mocked(fetch).mockImplementation((input) => {
      const path = String(input);
      if (path.includes('/api/audit/events')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              id: 'audit-shape-1',
              timestamp: '2026-05-07T12:00:00.000Z',
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
        );
      }

      if (path.includes('/api/projects/proj-42/activity')) {
        return Promise.resolve(activityRefetchResponse());
      }

      return Promise.resolve(
        new Response(
          JSON.stringify({ message: `Unexpected test request: ${path}` }),
          { status: 500, headers: { 'Content-Type': 'application/json' } },
        ),
      );
    });

    fireEvent.click(screen.getByTestId('add-components'));
    await waitFor(() => expectFetchCalledWithPath('/api/audit/events'));
    const auditCall = vi.mocked(fetch).mock.calls.find(([input]) => String(input).includes('/api/audit/events'));
    expect(JSON.parse(String(auditCall?.[1]?.body))).toMatchObject({
      type: 'phase.shape.builder_review_started',
      userId: 'designer-1',
      projectId: 'proj-42',
      flowStage: 'shape',
      phase: 'SHAPE',
    });
  });

  it('executes safe one-click cockpit suggestions through the backed phase action path', async () => {
    mockPhaseBootstrap({
      lifecyclePhase: 'CONTEXT',
      nextPhase: 'PLAN',
      canAdvance: true,
      aiNextActions: [],
      projectAccess: {
        isOwned: true,
        isIncluded: false,
        readOnly: false,
        role: 'OWNER',
        capabilities: {
          read: true,
          update: true,
          create: true,
          delete: false,
          include: true,
          comment: true,
        },
      },
    });
    renderRouteWithUser(<ShapeRoute />, {
      id: 'owner-1',
      email: 'owner@example.com',
      name: 'Owner',
      role: 'ADMIN',
      tenantId: 'tenant-1',
      workspaceIds: ['workspace-1'],
    });

    expect(await screen.findByTestId('shape-cockpit')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Run guided action' })).toBeInTheDocument();

    vi.mocked(fetch).mockImplementation((input) => {
      const path = String(input);
      if (path.includes('/api/audit/events')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              id: 'audit-shape-one-click',
              timestamp: '2026-05-07T12:00:00.000Z',
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
        );
      }

      if (path.includes('/api/projects/proj-42/activity')) {
        return Promise.resolve(activityRefetchResponse());
      }

      return Promise.resolve(
        new Response(
          JSON.stringify({ message: `Unexpected test request: ${path}` }),
          { status: 500, headers: { 'Content-Type': 'application/json' } },
        ),
      );
    });

    fireEvent.click(screen.getByRole('button', { name: 'Run guided action' }));

    await waitFor(() => expectFetchCalledWithPath('/api/audit/events'));
    const auditCall = vi.mocked(fetch).mock.calls.find(([input]) => String(input).includes('/api/audit/events'));
    expect(JSON.parse(String(auditCall?.[1]?.body))).toMatchObject({
      type: 'phase.shape.builder_review_started',
      userId: 'owner-1',
      projectId: 'proj-42',
      flowStage: 'shape',
      phase: 'SHAPE',
    });
    expect(await screen.findByTestId('phase-action-result')).toHaveTextContent('Shape review started');
  });

  it('mounts observe with preview runtime observability diagnostics from backed activity', async () => {
    mockPhaseBootstrap({
      lifecyclePhase: 'OBSERVE',
      nextPhase: 'LEARN',
      canAdvance: false,
      readiness: 66,
      requiredArtifacts: ['Preview health review'],
      activity: [
        {
          id: 'runtime-1',
          source: 'audit',
          action: 'preview.runtime.error',
          summary: 'ReferenceError surfaced in preview runtime.',
          timestamp: '2026-04-21T11:04:00.000Z',
          actor: null,
          severity: 'error',
          success: false,
        },
        {
          id: 'console-1',
          source: 'audit',
          action: 'preview.console.warning',
          summary: 'Console warning captured during Observe review.',
          timestamp: '2026-04-21T11:03:00.000Z',
          actor: null,
          severity: 'warning',
          success: true,
        },
        {
          id: 'policy-1',
          source: 'audit',
          action: 'preview.policy.blocked',
          summary: 'Preview policy blocked third-party script execution.',
          timestamp: '2026-04-21T11:02:00.000Z',
          actor: null,
          severity: 'warning',
          success: false,
        },
        {
          id: 'load-1',
          source: 'lifecycle',
          action: 'preview.reload.completed',
          summary: 'Preview reload completed in 615ms.',
          timestamp: '2026-04-21T11:01:00.000Z',
          actor: 'user-1',
          severity: null,
          success: true,
        },
      ],
    });
    renderRoute(<ObserveRoute />);

    expect(await screen.findByTestId('observe-cockpit')).toBeInTheDocument();
    expect(screen.getByTestId('observe-preview-diagnostics')).toBeInTheDocument();
    expect(screen.getByTestId('observe-preview-health')).toHaveTextContent('Preview health: Down');
    expect(screen.getByTestId('preview-runtime-error')).toHaveTextContent('ReferenceError');
    expect(screen.getByTestId('preview-console-log')).toHaveTextContent('Console warning');
    expect(screen.getByTestId('preview-policy-block')).toHaveTextContent('third-party script');
    expect(screen.getByTestId('preview-load-latency')).toHaveTextContent('615ms');
    expect(screen.getByTestId('preview-user-action')).toHaveTextContent('user-1');
  });

  it('surfaces a retryable error when the canonical shape packet fails to load', async () => {
    mockGetPhasePacket.mockRejectedValueOnce(new Error('Phase packet service offline'));

    renderRoute(<ShapeRoute />);

    expect(await screen.findByTestId('phase-packet-error')).toHaveTextContent('Phase packet unavailable');
    expect(screen.getByText('Phase packet service offline')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    expect(screen.queryByTestId('shape-cockpit')).not.toBeInTheDocument();
  });

  it('surfaces a retryable error when the canonical validate packet fails to load', async () => {
    mockGetPhasePacket.mockRejectedValueOnce(new Error('Readiness packet timed out'));

    renderRoute(<ValidateRoute />);

    expect(await screen.findByTestId('phase-packet-error')).toHaveTextContent('Phase packet unavailable');
    expect(screen.getByText('Readiness packet timed out')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    expect(screen.queryByTestId('validate-cockpit')).not.toBeInTheDocument();
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

  it('approves validate transitions through the lifecycle backend', async () => {
    mockPhaseBootstrap({ lifecyclePhase: 'VALIDATE', nextPhase: 'GENERATE' });
    vi.mocked(fetch)
      .mockResolvedValueOnce(auditResponse('audit-validate-1'))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ success: true, currentPhase: 'EXECUTE' }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(activityRefetchResponse());

    const rendered = renderRouteWithUser(<ValidateRoute />, {
      id: 'reviewer-1',
      email: 'reviewer@example.com',
      name: 'Reviewer',
      role: 'ADMIN',
      tenantId: 'tenant-1',
      workspaceIds: ['workspace-1'],
    });

    expect(await screen.findByTestId('validate-cockpit')).toBeInTheDocument();
    await rendered.user.click(screen.getByTestId('approve-changes'));

    await waitFor(() => {
      expect(
        vi.mocked(fetch).mock.calls.some(
          ([input, init]) =>
            String(input) === '/api/v1/lifecycle/advance' &&
            (init as RequestInit | undefined)?.body === JSON.stringify({
              projectId: 'proj-42',
              fromPhase: 'VALIDATE',
              toPhase: 'GENERATE',
              userId: 'reviewer-1',
            }),
        ),
      ).toBe(true);
    });
    expect(await screen.findByText(/Lifecycle transition approved from VALIDATE to EXECUTE/i)).toBeInTheDocument();
  });

  it('starts backend generation and requests diff review from the generate cockpit CTA', async () => {
    mockPhaseBootstrap({
      lifecyclePhase: 'GENERATE',
      nextPhase: 'RUN',
      requiredArtifacts: ['Generated React page', 'Generated tests'],
    });
    vi.mocked(fetch)
      .mockResolvedValueOnce(auditResponse('audit-generate-1'))
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

    renderRouteWithUser(<GenerateRoute />, {
      id: 'generator-1',
      email: 'generator@example.com',
      name: 'Generator',
      role: 'ADMIN',
      tenantId: 'tenant-1',
      workspaceIds: ['workspace-1'],
    });

    expect(await screen.findByTestId('generate-cockpit')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('generate-code'));

    expect(await screen.findByTestId('phase-action-result')).toHaveTextContent('gen-run-1');
    expectFetchCalledWithPath('/api/v1/yappc/generate');
    expectFetchCalledWithPath('/api/v1/yappc/generate/diff');
  });

  it('surfaces generate apply reject and rollback review decisions from the cockpit', async () => {
    mockPhaseBootstrap({
      lifecyclePhase: 'GENERATE',
      nextPhase: 'RUN',
      requiredArtifacts: ['Generated React page', 'Generated tests'],
    });
    vi.mocked(fetch)
      .mockResolvedValueOnce(auditResponse('audit-generate-review-1'))
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
      )
      .mockResolvedValueOnce(activityRefetchResponse());

    const rendered = renderRouteWithUser(<GenerateRoute />, {
      id: 'user-1',
      email: 'operator@example.com',
      name: 'Operator',
      role: 'ADMIN',
      tenantId: 'tenant-1',
      workspaceIds: ['workspace-1'],
    });

    expect(await screen.findByTestId('generate-cockpit')).toBeInTheDocument();
    await rendered.user.click(screen.getByTestId('generate-code'));
    expect(await screen.findByTestId('generate-review-actions')).toBeInTheDocument();
    expect(screen.getByTestId('generate-apply')).not.toBeDisabled();
    expect(screen.getByTestId('generate-reject')).not.toBeDisabled();
    expect(screen.getByTestId('generate-rollback')).not.toBeDisabled();
  });

  it('keeps generate mutations disabled for read-only included projects', async () => {
    mockPhaseBootstrap({
      lifecyclePhase: 'GENERATE',
      nextPhase: 'RUN',
      requiredArtifacts: ['Generated React page', 'Generated tests'],
      projectAccess: {
        isOwned: false,
        isIncluded: true,
        readOnly: true,
        role: 'VIEWER',
        capabilities: {
          read: true,
          update: false,
          create: false,
          delete: false,
          include: false,
          comment: true,
        },
      },
    });

    renderRoute(<GenerateRoute />);

    expect(await screen.findByTestId('generate-cockpit')).toBeInTheDocument();
    expect(screen.getByTestId('generate-code')).toBeDisabled();
    expect(screen.getByText('You have view-only access to this project.')).toBeInTheDocument();
    expect(
      vi.mocked(fetch).mock.calls.some(([input]) => String(input).includes('/api/v1/yappc/generate')),
    ).toBe(false);
  });

  it('starts the run workflow with tenant context and checks workflow status', async () => {
    mockPhaseBootstrap({
      lifecyclePhase: 'RUN',
      nextPhase: 'OBSERVE',
      requiredArtifacts: ['Deployment approval'],
    });
    vi.mocked(fetch)
      .mockResolvedValueOnce(auditResponse('audit-run-1'))
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
    expect(await screen.findByTestId('run-post-actions')).toBeInTheDocument();
    expect(screen.getByTestId('run-retry')).not.toBeDisabled();
    expect(screen.getByTestId('run-rollback')).not.toBeDisabled();
    expect(screen.getByTestId('run-promote')).not.toBeDisabled();
    expect(screen.getByTestId('run-observe-handoff')).not.toBeDisabled();
    expectFetchCalledWithPath('/api/v1/workflows/workflow-run-1/status');
    await waitFor(() => {
      const startCall = vi.mocked(fetch).mock.calls.find(([input]) => String(input).includes('/api/v1/workflows/yappc-run/start'));
      expect(startCall?.[1]?.headers).toMatchObject({ 'X-Tenant-Id': 'tenant-1' });
    });
  });
});

import React from 'react';
import { QueryClient } from '@tanstack/react-query';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Provider, createStore } from 'jotai';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from '@/test-utils/test-utils';
import { currentUserAtom } from '@/stores/user.store';
import type { User } from '@/types/dashboard';
import type { DegradedPacketDetails, HealthSignals, PhaseEvidence } from '@/types/phasePacket';

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
  estimatedReadyIn = 'Ready now',
  estimatedReadyInHours = 0,
  predictionConfidence = 0.82,
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
      eventType: 'PROJECT_UPDATED',
      outcome: 'SUCCESS',
      correlationId: 'corr-audit-1',
    },
  ],
  degradedDetails,
  evidence: evidenceOverride,
  healthSignals: healthSignalsOverride,
}: {
  readonly lifecyclePhase?: string;
  readonly nextPhase?: string;
  readonly canAdvance?: boolean;
  readonly readiness?: number;
  readonly estimatedReadyIn?: string | null;
  readonly estimatedReadyInHours?: number | null;
  readonly predictionConfidence?: number | null;
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
    readonly eventType?: string | null;
    readonly outcome?: string | null;
    readonly correlationId?: string | null;
  }[];
  readonly degradedDetails?: DegradedPacketDetails;
  readonly evidence?: readonly PhaseEvidence[];
  readonly healthSignals?: Partial<HealthSignals>;
} = {}) {
  const defaultHealthSignals: HealthSignals = {
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
  };

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
      estimatedReadyIn,
      estimatedReadyInHours,
      predictionConfidence,
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
      version: '1.0.0',
      title: artifact,
      completedAt: '2026-04-21T11:00:00.000Z',
      completedBy: 'user-1',
      evidenceId: `completed-evidence-${index + 1}`,
    })),
    activityFeed: activity.map((event) => ({
      id: event.id,
      type: event.source,
      action: event.action,
      summary: event.summary,
      actor: event.actor ?? 'system',
      timestamp: event.timestamp,
      severity: event.severity ?? 'INFO',
      eventType: event.eventType ?? event.action,
      success: event.success ?? null,
      outcome: event.outcome ?? (event.success === false ? 'FAILURE' : 'SUCCESS'),
      correlationId: event.correlationId ?? null,
    })),
    evidence: evidenceOverride ?? activity.map((event) => ({
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
      category: 'phase-transition',
      severity: 'high',
      confirmationRequired: true,
      idempotencyKey: `${phase}-primary`,
      auditType: `phase.${phase}.primary.requested`,
      parameters: {},
    }],
    dashboardActions: {
      primaryAction: `${phase}-primary`,
      blockedActions: canAdvance ? [] : [`${phase}-primary`],
      reviewRequiredActions: [],
      safeToContinueActions: canAdvance ? [`${phase}-primary`] : [],
    },
    healthSignals: {
      ...defaultHealthSignals,
      ...healthSignalsOverride,
    },
    degradedDetails,
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

  it('renders backend-provided transition estimate and confidence from the phase packet', async () => {
    mockPhaseBootstrap({
      estimatedReadyIn: '~6 hours',
      estimatedReadyInHours: 6,
      predictionConfidence: 0.64,
    });

    renderRoute(<ShapeRoute />);

    expect(await screen.findByTestId('shape-cockpit')).toBeInTheDocument();
    expect(screen.getByTestId('phase-packet-estimate')).toHaveTextContent('Ready estimate: ~6 hours');
    expect(screen.getByTestId('phase-packet-confidence')).toHaveTextContent('Prediction confidence: 64%');
  });

  it('renders traceable activity actor, outcome, event type, timestamp, and correlation ID', async () => {
    mockPhaseBootstrap({
      activity: [{
        id: 'audit-failure-1',
        source: 'audit',
        action: 'shape.validate',
        summary: 'Shape validation failed',
        timestamp: '2026-04-21T12:34:56.000Z',
        actor: 'designer-1',
        severity: 'ERROR',
        success: false,
        eventType: 'PHASE_ACTION_EXECUTED',
        outcome: 'FAILURE',
        correlationId: 'corr-shape-1',
      }],
    });

    renderRoute(<ShapeRoute />);

    expect(await screen.findByTestId('shape-cockpit')).toBeInTheDocument();
    expect(screen.getAllByText('Shape validation failed').length).toBeGreaterThan(0);
    expect(screen.getByTestId('activity-trace')).toHaveTextContent('Actor: designer-1');
    expect(screen.getByTestId('activity-trace')).toHaveTextContent('Event: PHASE_ACTION_EXECUTED');
    expect(screen.getByTestId('activity-trace')).toHaveTextContent('Outcome: FAILURE');
    expect(screen.getByTestId('activity-trace')).toHaveTextContent('Correlation ID: corr-shape-1');
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

  it('surfaces phase packet correlation id for support handoff', async () => {
    mockGetPhasePacket.mockRejectedValueOnce(new Error('Readiness packet timed out [Correlation ID: corr-phase-1]'));

    renderRoute(<ValidateRoute />);

    expect(await screen.findByTestId('phase-packet-error')).toHaveTextContent('Correlation ID: corr-phase-1');
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
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

  it('shows Data Cloud degraded packet recovery details and blocks stale enabled actions', async () => {
    mockPhaseBootstrap({
      lifecyclePhase: 'SHAPE',
      nextPhase: 'VALIDATE',
      canAdvance: true,
      degradedDetails: {
        dependency: 'DATA_CLOUD',
        reason: 'PROJECT_STATE_QUERY_FAILED',
        truthSource: 'projects',
        recoveryAction: 'Restore Data Cloud project state access before phase actions run.',
        impactedFeatures: ['phase-actions', 'artifact-status'],
      },
    });

    renderRoute(<ShapeRoute />);

    expect(await screen.findByTestId('shape-cockpit')).toBeInTheDocument();
    expect(screen.getByTestId('phase-degraded-details')).toHaveTextContent('Dependency degraded');
    expect(screen.getByTestId('phase-degraded-dependency')).toHaveTextContent('DATA_CLOUD');
    expect(screen.getByTestId('phase-degraded-truth-source')).toHaveTextContent('projects');
    expect(screen.getByTestId('phase-degraded-reason')).toHaveTextContent('PROJECT_STATE_QUERY_FAILED');
    expect(screen.getByTestId('phase-degraded-recovery')).toHaveTextContent('Restore Data Cloud project state access');
    expect(screen.getByTestId('phase-degraded-impacted-features')).toHaveTextContent('phase-actions, artifact-status');
    expect(screen.getByTestId('add-components')).toBeDisabled();
    expect(screen.getAllByText('Restore Data Cloud project state access before phase actions run.').length).toBeGreaterThan(0);
    expect(vi.mocked(fetch)).not.toHaveBeenCalled();
  });

  it('shows AEP evidence degradation and keeps unsafe validation advance disabled', async () => {
    mockPhaseBootstrap({
      lifecyclePhase: 'VALIDATE',
      nextPhase: 'GENERATE',
      canAdvance: false,
      readiness: 41,
      requiredArtifacts: ['Phase evidence unavailable'],
      evidence: [{
        id: 'EVIDENCE_QUERY_FAILED',
        type: 'SYSTEM_DEGRADED',
        title: 'Phase evidence unavailable',
        description: 'AEP evidence unavailable. Retry evidence sync before advancing.',
        timestamp: '2026-04-21T11:03:00.000Z',
        metadata: { dependency: 'AEP' },
        evidenceId: 'EVIDENCE_QUERY_FAILED',
      }],
    });

    renderRoute(<ValidateRoute />);

    expect(await screen.findByTestId('validate-cockpit')).toBeInTheDocument();
    expect(screen.getAllByText('Phase evidence unavailable').length).toBeGreaterThan(0);
    expect(screen.getByText('AEP evidence unavailable. Retry evidence sync before advancing.')).toBeInTheDocument();
    expect(screen.getByTestId('approve-changes')).toBeDisabled();
    expect(
      vi.mocked(fetch).mock.calls.some(([input]) => String(input).includes('/api/v1/lifecycle/advance')),
    ).toBe(false);
  });

  it('shows Kernel degraded runtime details and blocks Run controls before handoff', async () => {
    mockPhaseBootstrap({
      lifecyclePhase: 'RUN',
      nextPhase: 'OBSERVE',
      canAdvance: true,
      degradedDetails: {
        dependency: 'KERNEL',
        reason: 'MALFORMED_KERNEL_LIFECYCLE_TRUTH',
        truthSource: 'kernel_lifecycle_truth',
        recoveryAction: 'Repair Kernel lifecycle truth records and replay the latest lifecycle event.',
        impactedFeatures: ['kernel-health', 'run-promote', 'observe-handoff'],
      },
      healthSignals: {
        runtime: {
          isHealthy: false,
          status: 'degraded',
          lastDeployedAt: '2026-04-21T11:00:00.000Z',
          issues: ['Kernel lifecycle truth is malformed'],
        },
      },
    });

    renderRoute(<RunRoute />);

    expect(await screen.findByTestId('run-cockpit')).toBeInTheDocument();
    expect(screen.getByTestId('phase-degraded-dependency')).toHaveTextContent('KERNEL');
    expect(screen.getByTestId('phase-degraded-truth-source')).toHaveTextContent('kernel_lifecycle_truth');
    expect(screen.getByTestId('phase-degraded-recovery')).toHaveTextContent('Repair Kernel lifecycle truth records');
    expect(screen.getByTestId('check-readiness')).toBeDisabled();
    expect(
      vi.mocked(fetch).mock.calls.some(([input]) => String(input).includes('/api/v1/workflows/yappc-run/start')),
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

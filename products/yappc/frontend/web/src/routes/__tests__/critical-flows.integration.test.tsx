import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  RouterProvider,
  createMemoryRouter,
  type RouteObject,
} from 'react-router';
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest';

interface TestWorkspace {
  id: string;
  name: string;
  description?: string;
  ownerId: string;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
  aiSummary: string;
  aiTags: string[];
  projectCount: number;
  memberCount: number;
}

interface TestProject {
  id: string;
  name: string;
  description?: string;
  ownerWorkspaceId: string;
  workspaceId?: string;
  type: 'FULL_STACK' | 'BACKEND' | 'MOBILE' | 'UI';
  lifecyclePhase: 'INTENT' | 'SHAPE' | 'VALIDATE' | 'GENERATE' | 'RUN' | 'OBSERVE' | 'LEARN' | 'EVOLVE';
  currentPhase?: string;
  status: string;
  aiHealthScore?: number;
  aiNextActions?: string[];
  updatedAt?: string;
  lastActivityAt?: string;
  isOwned?: boolean;
  phaseProgress?: number;
}

interface WorkspaceContextState {
  currentWorkspaceId: string | null;
  currentWorkspace?: TestWorkspace;
  workspaces: TestWorkspace[];
  ownedProjects: TestProject[];
  includedProjects: TestProject[];
}

interface PhasePreview {
  projectId: string;
  currentPhase: string;
  nextPhase: string | null;
  canAdvance: boolean;
  readiness: number;
  blockers: string[];
  requiredArtifacts: string[];
  completedArtifacts: string[];
  estimatedReadyIn: string;
  estimatedReadyInHours: number;
  predictionConfidence: number;
  checkedAt: string;
}

const {
  appState,
  atoms,
  mockCreateWorkspaceMutateAsync,
  mockGetNextPhase,
  mockSetCurrentWorkspaceId,
  mockSetProjectBreadcrumb,
  mockSetSectionBreadcrumb,
  mockSetWorkspaceBreadcrumb,
  mockSuggestProject,
  mockSuggestWorkspace,
  mockTransition,
} = vi.hoisted(() => {
  const hoistedAppState: WorkspaceContextState = {
    currentWorkspaceId: null,
    currentWorkspace: undefined,
    workspaces: [],
    ownedProjects: [],
    includedProjects: [],
  };

  return {
    appState: hoistedAppState,
    atoms: {
      currentUserAtom: Symbol('currentUserAtom'),
      currentWorkspaceIdAtom: Symbol('currentWorkspaceIdAtom'),
      headerActionContextAtom: Symbol('headerActionContextAtom'),
      headerContextActionsAtom: Symbol('headerContextActionsAtom'),
      headerPhaseInfoAtom: Symbol('headerPhaseInfoAtom'),
      headerVisibleAtom: Symbol('headerVisibleAtom'),
      setCurrentWorkspaceAtom: Symbol('setCurrentWorkspaceAtom'),
      setProjectBreadcrumbAtom: Symbol('setProjectBreadcrumbAtom'),
      setSectionBreadcrumbAtom: Symbol('setSectionBreadcrumbAtom'),
      setWorkspaceBreadcrumbAtom: Symbol('setWorkspaceBreadcrumbAtom'),
    },
    mockCreateWorkspaceMutateAsync: vi.fn(),
    mockGetNextPhase: vi.fn(),
    mockSetCurrentWorkspaceId: vi.fn(),
    mockSetProjectBreadcrumb: vi.fn(),
    mockSetSectionBreadcrumb: vi.fn(),
    mockSetWorkspaceBreadcrumb: vi.fn(),
    mockSuggestProject: vi.fn(),
    mockSuggestWorkspace: vi.fn(),
    mockTransition: vi.fn(),
  };
});

function cloneState(): WorkspaceContextState {
  return {
    currentWorkspaceId: appState.currentWorkspaceId,
    currentWorkspace: appState.currentWorkspace,
    workspaces: [...appState.workspaces],
    ownedProjects: [...appState.ownedProjects],
    includedProjects: [...appState.includedProjects],
  };
}

function switchWorkspace(workspaceId: string): void {
  appState.currentWorkspaceId = workspaceId;
  appState.currentWorkspace = appState.workspaces.find(
    (workspace) => workspace.id === workspaceId
  );
}

vi.mock('jotai', async (importOriginal) => {
  const actual = await importOriginal<typeof import('jotai')>();

  return {
    ...actual,
    useAtom: () => [null, vi.fn()],
    useAtomValue: (atom: unknown) => {
      if (atom === atoms.currentWorkspaceIdAtom) {
        return appState.currentWorkspaceId;
      }

      if (atom === atoms.currentUserAtom) {
        return {
          id: 'user-1',
          firstName: 'Test',
          lastName: 'User',
          email: 'test@example.com',
        };
      }

      if (atom === atoms.headerVisibleAtom) {
        return true;
      }

      return null;
    },
    useSetAtom: (atom: unknown) => {
      if (atom === atoms.setWorkspaceBreadcrumbAtom) {
        return mockSetWorkspaceBreadcrumb;
      }

      if (atom === atoms.setProjectBreadcrumbAtom) {
        return mockSetProjectBreadcrumb;
      }

      if (atom === atoms.setSectionBreadcrumbAtom) {
        return mockSetSectionBreadcrumb;
      }

      if (atom === atoms.currentWorkspaceIdAtom) {
        return mockSetCurrentWorkspaceId;
      }

      return vi.fn();
    },
  };
});

vi.mock('../../hooks/useWorkspaceData', () => ({
  useCreateWorkspace: () => ({
    mutateAsync: mockCreateWorkspaceMutateAsync,
    isPending: false,
  }),
  useNameSuggestions: () => ({
    suggestWorkspace: mockSuggestWorkspace,
    suggestProject: mockSuggestProject,
  }),
  useWorkspaceContext: () => ({
    ...cloneState(),
    isLoading: false,
    error: null,
    refetch: vi.fn(),
    switchWorkspace,
  }),
}));

vi.mock('../../state/atoms/workspaceAtom', () => ({
  currentWorkspaceIdAtom: atoms.currentWorkspaceIdAtom,
}));

vi.mock('../../state/atoms/breadcrumbAtom', () => ({
  setProjectBreadcrumbAtom: atoms.setProjectBreadcrumbAtom,
  setSectionBreadcrumbAtom: atoms.setSectionBreadcrumbAtom,
  setWorkspaceBreadcrumbAtom: atoms.setWorkspaceBreadcrumbAtom,
}));

vi.mock('../../stores/user.store', () => ({
  currentUserAtom: atoms.currentUserAtom,
}));

vi.mock('../../state/atoms/layoutAtom', () => ({
  headerActionContextAtom: atoms.headerActionContextAtom,
  headerContextActionsAtom: atoms.headerContextActionsAtom,
  headerPhaseInfoAtom: atoms.headerPhaseInfoAtom,
  headerVisibleAtom: atoms.headerVisibleAtom,
}));

vi.mock('../../providers/AuthProvider', () => ({
  useCurrentUser: () => ({
    id: 'user-1',
    name: 'Test User',
    email: 'test@example.com',
    initials: 'TU',
    isAuthenticated: true,
  }),
}));

vi.mock('../../components/navigation', () => ({
  UnifiedContextHeader: () => <div data-testid="unified-context-header" />,
}));

vi.mock('../../components/intent', () => ({
  IntentDrawer: () => null,
}));

vi.mock('../../hooks/useLastOpenedProject', () => ({
  useLastOpenedProject: () => ({ setLastOpenedProject: vi.fn() }),
}));

vi.mock('../../components/route/ErrorBoundary', () => ({
  RouteErrorBoundary: () => <div data-testid="route-error-boundary" />,
}));

vi.mock('../../components/deploy/DeployPanelHost', () => ({
  DeployPanelHost: () => <div data-testid="deploy-panel-host">Deploy panel</div>,
}));

vi.mock('../../components/lifecycle', () => ({
  LifecycleExplorer: ({ projectId }: { projectId: string }) => (
    <div data-testid="lifecycle-explorer">Lifecycle Explorer {projectId}</div>
  ),
}));

vi.mock('../../services/canvas/lifecycle', () => ({
  useLifecycleArtifacts: () => ({
    artifacts: [],
    createArtifact: vi.fn(),
    updateArtifact: vi.fn(),
  }),
  usePhaseGates: () => ({
    currentPhase: 'EXECUTE',
    transition: mockTransition,
  }),
}));

// The deploy route and shell import these services at their full paths.
// Mock them to prevent real fetch calls in integration tests.
vi.mock('../../services/canvas/lifecycle/LifecycleArtifactService', () => ({
  useLifecycleArtifacts: () => ({
    artifacts: [],
    createArtifact: vi.fn(),
    updateArtifact: vi.fn(),
    loading: false,
    error: null,
  }),
}));

vi.mock('../../services/canvas/lifecycle/PhaseGateService', () => ({
  usePhaseGates: () => ({
    currentPhase: 'LEARN',
    gateStatuses: {},
    canTransition: vi.fn().mockReturnValue(true),
    transition: mockTransition,
  }),
}));

vi.mock('../../hooks/useAgentRunStream', () => ({
  useAgentRunStream: () => ({
    runs: [],
    setRuns: vi.fn(),
    isConnected: false,
  }),
}));

vi.mock('../../services/LifecycleWebSocketService', () => ({
  LifecycleWebSocketService: class {
    public connect(): void {}
    public disconnect(): void {}
    public onUpdate(): () => void {
      return () => {};
    }
    public onConnectionChange(): () => void {
      return () => {};
    }
  },
}));

vi.mock('../../hooks/useLifecycleTransition', () => ({
  useLifecycleTransition: () => ({
    handleApprovalTransition: vi.fn(),
    handleOneClickApproval: vi.fn(),
    handleAutomationClick: vi.fn(),
    clearFeedback: vi.fn(),
    automationFeedback: null,
    isTransitioning: false,
  }),
}));

vi.mock('../../hooks/useRequirementOrchestration', () => ({
  useRequirementOrchestration: () => ({
    submitApproved: vi.fn(),
    runRef: undefined,
    isSubmitting: false,
    error: null,
  }),
}));

vi.mock('../../hooks/useLifecycleData', () => ({
  useAIInsights: () => ({
    data: [
      {
        phase: 'LEARN',
        title: 'Deployment evidence is current',
        description: 'Recent activity confirms the current release posture.',
        type: 'insight',
        flowStage: 7,
        timestamp: '2026-04-20T12:00:00.000Z',
      },
    ],
  }),
  useAIRecommendations: () => ({
    data: [
      {
        id: 'rec-1',
        title: 'Validate release packet',
        description: 'Review the most recent lifecycle evidence before promotion.',
        confidence: 0.89,
        priority: 'high',
        persona: 'operator',
        type: 'validation',
      },
    ],
  }),
  useApplyLifecycleAutomationPlan: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
  useExecuteTask: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
  useLifecycleAutomationPlan: () => ({
    data: {
      projectId: 'proj-1',
      currentPhase: 'LEARN',
      nextPhase: null,
      canAutoAdvance: false,
      readiness: 74,
      blockers: [],
      estimatedReadyIn: '~1 day',
      estimatedReadyInHours: 24,
      predictionConfidence: 0.76,
      decisionSupport: {
        defaults: {
          approvalMode: 'manual_review',
          riskTolerance: 'low',
          validationDepth: 'deep',
          targetEnvironment: 'production',
          ownerRole: 'operator',
        },
        suggestions: [],
        progressiveDisclosure: {
          primaryActions: [],
          secondaryActions: [],
        },
      },
      execution: null,
      generatedAt: '2026-04-20T12:00:00.000Z',
    },
  }),
  useNextBestTask: () => ({
    data: {
      id: 'task-1',
      title: 'Validate release packet',
      description: 'Check lifecycle evidence before promotion.',
      phase: 'LEARN',
      flowStage: 7,
      persona: 'operator',
      priority: 'high',
      status: 'pending',
    },
  }),
  useReadinessAnomalies: () => ({
    data: [],
  }),
}));

vi.mock('@/services/lifecycle/phase-transition-api', () => ({
  phaseTransitionAPI: {
    getNextPhase: mockGetNextPhase,
  },
}));

vi.mock('@/components/workspace', async () => {
  const actual = await import('../../components/workspace/OnboardingFlow');

  return {
    OnboardingFlow: actual.OnboardingFlow,
    CreateProjectDialog: ({ isOpen }: { isOpen: boolean }) =>
      isOpen ? <div data-testid="create-project-dialog" /> : null,
  };
});

vi.mock('../../components/workspace', async () => {
  const actual = await import('../../components/workspace/OnboardingFlow');

  return {
    OnboardingFlow: actual.OnboardingFlow,
    CreateProjectDialog: ({ isOpen }: { isOpen: boolean }) =>
      isOpen ? <div data-testid="create-project-dialog" /> : null,
  };
});

vi.mock('../../components/workspace/CreateWorkspaceDialog', () => ({
  CreateWorkspaceDialog: ({ isOpen }: { isOpen: boolean }) =>
    isOpen ? <div data-testid="create-workspace-dialog" /> : null,
}));

// Enable all lifecycle phase tabs so the shell renders test-navigable tabs.
vi.mock('../../hooks/usePhaseFeatureGate', () => ({
  usePhaseFeatureGate: () => ({
    isPhaseEnabled: () => true,
    getEnabledPhases: () => ['intent', 'shape', 'validate', 'generate', 'run', 'observe', 'learn', 'evolve'],
  }),
}));

// Mock the onboarding status service so the route never auto-redirects while
// the test verifies the "You're All Set" step. markComplete still writes to
// localStorage so the localStorage assertion at the end of test 1 passes.
vi.mock('../../services/onboarding/OnboardingStatusService', () => ({
  useOnboardingStatus: () => ({
    status: { completed: false },
    isLoading: false,
    isError: false,
    error: null,
    markComplete: vi.fn(async (persona?: { primary?: string; active?: string[] }) => {
      localStorage.setItem('onboarding_complete', 'true');
      if (persona?.primary) {
        localStorage.setItem('yappc_primary_persona', JSON.stringify(persona.primary));
      }
      if (persona?.active) {
        localStorage.setItem('yappc_active_personas', JSON.stringify(persona.active));
      }
      return { completed: true };
    }),
    markIncomplete: vi.fn(),
    isUpdating: false,
  }),
}));

vi.mock('../app/project/lifecycle', () => ({
  default: () => <div data-testid="lifecycle-explorer">Lifecycle Explorer proj-1</div>,
}));

vi.mock('../app/project/deploy', () => ({
  default: () => (
    <div>
      <span data-testid="release-planning-status-badge">Planning-ready</span>
    </div>
  ),
}));

vi.mock('../app/project/preview', () => ({
  default: () => (
    <div>
      <span data-testid="preview-status-badge">External preview ready</span>
      <iframe
        title="Project Preview"
        src="https://preview.example.test/preview/proj-1"
      />
    </div>
  ),
}));

import OnboardingRoute from '../onboarding';
import WorkspaceSettingsRoute from '../settings';
import ProjectsRoute from '../app/projects';
import WorkspacesRoute from '../app/workspaces';
import ProjectOverviewRoute from '../app/project/index';
import { Layout as ProjectShellLayout } from '../app/project/_shell';
import LifecycleRoute from '../app/project/lifecycle';
import DeployRoute from '../app/project/deploy';
import PreviewRoute from '../app/project/preview';

function buildProjectRecord(projectId = 'proj-1'): TestProject {
  return {
    id: projectId,
    name: 'Alpha Project',
    description: 'A truthful overview surface.',
    ownerWorkspaceId: 'ws-1',
    workspaceId: 'ws-1',
    type: 'FULL_STACK',
    lifecyclePhase: 'LEARN',
    currentPhase: 'LEARN',
    status: 'ACTIVE',
    aiHealthScore: 88,
    aiNextActions: ['Validate release packet'],
    updatedAt: '2026-04-20T12:00:00.000Z',
    lastActivityAt: '2026-04-20T12:00:00.000Z',
    isOwned: true,
    phaseProgress: 72,
  };
}

function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function createRoutes(): RouteObject[] {
  return [
    {
      path: '/onboarding',
      element: <OnboardingRoute />,
    },
    {
      path: '/workspaces',
      element: <WorkspacesRoute />,
    },
    {
      path: '/settings',
      element: <WorkspaceSettingsRoute />,
    },
    {
      path: '/projects',
      element: <ProjectsRoute />,
    },
    {
      path: '/p/:projectId',
      element: <ProjectShellLayout />,
      children: [
        {
          index: true,
          element: <ProjectOverviewRoute />,
        },
        {
          // 'learn' phase tab → renders the lifecycle explorer
          path: 'learn',
          element: <LifecycleRoute />,
        },
        {
          // 'run' phase tab → renders the deploy/release planning surface
          path: 'run',
          element: <DeployRoute />,
        },
        {
          // 'observe' phase tab → renders the project preview surface
          path: 'observe',
          element: <PreviewRoute />,
        },
      ],
    },
  ];
}

function renderRouter(initialEntry: string) {
  const router = createMemoryRouter(createRoutes(), {
    initialEntries: [initialEntry],
  });
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );

  return router;
}

function seedWorkspaceAndProjectState(): void {
  const workspace: TestWorkspace = {
    id: 'ws-1',
    name: 'Workspace Alpha',
    description: 'Primary workspace',
    ownerId: 'user-1',
    isDefault: true,
    createdAt: '2026-04-20T12:00:00.000Z',
    updatedAt: '2026-04-20T12:00:00.000Z',
    aiSummary: '',
    aiTags: ['persona:developer'],
    projectCount: 1,
    memberCount: 1,
  };
  const project = buildProjectRecord();

  appState.currentWorkspaceId = workspace.id;
  appState.currentWorkspace = workspace;
  appState.workspaces = [workspace];
  appState.ownedProjects = [project];
  appState.includedProjects = [];
}

describe('Yappc mounted critical flows', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.unstubAllEnvs();
    vi.stubEnv('VITE_FEATURE_PROJECT_PREVIEW', 'true');
    vi.stubEnv('VITE_PREVIEW_BASE_URL', 'https://preview.example.test');

    appState.currentWorkspaceId = null;
    appState.currentWorkspace = undefined;
    appState.workspaces = [];
    appState.ownedProjects = [];
    appState.includedProjects = [];

    mockSuggestWorkspace.mockResolvedValue('Suggested Workspace');
    mockSuggestProject.mockResolvedValue('Suggested Project');
    mockTransition.mockResolvedValue({
      success: true,
      newPhase: 'EVOLVE',
      errors: [],
      warnings: [],
    });
    mockGetNextPhase.mockResolvedValue({
      projectId: 'proj-1',
      currentPhase: 'LEARN',
      nextPhase: 'EVOLVE',
      canAdvance: true,
      readiness: 92,
      blockers: [],
      requiredArtifacts: ['Documentation'],
      completedArtifacts: ['Documentation'],
      estimatedReadyIn: 'Ready now',
      estimatedReadyInHours: 0,
      predictionConfidence: 0.91,
      checkedAt: '2026-04-20T12:00:00.000Z',
    } satisfies PhasePreview);

    mockCreateWorkspaceMutateAsync.mockImplementation(
      async (input: {
        name: string;
        createDefaultProject: boolean;
        personaSelections?: string[];
        defaultProject?: { name: string; type: TestProject['type'] };
      }) => {
        const workspace: TestWorkspace = {
          id: 'ws-1',
          name: input.name,
          description: 'Created from onboarding',
          ownerId: 'user-1',
          isDefault: true,
          createdAt: '2026-04-20T12:00:00.000Z',
          updatedAt: '2026-04-20T12:00:00.000Z',
          aiSummary: '',
          aiTags: (input.personaSelections ?? []).map((persona) => `persona:${persona}`),
          projectCount: 1,
          memberCount: 1,
        };
        const project: TestProject = {
          ...buildProjectRecord('proj-onboarding'),
          name: input.defaultProject?.name ?? 'Suggested Project',
          type: input.defaultProject?.type ?? 'FULL_STACK',
        };

        appState.currentWorkspaceId = workspace.id;
        appState.currentWorkspace = workspace;
        appState.workspaces = [workspace];
        appState.ownedProjects = [project];
        appState.includedProjects = [];

        return workspace;
      }
    );

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
        const url =
          typeof input === 'string'
            ? input
            : input instanceof URL
              ? input.toString()
              : input.url;
        const method = init?.method ?? 'GET';

        if (url === '/api/workspaces/ws-1' && method === 'GET') {
          return jsonResponse({
            workspace: {
              id: 'ws-1',
              name: 'Workspace Alpha',
              description: 'Primary workspace',
              ownerId: 'user-1',
              createdAt: '2026-04-20T12:00:00.000Z',
              updatedAt: '2026-04-20T12:00:00.000Z',
            },
          });
        }

        if (url === '/api/projects/proj-1' && method === 'GET') {
          return jsonResponse({
            project: {
              ...buildProjectRecord('proj-1'),
              ownerWorkspace: { id: 'ws-1', name: 'Workspace Alpha' },
            },
          });
        }

        if (url === '/api/projects/proj-1/activity' && method === 'GET') {
          return jsonResponse({
            projectId: 'proj-1',
            activity: [
              {
                id: 'activity-1',
                source: 'audit',
                action: 'PROJECT_CREATED',
                summary:
                  'Project Alpha Project created in workspace Workspace Alpha',
                timestamp: '2026-04-20T12:00:00.000Z',
                actor: 'user-1',
              },
            ],
          });
        }

        if (url === '/api/projects/proj-1/artifacts' && method === 'GET') {
          return jsonResponse({ artifacts: [] });
        }

        if (url === '/api/projects/proj-1/sprints/current' && method === 'GET') {
          return jsonResponse({ sprint: null });
        }

        if (url === '/api/projects/proj-1/backlog?limit=20' && method === 'GET') {
          return jsonResponse({ items: [] });
        }

        if (url === '/api/projects/proj-1/runs?limit=10' && method === 'GET') {
          return jsonResponse({ runs: [] });
        }

        if (url === 'https://preview.example.test/health' && method === 'HEAD') {
          return new Response(null, { status: 200 });
        }

        // Onboarding status endpoint — served so fetch doesn't throw an
        // unhandled error warning from the service, even though useOnboardingStatus
        // is mocked at the hook level.
        if (url.endsWith('/api/onboarding/status')) {
          return jsonResponse({ completed: false });
        }

        return jsonResponse(
          {
            error: 'Unhandled mock request',
            method,
            url,
          },
          404
        );
      })
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('completes onboarding durably and lands on the mounted workspace surface', async () => {
    const user = userEvent.setup();
    const router = renderRouter('/onboarding');

    await user.click(await screen.findByRole('button', { name: /let's go/i }));

    const workspaceInput = await screen.findByPlaceholderText(
      'My Awesome Workspace'
    );
    fireEvent.change(workspaceInput, {
      target: { value: 'Launch Workspace' },
    });
    await user.click(screen.getByRole('button', { name: /continue/i }));

    const projectInput = await screen.findByPlaceholderText(
      'My First Project'
    );
    fireEvent.change(projectInput, {
      target: { value: 'Launch Project' },
    });
    await user.click(screen.getByRole('button', { name: /create & finish/i }));

    expect(await screen.findByText("You're All Set")).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /go to dashboard/i }));

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/workspaces');
    });

    expect(await screen.findByTestId('workspaces-page')).toBeInTheDocument();
    expect(screen.getByText('Launch Workspace')).toBeInTheDocument();
    expect(localStorage.getItem('onboarding_complete')).toBe('true');
    expect(appState.ownedProjects[0]?.name).toBe('Launch Project');
    expect(mockCreateWorkspaceMutateAsync).toHaveBeenCalledWith({
      name: 'Launch Workspace',
      createDefaultProject: true,
      personaSelections: ['developer'],
      defaultProject: {
        name: 'Launch Project',
        type: 'FULL_STACK',
      },
    });
  });

  it('routes workspace settings through the mounted supported path', async () => {
    seedWorkspaceAndProjectState();
    const user = userEvent.setup();
    const router = renderRouter('/workspaces');

    await user.click(await screen.findByRole('button', { name: /settings/i }));

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/settings');
    });

    expect(await screen.findByDisplayValue('Workspace Alpha')).toBeInTheDocument();
  });

  it('navigates from workspaces to projects and through the mounted project shell routes', async () => {
    seedWorkspaceAndProjectState();
    const user = userEvent.setup();
    const router = renderRouter('/workspaces');

    await user.click(await screen.findByRole('button', { name: 'Open' }));

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/projects');
    });

    await user.click(await screen.findByRole('button', { name: /alpha project/i }));

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/p/proj-1');
    });

    expect(await screen.findByTestId('project-overview-route')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByTestId('project-overview-promotion-status')).toHaveTextContent(
        'Ready for EVOLVE'
      );
    });

    await user.click(screen.getByRole('tab', { name: /learn/i }));
    expect(await screen.findByTestId('lifecycle-explorer')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: /run/i }));
    expect(await screen.findByTestId('release-planning-status-badge')).toHaveTextContent(
      'Planning-ready'
    );

    await user.click(screen.getByRole('tab', { name: /observe/i }));
    expect(await screen.findByTestId('preview-status-badge')).toHaveTextContent(
      'External preview ready'
    );
    expect(screen.getByTitle('Project Preview')).toHaveAttribute(
      'src',
      'https://preview.example.test/preview/proj-1'
    );
  });
});
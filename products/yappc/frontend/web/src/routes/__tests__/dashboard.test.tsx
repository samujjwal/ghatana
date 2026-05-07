import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ProjectWithOwnership, Workspace } from '../../state/atoms/workspaceAtom';

const mockNavigate = vi.fn();
const mockSetLastOpenedProject = vi.fn();
const mockUseWorkspaceContext = vi.hoisted(() => vi.fn());
const mockExecuteDashboardAction = vi.hoisted(() => vi.fn());

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('jotai', async (importOriginal) => {
  const actual = await importOriginal<typeof import('jotai')>();
  return {
    ...actual,
    useSetAtom: () => vi.fn(),
  };
});

vi.mock('../../hooks/useWorkspaceData', () => ({
  useWorkspaceContext: mockUseWorkspaceContext,
}));

vi.mock('../../hooks/useLastOpenedProject', () => ({
  useLastOpenedProject: () => ({
    getLastOpenedProject: () => null,
    setLastOpenedProject: mockSetLastOpenedProject,
  }),
}));

vi.mock('../../providers/AuthProvider', () => ({
  useCurrentUser: () => ({
    id: 'user-1',
    email: 'user@example.test',
    name: 'User One',
    isAuthenticated: true,
  }),
}));

vi.mock('../../lib/api', () => ({
  yappcApi: {
    projects: {
      executeDashboardAction: mockExecuteDashboardAction,
    },
  },
}));

import DashboardRoute from '../dashboard';

const workspace: Workspace = {
  id: 'ws-1',
  name: 'Workspace One',
  description: 'Current workspace',
  ownerId: 'user-1',
  role: 'EDITOR',
  isOwner: false,
  capabilities: { read: true, create: true, update: true, delete: false, comment: true },
  isDefault: true,
  aiTags: [],
  createdAt: '2026-04-01T00:00:00.000Z',
  updatedAt: '2026-04-02T00:00:00.000Z',
};

function buildProject(overrides: Partial<ProjectWithOwnership> = {}): ProjectWithOwnership {
  return {
    id: 'proj-1',
    name: 'Generate Project',
    description: 'Backed project',
    type: 'FULL_STACK',
    status: 'ACTIVE',
    lifecyclePhase: 'GENERATE',
    ownerWorkspaceId: 'ws-1',
    isOwned: true,
    isDefault: false,
    aiNextActions: [],
    aiHealthScore: 80,
    createdAt: '2026-04-01T00:00:00.000Z',
    updatedAt: '2026-04-03T00:00:00.000Z',
    ...overrides,
  };
}

function renderRoute(): void {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: {
        retry: false,
      },
      queries: {
        retry: false,
      },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <DashboardRoute />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('DashboardRoute source-of-truth actions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockExecuteDashboardAction.mockResolvedValue({
      projectId: 'project-safe',
      actionId: 'safe-1',
      outcome: 'opened-phase-cockpit',
      targetPhase: 'shape',
      targetPath: '/p/project-safe/shape',
      auditRecorded: true,
    });
    mockUseWorkspaceContext.mockReturnValue({
      ownedProjects: [buildProject()],
      includedProjects: [],
      workspaces: [workspace],
      currentWorkspace: workspace,
      dashboardActions: {
        workspaceId: 'ws-1',
        blockedWork: [],
        reviewRequired: [],
        safeToContinue: [],
        generatedAt: '2026-05-07T00:00:00.000Z',
      },
      dashboardActionsLoading: false,
      dashboardActionsError: null,
      isLoading: false,
    });
  });

  it('routes recommended fallback actions to the project phase route from backend lifecycle state', async () => {
    const user = userEvent.setup();
    renderRoute();

    expect(screen.getByText(/continue: resume generate phase/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /open next step/i }));

    expect(mockNavigate).toHaveBeenCalledWith('/p/proj-1/generate');
  });

  it('routes legacy backend lifecycle aliases through canonical mounted phases', async () => {
    const user = userEvent.setup();
    mockUseWorkspaceContext.mockReturnValue({
      ownedProjects: [
        buildProject({
          id: 'proj-legacy',
          name: 'Legacy Context Project',
          lifecyclePhase: 'CONTEXT',
        }),
      ],
      includedProjects: [],
      workspaces: [workspace],
      currentWorkspace: workspace,
      dashboardActions: {
        workspaceId: 'ws-1',
        blockedWork: [],
        reviewRequired: [],
        safeToContinue: [],
        generatedAt: '2026-05-07T00:00:00.000Z',
      },
      dashboardActionsLoading: false,
      dashboardActionsError: null,
      isLoading: false,
    });

    renderRoute();

    expect(screen.getByText(/continue: resume shape phase/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /open next step/i }));

    expect(mockNavigate).toHaveBeenCalledWith('/p/proj-legacy/shape');
  });

  it('routes server-provided next actions to a valid project phase route', async () => {
    const user = userEvent.setup();
    mockUseWorkspaceContext.mockReturnValue({
      ownedProjects: [
        buildProject({
          id: 'proj-2',
          name: 'Run Project',
          lifecyclePhase: 'RUN',
          aiNextActions: ['Review deployment blockers'],
        }),
      ],
      includedProjects: [],
      workspaces: [workspace],
      currentWorkspace: workspace,
      dashboardActions: {
        workspaceId: 'ws-1',
        blockedWork: [],
        reviewRequired: [],
        safeToContinue: [],
        generatedAt: '2026-05-07T00:00:00.000Z',
      },
      dashboardActionsLoading: false,
      dashboardActionsError: null,
      isLoading: false,
    });

    renderRoute();

    expect(screen.getByText(/continue: review deployment blockers/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /open next step/i }));

    expect(mockNavigate).toHaveBeenCalledWith('/p/proj-2/run');
  });

  it('uses a safe lifecycle resume action when backend review fields are missing', async () => {
    const user = userEvent.setup();
    const malformedProject = ({
      ...buildProject({
        id: 'proj-missing-contract',
        name: 'Missing Contract Project',
      }),
      aiNextActions: undefined,
      lifecyclePhase: undefined,
      updatedAt: undefined,
    } as unknown) as ProjectWithOwnership;

    mockUseWorkspaceContext.mockReturnValue({
      ownedProjects: [malformedProject],
      includedProjects: [],
      workspaces: [workspace],
      currentWorkspace: workspace,
      dashboardActions: {
        workspaceId: 'ws-1',
        blockedWork: [],
        reviewRequired: [],
        safeToContinue: [],
        generatedAt: '2026-05-07T00:00:00.000Z',
      },
      dashboardActionsLoading: false,
      dashboardActionsError: null,
      isLoading: false,
    });

    renderRoute();

    expect(screen.getByText(/continue: resume intent phase/i)).toBeInTheDocument();
    expect(screen.getByText(/no backed blocker or review actions are reported/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /open next step/i }));

    expect(mockNavigate).toHaveBeenCalledWith('/p/proj-missing-contract/intent');
  });

  it('surfaces backed review actions in workspace health instead of implying all-clear status', () => {
    mockUseWorkspaceContext.mockReturnValue({
      ownedProjects: [
        buildProject({
          id: 'proj-review',
          name: 'Review Project',
          lifecyclePhase: 'RUN',
          aiNextActions: ['Review deployment blockers', 'Approve rollback plan'],
        }),
      ],
      includedProjects: [],
      workspaces: [workspace],
      currentWorkspace: workspace,
      dashboardActions: {
        workspaceId: 'ws-1',
        blockedWork: [],
        reviewRequired: [],
        safeToContinue: [],
        generatedAt: '2026-05-07T00:00:00.000Z',
      },
      dashboardActionsLoading: false,
      dashboardActionsError: null,
      isLoading: false,
    });

    renderRoute();

    expect(screen.getByText(/review project has 2 backed review action/i)).toBeInTheDocument();
  });

  it('renders dedicated backend blocker, review, and safe-to-continue cards', async () => {
    const user = userEvent.setup();
    mockUseWorkspaceContext.mockReturnValue({
      ownedProjects: [buildProject({ id: 'project-safe', lifecyclePhase: 'SHAPE' })],
      includedProjects: [],
      workspaces: [workspace],
      currentWorkspace: workspace,
      dashboardActions: {
        workspaceId: 'ws-1',
        blockedWork: [
          {
            id: 'blocked-1',
            projectId: 'project-blocked',
            projectName: 'Blocked Project',
            workspaceId: 'ws-1',
            lifecyclePhase: 'RUN',
            routePhase: 'run',
            kind: 'blocker',
            title: 'Resolve critical security blocker',
            summary: 'Open run cockpit for the backed project action.',
            severity: 'critical',
            source: 'project.aiNextActions',
            requiresReview: true,
            safeToRun: false,
            updatedAt: '2026-05-07T00:00:00.000Z',
          },
        ],
        reviewRequired: [
          {
            id: 'review-1',
            projectId: 'project-review',
            projectName: 'Review Project',
            workspaceId: 'ws-1',
            lifecyclePhase: 'GENERATE',
            routePhase: 'generate',
            kind: 'review',
            title: 'Review generated diff',
            summary: 'Open generate cockpit for the backed project action.',
            severity: 'warning',
            source: 'project.aiNextActions',
            requiresReview: true,
            safeToRun: false,
            updatedAt: '2026-05-07T00:00:00.000Z',
          },
        ],
        safeToContinue: [
          {
            id: 'safe-1',
            projectId: 'project-safe',
            projectName: 'Safe Project',
            workspaceId: 'ws-1',
            lifecyclePhase: 'SHAPE',
            routePhase: 'shape',
            kind: 'safe-to-continue',
            title: 'Resume shape phase',
            summary: 'Continue from shape.',
            severity: 'info',
            source: 'project.lifecyclePhase',
            requiresReview: false,
            safeToRun: true,
            updatedAt: '2026-05-07T00:00:00.000Z',
          },
        ],
        generatedAt: '2026-05-07T00:00:00.000Z',
      },
      dashboardActionsLoading: false,
      dashboardActionsError: null,
      isLoading: false,
    });

    renderRoute();

    expect(screen.getByRole('heading', { name: /blocked work/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /review required/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /safe to continue/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /do this first: resolve critical security blocker/i })).toBeInTheDocument();
    expect(screen.getByText(/1 blocked item · 1 review item · 1 safe continuation/i)).toBeInTheDocument();
    expect(screen.getAllByText(/resolve critical security blocker/i).length).toBeGreaterThan(1);
    expect(screen.getByText(/review generated diff/i)).toBeInTheDocument();
    expect(screen.getAllByText(/resume shape phase/i).length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: /open blocker/i }));

    expect(mockNavigate).toHaveBeenCalledWith('/p/project-blocked/run');
  });

  it('prioritizes review guidance when no blockers are reported', async () => {
    const user = userEvent.setup();
    mockUseWorkspaceContext.mockReturnValue({
      ownedProjects: [buildProject({ id: 'project-review', lifecyclePhase: 'GENERATE' })],
      includedProjects: [],
      workspaces: [workspace],
      currentWorkspace: workspace,
      dashboardActions: {
        workspaceId: 'ws-1',
        blockedWork: [],
        reviewRequired: [
          {
            id: 'review-1',
            projectId: 'project-review',
            projectName: 'Review Project',
            workspaceId: 'ws-1',
            lifecyclePhase: 'GENERATE',
            routePhase: 'generate',
            kind: 'review',
            title: 'Review generated diff',
            summary: 'Open generate cockpit for the backed project action.',
            severity: 'warning',
            source: 'project.aiNextActions',
            requiresReview: true,
            safeToRun: false,
            updatedAt: '2026-05-07T00:00:00.000Z',
          },
        ],
        safeToContinue: [
          {
            id: 'safe-1',
            projectId: 'project-safe',
            projectName: 'Safe Project',
            workspaceId: 'ws-1',
            lifecyclePhase: 'SHAPE',
            routePhase: 'shape',
            kind: 'safe-to-continue',
            title: 'Resume shape phase',
            summary: 'Continue from shape.',
            severity: 'info',
            source: 'project.lifecyclePhase',
            requiresReview: false,
            safeToRun: true,
            updatedAt: '2026-05-07T00:00:00.000Z',
          },
        ],
        generatedAt: '2026-05-07T00:00:00.000Z',
      },
      dashboardActionsLoading: false,
      dashboardActionsError: null,
      isLoading: false,
    });

    renderRoute();

    expect(screen.getByRole('heading', { name: /review next: review generated diff/i })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /open review/i }));

    expect(mockNavigate).toHaveBeenCalledWith('/p/project-review/generate');
  });

  it('executes safe dashboard actions through the backend contract before navigation', async () => {
    const user = userEvent.setup();
    mockUseWorkspaceContext.mockReturnValue({
      ownedProjects: [buildProject({ id: 'project-safe', lifecyclePhase: 'SHAPE' })],
      includedProjects: [],
      workspaces: [workspace],
      currentWorkspace: workspace,
      dashboardActions: {
        workspaceId: 'ws-1',
        blockedWork: [],
        reviewRequired: [],
        safeToContinue: [
          {
            id: 'safe-1',
            projectId: 'project-safe',
            projectName: 'Safe Project',
            workspaceId: 'ws-1',
            lifecyclePhase: 'SHAPE',
            routePhase: 'shape',
            kind: 'safe-to-continue',
            title: 'Resume shape phase',
            summary: 'Continue from shape.',
            severity: 'info',
            source: 'project.lifecyclePhase',
            requiresReview: false,
            safeToRun: true,
            updatedAt: '2026-05-07T00:00:00.000Z',
          },
        ],
        generatedAt: '2026-05-07T00:00:00.000Z',
      },
      dashboardActionsLoading: false,
      dashboardActionsError: null,
      isLoading: false,
    });

    renderRoute();

    await user.click(screen.getByRole('button', { name: /resume shape phase/i }));

    await waitFor(() => {
      expect(mockExecuteDashboardAction).toHaveBeenCalledWith('project-safe', {
        workspaceId: 'ws-1',
        actionId: 'safe-1',
      });
      expect(mockNavigate).toHaveBeenCalledWith('/p/project-safe/shape');
    });
  });
});

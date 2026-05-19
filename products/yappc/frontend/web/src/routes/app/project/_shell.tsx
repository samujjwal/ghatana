/**
 * Project Shell Layout
 *
 * Simplified project layout with canonical phase tabs only.
 * Compatibility-only routes such as canvas, preview, deploy, and lifecycle are
 * retained for bookmarks but are not exposed as first-class project navigation.
 * Includes IntentDrawer for INTENT phase artifact creation.
 *
 * UI/UX Improvements:
 * - Removed duplicate LifecyclePhaseNavigator (now in CanvasStatusBar only)
 * - Consolidated header with inline phase badge
 * - Cleaner 2-row layout: Header + Tabs
 * - IntentDrawer for URL-driven artifact creation (?drawer=idea|research|problem)
 *
 * @doc.type component
 * @doc.purpose Application project shell layout
 * @doc.layer product
 * @doc.pattern Layout Component
 */

import { useCallback, useMemo, useEffect } from 'react';
import { NavLink, useParams, Outlet, useNavigate, useLocation } from 'react-router';
import { useAtomValue, useSetAtom } from 'jotai';
import { currentUserAtom } from '../../../stores/user.store';
import type { ProjectShellContract } from '@/contracts/workspace-project';
import {
  headerVisibleAtom,
  headerActionContextAtom,
  headerContextActionsAtom,
  headerPhaseInfoAtom
} from '../../../state/atoms/layoutAtom';
import { Share2 as Share, Settings as SettingsIcon, Download as FileDownload, Paintbrush as Brush, Eye as Visibility, Rocket as RocketLaunch, Activity as LifecycleIcon, LayoutDashboard, Lightbulb, Shapes, CheckCircle, Zap, Play, BarChart2, BookOpen, TrendingUp } from 'lucide-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';

import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';
import { IntentDrawer } from '../../../components/intent';
import { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';
import { yappcApi } from '@/lib/api';
import { useLifecycleArtifacts } from '../../../services/canvas/lifecycle/LifecycleArtifactService';
import { useLastOpenedProject } from '../../../hooks/useLastOpenedProject';
import { useWorkspaceContext } from '../../../hooks/useWorkspaceData';
import { UnifiedContextHeader } from '../../../components/navigation';
import { usePhaseFeatureGate } from '../../../hooks/usePhaseFeatureGate';

/**
 * 8-phase IA navigation — the only top-level project navigation.
 * Admin / dev / ops views surface as context-sensitive panels
 * inside the Run, Observe, Learn, and Evolve phases.
 */
const BASE_PROJECT_TABS = [
  {
    key: 'intent',
    label: 'Intent',
    icon: Lightbulb,
    tooltip: 'Capture goals, problems, and ideas — the "why" of this project',
  },
  {
    key: 'shape',
    label: 'Shape',
    icon: Shapes,
    tooltip: 'Define requirements, user stories, and design the solution',
  },
  {
    key: 'validate',
    label: 'Validate',
    icon: CheckCircle,
    tooltip: 'Review, approve, and gate requirements before generation',
  },
  {
    key: 'generate',
    label: 'Generate',
    icon: Zap,
    tooltip: 'Guided code, test, and artifact generation',
  },
  {
    key: 'run',
    label: 'Run',
    icon: Play,
    tooltip: 'Execute pipelines, deployments, and agent workflows',
  },
  {
    key: 'observe',
    label: 'Observe',
    icon: BarChart2,
    tooltip: 'Metrics, incidents, alerts, and live dashboards',
  },
  {
    key: 'learn',
    label: 'Learn',
    icon: BookOpen,
    tooltip: 'Retrospectives, recommended insights, and knowledge capture',
  },
  {
    key: 'evolve',
    label: 'Evolve',
    icon: TrendingUp,
    tooltip: 'Plan the next cycle: refine, promote, or retire',
  },
] as const;

/**
 * Project shell layout with minimal navigation
 */
export function Layout() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const basePath = `/p/${projectId}`;
  const isHeaderVisible = useAtomValue(headerVisibleAtom);
  const aiAssistEnabled = import.meta.env.VITE_FEATURE_AI_ASSIST === 'true';
  const { isPhaseEnabled } = usePhaseFeatureGate();

  const { setLastOpenedProject } = useLastOpenedProject();
  const { currentWorkspace, workspaces, ownedProjects, includedProjects } = useWorkspaceContext();

  // Fetch project data - MANDATORY scoped fetch only
  const { data: project, isLoading, error } = useQuery({
    queryKey: ['project', projectId, currentWorkspace?.id],
    queryFn: async () => {
      if (!projectId) {
        throw new Error('Project ID is required');
      }
      if (!currentWorkspace?.id) {
        throw new Error('Workspace context is required - project access must be scoped');
      }
      return yappcApi.projects.getScoped(projectId, currentWorkspace.id) as Promise<ProjectShellContract>;
    },
    enabled: !!projectId && !!currentWorkspace?.id,
    retry: false,
  });

  const currentUser = useAtomValue(currentUserAtom);
  const queryClient = useQueryClient();

  // SIMP-Y18: Eagerly prefetch data that child routes commonly need,
  // so navigating to any phase tab starts without a loading spinner.
  // All prefetches must use scoped access (TRACK-001, TRACK-002)
  useEffect(() => {
    if (!projectId || !currentWorkspace?.id) return;

    void queryClient.prefetchQuery({
      queryKey: ['project-artifacts', projectId, currentWorkspace.id],
      queryFn: () => yappcApi.projects.artifacts(projectId, currentWorkspace.id),
      staleTime: 60_000,
    });

    void queryClient.prefetchQuery({
      queryKey: ['project-sprint-current', projectId, currentWorkspace.id],
      queryFn: () => yappcApi.projects.sprintCurrent(projectId, currentWorkspace.id),
      staleTime: 60_000,
    });

    void queryClient.prefetchQuery({
      queryKey: ['project-backlog', projectId, currentWorkspace.id],
      queryFn: () => yappcApi.projects.backlog(projectId, currentWorkspace.id, 20),
      staleTime: 60_000,
    });

    void queryClient.prefetchQuery({
      queryKey: ['project-runs-recent', projectId, currentWorkspace.id],
      queryFn: () => yappcApi.projects.recentRuns(projectId, currentWorkspace.id, 10),
      staleTime: 60_000,
    });
  }, [projectId, currentWorkspace?.id, queryClient]);
  const currentUserProfile = currentUser as
    | { firstName?: string; lastName?: string; email?: string }
    | null;
  const user = currentUser
    ? {
        id: currentUser.id,
        name:
          `${currentUserProfile?.firstName ?? ''} ${currentUserProfile?.lastName ?? ''}`.trim() ||
          currentUser.id,
        email: currentUserProfile?.email ?? '',
        initials:
          (currentUserProfile?.firstName?.[0] ?? '') +
          (currentUserProfile?.lastName?.[0] ?? ''),
      }
    : { id: 'guest', name: 'Guest', email: '', initials: 'G' };

  // Prepare workspace info - TRACK-004: Use backend capability contract
  // All capability decisions come from backend, not frontend derivation
  const workspaceInfo = currentWorkspace ? {
    id: currentWorkspace.id,
    name: currentWorkspace.name,
    // Use backend-provided capabilities instead of frontend-derived ownership
    isOwner: project?.capabilities?.create ?? project?.isOwner ?? false,
  } : undefined;

  const workspacesList = workspaces.map(ws => ({
    id: ws.id,
    name: ws.name,
    isOwner: ws.id === project?.workspaceId, // Derive from project workspace context
  }));

  // Prepare project info
  const projectInfo = project ? {
    id: projectId || '',
    name: project.name,
    type: project.type,
  } : undefined;

  const projectsList = [...ownedProjects, ...includedProjects].map(p => ({
    id: p.id,
    name: p.name,
    // TRACK-004: Use backend-provided capabilities instead of frontend derivation
    isOwner: p.capabilities?.create ?? ownedProjects.some(op => op.id === p.id),
  }));

  // Define project-specific actions
  const shareEnabled = import.meta.env.VITE_FEATURE_PROJECT_SHARE === 'true';
  const exportEnabled =
    import.meta.env.VITE_FEATURE_PROJECT_EXPORT === 'true';

  const contextActions = [
    ...(shareEnabled
      ? [
          {
            id: 'share',
            label: 'Share',
            icon: Share,
            onClick: () => navigate(`${basePath}/share`),
            tooltip: 'Share project with team',
          },
        ]
      : []),
    {
      id: 'settings',
      label: 'Settings',
      icon: SettingsIcon,
      onClick: () => navigate(`${basePath}/settings`),
      tooltip: 'Project settings',
    },
    ...(exportEnabled
      ? [
          {
            id: 'export',
            label: 'Export',
            icon: FileDownload,
            onClick: () => {
              void (async () => {
                try {
                  if (!projectId || !currentWorkspace?.id) {
                    alert('Project and workspace context are required for export');
                    return;
                  }
                  const res = await yappcApi.projects.export(projectId, currentWorkspace.id);
                  
                  if (!res.ok) {
                    const errorData = await res.json().catch(() => ({ message: 'Unknown error' }));
                    const correlationId = res.headers.get('X-Correlation-ID') || 'unknown';
                    throw new Error(`Export authorization failed [Correlation ID: ${correlationId}]: ${errorData.message || res.statusText}`);
                  }
                  
                  const blob = await res.blob();
                  const url = URL.createObjectURL(blob);
                  const a = document.createElement('a');
                  a.href = url;
                  a.download = `project-${projectId}.zip`;
                  a.click();
                  URL.revokeObjectURL(url);
                } catch (err) {
                  const message = err instanceof Error ? err.message : 'Export failed';
                  // TRACK-003: User-visible error with correlation ID for audit trail
                  alert(`Export failed: ${message}. Please check your permissions and contact support if the issue persists.`);
                  console.error('[ProjectShell] Export failed:', err);
                }
              })();
            },
            tooltip: 'Export project',
            divider: true,
          },
        ]
      : []),
  ];

  // Track last opened project
  useEffect(() => {
    if (projectId && project?.workspaceId) {
      setLastOpenedProject(project.workspaceId, projectId);
    }
  }, [projectId, project?.workspaceId, setLastOpenedProject]);

  const setHeaderActionContext = useSetAtom(headerActionContextAtom);
  const setHeaderContextActions = useSetAtom(headerContextActionsAtom);
  const setHeaderPhaseInfo = useSetAtom(headerPhaseInfoAtom);

  // Update global header with project context
  useEffect(() => {
    if (project) {
      setHeaderActionContext('project');
      setHeaderContextActions(contextActions);
      setHeaderPhaseInfo({
        phase: project.currentPhase,
        label: project.currentPhase.charAt(0).toUpperCase() + project.currentPhase.slice(1),
        progress: project.phaseProgress || 0,
        status: 'active',
      });
    }

    return () => {
      setHeaderActionContext('global');
      setHeaderContextActions([]);
      setHeaderPhaseInfo(undefined);
    };
  }, [project, setHeaderActionContext, setHeaderContextActions, setHeaderPhaseInfo]);

  // Initialize lifecycle services before guard returns so hook order is stable
  // while workspace/project data loads or becomes available after selection.
  const { createArtifact, updateArtifact, artifacts } = useLifecycleArtifacts(
    projectId || ''
  );

  const handleIntentSave = useCallback(
    async (kind: LifecycleArtifactKind, data: unknown) => {
      if (!projectId) return { projectId: '' };

      const userId = currentUser?.id ?? 'anonymous';
      const existingArtifact = artifacts.find((a) => a.kind === kind);

      if (existingArtifact) {
        await updateArtifact(
          existingArtifact.id,
          { payload: data as Record<string, unknown> },
          userId
        );
      } else {
        await createArtifact(kind, userId);
      }

      return { projectId };
    },
    [projectId, currentUser?.id, artifacts, createArtifact, updateArtifact]
  );

  const handleAIAssist = useCallback(async (kind: LifecycleArtifactKind) => {
    if (!aiAssistEnabled || !projectId) {
      return null;
    }
    return yappcApi.ai.assist({ kind, projectId });
  }, [projectId, aiAssistEnabled]);

  const intentData = useMemo(() => {
    // Note: ArtifactSummary doesn't include payload, so this returns an empty object.
    // In production, fetch full artifacts with service.getArtifact(id).
    return {};
  }, []);

  // Handle missing scope or authorization errors
  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-full p-8 bg-bg-default">
        <div className="max-w-md text-center">
          <h2 className="text-2xl font-bold text-text-primary mb-4">Project Access Error</h2>
          <p className="text-text-secondary mb-6">
            {error instanceof Error ? error.message : 'Unable to load project. Project access must be scoped to a workspace.'}
          </p>
          <button
            onClick={() => navigate('/workspaces')}
            className="px-4 py-2 bg-info-color text-white rounded-md hover:bg-info-color/90 transition-colors"
          >
            Go to Workspaces
          </button>
        </div>
      </div>
    );
  }

  // Handle missing workspace context
  if (!currentWorkspace?.id && projectId) {
    return (
      <div className="flex flex-col items-center justify-center h-full p-8 bg-bg-default">
        <div className="max-w-md text-center">
          <h2 className="text-2xl font-bold text-text-primary mb-4">Workspace Context Required</h2>
          <p className="text-text-secondary mb-6">
            Project access must be scoped to a workspace. Please select a workspace to access this project.
          </p>
          <button
            onClick={() => navigate('/workspaces')}
            className="px-4 py-2 bg-info-color text-white rounded-md hover:bg-info-color/90 transition-colors"
          >
            Select Workspace
          </button>
        </div>
      </div>
    );
  }

  const projectName = project?.name || 'Loading...';
  const projectTabs = [...BASE_PROJECT_TABS].filter(tab => isPhaseEnabled(tab.key as any)) as (typeof BASE_PROJECT_TABS[number])[];

  return (
    <div className="flex flex-col h-full">
      {/* 8-phase IA navigation — the canonical top-level project nav */}
      <nav
        role="tablist"
        aria-label="Project phase navigation"
        className="flex gap-1 px-4 py-1.5 border-b border-divider bg-bg-default overflow-x-auto"
      >
        {projectTabs.map((tab) => (
          <NavLink
            key={tab.key}
            to={`${basePath}/${tab.key}`}
            role="tab"
            title={tab.tooltip}
            className={({ isActive }) =>
              [
                'flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium rounded-md transition-colors no-underline whitespace-nowrap',
                isActive
                  ? 'text-info-color bg-info-bg dark:bg-info-bg/20'
                  : 'text-text-secondary hover:text-text-primary hover:bg-surface-muted dark:hover:bg-surface-muted',
              ].join(' ')
            }
          >
            <tab.icon className="text-base" aria-hidden="true" />
            <span>{tab.label}</span>
          </NavLink>
        ))}
      </nav>

      {/* Route Content - Full height minus header and tabs */}
      <main className="flex-1 overflow-hidden bg-bg-default">
        <Outlet />
      </main>

      {/* IntentDrawer - URL-driven drawer for INTENT phase artifacts */}
      <IntentDrawer
        onSave={handleIntentSave}
        onAIAssist={aiAssistEnabled ? handleAIAssist : undefined}
        existingData={intentData}
      />
    </div>
  );
}

/**
 * Error boundary for project shell
 */
export function ErrorBoundary() {
  return (
    <RouteErrorBoundary
      title="Project Not Found"
      message="Unable to load this project. It may not exist or you may not have access."
    />
  );
}

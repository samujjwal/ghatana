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
import { parseJsonResourceResponse, parseJsonResponse } from '@/lib/http';
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
    tooltip: 'Guided code, test, and artefact generation',
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

  // Fetch project data
  const { data: project, isLoading } = useQuery({
    queryKey: ['project', projectId],
    queryFn: async () => {
      const response = await fetch(`/api/projects/${projectId}`);
      if (!response.ok) return null;
      return parseJsonResourceResponse<ProjectShellContract>(
        response,
        'project shell project query',
        'project'
      );
    },
    enabled: !!projectId,
  });

  const currentUser = useAtomValue(currentUserAtom);
  const queryClient = useQueryClient();

  // SIMP-Y18: Eagerly prefetch data that child routes commonly need,
  // so navigating to any phase tab starts without a loading spinner.
  useEffect(() => {
    if (!projectId) return;

    void queryClient.prefetchQuery({
      queryKey: ['project-artifacts', projectId],
      queryFn: () => fetch(`/api/projects/${projectId}/artifacts`).then((r) => r.json()),
      staleTime: 60_000,
    });

    void queryClient.prefetchQuery({
      queryKey: ['project-sprint-current', projectId],
      queryFn: () => fetch(`/api/projects/${projectId}/sprints/current`).then((r) => r.json()),
      staleTime: 60_000,
    });

    void queryClient.prefetchQuery({
      queryKey: ['project-backlog', projectId],
      queryFn: () => fetch(`/api/projects/${projectId}/backlog?limit=20`).then((r) => r.json()),
      staleTime: 60_000,
    });

    void queryClient.prefetchQuery({
      queryKey: ['project-runs-recent', projectId],
      queryFn: () => fetch(`/api/projects/${projectId}/runs?limit=10`).then((r) => r.json()),
      staleTime: 60_000,
    });
  }, [projectId, queryClient]);
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

  // Prepare workspace info
  const workspaceInfo = currentWorkspace ? {
    id: currentWorkspace.id,
    name: currentWorkspace.name,
    isOwner: true,
  } : undefined;

  const workspacesList = workspaces.map(ws => ({
    id: ws.id,
    name: ws.name,
    isOwner: true,
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
    isOwner: ownedProjects.some(op => op.id === p.id),
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
                  const res = await fetch(`/api/projects/${projectId}/export`);
                  if (!res.ok) throw new Error(`Export failed: ${res.status}`);
                  const blob = await res.blob();
                  const url = URL.createObjectURL(blob);
                  const a = document.createElement('a');
                  a.href = url;
                  a.download = `project-${projectId}.zip`;
                  a.click();
                  URL.revokeObjectURL(url);
                } catch (err) {
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

  const projectName = project?.name || 'Loading...';
  const projectTabs = [...BASE_PROJECT_TABS].filter(tab => isPhaseEnabled(tab.key as any)) as (typeof BASE_PROJECT_TABS[number])[];

  // Initialize lifecycle services
  const { createArtifact, updateArtifact, artifacts } = useLifecycleArtifacts(
    projectId || ''
  );

  // IntentDrawer handlers
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
    [projectId, artifacts, createArtifact, updateArtifact]
  );

  const handleAIAssist = useCallback(async (kind: LifecycleArtifactKind) => {
    if (!aiAssistEnabled) {
      return null;
    }
    const response = await fetch(`/api/ai/assist`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ kind, projectId }),
    });
    if (!response.ok) return null;
    return parseJsonResponse<Record<string, unknown>>(
      response,
      'project shell suggested assist'
    );
  }, [projectId, aiAssistEnabled]);

  // Load existing intent data from artifacts
  const intentData = useMemo(() => {
    // Note: ArtifactSummary doesn't include payload, so this returns an empty object
    // In production, you would fetch full artifacts with service.getArtifact(id)
    return {};
  }, []);

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
                  ? 'text-primary-600 bg-primary-50 dark:bg-primary-900/20'
                  : 'text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800',
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

/**
 * Project Shell Layout
 *
 * Simplified project layout with essential tabs only.
 * AI-first approach: canvas, preview, deploy, settings.
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
import {
  headerVisibleAtom,
  headerActionContextAtom,
  headerContextActionsAtom,
  headerPhaseInfoAtom
} from '../../../state/atoms/layoutAtom';
import { Share2 as Share, Settings as SettingsIcon, Download as FileDownload, Paintbrush as Brush, Boxes as Workspaces, Eye as Visibility, Rocket as RocketLaunch } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';

import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';
import { IntentDrawer } from '../../../components/intent';
import { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';
import { useLifecycleArtifacts } from '../../../services/canvas/lifecycle';
import { useLastOpenedProject } from '../../../hooks/useLastOpenedProject';
import { useWorkspaceContext } from '../../../hooks/useWorkspaceData';
import { UnifiedContextHeader } from '../../../components/navigation';

// Simplified tabs - only essential views
const projectTabs = [
  {
    key: 'canvas',
    label: 'Canvas',
    icon: Brush,
    tooltip: 'Unified canvas with all Epic 1-10 features',
  },
  {
    key: 'canvas-workspace',
    label: 'Workspace',
    icon: Workspaces,
    tooltip: 'Production workspace with lifecycle integration',
  },
  {
    key: 'preview',
    label: 'Preview',
    icon: Visibility,
    tooltip: 'Preview your application',
  },
  {
    key: 'deploy',
    label: 'Deploy',
    icon: RocketLaunch,
    tooltip: 'Deploy to production',
  },
  {
    key: 'settings',
    label: 'Settings',
    icon: SettingsIcon,
    tooltip: 'Project settings',
  },
];

/**
 * Project shell layout with minimal navigation
 */
export function Layout() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const basePath = `/app/p/${projectId}`;
  const isHeaderVisible = useAtomValue(headerVisibleAtom);
  const location = useLocation();
  const isCanvasView = location.pathname.endsWith('/canvas');

  const { setLastOpenedProject } = useLastOpenedProject();
  const { currentWorkspace, ownedWorkspaces, ownedProjects, includedProjects } = useWorkspaceContext();

  // Fetch project data
  const { data: project, isLoading } = useQuery({
    queryKey: ['project', projectId],
    queryFn: async () => {
      const response = await fetch(`/api/projects/${projectId}`);
      if (!response.ok) return null;
      return response.json();
    },
    enabled: !!projectId,
  });

  const currentUser = useAtomValue(currentUserAtom);
  const user = currentUser
    ? {
        id: currentUser.id,
        name: `${(currentUser as unknown).firstName ?? ''} ${(currentUser as unknown).lastName ?? ''}`.trim() || currentUser.id,
        email: (currentUser as unknown).email ?? '',
        initials: ((currentUser as unknown).firstName?.[0] ?? '') + ((currentUser as unknown).lastName?.[0] ?? ''),
      }
    : { id: 'guest', name: 'Guest', email: '', initials: 'G' };

  // Prepare workspace info
  const workspaceInfo = currentWorkspace ? {
    id: currentWorkspace.id,
    name: currentWorkspace.name,
    isOwner: true,
  } : undefined;

  const workspacesList = ownedWorkspaces.map(ws => ({
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
  const contextActions = [
    {
      id: 'share',
      label: 'Share',
      icon: Share,
      onClick: () => console.log('Share project'),
      tooltip: 'Share project with team',
    },
    {
      id: 'settings',
      label: 'Settings',
      icon: SettingsIcon,
      onClick: () => navigate(`${basePath}/settings`),
      tooltip: 'Project settings',
    },
    {
      id: 'export',
      label: 'Export',
      icon: FileDownload,
      onClick: () => console.log('Export project'),
      tooltip: 'Export project',
      divider: true,
    },
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
    const aiAssistEnabled = import.meta.env.VITE_FEATURE_AI_ASSIST === 'true';
    if (!aiAssistEnabled) {
      return null;
    }
    const response = await fetch(`/api/ai/assist`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ kind, projectId }),
    });
    if (!response.ok) return null;
    return response.json();
  }, [projectId]);

  // Load existing intent data from artifacts
  const intentData = useMemo(() => {
    // Note: ArtifactSummary doesn't include payload, so this returns an empty object
    // In production, you would fetch full artifacts with service.getArtifact(id)
    return {};
  }, []);

  return (
    <div className="flex flex-col h-full">
      {/* Navigation Tabs - Clean horizontal tab bar - Hidden on canvas to keep single bar */}
      {!isCanvasView && (
        <nav
          role="tablist"
          aria-label="Project navigation"
          className="flex gap-1 px-4 py-1.5 border-b border-divider bg-bg-default"
        >
          {projectTabs.map((tab) => (
            <NavLink
              key={tab.key}
              to={`${basePath}/${tab.key}`}
              role="tab"
              className={({ isActive }) =>
                [
                  'flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium rounded-md transition-colors no-underline',
                  isActive
                    ? 'text-primary-600 bg-primary-50 dark:bg-primary-900/20'
                    : 'text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800',
                ].join(' ')
              }
            >
              <tab.icon className="text-base" />
              <span>{tab.label}</span>
            </NavLink>
          ))}
        </nav>
      )}

      {/* Route Content - Full height minus header and tabs */}
      <main className="flex-1 overflow-hidden bg-bg-default">
        <Outlet />
      </main>

      {/* IntentDrawer - URL-driven drawer for INTENT phase artifacts */}
      <IntentDrawer
        onSave={handleIntentSave}
        onAIAssist={handleAIAssist}
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

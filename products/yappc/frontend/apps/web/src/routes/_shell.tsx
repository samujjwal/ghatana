/**
 * App Shell Layout
 *
 * Modern application layout with unified header bar navigation.
 * Maximizes canvas space by eliminating sidebar and consolidating navigation into header.
 *
 * Features:
 * - UnifiedHeaderBar with breadcrumb navigation and quick actions
 * - Full-width canvas area (no sidebar)
 * - WorkflowContextProvider for unified context management
 * - GuidancePanel for contextual help
 * - CommandPalette for action discovery (Cmd+K)
 * - Responsive design (mobile/tablet/desktop)
 *
 * @doc.type component
 * @doc.purpose Application shell layout
 * @doc.layer product
 * @doc.pattern Layout Component
 */

import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams, Outlet } from 'react-router';
import { useSetAtom, useAtomValue } from 'jotai';
import { useIsDarkMode, useThemeToggle } from '@ghatana/theme';
import {
  headerVisibleAtom,
  headerContextActionsAtom,
  headerActionContextAtom,
  headerPhaseInfoAtom,
  headerRoleInfoAtom,
  headerCanvasModeAtom,
  headerShowCanvasModeAtom,
  headerOnCanvasModeChangeAtom,
  headerNotificationCountAtom,
  headerShowAgentActivityAtom,
} from '../state/atoms/layoutAtom';

import { RouteErrorBoundary } from '../components/route/ErrorBoundary';
import {
  setWorkspaceBreadcrumbAtom,
  setProjectBreadcrumbAtom,
  setSectionBreadcrumbAtom,
} from '../state/atoms/breadcrumbAtom';
import { useWorkspaceContext } from '../hooks/useWorkspaceData';
import { RouteLoadingSpinner } from '../components/route/LoadingSpinner';
import { useActionState } from '../hooks/useActionState';
import { useKeyboardNavigation } from '../hooks/useKeyboardNavigation';
import { WorkflowContextProvider } from '../context/WorkflowContextProvider';
import { PersonaProvider } from '../context/PersonaContext';
import { GuidancePanel } from '../components/guidance';
import { CommandPalette } from '../components/command/CommandPalette';
import { CreateWorkspaceDialog } from '../components/workspace';
import { UnifiedContextHeader } from '../components/navigation';
import {
  KeyboardShortcutsPanel,
  useKeyboardShortcutsPanel,
} from '../components/help/KeyboardShortcutsPanel';
import { SkipLink } from '../components/accessibility';
import { useCurrentUser } from '../providers/AuthProvider';

/**
 * App Shell Component
 */
export function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();
  const [showGuidance, setShowGuidance] = useState(false);
  const [showCreateWorkspace, setShowCreateWorkspace] = useState(false);

  const {
    workspaces,
    currentWorkspace,
    ownedProjects,
    includedProjects,
    isLoading,
    error,
    refetch,
  } = useWorkspaceContext();

  const setWorkspaceBreadcrumb = useSetAtom(setWorkspaceBreadcrumbAtom);
  const setProjectBreadcrumb = useSetAtom(setProjectBreadcrumbAtom);
  const setSectionBreadcrumb = useSetAtom(setSectionBreadcrumbAtom);

  const allProjects = [...ownedProjects, ...includedProjects];

  // Check if user needs onboarding
  useEffect(() => {
    const isOnboarded = localStorage.getItem('onboarding_complete') === 'true';

    // Debug logging
    if (import.meta.env.DEV) {
      console.log('[Layout] Onboarding check:', {
        isOnboarded,
        isLoading,
        workspacesLength: workspaces.length,
        hasCurrentWorkspace: !!currentWorkspace,
        error: error?.message,
        storedKeys: {
          onboardingComplete: localStorage.getItem('onboarding_complete'),
          currentWorkspaceId: localStorage.getItem('yappc:currentWorkspaceId'),
        },
      });
    }

    // Redirect to onboarding if not completed — controlled by feature flag
    const onboardingEnabled = import.meta.env.VITE_FEATURE_ONBOARDING !== 'false';
    if (onboardingEnabled && !isOnboarded && !isLoading) {
      if (import.meta.env.DEV) {
        console.log('[Layout] Redirecting to onboarding (onboarding_complete is false)');
      }
      navigate('/onboarding', { replace: true });
    }
  }, [isLoading, navigate, workspaces.length]);

  // Update breadcrumbs
  useEffect(() => {
    if (currentWorkspace) {
      setWorkspaceBreadcrumb({
        id: currentWorkspace.id,
        name: currentWorkspace.name,
      });
    }
  }, [currentWorkspace, setWorkspaceBreadcrumb]);

  useEffect(() => {
    const pathParts = location.pathname.split('/').filter(Boolean);
    const section = pathParts[pathParts.length - 1];
    if (section && section !== 'app' && section !== projectId) {
      setSectionBreadcrumb(section);
    } else {
      setSectionBreadcrumb(undefined);
    }
  }, [location.pathname, projectId, setSectionBreadcrumb]);

  useEffect(() => {
    if (projectId) {
      const currentProject = allProjects.find((p) => p.id === projectId);
      if (currentProject) {
        setProjectBreadcrumb({
          id: currentProject.id,
          name: currentProject.name,
          isOwned: ownedProjects.some((p) => p.id === projectId),
        });
      }
    } else {
      setProjectBreadcrumb(null);
    }
  }, [
    projectId,
    allProjects,
    ownedProjects,
    includedProjects,
    setProjectBreadcrumb,
  ]);

  // Loading state
  if (isLoading && workspaces.length === 0) {
    return (
      <div className="flex h-screen items-center justify-center bg-bg-default">
        <RouteLoadingSpinner />
      </div>
    );
  }

  // Error state
  if (error) {
    // If error is 401/403 or just failed to load, we allow the child routes to handle it
    // This enables "Not Logged In" states in dashboard.tsx
    return (
      <PersonaProvider>
        <WorkflowContextProvider>
          <div className="flex flex-col h-screen bg-bg-default">
            <main className="flex-1 overflow-auto">
              <Outlet context={{ isError: true }} />
            </main>
          </div>
        </WorkflowContextProvider>
      </PersonaProvider>
    );
  }

  return (
    <PersonaProvider>
      <WorkflowContextProvider>
        <ShellContent
          showGuidance={showGuidance}
          setShowGuidance={setShowGuidance}
          showCreateWorkspace={showCreateWorkspace}
          setShowCreateWorkspace={setShowCreateWorkspace}
          projectId={projectId}
        />
      </WorkflowContextProvider>
    </PersonaProvider>
  );
}

/**
 * Shell Content Component
 * Separated to use hooks that require WorkflowContextProvider
 */
function ShellContent({
  showGuidance,
  setShowGuidance,
  showCreateWorkspace,
  setShowCreateWorkspace,
  projectId,
}: {
  showGuidance: boolean;
  setShowGuidance: (show: boolean) => void;
  showCreateWorkspace: boolean;
  setShowCreateWorkspace: (show: boolean) => void;
  projectId?: string;
}) {
  const location = useLocation();
  const navigate = useNavigate();
  const isHeaderVisible = useAtomValue(headerVisibleAtom);
  const contextActions = useAtomValue(headerContextActionsAtom);
  const actionContext = useAtomValue(headerActionContextAtom);
  const phaseInfo = useAtomValue(headerPhaseInfoAtom);
  const roleInfo = useAtomValue(headerRoleInfoAtom);
  const canvasMode = useAtomValue(headerCanvasModeAtom);
  const showCanvasMode = useAtomValue(headerShowCanvasModeAtom);
  const onCanvasModeChange = useAtomValue(headerOnCanvasModeChangeAtom);
  const notificationCount = useAtomValue(headerNotificationCountAtom);
  const showAgentActivity = useAtomValue(headerShowAgentActivityAtom);

  const actionState = useActionState();
  const { isOpen: shortcutsOpen, close: closeShortcuts } =
    useKeyboardShortcutsPanel();
  const isDarkMode = useIsDarkMode();
  const toggleTheme = useThemeToggle();
  const { currentWorkspace, workspaces, ownedProjects, includedProjects } =
    useWorkspaceContext();

  // Determine current section from pathname
  const section = location.pathname.split('/').filter(Boolean).pop();

  // User data from auth context (initialized by AuthProvider)
  const user = useCurrentUser();

  // Build workspace and project info for unified header
  const currentWorkspaceInfo = currentWorkspace
    ? {
        id: currentWorkspace.id,
        name: currentWorkspace.name,
        isOwner: true,
      }
    : undefined;

  const allProjects = [...ownedProjects, ...includedProjects];
  const currentProject = projectId
    ? allProjects.find((p) => p.id === projectId)
    : undefined;
  const currentProjectInfo = currentProject
    ? {
        id: currentProject.id,
        name: currentProject.name,
      }
    : undefined;

  const workspacesList = workspaces.map((ws) => ({
    id: ws.id,
    name: ws.name,
    isOwner: true,
  }));

  const projectsList = allProjects.map((p) => ({
    id: p.id,
    name: p.name,
    workspaceId: currentWorkspace?.id || '',
    isOwner: ownedProjects.some((op) => op.id === p.id),
  }));

  // Set up global keyboard shortcuts
  useKeyboardNavigation({
    enableProjectSwitcher: true,
    enableWorkspaceSwitcher: true,
    enableNewProject: true,
    enableHome: true,
  });

  return (
    <div className="flex flex-col h-screen bg-bg-default">
      {/* Skip Link for keyboard accessibility */}
      <SkipLink targetId="main-content">Skip to main content</SkipLink>

      {/* Keyboard Shortcuts Panel */}
      <KeyboardShortcutsPanel open={shortcutsOpen} onClose={closeShortcuts} />

      {/* Unified Context Header - replaces UnifiedHeaderBar */}
      {isHeaderVisible && (
        <UnifiedContextHeader
          user={user}
          workspace={currentWorkspaceInfo}
          project={currentProjectInfo}
          section={section}
          workspaces={workspacesList}
          projects={projectsList}
          actionContext={actionContext}
          contextActions={contextActions}
          canvasMode={canvasMode}
          showCanvasMode={showCanvasMode}
          phaseInfo={phaseInfo}
          roleInfo={roleInfo}
          onCanvasModeChange={onCanvasModeChange}
          notificationCount={notificationCount}
          showAgentActivity={showAgentActivity}
          darkMode={isDarkMode}
          onNew={() => navigate('/app/new')}
          onSearch={() => {
            // Dispatch mod+k to open CommandPalette (which listens on window)
            const isMac = navigator.platform.toUpperCase().includes('MAC');
            window.dispatchEvent(
              new KeyboardEvent('keydown', {
                key: 'k',
                metaKey: isMac,
                ctrlKey: !isMac,
                bubbles: true,
                cancelable: true,
              })
            );
          }}
          onNotifications={() => navigate('/notifications')}
          onHelp={() => window.open('/docs', '_blank')}
          onKeyboardShortcuts={() => {
            /* Open shortcuts panel */
          }}
          onThemeToggle={toggleTheme}
          onProfile={() => navigate('/profile')}
          onLogout={() => navigate('/logout')}
          onCreateWorkspace={() => setShowCreateWorkspace(true)}
          onCreateProject={() => navigate('/app/new')}
          onCreateWorkflow={() => navigate('/app/workflows/new')}
        />
      )}

      {/* Main Content Area - full width without sidebar */}
      <div className="flex flex-1 overflow-visible">
        {/* Guidance Panel (Collapsible Left Panel) - optional */}
        {showGuidance && projectId && (
          <GuidancePanel
            position="left"
            onToggle={(collapsed) => setShowGuidance(!collapsed)}
          />
        )}

        {/* Main Content */}
        <main
          id="main-content"
          role="main"
          aria-label="Main content area"
          className="flex-1 overflow-auto bg-bg-default"
          tabIndex={-1}
        >
          <Outlet />
        </main>
      </div>

      {/* Command Palette - Global action discovery (Cmd+K) */}
      <CommandPalette state={actionState} triggerKey="mod+k" />

      {/* Create Workspace Dialog */}
      <CreateWorkspaceDialog
        isOpen={showCreateWorkspace}
        onClose={() => setShowCreateWorkspace(false)}
      />
    </div>
  );
}

/**
 * Error Boundary
 */
export function ErrorBoundary() {
  return (
    <RouteErrorBoundary
      title="App Layout Error"
      message="There was an error loading the application layout."
    />
  );
}

// Default export for React Router
export default Layout;

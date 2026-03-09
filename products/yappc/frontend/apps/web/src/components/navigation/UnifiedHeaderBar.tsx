/**
 * Unified Header Bar
 * 
 * Modern header bar that combines breadcrumb navigation, quick actions, and user controls.
 * Replaces the sidebar to maximize canvas space while maintaining full navigation functionality.
 * 
 * Features:
 * - Enhanced breadcrumb with dropdown navigation
 * - Quick create actions (New Project, Workflow, Workspace)
 * - User controls (notifications, avatar, theme toggle)
 * - Responsive design (mobile/tablet/desktop)
 * - Agent activity badge
 * - Keyboard shortcuts support
 * 
 * @doc.type component
 * @doc.purpose Unified application header
 * @doc.layer product
 * @doc.pattern Layout Component
 */

import { Link } from 'react-router';
import { Sparkles as AutoAwesome, Bell as Notifications } from 'lucide-react';

import { Z_INDEX } from '../../styles/design-tokens';
import { EnhancedBreadcrumb } from './EnhancedBreadcrumb';
import { NewButton, QuickActionsPanel } from './QuickActionsPanel';

import { AgentActivityBadge } from '../workspace/AgentActivityBadge';
import { ThemeToggleButton } from '../../theme';
import { useWorkspaceContext } from '../../hooks/useWorkspaceData';

interface UnifiedHeaderBarProps {
    /** Current project ID (for project-specific context) */
    projectId?: string;
    /** Current section/page name */
    section?: string;
    /** Callback when creating new workspace */
    onCreateWorkspace?: () => void;
    /** Callback when creating new project */
    onCreateProject?: () => void;
    /** Callback when creating new workflow */
    onCreateWorkflow?: () => void;
    /** Show agent activity badge */
    showAgentActivity?: boolean;
    /** Additional CSS classes */
    className?: string;
}

/**
 * Unified Header Bar Component
 */
export function UnifiedHeaderBar({
    projectId,
    section,
    onCreateWorkspace,
    onCreateProject,
    onCreateWorkflow,
    showAgentActivity = true,
    className = '',
}: UnifiedHeaderBarProps) {
    const {
        currentWorkspace,
        ownedProjects,
        includedProjects,
        workspaces,
    } = useWorkspaceContext();

    // Find current project details
    const allProjects = [...ownedProjects, ...includedProjects];
    const currentProject = projectId ? allProjects.find(p => p.id === projectId) : undefined;

    // Build workspace object for breadcrumb
    const workspaceForBreadcrumb = currentWorkspace ? {
        id: currentWorkspace.id,
        name: currentWorkspace.name,
        isOwner: true, // NOTE: Get from workspace permissions
    } : undefined;

    // Build project object for breadcrumb
    const projectForBreadcrumb = currentProject ? {
        id: currentProject.id,
        name: currentProject.name,
        type: currentProject.type || 'app',
        isOwner: ownedProjects.some(p => p.id === currentProject.id),
    } : undefined;

    // Build workspaces list for dropdown
    const workspacesForBreadcrumb = workspaces.map(ws => ({
        id: ws.id,
        name: ws.name,
        description: ws.description,
        isOwner: true, // NOTE: Get from workspace permissions
        projectCount: 0, // NOTE: Calculate project count
    }));

    // Build projects list for dropdown with last opened tracking
    const projectsForBreadcrumb = allProjects.map(p => {
        // Get last opened from localStorage
        const lastOpenedData = localStorage.getItem('yappc_last_opened_projects');
        const lastOpenedMap = lastOpenedData ? JSON.parse(lastOpenedData) : {};
        const lastOpened = lastOpenedMap[currentWorkspace?.id || '']?.[p.id]?.timestamp;

        return {
            id: p.id,
            name: p.name,
            workspaceId: currentWorkspace?.id || '',
            type: p.type,
            lastOpened,
        };
    });

    return (
        <header
            className={`
                flex items-center justify-between gap-4 h-14
                bg-bg-paper border-b border-divider
                sticky top-0
                ${className}
            `}
            style={{ zIndex: Z_INDEX.header }}
            role="banner"
        >
            {/* Left: Logo + Enhanced Breadcrumb */}
            <div className="flex items-center gap-3 flex-1 min-w-0">
                {/* Logo */}
                <Link
                    to="/app"
                    className="flex items-center gap-2 px-3 py-2 no-underline hover:opacity-80 transition-opacity flex-shrink-0"
                    aria-label="YAPPC Home"
                >
                    <div className="w-6 h-6 rounded-md bg-gradient-to-br from-primary-500 to-purple-600 flex items-center justify-center">
                        <AutoAwesome className="w-3.5 h-3.5 text-white" />
                    </div>
                    <span className="text-sm font-bold text-text-primary hidden sm:inline">
                        YAPPC
                    </span>
                </Link>

                {/* Vertical Divider */}
                <div className="h-6 w-px bg-divider flex-shrink-0" />

                {/* Enhanced Breadcrumb with Project Selector */}
                <div className="flex-1 min-w-0">
                    <EnhancedBreadcrumb
                        workspace={workspaceForBreadcrumb}
                        project={projectForBreadcrumb}
                        section={section}
                        workspaces={workspacesForBreadcrumb}
                        projects={projectsForBreadcrumb}
                        showCreateActions
                        responsive
                        onCreateWorkspace={onCreateWorkspace}
                        onCreateProject={onCreateProject}
                        className="border-0 px-0 py-0"
                    />
                </div>
            </div>

            {/* Right: Actions + Controls */}
            <div className="flex items-center gap-2 flex-shrink-0 pr-3">
                {/* Agent Activity Badge (if enabled) */}
                {showAgentActivity && (
                    <AgentActivityBadge size="small" />
                )}



                {/* New Button */}
                <NewButton
                    onCreateProject={onCreateProject}
                    onCreateWorkflow={onCreateWorkflow}
                    onCreateWorkspace={onCreateWorkspace}
                    variant="default"
                />

                {/* Quick Actions Menu */}
                <QuickActionsPanel
                    projectId={projectId}
                    onCreateProject={onCreateProject}
                    onCreateWorkflow={onCreateWorkflow}
                    onCreateWorkspace={onCreateWorkspace}
                />

                {/* Vertical Divider */}
                <div className="h-6 w-px bg-divider" />

                {/* Notifications */}
                <button
                    className="p-2 rounded-md text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500 relative"
                    aria-label="Notifications"
                    title="Notifications"
                >
                    <Notifications className="w-5 h-5" />
                    {/* Notification badge (TODO: wire up to real notifications) */}
                    <span className="absolute top-1 right-1 w-2 h-2 bg-error-color rounded-full" />
                </button>

                {/* Theme Toggle */}
                <ThemeToggleButton variant="icon" />

                {/* User Avatar (TODO: wire up to real user data) */}
                <button
                    className="w-8 h-8 rounded-full bg-primary-100 dark:bg-primary-900/30 flex items-center justify-center text-sm font-medium text-primary-700 dark:text-primary-300 hover:ring-2 hover:ring-primary-500 transition-all focus:outline-none focus:ring-2 focus:ring-primary-500"
                    aria-label="User menu"
                    title="User menu"
                >
                    U
                </button>
            </div>
        </header>
    );
}

export default UnifiedHeaderBar;

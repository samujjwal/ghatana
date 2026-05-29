/**
 * App Route Index (Refactored)
 *
 * Home dashboard for mounted project and workspace entry points.
 * Smart container that manages authentication state and data fetching.
 *
 * @doc.type route
 * @doc.purpose Mounted home dashboard container
 * @doc.layer product
 * @doc.pattern Route Module
 */

import { useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router';
import { useSetAtom } from 'jotai';
import { useMutation } from '@tanstack/react-query';
import { Button } from '@ghatana/design-system';
import { ArrowRight, CheckCircle2, FolderOpen, Plus, RefreshCw, ShieldAlert } from 'lucide-react';

// Hooks
import { useWorkspaceContext } from '../hooks/useWorkspaceData';
import { useLastOpenedProject } from '../hooks/useLastOpenedProject';
import { useCurrentUser } from '../providers/AuthProvider';
import { headerVisibleAtom } from '../state/atoms/layoutAtom';
import { translate } from '../i18n/messages';
import { useDashboardDecision, type DashboardDecisionBrief as DashboardDecisionBriefType } from '../hooks/useDashboardDecision';
import { useDashboardActions } from '../hooks/useDashboardActions';

import { RouteErrorBoundary } from '../components/route/ErrorBoundary';

// Components
import { GuestLandingView } from '../components/dashboard/GuestLandingView';
import { EmptyStateView } from '../components/dashboard/EmptyStateView';
import { DashboardSkeleton } from '../components/dashboard/DashboardSkeleton';
import { NextActionDashboard } from '../components/dashboard/NextActionDashboard';
import { DashboardDecisionBrief } from '../components/dashboard/DashboardDecisionBrief';
import { DashboardActionStatusGrid } from '../components/dashboard/DashboardActionStatusGrid';

// Services
import ActionRegistry from '../services/ActionRegistry';
import type { ActionDefinition } from '../services/ActionRegistry';
import type { NextAction } from '../components/dashboard/NextActionDashboard';
import type { ProjectWithOwnership } from '../state/atoms/workspaceAtom';
import { getCanonicalPhaseLabel, normalizeToMountedPhase } from '../services/phase/CanonicalPhaseService';
import type { ProjectDashboardAction } from '../lib/api';
import { yappcApi } from '../lib/api';

function getProjectResumePath(project: ProjectWithOwnership): string {
    const phaseRoute = normalizeToMountedPhase(project.lifecyclePhase ?? 'INTENT');
    return `/p/${project.id}/${phaseRoute}`;
}

function getProjectUpdatedAt(project: ProjectWithOwnership): string {
    if (typeof project.updatedAt === 'string' && !Number.isNaN(Date.parse(project.updatedAt))) {
        return project.updatedAt;
    }
    if (typeof project.createdAt === 'string' && !Number.isNaN(Date.parse(project.createdAt))) {
        return project.createdAt;
    }
    return new Date(0).toISOString();
}

// TRACK-011: Mark client-derived actions as degraded/fallback
// TRACK-012: Remove empty-action fallback that masks backend failure
// Distinguish between loaded-and-empty and backend-unavailable
interface NextActionTitlesResult {
    readonly titles: readonly string[];
    readonly isDegraded: boolean;
    readonly isFallback: boolean;
}

function getProjectNextActionTitles(
    project: ProjectWithOwnership,
    backendAvailable: boolean,
): NextActionTitlesResult {
    if (!backendAvailable) {
        // Backend is unavailable - don't mask the failure
        return { titles: [], isDegraded: true, isFallback: false };
    }

    // Use the aiNextActions from the project data (populated by backend)
    // The backend now provides authoritative next actions via the /next-actions endpoint
    // For now, use the existing aiNextActions array which is already fetched
    if (Array.isArray(project.aiNextActions)) {
        const backedActions = project.aiNextActions
            .filter((action): action is string => typeof action === 'string' && action.trim().length > 0)
            .slice(0, 3);

        if (backedActions.length > 0) {
            return { titles: backedActions, isDegraded: false, isFallback: false };
        }
    }

    const phaseLabel = getCanonicalPhaseLabel(normalizeToMountedPhase(project.lifecyclePhase ?? 'INTENT')).toLowerCase();
    return { titles: [`Resume ${phaseLabel} phase`], isDegraded: false, isFallback: true };
}

// TRACK-013: Add dashboard degraded-state UX
// Show retry, reason, correlation ID when APIs fail
// DashboardDecisionBrief is now imported from useDashboardDecision hook

function pluralize(count: number, singular: string, plural = `${singular}s`): string {
    return `${count} ${count === 1 ? singular : plural}`;
}

/**
 * Home Dashboard Component
 * 
 * Orchestrates the view state based on data availability and authentication.
 */
export default function Component() {
    const navigate = useNavigate();
    const {
        ownedProjects,
        includedProjects,
        workspaces,
        currentWorkspace,
        dashboardActions,
        dashboardActionsLoading,
        dashboardActionsError,
        isLoading: workspaceLoading,
        refetch,
    } = useWorkspaceContext();
    const { getLastOpenedProject, setLastOpenedProject } = useLastOpenedProject();
    const setHeaderVisible = useSetAtom(headerVisibleAtom);

    const allProjects = [...ownedProjects, ...includedProjects];
    const isLoading = workspaceLoading;

    // Derive guest/empty states from real auth and workspace data
    const currentUser = useCurrentUser();
    const isGuest = !currentUser.isAuthenticated;
    const isEmpty = !isLoading && !isGuest && allProjects.length === 0;
    const recentProjects = useMemo(
        () => [...allProjects].sort((left, right) => getProjectUpdatedAt(right).localeCompare(getProjectUpdatedAt(left))).slice(0, 3),
        [allProjects]
    );

    // -----------------------------------------------------------------------
    // Next-best-action derivation (P2-003)
    // Derives ranked actions from the most recent project's lifecyclePhase and
    // AI-computed aiNextActions without requiring WorkflowContextProvider.
    // The top action is also registered in ActionRegistry so it surfaces
    // consistently in the CommandPalette (Cmd+K).
    // -----------------------------------------------------------------------
    const mostRecentProject = recentProjects[0];
    const rawBlockedWork = dashboardActions?.blockedWork ?? [];
    const rawReviewRequired = dashboardActions?.reviewRequired ?? [];
    const rawSafeToContinue = dashboardActions?.safeToContinue ?? [];
    const hasAnyDashboardAction = rawBlockedWork.length > 0 || rawReviewRequired.length > 0 || rawSafeToContinue.length > 0;
    const blockedWork = rawBlockedWork.filter((action) => !action.isDegraded && !action.isFallback);
    const reviewRequired = rawReviewRequired.filter((action) => !action.isDegraded && !action.isFallback);
    const backendSafeToContinue = rawSafeToContinue.filter((action) => !action.isDegraded && !action.isFallback);
    const safeToContinue = backendSafeToContinue;
    // TRACK-011: Use backend dashboard actions as authoritative source
    // Mark as degraded when backend is unavailable
    const dashboardDecisionBrief = useDashboardDecision(
        blockedWork,
        reviewRequired,
        safeToContinue,
        dashboardActionsLoading === true,
        dashboardActionsError,
    );
    const dashboardDecisionAction = dashboardDecisionBrief.action;
    const { openDashboardAction, isExecuting } = useDashboardActions(currentWorkspace?.id ?? null);

    const dashboardNextActions = useMemo<readonly NextAction[]>(() => {
        if (!mostRecentProject) {
            return [];
        }

        if (hasAnyDashboardAction) {
            return [];
        }

        // Backend is available if not loading and no error
        const backendAvailable = !dashboardActionsLoading && !dashboardActionsError;
        const { titles, isDegraded } = getProjectNextActionTitles(mostRecentProject, backendAvailable);

        return titles.map((title: string, index: number): NextAction => ({
            id: `dashboard-next-action-${index}`,
            title,
            description: `${title} · ${mostRecentProject.name}${isDegraded ? ' (degraded)' : ''}`,
            priority: index === 0 ? 'primary' : index === 1 ? 'secondary' : 'tertiary',
            action: () => {
                navigate(getProjectResumePath(mostRecentProject));
            },
        }));
    }, [mostRecentProject, navigate, dashboardActionsLoading, dashboardActionsError, hasAnyDashboardAction]);

    // Register the top dashboard action in ActionRegistry so it appears in
    // the CommandPalette under the 'ai' category at maximum priority.
    useEffect(() => {
        if (dashboardNextActions.length === 0 || !mostRecentProject) {
            return;
        }

        const top = dashboardNextActions[0];
        if (!top) {
            return;
        }

        const entry: ActionDefinition = {
            id: 'next-best-action-top',
            label: top.title,
            description: top.description,
            icon: 'TrendingUp',
            category: 'ai',
            priority: 9999,
            context: {},
            handler: () => {
                top.action();
            },
        };

        ActionRegistry.register(entry);
        return () => {
            ActionRegistry.unregister('next-best-action-top');
        };
    }, [dashboardNextActions, mostRecentProject]);

    // Control header visibility based on auth state
    useEffect(() => {
        setHeaderVisible(!isGuest);
        return () => setHeaderVisible(true);
    }, [isGuest, setHeaderVisible]);

    const handleProjectClick = (projectId: string) => {
        const project = allProjects.find((candidate) => candidate.id === projectId);
        if (!project) {
            return;
        }

        const workspaceId = project.ownerWorkspaceId || workspaces[0]?.id;
        if (workspaceId) {
            setLastOpenedProject(workspaceId, projectId);
        }
        navigate(getProjectResumePath(project));
    };

    const handleWorkspaceClick = (workspaceId: string) => {
        // Try to get last opened project for this workspace
        const lastProjectId = getLastOpenedProject(workspaceId);

        if (lastProjectId) {
            const project = allProjects.find(p => p.id === lastProjectId);
            if (project) {
                navigate(`/p/${lastProjectId}`);
                return;
            }
        }

        // Fallback: open first project in workspace
        const workspaceProjects = currentWorkspace?.id === workspaceId
            ? allProjects
            : allProjects.filter(p => p.ownerWorkspaceId === workspaceId);
        if (workspaceProjects.length > 0) {
            const firstProject = workspaceProjects[0];
            setLastOpenedProject(workspaceId, firstProject.id);
            navigate(getProjectResumePath(firstProject));
        } else {
            navigate(`/projects`);
        }
    };

    // --- RENDER ---

    if (isLoading) {
        return <DashboardSkeleton />;
    }

    if (isGuest) {
        return (
            <GuestLandingView
                onDemoLogin={() => navigate('/login')}
            />
        );
    }

    if (isEmpty) {
        return (
            <EmptyStateView
                onCreateProject={() => navigate('/projects')}
                onSkip={() => navigate('/projects')}
            />
        );
    }

    return (
        <div className="h-full overflow-auto bg-bg-default">
            <section className="mx-auto flex w-full max-w-6xl flex-col gap-8 px-6 py-8">
                <div className="flex flex-col gap-3">
                    <p className="text-sm font-semibold uppercase tracking-[0.18em] text-brand">
                        {translate('dashboard.workspaceHome')}
                    </p>
                    <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                        <div>
                            <h1 className="text-3xl font-semibold text-fg">{translate('dashboard.resumeWorkTitle')}</h1>
                            <p className="max-w-2xl text-sm text-fg-muted">
                                {translate('dashboard.resumeWorkDescription')}
                            </p>
                        </div>
                        <Button
                            type="button"
                            variant="solid"
                            onClick={() => navigate('/projects')}
                            className="inline-flex items-center gap-2 rounded-lg bg-brand px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand/90"
                        >
                            <Plus className="h-4 w-4" />
                            {translate('dashboard.createProject')}
                        </Button>
                    </div>
                </div>

                <div className="grid gap-4 md:grid-cols-3">
                        <Button
                            type="button"
                            variant="outline"
                            aria-label="Resume latest project"
                            onClick={() => {
                                const [latestProject] = recentProjects;
                                if (latestProject) {
                                handleProjectClick(latestProject.id);
                                return;
                            }
                            navigate('/projects');
                        }}
                        className="rounded-2xl border border-divider bg-bg-paper p-5 text-left shadow-sm transition-transform hover:-translate-y-0.5"
                    >
                        <div className="mb-4 inline-flex rounded-full bg-brand/10 p-2 text-brand dark:bg-brand/20 dark:text-brand/80">
                            <FolderOpen className="h-5 w-5" />
                        </div>
                        <h2 className="text-lg font-semibold text-fg">{translate('dashboard.resumeProject')}</h2>
                        <p className="mt-2 text-sm text-fg-muted">
                            {recentProjects[0]
                                ? `${translate('dashboard.openProjectInCockpit')} ${recentProjects[0].name} ${translate('dashboard.inItsProjectCockpit')}`
                                : translate('dashboard.resumeProjectDescription')}
                        </p>
                    </Button>

                    <Button
                        type="button"
                        variant="outline"
                        aria-label="Create new project"
                        onClick={() => navigate('/projects')}
                        className="rounded-2xl border border-divider bg-bg-paper p-5 text-left shadow-sm transition-transform hover:-translate-y-0.5"
                    >
                        <div className="mb-4 inline-flex rounded-full bg-success/10 p-2 text-success dark:bg-success/20 dark:text-success/80">
                            <Plus className="h-5 w-5" />
                        </div>
                        <h2 className="text-lg font-semibold text-fg">{translate('dashboard.createProject')}</h2>
                        <p className="mt-2 text-sm text-fg-muted">
                            {translate('dashboard.createProjectDescription')}
                        </p>
                    </Button>

                    <div className="rounded-2xl border border-divider bg-bg-paper p-5 shadow-sm">
                        <div className="mb-3 flex items-center gap-2">
                            <div className="inline-flex rounded-full bg-warning/10 p-2 text-warning dark:bg-warning/20 dark:text-warning/80">
                                <ShieldAlert className="h-5 w-5" />
                            </div>
                            <h2 className="text-lg font-semibold text-fg">{translate('dashboard.workspaceHealth')}</h2>
                        </div>
                        <div className="space-y-2">
                            {(() => {
                                const emptyWorkspaces = workspaces.filter((ws) => !allProjects.some((p) => p.ownerWorkspaceId === ws.id));
                                if (emptyWorkspaces.length > 0) {
                                    return emptyWorkspaces.slice(0, 2).map((ws) => (
                                        <p key={ws.id} className="text-sm text-warning dark:text-warning">
                                            {ws.name} {translate('dashboard.workspaceHasNoProjects')}
                                        </p>
                                    ));
                                }
                                if (mostRecentProject && !dashboardActionsLoading && !dashboardActionsError) {
                                    const aiActionCount = Array.isArray(mostRecentProject.aiNextActions)
                                        ? mostRecentProject.aiNextActions.filter((action) => typeof action === 'string' && action.trim().length > 0).length
                                        : 0;
                                    if (aiActionCount > 0) {
                                        return (
                                            <p className="text-sm text-warning dark:text-warning">
                                                {mostRecentProject.name} {translate('dashboard.hasReviewActions')}
                                            </p>
                                        );
                                    }
                                }
                                if (mostRecentProject) {
                                    return (
                                        <p className="text-sm text-fg-muted">
                                            {translate('dashboard.noBackedActions')}
                                        </p>
                                    );
                                }
                                return (
                                    <div className="flex items-center gap-2 text-sm text-success dark:text-success">
                                        <CheckCircle2 className="h-4 w-4" />
                                        <span>{translate('dashboard.allWorkspacesActive')}</span>
                                    </div>
                                );
                            })()}
                        </div>
                    </div>
                </div>

                {dashboardNextActions.length > 0 && dashboardNextActions[0] != null && (
                    <section aria-label="Recommended next action">
                        <NextActionDashboard
                            primaryAction={dashboardNextActions[0]}
                            secondaryAction={dashboardNextActions[1]}
                        />
                    </section>
                )}

                <DashboardDecisionBrief
                    headline={dashboardDecisionBrief.headline}
                    description={dashboardDecisionBrief.description}
                    action={dashboardDecisionBrief.action}
                    ctaLabel={dashboardDecisionBrief.ctaLabel}
                    isDegraded={dashboardDecisionBrief.isDegraded}
                    correlationId={dashboardDecisionBrief.correlationId}
                    retryAvailable={dashboardDecisionBrief.retryAvailable}
                    blockedCount={blockedWork.length}
                    reviewCount={reviewRequired.length}
                    safeCount={safeToContinue.length}
                    onActionClick={openDashboardAction}
                    onRetry={refetch}
                />

                <DashboardActionStatusGrid
                    blockedWork={blockedWork}
                    reviewRequired={reviewRequired}
                    safeToContinue={safeToContinue}
                    loading={dashboardActionsLoading === true}
                    error={dashboardActionsError}
                    onOpenProject={openDashboardAction}
                    onRetry={refetch}
                />

                <section className="rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm">
                    <div className="flex items-center justify-between gap-4">
                        <div>
                            <h2 className="text-xl font-semibold text-fg">{translate('dashboard.recentProjects')}</h2>
                            <p className="mt-1 text-sm text-fg-muted">
                                {translate('dashboard.recentProjectsDescription')}
                            </p>
                        </div>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => navigate('/projects')}
                            className="inline-flex items-center gap-2 text-sm font-medium text-brand transition-colors hover:text-brand/90"
                        >
                            {translate('dashboard.viewAllProjects')}
                            <ArrowRight className="h-4 w-4" />
                        </Button>
                    </div>

                    <div className="mt-6 grid gap-3">
                        {recentProjects.map((project) => (
                            <Button
                                key={project.id}
                                type="button"
                                variant="outline"
                                onClick={() => handleProjectClick(project.id)}
                                className="flex items-center justify-between rounded-xl border border-divider px-4 py-3 text-left transition-colors hover:bg-bg-default"
                            >
                                <div>
                                    <p className="font-medium text-fg">{project.name}</p>
                                    <p className="text-sm text-fg-muted">
                                        {project.description || translate('dashboard.noDescriptionYet')}
                                    </p>
                                </div>
                                <span className="text-xs uppercase tracking-[0.14em] text-fg-muted">
                                    {project.type}
                                </span>
                            </Button>
                        ))}
                    </div>
                </section>

                <section className="rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm">
                    <div className="flex items-center justify-between gap-4">
                        <div>
                            <h2 className="text-xl font-semibold text-fg">{translate('dashboard.workspaces')}</h2>
                            <p className="mt-1 text-sm text-fg-muted">
                                {translate('dashboard.workspacesDescription')}
                            </p>
                        </div>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => navigate('/workspaces')}
                            className="text-sm font-medium text-brand transition-colors hover:text-brand/90"
                        >
                            {translate('dashboard.manageWorkspaces')}
                        </Button>
                    </div>

                    <div className="mt-6 grid gap-3 md:grid-cols-2">
                        {workspaces.map((workspace) => (
                            <Button
                                key={workspace.id}
                                type="button"
                                variant="outline"
                                onClick={() => handleWorkspaceClick(workspace.id)}
                                className="rounded-xl border border-divider px-4 py-4 text-left transition-colors hover:bg-bg-default"
                            >
                                <p className="font-medium text-fg">{workspace.name}</p>
                                <p className="mt-1 text-sm text-fg-muted">
                                    {workspace.description || translate('dashboard.noDescriptionYet')}
                                </p>
                            </Button>
                        ))}
                    </div>
                </section>
            </section>
        </div>
    );
}

/**
 * Route Error Boundary
 */
export function ErrorBoundary() {
    return (
        <RouteErrorBoundary
            title="Dashboard Error"
            message="Something went wrong while loading your dashboard."
        />
    );
}

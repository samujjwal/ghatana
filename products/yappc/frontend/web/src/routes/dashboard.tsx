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
import { ArrowRight, CheckCircle2, FolderOpen, Plus, ShieldAlert } from 'lucide-react';

// Hooks
import { useWorkspaceContext } from '../hooks/useWorkspaceData';
import { useLastOpenedProject } from '../hooks/useLastOpenedProject';
import { useCurrentUser } from '../providers/AuthProvider';
import { headerVisibleAtom } from '../state/atoms/layoutAtom';

import { RouteErrorBoundary } from '../components/route/ErrorBoundary';

// Components
import { GuestLandingView } from '../components/dashboard/GuestLandingView';
import { EmptyStateView } from '../components/dashboard/EmptyStateView';
import { DashboardSkeleton } from '../components/dashboard/DashboardSkeleton';
import { NextActionDashboard } from '../components/dashboard/NextActionDashboard';

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

function getProjectNextActionTitles(project: ProjectWithOwnership): readonly string[] {
    if (Array.isArray(project.aiNextActions)) {
        const backedActions = project.aiNextActions
            .filter((action): action is string => typeof action === 'string' && action.trim().length > 0)
            .slice(0, 3);

        if (backedActions.length > 0) {
            return backedActions;
        }
    }

    return [`Resume ${getCanonicalPhaseLabel(project.lifecyclePhase ?? 'INTENT').toLowerCase()} phase`];
}

interface DashboardDecisionBrief {
    readonly headline: string;
    readonly description: string;
    readonly action: ProjectDashboardAction | null;
    readonly ctaLabel: string | null;
}

function pluralize(count: number, singular: string, plural = `${singular}s`): string {
    return `${count} ${count === 1 ? singular : plural}`;
}

function buildDashboardDecisionBrief(
    blockedWork: readonly ProjectDashboardAction[],
    reviewRequired: readonly ProjectDashboardAction[],
    safeToContinue: readonly ProjectDashboardAction[],
    loading: boolean,
    error: unknown,
): DashboardDecisionBrief {
    if (loading) {
        return {
            headline: 'Checking workspace action status',
            description: 'Loading backed blocker, review, and continuation actions before recommending the next step.',
            action: null,
            ctaLabel: null,
        };
    }

    if (error) {
        return {
            headline: 'Refresh backed action status',
            description: 'The dashboard could not load backend-classified action status, so avoid assuming the workspace is clear.',
            action: null,
            ctaLabel: null,
        };
    }

    const [firstBlocked] = blockedWork;
    if (firstBlocked) {
        return {
            headline: `Do this first: ${firstBlocked.title}`,
            description: `${firstBlocked.projectName} is blocked. Clear this before continuing lower-risk work.`,
            action: firstBlocked,
            ctaLabel: 'Open blocker',
        };
    }

    const [firstReview] = reviewRequired;
    if (firstReview) {
        return {
            headline: `Review next: ${firstReview.title}`,
            description: `${firstReview.projectName} needs operator review before the workspace can be treated as clear.`,
            action: firstReview,
            ctaLabel: 'Open review',
        };
    }

    const [firstSafeAction] = safeToContinue;
    if (firstSafeAction) {
        return {
            headline: `Safe to continue: ${firstSafeAction.title}`,
            description: `${firstSafeAction.projectName} has a backend-classified continuation action ready to execute.`,
            action: firstSafeAction,
            ctaLabel: 'Continue safely',
        };
    }

    return {
        headline: 'No backed action is waiting',
        description: 'No blocker, review, or safe continuation action is currently reported for this workspace.',
        action: null,
        ctaLabel: null,
    };
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
    const blockedWork = dashboardActions?.blockedWork ?? [];
    const reviewRequired = dashboardActions?.reviewRequired ?? [];
    const safeToContinue = dashboardActions?.safeToContinue ?? [];
    const dashboardDecisionBrief = buildDashboardDecisionBrief(
        blockedWork,
        reviewRequired,
        safeToContinue,
        dashboardActionsLoading === true,
        dashboardActionsError,
    );
    const executeDashboardAction = useMutation({
        mutationFn: async (action: ProjectDashboardAction) => {
            const workspaceId = currentWorkspace?.id ?? action.workspaceId;
            return yappcApi.projects.executeDashboardAction(action.projectId, {
                workspaceId,
                actionId: action.id,
            });
        },
        onSuccess: (result) => {
            navigate(result.targetPath);
        },
    });

    const openDashboardAction = (action: ProjectDashboardAction) => {
        if (action.safeToRun) {
            executeDashboardAction.mutate(action);
            return;
        }
        navigate(`/p/${action.projectId}/${action.routePhase}`);
    };

    const dashboardNextActions = useMemo<readonly NextAction[]>(() => {
        if (!mostRecentProject) {
            return [];
        }

        const actionTitles = getProjectNextActionTitles(mostRecentProject);

        return actionTitles.map((title, index): NextAction => ({
            id: `dashboard-next-action-${index}`,
            title,
            description: `${title} · ${mostRecentProject.name}`,
            priority: index === 0 ? 'primary' : index === 1 ? 'secondary' : 'tertiary',
            action: () => {
                navigate(getProjectResumePath(mostRecentProject));
            },
        }));
    }, [mostRecentProject, navigate]);

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
                    <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary-600">
                        Workspace Home
                    </p>
                    <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                        <div>
                            <h1 className="text-3xl font-semibold text-text-primary">Resume work without detours</h1>
                            <p className="max-w-2xl text-sm text-text-secondary">
                                The dashboard only exposes the next truthful actions: continue a project,
                                create one, or review blockers in your current workspace.
                            </p>
                        </div>
                        <Button
                            type="button"
                            variant="solid"
                            onClick={() => navigate('/projects')}
                            className="inline-flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-primary-700"
                        >
                            <Plus className="h-4 w-4" />
                            Create Project
                        </Button>
                    </div>
                </div>

                <div className="grid gap-4 md:grid-cols-3">
                    <Button
                        type="button"
                        variant="outline"
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
                        <div className="mb-4 inline-flex rounded-full bg-primary-50 p-2 text-primary-600 dark:bg-primary-900/30 dark:text-primary-300">
                            <FolderOpen className="h-5 w-5" />
                        </div>
                        <h2 className="text-lg font-semibold text-text-primary">Resume Project</h2>
                        <p className="mt-2 text-sm text-text-secondary">
                            {recentProjects[0]
                                ? `Open ${recentProjects[0].name} in its project cockpit.`
                                : 'Open a project cockpit and continue where you left off.'}
                        </p>
                    </Button>

                    <Button
                        type="button"
                        variant="outline"
                        onClick={() => navigate('/projects')}
                        className="rounded-2xl border border-divider bg-bg-paper p-5 text-left shadow-sm transition-transform hover:-translate-y-0.5"
                    >
                        <div className="mb-4 inline-flex rounded-full bg-emerald-50 p-2 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-300">
                            <Plus className="h-5 w-5" />
                        </div>
                        <h2 className="text-lg font-semibold text-text-primary">Create Project</h2>
                        <p className="mt-2 text-sm text-text-secondary">
                            Start a new product in the active workspace with a persisted, API-backed create flow.
                        </p>
                    </Button>

                    <div className="rounded-2xl border border-divider bg-bg-paper p-5 shadow-sm">
                        <div className="mb-3 flex items-center gap-2">
                            <div className="inline-flex rounded-full bg-warning-bg p-2 text-warning-color dark:bg-warning-bg/30 dark:text-warning-color">
                                <ShieldAlert className="h-5 w-5" />
                            </div>
                            <h2 className="text-lg font-semibold text-text-primary">Workspace Health</h2>
                        </div>
                        <div className="space-y-2">
                            {workspaces.filter((ws) => !allProjects.some((p) => p.ownerWorkspaceId === ws.id)).length > 0 ? (
                                workspaces
                                    .filter((ws) => !allProjects.some((p) => p.ownerWorkspaceId === ws.id))
                                    .slice(0, 2)
                                    .map((ws) => (
                                        <p key={ws.id} className="text-sm text-warning-color dark:text-warning-color">
                                            {ws.name} has no projects yet.
                                        </p>
                                    ))
                            ) : mostRecentProject && getProjectNextActionTitles(mostRecentProject).length > 0 && Array.isArray(mostRecentProject.aiNextActions) && mostRecentProject.aiNextActions.length > 0 ? (
                                <p className="text-sm text-warning-color dark:text-warning-color">
                                    {mostRecentProject.name} has {getProjectNextActionTitles(mostRecentProject).length} backed review action(s) to check.
                                </p>
                            ) : mostRecentProject ? (
                                <p className="text-sm text-text-secondary">
                                    No backed blocker or review actions are reported by the project API yet. Resume the lifecycle cockpit to refresh readiness.
                                </p>
                            ) : (
                                <div className="flex items-center gap-2 text-sm text-emerald-700 dark:text-emerald-300">
                                    <CheckCircle2 className="h-4 w-4" />
                                    <span>All workspaces have active projects.</span>
                                </div>
                            )}
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

                <section
                    aria-label="Dashboard decision brief"
                    className="rounded-2xl border border-divider bg-bg-paper p-5 shadow-sm"
                >
                    <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                        <div>
                            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-secondary">
                                What to do next
                            </p>
                            <h2 className="mt-2 text-xl font-semibold text-text-primary">
                                {dashboardDecisionBrief.headline}
                            </h2>
                            <p className="mt-2 max-w-3xl text-sm text-text-secondary">
                                {dashboardDecisionBrief.description}
                            </p>
                            <p className="mt-3 text-xs font-medium uppercase tracking-[0.14em] text-text-secondary">
                                {pluralize(blockedWork.length, 'blocked item')} · {pluralize(reviewRequired.length, 'review item')} · {pluralize(safeToContinue.length, 'safe continuation')}
                            </p>
                        </div>
                        {dashboardDecisionBrief.action && dashboardDecisionBrief.ctaLabel && (
                            <Button
                                type="button"
                                variant="solid"
                                onClick={() => openDashboardAction(dashboardDecisionBrief.action)}
                                className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-primary-700"
                            >
                                {dashboardDecisionBrief.ctaLabel}
                                <ArrowRight className="h-4 w-4" />
                            </Button>
                        )}
                    </div>
                </section>

                <section className="grid gap-4 md:grid-cols-3" aria-label="Backed dashboard action status">
                    <DashboardActionStatusCard
                        title="Blocked Work"
                        tone="warning"
                        actions={blockedWork}
                        loading={dashboardActionsLoading === true}
                        error={dashboardActionsError}
                        emptyText="No backend blockers are reported for this workspace."
                        onOpenProject={openDashboardAction}
                    />
                    <DashboardActionStatusCard
                        title="Review Required"
                        tone="review"
                        actions={reviewRequired}
                        loading={dashboardActionsLoading === true}
                        error={dashboardActionsError}
                        emptyText="No backend review actions are waiting."
                        onOpenProject={openDashboardAction}
                    />
                    <DashboardActionStatusCard
                        title="Safe To Continue"
                        tone="safe"
                        actions={safeToContinue}
                        loading={dashboardActionsLoading === true}
                        error={dashboardActionsError}
                        emptyText="No safe continuation action has been reported yet."
                        onOpenProject={openDashboardAction}
                    />
                    {executeDashboardAction.error instanceof Error && (
                        <p role="alert" className="md:col-span-3 rounded-xl border border-error-border bg-error-bg px-4 py-3 text-sm text-error-color">
                            Could not execute the safe dashboard action: {executeDashboardAction.error.message}
                        </p>
                    )}
                </section>

                <section className="rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm">
                    <div className="flex items-center justify-between gap-4">
                        <div>
                            <h2 className="text-xl font-semibold text-text-primary">Recent Projects</h2>                            <p className="mt-1 text-sm text-text-secondary">
                                Jump directly back into the latest project cockpits.
                            </p>
                        </div>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => navigate('/projects')}
                            className="inline-flex items-center gap-2 text-sm font-medium text-primary-600 transition-colors hover:text-primary-700"
                        >
                            View all projects
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
                                    <p className="font-medium text-text-primary">{project.name}</p>
                                    <p className="text-sm text-text-secondary">
                                        {project.description || 'No description yet'}
                                    </p>
                                </div>
                                <span className="text-xs uppercase tracking-[0.14em] text-text-secondary">
                                    {project.type}
                                </span>
                            </Button>
                        ))}
                    </div>
                </section>

                <section className="rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm">
                    <div className="flex items-center justify-between gap-4">
                        <div>
                            <h2 className="text-xl font-semibold text-text-primary">Workspaces</h2>
                            <p className="mt-1 text-sm text-text-secondary">
                                Switch context only when needed.
                            </p>
                        </div>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => navigate('/workspaces')}
                            className="text-sm font-medium text-primary-600 transition-colors hover:text-primary-700"
                        >
                            Manage workspaces
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
                                <p className="font-medium text-text-primary">{workspace.name}</p>
                                <p className="mt-1 text-sm text-text-secondary">
                                    {workspace.description || 'No description yet'}
                                </p>
                            </Button>
                        ))}
                    </div>
                </section>
            </section>
        </div>
    );
}

interface DashboardActionStatusCardProps {
    readonly title: string;
    readonly tone: 'warning' | 'review' | 'safe';
    readonly actions: readonly ProjectDashboardAction[];
    readonly loading: boolean;
    readonly error: unknown;
    readonly emptyText: string;
    readonly onOpenProject: (action: ProjectDashboardAction) => void;
}

function DashboardActionStatusCard(props: DashboardActionStatusCardProps) {
    const { title, tone, actions, loading, error, emptyText, onOpenProject } = props;
    const toneClass =
        tone === 'safe'
            ? 'border-emerald-200 bg-emerald-50/60 text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/20 dark:text-emerald-200'
            : tone === 'review'
                ? 'border-primary-200 bg-primary-50/60 text-primary-800 dark:border-primary-900/50 dark:bg-primary-950/20 dark:text-primary-200'
                : 'border-warning-border bg-warning-bg/60 text-warning-color dark:border-warning-border/50 dark:bg-warning-bg/20 dark:text-warning-color';

    return (
        <div className={`rounded-2xl border p-5 shadow-sm ${toneClass}`}>
            <div className="flex items-center justify-between gap-3">
                <h2 className="text-base font-semibold">{title}</h2>
                <span className="rounded-full bg-white/70 px-2 py-0.5 text-xs font-semibold dark:bg-black/20">
                    {actions.length}
                </span>
            </div>

            {loading ? (
                <p className="mt-4 text-sm opacity-80">Loading backend action status...</p>
            ) : error ? (
                <p className="mt-4 text-sm opacity-80">Could not load backed action status. Retry from the dashboard refresh.</p>
            ) : actions.length === 0 ? (
                <p className="mt-4 text-sm opacity-80">{emptyText}</p>
            ) : (
                <div className="mt-4 space-y-3">
                    {actions.slice(0, 3).map((action) => (
                        <Button
                            key={action.id}
                            type="button"
                            variant="outline"
                            onClick={() => onOpenProject(action)}
                            className="block w-full rounded-xl bg-white/75 p-3 text-left text-sm shadow-sm transition-transform hover:-translate-y-0.5 dark:bg-black/20"
                        >
                            <span className="block font-semibold">{action.title}</span>
                            <span className="mt-1 block opacity-80">{action.projectName} · {action.summary}</span>
                        </Button>
                    ))}
                </div>
            )}
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

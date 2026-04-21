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
import { ArrowRight, FolderOpen, Plus, ShieldAlert } from 'lucide-react';

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

/**
 * Home Dashboard Component
 * 
 * Orchestrates the view state based on data availability and authentication.
 */
export default function Component() {
    const navigate = useNavigate();
    const { ownedProjects, includedProjects, workspaces, isLoading: workspaceLoading } = useWorkspaceContext();
    const { getLastOpenedProject, setLastOpenedProject } = useLastOpenedProject();
    const setHeaderVisible = useSetAtom(headerVisibleAtom);

    const allProjects = [...ownedProjects, ...includedProjects];
    const isLoading = workspaceLoading;

    // Derive guest/empty states from real auth and workspace data
    const currentUser = useCurrentUser();
    const isGuest = !currentUser.isAuthenticated;
    const isEmpty = !isLoading && !isGuest && allProjects.length === 0;
    const recentProjects = useMemo(
        () => [...allProjects].sort((left, right) => right.updatedAt.localeCompare(left.updatedAt)).slice(0, 3),
        [allProjects]
    );

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
        navigate(`/p/${projectId}`);
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
        const workspaceProjects = allProjects.filter(p => p.ownerWorkspaceId === workspaceId);
        if (workspaceProjects.length > 0) {
            const firstProject = workspaceProjects[0];
            setLastOpenedProject(workspaceId, firstProject.id);
            navigate(`/p/${firstProject.id}`);
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
                        <button
                            type="button"
                            onClick={() => navigate('/projects')}
                            className="inline-flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-primary-700"
                        >
                            <Plus className="h-4 w-4" />
                            Create Project
                        </button>
                    </div>
                </div>

                <div className="grid gap-4 md:grid-cols-3">
                    <button
                        type="button"
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
                    </button>

                    <button
                        type="button"
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
                    </button>

                    <button
                        type="button"
                        onClick={() => navigate('/workspaces')}
                        className="rounded-2xl border border-divider bg-bg-paper p-5 text-left shadow-sm transition-transform hover:-translate-y-0.5"
                    >
                        <div className="mb-4 inline-flex rounded-full bg-amber-50 p-2 text-amber-600 dark:bg-amber-900/30 dark:text-amber-300">
                            <ShieldAlert className="h-5 w-5" />
                        </div>
                        <h2 className="text-lg font-semibold text-text-primary">Review Blockers</h2>
                        <p className="mt-2 text-sm text-text-secondary">
                            Check workspace availability, loading failures, and unresolved setup issues before continuing.
                        </p>
                    </button>
                </div>

                <section className="rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm">
                    <div className="flex items-center justify-between gap-4">
                        <div>
                            <h2 className="text-xl font-semibold text-text-primary">Recent Projects</h2>
                            <p className="mt-1 text-sm text-text-secondary">
                                Jump directly back into the latest project cockpits.
                            </p>
                        </div>
                        <button
                            type="button"
                            onClick={() => navigate('/projects')}
                            className="inline-flex items-center gap-2 text-sm font-medium text-primary-600 transition-colors hover:text-primary-700"
                        >
                            View all projects
                            <ArrowRight className="h-4 w-4" />
                        </button>
                    </div>

                    <div className="mt-6 grid gap-3">
                        {recentProjects.map((project) => (
                            <button
                                key={project.id}
                                type="button"
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
                            </button>
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
                        <button
                            type="button"
                            onClick={() => navigate('/workspaces')}
                            className="text-sm font-medium text-primary-600 transition-colors hover:text-primary-700"
                        >
                            Manage workspaces
                        </button>
                    </div>

                    <div className="mt-6 grid gap-3 md:grid-cols-2">
                        {workspaces.map((workspace) => (
                            <button
                                key={workspace.id}
                                type="button"
                                onClick={() => handleWorkspaceClick(workspace.id)}
                                className="rounded-xl border border-divider px-4 py-4 text-left transition-colors hover:bg-bg-default"
                            >
                                <p className="font-medium text-text-primary">{workspace.name}</p>
                                <p className="mt-1 text-sm text-text-secondary">
                                    {workspace.description || 'No description yet'}
                                </p>
                            </button>
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

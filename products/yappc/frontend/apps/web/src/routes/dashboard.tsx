/**
 * App Route Index (Refactored)
 *
 * Home Dashboard - Command Center & Task Executor.
 * Smart container that manages authentication state and data fetching.
 *
 * @doc.type route
 * @doc.purpose Task-centric home dashboard container
 * @doc.layer product
 * @doc.pattern Route Module
 */

import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router';
import { useSetAtom } from 'jotai';
import { useQuery } from '@tanstack/react-query';

// Hooks
import { useWorkspaceContext } from '../hooks/useWorkspaceData';
import { useWorkflows } from '../hooks/useWorkflows';
import { useLastOpenedProject } from '../hooks/useLastOpenedProject';
import { useCurrentUser } from '../providers/AuthProvider';
import { headerVisibleAtom } from '../state/atoms/layoutAtom';

import { RouteErrorBoundary } from '../components/route/ErrorBoundary';

// Components
import { WorkspaceSelectionDialog } from '../components/workspace/WorkspaceSelectionDialog';
import { DashboardView } from '../components/dashboard/DashboardView';
import { GuestLandingView } from '../components/dashboard/GuestLandingView';
import { EmptyStateView } from '../components/dashboard/EmptyStateView';
import { DashboardSkeleton } from '../components/dashboard/DashboardSkeleton';
import type { PriorityTask } from '../components/dashboard/PriorityTasksList';

/**
 * Home Dashboard Component
 * 
 * Orchestrates the view state based on data availability and authentication.
 */
export default function Component() {
    const navigate = useNavigate();
    const { ownedProjects, includedProjects, workspaces, isLoading: workspaceLoading } = useWorkspaceContext();
    const { workflows, isLoading: workflowsLoading } = useWorkflows();
    const { getLastOpenedProject, setLastOpenedProject } = useLastOpenedProject();
    const setHeaderVisible = useSetAtom(headerVisibleAtom);

    const allProjects = [...ownedProjects, ...includedProjects];
    const isLoading = workspaceLoading || workflowsLoading;

    // Workspace selection dialog state
    const [workspaceDialogOpen, setWorkspaceDialogOpen] = useState(false);
    const [selectedProject, setSelectedProject] = useState<{ id: string; name: string } | null>(null);
    const [projectWorkspaces, setProjectWorkspaces] = useState<Array<{ id: string; name: string; description?: string; isOwner?: boolean }>>([]);

    // Derive guest/empty states from real auth and workspace data
    const currentUser = useCurrentUser();
    const isGuest = !currentUser.isAuthenticated;
    const isEmpty = !isLoading && !isGuest && allProjects.length === 0 && workflows.length === 0;

    // Control header visibility based on auth state
    useEffect(() => {
        setHeaderVisible(!isGuest);
        return () => setHeaderVisible(true);
    }, [isGuest, setHeaderVisible]);

    // Fetch priority tasks from the API
    const { data: priorityTasksData } = useQuery<PriorityTask[]>({
        queryKey: ['priority-tasks'],
        queryFn: async () => {
            const res = await fetch('/api/tasks?priority=high&limit=5');
            if (!res.ok) return [];
            return res.json() as Promise<PriorityTask[]>;
        },
        enabled: !isGuest && !isLoading,
        initialData: [],
    });
    const priorityTasks: PriorityTask[] = priorityTasksData ?? [];

    // --- HANDLERS ---

    const handleTaskClick = (task: PriorityTask) => {
        // Implicitly switch persona logic will be handled by the route or context
        navigate(`/p/${task.projectId}/canvas?taskId=${task.id}&persona=${task.persona.toLowerCase()}`);
    };

    const handleProjectClick = (projectId: string) => {
        const project = allProjects.find(p => p.id === projectId);
        if (!project) return;

        // Find all workspaces this project belongs to
        const relatedWorkspaces = workspaces.filter(ws => {
            return project.workspaceId === ws.id;
        });

        if (relatedWorkspaces.length > 1) {
            // Show workspace selection dialog
            setSelectedProject({ id: projectId, name: project.name });
            setProjectWorkspaces(relatedWorkspaces.map(ws => ({
                id: ws.id,
                name: ws.name,
                description: ws.description,
                isOwner: project.workspaceId === ws.id,
            })));
            setWorkspaceDialogOpen(true);
        } else {
            // Direct navigation
            const workspaceId = project.workspaceId || workspaces[0]?.id;
            if (workspaceId) {
                setLastOpenedProject(workspaceId, projectId);
            }
            navigate(`/p/${projectId}/canvas`);
        }
    };

    const handleWorkspaceSelection = (workspaceId: string) => {
        if (selectedProject) {
            setLastOpenedProject(workspaceId, selectedProject.id);
            navigate(`/p/${selectedProject.id}/canvas`);
            setWorkspaceDialogOpen(false);
            setSelectedProject(null);
        }
    };

    const handleWorkspaceClick = (workspaceId: string) => {
        // Try to get last opened project for this workspace
        const lastProjectId = getLastOpenedProject(workspaceId);

        if (lastProjectId) {
            const project = allProjects.find(p => p.id === lastProjectId);
            if (project) {
                navigate(`/p/${lastProjectId}/canvas`);
                return;
            }
        }

        // Fallback: open first project in workspace
        const workspaceProjects = allProjects.filter(p => p.workspaceId === workspaceId);
        if (workspaceProjects.length > 0) {
            const firstProject = workspaceProjects[0];
            setLastOpenedProject(workspaceId, firstProject.id);
            navigate(`/p/${firstProject.id}/canvas`);
        } else {
            navigate(`/projects`);
        }
    };

    const handleWorkflowClick = (workflowId: string) => {
        navigate(`/workflows/${workflowId}`);
    };

    const handleSearchClick = () => {
        window.dispatchEvent(new KeyboardEvent('keydown', {
            key: 'k',
            metaKey: true,
            bubbles: true
        }));
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
                onCreateProject={() => navigate('/projects/new')}
                onSkip={() => navigate('/projects')}
            />
        );
    }

    return (
        <div className="h-full overflow-auto bg-bg-default">
            <DashboardView
                priorityTasks={priorityTasks}
                recentProjects={allProjects}
                recentWorkflows={workflows}
                workspaces={workspaces}
                onTaskClick={handleTaskClick}
                onViewAllTasks={() => { }} // NOTE: Navigate to tasks inbox
                onSearchClick={handleSearchClick}
                onProjectClick={handleProjectClick}
                onCreateProject={() => navigate('/projects/new')}
                onViewAllProjects={() => navigate('/projects')}
                onWorkflowClick={handleWorkflowClick}
                onCreateWorkflow={() => navigate('/workflows/new')}
                onViewAllWorkflows={() => navigate('/workflows')}
                onWorkspaceClick={handleWorkspaceClick}
                onCreateWorkspace={() => navigate('/workspaces')}
                onViewAllWorkspaces={() => navigate('/workspaces')}
            />

            {/* Workspace Selection Dialog */}
            <WorkspaceSelectionDialog
                open={workspaceDialogOpen}
                projectName={selectedProject?.name || ''}
                workspaces={projectWorkspaces}
                defaultWorkspaceId={projectWorkspaces.find(w => w.isOwner)?.id}
                onSelect={handleWorkspaceSelection}
                onCancel={() => {
                    setWorkspaceDialogOpen(false);
                    setSelectedProject(null);
                }}
            />
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

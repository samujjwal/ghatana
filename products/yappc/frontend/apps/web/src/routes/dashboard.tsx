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
import { Box, Typography, Button, Stack } from '@ghatana/ui';
import { useSetAtom } from 'jotai';

// Hooks
import { useWorkspaceContext } from '../hooks/useWorkspaceData';
import { useWorkflows } from '../hooks/useWorkflows';
import { useLastOpenedProject } from '../hooks/useLastOpenedProject';
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

    // --- DEMO STATE MANAGEMENT ---
    // In a real app, these states would be derived from `useWorkspaceContext` and `useAuth`.
    // For development visualization, we use local state toggles.
    const [demoState, setDemoState] = useState<'authenticated' | 'empty' | 'guest'>('authenticated');

    // Derived states based on demo toggle OR real data
    const isGuest = demoState === 'guest';
    const isEmpty = demoState === 'empty' || (allProjects.length === 0 && workflows.length === 0);

    // Effect to control header visibility based on state
    useEffect(() => {
        // Only show header in authenticated state
        setHeaderVisible(demoState === 'authenticated');

        // Cleanup: ensure header is visible when leaving this route
        return () => setHeaderVisible(true);
    }, [demoState, setHeaderVisible]);

    // Mock Tasks Data (replacing with real API in future)
    const priorityTasks: PriorityTask[] = [
        {
            id: '101',
            title: 'Review Wireframes for Login Flow',
            project: 'Project Alpha',
            projectId: 'alpha',
            type: 'Design',
            priority: 'High',
            persona: 'Product Manager',
            dueDate: new Date(Date.now() + 86400000).toISOString(), // Tomorrow
        },
        {
            id: '102',
            title: 'Fix Build Failure in CI Pipeline',
            project: 'Backend Core',
            projectId: 'backend',
            type: 'Code',
            priority: 'Urgent',
            persona: 'Developer',
            dueDate: new Date(Date.now() - 3600000).toISOString(), // Overdue
            isBlocked: true
        },
        {
            id: '103',
            title: 'Approve Staging Release v2.1',
            project: 'Mobile UI Kit',
            projectId: 'mobile-kit',
            type: 'Deploy',
            priority: 'Medium',
            persona: 'DevOps',
            dueDate: new Date(Date.now() + 172800000).toISOString()
        },
    ];

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

    const onDemoStateChange = (state: 'authenticated' | 'empty' | 'guest') => {
        setDemoState(state);
    };

    const handleSearchClick = () => {
        // Trigger generic search command
        window.dispatchEvent(new KeyboardEvent('keydown', {
            key: 'k',
            metaKey: true,
            bubbles: true
        }));
    };

    // --- RENDER ---

    const DebugControls = () => (
        <Box className="fixed p-2 rounded-lg bottom-[16px] right-[16px] z-[9999] bg-white dark:bg-gray-900 shadow-md opacity-[0.8]">
            <Typography as="span" className="mb-2 block text-xs font-bold text-gray-500">
                Page State Preview
            </Typography>
            <Stack spacing={1}>
                <Button size="sm" variant={demoState === 'guest' ? 'contained' : 'outlined'} onClick={() => onDemoStateChange('guest')}>Guest</Button>
                <Button size="sm" variant={demoState === 'empty' ? 'contained' : 'outlined'} onClick={() => onDemoStateChange('empty')}>Empty</Button>
                <Button size="sm" variant={demoState === 'authenticated' ? 'contained' : 'outlined'} onClick={() => onDemoStateChange('authenticated')}>Full</Button>
            </Stack>
        </Box>
    );

    if (isLoading) {
        return <DashboardSkeleton />;
    }

    if (isGuest) {
        return (
            <>
                <GuestLandingView
                    onDemoLogin={() => setDemoState('authenticated')}
                    onDemoEmpty={() => setDemoState('empty')}
                />

                {/* Normally debug controls aren't on landing page, but for testing: */}
                {/* <DebugControls /> */}
            </>
        );
    }

    if (isEmpty) {
        return (
            <>
                <EmptyStateView
                    onCreateProject={() => navigate('/projects/new')}
                    onSkip={() => setDemoState('authenticated')}
                />
                {import.meta.env.DEV && <DebugControls />}
            </>
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
            {import.meta.env.DEV && <DebugControls />}
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

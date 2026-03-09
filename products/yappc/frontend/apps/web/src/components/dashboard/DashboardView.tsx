import { useNavigate } from 'react-router';
import { Folder, GitBranch as AccountTree, Plus as Add, Search, Building as Business, Code, ExternalLink as Launch } from 'lucide-react';
import { Card, CardContent, Typography, Button, Box, Grid, Surface as Paper, IconButton, Input as InputBase, Chip, Avatar, Stack } from '@ghatana/ui';

import { PriorityTasksList, type PriorityTask } from './PriorityTasksList';
import { ProjectCard } from '../project/ProjectCard';
import type { Project, Workflow, Workspace } from './types';
import { ChevronRight } from 'lucide-react';

interface DashboardViewProps {
    userName?: string;
    priorityTasks: PriorityTask[];
    recentProjects: Project[];
    recentWorkflows: Workflow[];
    workspaces: Workspace[];

    onTaskClick: (task: PriorityTask) => void;
    onViewAllTasks: () => void;

    onSearchClick?: () => void;

    onProjectClick: (projectId: string) => void;
    onCreateProject: () => void;
    onViewAllProjects: () => void;

    onWorkflowClick: (workflowId: string) => void;
    onCreateWorkflow: () => void;
    onViewAllWorkflows: () => void;

    onWorkspaceClick: (workspaceId: string) => void;
    onCreateWorkspace: () => void;
    onViewAllWorkspaces: () => void;
}

export function DashboardView({
    userName,
    priorityTasks,
    recentProjects,
    recentWorkflows,
    workspaces,
    onTaskClick,
    onViewAllTasks,
    onSearchClick,
    onProjectClick,
    onCreateProject,
    onViewAllProjects,
    onWorkflowClick,
    onCreateWorkflow,
    onViewAllWorkflows,
    onWorkspaceClick,
    onCreateWorkspace,
    onViewAllWorkspaces
}: DashboardViewProps) {
    const handleSearchKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
        if (!onSearchClick) return;
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onSearchClick();
        }
    };

    return (
        <div className="max-w-7xl mx-auto p-6">
            {/* HERO SECTION */}
            <div className="mb-10">
                <Box display="flex" flexDirection={{ xs: 'column', md: 'row' }} alignItems={{ xs: 'start', md: 'center' }} justifyContent="space-between" mb={2}>
                    <div>
                        <Typography as="h4" fontWeight="bold" className="text-text-primary mb-1">
                            {userName ? `Welcome back, ${userName}` : 'Welcome back'}
                        </Typography>
                        <Typography as="p" className="text-text-secondary">
                            Here are your priority tasks for today.
                        </Typography>
                    </div>
                    <Stack direction="row" alignItems="center" spacing={1} className="mt-4 md:mt-0">
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">VIEWING AS</Typography>
                        <Chip
                            avatar={<Avatar className="bg-indigo-600">P</Avatar>}
                            label="Product Manager"
                            onClick={() => { }}
                            variant="outlined"
                            tone="secondary"
                        />
                    </Stack>
                </Box>

                <Paper
                    variant="flat"
                    onClick={onSearchClick}
                    className="flex items-center w-full max-w-[700px] rounded-lg border border-solid border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-900"
                    style={{
                        cursor: onSearchClick ? 'pointer' : 'default',
                        transition: 'box-shadow 0.2s',
                    }}
                >
                    <IconButton className="p-[10px]" aria-label="search">
                        <Search />
                    </IconButton>
                    <InputBase
                        className="ml-2 flex-1"
                        placeholder="Search tasks, projects, code, or ask AI..."
                        readOnly
                        inputProps={{ 'aria-label': 'Search tasks, projects, code, or ask AI' }}
                        onKeyDown={handleSearchKeyDown}
                    />
                    <Chip label="Cmd+K" size="sm" className="mr-2 text-xs rounded h-[24px]" />
                </Paper>
            </div>

            {/* PRIORITY TASKS SECTION */}
            <PriorityTasksList
                tasks={priorityTasks}
                onTaskClick={onTaskClick}
                onViewAll={onViewAllTasks}
            />

            {/* Main Grid (IDE Launch, Projects & Workflows) */}
            <Typography as="h6" fontWeight="bold" className="mb-6">
                Development Hub
            </Typography>
            <Grid container spacing={4}>
                {/* IDE Launch Card */}
                <Grid item xs={12} lg={6}>
                    <Card
                        variant="flat"
                        className="border-[2px_solid] border-blue-600 h-full rounded-lg relative overflow-hidden before:absolute before:top-[0px] before:left-[0px] before:right-[0px] before:h-[4px]" >
                        <CardContent className="p-6">
                            <Box display="flex" alignItems="center" gap={2} mb={3}>
                                <Box
                                    className="flex h-[48px] w-[48px] items-center justify-center rounded-lg text-white"
                                    style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}
                                >
                                    <Code className="text-2xl" />
                                </Box>
                                <Box>
                                    <Typography as="h6" fontWeight="bold" color="primary.main">
                                        Collaborative IDE
                                    </Typography>
                                    <Typography as="p" className="text-sm" color="text.secondary">
                                        Full-featured development environment
                                    </Typography>
                                </Box>
                            </Box>

                            <Box className="mb-6">
                                <Typography as="p" className="mb-4 text-sm" color="text.secondary">
                                    Experience the complete IDE with:
                                </Typography>
                                <Box component="ul" className="pl-4 m-0 [&_li]:mb-2">
                                <Typography component="li" as="p" className="text-sm" color="text.secondary">
                                        Monaco Editor with syntax highlighting
                                    </Typography>
                                    <Typography component="li" as="p" className="text-sm" color="text.secondary">
                                        Visual UI Builder with drag-and-drop
                                    </Typography>
                                    <Typography component="li" as="p" className="text-sm" color="text.secondary">
                                        Advanced debugging capabilities
                                    </Typography>
                                    <Typography component="li" as="p" className="text-sm" color="text.secondary">
                                        Real-time collaborative editing
                                    </Typography>
                                    <Typography component="li" as="p" className="text-sm" color="text.secondary">
                                        Smart refactoring tools
                                    </Typography>
                                </Box>
                            </Box>

                    <Button
                        variant="solid"
                        size="lg"
                        startIcon={<Launch />}
                        onClick={() => window.open('/ide', '_blank')}
                                className="text-white py-[9.6px] px-6 text-base font-bold rounded-lg shadow w-full hover:shadow-md" style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }} >
                        Launch IDE
                    </Button>
                        </CardContent>
                    </Card>
                </Grid>

                {/* Projects Section */}
                <Grid size={{ xs: 12, md: 6 }}>
                    <Card variant="flat" className="h-full rounded-lg border border-solid border-gray-200 dark:border-gray-700">
                        <CardContent>
                            <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
                                <Box display="flex" alignItems="center" gap={1}>
                                    <Folder color="action" />
                                    <Typography as="p" className="text-lg font-medium" fontWeight="bold">
                                        Projects
                                    </Typography>
                                </Box>
                                <Button
                                    startIcon={<Add />}
                                    onClick={onCreateProject}
                                    size="sm"
                                >
                                    New
                                </Button>
                            </Box>

                            <div className="space-y-2">
                                {recentProjects.length === 0 ? (
                                    <div className="text-center py-8">
                                        <Folder className="mb-4 text-5xl text-gray-500 dark:text-gray-400 opacity-[0.3]" />
                                        <Typography as="p" className="text-sm" color="text.secondary" gutterBottom>
                                            No projects yet.
                                        </Typography>
                                        <Button variant="outlined" size="sm" startIcon={<Add />} onClick={onCreateProject}>
                                            Create Project
                                        </Button>
                                    </div>
                                ) : (
                                    <>
                                        {recentProjects.slice(0, 3).map((project) => (
                                            <ProjectCard
                                                key={project.id}
                                                project={project}
                                                onClick={onProjectClick}
                                            />
                                        ))}
                                        <Button
                                            fullWidth
                                            onClick={onViewAllProjects}
                                            size="sm"
                                            className="mt-2"
                                        >
                                            View all {recentProjects.length > 3 ? recentProjects.length : ''} projects
                                        </Button>
                                    </>
                                )}
                            </div>
                        </CardContent>
                    </Card>
                </Grid>

                {/* Workflows Section */}
                <Grid size={{ xs: 12, md: 6 }}>
                    <Card variant="flat" className="h-full rounded-lg border border-solid border-gray-200 dark:border-gray-700">
                        <CardContent>
                            <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
                                <Box display="flex" alignItems="center" gap={1}>
                                    <AccountTree color="action" />
                                    <Typography as="p" className="text-lg font-medium" fontWeight="bold">
                                        Workflows
                                    </Typography>
                                </Box>
                                <Button
                                    startIcon={<Add />}
                                    onClick={onCreateWorkflow}
                                    size="sm"
                                >
                                    New
                                </Button>
                            </Box>

                            <div className="space-y-2">
                                {recentWorkflows.length === 0 ? (
                                    <div className="text-center py-8">
                                        <AccountTree className="mb-4 text-5xl text-gray-500 dark:text-gray-400 opacity-[0.3]" />
                                        <Typography as="p" className="text-sm" color="text.secondary" gutterBottom>
                                            No workflows yet.
                                        </Typography>
                                        <Button variant="outlined" size="sm" startIcon={<Add />} onClick={onCreateWorkflow}>
                                            Create Workflow
                                        </Button>
                                    </div>
                                ) : (
                                    <>
                                        {recentWorkflows.slice(0, 3).map((workflow) => (
                                            <div
                                                key={workflow.id}
                                                onClick={() => onWorkflowClick(workflow.id)}
                                                className="p-3 rounded-lg border border-divider hover:border-primary-300 dark:hover:border-primary-700 hover:bg-bg-paper-secondary cursor-pointer transition-all group"
                                            >
                                                <div className="flex items-center justify-between">
                                                    <div className="flex-1">
                                                        <Typography as="p" className="text-sm transition-colors group-hover:text-primary-600" fontWeight="medium">
                                                            {workflow.name}
                                                        </Typography>
                                                        <div className="flex items-center gap-2 mt-1">
                                                            <Chip
                                                                label={workflow.status}
                                                                size="sm"
                                                                color={workflow.status === 'active' ? 'success' : 'default'}
                                                                variant="outlined"
                                                                className="h-[20px] text-[0.6rem]"
                                                            />
                                                        </div>
                                                    </div>
                                                    <ChevronRight size={16} className="text-text-secondary group-hover:text-primary-600 transition-colors" />
                                                </div>
                                            </div>
                                        ))}
                                        <Button
                                            fullWidth
                                            onClick={onViewAllWorkflows}
                                            size="sm"
                                            className="mt-2"
                                        >
                                            View all {recentWorkflows.length > 3 ? recentWorkflows.length : ''} workflows
                                        </Button>
                                    </>
                                )}
                            </div>
                        </CardContent>
                    </Card>
                </Grid>

                {/* Workspaces List (Compact) */}
                <Grid size={{ xs: 12 }}>
                    <Box className="mt-4 flex gap-4 flex-wrap">
                        {workspaces.map(ws => (
                            <Chip
                                key={ws.id}
                                icon={<Business className="text-base" />}
                                label={ws.name}
                                component="a"
                                clickable
                                onClick={() => onWorkspaceClick(ws.id)}
                            />
                        ))}
                        <Chip
                            icon={<Add className="text-base" />}
                            label="New Workspace"
                            variant="outlined"
                            onClick={onCreateWorkspace}
                        />
                    </Box>
                </Grid>
            </Grid>
        </div>
    );
}

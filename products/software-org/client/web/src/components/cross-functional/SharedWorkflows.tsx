/**
 * Shared Workflows Component
 *
 * Component for viewing and managing collaborative workflow processes across teams
 * and organizational layers.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import {
    Grid,
    Card,
    Box,
    Chip,
    Tabs,
    Tab,
    Button,
    Typography,
    Stack,
    Table,
    TableHead,
    TableBody,
    TableRow,
    TableCell,
    LinearProgress,
} from '@ghatana/design-system';

/**
 * Workflow metrics
 */
export interface WorkflowMetrics {
    totalWorkflows: number;
    activeWorkflows: number;
    completionRate: number; // 0-100
    avgCycleTime: number; // days
}

/**
 * Workflow process
 */
export interface WorkflowProcess {
    id: string;
    name: string;
    category: 'approval' | 'review' | 'development' | 'deployment' | 'onboarding';
    status: 'not-started' | 'in-progress' | 'blocked' | 'completed';
    priority: 'urgent' | 'high' | 'normal' | 'low';
    progress: number; // 0-100
    currentStage: string;
    totalStages: number;
    participants: string[];
    owner: string;
    startDate: string;
    dueDate: string;
    estimatedDays: number;
}

/**
 * Workflow stage
 */
export interface WorkflowStage {
    id: string;
    workflowId: string;
    stageName: string;
    status: 'pending' | 'active' | 'completed' | 'skipped';
    assignee: string;
    startDate?: string;
    completedDate?: string;
    duration?: number; // days
    dependencies: string[];
    blockers?: string[];
}

/**
 * Workflow template
 */
export interface WorkflowTemplate {
    id: string;
    name: string;
    category: 'approval' | 'review' | 'development' | 'deployment' | 'onboarding';
    description: string;
    stages: string[];
    estimatedDuration: number; // days
    participantRoles: string[];
    usageCount: number;
    lastUsed: string;
}

/**
 * Workflow activity
 */
export interface WorkflowActivity {
    id: string;
    workflowId: string;
    workflowName: string;
    activityType: 'created' | 'updated' | 'completed' | 'blocked' | 'assigned';
    description: string;
    user: string;
    timestamp: string;
    details?: string;
}

/**
 * Shared Workflows Props
 */
export interface SharedWorkflowsProps {
    /** Workflow metrics */
    metrics: WorkflowMetrics;
    /** Active workflows */
    workflows: WorkflowProcess[];
    /** Workflow stages */
    stages: WorkflowStage[];
    /** Workflow templates */
    templates: WorkflowTemplate[];
    /** Workflow activities */
    activities: WorkflowActivity[];
    /** Callback when workflow is clicked */
    onWorkflowClick?: (workflowId: string) => void;
    /** Callback when stage is clicked */
    onStageClick?: (stageId: string) => void;
    /** Callback when template is clicked */
    onTemplateClick?: (templateId: string) => void;
    /** Callback when activity is clicked */
    onActivityClick?: (activityId: string) => void;
    /** Callback when create workflow is clicked */
    onCreateWorkflow?: () => void;
}

/**
 * Shared Workflows Component
 *
 * Provides comprehensive workflow management with:
 * - Workflow summary metrics
 * - Active workflows with progress tracking
 * - Workflow stage details and dependencies
 * - Workflow templates for common processes
 * - Activity stream for workflow events
 * - Tab-based navigation (Workflows, Stages, Templates, Activity)
 *
 * Reuses @ghatana/design-system components:
 * - Card (workflow cards, template cards)
 * - Table (stages table, activity table)
 * - Chip (status, category, priority indicators)
 * - LinearProgress (progress bars)
 *
 * @example
 * ```tsx
 * <SharedWorkflows
 *   metrics={workflowMetrics}
 *   workflows={activeWorkflows}
 *   stages={workflowStages}
 *   templates={workflowTemplates}
 *   activities={recentActivities}
 *   onWorkflowClick={(id) => navigate(`/workflows/${id}`)}
 *   onCreateWorkflow={() => openCreateDialog()}
 * />
 * ```
 */
export const SharedWorkflows: React.FC<SharedWorkflowsProps> = ({
    metrics,
    workflows,
    stages,
    templates,
    activities,
    onWorkflowClick,
    onStageClick,
    onTemplateClick,
    onActivityClick,
    onCreateWorkflow,
}) => {
    const [selectedTab, setSelectedTab] = useState<'workflows' | 'stages' | 'templates' | 'activity'>('workflows');
    const [statusFilter, setStatusFilter] = useState<'all' | 'not-started' | 'in-progress' | 'blocked' | 'completed'>('all');

    // Get status color
    const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'completed':
                return 'success';
            case 'in-progress':
            case 'active':
                return 'warning';
            case 'blocked':
                return 'error';
            case 'not-started':
            case 'pending':
            default:
                return 'default';
        }
    };

    // Get priority color
    const getPriorityColor = (priority: string): 'error' | 'warning' | 'default' => {
        switch (priority) {
            case 'urgent':
                return 'error';
            case 'high':
                return 'warning';
            default:
                return 'default';
        }
    };

    // Get category color
    const getCategoryColor = (category: string): 'error' | 'warning' | 'default' => {
        switch (category) {
            case 'approval':
            case 'deployment':
                return 'error';
            case 'review':
            case 'development':
                return 'warning';
            default:
                return 'default';
        }
    };

    // Get activity icon
    const getActivityIcon = (type: string): string => {
        switch (type) {
            case 'created':
                return '✨';
            case 'updated':
                return '🔄';
            case 'completed':
                return '✅';
            case 'blocked':
                return '🚫';
            case 'assigned':
                return '👤';
            default:
                return '📝';
        }
    };

    // Format date
    const formatDate = (dateString: string): string => {
        const date = new Date(dateString);
        return date.toLocaleDateString();
    };

    // Format relative time
    const formatRelativeTime = (timestamp: string): string => {
        const date = new Date(timestamp);
        const now = new Date();
        const diffMs = now.getTime() - date.getTime();
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);

        if (diffMins < 1) return 'Just now';
        if (diffMins < 60) return `${diffMins}m ago`;
        if (diffHours < 24) return `${diffHours}h ago`;
        if (diffDays < 7) return `${diffDays}d ago`;
        return date.toLocaleDateString();
    };

    // Calculate days remaining
    const getDaysRemaining = (dueDate: string): number => {
        const due = new Date(dueDate);
        const now = new Date();
        const diffMs = due.getTime() - now.getTime();
        return Math.ceil(diffMs / 86400000);
    };

    // Filter workflows
    const filteredWorkflows = statusFilter === 'all' ? workflows : workflows.filter((w) => w.status === statusFilter);

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Shared Workflows
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Collaborative processes across teams and layers
                    </Typography>
                </Box>
                {onCreateWorkflow && (
                    <Button variant="primary" size="md" onClick={onCreateWorkflow}>
                        Create Workflow
                    </Button>
                )}
            </Box>

            {/* Metrics Summary */}
            <Grid columns={4} gap={4}>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Total Workflows
                        </Typography>
                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100 mt-1">
                            {metrics.totalWorkflows}
                        </Typography>
                        <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                            {metrics.activeWorkflows} active
                        </Typography>
                    </Box>
                </Card>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Completion Rate
                        </Typography>
                        <Typography variant="h5" className={`mt-1 ${metrics.completionRate >= 80 ? 'text-green-600' : metrics.completionRate >= 60 ? 'text-orange-600' : 'text-red-600'}`}>
                            {metrics.completionRate}%
                        </Typography>
                    </Box>
                </Card>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Avg Cycle Time
                        </Typography>
                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100 mt-1">
                            {metrics.avgCycleTime}d
                        </Typography>
                    </Box>
                </Card>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Blocked Workflows
                        </Typography>
                        <Typography variant="h5" className={`mt-1 ${workflows.filter((w) => w.status === 'blocked').length > 0 ? 'text-red-600' : 'text-slate-900 dark:text-neutral-100'}`}>
                            {workflows.filter((w) => w.status === 'blocked').length}
                        </Typography>
                    </Box>
                </Card>
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`Workflows (${workflows.length})`} value="workflows" />
                    <Tab label={`Stages (${stages.length})`} value="stages" />
                    <Tab label={`Templates (${templates.length})`} value="templates" />
                    <Tab label={`Activity (${activities.length})`} value="activity" />
                </Tabs>

                {/* Workflows Tab */}
                {selectedTab === 'workflows' && (
                    <Box className="p-4">
                        {/* Status Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Chip label={`All (${workflows.length})`} color={statusFilter === 'all' ? 'error' : 'default'} onClick={() => setStatusFilter('all')} />
                            <Chip
                                label={`Not Started (${workflows.filter((w) => w.status === 'not-started').length})`}
                                color={statusFilter === 'not-started' ? 'default' : 'default'}
                                onClick={() => setStatusFilter('not-started')}
                            />
                            <Chip
                                label={`In Progress (${workflows.filter((w) => w.status === 'in-progress').length})`}
                                color={statusFilter === 'in-progress' ? 'warning' : 'default'}
                                onClick={() => setStatusFilter('in-progress')}
                            />
                            <Chip
                                label={`Blocked (${workflows.filter((w) => w.status === 'blocked').length})`}
                                color={statusFilter === 'blocked' ? 'error' : 'default'}
                                onClick={() => setStatusFilter('blocked')}
                            />
                            <Chip
                                label={`Completed (${workflows.filter((w) => w.status === 'completed').length})`}
                                color={statusFilter === 'completed' ? 'success' : 'default'}
                                onClick={() => setStatusFilter('completed')}
                            />
                        </Stack>

                        {/* Workflow List */}
                        <Stack spacing={3}>
                            {filteredWorkflows.map((workflow) => (
                                <Card key={workflow.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onWorkflowClick?.(workflow.id)}>
                                    <Box className="p-4">
                                        {/* Workflow Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {workflow.name}
                                                    </Typography>
                                                    <Chip label={workflow.category} color={getCategoryColor(workflow.category)} size="small" />
                                                    <Chip label={workflow.status} color={getStatusColor(workflow.status)} size="small" />
                                                    <Chip label={workflow.priority} color={getPriorityColor(workflow.priority)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    Owner: {workflow.owner} • Stage {workflow.currentStage} of {workflow.totalStages}
                                                </Typography>
                                            </Box>
                                            <Box className="text-right">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Due in
                                                </Typography>
                                                <Typography
                                                    variant="h6"
                                                    className={getDaysRemaining(workflow.dueDate) < 3 ? 'text-red-600' : getDaysRemaining(workflow.dueDate) < 7 ? 'text-orange-600' : 'text-slate-900 dark:text-neutral-100'}
                                                >
                                                    {getDaysRemaining(workflow.dueDate)}d
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Progress Bar */}
                                        <Box className="mb-3">
                                            <Box className="flex items-center justify-between mb-1">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Progress
                                                </Typography>
                                                <Typography variant="caption" className="text-slate-900 dark:text-neutral-100">
                                                    {workflow.progress}%
                                                </Typography>
                                            </Box>
                                            <LinearProgress
                                                variant="determinate"
                                                value={workflow.progress}
                                                color={workflow.status === 'completed' ? 'success' : workflow.status === 'blocked' ? 'error' : 'warning'}
                                            />
                                        </Box>

                                        {/* Workflow Footer */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={2} gap={3}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Participants ({workflow.participants.length})
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {workflow.participants.slice(0, 3).join(', ')}
                                                        {workflow.participants.length > 3 && ` +${workflow.participants.length - 3} more`}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Timeline
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {formatDate(workflow.startDate)} → {formatDate(workflow.dueDate)}
                                                    </Typography>
                                                </Box>
                                            </Grid>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    </Box>
                )}

                {/* Stages Tab */}
                {selectedTab === 'stages' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Workflow Stages
                        </Typography>

                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Stage Name</TableCell>
                                    <TableCell>Workflow</TableCell>
                                    <TableCell>Status</TableCell>
                                    <TableCell>Assignee</TableCell>
                                    <TableCell>Duration</TableCell>
                                    <TableCell>Dependencies</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {stages.map((stage) => (
                                    <TableRow
                                        key={stage.id}
                                        className="cursor-pointer hover:bg-slate-50 dark:hover:bg-neutral-800"
                                        onClick={() => onStageClick?.(stage.id)}
                                    >
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                {stage.stageName}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {workflows.find((w) => w.id === stage.workflowId)?.name || 'Unknown'}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={stage.status} color={getStatusColor(stage.status)} size="small" />
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                {stage.assignee}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {stage.duration ? `${stage.duration}d` : '-'}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {stage.dependencies.length > 0 ? `${stage.dependencies.length} deps` : 'None'}
                                            </Typography>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </Box>
                )}

                {/* Templates Tab */}
                {selectedTab === 'templates' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Workflow Templates
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {templates.map((template) => (
                                <Card key={template.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onTemplateClick?.(template.id)}>
                                    <Box className="p-4">
                                        {/* Template Header */}
                                        <Box className="flex items-start justify-between mb-2">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {template.name}
                                                    </Typography>
                                                    <Chip label={template.category} color={getCategoryColor(template.category)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {template.description}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Template Details */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={3} gap={2}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Stages
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {template.stages.length}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Duration
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {template.estimatedDuration}d
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Usage
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {template.usageCount}×
                                                    </Typography>
                                                </Box>
                                            </Grid>
                                        </Box>

                                        {/* Participant Roles */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 mb-2 block">
                                                Participant Roles
                                            </Typography>
                                            <Stack direction="row" spacing={1} className="flex-wrap">
                                                {template.participantRoles.slice(0, 3).map((role, i) => (
                                                    <Chip key={i} label={role} size="small" />
                                                ))}
                                                {template.participantRoles.length > 3 && <Chip label={`+${template.participantRoles.length - 3}`} size="small" />}
                                            </Stack>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}

                {/* Activity Tab */}
                {selectedTab === 'activity' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Recent Activity
                        </Typography>

                        <Stack spacing={2}>
                            {activities.map((activity) => (
                                <Card key={activity.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onActivityClick?.(activity.id)}>
                                    <Box className="p-4">
                                        <Box className="flex items-start gap-3">
                                            <Typography variant="h5" className="text-slate-600 dark:text-neutral-400">
                                                {getActivityIcon(activity.activityType)}
                                            </Typography>
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {activity.description}
                                                    </Typography>
                                                    <Chip label={activity.activityType} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-1">
                                                    Workflow: {activity.workflowName}
                                                </Typography>
                                                {activity.details && (
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {activity.details}
                                                    </Typography>
                                                )}
                                                <Box className="flex items-center gap-2 mt-2">
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        {activity.user}
                                                    </Typography>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        • {formatRelativeTime(activity.timestamp)}
                                                    </Typography>
                                                </Box>
                                            </Box>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    </Box>
                )}
            </Card>
        </Box>
    );
};

/**
 * Mock data for development/testing
 */
export const mockSharedWorkflowsData = {
    metrics: {
        totalWorkflows: 28,
        activeWorkflows: 15,
        completionRate: 82,
        avgCycleTime: 12,
    } as WorkflowMetrics,

    workflows: [
        {
            id: 'wf-1',
            name: 'Q1 Budget Approval Process',
            category: 'approval',
            status: 'in-progress',
            priority: 'urgent',
            progress: 65,
            currentStage: '3',
            totalStages: 5,
            participants: ['Sarah Johnson', 'Mike Chen', 'Emily Davis', 'John Smith'],
            owner: 'Sarah Johnson',
            startDate: '2025-12-01T00:00:00Z',
            dueDate: '2025-12-15T00:00:00Z',
            estimatedDays: 14,
        },
        {
            id: 'wf-2',
            name: 'Feature Development - AI Analytics',
            category: 'development',
            status: 'in-progress',
            priority: 'high',
            progress: 42,
            currentStage: '2',
            totalStages: 4,
            participants: ['Platform Engineering', 'Product Design', 'QA Team'],
            owner: 'Mike Chen',
            startDate: '2025-11-15T00:00:00Z',
            dueDate: '2025-12-20T00:00:00Z',
            estimatedDays: 35,
        },
        {
            id: 'wf-3',
            name: 'New Employee Onboarding',
            category: 'onboarding',
            status: 'blocked',
            priority: 'normal',
            progress: 25,
            currentStage: '1',
            totalStages: 6,
            participants: ['HR Team', 'IT Support', 'Manager'],
            owner: 'Emily Davis',
            startDate: '2025-12-08T00:00:00Z',
            dueDate: '2025-12-22T00:00:00Z',
            estimatedDays: 14,
        },
    ] as WorkflowProcess[],

    stages: [
        {
            id: 'stage-1',
            workflowId: 'wf-1',
            stageName: 'Budget Submission',
            status: 'completed',
            assignee: 'Sarah Johnson',
            startDate: '2025-12-01T00:00:00Z',
            completedDate: '2025-12-03T00:00:00Z',
            duration: 2,
            dependencies: [],
        },
        {
            id: 'stage-2',
            workflowId: 'wf-1',
            stageName: 'Manager Review',
            status: 'completed',
            assignee: 'Mike Chen',
            startDate: '2025-12-03T00:00:00Z',
            completedDate: '2025-12-06T00:00:00Z',
            duration: 3,
            dependencies: ['stage-1'],
        },
        {
            id: 'stage-3',
            workflowId: 'wf-1',
            stageName: 'Director Approval',
            status: 'active',
            assignee: 'Emily Davis',
            startDate: '2025-12-06T00:00:00Z',
            dependencies: ['stage-2'],
        },
        {
            id: 'stage-4',
            workflowId: 'wf-2',
            stageName: 'Requirements Analysis',
            status: 'completed',
            assignee: 'Product Design',
            startDate: '2025-11-15T00:00:00Z',
            completedDate: '2025-11-20T00:00:00Z',
            duration: 5,
            dependencies: [],
        },
        {
            id: 'stage-5',
            workflowId: 'wf-2',
            stageName: 'Development',
            status: 'active',
            assignee: 'Platform Engineering',
            startDate: '2025-11-20T00:00:00Z',
            dependencies: ['stage-4'],
        },
    ] as WorkflowStage[],

    templates: [
        {
            id: 'tmpl-1',
            name: 'Standard Approval Workflow',
            category: 'approval',
            description: 'Multi-level approval process for budget, resources, and strategic decisions',
            stages: ['Submission', 'Manager Review', 'Director Approval', 'VP Approval', 'Final Sign-off'],
            estimatedDuration: 10,
            participantRoles: ['Submitter', 'Manager', 'Director', 'VP', 'Executive'],
            usageCount: 45,
            lastUsed: '2025-12-10T00:00:00Z',
        },
        {
            id: 'tmpl-2',
            name: 'Feature Development Process',
            category: 'development',
            description: 'End-to-end feature development from requirements to deployment',
            stages: ['Requirements', 'Design', 'Development', 'Testing', 'Deployment'],
            estimatedDuration: 30,
            participantRoles: ['Product Manager', 'Designer', 'Engineer', 'QA', 'DevOps'],
            usageCount: 28,
            lastUsed: '2025-12-08T00:00:00Z',
        },
        {
            id: 'tmpl-3',
            name: 'Employee Onboarding',
            category: 'onboarding',
            description: 'Complete onboarding process for new team members',
            stages: ['Pre-boarding', 'Day 1 Setup', 'Training', '30-day Check-in', '90-day Review'],
            estimatedDuration: 90,
            participantRoles: ['HR', 'Manager', 'IT', 'Mentor', 'Buddy'],
            usageCount: 18,
            lastUsed: '2025-12-05T00:00:00Z',
        },
    ] as WorkflowTemplate[],

    activities: [
        {
            id: 'act-1',
            workflowId: 'wf-1',
            workflowName: 'Q1 Budget Approval Process',
            activityType: 'updated',
            description: 'Moved to Director Approval stage',
            user: 'Mike Chen',
            timestamp: '2025-12-11T09:30:00Z',
            details: 'Manager review completed successfully',
        },
        {
            id: 'act-2',
            workflowId: 'wf-3',
            workflowName: 'New Employee Onboarding',
            activityType: 'blocked',
            description: 'Workflow blocked due to missing IT equipment',
            user: 'Emily Davis',
            timestamp: '2025-12-11T08:15:00Z',
            details: 'Waiting for laptop delivery',
        },
        {
            id: 'act-3',
            workflowId: 'wf-2',
            workflowName: 'Feature Development - AI Analytics',
            activityType: 'assigned',
            description: 'Development stage assigned to Platform Engineering',
            user: 'Mike Chen',
            timestamp: '2025-12-10T16:45:00Z',
        },
    ] as WorkflowActivity[],
};

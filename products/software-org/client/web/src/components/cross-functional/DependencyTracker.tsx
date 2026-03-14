/**
 * Dependency Tracker Component
 *
 * Component for tracking and visualizing cross-team dependencies,
 * identifying blockers, and coordinating work across teams.
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
} from '@ghatana/design-system';

/**
 * Dependency metrics
 */
export interface DependencyMetrics {
    totalDependencies: number;
    activeDependencies: number;
    blockedTasks: number;
    avgResolutionDays: number;
}

/**
 * Cross-team dependency
 */
export interface CrossTeamDependency {
    id: string;
    dependentTeam: string;
    dependencyTeam: string;
    taskName: string;
    status: 'pending' | 'in-progress' | 'blocked' | 'resolved';
    priority: 'urgent' | 'high' | 'normal' | 'low';
    type: 'deliverable' | 'review' | 'approval' | 'information' | 'resource';
    dueDate: string;
    owner: string;
    blockers?: string[];
    impact: 'critical' | 'high' | 'medium' | 'low';
}

/**
 * Blocker
 */
export interface Blocker {
    id: string;
    blockedTask: string;
    blockedTeam: string;
    blockerDescription: string;
    blockerTeam: string;
    severity: 'critical' | 'major' | 'minor';
    reportedDate: string;
    owner: string;
    status: 'open' | 'in-progress' | 'resolved';
    estimatedResolution?: string; // ISO date
}

/**
 * Team coordination
 */
export interface TeamCoordination {
    id: string;
    teams: string[];
    coordinationType: 'sync-meeting' | 'shared-sprint' | 'joint-project' | 'knowledge-transfer';
    frequency: 'daily' | 'weekly' | 'biweekly' | 'monthly' | 'as-needed';
    nextScheduled: string;
    participants: string[];
    effectiveness: number; // 0-100
}

/**
 * Dependency activity
 */
export interface DependencyActivity {
    id: string;
    activityType: 'created' | 'updated' | 'blocked' | 'unblocked' | 'resolved';
    dependencyName: string;
    teams: string[];
    description: string;
    user: string;
    timestamp: string;
}

/**
 * Dependency Tracker Props
 */
export interface DependencyTrackerProps {
    /** Dependency metrics */
    metrics: DependencyMetrics;
    /** Cross-team dependencies */
    dependencies: CrossTeamDependency[];
    /** Blockers */
    blockers: Blocker[];
    /** Team coordination */
    coordination: TeamCoordination[];
    /** Dependency activities */
    activities: DependencyActivity[];
    /** Callback when dependency is clicked */
    onDependencyClick?: (dependencyId: string) => void;
    /** Callback when blocker is clicked */
    onBlockerClick?: (blockerId: string) => void;
    /** Callback when coordination is clicked */
    onCoordinationClick?: (coordinationId: string) => void;
    /** Callback when activity is clicked */
    onActivityClick?: (activityId: string) => void;
    /** Callback when create dependency is clicked */
    onCreateDependency?: () => void;
}

/**
 * Dependency Tracker Component
 *
 * Provides comprehensive dependency tracking with:
 * - Dependency summary metrics
 * - Cross-team dependency visualization
 * - Blocker identification and tracking
 * - Team coordination management
 * - Activity stream for dependency events
 * - Tab-based navigation (Dependencies, Blockers, Coordination, Activity)
 *
 * Reuses @ghatana/design-system components:
 * - Card (dependency cards, blocker cards, coordination cards)
 * - Table (dependency table, blocker table)
 * - Chip (status, priority, type, severity indicators)
 *
 * @example
 * ```tsx
 * <DependencyTracker
 *   metrics={dependencyMetrics}
 *   dependencies={crossTeamDependencies}
 *   blockers={activeBlockers}
 *   coordination={teamCoordination}
 *   activities={recentActivities}
 *   onDependencyClick={(id) => navigate(`/dependencies/${id}`)}
 *   onCreateDependency={() => openCreateDialog()}
 * />
 * ```
 */
export const DependencyTracker: React.FC<DependencyTrackerProps> = ({
    metrics,
    dependencies,
    blockers,
    coordination,
    activities,
    onDependencyClick,
    onBlockerClick,
    onCoordinationClick,
    onActivityClick,
    onCreateDependency,
}) => {
    const [selectedTab, setSelectedTab] = useState<'dependencies' | 'blockers' | 'coordination' | 'activity'>('dependencies');
    const [statusFilter, setStatusFilter] = useState<'all' | 'pending' | 'in-progress' | 'blocked' | 'resolved'>('all');

    // Get status color
    const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'resolved':
                return 'success';
            case 'in-progress':
                return 'warning';
            case 'blocked':
            case 'open':
                return 'error';
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

    // Get severity color
    const getSeverityColor = (severity: string): 'error' | 'warning' | 'default' => {
        switch (severity) {
            case 'critical':
                return 'error';
            case 'major':
                return 'warning';
            default:
                return 'default';
        }
    };

    // Get impact color
    const getImpactColor = (impact: string): 'error' | 'warning' | 'default' => {
        switch (impact) {
            case 'critical':
                return 'error';
            case 'high':
                return 'warning';
            default:
                return 'default';
        }
    };

    // Get type color
    const getTypeColor = (type: string): 'error' | 'warning' | 'default' => {
        switch (type) {
            case 'deliverable':
            case 'approval':
                return 'error';
            case 'review':
            case 'resource':
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
            case 'blocked':
                return '🚫';
            case 'unblocked':
                return '✅';
            case 'resolved':
                return '🎉';
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

    // Calculate days until due
    const getDaysUntilDue = (dueDate: string): number => {
        const due = new Date(dueDate);
        const now = new Date();
        const diffMs = due.getTime() - now.getTime();
        return Math.ceil(diffMs / 86400000);
    };

    // Filter dependencies
    const filteredDependencies = statusFilter === 'all' ? dependencies : dependencies.filter((d) => d.status === statusFilter);

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Dependency Tracker
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Cross-team dependencies, blockers, and coordination
                    </Typography>
                </Box>
                {onCreateDependency && (
                    <Button variant="primary" size="md" onClick={onCreateDependency}>
                        Create Dependency
                    </Button>
                )}
            </Box>

            {/* Metrics Summary */}
            <Grid columns={4} gap={4}>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Total Dependencies
                        </Typography>
                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100 mt-1">
                            {metrics.totalDependencies}
                        </Typography>
                        <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                            {metrics.activeDependencies} active
                        </Typography>
                    </Box>
                </Card>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Blocked Tasks
                        </Typography>
                        <Typography variant="h5" className={`mt-1 ${metrics.blockedTasks > 0 ? 'text-red-600' : 'text-slate-900 dark:text-neutral-100'}`}>
                            {metrics.blockedTasks}
                        </Typography>
                    </Box>
                </Card>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Avg Resolution
                        </Typography>
                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100 mt-1">
                            {metrics.avgResolutionDays}d
                        </Typography>
                    </Box>
                </Card>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Open Blockers
                        </Typography>
                        <Typography variant="h5" className={`mt-1 ${blockers.filter((b) => b.status === 'open').length > 0 ? 'text-red-600' : 'text-slate-900 dark:text-neutral-100'}`}>
                            {blockers.filter((b) => b.status === 'open').length}
                        </Typography>
                    </Box>
                </Card>
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`Dependencies (${dependencies.length})`} value="dependencies" />
                    <Tab label={`Blockers (${blockers.length})`} value="blockers" />
                    <Tab label={`Coordination (${coordination.length})`} value="coordination" />
                    <Tab label={`Activity (${activities.length})`} value="activity" />
                </Tabs>

                {/* Dependencies Tab */}
                {selectedTab === 'dependencies' && (
                    <Box className="p-4">
                        {/* Status Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Chip label={`All (${dependencies.length})`} color={statusFilter === 'all' ? 'error' : 'default'} onClick={() => setStatusFilter('all')} />
                            <Chip
                                label={`Pending (${dependencies.filter((d) => d.status === 'pending').length})`}
                                color={statusFilter === 'pending' ? 'default' : 'default'}
                                onClick={() => setStatusFilter('pending')}
                            />
                            <Chip
                                label={`In Progress (${dependencies.filter((d) => d.status === 'in-progress').length})`}
                                color={statusFilter === 'in-progress' ? 'warning' : 'default'}
                                onClick={() => setStatusFilter('in-progress')}
                            />
                            <Chip
                                label={`Blocked (${dependencies.filter((d) => d.status === 'blocked').length})`}
                                color={statusFilter === 'blocked' ? 'error' : 'default'}
                                onClick={() => setStatusFilter('blocked')}
                            />
                            <Chip
                                label={`Resolved (${dependencies.filter((d) => d.status === 'resolved').length})`}
                                color={statusFilter === 'resolved' ? 'success' : 'default'}
                                onClick={() => setStatusFilter('resolved')}
                            />
                        </Stack>

                        {/* Dependencies Table */}
                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Task Name</TableCell>
                                    <TableCell>Dependent Team</TableCell>
                                    <TableCell>Dependency Team</TableCell>
                                    <TableCell>Type</TableCell>
                                    <TableCell>Status</TableCell>
                                    <TableCell>Priority</TableCell>
                                    <TableCell>Due Date</TableCell>
                                    <TableCell>Owner</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {filteredDependencies.map((dep) => (
                                    <TableRow
                                        key={dep.id}
                                        className="cursor-pointer hover:bg-slate-50 dark:hover:bg-neutral-800"
                                        onClick={() => onDependencyClick?.(dep.id)}
                                    >
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                {dep.taskName}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {dep.dependentTeam}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {dep.dependencyTeam}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={dep.type} color={getTypeColor(dep.type)} size="small" />
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={dep.status} color={getStatusColor(dep.status)} size="small" />
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={dep.priority} color={getPriorityColor(dep.priority)} size="small" />
                                        </TableCell>
                                        <TableCell>
                                            <Typography
                                                variant="body2"
                                                className={`${getDaysUntilDue(dep.dueDate) < 3 ? 'text-red-600' : getDaysUntilDue(dep.dueDate) < 7 ? 'text-orange-600' : 'text-slate-900 dark:text-neutral-100'}`}
                                            >
                                                {formatDate(dep.dueDate)}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                {dep.owner}
                                            </Typography>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </Box>
                )}

                {/* Blockers Tab */}
                {selectedTab === 'blockers' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Active Blockers
                        </Typography>

                        <Stack spacing={3}>
                            {blockers.map((blocker) => (
                                <Card key={blocker.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onBlockerClick?.(blocker.id)}>
                                    <Box className="p-4">
                                        {/* Blocker Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {blocker.blockedTask}
                                                    </Typography>
                                                    <Chip label={blocker.severity} color={getSeverityColor(blocker.severity)} size="small" />
                                                    <Chip label={blocker.status} color={getStatusColor(blocker.status)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    Blocked Team: {blocker.blockedTeam}
                                                </Typography>
                                            </Box>
                                            <Box className="text-right">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Reported
                                                </Typography>
                                                <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                    {formatDate(blocker.reportedDate)}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Blocker Description */}
                                        <Box className="mb-3">
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                {blocker.blockerDescription}
                                            </Typography>
                                        </Box>

                                        {/* Blocker Details */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={3} gap={3}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Blocker Team
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {blocker.blockerTeam}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Owner
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {blocker.owner}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Est. Resolution
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {blocker.estimatedResolution ? formatDate(blocker.estimatedResolution) : 'TBD'}
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

                {/* Coordination Tab */}
                {selectedTab === 'coordination' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Team Coordination
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {coordination.map((coord) => (
                                <Card key={coord.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onCoordinationClick?.(coord.id)}>
                                    <Box className="p-4">
                                        {/* Coordination Header */}
                                        <Box className="mb-3">
                                            <Box className="flex items-center gap-2 mb-1">
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                    {coord.coordinationType.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')}
                                                </Typography>
                                                <Chip label={coord.frequency} size="small" />
                                            </Box>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {coord.teams.join(' ↔ ')}
                                            </Typography>
                                        </Box>

                                        {/* Effectiveness */}
                                        <Box className="mb-3">
                                            <Box className="flex items-center justify-between mb-1">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Effectiveness
                                                </Typography>
                                                <Typography
                                                    variant="caption"
                                                    className={`${coord.effectiveness >= 80 ? 'text-green-600' : coord.effectiveness >= 60 ? 'text-orange-600' : 'text-red-600'} font-medium`}
                                                >
                                                    {coord.effectiveness}%
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Coordination Details */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={2} gap={2}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Next Scheduled
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {formatDate(coord.nextScheduled)}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Participants
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {coord.participants.length}
                                                    </Typography>
                                                </Box>
                                            </Grid>
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
                                                    Dependency: {activity.dependencyName}
                                                </Typography>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-1">
                                                    Teams: {activity.teams.join(' → ')}
                                                </Typography>
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
export const mockDependencyTrackerData = {
    metrics: {
        totalDependencies: 24,
        activeDependencies: 16,
        blockedTasks: 3,
        avgResolutionDays: 8,
    } as DependencyMetrics,

    dependencies: [
        {
            id: 'dep-1',
            dependentTeam: 'Frontend Team',
            dependencyTeam: 'Backend Team',
            taskName: 'User Authentication API',
            status: 'in-progress',
            priority: 'urgent',
            type: 'deliverable',
            dueDate: '2025-12-15T00:00:00Z',
            owner: 'Sarah Chen',
            impact: 'critical',
        },
        {
            id: 'dep-2',
            dependentTeam: 'Mobile Team',
            dependencyTeam: 'Design Team',
            taskName: 'App Redesign Mockups',
            status: 'blocked',
            priority: 'high',
            type: 'review',
            dueDate: '2025-12-18T00:00:00Z',
            owner: 'Mike Rodriguez',
            blockers: ['Design resource unavailable'],
            impact: 'high',
        },
        {
            id: 'dep-3',
            dependentTeam: 'Data Team',
            dependencyTeam: 'Infrastructure Team',
            taskName: 'Analytics Database Setup',
            status: 'pending',
            priority: 'normal',
            type: 'resource',
            dueDate: '2025-12-22T00:00:00Z',
            owner: 'Emily Johnson',
            impact: 'medium',
        },
    ] as CrossTeamDependency[],

    blockers: [
        {
            id: 'blocker-1',
            blockedTask: 'Mobile App Release',
            blockedTeam: 'Mobile Team',
            blockerDescription: 'Waiting for design mockups approval from Design Team',
            blockerTeam: 'Design Team',
            severity: 'critical',
            reportedDate: '2025-12-08T00:00:00Z',
            owner: 'Mike Rodriguez',
            status: 'open',
            estimatedResolution: '2025-12-13T00:00:00Z',
        },
        {
            id: 'blocker-2',
            blockedTask: 'API Integration',
            blockedTeam: 'Frontend Team',
            blockerDescription: 'Backend API not yet deployed to staging environment',
            blockerTeam: 'Backend Team',
            severity: 'major',
            reportedDate: '2025-12-09T00:00:00Z',
            owner: 'Sarah Chen',
            status: 'in-progress',
            estimatedResolution: '2025-12-12T00:00:00Z',
        },
        {
            id: 'blocker-3',
            blockedTask: 'Performance Testing',
            blockedTeam: 'QA Team',
            blockerDescription: 'Missing test data from Data Team',
            blockerTeam: 'Data Team',
            severity: 'minor',
            reportedDate: '2025-12-10T00:00:00Z',
            owner: 'David Park',
            status: 'resolved',
        },
    ] as Blocker[],

    coordination: [
        {
            id: 'coord-1',
            teams: ['Frontend Team', 'Backend Team'],
            coordinationType: 'sync-meeting',
            frequency: 'daily',
            nextScheduled: '2025-12-12T10:00:00Z',
            participants: ['Sarah Chen', 'Mike Rodriguez', 'Emily Johnson'],
            effectiveness: 85,
        },
        {
            id: 'coord-2',
            teams: ['Design Team', 'Mobile Team', 'Frontend Team'],
            coordinationType: 'joint-project',
            frequency: 'weekly',
            nextScheduled: '2025-12-16T14:00:00Z',
            participants: ['Mike Rodriguez', 'Lisa Thompson', 'David Park'],
            effectiveness: 72,
        },
        {
            id: 'coord-3',
            teams: ['Data Team', 'Infrastructure Team'],
            coordinationType: 'knowledge-transfer',
            frequency: 'biweekly',
            nextScheduled: '2025-12-20T15:00:00Z',
            participants: ['Emily Johnson', 'James Wilson'],
            effectiveness: 90,
        },
    ] as TeamCoordination[],

    activities: [
        {
            id: 'act-1',
            activityType: 'blocked',
            dependencyName: 'App Redesign Mockups',
            teams: ['Mobile Team', 'Design Team'],
            description: 'Dependency blocked due to resource unavailability',
            user: 'Mike Rodriguez',
            timestamp: '2025-12-11T09:30:00Z',
        },
        {
            id: 'act-2',
            activityType: 'updated',
            dependencyName: 'User Authentication API',
            teams: ['Frontend Team', 'Backend Team'],
            description: 'Progress updated to 75% complete',
            user: 'Sarah Chen',
            timestamp: '2025-12-11T08:15:00Z',
        },
        {
            id: 'act-3',
            activityType: 'resolved',
            dependencyName: 'Performance Test Data',
            teams: ['QA Team', 'Data Team'],
            description: 'Dependency resolved - test data delivered',
            user: 'David Park',
            timestamp: '2025-12-10T16:45:00Z',
        },
    ] as DependencyActivity[],
};

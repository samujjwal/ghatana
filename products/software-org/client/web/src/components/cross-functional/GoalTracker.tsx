/**
 * Goal Tracker Component
 *
 * Component for tracking organizational goals and OKRs (Objectives and Key Results),
 * monitoring progress, alignment, and team contributions.
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
    LinearProgress,
} from '@ghatana/ui';

/**
 * Goal metrics
 */
export interface GoalMetrics {
    totalGoals: number;
    activeGoals: number;
    completionRate: number; // 0-100
    avgProgress: number; // 0-100
}

/**
 * Organizational goal (OKR)
 */
export interface OrganizationalGoal {
    id: string;
    objective: string;
    description: string;
    level: 'company' | 'department' | 'team' | 'individual';
    owner: string;
    status: 'not-started' | 'on-track' | 'at-risk' | 'behind' | 'completed';
    priority: 'urgent' | 'high' | 'normal' | 'low';
    startDate: string;
    targetDate: string;
    progress: number; // 0-100
    keyResults: string[];
    contributingTeams: string[];
    alignment?: string; // Parent goal ID
}

/**
 * Key result
 */
export interface KeyResult {
    id: string;
    goalId: string;
    goalObjective: string;
    keyResult: string;
    metric: string;
    targetValue: number;
    currentValue: number;
    unit: string;
    status: 'not-started' | 'on-track' | 'at-risk' | 'behind' | 'completed';
    lastUpdated: string;
    owner: string;
}

/**
 * Team contribution
 */
export interface TeamContribution {
    id: string;
    teamName: string;
    contributingGoals: number;
    completedGoals: number;
    totalProgress: number; // 0-100
    impactScore: number; // 0-100
    topContributors: string[];
}

/**
 * Goal activity
 */
export interface GoalActivity {
    id: string;
    activityType: 'created' | 'updated' | 'completed' | 'at-risk' | 'aligned';
    goalName: string;
    description: string;
    user: string;
    timestamp: string;
    previousValue?: number;
    newValue?: number;
}

/**
 * Goal Tracker Props
 */
export interface GoalTrackerProps {
    /** Goal metrics */
    metrics: GoalMetrics;
    /** Organizational goals */
    goals: OrganizationalGoal[];
    /** Key results */
    keyResults: KeyResult[];
    /** Team contributions */
    teamContributions: TeamContribution[];
    /** Goal activities */
    activities: GoalActivity[];
    /** Callback when goal is clicked */
    onGoalClick?: (goalId: string) => void;
    /** Callback when key result is clicked */
    onKeyResultClick?: (keyResultId: string) => void;
    /** Callback when team is clicked */
    onTeamClick?: (teamId: string) => void;
    /** Callback when activity is clicked */
    onActivityClick?: (activityId: string) => void;
    /** Callback when create goal is clicked */
    onCreateGoal?: () => void;
}

/**
 * Goal Tracker Component
 *
 * Provides comprehensive goal tracking with:
 * - Goal summary metrics
 * - Organizational goals (OKRs) with progress tracking
 * - Key result monitoring
 * - Team contribution analysis
 * - Activity stream for goal updates
 * - Tab-based navigation (Goals, Key Results, Teams, Activity)
 *
 * Reuses @ghatana/ui components:
 * - Card (goal cards, key result cards, team cards)
 * - LinearProgress (progress bars for goals, key results, teams)
 * - Chip (status, priority, level indicators)
 *
 * @example
 * ```tsx
 * <GoalTracker
 *   metrics={goalMetrics}
 *   goals={organizationalGoals}
 *   keyResults={allKeyResults}
 *   teamContributions={teamData}
 *   activities={recentActivities}
 *   onGoalClick={(id) => navigate(`/goals/${id}`)}
 *   onCreateGoal={() => openCreateDialog()}
 * />
 * ```
 */
export const GoalTracker: React.FC<GoalTrackerProps> = ({
    metrics,
    goals,
    keyResults,
    teamContributions,
    activities,
    onGoalClick,
    onKeyResultClick,
    onTeamClick,
    onActivityClick,
    onCreateGoal,
}) => {
    const [selectedTab, setSelectedTab] = useState<'goals' | 'key-results' | 'teams' | 'activity'>('goals');
    const [statusFilter, setStatusFilter] = useState<'all' | 'not-started' | 'on-track' | 'at-risk' | 'behind' | 'completed'>('all');
    const [levelFilter, setLevelFilter] = useState<'all' | 'company' | 'department' | 'team' | 'individual'>('all');

    // Get status color
    const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'completed':
            case 'on-track':
                return 'success';
            case 'at-risk':
                return 'warning';
            case 'behind':
                return 'error';
            case 'not-started':
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

    // Get level color
    const getLevelColor = (level: string): 'error' | 'warning' | 'default' => {
        switch (level) {
            case 'company':
                return 'error';
            case 'department':
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
                return '🎉';
            case 'at-risk':
                return '⚠️';
            case 'aligned':
                return '🔗';
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

    // Calculate days until target
    const getDaysUntilTarget = (targetDate: string): number => {
        const target = new Date(targetDate);
        const now = new Date();
        const diffMs = target.getTime() - now.getTime();
        return Math.ceil(diffMs / 86400000);
    };

    // Filter goals
    let filteredGoals = statusFilter === 'all' ? goals : goals.filter((g) => g.status === statusFilter);
    filteredGoals = levelFilter === 'all' ? filteredGoals : filteredGoals.filter((g) => g.level === levelFilter);

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Goal Tracker
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Organizational goals, OKRs, and team contributions
                    </Typography>
                </Box>
                {onCreateGoal && (
                    <Button variant="primary" size="md" onClick={onCreateGoal}>
                        Create Goal
                    </Button>
                )}
            </Box>

            {/* Metrics Summary */}
            <Grid columns={4} gap={4}>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Total Goals
                        </Typography>
                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100 mt-1">
                            {metrics.totalGoals}
                        </Typography>
                        <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                            {metrics.activeGoals} active
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
                            Avg Progress
                        </Typography>
                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100 mt-1">
                            {metrics.avgProgress}%
                        </Typography>
                        <Box className="mt-2">
                            <LinearProgress
                                variant="determinate"
                                value={metrics.avgProgress}
                                color={metrics.avgProgress >= 75 ? 'success' : metrics.avgProgress >= 50 ? 'warning' : 'error'}
                            />
                        </Box>
                    </Box>
                </Card>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            At Risk Goals
                        </Typography>
                        <Typography variant="h5" className={`mt-1 ${goals.filter((g) => g.status === 'at-risk' || g.status === 'behind').length > 0 ? 'text-red-600' : 'text-slate-900 dark:text-neutral-100'}`}>
                            {goals.filter((g) => g.status === 'at-risk' || g.status === 'behind').length}
                        </Typography>
                    </Box>
                </Card>
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`Goals (${goals.length})`} value="goals" />
                    <Tab label={`Key Results (${keyResults.length})`} value="key-results" />
                    <Tab label={`Teams (${teamContributions.length})`} value="teams" />
                    <Tab label={`Activity (${activities.length})`} value="activity" />
                </Tabs>

                {/* Goals Tab */}
                {selectedTab === 'goals' && (
                    <Box className="p-4">
                        {/* Status Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Chip label={`All (${goals.length})`} color={statusFilter === 'all' ? 'error' : 'default'} onClick={() => setStatusFilter('all')} />
                            <Chip
                                label={`On Track (${goals.filter((g) => g.status === 'on-track').length})`}
                                color={statusFilter === 'on-track' ? 'success' : 'default'}
                                onClick={() => setStatusFilter('on-track')}
                            />
                            <Chip
                                label={`At Risk (${goals.filter((g) => g.status === 'at-risk').length})`}
                                color={statusFilter === 'at-risk' ? 'warning' : 'default'}
                                onClick={() => setStatusFilter('at-risk')}
                            />
                            <Chip
                                label={`Behind (${goals.filter((g) => g.status === 'behind').length})`}
                                color={statusFilter === 'behind' ? 'error' : 'default'}
                                onClick={() => setStatusFilter('behind')}
                            />
                            <Chip
                                label={`Completed (${goals.filter((g) => g.status === 'completed').length})`}
                                color={statusFilter === 'completed' ? 'success' : 'default'}
                                onClick={() => setStatusFilter('completed')}
                            />
                        </Stack>

                        {/* Level Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 self-center">
                                Level:
                            </Typography>
                            <Chip label="All" color={levelFilter === 'all' ? 'error' : 'default'} size="small" onClick={() => setLevelFilter('all')} />
                            <Chip label="Company" color={levelFilter === 'company' ? 'error' : 'default'} size="small" onClick={() => setLevelFilter('company')} />
                            <Chip label="Department" color={levelFilter === 'department' ? 'warning' : 'default'} size="small" onClick={() => setLevelFilter('department')} />
                            <Chip label="Team" color={levelFilter === 'team' ? 'default' : 'default'} size="small" onClick={() => setLevelFilter('team')} />
                        </Stack>

                        {/* Goal List */}
                        <Stack spacing={3}>
                            {filteredGoals.map((goal) => (
                                <Card key={goal.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onGoalClick?.(goal.id)}>
                                    <Box className="p-4">
                                        {/* Goal Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {goal.objective}
                                                    </Typography>
                                                    <Chip label={goal.level} color={getLevelColor(goal.level)} size="small" />
                                                    <Chip label={goal.status} color={getStatusColor(goal.status)} size="small" />
                                                    <Chip label={goal.priority} color={getPriorityColor(goal.priority)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {goal.description}
                                                </Typography>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Owner: {goal.owner}
                                                </Typography>
                                            </Box>
                                            <Box className="text-right">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Due in
                                                </Typography>
                                                <Typography
                                                    variant="h6"
                                                    className={getDaysUntilTarget(goal.targetDate) < 7 ? 'text-red-600' : getDaysUntilTarget(goal.targetDate) < 30 ? 'text-orange-600' : 'text-slate-900 dark:text-neutral-100'}
                                                >
                                                    {getDaysUntilTarget(goal.targetDate)}d
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
                                                    {goal.progress}%
                                                </Typography>
                                            </Box>
                                            <LinearProgress
                                                variant="determinate"
                                                value={goal.progress}
                                                color={goal.status === 'completed' || goal.status === 'on-track' ? 'success' : goal.status === 'at-risk' ? 'warning' : 'error'}
                                            />
                                        </Box>

                                        {/* Goal Details */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={2} gap={3}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Key Results ({goal.keyResults.length})
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {goal.keyResults.slice(0, 2).join(', ')}
                                                        {goal.keyResults.length > 2 && ` +${goal.keyResults.length - 2} more`}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Contributing Teams ({goal.contributingTeams.length})
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {goal.contributingTeams.slice(0, 2).join(', ')}
                                                        {goal.contributingTeams.length > 2 && ` +${goal.contributingTeams.length - 2} more`}
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

                {/* Key Results Tab */}
                {selectedTab === 'key-results' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Key Results
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {keyResults.map((kr) => (
                                <Card key={kr.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onKeyResultClick?.(kr.id)}>
                                    <Box className="p-4">
                                        {/* Key Result Header */}
                                        <Box className="mb-3">
                                            <Box className="flex items-center gap-2 mb-1">
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                    {kr.keyResult}
                                                </Typography>
                                                <Chip label={kr.status} color={getStatusColor(kr.status)} size="small" />
                                            </Box>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {kr.goalObjective}
                                            </Typography>
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                Owner: {kr.owner}
                                            </Typography>
                                        </Box>

                                        {/* Metric Progress */}
                                        <Box className="mb-3">
                                            <Box className="flex items-center justify-between mb-1">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    {kr.metric}
                                                </Typography>
                                                <Typography variant="caption" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                    {kr.currentValue} / {kr.targetValue} {kr.unit}
                                                </Typography>
                                            </Box>
                                            <LinearProgress
                                                variant="determinate"
                                                value={(kr.currentValue / kr.targetValue) * 100}
                                                color={(kr.currentValue / kr.targetValue) * 100 >= 75 ? 'success' : (kr.currentValue / kr.targetValue) * 100 >= 50 ? 'warning' : 'error'}
                                            />
                                        </Box>

                                        {/* Last Updated */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                Last Updated
                                            </Typography>
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                {formatDate(kr.lastUpdated)}
                                            </Typography>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}

                {/* Teams Tab */}
                {selectedTab === 'teams' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Team Contributions
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {teamContributions.map((team) => (
                                <Card key={team.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onTeamClick?.(team.id)}>
                                    <Box className="p-4">
                                        {/* Team Header */}
                                        <Box className="mb-3">
                                            <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-1">
                                                {team.teamName}
                                            </Typography>
                                        </Box>

                                        {/* Total Progress */}
                                        <Box className="mb-3">
                                            <Box className="flex items-center justify-between mb-1">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Total Progress
                                                </Typography>
                                                <Typography variant="caption" className="text-slate-900 dark:text-neutral-100">
                                                    {team.totalProgress}%
                                                </Typography>
                                            </Box>
                                            <LinearProgress
                                                variant="determinate"
                                                value={team.totalProgress}
                                                color={team.totalProgress >= 75 ? 'success' : team.totalProgress >= 50 ? 'warning' : 'error'}
                                            />
                                        </Box>

                                        {/* Team Details */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={3} gap={2}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Contributing
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {team.contributingGoals}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Completed
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {team.completedGoals}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Impact
                                                    </Typography>
                                                    <Typography variant="body2" className={`${team.impactScore >= 80 ? 'text-green-600' : team.impactScore >= 60 ? 'text-orange-600' : 'text-red-600'} font-medium`}>
                                                        {team.impactScore}%
                                                    </Typography>
                                                </Box>
                                            </Grid>
                                        </Box>

                                        {/* Top Contributors */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 mb-2 block">
                                                Top Contributors
                                            </Typography>
                                            <Stack direction="row" spacing={1} className="flex-wrap">
                                                {team.topContributors.slice(0, 3).map((contributor, i) => (
                                                    <Chip key={i} label={contributor} size="small" />
                                                ))}
                                                {team.topContributors.length > 3 && <Chip label={`+${team.topContributors.length - 3}`} size="small" />}
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
                                                    Goal: {activity.goalName}
                                                </Typography>
                                                {activity.previousValue !== undefined && activity.newValue !== undefined && (
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        Progress: {activity.previousValue}% → {activity.newValue}%
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
export const mockGoalTrackerData = {
    metrics: {
        totalGoals: 18,
        activeGoals: 12,
        completionRate: 75,
        avgProgress: 68,
    } as GoalMetrics,

    goals: [
        {
            id: 'goal-1',
            objective: 'Increase Platform Adoption',
            description: 'Grow active user base by 50% through improved onboarding and feature discovery',
            level: 'company',
            owner: 'Sarah Johnson',
            status: 'on-track',
            priority: 'urgent',
            startDate: '2025-10-01T00:00:00Z',
            targetDate: '2025-12-31T00:00:00Z',
            progress: 72,
            keyResults: ['50K active users', '4.5 star rating', '80% retention'],
            contributingTeams: ['Product', 'Engineering', 'Marketing'],
        },
        {
            id: 'goal-2',
            objective: 'Improve System Performance',
            description: 'Reduce API latency and increase system reliability to world-class levels',
            level: 'department',
            owner: 'Mike Chen',
            status: 'at-risk',
            priority: 'high',
            startDate: '2025-11-01T00:00:00Z',
            targetDate: '2025-12-20T00:00:00Z',
            progress: 45,
            keyResults: ['<100ms latency', '99.9% uptime', '50% cost reduction'],
            contributingTeams: ['Platform Engineering', 'Infrastructure'],
        },
        {
            id: 'goal-3',
            objective: 'Launch AI Analytics Feature',
            description: 'Deliver AI-powered analytics to provide actionable insights for users',
            level: 'team',
            owner: 'Emily Davis',
            status: 'on-track',
            priority: 'normal',
            startDate: '2025-11-15T00:00:00Z',
            targetDate: '2026-01-31T00:00:00Z',
            progress: 65,
            keyResults: ['MVP launch', '1000 beta users', '85% satisfaction'],
            contributingTeams: ['Data Science', 'Product Design'],
        },
    ] as OrganizationalGoal[],

    keyResults: [
        {
            id: 'kr-1',
            goalId: 'goal-1',
            goalObjective: 'Increase Platform Adoption',
            keyResult: 'Reach 50,000 active users',
            metric: 'Active Users',
            targetValue: 50000,
            currentValue: 36000,
            unit: 'users',
            status: 'on-track',
            lastUpdated: '2025-12-10T00:00:00Z',
            owner: 'Sarah Johnson',
        },
        {
            id: 'kr-2',
            goalId: 'goal-1',
            goalObjective: 'Increase Platform Adoption',
            keyResult: 'Achieve 4.5+ star rating',
            metric: 'App Rating',
            targetValue: 4.5,
            currentValue: 4.3,
            unit: 'stars',
            status: 'on-track',
            lastUpdated: '2025-12-09T00:00:00Z',
            owner: 'Product Team',
        },
        {
            id: 'kr-3',
            goalId: 'goal-2',
            goalObjective: 'Improve System Performance',
            keyResult: 'Reduce API latency to <100ms',
            metric: 'P95 Latency',
            targetValue: 100,
            currentValue: 145,
            unit: 'ms',
            status: 'at-risk',
            lastUpdated: '2025-12-11T00:00:00Z',
            owner: 'Mike Chen',
        },
        {
            id: 'kr-4',
            goalId: 'goal-3',
            goalObjective: 'Launch AI Analytics Feature',
            keyResult: 'Acquire 1000 beta users',
            metric: 'Beta Users',
            targetValue: 1000,
            currentValue: 650,
            unit: 'users',
            status: 'on-track',
            lastUpdated: '2025-12-10T00:00:00Z',
            owner: 'Emily Davis',
        },
    ] as KeyResult[],

    teamContributions: [
        {
            id: 'team-1',
            teamName: 'Platform Engineering',
            contributingGoals: 5,
            completedGoals: 2,
            totalProgress: 78,
            impactScore: 85,
            topContributors: ['Mike Chen', 'Sarah Chen', 'David Park'],
        },
        {
            id: 'team-2',
            teamName: 'Product Design',
            contributingGoals: 4,
            completedGoals: 3,
            totalProgress: 82,
            impactScore: 90,
            topContributors: ['Lisa Thompson', 'Mike Rodriguez'],
        },
        {
            id: 'team-3',
            teamName: 'Data Science',
            contributingGoals: 3,
            completedGoals: 1,
            totalProgress: 65,
            impactScore: 75,
            topContributors: ['Emily Johnson', 'James Wilson'],
        },
    ] as TeamContribution[],

    activities: [
        {
            id: 'act-1',
            activityType: 'updated',
            goalName: 'Increase Platform Adoption',
            description: 'Progress updated for Q4 goal',
            user: 'Sarah Johnson',
            timestamp: '2025-12-11T09:30:00Z',
            previousValue: 68,
            newValue: 72,
        },
        {
            id: 'act-2',
            activityType: 'at-risk',
            goalName: 'Improve System Performance',
            description: 'Goal marked as at-risk due to latency concerns',
            user: 'Mike Chen',
            timestamp: '2025-12-11T08:15:00Z',
        },
        {
            id: 'act-3',
            activityType: 'completed',
            goalName: 'Q4 Design System Update',
            description: 'Design system overhaul completed successfully',
            user: 'Lisa Thompson',
            timestamp: '2025-12-10T16:45:00Z',
            previousValue: 95,
            newValue: 100,
        },
    ] as GoalActivity[],
};

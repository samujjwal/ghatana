/**
 * IC (Individual Contributor) Dashboard Component
 *
 * <p><b>Purpose</b><br>
 * Unified persona dashboard for ICs (human or agent) showing work items,
 * capacity, growth, and actionable quick actions. Integrates with DevSecOps
 * flow for complete task execution from dashboard.
 *
 * <p><b>Features</b><br>
 * - Work items with DevSecOps phase indicators
 * - Work session management (start/end)
 * - Capacity and availability tracking
 * - Growth goals and progress
 * - Quick actions with real functionality
 * - AI suggestions
 *
 * @doc.type component
 * @doc.purpose Unified persona dashboard for ICs
 * @doc.layer product
 * @doc.pattern Dashboard
 */

import { useCallback } from 'react';
import { useNavigate } from 'react-router';
import { Grid, Card, KpiCard, Box, Button, Chip } from '@ghatana/ui';
import {
    usePersonaDashboard,
    useWorkSession,
    useAvailabilityStatus,
} from '@/hooks/useUnifiedPersona';
import { PersonaFlowStrip } from '@/shared/components/PersonaFlowStrip';
import type { DevSecOpsPhaseId } from '@/shared/types/org';

/**
 * Phase labels for display
 */
const PHASE_LABELS: Record<DevSecOpsPhaseId, string> = {
    intake: 'Intake',
    plan: 'Plan',
    build: 'Build',
    verify: 'Verify',
    review: 'Review',
    staging: 'Staging',
    deploy: 'Deploy',
    operate: 'Operate',
    learn: 'Learn',
};

/**
 * Mock meetings (to be replaced with real data)
 */
const mockMeetings = [
    { id: 'm1', title: 'Daily Standup', time: '10:00 AM', duration: '15 min' },
    { id: 'm2', title: '1:1 with Manager', time: '2:00 PM', duration: '30 min' },
    { id: 'm3', title: 'Sprint Planning', time: '4:00 PM', duration: '1 hour' },
];

export function ICDashboard() {
    const navigate = useNavigate();
    const { persona, workItems, goals, isLoading } = usePersonaDashboard();
    const { session, isActive: isSessionActive, startSession, endSession, isStarting } = useWorkSession();
    const { status: availabilityStatus, updateStatus } = useAvailabilityStatus();

    // Computed values
    const inProgressCount = workItems.filter((t) => t.status === 'in-progress').length;
    const doneCount = workItems.filter((t) => t.status === 'done').length;
    const highPriorityCount = workItems.filter((t) => t.priority === 'p0' || t.priority === 'p1').length;
    const activeGoalsCount = goals.filter((g) => g.status === 'in-progress').length;

    // Handlers
    const handleTaskClick = useCallback((taskId: string) => {
        navigate(`/ic/work-items/${taskId}`);
    }, [navigate]);

    const handleViewAllTasks = useCallback(() => {
        navigate('/ic/tasks');
    }, [navigate]);

    const handleStartSession = useCallback(() => {
        startSession(undefined);
    }, [startSession]);

    const handleEndSession = useCallback(() => {
        if (session?.id) {
            endSession(session.id);
        }
    }, [endSession, session]);

    const handleRequestTimeOff = useCallback(() => {
        navigate('/ic/time-off');
    }, [navigate]);

    const handleUpdateStatus = useCallback((newStatus: 'available' | 'busy' | 'away') => {
        updateStatus({ status: newStatus });
    }, [updateStatus]);

    const handleViewGrowth = useCallback(() => {
        navigate('/ic/growth');
    }, [navigate]);

    const getStatusColor = (status: string): 'primary' | 'default' | 'success' | 'warning' | 'danger' => {
        switch (status) {
            case 'in-progress':
                return 'primary';
            case 'ready':
                return 'warning';
            case 'done':
                return 'success';
            case 'blocked':
                return 'danger';
            default:
                return 'default';
        }
    };

    const getPriorityColor = (priority: string): 'primary' | 'default' | 'success' | 'warning' | 'danger' => {
        switch (priority) {
            case 'p0':
                return 'danger';
            case 'p1':
                return 'warning';
            case 'p2':
                return 'primary';
            default:
                return 'default';
        }
    };

    const getAvailabilityColor = (status: string): string => {
        switch (status) {
            case 'available':
                return 'bg-green-500';
            case 'busy':
                return 'bg-yellow-500';
            case 'away':
                return 'bg-orange-500';
            case 'offline':
            case 'maintenance':
                return 'bg-gray-500';
            default:
                return 'bg-gray-400';
        }
    };

    // Loading state
    if (isLoading) {
        return (
            <Box className="p-6 flex items-center justify-center min-h-[400px]">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4" />
                    <p className="text-slate-600 dark:text-neutral-400">Loading dashboard...</p>
                </div>
            </Box>
        );
    }

    return (
        <Box className="p-6 space-y-6">
            {/* Welcome Header with Status */}
            <div className="mb-8 flex items-start justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
                        Welcome back, {persona?.name || 'Developer'}
                    </h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-2">
                        {isSessionActive ? (
                            <span className="flex items-center gap-2">
                                <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse" />
                                Work session active • {session?.productiveTimeMinutes || 0} min productive time
                            </span>
                        ) : (
                            "Here's your day at a glance"
                        )}
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <div className="flex items-center gap-2">
                        <span className={`w-3 h-3 rounded-full ${getAvailabilityColor(availabilityStatus)}`} />
                        <span className="text-sm text-slate-600 dark:text-neutral-400 capitalize">
                            {availabilityStatus}
                        </span>
                    </div>
                    {persona?.capacity && (
                        <div className="text-sm text-slate-500 dark:text-neutral-500">
                            {persona.capacity.currentUtilization}% utilized
                        </div>
                    )}
                </div>
            </div>

            {/* DevSecOps Flow Strip */}
            <PersonaFlowStrip
                personaId="engineer"
                onPhaseClick={(phase) => navigate(`/ic/tasks?phase=${phase}`)}
            />

            {/* Top KPI Cards */}
            <Grid columns={4} gap={4}>
                <KpiCard
                    label="My Tasks"
                    value={workItems.length}
                    description="Active tasks"
                    status="healthy"
                />

                <KpiCard
                    label="In Progress"
                    value={inProgressCount}
                    description="Currently working"
                    status={inProgressCount > 3 ? 'warning' : 'healthy'}
                />

                <KpiCard
                    label="High Priority"
                    value={highPriorityCount}
                    description="P0 & P1 items"
                    status={highPriorityCount > 2 ? 'critical' : 'healthy'}
                />

                <KpiCard
                    label="Completed"
                    value={doneCount}
                    description="This week"
                    trend={{ direction: 'up', value: 2 }}
                    status="healthy"
                />
            </Grid>

            {/* My Tasks with DevSecOps Phase */}
            <Card title="My Tasks" subtitle="Active work items by DevSecOps phase">
                <Box className="p-4">
                    <div className="space-y-3">
                        {workItems.slice(0, 5).map((task) => (
                            <div
                                key={task.id}
                                onClick={() => handleTaskClick(task.id)}
                                className="p-4 border border-slate-200 dark:border-neutral-600 rounded-lg hover:border-blue-400 dark:hover:border-blue-500 hover:shadow-md transition-all cursor-pointer"
                            >
                                <div className="flex items-start justify-between mb-2">
                                    <div className="flex-1">
                                        <h4 className="font-semibold text-slate-900 dark:text-neutral-200">
                                            {task.title}
                                        </h4>
                                        <p className="text-sm text-slate-500 dark:text-neutral-500 mt-1">
                                            {task.description}
                                        </p>
                                    </div>
                                    <div className="flex gap-2 ml-4">
                                        <Chip
                                            label={PHASE_LABELS[task.phase]}
                                            tone="primary"
                                            size="sm"
                                        />
                                        <Chip
                                            label={task.status.replace('-', ' ')}
                                            tone={getStatusColor(task.status)}
                                            size="sm"
                                        />
                                        <Chip
                                            label={task.priority.toUpperCase()}
                                            tone={getPriorityColor(task.priority)}
                                            size="sm"
                                        />
                                    </div>
                                </div>
                                <div className="flex items-center justify-between text-sm text-slate-600 dark:text-neutral-400">
                                    <span>Due: {task.dueDate || 'No due date'}</span>
                                    {task.estimatedHours && (
                                        <span>
                                            {task.loggedHours || 0}h / {task.estimatedHours}h logged
                                        </span>
                                    )}
                                </div>
                            </div>
                        ))}
                    </div>
                </Box>

                <Box className="p-4 border-t border-slate-200 dark:border-neutral-700">
                    <Button variant="solid" size="sm" fullWidth onClick={handleViewAllTasks}>
                        View All Tasks →
                    </Button>
                </Box>
            </Card>

            {/* Today's Schedule & Growth */}
            <Grid columns={2} gap={4}>
                {/* Today's Schedule */}
                <Card title="Today's Schedule" subtitle="Upcoming meetings">
                    <Box className="p-4">
                        <div className="space-y-3">
                            {mockMeetings.map((meeting) => (
                                <div
                                    key={meeting.id}
                                    className="flex items-center justify-between p-3 bg-slate-50 dark:bg-neutral-800 rounded-lg"
                                >
                                    <div>
                                        <p className="font-medium text-slate-900 dark:text-neutral-200">
                                            {meeting.title}
                                        </p>
                                        <p className="text-sm text-slate-600 dark:text-neutral-400">
                                            {meeting.duration}
                                        </p>
                                    </div>
                                    <div className="text-right">
                                        <p className="font-semibold text-blue-600">{meeting.time}</p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </Box>

                    <Box className="p-4 border-t border-slate-200 dark:border-neutral-700">
                        <Button variant="outline" size="sm" fullWidth onClick={() => navigate('/ic/tasks')}>
                            View Full Calendar →
                        </Button>
                    </Box>
                </Card>

                {/* Growth Goals */}
                <Card title="Growth Goals" subtitle={`${activeGoalsCount} active goals`}>
                    <Box className="p-4">
                        <div className="space-y-3">
                            {goals.slice(0, 3).map((goal) => (
                                <div key={goal.id} className="p-3 bg-slate-50 dark:bg-neutral-800 rounded-lg">
                                    <div className="flex items-center justify-between mb-2">
                                        <p className="font-medium text-slate-900 dark:text-neutral-200">
                                            {goal.title}
                                        </p>
                                        <span className="text-sm text-slate-500 dark:text-neutral-500">
                                            {goal.progress}%
                                        </span>
                                    </div>
                                    <div className="w-full bg-slate-200 dark:bg-neutral-700 rounded-full h-2">
                                        <div
                                            className="bg-blue-600 h-2 rounded-full transition-all"
                                            style={{ width: `${goal.progress}%` }}
                                        />
                                    </div>
                                    <p className="text-xs text-slate-500 dark:text-neutral-500 mt-2">
                                        Target: {goal.targetDate}
                                    </p>
                                </div>
                            ))}
                        </div>
                    </Box>

                    <Box className="p-4 border-t border-slate-200 dark:border-neutral-700">
                        <Button variant="outline" size="sm" fullWidth onClick={handleViewGrowth}>
                            View Growth Plan →
                        </Button>
                    </Box>
                </Card>
            </Grid>

            {/* Capacity & Metrics */}
            {persona?.capacity && (
                <Card title="Capacity Overview" subtitle="Work allocation">
                    <Box className="p-4">
                        <Grid columns={3} gap={4}>
                            <div className="p-4 bg-green-50 dark:bg-green-600/20 rounded-lg">
                                <p className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Available</p>
                                <p className="text-2xl font-bold text-green-700 dark:text-green-400">
                                    {persona.capacity.available}%
                                </p>
                                <p className="text-xs text-green-600 dark:text-green-400 mt-1">
                                    {persona.capacity.dailyLimit}h daily limit
                                </p>
                            </div>

                            <div className="p-4 bg-blue-50 dark:bg-blue-600/20 rounded-lg">
                                <p className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Allocated</p>
                                <p className="text-2xl font-bold text-blue-700 dark:text-blue-400">
                                    {persona.capacity.allocated}%
                                </p>
                                <p className="text-xs text-blue-600 dark:text-blue-400 mt-1">
                                    {workItems.length} active items
                                </p>
                            </div>

                            <div className="p-4 bg-purple-50 dark:bg-purple-600/20 rounded-lg">
                                <p className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Utilization</p>
                                <p className="text-2xl font-bold text-purple-700 dark:text-purple-400">
                                    {persona.capacity.currentUtilization}%
                                </p>
                                <p className="text-xs text-purple-600 dark:text-purple-400 mt-1">
                                    {persona.capacity.reservedForUrgent}% reserved for urgent
                                </p>
                            </div>
                        </Grid>
                    </Box>
                </Card>
            )}

            {/* Quick Actions */}
            <Card title="Quick Actions">
                <Box className="p-4">
                    <Grid columns={4} gap={3}>
                        {isSessionActive ? (
                            <Button
                                variant="outline"
                                size="md"
                                fullWidth
                                onClick={handleEndSession}
                            >
                                🛑 End Work Session
                            </Button>
                        ) : (
                            <Button
                                variant="solid"
                                size="md"
                                fullWidth
                                onClick={handleStartSession}
                                disabled={isStarting}
                            >
                                {isStarting ? '⏳ Starting...' : '▶️ Start Work Session'}
                            </Button>
                        )}
                        <Button variant="outline" size="md" fullWidth onClick={handleRequestTimeOff}>
                            📅 Request Time Off
                        </Button>
                        <Button
                            variant="outline"
                            size="md"
                            fullWidth
                            onClick={() => handleUpdateStatus(availabilityStatus === 'available' ? 'busy' : 'available')}
                        >
                            {availabilityStatus === 'available' ? '🔴 Set Busy' : '🟢 Set Available'}
                        </Button>
                        <Button variant="outline" size="md" fullWidth onClick={handleViewGrowth}>
                            📈 View Growth
                        </Button>
                    </Grid>
                </Box>
            </Card>

            {/* AI Suggestions */}
            <Card title="AI Suggestions" subtitle="Personalized recommendations">
                <Box className="p-4">
                    <div className="space-y-2">
                        {highPriorityCount > 0 && (
                            <div className="p-3 bg-blue-50 dark:bg-indigo-600/30 border-l-4 border-blue-500 rounded">
                                <p className="text-sm text-slate-900 dark:text-neutral-200">
                                    💡 <strong>Tip:</strong> You have {highPriorityCount} high-priority tasks.
                                    Consider focusing on these first.
                                </p>
                            </div>
                        )}

                        {doneCount > 5 && (
                            <div className="p-3 bg-green-50 dark:bg-green-600/30 border-l-4 border-green-500 rounded">
                                <p className="text-sm text-slate-900 dark:text-neutral-200">
                                    ✨ <strong>Well done!</strong> You've completed {doneCount} tasks this week,
                                    above team average.
                                </p>
                            </div>
                        )}

                        {persona?.capacity && persona.capacity.currentUtilization > 90 && (
                            <div className="p-3 bg-yellow-50 dark:bg-yellow-600/30 border-l-4 border-yellow-500 rounded">
                                <p className="text-sm text-slate-900 dark:text-neutral-200">
                                    ⚠️ <strong>Heads up:</strong> Your capacity utilization is at{' '}
                                    {persona.capacity.currentUtilization}%. Consider delegating or rescheduling
                                    some tasks.
                                </p>
                            </div>
                        )}

                        {activeGoalsCount > 0 && goals[0]?.progress < 30 && (
                            <div className="p-3 bg-purple-50 dark:bg-purple-600/30 border-l-4 border-purple-500 rounded">
                                <p className="text-sm text-slate-900 dark:text-neutral-200">
                                    🎯 <strong>Growth:</strong> Your goal "{goals[0]?.title}" is at {goals[0]?.progress}%.
                                    Consider allocating time this week to make progress.
                                </p>
                            </div>
                        )}
                    </div>
                </Box>
            </Card>
        </Box>
    );
}


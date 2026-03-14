import React, { useState } from 'react';
import {
    Card,
    CardContent,
    CardHeader,
    Typography,
    Box,
    Chip,
    Button,
    IconButton,
    LinearProgress,
    Avatar,
    List,
    ListItem,
    ListItemText,
    ListItemAvatar,
    Divider,
    Alert,
    Tab,
    Tabs,
} from '@ghatana/design-system';
import {
    CheckCircle as CheckCircleIcon,
    RadioButtonUnchecked as RadioButtonUncheckedIcon,
    TrendingUp as TrendingUpIcon,
    TrendingDown as TrendingDownIcon,
    Notifications as NotificationsIcon,
    Assignment as AssignmentIcon,
    EmojiEvents as EmojiEventsIcon,
    School as SchoolIcon,
    CalendarToday as CalendarTodayIcon,
    ChevronRight as ChevronRightIcon,
    Flag as FlagIcon,
    PlayArrow as PlayArrowIcon,
    Pause as PauseIcon,
    CheckCircleOutline as CheckCircleOutlineIcon,
} from '@ghatana/design-system/icons';

// ==================== TYPES ====================

export interface Task {
    id: string;
    title: string;
    description?: string;
    status: 'not_started' | 'in_progress' | 'completed' | 'blocked';
    priority: 'low' | 'medium' | 'high' | 'urgent';
    dueDate?: string;
    project?: string;
    assignedBy?: string;
    estimatedHours?: number;
    actualHours?: number;
}

export interface Activity {
    id: string;
    type: 'task_completed' | 'goal_updated' | 'skill_added' | 'achievement_earned' | 'feedback_received';
    title: string;
    description: string;
    timestamp: string;
    metadata?: {
        taskId?: string;
        goalId?: string;
        skillName?: string;
        achievementName?: string;
    };
}

export interface Notification {
    id: string;
    type: 'info' | 'warning' | 'error' | 'success';
    title: string;
    message: string;
    timestamp: string;
    read: boolean;
    actionLabel?: string;
    actionUrl?: string;
}

export interface Goal {
    id: string;
    title: string;
    type: 'okr' | 'personal' | 'team';
    progress: number; // 0-100
    dueDate: string;
    status: 'on_track' | 'at_risk' | 'behind' | 'completed';
}

export interface QuickAction {
    id: string;
    label: string;
    icon: React.ReactNode;
    color: 'primary' | 'secondary' | 'info' | 'success' | 'warning' | 'error';
    onClick: () => void;
}

export interface PersonalDashboardProps {
    currentUser?: {
        name: string;
        email: string;
        role: string;
        department?: string;
        avatar?: string;
    };
    tasks?: Task[];
    recentActivity?: Activity[];
    notifications?: Notification[];
    goals?: Goal[];
    quickActions?: QuickAction[];
    onTaskClick?: (taskId: string) => void;
    onTaskStatusChange?: (taskId: string, newStatus: Task['status']) => void;
    onNotificationRead?: (notificationId: string) => void;
    onNotificationAction?: (notificationId: string, actionUrl: string) => void;
    onGoalClick?: (goalId: string) => void;
}

// ==================== MOCK DATA ====================

const mockCurrentUser = {
    name: 'Alex Johnson',
    email: 'alex.johnson@acme.com',
    role: 'Senior Software Engineer',
    department: 'Engineering',
    avatar: 'AJ',
};

const mockTasks: Task[] = [
    {
        id: 'task-1',
        title: 'Complete API integration for user service',
        description: 'Implement REST endpoints for user CRUD operations',
        status: 'in_progress',
        priority: 'high',
        dueDate: '2025-12-15T17:00:00Z',
        project: 'User Management System',
        assignedBy: 'Sarah Chen (Manager)',
        estimatedHours: 8,
        actualHours: 5,
    },
    {
        id: 'task-2',
        title: 'Review pull request #234',
        description: 'Security updates for authentication module',
        status: 'not_started',
        priority: 'urgent',
        dueDate: '2025-12-12T12:00:00Z',
        project: 'Security Sprint',
        assignedBy: 'David Park (Tech Lead)',
    },
    {
        id: 'task-3',
        title: 'Update documentation for deployment process',
        description: 'Add Docker and Kubernetes setup instructions',
        status: 'not_started',
        priority: 'medium',
        dueDate: '2025-12-18T17:00:00Z',
        project: 'DevOps Improvement',
    },
    {
        id: 'task-4',
        title: 'Fix bug in payment processing',
        description: 'Investigate timeout issues in Stripe integration',
        status: 'blocked',
        priority: 'high',
        dueDate: '2025-12-13T17:00:00Z',
        project: 'Payment Gateway',
        assignedBy: 'Sarah Chen (Manager)',
    },
    {
        id: 'task-5',
        title: 'Unit tests for order service',
        description: 'Increase test coverage to 80%',
        status: 'in_progress',
        priority: 'medium',
        dueDate: '2025-12-20T17:00:00Z',
        project: 'Quality Assurance',
        estimatedHours: 6,
        actualHours: 2,
    },
];

const mockRecentActivity: Activity[] = [
    {
        id: 'act-1',
        type: 'task_completed',
        title: 'Task Completed',
        description: 'Set up CI/CD pipeline for staging environment',
        timestamp: '2025-12-11T10:30:00Z',
        metadata: { taskId: 'task-100' },
    },
    {
        id: 'act-2',
        type: 'goal_updated',
        title: 'Goal Progress Updated',
        description: 'Q4 OKR: Reduce API latency - 75% complete',
        timestamp: '2025-12-11T09:15:00Z',
        metadata: { goalId: 'goal-1' },
    },
    {
        id: 'act-3',
        type: 'skill_added',
        title: 'New Skill Added',
        description: 'Added Kubernetes to your skill profile',
        timestamp: '2025-12-10T16:45:00Z',
        metadata: { skillName: 'Kubernetes' },
    },
    {
        id: 'act-4',
        type: 'achievement_earned',
        title: 'Achievement Unlocked',
        description: 'Code Review Champion - Reviewed 50+ PRs this quarter',
        timestamp: '2025-12-10T14:20:00Z',
        metadata: { achievementName: 'Code Review Champion' },
    },
    {
        id: 'act-5',
        type: 'feedback_received',
        title: 'Feedback Received',
        description: 'Great work on the authentication refactoring!',
        timestamp: '2025-12-09T11:00:00Z',
    },
];

const mockNotifications: Notification[] = [
    {
        id: 'notif-1',
        type: 'warning',
        title: 'Deadline Approaching',
        message: 'Review pull request #234 is due tomorrow',
        timestamp: '2025-12-11T08:00:00Z',
        read: false,
        actionLabel: 'View PR',
        actionUrl: '/pull-requests/234',
    },
    {
        id: 'notif-2',
        type: 'info',
        title: 'New Task Assigned',
        message: 'Sarah Chen assigned you "API integration for user service"',
        timestamp: '2025-12-11T07:30:00Z',
        read: false,
        actionLabel: 'View Task',
        actionUrl: '/tasks/task-1',
    },
    {
        id: 'notif-3',
        type: 'success',
        title: 'Goal Milestone Reached',
        message: 'You\'ve completed 75% of your Q4 OKR',
        timestamp: '2025-12-10T16:00:00Z',
        read: true,
    },
];

const mockGoals: Goal[] = [
    {
        id: 'goal-1',
        title: 'Q4 OKR: Reduce API latency by 30%',
        type: 'okr',
        progress: 75,
        dueDate: '2025-12-31T23:59:59Z',
        status: 'on_track',
    },
    {
        id: 'goal-2',
        title: 'Complete AWS Solutions Architect certification',
        type: 'personal',
        progress: 45,
        dueDate: '2026-03-31T23:59:59Z',
        status: 'on_track',
    },
    {
        id: 'goal-3',
        title: 'Team Goal: Ship mobile app v2.0',
        type: 'team',
        progress: 60,
        dueDate: '2026-01-15T23:59:59Z',
        status: 'at_risk',
    },
    {
        id: 'goal-4',
        title: 'Mentor 2 junior developers',
        type: 'personal',
        progress: 50,
        dueDate: '2025-12-31T23:59:59Z',
        status: 'on_track',
    },
];

// ==================== COMPONENT ====================

export const PersonalDashboard: React.FC<PersonalDashboardProps> = ({
    currentUser = mockCurrentUser,
    tasks = mockTasks,
    recentActivity = mockRecentActivity,
    notifications = mockNotifications,
    goals = mockGoals,
    quickActions,
    onTaskClick,
    onTaskStatusChange,
    onNotificationRead,
    onNotificationAction,
    onGoalClick,
}) => {
    const [taskFilter, setTaskFilter] = useState<'all' | 'today' | 'upcoming' | 'overdue'>('all');
    const [selectedTab, setSelectedTab] = useState(0);

    // Helper functions
    const getPriorityColor = (priority: Task['priority']): 'error' | 'warning' | 'info' | 'default' => {
        switch (priority) {
            case 'urgent':
                return 'error';
            case 'high':
                return 'warning';
            case 'medium':
                return 'info';
            case 'low':
            default:
                return 'default';
        }
    };

    const getStatusColor = (status: Task['status']): 'success' | 'info' | 'warning' | 'error' => {
        switch (status) {
            case 'completed':
                return 'success';
            case 'in_progress':
                return 'info';
            case 'blocked':
                return 'error';
            case 'not_started':
            default:
                return 'warning';
        }
    };

    const getGoalStatusColor = (status: Goal['status']): 'success' | 'info' | 'warning' | 'error' => {
        switch (status) {
            case 'completed':
                return 'success';
            case 'on_track':
                return 'info';
            case 'at_risk':
                return 'warning';
            case 'behind':
                return 'error';
        }
    };

    const getActivityIcon = (type: Activity['type']) => {
        switch (type) {
            case 'task_completed':
                return <CheckCircleIcon color="success" />;
            case 'goal_updated':
                return <EmojiEventsIcon color="info" />;
            case 'skill_added':
                return <SchoolIcon color="primary" />;
            case 'achievement_earned':
                return <EmojiEventsIcon color="warning" />;
            case 'feedback_received':
                return <NotificationsIcon color="secondary" />;
        }
    };

    const formatTimestamp = (timestamp: string): string => {
        const date = new Date(timestamp);
        const now = new Date();
        const diffMs = now.getTime() - date.getTime();
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);

        if (diffMins < 60) return `${diffMins}m ago`;
        if (diffHours < 24) return `${diffHours}h ago`;
        if (diffDays < 7) return `${diffDays}d ago`;
        return date.toLocaleDateString();
    };

    const formatDueDate = (dueDate: string): string => {
        const date = new Date(dueDate);
        const now = new Date();
        const diffMs = date.getTime() - now.getTime();
        const diffDays = Math.ceil(diffMs / 86400000);

        if (diffDays < 0) return 'Overdue';
        if (diffDays === 0) return 'Due today';
        if (diffDays === 1) return 'Due tomorrow';
        if (diffDays < 7) return `Due in ${diffDays} days`;
        return date.toLocaleDateString();
    };

    const isOverdue = (dueDate?: string): boolean => {
        if (!dueDate) return false;
        return new Date(dueDate).getTime() < new Date().getTime();
    };

    const filterTasks = (task: Task): boolean => {
        if (taskFilter === 'all') return true;

        if (!task.dueDate) return taskFilter === 'upcoming';

        const dueDate = new Date(task.dueDate);
        const now = new Date();
        const diffDays = Math.ceil((dueDate.getTime() - now.getTime()) / 86400000);

        switch (taskFilter) {
            case 'today':
                return diffDays === 0;
            case 'upcoming':
                return diffDays > 0 && diffDays <= 7;
            case 'overdue':
                return diffDays < 0;
            default:
                return true;
        }
    };

    const filteredTasks = tasks.filter(filterTasks);
    const unreadNotifications = notifications.filter(n => !n.read);

    const defaultQuickActions: QuickAction[] = [
        {
            id: 'create-task',
            label: 'New Task',
            icon: <AssignmentIcon />,
            color: 'primary',
            onClick: () => console.log('Create task'),
        },
        {
            id: 'update-goal',
            label: 'Update Goal',
            icon: <EmojiEventsIcon />,
            color: 'secondary',
            onClick: () => console.log('Update goal'),
        },
        {
            id: 'log-time',
            label: 'Log Time',
            icon: <CalendarTodayIcon />,
            color: 'info',
            onClick: () => console.log('Log time'),
        },
        {
            id: 'add-skill',
            label: 'Add Skill',
            icon: <SchoolIcon />,
            color: 'success',
            onClick: () => console.log('Add skill'),
        },
    ];

    const actions = quickActions || defaultQuickActions;

    return (
        <Box sx={{ p: 3 }}>
            {/* Header */}
            <Box sx={{ mb: 4 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
                    <Avatar sx={{ width: 56, height: 56, bgcolor: 'primary.main' }}>
                        {currentUser.avatar || currentUser.name.split(' ').map(n => n[0]).join('')}
                    </Avatar>
                    <Box>
                        <Typography variant="h4">Welcome back, {currentUser.name.split(' ')[0]}!</Typography>
                        <Typography variant="body2" color="text.secondary">
                            {currentUser.role} • {currentUser.department}
                        </Typography>
                    </Box>
                </Box>

                {/* Quick Stats */}
                <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 2, mt: 3 }}>
                    <Card variant="outlined">
                        <CardContent>
                            <Typography variant="body2" color="text.secondary" gutterBottom>
                                Active Tasks
                            </Typography>
                            <Typography variant="h4">
                                {tasks.filter(t => t.status === 'in_progress').length}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                                {tasks.filter(t => t.status === 'not_started').length} pending
                            </Typography>
                        </CardContent>
                    </Card>

                    <Card variant="outlined">
                        <CardContent>
                            <Typography variant="body2" color="text.secondary" gutterBottom>
                                Goals Progress
                            </Typography>
                            <Typography variant="h4">
                                {Math.round(goals.reduce((acc, g) => acc + g.progress, 0) / goals.length)}%
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                                {goals.filter(g => g.status === 'on_track').length} on track
                            </Typography>
                        </CardContent>
                    </Card>

                    <Card variant="outlined">
                        <CardContent>
                            <Typography variant="body2" color="text.secondary" gutterBottom>
                                Notifications
                            </Typography>
                            <Typography variant="h4">{unreadNotifications.length}</Typography>
                            <Typography variant="caption" color="text.secondary">
                                unread
                            </Typography>
                        </CardContent>
                    </Card>

                    <Card variant="outlined">
                        <CardContent>
                            <Typography variant="body2" color="text.secondary" gutterBottom>
                                This Week
                            </Typography>
                            <Typography variant="h4">
                                {tasks.filter(t => t.status === 'completed').length}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                                tasks completed
                            </Typography>
                        </CardContent>
                    </Card>
                </Box>
            </Box>

            {/* Quick Actions */}
            <Card sx={{ mb: 3 }}>
                <CardHeader title="Quick Actions" />
                <CardContent>
                    <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                        {actions.map((action) => (
                            <Button
                                key={action.id}
                                variant="outlined"
                                color={action.color}
                                startIcon={action.icon}
                                onClick={action.onClick}
                            >
                                {action.label}
                            </Button>
                        ))}
                    </Box>
                </CardContent>
            </Card>

            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '2fr 1fr' }, gap: 3 }}>
                {/* Left Column */}
                <Box>
                    {/* My Tasks */}
                    <Card sx={{ mb: 3 }}>
                        <CardHeader
                            title="My Tasks"
                            action={
                                <Box sx={{ display: 'flex', gap: 1 }}>
                                    <Chip
                                        label="All"
                                        color={taskFilter === 'all' ? 'primary' : 'default'}
                                        onClick={() => setTaskFilter('all')}
                                        size="small"
                                    />
                                    <Chip
                                        label="Today"
                                        color={taskFilter === 'today' ? 'primary' : 'default'}
                                        onClick={() => setTaskFilter('today')}
                                        size="small"
                                    />
                                    <Chip
                                        label="Upcoming"
                                        color={taskFilter === 'upcoming' ? 'primary' : 'default'}
                                        onClick={() => setTaskFilter('upcoming')}
                                        size="small"
                                    />
                                    <Chip
                                        label="Overdue"
                                        color={taskFilter === 'overdue' ? 'primary' : 'default'}
                                        onClick={() => setTaskFilter('overdue')}
                                        size="small"
                                    />
                                </Box>
                            }
                        />
                        <CardContent>
                            {filteredTasks.length === 0 ? (
                                <Alert severity="info">No tasks match the current filter</Alert>
                            ) : (
                                <List>
                                    {filteredTasks.map((task, index) => (
                                        <React.Fragment key={task.id}>
                                            {index > 0 && <Divider />}
                                            <ListItem
                                                sx={{
                                                    cursor: 'pointer',
                                                    '&:hover': { bgcolor: 'action.hover' },
                                                    alignItems: 'flex-start',
                                                }}
                                                onClick={() => onTaskClick?.(task.id)}
                                            >
                                                <ListItemAvatar>
                                                    <IconButton
                                                        size="small"
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            const newStatus = task.status === 'completed' ? 'in_progress' : 'completed';
                                                            onTaskStatusChange?.(task.id, newStatus);
                                                        }}
                                                    >
                                                        {task.status === 'completed' ? (
                                                            <CheckCircleIcon color="success" />
                                                        ) : (
                                                            <RadioButtonUncheckedIcon color="action" />
                                                        )}
                                                    </IconButton>
                                                </ListItemAvatar>
                                                <ListItemText
                                                    primary={
                                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                                                            <Typography
                                                                variant="body1"
                                                                sx={{ textDecoration: task.status === 'completed' ? 'line-through' : 'none' }}
                                                            >
                                                                {task.title}
                                                            </Typography>
                                                            <Chip label={task.priority.toUpperCase()} color={getPriorityColor(task.priority)} size="small" />
                                                            <Chip
                                                                label={task.status.replace('_', ' ').toUpperCase()}
                                                                color={getStatusColor(task.status)}
                                                                size="small"
                                                            />
                                                        </Box>
                                                    }
                                                    secondary={
                                                        <Box sx={{ mt: 1 }}>
                                                            {task.description && (
                                                                <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
                                                                    {task.description}
                                                                </Typography>
                                                            )}
                                                            <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center' }}>
                                                                {task.project && (
                                                                    <Typography variant="caption" color="text.secondary">
                                                                        📁 {task.project}
                                                                    </Typography>
                                                                )}
                                                                {task.dueDate && (
                                                                    <Typography
                                                                        variant="caption"
                                                                        color={isOverdue(task.dueDate) ? 'error' : 'text.secondary'}
                                                                    >
                                                                        📅 {formatDueDate(task.dueDate)}
                                                                    </Typography>
                                                                )}
                                                                {task.assignedBy && (
                                                                    <Typography variant="caption" color="text.secondary">
                                                                        👤 {task.assignedBy}
                                                                    </Typography>
                                                                )}
                                                                {task.estimatedHours && (
                                                                    <Typography variant="caption" color="text.secondary">
                                                                        ⏱️ {task.actualHours || 0}/{task.estimatedHours}h
                                                                    </Typography>
                                                                )}
                                                            </Box>
                                                        </Box>
                                                    }
                                                />
                                                <ChevronRightIcon color="action" />
                                            </ListItem>
                                        </React.Fragment>
                                    ))}
                                </List>
                            )}
                        </CardContent>
                    </Card>

                    {/* Goals Overview */}
                    <Card>
                        <CardHeader title="My Goals" />
                        <CardContent>
                            <List>
                                {goals.map((goal, index) => (
                                    <React.Fragment key={goal.id}>
                                        {index > 0 && <Divider sx={{ my: 2 }} />}
                                        <ListItem
                                            sx={{ cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' }, px: 0 }}
                                            onClick={() => onGoalClick?.(goal.id)}
                                        >
                                            <Box sx={{ width: '100%' }}>
                                                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                                        <FlagIcon color="action" fontSize="small" />
                                                        <Typography variant="body1">{goal.title}</Typography>
                                                    </Box>
                                                    <Chip label={goal.status.replace('_', ' ').toUpperCase()} color={getGoalStatusColor(goal.status)} size="small" />
                                                </Box>
                                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                                                    <LinearProgress
                                                        variant="determinate"
                                                        value={goal.progress}
                                                        sx={{ flex: 1 }}
                                                        color={getGoalStatusColor(goal.status)}
                                                    />
                                                    <Typography variant="body2" color="text.secondary" sx={{ minWidth: 45 }}>
                                                        {goal.progress}%
                                                    </Typography>
                                                </Box>
                                                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                                    <Chip label={goal.type.toUpperCase()} size="small" variant="outlined" />
                                                    <Typography variant="caption" color="text.secondary">
                                                        Due {formatDueDate(goal.dueDate)}
                                                    </Typography>
                                                </Box>
                                            </Box>
                                        </ListItem>
                                    </React.Fragment>
                                ))}
                            </List>
                        </CardContent>
                    </Card>
                </Box>

                {/* Right Column */}
                <Box>
                    {/* Notifications */}
                    <Card sx={{ mb: 3 }}>
                        <CardHeader
                            title="Notifications"
                            action={
                                unreadNotifications.length > 0 && (
                                    <Chip label={`${unreadNotifications.length} new`} color="error" size="small" />
                                )
                            }
                        />
                        <CardContent>
                            {notifications.length === 0 ? (
                                <Alert severity="info">No notifications</Alert>
                            ) : (
                                <List>
                                    {notifications.slice(0, 5).map((notification, index) => (
                                        <React.Fragment key={notification.id}>
                                            {index > 0 && <Divider />}
                                            <ListItem
                                                sx={{
                                                    bgcolor: notification.read ? 'transparent' : 'action.hover',
                                                    borderRadius: 1,
                                                    mb: 0.5,
                                                    flexDirection: 'column',
                                                    alignItems: 'flex-start',
                                                }}
                                            >
                                                <Box sx={{ display: 'flex', justifyContent: 'space-between', width: '100%', mb: 0.5 }}>
                                                    <Typography variant="subtitle2">{notification.title}</Typography>
                                                    {!notification.read && <Chip label="NEW" color="error" size="small" />}
                                                </Box>
                                                <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                                                    {notification.message}
                                                </Typography>
                                                <Box sx={{ display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
                                                    <Typography variant="caption" color="text.secondary">
                                                        {formatTimestamp(notification.timestamp)}
                                                    </Typography>
                                                    {notification.actionLabel && notification.actionUrl && (
                                                        <Button
                                                            size="small"
                                                            onClick={() => {
                                                                onNotificationAction?.(notification.id, notification.actionUrl!);
                                                                if (!notification.read) {
                                                                    onNotificationRead?.(notification.id);
                                                                }
                                                            }}
                                                        >
                                                            {notification.actionLabel}
                                                        </Button>
                                                    )}
                                                </Box>
                                            </ListItem>
                                        </React.Fragment>
                                    ))}
                                </List>
                            )}
                        </CardContent>
                    </Card>

                    {/* Recent Activity */}
                    <Card>
                        <CardHeader title="Recent Activity" />
                        <CardContent>
                            <List>
                                {recentActivity.map((activity, index) => (
                                    <React.Fragment key={activity.id}>
                                        {index > 0 && <Divider />}
                                        <ListItem sx={{ alignItems: 'flex-start', px: 0 }}>
                                            <ListItemAvatar>{getActivityIcon(activity.type)}</ListItemAvatar>
                                            <ListItemText
                                                primary={activity.title}
                                                secondary={
                                                    <>
                                                        <Typography variant="body2" color="text.secondary">
                                                            {activity.description}
                                                        </Typography>
                                                        <Typography variant="caption" color="text.secondary">
                                                            {formatTimestamp(activity.timestamp)}
                                                        </Typography>
                                                    </>
                                                }
                                            />
                                        </ListItem>
                                    </React.Fragment>
                                ))}
                            </List>
                        </CardContent>
                    </Card>
                </Box>
            </Box>
        </Box>
    );
};

export default PersonalDashboard;

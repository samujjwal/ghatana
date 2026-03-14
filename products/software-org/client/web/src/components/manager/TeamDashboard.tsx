import React, { useState } from 'react';
import {
    Card,
    CardContent,
    CardHeader,
    Chip,
    LinearProgress,
    Avatar,
    IconButton,
    Tabs,
    Tab,
    Table,
    TableHead,
    TableRow,
    TableCell,
    TableBody,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    Alert,
    Tooltip,
} from '@ghatana/design-system';
import {
    Group,
    Person,
    TrendingUp,
    TrendingDown,
    Schedule,
    CheckCircle,
    Warning,
    Assignment,
    Star,
    EmojiEvents,
    LocalFireDepartment,
    Speed,
    People,
    CalendarToday,
    MoreVert,
} from '@ghatana/design-system/icons';

// ============================================================================
// Type Definitions
// ============================================================================

interface TeamMember {
    id: string;
    name: string;
    email: string;
    role: string;
    avatar?: string;
    status: 'active' | 'on_leave' | 'out_sick' | 'remote';
    workload: number; // 0-100 percentage
    performance: {
        rating: number; // 1-5
        trend: 'up' | 'down' | 'stable';
        tasksCompleted: number;
        tasksInProgress: number;
    };
    nextOneOnOne?: Date;
    lastReview?: Date;
}

interface TeamProject {
    id: string;
    name: string;
    status: 'on_track' | 'at_risk' | 'delayed' | 'completed';
    progress: number; // 0-100
    dueDate: Date;
    assignedMembers: string[]; // member IDs
    priority: 'low' | 'medium' | 'high' | 'critical';
}

interface TeamMetric {
    label: string;
    value: string | number;
    change?: number; // percentage change
    trend?: 'up' | 'down' | 'stable';
    icon: React.ReactNode;
    color?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'info';
}

interface TeamAlert {
    id: string;
    type: 'info' | 'warning' | 'error' | 'success';
    title: string;
    message: string;
    timestamp: Date;
    actionLabel?: string;
    actionUrl?: string;
}

export interface TeamDashboardProps {
    teamName?: string;
    members?: TeamMember[];
    projects?: TeamProject[];
    alerts?: TeamAlert[];
    onMemberClick?: (memberId: string) => void;
    onProjectClick?: (projectId: string) => void;
    onScheduleOneOnOne?: (memberId: string) => void;
    onStartReview?: (memberId: string) => void;
    onAlertAction?: (alertId: string, actionUrl: string) => void;
}

// ============================================================================
// Mock Data
// ============================================================================

const mockMembers: TeamMember[] = [
    {
        id: '1',
        name: 'Sarah Johnson',
        email: 'sarah.j@company.com',
        role: 'Senior Software Engineer',
        status: 'active',
        workload: 85,
        performance: {
            rating: 4.5,
            trend: 'up',
            tasksCompleted: 23,
            tasksInProgress: 3,
        },
        nextOneOnOne: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000),
        lastReview: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000),
    },
    {
        id: '2',
        name: 'Michael Chen',
        email: 'michael.c@company.com',
        role: 'Software Engineer',
        status: 'active',
        workload: 95,
        performance: {
            rating: 4.0,
            trend: 'stable',
            tasksCompleted: 18,
            tasksInProgress: 5,
        },
        nextOneOnOne: new Date(Date.now() + 5 * 24 * 60 * 60 * 1000),
        lastReview: new Date(Date.now() - 45 * 24 * 60 * 60 * 1000),
    },
    {
        id: '3',
        name: 'Emily Rodriguez',
        email: 'emily.r@company.com',
        role: 'Software Engineer',
        status: 'on_leave',
        workload: 0,
        performance: {
            rating: 4.2,
            trend: 'up',
            tasksCompleted: 15,
            tasksInProgress: 0,
        },
        lastReview: new Date(Date.now() - 60 * 24 * 60 * 60 * 1000),
    },
    {
        id: '4',
        name: 'David Kim',
        email: 'david.k@company.com',
        role: 'Junior Software Engineer',
        status: 'active',
        workload: 70,
        performance: {
            rating: 3.8,
            trend: 'up',
            tasksCompleted: 12,
            tasksInProgress: 4,
        },
        nextOneOnOne: new Date(Date.now() + 1 * 24 * 60 * 60 * 1000),
        lastReview: new Date(Date.now() - 90 * 24 * 60 * 60 * 1000),
    },
    {
        id: '5',
        name: 'Jessica Lee',
        email: 'jessica.l@company.com',
        role: 'Senior Software Engineer',
        status: 'remote',
        workload: 78,
        performance: {
            rating: 4.3,
            trend: 'stable',
            tasksCompleted: 20,
            tasksInProgress: 2,
        },
        nextOneOnOne: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
        lastReview: new Date(Date.now() - 20 * 24 * 60 * 60 * 1000),
    },
];

const mockProjects: TeamProject[] = [
    {
        id: '1',
        name: 'User Authentication Redesign',
        status: 'on_track',
        progress: 75,
        dueDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000),
        assignedMembers: ['1', '2'],
        priority: 'high',
    },
    {
        id: '2',
        name: 'API Performance Optimization',
        status: 'at_risk',
        progress: 45,
        dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
        assignedMembers: ['1', '5'],
        priority: 'critical',
    },
    {
        id: '3',
        name: 'Dashboard Analytics',
        status: 'on_track',
        progress: 60,
        dueDate: new Date(Date.now() + 21 * 24 * 60 * 60 * 1000),
        assignedMembers: ['4'],
        priority: 'medium',
    },
    {
        id: '4',
        name: 'Mobile App Bug Fixes',
        status: 'delayed',
        progress: 30,
        dueDate: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000),
        assignedMembers: ['2', '4'],
        priority: 'high',
    },
];

const mockAlerts: TeamAlert[] = [
    {
        id: '1',
        type: 'warning',
        title: 'Michael Chen Overallocated',
        message: 'Michael is at 95% capacity. Consider redistributing tasks.',
        timestamp: new Date(Date.now() - 2 * 60 * 60 * 1000),
        actionLabel: 'View Workload',
        actionUrl: '/team/workload',
    },
    {
        id: '2',
        type: 'error',
        title: 'Project Delayed',
        message: 'Mobile App Bug Fixes is behind schedule. Due in 3 days.',
        timestamp: new Date(Date.now() - 4 * 60 * 60 * 1000),
        actionLabel: 'View Project',
        actionUrl: '/projects/4',
    },
    {
        id: '3',
        type: 'info',
        title: '1:1 Scheduled',
        message: 'Upcoming 1:1 with David Kim tomorrow at 2 PM.',
        timestamp: new Date(Date.now() - 6 * 60 * 60 * 1000),
        actionLabel: 'View Calendar',
        actionUrl: '/calendar',
    },
];

// ============================================================================
// Component
// ============================================================================

export const TeamDashboard: React.FC<TeamDashboardProps> = ({
    teamName = 'Engineering Team',
    members = mockMembers,
    projects = mockProjects,
    alerts = mockAlerts,
    onMemberClick,
    onProjectClick,
    onScheduleOneOnOne,
    onStartReview,
    onAlertAction,
}) => {
    const [selectedTab, setSelectedTab] = useState<number>(0);
    const [selectedMember, setSelectedMember] = useState<TeamMember | null>(null);
    const [memberDialogOpen, setMemberDialogOpen] = useState(false);

    // Calculate team metrics
    const activeMembers = members.filter((m) => m.status === 'active');
    const avgWorkload =
        activeMembers.reduce((sum, m) => sum + m.workload, 0) / activeMembers.length || 0;
    const avgPerformance =
        members.reduce((sum, m) => sum + m.performance.rating, 0) / members.length || 0;
    const overallocatedCount = members.filter((m) => m.workload > 90).length;
    const projectsAtRisk = projects.filter((p) => p.status === 'at_risk' || p.status === 'delayed')
        .length;

    const teamMetrics: TeamMetric[] = [
        {
            label: 'Team Size',
            value: members.length,
            icon: <People fontSize="large" />,
            color: 'primary',
        },
        {
            label: 'Avg Workload',
            value: `${Math.round(avgWorkload)}%`,
            change: avgWorkload > 85 ? -5 : 2,
            trend: avgWorkload > 85 ? 'up' : 'stable',
            icon: <Speed fontSize="large" />,
            color: avgWorkload > 85 ? 'warning' : 'success',
        },
        {
            label: 'Avg Performance',
            value: avgPerformance.toFixed(1),
            change: 3,
            trend: 'up',
            icon: <Star fontSize="large" />,
            color: 'success',
        },
        {
            label: 'Projects',
            value: `${projects.length} Active`,
            icon: <Assignment fontSize="large" />,
            color: projectsAtRisk > 0 ? 'error' : 'info',
        },
    ];

    const handleMemberCardClick = (member: TeamMember) => {
        setSelectedMember(member);
        setMemberDialogOpen(true);
        onMemberClick?.(member.id);
    };

    const handleScheduleOneOnOne = (member: TeamMember) => {
        onScheduleOneOnOne?.(member.id);
        setMemberDialogOpen(false);
    };

    const handleStartReview = (member: TeamMember) => {
        onStartReview?.(member.id);
        setMemberDialogOpen(false);
    };

    const getStatusColor = (
        status: TeamMember['status']
    ): 'default' | 'success' | 'warning' | 'error' | 'info' => {
        switch (status) {
            case 'active':
                return 'success';
            case 'on_leave':
                return 'warning';
            case 'out_sick':
                return 'error';
            case 'remote':
                return 'info';
            default:
                return 'default';
        }
    };

    const getStatusLabel = (status: TeamMember['status']): string => {
        switch (status) {
            case 'active':
                return 'Active';
            case 'on_leave':
                return 'On Leave';
            case 'out_sick':
                return 'Out Sick';
            case 'remote':
                return 'Remote';
            default:
                return status;
        }
    };

    const getProjectStatusColor = (
        status: TeamProject['status']
    ): 'default' | 'success' | 'warning' | 'error' | 'info' => {
        switch (status) {
            case 'on_track':
                return 'success';
            case 'at_risk':
                return 'warning';
            case 'delayed':
                return 'error';
            case 'completed':
                return 'info';
            default:
                return 'default';
        }
    };

    const getPriorityColor = (
        priority: TeamProject['priority']
    ): 'default' | 'success' | 'warning' | 'error' | 'info' => {
        switch (priority) {
            case 'critical':
                return 'error';
            case 'high':
                return 'warning';
            case 'medium':
                return 'info';
            case 'low':
                return 'default';
            default:
                return 'default';
        }
    };

    const formatDate = (date: Date): string => {
        const now = new Date();
        const diffMs = date.getTime() - now.getTime();
        const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));

        if (diffDays === 0) return 'Today';
        if (diffDays === 1) return 'Tomorrow';
        if (diffDays === -1) return 'Yesterday';
        if (diffDays > 0 && diffDays <= 7) return `In ${diffDays} days`;
        if (diffDays < 0 && diffDays >= -7) return `${Math.abs(diffDays)} days ago`;
        return date.toLocaleDateString();
    };

    const getWorkloadColor = (workload: number): 'success' | 'warning' | 'error' => {
        if (workload <= 80) return 'success';
        if (workload <= 95) return 'warning';
        return 'error';
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <Group fontSize="large" className="text-primary" />
                    <div>
                        <h1 className="text-3xl font-bold dark:text-white">{teamName}</h1>
                        <p className="text-sm text-gray-600 dark:text-gray-400">
                            {activeMembers.length} active members · {projects.length} active projects
                        </p>
                    </div>
                </div>
            </div>

            {/* Team Metrics */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                {teamMetrics.map((metric, index) => (
                    <Card key={index}>
                        <CardContent className="flex items-center gap-4 p-4">
                            <div className={`text-${metric.color || 'primary'}`}>{metric.icon}</div>
                            <div className="flex-1">
                                <p className="text-sm text-gray-600 dark:text-gray-400">{metric.label}</p>
                                <div className="flex items-center gap-2">
                                    <p className="text-2xl font-bold dark:text-white">{metric.value}</p>
                                    {metric.change !== undefined && (
                                        <Chip
                                            label={`${metric.change > 0 ? '+' : ''}${metric.change}%`}
                                            size="small"
                                            color={metric.trend === 'up' ? 'success' : metric.trend === 'down' ? 'error' : 'default'}
                                            icon={
                                                metric.trend === 'up' ? (
                                                    <TrendingUp fontSize="small" />
                                                ) : metric.trend === 'down' ? (
                                                    <TrendingDown fontSize="small" />
                                                ) : undefined
                                            }
                                        />
                                    )}
                                </div>
                            </div>
                        </CardContent>
                    </Card>
                ))}
            </div>

            {/* Alerts */}
            {alerts.length > 0 && (
                <div className="space-y-2">
                    {alerts.slice(0, 3).map((alert) => (
                        <Alert
                            key={alert.id}
                            severity={alert.type}
                            action={
                                alert.actionLabel && alert.actionUrl ? (
                                    <Button
                                        size="small"
                                        onClick={() => onAlertAction?.(alert.id, alert.actionUrl!)}
                                    >
                                        {alert.actionLabel}
                                    </Button>
                                ) : undefined
                            }
                        >
                            <strong>{alert.title}</strong> - {alert.message}
                        </Alert>
                    ))}
                </div>
            )}

            {/* Tabs */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, newValue) => setSelectedTab(newValue)}>
                    <Tab label="Team Members" />
                    <Tab label="Projects" />
                    <Tab label="Workload" />
                </Tabs>

                <CardContent className="p-6">
                    {/* Team Members Tab */}
                    {selectedTab === 0 && (
                        <div className="space-y-4">
                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                {members.map((member) => (
                                    <Card
                                        key={member.id}
                                        className="cursor-pointer hover:shadow-lg transition-shadow"
                                        onClick={() => handleMemberCardClick(member)}
                                    >
                                        <CardContent className="p-4">
                                            <div className="flex items-start gap-3">
                                                <Avatar src={member.avatar}>
                                                    {member.name
                                                        .split(' ')
                                                        .map((n) => n[0])
                                                        .join('')}
                                                </Avatar>
                                                <div className="flex-1 min-w-0">
                                                    <div className="flex items-center gap-2 mb-1">
                                                        <p className="font-semibold dark:text-white truncate">{member.name}</p>
                                                        {member.performance.rating >= 4.5 && (
                                                            <Tooltip title="Top Performer">
                                                                <EmojiEvents fontSize="small" className="text-warning" />
                                                            </Tooltip>
                                                        )}
                                                        {member.workload > 90 && (
                                                            <Tooltip title="Overallocated">
                                                                <LocalFireDepartment fontSize="small" className="text-error" />
                                                            </Tooltip>
                                                        )}
                                                    </div>
                                                    <p className="text-sm text-gray-600 dark:text-gray-400 truncate mb-2">
                                                        {member.role}
                                                    </p>
                                                    <div className="flex items-center gap-2 mb-2">
                                                        <Chip label={getStatusLabel(member.status)} size="small" color={getStatusColor(member.status)} />
                                                        <Chip
                                                            label={`${member.workload}%`}
                                                            size="small"
                                                            color={getWorkloadColor(member.workload)}
                                                        />
                                                    </div>
                                                    <div className="space-y-1">
                                                        <div className="flex items-center justify-between text-xs">
                                                            <span className="text-gray-600 dark:text-gray-400">Performance</span>
                                                            <span className="font-semibold dark:text-white">
                                                                {member.performance.rating.toFixed(1)}/5.0
                                                            </span>
                                                        </div>
                                                        <LinearProgress
                                                            variant="determinate"
                                                            value={(member.performance.rating / 5) * 100}
                                                            color="success"
                                                        />
                                                    </div>
                                                    {member.nextOneOnOne && (
                                                        <div className="flex items-center gap-1 mt-2 text-xs text-gray-600 dark:text-gray-400">
                                                            <CalendarToday fontSize="inherit" />
                                                            <span>1:1 {formatDate(member.nextOneOnOne)}</span>
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        </CardContent>
                                    </Card>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Projects Tab */}
                    {selectedTab === 1 && (
                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Project Name</TableCell>
                                    <TableCell>Status</TableCell>
                                    <TableCell>Priority</TableCell>
                                    <TableCell>Progress</TableCell>
                                    <TableCell>Due Date</TableCell>
                                    <TableCell>Team</TableCell>
                                    <TableCell>Actions</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {projects.map((project) => (
                                    <TableRow
                                        key={project.id}
                                        className="cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800"
                                        onClick={() => onProjectClick?.(project.id)}
                                    >
                                        <TableCell>
                                            <span className="font-semibold dark:text-white">{project.name}</span>
                                        </TableCell>
                                        <TableCell>
                                            <Chip
                                                label={project.status.replace('_', ' ').toUpperCase()}
                                                size="small"
                                                color={getProjectStatusColor(project.status)}
                                            />
                                        </TableCell>
                                        <TableCell>
                                            <Chip
                                                label={project.priority.toUpperCase()}
                                                size="small"
                                                color={getPriorityColor(project.priority)}
                                            />
                                        </TableCell>
                                        <TableCell>
                                            <div className="w-32">
                                                <div className="flex items-center justify-between text-xs mb-1">
                                                    <span className="dark:text-white">{project.progress}%</span>
                                                </div>
                                                <LinearProgress
                                                    variant="determinate"
                                                    value={project.progress}
                                                    color={getProjectStatusColor(project.status)}
                                                />
                                            </div>
                                        </TableCell>
                                        <TableCell>
                                            <span className="dark:text-white">{formatDate(project.dueDate)}</span>
                                        </TableCell>
                                        <TableCell>
                                            <div className="flex -space-x-2">
                                                {project.assignedMembers.slice(0, 3).map((memberId) => {
                                                    const member = members.find((m) => m.id === memberId);
                                                    return member ? (
                                                        <Tooltip key={memberId} title={member.name}>
                                                            <Avatar src={member.avatar} className="border-2 border-white dark:border-gray-800">
                                                                {member.name
                                                                    .split(' ')
                                                                    .map((n) => n[0])
                                                                    .join('')}
                                                            </Avatar>
                                                        </Tooltip>
                                                    ) : null;
                                                })}
                                                {project.assignedMembers.length > 3 && (
                                                    <Avatar className="border-2 border-white dark:border-gray-800">
                                                        +{project.assignedMembers.length - 3}
                                                    </Avatar>
                                                )}
                                            </div>
                                        </TableCell>
                                        <TableCell>
                                            <IconButton size="small">
                                                <MoreVert fontSize="small" />
                                            </IconButton>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    )}

                    {/* Workload Tab */}
                    {selectedTab === 2 && (
                        <div className="space-y-4">
                            {members.map((member) => (
                                <div key={member.id} className="space-y-2">
                                    <div className="flex items-center justify-between">
                                        <div className="flex items-center gap-3">
                                            <Avatar src={member.avatar} size="small">
                                                {member.name
                                                    .split(' ')
                                                    .map((n) => n[0])
                                                    .join('')}
                                            </Avatar>
                                            <div>
                                                <p className="font-semibold dark:text-white">{member.name}</p>
                                                <p className="text-sm text-gray-600 dark:text-gray-400">
                                                    {member.performance.tasksInProgress} active tasks
                                                </p>
                                            </div>
                                        </div>
                                        <div className="flex items-center gap-2">
                                            <Chip
                                                label={`${member.workload}% Capacity`}
                                                size="small"
                                                color={getWorkloadColor(member.workload)}
                                            />
                                            {member.workload > 90 && <Warning className="text-error" />}
                                        </div>
                                    </div>
                                    <LinearProgress
                                        variant="determinate"
                                        value={Math.min(member.workload, 100)}
                                        color={getWorkloadColor(member.workload)}
                                    />
                                </div>
                            ))}

                            {overallocatedCount > 0 && (
                                <Alert severity="warning" className="mt-4">
                                    <strong>{overallocatedCount} team member(s) overallocated.</strong> Consider
                                    redistributing tasks or adjusting priorities.
                                </Alert>
                            )}
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* Member Detail Dialog */}
            <Dialog open={memberDialogOpen} onClose={() => setMemberDialogOpen(false)} maxWidth="md" fullWidth>
                {selectedMember && (
                    <>
                        <DialogTitle>
                            <div className="flex items-center gap-3">
                                <Avatar src={selectedMember.avatar}>
                                    {selectedMember.name
                                        .split(' ')
                                        .map((n) => n[0])
                                        .join('')}
                                </Avatar>
                                <div>
                                    <h2 className="text-xl font-bold dark:text-white">{selectedMember.name}</h2>
                                    <p className="text-sm text-gray-600 dark:text-gray-400">{selectedMember.role}</p>
                                </div>
                            </div>
                        </DialogTitle>
                        <DialogContent>
                            <div className="space-y-4">
                                {/* Status and Workload */}
                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <p className="text-sm text-gray-600 dark:text-gray-400 mb-1">Status</p>
                                        <Chip label={getStatusLabel(selectedMember.status)} color={getStatusColor(selectedMember.status)} />
                                    </div>
                                    <div>
                                        <p className="text-sm text-gray-600 dark:text-gray-400 mb-1">Workload</p>
                                        <Chip label={`${selectedMember.workload}%`} color={getWorkloadColor(selectedMember.workload)} />
                                    </div>
                                </div>

                                {/* Performance */}
                                <div>
                                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">Performance Rating</p>
                                    <div className="flex items-center gap-4">
                                        <div className="flex-1">
                                            <LinearProgress
                                                variant="determinate"
                                                value={(selectedMember.performance.rating / 5) * 100}
                                                color="success"
                                            />
                                        </div>
                                        <span className="text-2xl font-bold dark:text-white">
                                            {selectedMember.performance.rating.toFixed(1)}/5.0
                                        </span>
                                    </div>
                                </div>

                                {/* Tasks */}
                                <div className="grid grid-cols-2 gap-4">
                                    <Card>
                                        <CardContent className="p-4 text-center">
                                            <CheckCircle className="text-success mb-2" fontSize="large" />
                                            <p className="text-2xl font-bold dark:text-white">
                                                {selectedMember.performance.tasksCompleted}
                                            </p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Tasks Completed</p>
                                        </CardContent>
                                    </Card>
                                    <Card>
                                        <CardContent className="p-4 text-center">
                                            <Schedule className="text-info mb-2" fontSize="large" />
                                            <p className="text-2xl font-bold dark:text-white">
                                                {selectedMember.performance.tasksInProgress}
                                            </p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">In Progress</p>
                                        </CardContent>
                                    </Card>
                                </div>

                                {/* Dates */}
                                <div className="space-y-2">
                                    {selectedMember.nextOneOnOne && (
                                        <div className="flex items-center justify-between">
                                            <span className="text-sm text-gray-600 dark:text-gray-400">Next 1:1</span>
                                            <span className="font-semibold dark:text-white">
                                                {formatDate(selectedMember.nextOneOnOne)}
                                            </span>
                                        </div>
                                    )}
                                    {selectedMember.lastReview && (
                                        <div className="flex items-center justify-between">
                                            <span className="text-sm text-gray-600 dark:text-gray-400">Last Review</span>
                                            <span className="font-semibold dark:text-white">
                                                {formatDate(selectedMember.lastReview)}
                                            </span>
                                        </div>
                                    )}
                                </div>
                            </div>
                        </DialogContent>
                        <DialogActions>
                            <Button onClick={() => setMemberDialogOpen(false)}>Close</Button>
                            <Button onClick={() => handleScheduleOneOnOne(selectedMember)} color="primary" variant="outlined">
                                Schedule 1:1
                            </Button>
                            <Button onClick={() => handleStartReview(selectedMember)} color="primary" variant="contained">
                                Start Review
                            </Button>
                        </DialogActions>
                    </>
                )}
            </Dialog>
        </div>
    );
};

export default TeamDashboard;

import React, { useState } from 'react';
import {
    Box,
    Typography,
    Card,
    CardContent,
    Chip,
    Button,
    LinearProgress,
    Avatar,
    AvatarGroup,
    Tabs,
    Tab,
    Alert,
} from '@ghatana/ui';
import { Grid, KpiCard } from '@ghatana/ui';

// Types
export type ProjectStatus = 'on-track' | 'at-risk' | 'behind' | 'completed' | 'on-hold';
export type ProjectPriority = 'low' | 'medium' | 'high' | 'critical';
export type HealthStatus = 'healthy' | 'warning' | 'critical';

export interface Project {
    id: string;
    name: string;
    description: string;
    status: ProjectStatus;
    priority: ProjectPriority;
    progress: number; // 0-100
    budget: {
        allocated: number;
        spent: number;
        remaining: number;
    };
    timeline: {
        startDate: string;
        endDate: string;
        daysRemaining: number;
    };
    team: {
        lead: string;
        members: number;
        avatar?: string;
    };
    health: {
        overall: HealthStatus;
        budget: HealthStatus;
        timeline: HealthStatus;
        scope: HealthStatus;
    };
    kpis: {
        velocity: number;
        quality: number;
        satisfaction: number;
    };
    department: string;
}

export interface PortfolioMetrics {
    totalProjects: number;
    activeProjects: number;
    completedThisQuarter: number;
    atRisk: number;
    totalBudget: number;
    budgetUtilized: number;
    averageHealth: number;
    teamUtilization: number;
}

export interface PortfolioDashboardProps {
    onProjectClick?: (projectId: string) => void;
    onCreateProject?: () => void;
    onViewDetails?: (projectId: string) => void;
    onExportReport?: () => void;
    department?: string;
}

// Mock data
const mockProjects: Project[] = [
    {
        id: '1',
        name: 'Platform Modernization',
        description: 'Migrate legacy monolith to microservices architecture',
        status: 'on-track',
        priority: 'critical',
        progress: 65,
        budget: {
            allocated: 2500000,
            spent: 1625000,
            remaining: 875000,
        },
        timeline: {
            startDate: '2024-01-01',
            endDate: '2024-06-30',
            daysRemaining: 120,
        },
        team: {
            lead: 'Sarah Johnson',
            members: 12,
        },
        health: {
            overall: 'healthy',
            budget: 'healthy',
            timeline: 'healthy',
            scope: 'warning',
        },
        kpis: {
            velocity: 85,
            quality: 92,
            satisfaction: 88,
        },
        department: 'Engineering',
    },
    {
        id: '2',
        name: 'Mobile App 2.0',
        description: 'Complete redesign of mobile applications',
        status: 'at-risk',
        priority: 'high',
        progress: 45,
        budget: {
            allocated: 1200000,
            spent: 750000,
            remaining: 450000,
        },
        timeline: {
            startDate: '2024-02-01',
            endDate: '2024-05-31',
            daysRemaining: 90,
        },
        team: {
            lead: 'Michael Chen',
            members: 8,
        },
        health: {
            overall: 'warning',
            budget: 'warning',
            timeline: 'critical',
            scope: 'healthy',
        },
        kpis: {
            velocity: 68,
            quality: 85,
            satisfaction: 75,
        },
        department: 'Product',
    },
    {
        id: '3',
        name: 'Security Enhancement Program',
        description: 'Implementation of zero-trust security architecture',
        status: 'on-track',
        priority: 'critical',
        progress: 80,
        budget: {
            allocated: 800000,
            spent: 640000,
            remaining: 160000,
        },
        timeline: {
            startDate: '2023-12-01',
            endDate: '2024-03-31',
            daysRemaining: 30,
        },
        team: {
            lead: 'David Park',
            members: 6,
        },
        health: {
            overall: 'healthy',
            budget: 'healthy',
            timeline: 'healthy',
            scope: 'healthy',
        },
        kpis: {
            velocity: 92,
            quality: 95,
            satisfaction: 90,
        },
        department: 'Security',
    },
    {
        id: '4',
        name: 'Data Analytics Platform',
        description: 'Build self-service analytics platform for business users',
        status: 'behind',
        priority: 'medium',
        progress: 30,
        budget: {
            allocated: 1500000,
            spent: 900000,
            remaining: 600000,
        },
        timeline: {
            startDate: '2024-01-15',
            endDate: '2024-08-31',
            daysRemaining: 180,
        },
        team: {
            lead: 'Emily White',
            members: 10,
        },
        health: {
            overall: 'warning',
            budget: 'critical',
            timeline: 'warning',
            scope: 'healthy',
        },
        kpis: {
            velocity: 55,
            quality: 78,
            satisfaction: 70,
        },
        department: 'Data',
    },
    {
        id: '5',
        name: 'Customer Portal Enhancement',
        description: 'Improve customer self-service capabilities',
        status: 'completed',
        priority: 'high',
        progress: 100,
        budget: {
            allocated: 600000,
            spent: 580000,
            remaining: 20000,
        },
        timeline: {
            startDate: '2023-10-01',
            endDate: '2024-01-31',
            daysRemaining: 0,
        },
        team: {
            lead: 'Lisa Wang',
            members: 5,
        },
        health: {
            overall: 'healthy',
            budget: 'healthy',
            timeline: 'healthy',
            scope: 'healthy',
        },
        kpis: {
            velocity: 88,
            quality: 93,
            satisfaction: 92,
        },
        department: 'Customer Success',
    },
];

const mockMetrics: PortfolioMetrics = {
    totalProjects: 5,
    activeProjects: 4,
    completedThisQuarter: 1,
    atRisk: 2,
    totalBudget: 6600000,
    budgetUtilized: 4495000,
    averageHealth: 78,
    teamUtilization: 82,
};

const statusColors: Record<ProjectStatus, 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info'> = {
    'on-track': 'success',
    'at-risk': 'warning',
    'behind': 'error',
    'completed': 'info',
    'on-hold': 'secondary',
};

const priorityColors: Record<ProjectPriority, 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info'> = {
    low: 'info',
    medium: 'primary',
    high: 'warning',
    critical: 'error',
};

const healthColors: Record<HealthStatus, 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info'> = {
    healthy: 'success',
    warning: 'warning',
    critical: 'error',
};

const formatCurrency = (amount: number): string => {
    return `$${(amount / 1000000).toFixed(1)}M`;
};

const formatDate = (dateString: string): string => {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
};

export const PortfolioDashboard: React.FC<PortfolioDashboardProps> = ({
    onProjectClick,
    onCreateProject,
    onViewDetails,
    onExportReport,
    department,
}) => {
    const [projects] = useState<Project[]>(mockProjects);
    const [metrics] = useState<PortfolioMetrics>(mockMetrics);
    const [selectedTab, setSelectedTab] = useState(0);
    const [filterStatus, setFilterStatus] = useState<ProjectStatus | 'all'>('all');

    const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
        setSelectedTab(newValue);
    };

    const getFilteredProjects = () => {
        let filtered = projects;

        // Filter by department if specified
        if (department) {
            filtered = filtered.filter((p) => p.department === department);
        }

        // Filter by status
        if (filterStatus !== 'all') {
            filtered = filtered.filter((p) => p.status === filterStatus);
        }

        // Filter by tab
        switch (selectedTab) {
            case 0: // All
                return filtered;
            case 1: // Active
                return filtered.filter((p) => p.status === 'on-track' || p.status === 'at-risk' || p.status === 'behind');
            case 2: // At Risk
                return filtered.filter((p) => p.status === 'at-risk' || p.status === 'behind');
            case 3: // Completed
                return filtered.filter((p) => p.status === 'completed');
            default:
                return filtered;
        }
    };

    const filteredProjects = getFilteredProjects();

    return (
        <Box>
            {/* Header */}
            <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Box>
                    <Typography variant="h4" className="dark:text-white">
                        Portfolio Dashboard
                    </Typography>
                    <Typography variant="body2" className="dark:text-gray-400">
                        {department ? `${department} Department` : 'All Departments'} - Strategic project oversight
                    </Typography>
                </Box>
                <Box sx={{ display: 'flex', gap: 1 }}>
                    <Button variant="outlined" onClick={onExportReport}>
                        Export Report
                    </Button>
                    <Button variant="contained" color="primary" onClick={onCreateProject}>
                        New Project
                    </Button>
                </Box>
            </Box>

            {/* KPI Cards - Reusing @ghatana/ui KpiCard */}
            <Grid columns={4} gap={3} sx={{ mb: 3 }}>
                <KpiCard
                    title="Active Projects"
                    value={metrics.activeProjects}
                    target={metrics.totalProjects}
                    trend={{ direction: 'up', value: 10 }}
                    showProgress={true}
                />
                <KpiCard
                    title="At Risk Projects"
                    value={metrics.atRisk}
                    unit=""
                    trend={{ direction: 'down', value: 15 }}
                />
                <KpiCard
                    title="Budget Utilized"
                    value={Math.round((metrics.budgetUtilized / metrics.totalBudget) * 100)}
                    unit="%"
                    target={100}
                    showProgress={true}
                />
                <KpiCard
                    title="Team Utilization"
                    value={metrics.teamUtilization}
                    unit="%"
                    trend={{ direction: 'up', value: 5 }}
                    target={100}
                    showProgress={true}
                />
            </Grid>

            {/* Portfolio Health Summary */}
            <Card className="dark:bg-gray-800" sx={{ mb: 3 }}>
                <CardContent>
                    <Typography variant="h6" className="dark:text-white" sx={{ mb: 2 }}>
                        Portfolio Health
                    </Typography>
                    <Grid columns={4} gap={2}>
                        <Box>
                            <Typography variant="caption" className="dark:text-gray-400">
                                Budget Health
                            </Typography>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1 }}>
                                <LinearProgress
                                    variant="determinate"
                                    value={(metrics.budgetUtilized / metrics.totalBudget) * 100}
                                    color="primary"
                                    sx={{ flex: 1 }}
                                />
                                <Typography variant="body2" className="dark:text-white">
                                    {Math.round((metrics.budgetUtilized / metrics.totalBudget) * 100)}%
                                </Typography>
                            </Box>
                            <Typography variant="caption" className="dark:text-gray-400">
                                {formatCurrency(metrics.budgetUtilized)} of {formatCurrency(metrics.totalBudget)}
                            </Typography>
                        </Box>
                        <Box>
                            <Typography variant="caption" className="dark:text-gray-400">
                                Overall Health Score
                            </Typography>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1 }}>
                                <LinearProgress
                                    variant="determinate"
                                    value={metrics.averageHealth}
                                    color={metrics.averageHealth >= 80 ? 'success' : metrics.averageHealth >= 60 ? 'warning' : 'error'}
                                    sx={{ flex: 1 }}
                                />
                                <Typography variant="body2" className="dark:text-white">
                                    {metrics.averageHealth}%
                                </Typography>
                            </Box>
                            <Chip
                                label={metrics.averageHealth >= 80 ? 'Healthy' : metrics.averageHealth >= 60 ? 'Warning' : 'Critical'}
                                size="small"
                                color={metrics.averageHealth >= 80 ? 'success' : metrics.averageHealth >= 60 ? 'warning' : 'error'}
                                sx={{ mt: 0.5 }}
                            />
                        </Box>
                        <Box>
                            <Typography variant="caption" className="dark:text-gray-400">
                                Projects Status
                            </Typography>
                            <Box sx={{ display: 'flex', gap: 1, mt: 1, flexWrap: 'wrap' }}>
                                <Chip label={`${projects.filter((p) => p.status === 'on-track').length} On Track`} size="small" color="success" />
                                <Chip label={`${projects.filter((p) => p.status === 'at-risk').length} At Risk`} size="small" color="warning" />
                                <Chip label={`${projects.filter((p) => p.status === 'behind').length} Behind`} size="small" color="error" />
                            </Box>
                        </Box>
                        <Box>
                            <Typography variant="caption" className="dark:text-gray-400">
                                Completed This Quarter
                            </Typography>
                            <Typography variant="h4" className="dark:text-white" sx={{ mt: 1 }}>
                                {metrics.completedThisQuarter}
                            </Typography>
                            <Typography variant="caption" className="dark:text-gray-400">
                                {Math.round((metrics.completedThisQuarter / metrics.totalProjects) * 100)}% completion rate
                            </Typography>
                        </Box>
                    </Grid>
                </CardContent>
            </Card>

            {/* Tabs */}
            <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
                <Tabs value={selectedTab} onChange={handleTabChange}>
                    <Tab
                        label={
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                All Projects
                                <Chip label={projects.length} size="small" />
                            </Box>
                        }
                    />
                    <Tab
                        label={
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                Active
                                <Chip
                                    label={projects.filter((p) => p.status === 'on-track' || p.status === 'at-risk' || p.status === 'behind').length}
                                    size="small"
                                    color="primary"
                                />
                            </Box>
                        }
                    />
                    <Tab
                        label={
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                At Risk
                                <Chip
                                    label={projects.filter((p) => p.status === 'at-risk' || p.status === 'behind').length}
                                    size="small"
                                    color="error"
                                />
                            </Box>
                        }
                    />
                    <Tab
                        label={
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                Completed
                                <Chip label={projects.filter((p) => p.status === 'completed').length} size="small" color="success" />
                            </Box>
                        }
                    />
                </Tabs>
            </Box>

            {/* Project List */}
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {filteredProjects.length === 0 ? (
                    <Alert severity="info">No projects found matching the selected filters.</Alert>
                ) : (
                    filteredProjects.map((project) => (
                        <Card
                            key={project.id}
                            className="dark:bg-gray-800"
                            sx={{
                                cursor: 'pointer',
                                '&:hover': { bgcolor: 'action.hover' },
                            }}
                            onClick={() => onProjectClick?.(project.id)}
                        >
                            <CardContent>
                                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', mb: 2 }}>
                                    <Box sx={{ flex: 1 }}>
                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                                            <Typography variant="h6" className="dark:text-white">
                                                {project.name}
                                            </Typography>
                                            <Chip label={project.status.replace('-', ' ')} size="small" color={statusColors[project.status]} />
                                            <Chip label={project.priority} size="small" color={priorityColors[project.priority]} />
                                            <Chip label={project.health.overall} size="small" color={healthColors[project.health.overall]} />
                                        </Box>
                                        <Typography variant="body2" className="dark:text-gray-400">
                                            {project.description}
                                        </Typography>
                                    </Box>
                                    <Button variant="outlined" size="small" onClick={(e) => {
                                        e.stopPropagation();
                                        onViewDetails?.(project.id);
                                    }}>
                                        View Details
                                    </Button>
                                </Box>

                                {/* Progress Bar */}
                                <Box sx={{ mb: 2 }}>
                                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            Overall Progress
                                        </Typography>
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            {project.progress}%
                                        </Typography>
                                    </Box>
                                    <LinearProgress variant="determinate" value={project.progress} color={statusColors[project.status]} />
                                </Box>

                                {/* Project Metrics Grid */}
                                <Grid columns={4} gap={2}>
                                    {/* Budget */}
                                    <Box>
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            Budget
                                        </Typography>
                                        <Typography variant="body2" className="dark:text-white" sx={{ fontWeight: 600 }}>
                                            {formatCurrency(project.budget.spent)} / {formatCurrency(project.budget.allocated)}
                                        </Typography>
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            {formatCurrency(project.budget.remaining)} remaining
                                        </Typography>
                                    </Box>

                                    {/* Timeline */}
                                    <Box>
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            Timeline
                                        </Typography>
                                        <Typography variant="body2" className="dark:text-white" sx={{ fontWeight: 600 }}>
                                            {formatDate(project.timeline.startDate)} - {formatDate(project.timeline.endDate)}
                                        </Typography>
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            {project.timeline.daysRemaining > 0 ? `${project.timeline.daysRemaining} days remaining` : 'Completed'}
                                        </Typography>
                                    </Box>

                                    {/* Team */}
                                    <Box>
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            Team
                                        </Typography>
                                        <Typography variant="body2" className="dark:text-white" sx={{ fontWeight: 600 }}>
                                            {project.team.lead}
                                        </Typography>
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            {project.team.members} team members
                                        </Typography>
                                    </Box>

                                    {/* KPIs */}
                                    <Box>
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            Key Metrics
                                        </Typography>
                                        <Box sx={{ display: 'flex', gap: 1, mt: 0.5 }}>
                                            <Chip label={`Velocity: ${project.kpis.velocity}%`} size="small" variant="outlined" />
                                            <Chip label={`Quality: ${project.kpis.quality}%`} size="small" variant="outlined" />
                                        </Box>
                                    </Box>
                                </Grid>

                                {/* Health Indicators */}
                                <Box sx={{ display: 'flex', gap: 2, mt: 2, pt: 2, borderTop: '1px solid', borderColor: 'divider' }}>
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                                        <Box
                                            sx={{
                                                width: 8,
                                                height: 8,
                                                borderRadius: '50%',
                                                bgcolor: project.health.budget === 'healthy' ? 'success.main' : project.health.budget === 'warning' ? 'warning.main' : 'error.main',
                                            }}
                                        />
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            Budget: {project.health.budget}
                                        </Typography>
                                    </Box>
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                                        <Box
                                            sx={{
                                                width: 8,
                                                height: 8,
                                                borderRadius: '50%',
                                                bgcolor: project.health.timeline === 'healthy' ? 'success.main' : project.health.timeline === 'warning' ? 'warning.main' : 'error.main',
                                            }}
                                        />
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            Timeline: {project.health.timeline}
                                        </Typography>
                                    </Box>
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                                        <Box
                                            sx={{
                                                width: 8,
                                                height: 8,
                                                borderRadius: '50%',
                                                bgcolor: project.health.scope === 'healthy' ? 'success.main' : project.health.scope === 'warning' ? 'warning.main' : 'error.main',
                                            }}
                                        />
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            Scope: {project.health.scope}
                                        </Typography>
                                    </Box>
                                </Box>
                            </CardContent>
                        </Card>
                    ))
                )}
            </Box>
        </Box>
    );
};

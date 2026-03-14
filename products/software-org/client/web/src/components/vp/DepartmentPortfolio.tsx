/**
 * Department Portfolio Component
 *
 * VP-level multi-department oversight component showing cross-department KPIs,
 * department health tracking, comparison metrics, and strategic alignment.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import {
    Grid,
    Card,
    Box,
    Chip,
    LinearProgress,
    Tabs,
    Tab,
    Alert,
    Button,
    Typography,
    Stack,
} from '@ghatana/design-system';
import { KpiCard } from '@/shared/components/org';

/**
 * Department performance data
 */
export interface DepartmentPerformance {
    id: string;
    name: string;
    headcount: number;
    velocity: number; // 0-100
    quality: number; // 0-100
    satisfaction: number; // 0-5
    budgetUtilization: number; // 0-100
    openPositions: number;
    status: 'healthy' | 'warning' | 'critical';
    initiatives: number;
    completedInitiatives: number;
    kpis: {
        deploymentFrequency: number;
        leadTime: number; // hours
        mttr: number; // hours
        changeFailureRate: number; // 0-100
    };
}

/**
 * Strategic initiative data
 */
export interface StrategicInitiative {
    id: string;
    name: string;
    departmentId: string;
    departmentName: string;
    progress: number; // 0-100
    status: 'on-track' | 'at-risk' | 'blocked';
    priority: 'high' | 'medium' | 'low';
    startDate: string;
    targetDate: string;
    owner: string;
    dependencies: string[];
}

/**
 * Portfolio metrics aggregation
 */
export interface PortfolioMetrics {
    totalHeadcount: number;
    totalBudget: number;
    budgetUtilized: number;
    avgVelocity: number;
    avgQuality: number;
    avgSatisfaction: number;
    openPositions: number;
    activeInitiatives: number;
    completedInitiatives: number;
    departmentCount: number;
}

/**
 * Department Portfolio Props
 */
export interface DepartmentPortfolioProps {
    /** Portfolio-level aggregated metrics */
    portfolioMetrics: PortfolioMetrics;
    /** Individual department performance data */
    departments: DepartmentPerformance[];
    /** Cross-department strategic initiatives */
    initiatives: StrategicInitiative[];
    /** Callback when department is selected */
    onDepartmentClick?: (departmentId: string) => void;
    /** Callback when initiative is selected */
    onInitiativeClick?: (initiativeId: string) => void;
    /** Callback when view all departments is clicked */
    onViewAllDepartments?: () => void;
    /** Callback when export report is clicked */
    onExportReport?: () => void;
}

/**
 * Department Portfolio Component
 *
 * Provides VP-level visibility across all departments with:
 * - Portfolio-level KPIs (headcount, budget, velocity, satisfaction)
 * - Department comparison cards with health status
 * - Strategic initiatives tracking
 * - Department filtering (All/Healthy/Warning/Critical)
 * - Cross-department insights
 *
 * Reuses @ghatana/design-system components and shared org KPI cards:
 * - KpiCard (4 portfolio KPIs)
 * - Grid (responsive layouts)
 * - Card (department cards, initiatives)
 * - Chip (status, priority indicators)
 * - LinearProgress (velocity, quality, budget bars)
 * - Tabs (department filtering)
 * - Alert (warnings, empty states)
 *
 * @example
 * ```tsx
 * <DepartmentPortfolio
 *   portfolioMetrics={metrics}
 *   departments={departmentList}
 *   initiatives={initiativeList}
 *   onDepartmentClick={(id) => navigate(`/vp/departments/${id}`)}
 *   onExportReport={() => downloadReport()}
 * />
 * ```
 */
export const DepartmentPortfolio: React.FC<DepartmentPortfolioProps> = ({
    portfolioMetrics,
    departments,
    initiatives,
    onDepartmentClick,
    onInitiativeClick,
    onViewAllDepartments,
    onExportReport,
}) => {
    const [selectedTab, setSelectedTab] = useState<'all' | 'healthy' | 'warning' | 'critical'>('all');

    // Filter departments based on selected tab
    const filteredDepartments = departments.filter((dept) => {
        if (selectedTab === 'all') return true;
        return dept.status === selectedTab;
    });

    // Count departments by status
    const statusCounts = {
        healthy: departments.filter((d) => d.status === 'healthy').length,
        warning: departments.filter((d) => d.status === 'warning').length,
        critical: departments.filter((d) => d.status === 'critical').length,
    };

    // Get status color
    const getStatusColor = (status: 'healthy' | 'warning' | 'critical'): 'success' | 'warning' | 'error' => {
        switch (status) {
            case 'healthy':
                return 'success';
            case 'warning':
                return 'warning';
            case 'critical':
                return 'error';
        }
    };

    // Get priority color
    const getPriorityColor = (priority: 'high' | 'medium' | 'low'): 'error' | 'warning' | 'default' => {
        switch (priority) {
            case 'high':
                return 'error';
            case 'medium':
                return 'warning';
            case 'low':
                return 'default';
        }
    };

    // Get initiative status color
    const getInitiativeStatusColor = (status: 'on-track' | 'at-risk' | 'blocked'): 'success' | 'warning' | 'error' => {
        switch (status) {
            case 'on-track':
                return 'success';
            case 'at-risk':
                return 'warning';
            case 'blocked':
                return 'error';
        }
    };

    // Format currency
    const formatCurrency = (amount: number): string => {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 0,
            maximumFractionDigits: 0,
        }).format(amount);
    };

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Department Portfolio
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Cross-department overview and strategic alignment
                    </Typography>
                </Box>
                <Stack direction="row" spacing={2}>
                    {onViewAllDepartments && (
                        <Button variant="outline" size="md" onClick={onViewAllDepartments}>
                            View All Departments
                        </Button>
                    )}
                    {onExportReport && (
                        <Button variant="primary" size="md" onClick={onExportReport}>
                            Export Report
                        </Button>
                    )}
                </Stack>
            </Box>

            {/* Portfolio KPIs */}
            <Grid columns={4} gap={4}>
                <KpiCard
                    label="Total Headcount"
                    value={portfolioMetrics.totalHeadcount}
                    description={`Across ${portfolioMetrics.departmentCount} departments`}
                    status="healthy"
                />

                <KpiCard
                    label="Budget Utilization"
                    value={`${Math.round(portfolioMetrics.budgetUtilized)}%`}
                    description={`${formatCurrency(portfolioMetrics.budgetUtilized * portfolioMetrics.totalBudget / 100)} of ${formatCurrency(portfolioMetrics.totalBudget)}`}
                    status={portfolioMetrics.budgetUtilized > 90 ? 'warning' : 'healthy'}
                />

                <KpiCard
                    label="Avg Velocity"
                    value={`${Math.round(portfolioMetrics.avgVelocity)}%`}
                    description="Portfolio average"
                    status={portfolioMetrics.avgVelocity >= 80 ? 'healthy' : 'warning'}
                />

                <KpiCard
                    label="Satisfaction"
                    value={portfolioMetrics.avgSatisfaction.toFixed(1)}
                    description="Employee satisfaction"
                    status={portfolioMetrics.avgSatisfaction >= 4.0 ? 'healthy' : 'warning'}
                />
            </Grid>

            {/* Department Status Tabs */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`All Departments (${departments.length})`} value="all" />
                    <Tab label={`Healthy (${statusCounts.healthy})`} value="healthy" />
                    <Tab label={`Warning (${statusCounts.warning})`} value="warning" />
                    <Tab label={`Critical (${statusCounts.critical})`} value="critical" />
                </Tabs>

                {/* Warnings for critical/warning departments */}
                {selectedTab === 'critical' && statusCounts.critical > 0 && (
                    <Box className="p-4">
                        <Alert severity="error" className="mb-4">
                            {statusCounts.critical} department{statusCounts.critical > 1 ? 's' : ''} require immediate attention
                        </Alert>
                    </Box>
                )}
                {selectedTab === 'warning' && statusCounts.warning > 0 && (
                    <Box className="p-4">
                        <Alert severity="warning" className="mb-4">
                            {statusCounts.warning} department{statusCounts.warning > 1 ? 's' : ''} need monitoring
                        </Alert>
                    </Box>
                )}

                {/* Department Cards Grid */}
                <Box className="p-4">
                    {filteredDepartments.length === 0 ? (
                        <Alert severity="info">
                            No departments found for the selected filter
                        </Alert>
                    ) : (
                        <Grid columns={2} gap={4}>
                            {filteredDepartments.map((dept) => (
                                <Card
                                    key={dept.id}
                                    className="cursor-pointer hover:shadow-lg transition-shadow"
                                    onClick={() => onDepartmentClick?.(dept.id)}
                                >
                                    <Box className="p-4">
                                        {/* Department Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box>
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                    {dept.name}
                                                </Typography>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {dept.headcount} team members • {dept.openPositions} open positions
                                                </Typography>
                                            </Box>
                                            <Chip
                                                label={dept.status}
                                                color={getStatusColor(dept.status)}
                                                size="small"
                                            />
                                        </Box>

                                        {/* Key Metrics */}
                                        <Stack spacing={2}>
                                            {/* Velocity */}
                                            <Box>
                                                <Box className="flex items-center justify-between mb-1">
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        Velocity
                                                    </Typography>
                                                    <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                        {dept.velocity}%
                                                    </Typography>
                                                </Box>
                                                <LinearProgress
                                                    variant="determinate"
                                                    value={dept.velocity}
                                                    color={dept.velocity >= 80 ? 'success' : dept.velocity >= 60 ? 'warning' : 'error'}
                                                />
                                            </Box>

                                            {/* Quality */}
                                            <Box>
                                                <Box className="flex items-center justify-between mb-1">
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        Quality Score
                                                    </Typography>
                                                    <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                        {dept.quality}%
                                                    </Typography>
                                                </Box>
                                                <LinearProgress
                                                    variant="determinate"
                                                    value={dept.quality}
                                                    color={dept.quality >= 90 ? 'success' : dept.quality >= 75 ? 'warning' : 'error'}
                                                />
                                            </Box>

                                            {/* Budget */}
                                            <Box>
                                                <Box className="flex items-center justify-between mb-1">
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        Budget
                                                    </Typography>
                                                    <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                        {dept.budgetUtilization}%
                                                    </Typography>
                                                </Box>
                                                <LinearProgress
                                                    variant="determinate"
                                                    value={dept.budgetUtilization}
                                                    color={dept.budgetUtilization <= 85 ? 'success' : dept.budgetUtilization <= 95 ? 'warning' : 'error'}
                                                />
                                            </Box>
                                        </Stack>

                                        {/* Initiatives Summary */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                Initiatives: {dept.completedInitiatives}/{dept.initiatives} completed
                                            </Typography>
                                        </Box>

                                        {/* KPI Summary */}
                                        <Box className="mt-3 grid grid-cols-2 gap-2">
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Deploy Freq
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {dept.kpis.deploymentFrequency}/day
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Lead Time
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {dept.kpis.leadTime}h
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    MTTR
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {dept.kpis.mttr}h
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Change Failure
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {dept.kpis.changeFailureRate}%
                                                </Typography>
                                            </Box>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    )}
                </Box>
            </Card>

            {/* Strategic Initiatives */}
            <Card>
                <Box className="p-4 border-b border-slate-200 dark:border-neutral-700">
                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                        Cross-Department Initiatives
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Strategic initiatives across departments
                    </Typography>
                </Box>

                <Box className="p-4">
                    {initiatives.length === 0 ? (
                        <Alert severity="info">
                            No active initiatives found
                        </Alert>
                    ) : (
                        <Stack spacing={3}>
                            {initiatives.map((initiative) => (
                                <Card
                                    key={initiative.id}
                                    className="cursor-pointer hover:shadow-md transition-shadow"
                                    onClick={() => onInitiativeClick?.(initiative.id)}
                                >
                                    <Box className="p-4">
                                        {/* Initiative Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {initiative.name}
                                                    </Typography>
                                                    <Chip
                                                        label={initiative.priority}
                                                        color={getPriorityColor(initiative.priority)}
                                                        size="small"
                                                    />
                                                    <Chip
                                                        label={initiative.status}
                                                        color={getInitiativeStatusColor(initiative.status)}
                                                        size="small"
                                                    />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {initiative.departmentName} • Owner: {initiative.owner}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Progress Bar */}
                                        <Box className="mb-3">
                                            <Box className="flex items-center justify-between mb-1">
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    Progress
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {initiative.progress}%
                                                </Typography>
                                            </Box>
                                            <LinearProgress
                                                variant="determinate"
                                                value={initiative.progress}
                                                color={getInitiativeStatusColor(initiative.status)}
                                            />
                                        </Box>

                                        {/* Timeline */}
                                        <Box className="flex items-center gap-4 text-sm text-slate-600 dark:text-neutral-400">
                                            <span>Start: {new Date(initiative.startDate).toLocaleDateString()}</span>
                                            <span>•</span>
                                            <span>Target: {new Date(initiative.targetDate).toLocaleDateString()}</span>
                                            {initiative.dependencies.length > 0 && (
                                                <>
                                                    <span>•</span>
                                                    <span>{initiative.dependencies.length} dependencies</span>
                                                </>
                                            )}
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    )}
                </Box>
            </Card>
        </Box>
    );
};

/**
 * Mock data for development/testing
 */
export const mockPortfolioData = {
    portfolioMetrics: {
        totalHeadcount: 450,
        totalBudget: 15000000,
        budgetUtilized: 78,
        avgVelocity: 85,
        avgQuality: 88,
        avgSatisfaction: 4.2,
        openPositions: 25,
        activeInitiatives: 12,
        completedInitiatives: 8,
        departmentCount: 5,
    } as PortfolioMetrics,

    departments: [
        {
            id: 'dept-eng',
            name: 'Engineering',
            headcount: 180,
            velocity: 92,
            quality: 95,
            satisfaction: 4.5,
            budgetUtilization: 82,
            openPositions: 10,
            status: 'healthy',
            initiatives: 5,
            completedInitiatives: 3,
            kpis: {
                deploymentFrequency: 4.2,
                leadTime: 24,
                mttr: 1.5,
                changeFailureRate: 3,
            },
        },
        {
            id: 'dept-product',
            name: 'Product',
            headcount: 45,
            velocity: 88,
            quality: 90,
            satisfaction: 4.3,
            budgetUtilization: 75,
            openPositions: 3,
            status: 'healthy',
            initiatives: 3,
            completedInitiatives: 2,
            kpis: {
                deploymentFrequency: 3.8,
                leadTime: 32,
                mttr: 2.0,
                changeFailureRate: 4,
            },
        },
        {
            id: 'dept-qa',
            name: 'QA',
            headcount: 60,
            velocity: 78,
            quality: 85,
            satisfaction: 4.0,
            budgetUtilization: 88,
            openPositions: 5,
            status: 'warning',
            initiatives: 2,
            completedInitiatives: 1,
            kpis: {
                deploymentFrequency: 3.5,
                leadTime: 40,
                mttr: 3.0,
                changeFailureRate: 6,
            },
        },
        {
            id: 'dept-devops',
            name: 'DevOps',
            headcount: 35,
            velocity: 90,
            quality: 92,
            satisfaction: 4.4,
            budgetUtilization: 70,
            openPositions: 2,
            status: 'healthy',
            initiatives: 2,
            completedInitiatives: 2,
            kpis: {
                deploymentFrequency: 5.0,
                leadTime: 18,
                mttr: 1.0,
                changeFailureRate: 2,
            },
        },
        {
            id: 'dept-design',
            name: 'Design',
            headcount: 30,
            velocity: 75,
            quality: 82,
            satisfaction: 3.8,
            budgetUtilization: 95,
            openPositions: 5,
            status: 'warning',
            initiatives: 1,
            completedInitiatives: 0,
            kpis: {
                deploymentFrequency: 2.5,
                leadTime: 48,
                mttr: 4.0,
                changeFailureRate: 8,
            },
        },
    ] as DepartmentPerformance[],

    initiatives: [
        {
            id: 'init-1',
            name: 'Platform Modernization',
            departmentId: 'dept-eng',
            departmentName: 'Engineering',
            progress: 75,
            status: 'on-track',
            priority: 'high',
            startDate: '2025-01-15',
            targetDate: '2025-06-30',
            owner: 'John Director',
            dependencies: ['init-4'],
        },
        {
            id: 'init-2',
            name: 'Mobile App Redesign',
            departmentId: 'dept-product',
            departmentName: 'Product',
            progress: 45,
            status: 'at-risk',
            priority: 'high',
            startDate: '2025-02-01',
            targetDate: '2025-05-31',
            owner: 'Sarah Manager',
            dependencies: [],
        },
        {
            id: 'init-3',
            name: 'Security Compliance Update',
            departmentId: 'dept-devops',
            departmentName: 'DevOps',
            progress: 90,
            status: 'on-track',
            priority: 'high',
            startDate: '2024-11-01',
            targetDate: '2025-03-31',
            owner: 'Mike Lead',
            dependencies: [],
        },
        {
            id: 'init-4',
            name: 'Test Automation Framework',
            departmentId: 'dept-qa',
            departmentName: 'QA',
            progress: 30,
            status: 'blocked',
            priority: 'medium',
            startDate: '2025-01-01',
            targetDate: '2025-04-30',
            owner: 'Lisa Tester',
            dependencies: [],
        },
    ] as StrategicInitiative[],
};

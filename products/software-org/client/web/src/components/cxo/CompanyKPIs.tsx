/**
 * Company KPIs Component
 *
 * CXO-level company KPIs component with top-level metrics, strategic goals,
 * financial performance, and growth tracking.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import {
    Grid,
    Card,
    KpiCard,
    Box,
    Chip,
    LinearProgress,
    Tabs,
    Tab,
    Alert,
    Button,
    Typography,
    Stack,
} from '@ghatana/ui';

/**
 * Company-level KPI
 */
export interface CompanyKPI {
    id: string;
    category: 'financial' | 'growth' | 'operational' | 'customer' | 'employee';
    name: string;
    value: string;
    target: string;
    progress: number; // 0-100
    trend: 'up' | 'down' | 'stable';
    trendValue: string; // e.g., "+12%" or "-3%"
    period: 'monthly' | 'quarterly' | 'yearly';
    status: 'exceeding' | 'on-track' | 'below' | 'critical';
    description: string;
}

/**
 * Strategic goal
 */
export interface StrategicGoal {
    id: string;
    title: string;
    category: 'revenue' | 'market' | 'product' | 'team' | 'efficiency';
    targetDate: string;
    progress: number; // 0-100
    status: 'ahead' | 'on-track' | 'at-risk' | 'delayed';
    owner: string;
    milestones: {
        id: string;
        title: string;
        completed: boolean;
        targetDate: string;
    }[];
}

/**
 * Growth metric
 */
export interface GrowthMetric {
    id: string;
    name: string;
    current: number;
    previous: number;
    growthRate: number; // Percentage
    target: number;
    unit: string;
    status: 'exceeding' | 'on-track' | 'below';
}

/**
 * Financial health indicator
 */
export interface FinancialHealth {
    id: string;
    metric: string;
    value: string;
    benchmark: string;
    status: 'healthy' | 'warning' | 'critical';
    description: string;
}

/**
 * Company KPIs Props
 */
export interface CompanyKPIsProps {
    /** Company-level KPIs */
    kpis: CompanyKPI[];
    /** Strategic goals */
    goals: StrategicGoal[];
    /** Growth metrics */
    growthMetrics: GrowthMetric[];
    /** Financial health indicators */
    financialHealth: FinancialHealth[];
    /** Callback when KPI is clicked */
    onKPIClick?: (kpiId: string) => void;
    /** Callback when goal is clicked */
    onGoalClick?: (goalId: string) => void;
    /** Callback when export is clicked */
    onExportDashboard?: () => void;
}

/**
 * Company KPIs Component
 *
 * Provides CXO-level KPI tracking with:
 * - Category-based KPI dashboard (Financial, Growth, Operational, Customer, Employee)
 * - Strategic goal tracking with milestones
 * - Growth metrics with trend analysis
 * - Financial health indicators
 * - Tab-based navigation (All KPIs, Goals, Growth, Health)
 *
 * Reuses @ghatana/ui components:
 * - KpiCard (summary metrics)
 * - Grid (responsive layouts)
 * - Card (KPI cards, goal cards)
 * - Chip (status, category, trend indicators)
 * - LinearProgress (progress bars)
 * - Tabs (navigation)
 * - Alert (warnings, critical status)
 *
 * @example
 * ```tsx
 * <CompanyKPIs
 *   kpis={kpiList}
 *   goals={goalList}
 *   growthMetrics={growthList}
 *   financialHealth={healthList}
 *   onKPIClick={(id) => navigate(`/cxo/kpis/${id}`)}
 * />
 * ```
 */
export const CompanyKPIs: React.FC<CompanyKPIsProps> = ({
    kpis,
    goals,
    growthMetrics,
    financialHealth,
    onKPIClick,
    onGoalClick,
    onExportDashboard,
}) => {
    const [selectedTab, setSelectedTab] = useState<'all' | 'goals' | 'growth' | 'health'>('all');
    const [selectedCategory, setSelectedCategory] = useState<'all' | 'financial' | 'growth' | 'operational' | 'customer' | 'employee'>('all');

    // Get category color
    const getCategoryColor = (category: string): 'default' | 'warning' | 'error' => {
        switch (category) {
            case 'financial':
                return 'error';
            case 'growth':
                return 'error';
            case 'operational':
                return 'warning';
            case 'customer':
                return 'warning';
            case 'employee':
                return 'default';
            case 'revenue':
                return 'error';
            case 'market':
                return 'warning';
            case 'product':
                return 'error';
            case 'team':
                return 'default';
            case 'efficiency':
                return 'warning';
            default:
                return 'default';
        }
    };

    // Get status color
    const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'exceeding':
            case 'ahead':
            case 'healthy':
                return 'success';
            case 'on-track':
                return 'success';
            case 'below':
            case 'at-risk':
            case 'warning':
                return 'warning';
            case 'critical':
            case 'delayed':
                return 'error';
            default:
                return 'default';
        }
    };

    // Get trend icon
    const getTrendIcon = (trend: 'up' | 'down' | 'stable'): string => {
        switch (trend) {
            case 'up':
                return '↑';
            case 'down':
                return '↓';
            case 'stable':
                return '→';
        }
    };

    // Get trend color
    const getTrendColor = (trend: 'up' | 'down' | 'stable'): string => {
        switch (trend) {
            case 'up':
                return 'text-green-600';
            case 'down':
                return 'text-red-600';
            case 'stable':
                return 'text-slate-600 dark:text-neutral-400';
        }
    };

    // Filter KPIs by category
    const filteredKPIs = selectedCategory === 'all' ? kpis : kpis.filter((kpi) => kpi.category === selectedCategory);

    // Count KPIs by status
    const statusCounts = {
        critical: kpis.filter((k) => k.status === 'critical').length,
        below: kpis.filter((k) => k.status === 'below').length,
        onTrack: kpis.filter((k) => k.status === 'on-track').length,
        exceeding: kpis.filter((k) => k.status === 'exceeding').length,
    };

    // Count goals at risk
    const goalsAtRisk = goals.filter((g) => g.status === 'at-risk' || g.status === 'delayed').length;

    // Count critical financial health
    const criticalHealth = financialHealth.filter((h) => h.status === 'critical').length;

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Company KPIs
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Top-level metrics and strategic goals
                    </Typography>
                </Box>
                {onExportDashboard && (
                    <Button variant="primary" size="md" onClick={onExportDashboard}>
                        Export Dashboard
                    </Button>
                )}
            </Box>

            {/* Summary KPIs */}
            <Grid columns={4} gap={4}>
                <KpiCard label="Critical KPIs" value={statusCounts.critical} description="Require immediate attention" status={statusCounts.critical > 0 ? 'error' : 'healthy'} />

                <KpiCard label="Below Target" value={statusCounts.below} description="Need improvement" status={statusCounts.below > 5 ? 'warning' : 'healthy'} />

                <KpiCard label="On Track" value={statusCounts.onTrack} description="Meeting targets" status="healthy" />

                <KpiCard label="Exceeding" value={statusCounts.exceeding} description="Outperforming targets" status="healthy" />
            </Grid>

            {/* Critical Alerts */}
            {(statusCounts.critical > 0 || goalsAtRisk > 0 || criticalHealth > 0) && (
                <Alert severity="error">
                    {statusCounts.critical > 0 && `${statusCounts.critical} critical KPI${statusCounts.critical > 1 ? 's' : ''} `}
                    {goalsAtRisk > 0 && `${goalsAtRisk} goal${goalsAtRisk > 1 ? 's' : ''} at risk `}
                    {criticalHealth > 0 && `${criticalHealth} critical health indicator${criticalHealth > 1 ? 's' : ''}`}
                </Alert>
            )}

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`All KPIs (${kpis.length})`} value="all" />
                    <Tab label={`Goals (${goals.length})`} value="goals" />
                    <Tab label={`Growth (${growthMetrics.length})`} value="growth" />
                    <Tab label={`Health (${financialHealth.length})`} value="health" />
                </Tabs>

                {/* All KPIs Tab */}
                {selectedTab === 'all' && (
                    <Box className="p-4">
                        {/* Category Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Chip label="All" color={selectedCategory === 'all' ? 'error' : 'default'} onClick={() => setSelectedCategory('all')} />
                            <Chip label="Financial" color={selectedCategory === 'financial' ? 'error' : 'default'} onClick={() => setSelectedCategory('financial')} />
                            <Chip label="Growth" color={selectedCategory === 'growth' ? 'error' : 'default'} onClick={() => setSelectedCategory('growth')} />
                            <Chip label="Operational" color={selectedCategory === 'operational' ? 'default' : 'default'} onClick={() => setSelectedCategory('operational')} />
                            <Chip label="Customer" color={selectedCategory === 'customer' ? 'default' : 'default'} onClick={() => setSelectedCategory('customer')} />
                            <Chip label="Employee" color={selectedCategory === 'employee' ? 'default' : 'default'} onClick={() => setSelectedCategory('employee')} />
                        </Stack>

                        {/* KPI Grid */}
                        <Grid columns={2} gap={4}>
                            {filteredKPIs.map((kpi) => (
                                <Card key={kpi.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onKPIClick?.(kpi.id)}>
                                    <Box className="p-4">
                                        {/* KPI Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {kpi.name}
                                                    </Typography>
                                                    <Chip label={kpi.category} color={getCategoryColor(kpi.category)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {kpi.description}
                                                </Typography>
                                            </Box>
                                            <Chip label={kpi.status} color={getStatusColor(kpi.status)} size="small" />
                                        </Box>

                                        {/* KPI Values */}
                                        <Box className="mb-3">
                                            <Box className="flex items-baseline gap-2 mb-1">
                                                <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                                                    {kpi.value}
                                                </Typography>
                                                <Typography variant="body2" className="text-slate-500 dark:text-neutral-400">
                                                    / {kpi.target}
                                                </Typography>
                                                <Chip
                                                    label={kpi.trendValue}
                                                    color={kpi.trend === 'up' ? 'success' : kpi.trend === 'down' ? 'error' : 'default'}
                                                    size="small"
                                                />
                                            </Box>
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                {kpi.period} metric
                                            </Typography>
                                        </Box>

                                        {/* Progress */}
                                        <Box>
                                            <Box className="flex items-center justify-between mb-1">
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    Progress to Target
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {kpi.progress}%
                                                </Typography>
                                            </Box>
                                            <LinearProgress variant="determinate" value={kpi.progress} color={getStatusColor(kpi.status)} />
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}

                {/* Goals Tab */}
                {selectedTab === 'goals' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Strategic Goals
                        </Typography>

                        {goals.length === 0 ? (
                            <Alert severity="info">No strategic goals defined</Alert>
                        ) : (
                            <Stack spacing={3}>
                                {goals.map((goal) => (
                                    <Card key={goal.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onGoalClick?.(goal.id)}>
                                        <Box className="p-4">
                                            {/* Goal Header */}
                                            <Box className="flex items-start justify-between mb-3">
                                                <Box className="flex-1">
                                                    <Box className="flex items-center gap-2 mb-1">
                                                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                            {goal.title}
                                                        </Typography>
                                                        <Chip label={goal.category} color={getCategoryColor(goal.category)} size="small" />
                                                        <Chip label={goal.status} color={getStatusColor(goal.status)} size="small" />
                                                    </Box>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        Owner: {goal.owner} • Target: {new Date(goal.targetDate).toLocaleDateString()}
                                                    </Typography>
                                                </Box>
                                            </Box>

                                            {/* Progress */}
                                            <Box className="mb-3">
                                                <Box className="flex items-center justify-between mb-1">
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        Progress
                                                    </Typography>
                                                    <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                        {goal.progress}%
                                                    </Typography>
                                                </Box>
                                                <LinearProgress variant="determinate" value={goal.progress} color={getStatusColor(goal.status)} />
                                            </Box>

                                            {/* Milestones */}
                                            <Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-2">
                                                    Milestones ({goal.milestones.filter((m) => m.completed).length}/{goal.milestones.length} completed)
                                                </Typography>
                                                <Stack spacing={1}>
                                                    {goal.milestones.map((milestone) => (
                                                        <Box key={milestone.id} className="flex items-center gap-2">
                                                            <Box
                                                                className={`w-4 h-4 rounded-full border-2 ${milestone.completed
                                                                        ? 'bg-green-500 border-green-500'
                                                                        : 'border-slate-300 dark:border-neutral-600'
                                                                    }`}
                                                            />
                                                            <Typography
                                                                variant="body2"
                                                                className={milestone.completed ? 'text-slate-500 dark:text-neutral-400 line-through' : 'text-slate-900 dark:text-neutral-100'}
                                                            >
                                                                {milestone.title}
                                                            </Typography>
                                                            <Typography variant="caption" className="text-slate-400 dark:text-neutral-500">
                                                                {new Date(milestone.targetDate).toLocaleDateString()}
                                                            </Typography>
                                                        </Box>
                                                    ))}
                                                </Stack>
                                            </Box>
                                        </Box>
                                    </Card>
                                ))}
                            </Stack>
                        )}
                    </Box>
                )}

                {/* Growth Tab */}
                {selectedTab === 'growth' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Growth Metrics
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {growthMetrics.map((metric) => (
                                <Card key={metric.id}>
                                    <Box className="p-4">
                                        {/* Metric Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                {metric.name}
                                            </Typography>
                                            <Chip label={metric.status} color={getStatusColor(metric.status)} size="small" />
                                        </Box>

                                        {/* Current vs Previous */}
                                        <Box className="mb-3">
                                            <Box className="flex items-baseline gap-2 mb-1">
                                                <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                                                    {metric.current.toLocaleString()} {metric.unit}
                                                </Typography>
                                                <Chip
                                                    label={`${metric.growthRate >= 0 ? '+' : ''}${metric.growthRate}%`}
                                                    color={metric.growthRate >= 0 ? 'success' : 'error'}
                                                    size="small"
                                                />
                                            </Box>
                                            <Typography variant="body2" className="text-slate-500 dark:text-neutral-400">
                                                Previous: {metric.previous.toLocaleString()} {metric.unit}
                                            </Typography>
                                        </Box>

                                        {/* Target Progress */}
                                        <Box>
                                            <Box className="flex items-center justify-between mb-1">
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    Target: {metric.target.toLocaleString()} {metric.unit}
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {Math.round((metric.current / metric.target) * 100)}%
                                                </Typography>
                                            </Box>
                                            <LinearProgress
                                                variant="determinate"
                                                value={Math.min((metric.current / metric.target) * 100, 100)}
                                                color={getStatusColor(metric.status)}
                                            />
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}

                {/* Health Tab */}
                {selectedTab === 'health' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Financial Health Indicators
                        </Typography>

                        <Stack spacing={3}>
                            {financialHealth.map((health) => (
                                <Card key={health.id} className={health.status === 'critical' ? 'border-2 border-red-500' : ''}>
                                    <Box className="p-4">
                                        <Box className="flex items-start justify-between mb-2">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {health.metric}
                                                    </Typography>
                                                    <Chip label={health.status} color={getStatusColor(health.status)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {health.description}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        <Grid columns={2} gap={3} className="mt-3">
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Current Value
                                                </Typography>
                                                <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                                    {health.value}
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Benchmark
                                                </Typography>
                                                <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                                    {health.benchmark}
                                                </Typography>
                                            </Box>
                                        </Grid>
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
export const mockCompanyKPIsData = {
    kpis: [
        {
            id: 'kpi-1',
            category: 'financial',
            name: 'Annual Recurring Revenue',
            value: '$48M',
            target: '$100M',
            progress: 48,
            trend: 'up',
            trendValue: '+25%',
            period: 'yearly',
            status: 'on-track',
            description: 'Total contracted revenue on annual basis',
        },
        {
            id: 'kpi-2',
            category: 'financial',
            name: 'Gross Margin',
            value: '78%',
            target: '75%',
            progress: 104,
            trend: 'up',
            trendValue: '+3%',
            period: 'quarterly',
            status: 'exceeding',
            description: 'Revenue minus cost of goods sold',
        },
        {
            id: 'kpi-3',
            category: 'growth',
            name: 'Customer Count',
            value: '1,350',
            target: '2,000',
            progress: 68,
            trend: 'up',
            trendValue: '+15%',
            period: 'monthly',
            status: 'on-track',
            description: 'Total active paying customers',
        },
        {
            id: 'kpi-4',
            category: 'customer',
            name: 'Net Revenue Retention',
            value: '125%',
            target: '120%',
            progress: 104,
            trend: 'up',
            trendValue: '+5%',
            period: 'quarterly',
            status: 'exceeding',
            description: 'Revenue retention from existing customers',
        },
        {
            id: 'kpi-5',
            category: 'operational',
            name: 'Platform Uptime',
            value: '99.95%',
            target: '99.9%',
            progress: 100,
            trend: 'stable',
            trendValue: '+0.05%',
            period: 'monthly',
            status: 'exceeding',
            description: 'Service availability percentage',
        },
        {
            id: 'kpi-6',
            category: 'employee',
            name: 'Employee NPS',
            value: '72',
            target: '70',
            progress: 103,
            trend: 'up',
            trendValue: '+5',
            period: 'quarterly',
            status: 'exceeding',
            description: 'Employee satisfaction and loyalty',
        },
    ] as CompanyKPI[],

    goals: [
        {
            id: 'goal-1',
            title: 'Achieve $100M ARR',
            category: 'revenue',
            targetDate: '2025-12-31',
            progress: 48,
            status: 'on-track',
            owner: 'CEO',
            milestones: [
                { id: 'm1', title: 'Reach $50M ARR', completed: true, targetDate: '2025-06-30' },
                { id: 'm2', title: 'Launch enterprise tier', completed: true, targetDate: '2025-03-31' },
                { id: 'm3', title: 'Expand to EMEA', completed: false, targetDate: '2025-09-30' },
                { id: 'm4', title: 'Close 10 enterprise deals', completed: false, targetDate: '2025-12-31' },
            ],
        },
        {
            id: 'goal-2',
            title: 'Launch AI Platform',
            category: 'product',
            targetDate: '2025-09-30',
            progress: 75,
            status: 'on-track',
            owner: 'CTO',
            milestones: [
                { id: 'm5', title: 'Complete architecture design', completed: true, targetDate: '2025-01-31' },
                { id: 'm6', title: 'Beta release', completed: true, targetDate: '2025-06-30' },
                { id: 'm7', title: 'General availability', completed: false, targetDate: '2025-09-30' },
            ],
        },
        {
            id: 'goal-3',
            title: 'Grow Team to 1,000',
            category: 'team',
            targetDate: '2025-12-31',
            progress: 65,
            status: 'at-risk',
            owner: 'CHRO',
            milestones: [
                { id: 'm8', title: 'Hire 200 engineers', completed: false, targetDate: '2025-08-31' },
                { id: 'm9', title: 'Open 3 new offices', completed: false, targetDate: '2025-10-31' },
                { id: 'm10', title: 'Establish talent pipeline', completed: true, targetDate: '2025-03-31' },
            ],
        },
    ] as StrategicGoal[],

    growthMetrics: [
        { id: 'growth-1', name: 'Monthly Recurring Revenue', current: 4000000, previous: 3500000, growthRate: 14, target: 8333333, unit: 'USD', status: 'on-track' },
        { id: 'growth-2', name: 'New Customers', current: 150, previous: 120, growthRate: 25, target: 200, unit: 'customers', status: 'on-track' },
        { id: 'growth-3', name: 'Active Users', current: 25000, previous: 20000, growthRate: 25, target: 50000, unit: 'users', status: 'on-track' },
        { id: 'growth-4', name: 'Product Adoption', current: 68, previous: 62, growthRate: 10, target: 80, unit: '%', status: 'on-track' },
    ] as GrowthMetric[],

    financialHealth: [
        {
            id: 'health-1',
            metric: 'Cash Runway',
            value: '18 months',
            benchmark: '12+ months',
            status: 'healthy',
            description: 'Months of operational cash reserves at current burn rate',
        },
        {
            id: 'health-2',
            metric: 'Burn Multiple',
            value: '1.8',
            benchmark: '< 2.0',
            status: 'healthy',
            description: 'Net burn divided by net new ARR',
        },
        {
            id: 'health-3',
            metric: 'Rule of 40',
            value: '47%',
            benchmark: '> 40%',
            status: 'healthy',
            description: 'Growth rate plus profit margin',
        },
        {
            id: 'health-4',
            metric: 'LTV/CAC Ratio',
            value: '3.2',
            benchmark: '> 3.0',
            status: 'healthy',
            description: 'Customer lifetime value to acquisition cost ratio',
        },
    ] as FinancialHealth[],
};

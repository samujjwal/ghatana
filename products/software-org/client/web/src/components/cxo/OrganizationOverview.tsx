/**
 * Organization Overview Component
 *
 * CXO-level organization-wide overview component showing company metrics,
 * department health, strategic initiatives, and executive dashboard.
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
 * Company-wide metrics
 */
export interface CompanyMetrics {
    totalHeadcount: number;
    departmentCount: number;
    revenue: number; // Annual revenue
    growthRate: number; // YoY growth percentage
    profitMargin: number; // Percentage
    customerSatisfaction: number; // 0-5 score
    employeeSatisfaction: number; // 0-5 score
    marketShare: number; // Percentage
}

/**
 * Department health summary
 */
export interface DepartmentHealth {
    id: string;
    name: string;
    headcount: number;
    health: 'excellent' | 'good' | 'fair' | 'poor';
    velocity: number; // 0-100
    budgetStatus: 'under' | 'on-track' | 'over';
    budgetUtilization: number; // 0-100
    openPositions: number;
    keyMetrics: {
        productivity: number; // 0-100
        quality: number; // 0-100
        satisfaction: number; // 0-5
        attrition: number; // Percentage
    };
}

/**
 * Strategic objective
 */
export interface StrategicObjective {
    id: string;
    title: string;
    description: string;
    category: 'growth' | 'efficiency' | 'innovation' | 'culture' | 'market';
    status: 'on-track' | 'at-risk' | 'achieved' | 'delayed';
    progress: number; // 0-100
    targetDate: string;
    owner: string;
    impact: 'high' | 'medium' | 'low';
    departments: string[];
}

/**
 * Financial summary
 */
export interface FinancialSummary {
    quarterlyRevenue: number;
    quarterlyExpenses: number;
    quarterlyProfit: number;
    yearlyRevenue: number;
    yearlyExpenses: number;
    yearlyProfit: number;
    cashReserves: number;
    burnRate: number; // Monthly
    runway: number; // Months
}

/**
 * Organization Overview Props
 */
export interface OrganizationOverviewProps {
    /** Company-wide metrics */
    companyMetrics: CompanyMetrics;
    /** Department health summaries */
    departments: DepartmentHealth[];
    /** Strategic objectives */
    objectives: StrategicObjective[];
    /** Financial summary */
    financialSummary: FinancialSummary;
    /** Callback when department is selected */
    onDepartmentClick?: (departmentId: string) => void;
    /** Callback when objective is selected */
    onObjectiveClick?: (objectiveId: string) => void;
    /** Callback when view financials is clicked */
    onViewFinancials?: () => void;
    /** Callback when export report is clicked */
    onExportReport?: () => void;
}

/**
 * Organization Overview Component
 *
 * Provides CXO-level visibility across the entire organization with:
 * - Company-level KPIs (headcount, revenue, growth, satisfaction)
 * - Department health dashboard
 * - Strategic objectives tracking
 * - Financial summary
 * - Tab-based navigation (Departments, Objectives, Financials)
 *
 * Reuses @ghatana/ui components:
 * - KpiCard (4 company KPIs)
 * - Grid (responsive layouts)
 * - Card (department cards, objective cards)
 * - Chip (status, health, category indicators)
 * - LinearProgress (progress bars, budget bars)
 * - Tabs (navigation)
 * - Alert (warnings, insights)
 *
 * @example
 * ```tsx
 * <OrganizationOverview
 *   companyMetrics={metrics}
 *   departments={deptList}
 *   objectives={objectiveList}
 *   financialSummary={financials}
 *   onDepartmentClick={(id) => navigate(`/cxo/departments/${id}`)}
 * />
 * ```
 */
export const OrganizationOverview: React.FC<OrganizationOverviewProps> = ({
    companyMetrics,
    departments,
    objectives,
    financialSummary,
    onDepartmentClick,
    onObjectiveClick,
    onViewFinancials,
    onExportReport,
}) => {
    const [selectedTab, setSelectedTab] = useState<'departments' | 'objectives' | 'financials'>('departments');

    // Get health color
    const getHealthColor = (health: 'excellent' | 'good' | 'fair' | 'poor'): 'success' | 'warning' | 'error' | 'default' => {
        switch (health) {
            case 'excellent':
                return 'success';
            case 'good':
                return 'success';
            case 'fair':
                return 'warning';
            case 'poor':
                return 'error';
        }
    };

    // Get budget status color
    const getBudgetStatusColor = (status: 'under' | 'on-track' | 'over'): 'success' | 'warning' | 'error' => {
        switch (status) {
            case 'under':
                return 'success';
            case 'on-track':
                return 'success';
            case 'over':
                return 'error';
        }
    };

    // Get objective status color
    const getObjectiveStatusColor = (status: 'on-track' | 'at-risk' | 'achieved' | 'delayed'): 'success' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'on-track':
                return 'success';
            case 'at-risk':
                return 'warning';
            case 'achieved':
                return 'success';
            case 'delayed':
                return 'error';
        }
    };

    // Get category color
    const getCategoryColor = (category: 'growth' | 'efficiency' | 'innovation' | 'culture' | 'market'): 'default' | 'warning' | 'error' => {
        switch (category) {
            case 'growth':
                return 'error';
            case 'efficiency':
                return 'warning';
            case 'innovation':
                return 'error';
            case 'culture':
                return 'default';
            case 'market':
                return 'warning';
        }
    };

    // Get impact color
    const getImpactColor = (impact: 'high' | 'medium' | 'low'): 'error' | 'warning' | 'default' => {
        switch (impact) {
            case 'high':
                return 'error';
            case 'medium':
                return 'warning';
            case 'low':
                return 'default';
        }
    };

    // Format currency
    const formatCurrency = (amount: number): string => {
        if (amount >= 1000000) {
            return `$${(amount / 1000000).toFixed(1)}M`;
        } else if (amount >= 1000) {
            return `$${(amount / 1000).toFixed(0)}K`;
        }
        return `$${amount.toFixed(0)}`;
    };

    // Count departments by health
    const healthCounts = {
        excellent: departments.filter((d) => d.health === 'excellent').length,
        good: departments.filter((d) => d.health === 'good').length,
        fair: departments.filter((d) => d.health === 'fair').length,
        poor: departments.filter((d) => d.health === 'poor').length,
    };

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Organization Overview
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Company-wide metrics and strategic dashboard
                    </Typography>
                </Box>
                <Stack direction="row" spacing={2}>
                    {onViewFinancials && (
                        <Button variant="outline" size="md" onClick={onViewFinancials}>
                            View Financials
                        </Button>
                    )}
                    {onExportReport && (
                        <Button variant="primary" size="md" onClick={onExportReport}>
                            Export Report
                        </Button>
                    )}
                </Stack>
            </Box>

            {/* Company KPIs */}
            <Grid columns={4} gap={4}>
                <KpiCard
                    label="Total Headcount"
                    value={companyMetrics.totalHeadcount}
                    description={`Across ${companyMetrics.departmentCount} departments`}
                    status="healthy"
                />

                <KpiCard
                    label="Annual Revenue"
                    value={formatCurrency(companyMetrics.revenue)}
                    description={`${companyMetrics.growthRate >= 0 ? '+' : ''}${companyMetrics.growthRate}% YoY`}
                    status={companyMetrics.growthRate >= 10 ? 'healthy' : 'warning'}
                />

                <KpiCard
                    label="Profit Margin"
                    value={`${companyMetrics.profitMargin}%`}
                    description="Net profit margin"
                    status={companyMetrics.profitMargin >= 20 ? 'healthy' : companyMetrics.profitMargin >= 10 ? 'warning' : 'error'}
                />

                <KpiCard
                    label="Employee Satisfaction"
                    value={companyMetrics.employeeSatisfaction.toFixed(1)}
                    description="Company average"
                    status={companyMetrics.employeeSatisfaction >= 4.0 ? 'healthy' : 'warning'}
                />
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`Departments (${departments.length})`} value="departments" />
                    <Tab label={`Objectives (${objectives.length})`} value="objectives" />
                    <Tab label="Financials" value="financials" />
                </Tabs>

                {/* Departments Tab */}
                {selectedTab === 'departments' && (
                    <Box className="p-4">
                        {/* Health Summary */}
                        {(healthCounts.fair > 0 || healthCounts.poor > 0) && (
                            <Alert severity={healthCounts.poor > 0 ? 'error' : 'warning'} className="mb-4">
                                {healthCounts.poor > 0
                                    ? `${healthCounts.poor} department${healthCounts.poor > 1 ? 's' : ''} in poor health require immediate attention`
                                    : `${healthCounts.fair} department${healthCounts.fair > 1 ? 's' : ''} need monitoring`}
                            </Alert>
                        )}

                        <Grid columns={2} gap={4}>
                            {departments.map((dept) => (
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
                                            <Stack direction="row" spacing={1}>
                                                <Chip label={dept.health} color={getHealthColor(dept.health)} size="small" />
                                                <Chip label={dept.budgetStatus} color={getBudgetStatusColor(dept.budgetStatus)} size="small" />
                                            </Stack>
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

                                            {/* Budget */}
                                            <Box>
                                                <Box className="flex items-center justify-between mb-1">
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        Budget Utilization
                                                    </Typography>
                                                    <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                        {dept.budgetUtilization}%
                                                    </Typography>
                                                </Box>
                                                <LinearProgress
                                                    variant="determinate"
                                                    value={dept.budgetUtilization}
                                                    color={dept.budgetUtilization <= 90 ? 'success' : dept.budgetUtilization <= 100 ? 'warning' : 'error'}
                                                />
                                            </Box>
                                        </Stack>

                                        {/* Department Metrics Grid */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={2} gap={2}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Productivity
                                                    </Typography>
                                                    <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                        {dept.keyMetrics.productivity}%
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Quality
                                                    </Typography>
                                                    <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                        {dept.keyMetrics.quality}%
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Satisfaction
                                                    </Typography>
                                                    <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                        {dept.keyMetrics.satisfaction.toFixed(1)}/5.0
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Attrition
                                                    </Typography>
                                                    <Typography variant="body2" className={`font-medium ${dept.keyMetrics.attrition > 10 ? 'text-red-600' : 'text-slate-900 dark:text-neutral-100'}`}>
                                                        {dept.keyMetrics.attrition}%
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

                {/* Objectives Tab */}
                {selectedTab === 'objectives' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Strategic Objectives
                        </Typography>

                        {objectives.length === 0 ? (
                            <Alert severity="info">No strategic objectives defined</Alert>
                        ) : (
                            <Stack spacing={3}>
                                {objectives.map((objective) => (
                                    <Card
                                        key={objective.id}
                                        className="cursor-pointer hover:shadow-md transition-shadow"
                                        onClick={() => onObjectiveClick?.(objective.id)}
                                    >
                                        <Box className="p-4">
                                            {/* Objective Header */}
                                            <Box className="flex items-start justify-between mb-2">
                                                <Box className="flex-1">
                                                    <Box className="flex items-center gap-2 mb-1">
                                                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                            {objective.title}
                                                        </Typography>
                                                        <Chip label={objective.category} color={getCategoryColor(objective.category)} size="small" />
                                                        <Chip label={objective.status} color={getObjectiveStatusColor(objective.status)} size="small" />
                                                        <Chip label={`${objective.impact} impact`} color={getImpactColor(objective.impact)} size="small" />
                                                    </Box>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {objective.description}
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
                                                        {objective.progress}%
                                                    </Typography>
                                                </Box>
                                                <LinearProgress
                                                    variant="determinate"
                                                    value={objective.progress}
                                                    color={getObjectiveStatusColor(objective.status)}
                                                />
                                            </Box>

                                            {/* Metadata */}
                                            <Box className="flex items-center gap-4 text-sm text-slate-600 dark:text-neutral-400">
                                                <span>Owner: {objective.owner}</span>
                                                <span>•</span>
                                                <span>Target: {new Date(objective.targetDate).toLocaleDateString()}</span>
                                                <span>•</span>
                                                <span>{objective.departments.length} departments involved</span>
                                            </Box>
                                        </Box>
                                    </Card>
                                ))}
                            </Stack>
                        )}
                    </Box>
                )}

                {/* Financials Tab */}
                {selectedTab === 'financials' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Financial Summary
                        </Typography>

                        {/* Quarterly Financials */}
                        <Card className="mb-4">
                            <Box className="p-4">
                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-3">
                                    Current Quarter
                                </Typography>
                                <Grid columns={3} gap={4}>
                                    <Box>
                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                            Revenue
                                        </Typography>
                                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                            {formatCurrency(financialSummary.quarterlyRevenue)}
                                        </Typography>
                                    </Box>
                                    <Box>
                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                            Expenses
                                        </Typography>
                                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                            {formatCurrency(financialSummary.quarterlyExpenses)}
                                        </Typography>
                                    </Box>
                                    <Box>
                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                            Profit
                                        </Typography>
                                        <Typography variant="h5" className={financialSummary.quarterlyProfit >= 0 ? 'text-green-600' : 'text-red-600'}>
                                            {formatCurrency(financialSummary.quarterlyProfit)}
                                        </Typography>
                                    </Box>
                                </Grid>
                            </Box>
                        </Card>

                        {/* Yearly Financials */}
                        <Card className="mb-4">
                            <Box className="p-4">
                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-3">
                                    Year to Date
                                </Typography>
                                <Grid columns={3} gap={4}>
                                    <Box>
                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                            Revenue
                                        </Typography>
                                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                            {formatCurrency(financialSummary.yearlyRevenue)}
                                        </Typography>
                                    </Box>
                                    <Box>
                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                            Expenses
                                        </Typography>
                                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                            {formatCurrency(financialSummary.yearlyExpenses)}
                                        </Typography>
                                    </Box>
                                    <Box>
                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                            Profit
                                        </Typography>
                                        <Typography variant="h5" className={financialSummary.yearlyProfit >= 0 ? 'text-green-600' : 'text-red-600'}>
                                            {formatCurrency(financialSummary.yearlyProfit)}
                                        </Typography>
                                    </Box>
                                </Grid>
                            </Box>
                        </Card>

                        {/* Cash & Runway */}
                        <Card>
                            <Box className="p-4">
                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-3">
                                    Cash Position
                                </Typography>
                                <Grid columns={3} gap={4}>
                                    <Box>
                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                            Cash Reserves
                                        </Typography>
                                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                            {formatCurrency(financialSummary.cashReserves)}
                                        </Typography>
                                    </Box>
                                    <Box>
                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                            Monthly Burn Rate
                                        </Typography>
                                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                            {formatCurrency(financialSummary.burnRate)}
                                        </Typography>
                                    </Box>
                                    <Box>
                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                            Runway
                                        </Typography>
                                        <Typography variant="h5" className={financialSummary.runway >= 18 ? 'text-green-600' : financialSummary.runway >= 12 ? 'text-yellow-600' : 'text-red-600'}>
                                            {financialSummary.runway} months
                                        </Typography>
                                    </Box>
                                </Grid>

                                {financialSummary.runway < 12 && (
                                    <Alert severity="warning" className="mt-4">
                                        Cash runway below 12 months - consider fundraising or cost reduction measures
                                    </Alert>
                                )}
                            </Box>
                        </Card>
                    </Box>
                )}
            </Card>
        </Box>
    );
};

/**
 * Mock data for development/testing
 */
export const mockOrganizationOverviewData = {
    companyMetrics: {
        totalHeadcount: 650,
        departmentCount: 8,
        revenue: 50000000,
        growthRate: 25,
        profitMargin: 22,
        customerSatisfaction: 4.3,
        employeeSatisfaction: 4.2,
        marketShare: 15,
    } as CompanyMetrics,

    departments: [
        {
            id: 'dept-eng',
            name: 'Engineering',
            headcount: 220,
            health: 'excellent',
            velocity: 92,
            budgetStatus: 'on-track',
            budgetUtilization: 85,
            openPositions: 15,
            keyMetrics: { productivity: 88, quality: 95, satisfaction: 4.5, attrition: 8 },
        },
        {
            id: 'dept-product',
            name: 'Product',
            headcount: 65,
            health: 'good',
            velocity: 88,
            budgetStatus: 'on-track',
            budgetUtilization: 80,
            openPositions: 5,
            keyMetrics: { productivity: 85, quality: 90, satisfaction: 4.3, attrition: 10 },
        },
        {
            id: 'dept-sales',
            name: 'Sales',
            headcount: 120,
            health: 'good',
            velocity: 85,
            budgetStatus: 'under',
            budgetUtilization: 75,
            openPositions: 8,
            keyMetrics: { productivity: 90, quality: 88, satisfaction: 4.1, attrition: 12 },
        },
        {
            id: 'dept-marketing',
            name: 'Marketing',
            headcount: 45,
            health: 'fair',
            velocity: 78,
            budgetStatus: 'over',
            budgetUtilization: 105,
            openPositions: 3,
            keyMetrics: { productivity: 75, quality: 82, satisfaction: 3.9, attrition: 15 },
        },
    ] as DepartmentHealth[],

    objectives: [
        {
            id: 'obj-1',
            title: 'Achieve $100M ARR by Q4',
            description: 'Scale revenue to $100M annual recurring revenue through new customer acquisition and upsells',
            category: 'growth',
            status: 'on-track',
            progress: 75,
            targetDate: '2025-12-31',
            owner: 'CEO',
            impact: 'high',
            departments: ['Sales', 'Marketing', 'Product'],
        },
        {
            id: 'obj-2',
            title: 'Launch AI-Powered Platform',
            description: 'Release next-generation AI features to maintain competitive advantage',
            category: 'innovation',
            status: 'at-risk',
            progress: 60,
            targetDate: '2025-09-30',
            owner: 'CTO',
            impact: 'high',
            departments: ['Engineering', 'Product'],
        },
        {
            id: 'obj-3',
            title: 'Improve Employee Retention',
            description: 'Reduce attrition to below 10% through culture and compensation initiatives',
            category: 'culture',
            status: 'on-track',
            progress: 65,
            targetDate: '2025-12-31',
            owner: 'CHRO',
            impact: 'medium',
            departments: ['HR', 'All'],
        },
    ] as StrategicObjective[],

    financialSummary: {
        quarterlyRevenue: 12500000,
        quarterlyExpenses: 9500000,
        quarterlyProfit: 3000000,
        yearlyRevenue: 48000000,
        yearlyExpenses: 37000000,
        yearlyProfit: 11000000,
        cashReserves: 25000000,
        burnRate: 2000000,
        runway: 12,
    } as FinancialSummary,
};

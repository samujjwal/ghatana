/**
 * Budget Tracker Component
 *
 * Department budget management and financial oversight for directors.
 * Tracks budget allocation, spending, forecasts, and variance analysis.
 *
 * REUSE: Grid, Card, LinearProgress, Chip from @ghatana/design-system and KpiCard from shared org components
 * PATTERN: Following PortfolioDashboard and ResourcePlanner patterns
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import {
    Box,
    Typography,
    Card,
    CardContent,
    Grid,
    Button,
    Chip,
    LinearProgress,
    Tabs,
    Tab,
    Alert,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
} from '@ghatana/design-system';
import { KpiCard } from '@/shared/components/org';

// ============================================================================
// TypeScript Interfaces
// ============================================================================

/**
 * Budget category with allocation and spending
 */
interface BudgetCategory {
    id: string;
    name: string;
    allocated: number;
    spent: number;
    committed: number; // Committed but not yet spent
    remaining: number;
    variance: number; // Percentage over/under
    status: 'on-track' | 'warning' | 'over-budget';
    subcategories?: BudgetSubcategory[];
}

/**
 * Budget subcategory
 */
interface BudgetSubcategory {
    id: string;
    name: string;
    allocated: number;
    spent: number;
    remaining: number;
}

/**
 * Budget forecast
 */
interface BudgetForecast {
    month: string;
    projected: number;
    actual?: number;
    trend: 'increasing' | 'decreasing' | 'stable';
}

/**
 * Budget request/approval
 */
interface BudgetRequest {
    id: string;
    requestedBy: string;
    category: string;
    amount: number;
    purpose: string;
    priority: 'critical' | 'high' | 'medium' | 'low';
    status: 'pending' | 'approved' | 'rejected';
    requestDate: Date;
    decision?: {
        approvedBy: string;
        approvedAmount?: number;
        decisionDate: Date;
        notes?: string;
    };
}

/**
 * Overall budget metrics
 */
interface BudgetMetrics {
    totalBudget: number;
    totalSpent: number;
    totalCommitted: number;
    totalRemaining: number;
    utilizationPercent: number;
    projectedEndOfYearSpend: number;
    variance: number; // Percentage over/under projected
    categoriesOverBudget: number;
    pendingRequests: number;
}

/**
 * Component props
 */
export interface BudgetTrackerProps {
    /** Budget categories */
    categories?: BudgetCategory[];
    /** Overall metrics */
    metrics?: BudgetMetrics;
    /** Budget forecasts */
    forecasts?: BudgetForecast[];
    /** Pending requests */
    requests?: BudgetRequest[];
    /** Callback when approving budget */
    onApproveBudget?: (requestId: string, amount: number) => void;
    /** Callback when rejecting budget */
    onRejectBudget?: (requestId: string, reason: string) => void;
    /** Callback when viewing details */
    onViewDetails?: (categoryId: string) => void;
    /** Callback when exporting report */
    onExportReport?: () => void;
}

// ============================================================================
// Mock Data
// ============================================================================

const mockMetrics: BudgetMetrics = {
    totalBudget: 5000000, // $5M
    totalSpent: 3200000, // $3.2M
    totalCommitted: 600000, // $600K
    totalRemaining: 1200000, // $1.2M
    utilizationPercent: 76, // (spent + committed) / total * 100
    projectedEndOfYearSpend: 4800000, // $4.8M
    variance: -4, // 4% under budget
    categoriesOverBudget: 1,
    pendingRequests: 3,
};

const mockCategories: BudgetCategory[] = [
    {
        id: 'cat-personnel',
        name: 'Personnel',
        allocated: 3000000,
        spent: 2100000,
        committed: 300000,
        remaining: 600000,
        variance: -20,
        status: 'on-track',
        subcategories: [
            { id: 'sub-salaries', name: 'Salaries', allocated: 2500000, spent: 1800000, remaining: 700000 },
            { id: 'sub-contractors', name: 'Contractors', allocated: 400000, spent: 250000, remaining: 150000 },
            { id: 'sub-benefits', name: 'Benefits', allocated: 100000, spent: 50000, remaining: 50000 },
        ],
    },
    {
        id: 'cat-infrastructure',
        name: 'Infrastructure',
        allocated: 800000,
        spent: 550000,
        committed: 150000,
        remaining: 100000,
        variance: -12.5,
        status: 'warning',
        subcategories: [
            { id: 'sub-cloud', name: 'Cloud Services', allocated: 500000, spent: 380000, remaining: 120000 },
            { id: 'sub-licenses', name: 'Software Licenses', allocated: 200000, spent: 120000, remaining: 80000 },
            { id: 'sub-hardware', name: 'Hardware', allocated: 100000, spent: 50000, remaining: 50000 },
        ],
    },
    {
        id: 'cat-projects',
        name: 'Projects',
        allocated: 1000000,
        spent: 450000,
        committed: 150000,
        remaining: 400000,
        variance: -40,
        status: 'on-track',
    },
    {
        id: 'cat-training',
        name: 'Training & Development',
        allocated: 150000,
        spent: 80000,
        committed: 0,
        remaining: 70000,
        variance: -46.7,
        status: 'on-track',
    },
    {
        id: 'cat-operations',
        name: 'Operations',
        allocated: 50000,
        spent: 20000,
        committed: 0,
        remaining: 30000,
        variance: -60,
        status: 'on-track',
    },
];

const mockForecasts: BudgetForecast[] = [
    { month: 'Jan', projected: 350000, actual: 320000, trend: 'stable' },
    { month: 'Feb', projected: 380000, actual: 390000, trend: 'increasing' },
    { month: 'Mar', projected: 400000, actual: 410000, trend: 'increasing' },
    { month: 'Apr', projected: 420000, actual: 400000, trend: 'decreasing' },
    { month: 'May', projected: 450000, actual: 440000, trend: 'stable' },
    { month: 'Jun', projected: 480000, trend: 'increasing' },
    { month: 'Jul', projected: 500000, trend: 'stable' },
    { month: 'Aug', projected: 520000, trend: 'increasing' },
    { month: 'Sep', projected: 540000, trend: 'stable' },
    { month: 'Oct', projected: 560000, trend: 'stable' },
    { month: 'Nov', projected: 580000, trend: 'stable' },
    { month: 'Dec', projected: 600000, trend: 'stable' },
];

const mockRequests: BudgetRequest[] = [
    {
        id: 'req-1',
        requestedBy: 'Sarah Manager',
        category: 'Personnel',
        amount: 150000,
        purpose: 'Hire 2 additional engineers for Platform Modernization project',
        priority: 'high',
        status: 'pending',
        requestDate: new Date('2025-11-20'),
    },
    {
        id: 'req-2',
        requestedBy: 'John Tech Lead',
        category: 'Infrastructure',
        amount: 50000,
        purpose: 'Additional cloud capacity for Q1 2026',
        priority: 'medium',
        status: 'pending',
        requestDate: new Date('2025-11-25'),
    },
    {
        id: 'req-3',
        requestedBy: 'Lisa Team Lead',
        category: 'Training',
        amount: 20000,
        purpose: 'Kubernetes certification training for 5 engineers',
        priority: 'medium',
        status: 'pending',
        requestDate: new Date('2025-12-01'),
    },
];

// ============================================================================
// Helper Functions
// ============================================================================

const formatCurrency = (amount: number): string => {
    return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0,
    }).format(amount);
};

const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
    switch (status) {
        case 'on-track':
            return 'success';
        case 'warning':
            return 'warning';
        case 'over-budget':
            return 'error';
        default:
            return 'default';
    }
};

const getPriorityColor = (priority: string): 'error' | 'warning' | 'info' | 'default' => {
    switch (priority) {
        case 'critical':
            return 'error';
        case 'high':
            return 'warning';
        case 'medium':
            return 'info';
        default:
            return 'default';
    }
};

const getRequestStatusColor = (status: string): 'warning' | 'success' | 'error' | 'default' => {
    switch (status) {
        case 'pending':
            return 'warning';
        case 'approved':
            return 'success';
        case 'rejected':
            return 'error';
        default:
            return 'default';
    }
};

// ============================================================================
// Main Component
// ============================================================================

export const BudgetTracker: React.FC<BudgetTrackerProps> = ({
    categories = mockCategories,
    metrics = mockMetrics,
    forecasts = mockForecasts,
    requests = mockRequests,
    onApproveBudget,
    onRejectBudget,
    onViewDetails,
    onExportReport,
}) => {
    const [activeTab, setActiveTab] = useState<number>(0);

    const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
        setActiveTab(newValue);
    };

    return (
        <Box className="p-6 space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <Typography variant="h4" className="font-bold text-slate-900 dark:text-neutral-100">
                        Budget Tracker
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Department budget management and financial oversight
                    </Typography>
                </div>
                <Button variant="outlined" onClick={onExportReport}>
                    Export Report
                </Button>
            </div>

            {/* Budget KPIs */}
            <Grid columns={4} gap={3}>
                <KpiCard
                    title="Total Budget"
                    value={formatCurrency(metrics.totalBudget)}
                    showProgress={false}
                />
                <KpiCard
                    title="Spent"
                    value={formatCurrency(metrics.totalSpent)}
                    target={metrics.totalBudget}
                    showProgress={true}
                />
                <KpiCard
                    title="Utilization"
                    value={metrics.utilizationPercent}
                    unit="%"
                    target={85}
                    trend={{
                        direction: metrics.variance < 0 ? 'down' : 'up',
                        value: Math.abs(metrics.variance),
                    }}
                    showProgress={true}
                />
                <KpiCard
                    title="Remaining"
                    value={formatCurrency(metrics.totalRemaining)}
                    showProgress={false}
                />
            </Grid>

            {/* Budget Health Alert */}
            {metrics.categoriesOverBudget > 0 && (
                <Alert severity="warning">
                    ⚠️ {metrics.categoriesOverBudget} categor{metrics.categoriesOverBudget === 1 ? 'y is' : 'ies are'} over budget
                </Alert>
            )}

            {/* Tabs */}
            <Card>
                <Tabs value={activeTab} onChange={handleTabChange}>
                    <Tab label="Budget Breakdown" />
                    <Tab label="Forecasts" />
                    <Tab label={`Requests (${requests.filter((r) => r.status === 'pending').length})`} />
                </Tabs>

                <CardContent>
                    {/* Tab 1: Budget Breakdown */}
                    {activeTab === 0 && (
                        <div className="space-y-4">
                            <Grid columns={1} gap={3}>
                                {categories.map((category) => {
                                    const utilizationPercent = (category.spent / category.allocated) * 100;
                                    const commitmentPercent = ((category.spent + category.committed) / category.allocated) * 100;

                                    return (
                                        <Card key={category.id} variant="outlined">
                                            <CardContent>
                                                <div className="flex items-start justify-between mb-4">
                                                    <div className="flex-1">
                                                        <div className="flex items-center gap-2 mb-2">
                                                            <Typography variant="h6" className="font-semibold text-slate-900 dark:text-neutral-100">
                                                                {category.name}
                                                            </Typography>
                                                            <Chip
                                                                label={category.status.replace('-', ' ')}
                                                                color={getStatusColor(category.status)}
                                                                size="small"
                                                            />
                                                        </div>
                                                        <div className="grid grid-cols-4 gap-4 mb-3">
                                                            <div>
                                                                <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                                                                    Allocated
                                                                </Typography>
                                                                <Typography variant="body1" className="font-semibold text-slate-900 dark:text-neutral-100">
                                                                    {formatCurrency(category.allocated)}
                                                                </Typography>
                                                            </div>
                                                            <div>
                                                                <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                                                                    Spent
                                                                </Typography>
                                                                <Typography variant="body1" className="font-semibold text-blue-600">
                                                                    {formatCurrency(category.spent)}
                                                                </Typography>
                                                            </div>
                                                            <div>
                                                                <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                                                                    Committed
                                                                </Typography>
                                                                <Typography variant="body1" className="font-semibold text-yellow-600">
                                                                    {formatCurrency(category.committed)}
                                                                </Typography>
                                                            </div>
                                                            <div>
                                                                <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                                                                    Remaining
                                                                </Typography>
                                                                <Typography variant="body1" className="font-semibold text-green-600">
                                                                    {formatCurrency(category.remaining)}
                                                                </Typography>
                                                            </div>
                                                        </div>

                                                        {/* Progress Bars */}
                                                        <div className="space-y-2">
                                                            <div>
                                                                <div className="flex justify-between mb-1">
                                                                    <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                                                                        Spent
                                                                    </Typography>
                                                                    <Typography variant="caption" className="font-semibold text-slate-900 dark:text-neutral-100">
                                                                        {Math.round(utilizationPercent)}%
                                                                    </Typography>
                                                                </div>
                                                                <LinearProgress
                                                                    value={Math.min(utilizationPercent, 100)}
                                                                    color={
                                                                        category.status === 'over-budget'
                                                                            ? 'error'
                                                                            : category.status === 'warning'
                                                                                ? 'warning'
                                                                                : 'success'
                                                                    }
                                                                />
                                                            </div>
                                                            <div>
                                                                <div className="flex justify-between mb-1">
                                                                    <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                                                                        Spent + Committed
                                                                    </Typography>
                                                                    <Typography variant="caption" className="font-semibold text-slate-900 dark:text-neutral-100">
                                                                        {Math.round(commitmentPercent)}%
                                                                    </Typography>
                                                                </div>
                                                                <LinearProgress
                                                                    value={Math.min(commitmentPercent, 100)}
                                                                    color="warning"
                                                                    variant="determinate"
                                                                />
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <Button
                                                        variant="outlined"
                                                        size="small"
                                                        onClick={() => onViewDetails?.(category.id)}
                                                    >
                                                        View Details
                                                    </Button>
                                                </div>

                                                {/* Subcategories */}
                                                {category.subcategories && category.subcategories.length > 0 && (
                                                    <div className="mt-4 pt-4 border-t border-slate-200 dark:border-neutral-700">
                                                        <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-2">
                                                            Subcategories:
                                                        </Typography>
                                                        <div className="space-y-2">
                                                            {category.subcategories.map((sub) => (
                                                                <div key={sub.id} className="flex items-center justify-between p-2 bg-slate-50 dark:bg-slate-800 rounded">
                                                                    <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                                        {sub.name}
                                                                    </Typography>
                                                                    <div className="flex items-center gap-4 text-sm">
                                                                        <span className="text-slate-600 dark:text-neutral-400">
                                                                            {formatCurrency(sub.spent)} / {formatCurrency(sub.allocated)}
                                                                        </span>
                                                                        <span className="text-green-600 font-medium">
                                                                            {formatCurrency(sub.remaining)} left
                                                                        </span>
                                                                    </div>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    </div>
                                                )}
                                            </CardContent>
                                        </Card>
                                    );
                                })}
                            </Grid>
                        </div>
                    )}

                    {/* Tab 2: Forecasts */}
                    {activeTab === 1 && (
                        <div className="space-y-4">
                            <Alert severity="info">
                                Monthly spending forecast based on historical trends and committed expenses
                            </Alert>

                            <Card variant="outlined">
                                <CardContent>
                                    <Typography variant="h6" className="font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                                        Monthly Forecast
                                    </Typography>
                                    <TableContainer>
                                        <Table>
                                            <TableHead>
                                                <TableRow>
                                                    <TableCell>Month</TableCell>
                                                    <TableCell align="right">Projected</TableCell>
                                                    <TableCell align="right">Actual</TableCell>
                                                    <TableCell align="right">Variance</TableCell>
                                                    <TableCell>Trend</TableCell>
                                                </TableRow>
                                            </TableHead>
                                            <TableBody>
                                                {forecasts.map((forecast) => {
                                                    const variance = forecast.actual
                                                        ? ((forecast.actual - forecast.projected) / forecast.projected) * 100
                                                        : null;

                                                    return (
                                                        <TableRow key={forecast.month}>
                                                            <TableCell className="font-medium">{forecast.month}</TableCell>
                                                            <TableCell align="right">{formatCurrency(forecast.projected)}</TableCell>
                                                            <TableCell align="right">
                                                                {forecast.actual ? formatCurrency(forecast.actual) : '-'}
                                                            </TableCell>
                                                            <TableCell align="right">
                                                                {variance !== null ? (
                                                                    <span
                                                                        className={
                                                                            variance > 0
                                                                                ? 'text-red-600'
                                                                                : variance < 0
                                                                                    ? 'text-green-600'
                                                                                    : 'text-slate-600'
                                                                        }
                                                                    >
                                                                        {variance > 0 ? '+' : ''}
                                                                        {variance.toFixed(1)}%
                                                                    </span>
                                                                ) : (
                                                                    '-'
                                                                )}
                                                            </TableCell>
                                                            <TableCell>
                                                                <Chip
                                                                    label={forecast.trend}
                                                                    size="small"
                                                                    color={
                                                                        forecast.trend === 'increasing'
                                                                            ? 'warning'
                                                                            : forecast.trend === 'decreasing'
                                                                                ? 'success'
                                                                                : 'default'
                                                                    }
                                                                />
                                                            </TableCell>
                                                        </TableRow>
                                                    );
                                                })}
                                            </TableBody>
                                        </Table>
                                    </TableContainer>
                                </CardContent>
                            </Card>

                            {/* Projection Summary */}
                            <Card variant="outlined">
                                <CardContent>
                                    <Typography variant="h6" className="font-semibold text-slate-900 dark:text-neutral-100 mb-3">
                                        Year-End Projection
                                    </Typography>
                                    <Grid columns={3} gap={3}>
                                        <div>
                                            <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                                                Projected Spend
                                            </Typography>
                                            <Typography variant="h5" className="font-bold text-slate-900 dark:text-neutral-100">
                                                {formatCurrency(metrics.projectedEndOfYearSpend)}
                                            </Typography>
                                        </div>
                                        <div>
                                            <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                                                Total Budget
                                            </Typography>
                                            <Typography variant="h5" className="font-bold text-slate-900 dark:text-neutral-100">
                                                {formatCurrency(metrics.totalBudget)}
                                            </Typography>
                                        </div>
                                        <div>
                                            <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                                                Variance
                                            </Typography>
                                            <Typography
                                                variant="h5"
                                                className={`font-bold ${metrics.variance < 0 ? 'text-green-600' : 'text-red-600'
                                                    }`}
                                            >
                                                {metrics.variance < 0 ? '' : '+'}
                                                {metrics.variance.toFixed(1)}%
                                            </Typography>
                                        </div>
                                    </Grid>
                                </CardContent>
                            </Card>
                        </div>
                    )}

                    {/* Tab 3: Budget Requests */}
                    {activeTab === 2 && (
                        <div className="space-y-4">
                            {requests.length === 0 ? (
                                <Alert severity="info">No pending budget requests</Alert>
                            ) : (
                                <Grid columns={1} gap={3}>
                                    {requests.map((request) => (
                                        <Card key={request.id} variant="outlined">
                                            <CardContent>
                                                <div className="flex items-start justify-between mb-3">
                                                    <div className="flex-1">
                                                        <div className="flex items-center gap-2 mb-2">
                                                            <Typography variant="h6" className="font-semibold text-slate-900 dark:text-neutral-100">
                                                                {formatCurrency(request.amount)}
                                                            </Typography>
                                                            <Chip
                                                                label={request.priority}
                                                                color={getPriorityColor(request.priority)}
                                                                size="small"
                                                            />
                                                            <Chip
                                                                label={request.status}
                                                                color={getRequestStatusColor(request.status)}
                                                                size="small"
                                                            />
                                                        </div>
                                                        <Typography variant="body2" className="text-slate-700 dark:text-neutral-300 mb-2">
                                                            {request.purpose}
                                                        </Typography>
                                                        <div className="flex items-center gap-4 text-sm text-slate-600 dark:text-neutral-400">
                                                            <span>Requested by: {request.requestedBy}</span>
                                                            <span>•</span>
                                                            <span>Category: {request.category}</span>
                                                            <span>•</span>
                                                            <span>{request.requestDate.toLocaleDateString()}</span>
                                                        </div>
                                                    </div>
                                                    {request.status === 'pending' && (
                                                        <div className="flex gap-2 ml-4">
                                                            <Button
                                                                variant="outlined"
                                                                size="small"
                                                                color="error"
                                                                onClick={() => onRejectBudget?.(request.id, '')}
                                                            >
                                                                Reject
                                                            </Button>
                                                            <Button
                                                                variant="contained"
                                                                size="small"
                                                                color="success"
                                                                onClick={() => onApproveBudget?.(request.id, request.amount)}
                                                            >
                                                                Approve
                                                            </Button>
                                                        </div>
                                                    )}
                                                </div>

                                                {request.decision && (
                                                    <Alert severity={request.status === 'approved' ? 'success' : 'error'} className="mt-3">
                                                        <Typography variant="body2">
                                                            {request.status === 'approved' ? '✅ Approved' : '❌ Rejected'} by{' '}
                                                            {request.decision.approvedBy} on{' '}
                                                            {request.decision.decisionDate.toLocaleDateString()}
                                                            {request.decision.approvedAmount && (
                                                                <> for {formatCurrency(request.decision.approvedAmount)}</>
                                                            )}
                                                        </Typography>
                                                        {request.decision.notes && (
                                                            <Typography variant="caption" className="mt-1 block">
                                                                Notes: {request.decision.notes}
                                                            </Typography>
                                                        )}
                                                    </Alert>
                                                )}
                                            </CardContent>
                                        </Card>
                                    ))}
                                </Grid>
                            )}
                        </div>
                    )}
                </CardContent>
            </Card>
        </Box>
    );
};

export default BudgetTracker;

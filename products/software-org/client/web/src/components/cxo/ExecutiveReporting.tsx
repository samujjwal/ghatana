/**
 * Executive Reporting Component
 *
 * CXO-level executive reporting component with financial reports, board presentations,
 * quarterly reviews, and performance summaries.
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
    Table,
    TableHead,
    TableBody,
    TableRow,
    TableCell,
} from '@ghatana/design-system';
import { KpiCard } from '@/shared/components/org';

/**
 * Quarterly performance metrics
 */
export interface QuarterlyPerformance {
    quarter: string; // e.g., "Q1 2025"
    revenue: number;
    expenses: number;
    profit: number;
    margin: number; // Percentage
    headcount: number;
    customerCount: number;
    churnRate: number; // Percentage
}

/**
 * Board report item
 */
export interface BoardReportItem {
    id: string;
    title: string;
    category: 'financial' | 'operational' | 'strategic' | 'risk';
    status: 'positive' | 'neutral' | 'negative';
    summary: string;
    metrics: {
        label: string;
        value: string;
        trend?: 'up' | 'down' | 'stable';
    }[];
}

/**
 * Key performance indicator
 */
export interface ExecutiveKPI {
    id: string;
    name: string;
    value: string;
    target: string;
    progress: number; // 0-100
    trend: 'up' | 'down' | 'stable';
    trendValue: string; // e.g., "+12%"
    status: 'exceeding' | 'on-track' | 'below';
}

/**
 * Risk indicator
 */
export interface RiskIndicator {
    id: string;
    title: string;
    severity: 'critical' | 'high' | 'medium' | 'low';
    category: 'financial' | 'operational' | 'market' | 'regulatory' | 'talent';
    impact: string;
    mitigation: string;
    owner: string;
}

/**
 * Executive Reporting Props
 */
export interface ExecutiveReportingProps {
    /** Quarterly performance data */
    quarterlyPerformance: QuarterlyPerformance[];
    /** Board report items */
    boardReports: BoardReportItem[];
    /** Executive KPIs */
    executiveKPIs: ExecutiveKPI[];
    /** Risk indicators */
    risks: RiskIndicator[];
    /** Current quarter identifier */
    currentQuarter: string;
    /** Callback when exporting board report */
    onExportBoardReport?: () => void;
    /** Callback when exporting quarterly report */
    onExportQuarterlyReport?: () => void;
    /** Callback when risk is clicked */
    onRiskClick?: (riskId: string) => void;
    /** Callback when KPI is clicked */
    onKPIClick?: (kpiId: string) => void;
}

/**
 * Executive Reporting Component
 *
 * Provides CXO-level reporting capabilities with:
 * - Quarterly performance tracking
 * - Board report summaries
 * - Executive KPIs dashboard
 * - Risk indicators
 * - Tab-based navigation (Performance, Board Reports, KPIs, Risks)
 *
 * Reuses @ghatana/design-system components and shared org KPI cards:
 * - KpiCard (executive KPIs)
 * - Grid (responsive layouts)
 * - Card (report cards, risk cards)
 * - Table (quarterly performance)
 * - Chip (status, category indicators)
 * - LinearProgress (KPI progress)
 * - Tabs (navigation)
 * - Alert (warnings, critical risks)
 *
 * @example
 * ```tsx
 * <ExecutiveReporting
 *   quarterlyPerformance={performanceData}
 *   boardReports={reports}
 *   executiveKPIs={kpis}
 *   risks={riskList}
 *   currentQuarter="Q1 2025"
 *   onExportBoardReport={() => exportToPDF()}
 * />
 * ```
 */
export const ExecutiveReporting: React.FC<ExecutiveReportingProps> = ({
    quarterlyPerformance,
    boardReports,
    executiveKPIs,
    risks,
    currentQuarter,
    onExportBoardReport,
    onExportQuarterlyReport,
    onRiskClick,
    onKPIClick,
}) => {
    const [selectedTab, setSelectedTab] = useState<'performance' | 'board' | 'kpis' | 'risks'>('performance');

    // Get category color
    const getCategoryColor = (category: string): 'default' | 'warning' | 'error' => {
        switch (category) {
            case 'financial':
                return 'error';
            case 'operational':
                return 'warning';
            case 'strategic':
                return 'error';
            case 'risk':
                return 'warning';
            case 'market':
                return 'warning';
            case 'regulatory':
                return 'error';
            case 'talent':
                return 'default';
            default:
                return 'default';
        }
    };

    // Get status color
    const getStatusColor = (status: 'positive' | 'neutral' | 'negative'): 'success' | 'default' | 'error' => {
        switch (status) {
            case 'positive':
                return 'success';
            case 'neutral':
                return 'default';
            case 'negative':
                return 'error';
        }
    };

    // Get KPI status color
    const getKPIStatusColor = (status: 'exceeding' | 'on-track' | 'below'): 'success' | 'warning' | 'error' => {
        switch (status) {
            case 'exceeding':
                return 'success';
            case 'on-track':
                return 'success';
            case 'below':
                return 'error';
        }
    };

    // Get severity color
    const getSeverityColor = (severity: 'critical' | 'high' | 'medium' | 'low'): 'error' | 'warning' | 'default' => {
        switch (severity) {
            case 'critical':
                return 'error';
            case 'high':
                return 'error';
            case 'medium':
                return 'warning';
            case 'low':
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

    // Format currency
    const formatCurrency = (amount: number): string => {
        if (amount >= 1000000) {
            return `$${(amount / 1000000).toFixed(1)}M`;
        } else if (amount >= 1000) {
            return `$${(amount / 1000).toFixed(0)}K`;
        }
        return `$${amount.toFixed(0)}`;
    };

    // Count critical risks
    const criticalRisks = risks.filter((r) => r.severity === 'critical' || r.severity === 'high').length;

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Executive Reporting
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Board presentations and performance summaries
                    </Typography>
                </Box>
                <Stack direction="row" spacing={2}>
                    {onExportQuarterlyReport && (
                        <Button variant="outline" size="md" onClick={onExportQuarterlyReport}>
                            Export Quarterly
                        </Button>
                    )}
                    {onExportBoardReport && (
                        <Button variant="primary" size="md" onClick={onExportBoardReport}>
                            Export Board Report
                        </Button>
                    )}
                </Stack>
            </Box>

            {/* Critical Risks Alert */}
            {criticalRisks > 0 && (
                <Alert severity="error">
                    {criticalRisks} critical/high severity risk{criticalRisks > 1 ? 's' : ''} require immediate attention
                </Alert>
            )}

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label="Quarterly Performance" value="performance" />
                    <Tab label={`Board Reports (${boardReports.length})`} value="board" />
                    <Tab label={`Executive KPIs (${executiveKPIs.length})`} value="kpis" />
                    <Tab label={`Risks (${risks.length})`} value="risks" />
                </Tabs>

                {/* Performance Tab */}
                {selectedTab === 'performance' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Quarterly Performance Trends
                        </Typography>

                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Quarter</TableCell>
                                    <TableCell align="right">Revenue</TableCell>
                                    <TableCell align="right">Expenses</TableCell>
                                    <TableCell align="right">Profit</TableCell>
                                    <TableCell align="right">Margin</TableCell>
                                    <TableCell align="right">Headcount</TableCell>
                                    <TableCell align="right">Customers</TableCell>
                                    <TableCell align="right">Churn</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {quarterlyPerformance.map((quarter) => (
                                    <TableRow key={quarter.quarter} className={quarter.quarter === currentQuarter ? 'bg-blue-50 dark:bg-blue-900/20' : ''}>
                                        <TableCell>
                                            <Box className="flex items-center gap-2">
                                                <Typography variant="body2" className="font-medium">
                                                    {quarter.quarter}
                                                </Typography>
                                                {quarter.quarter === currentQuarter && <Chip label="Current" color="default" size="small" />}
                                            </Box>
                                        </TableCell>
                                        <TableCell align="right">{formatCurrency(quarter.revenue)}</TableCell>
                                        <TableCell align="right">{formatCurrency(quarter.expenses)}</TableCell>
                                        <TableCell align="right">
                                            <span className={quarter.profit >= 0 ? 'text-green-600' : 'text-red-600'}>{formatCurrency(quarter.profit)}</span>
                                        </TableCell>
                                        <TableCell align="right">{quarter.margin}%</TableCell>
                                        <TableCell align="right">{quarter.headcount}</TableCell>
                                        <TableCell align="right">{quarter.customerCount.toLocaleString()}</TableCell>
                                        <TableCell align="right">
                                            <span className={quarter.churnRate > 5 ? 'text-red-600' : 'text-slate-900 dark:text-neutral-100'}>{quarter.churnRate}%</span>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </Box>
                )}

                {/* Board Reports Tab */}
                {selectedTab === 'board' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Board Report Items
                        </Typography>

                        {boardReports.length === 0 ? (
                            <Alert severity="info">No board reports available</Alert>
                        ) : (
                            <Stack spacing={3}>
                                {boardReports.map((report) => (
                                    <Card key={report.id} className="border border-slate-200 dark:border-neutral-700">
                                        <Box className="p-4">
                                            {/* Report Header */}
                                            <Box className="flex items-start justify-between mb-3">
                                                <Box>
                                                    <Box className="flex items-center gap-2 mb-1">
                                                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                            {report.title}
                                                        </Typography>
                                                        <Chip label={report.category} color={getCategoryColor(report.category)} size="small" />
                                                        <Chip label={report.status} color={getStatusColor(report.status)} size="small" />
                                                    </Box>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {report.summary}
                                                    </Typography>
                                                </Box>
                                            </Box>

                                            {/* Report Metrics */}
                                            <Grid columns={4} gap={3} className="mt-3">
                                                {report.metrics.map((metric, index) => (
                                                    <Box key={index}>
                                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                            {metric.label}
                                                        </Typography>
                                                        <Box className="flex items-center gap-1">
                                                            <Typography variant="body1" className="font-medium text-slate-900 dark:text-neutral-100">
                                                                {metric.value}
                                                            </Typography>
                                                            {metric.trend && <span className={getTrendColor(metric.trend)}>{getTrendIcon(metric.trend)}</span>}
                                                        </Box>
                                                    </Box>
                                                ))}
                                            </Grid>
                                        </Box>
                                    </Card>
                                ))}
                            </Stack>
                        )}
                    </Box>
                )}

                {/* KPIs Tab */}
                {selectedTab === 'kpis' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Executive KPIs
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {executiveKPIs.map((kpi) => (
                                <Card
                                    key={kpi.id}
                                    className="cursor-pointer hover:shadow-md transition-shadow"
                                    onClick={() => onKPIClick?.(kpi.id)}
                                >
                                    <Box className="p-4">
                                        {/* KPI Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-1">
                                                    {kpi.name}
                                                </Typography>
                                                <Box className="flex items-baseline gap-2">
                                                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                                                        {kpi.value}
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-500 dark:text-neutral-400">
                                                        / {kpi.target}
                                                    </Typography>
                                                    <Chip label={kpi.trendValue} color={kpi.trend === 'up' ? 'success' : kpi.trend === 'down' ? 'error' : 'default'} size="small" />
                                                </Box>
                                            </Box>
                                            <Chip label={kpi.status} color={getKPIStatusColor(kpi.status)} size="small" />
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
                                            <LinearProgress variant="determinate" value={kpi.progress} color={getKPIStatusColor(kpi.status)} />
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}

                {/* Risks Tab */}
                {selectedTab === 'risks' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Risk Indicators
                        </Typography>

                        {risks.length === 0 ? (
                            <Alert severity="success">No active risks identified</Alert>
                        ) : (
                            <Stack spacing={3}>
                                {risks.map((risk) => (
                                    <Card
                                        key={risk.id}
                                        className="cursor-pointer hover:shadow-md transition-shadow border border-slate-200 dark:border-neutral-700"
                                        onClick={() => onRiskClick?.(risk.id)}
                                    >
                                        <Box className="p-4">
                                            {/* Risk Header */}
                                            <Box className="flex items-start justify-between mb-3">
                                                <Box className="flex-1">
                                                    <Box className="flex items-center gap-2 mb-1">
                                                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                            {risk.title}
                                                        </Typography>
                                                        <Chip label={risk.severity} color={getSeverityColor(risk.severity)} size="small" />
                                                        <Chip label={risk.category} color={getCategoryColor(risk.category)} size="small" />
                                                    </Box>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-2">
                                                        Owner: {risk.owner}
                                                    </Typography>
                                                </Box>
                                            </Box>

                                            {/* Risk Details */}
                                            <Box className="space-y-2">
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 font-medium">
                                                        Impact:
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {risk.impact}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 font-medium">
                                                        Mitigation:
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {risk.mitigation}
                                                    </Typography>
                                                </Box>
                                            </Box>
                                        </Box>
                                    </Card>
                                ))}
                            </Stack>
                        )}
                    </Box>
                )}
            </Card>
        </Box>
    );
};

/**
 * Mock data for development/testing
 */
export const mockExecutiveReportingData = {
    quarterlyPerformance: [
        { quarter: 'Q4 2024', revenue: 11000000, expenses: 8500000, profit: 2500000, margin: 23, headcount: 580, customerCount: 1200, churnRate: 4.2 },
        { quarter: 'Q1 2025', revenue: 12500000, expenses: 9500000, profit: 3000000, margin: 24, headcount: 650, customerCount: 1350, churnRate: 3.8 },
        { quarter: 'Q2 2025', revenue: 14000000, expenses: 10200000, profit: 3800000, margin: 27, headcount: 720, customerCount: 1500, churnRate: 3.5 },
        { quarter: 'Q3 2025', revenue: 15500000, expenses: 11000000, profit: 4500000, margin: 29, headcount: 800, customerCount: 1680, churnRate: 3.2 },
    ] as QuarterlyPerformance[],

    boardReports: [
        {
            id: 'report-1',
            title: 'Q1 Financial Performance',
            category: 'financial',
            status: 'positive',
            summary: 'Revenue exceeded target by 12%, profit margin improved to 24%',
            metrics: [
                { label: 'Revenue', value: '$12.5M', trend: 'up' },
                { label: 'Profit', value: '$3.0M', trend: 'up' },
                { label: 'Margin', value: '24%', trend: 'up' },
                { label: 'Growth', value: '+25%', trend: 'up' },
            ],
        },
        {
            id: 'report-2',
            title: 'Product Development Progress',
            category: 'operational',
            status: 'positive',
            summary: 'AI platform launch on track for Q3, beta testing exceeding expectations',
            metrics: [
                { label: 'Features Completed', value: '85%', trend: 'up' },
                { label: 'Beta Users', value: '500', trend: 'up' },
                { label: 'Satisfaction', value: '4.7/5', trend: 'up' },
                { label: 'Timeline', value: 'On Track', trend: 'stable' },
            ],
        },
        {
            id: 'report-3',
            title: 'Talent Acquisition',
            category: 'operational',
            status: 'neutral',
            summary: 'Hiring pace steady but competitive market pressures remain',
            metrics: [
                { label: 'New Hires', value: '70', trend: 'up' },
                { label: 'Attrition', value: '9%', trend: 'down' },
                { label: 'Open Roles', value: '35', trend: 'stable' },
                { label: 'Time to Hire', value: '45 days', trend: 'stable' },
            ],
        },
    ] as BoardReportItem[],

    executiveKPIs: [
        { id: 'kpi-1', name: 'Annual Recurring Revenue', value: '$48M', target: '$100M', progress: 48, trend: 'up', trendValue: '+25%', status: 'on-track' },
        { id: 'kpi-2', name: 'Net Revenue Retention', value: '125%', target: '120%', progress: 104, trend: 'up', trendValue: '+5%', status: 'exceeding' },
        { id: 'kpi-3', name: 'Gross Margin', value: '78%', target: '75%', progress: 104, trend: 'up', trendValue: '+3%', status: 'exceeding' },
        { id: 'kpi-4', name: 'Customer Acquisition Cost', value: '$8.2K', target: '$7.5K', progress: 91, trend: 'down', trendValue: '-$500', status: 'below' },
        { id: 'kpi-5', name: 'Employee NPS', value: '72', target: '70', progress: 103, trend: 'up', trendValue: '+5', status: 'exceeding' },
        { id: 'kpi-6', name: 'Product Velocity', value: '88%', target: '85%', progress: 104, trend: 'up', trendValue: '+8%', status: 'exceeding' },
    ] as ExecutiveKPI[],

    risks: [
        {
            id: 'risk-1',
            title: 'Enterprise Sales Pipeline Slowing',
            severity: 'high',
            category: 'market',
            impact: 'Q3 revenue target at risk if pipeline does not accelerate by end of Q2',
            mitigation: 'Increased marketing spend, expanded SDR team, CEO-led enterprise outreach program',
            owner: 'CRO',
        },
        {
            id: 'risk-2',
            title: 'Engineering Talent Retention',
            severity: 'medium',
            category: 'talent',
            impact: 'Key engineering departures could delay AI platform launch',
            mitigation: 'Compensation review, equity refresh program, improved engineering culture initiatives',
            owner: 'CTO',
        },
        {
            id: 'risk-3',
            title: 'Cloud Infrastructure Costs',
            severity: 'medium',
            category: 'financial',
            impact: 'Infrastructure costs growing faster than revenue, compressing margins',
            mitigation: 'Architecture optimization, multi-cloud strategy, reserved instance purchases',
            owner: 'CFO',
        },
    ] as RiskIndicator[],

    currentQuarter: 'Q1 2025',
};

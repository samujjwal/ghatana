/**
 * Cross-Functional Metrics Component
 *
 * VP-level department comparison and benchmarking component with comparative KPIs,
 * trend analysis, performance rankings, and cross-functional insights.
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
 * Department comparison metrics
 */
export interface DepartmentComparison {
    id: string;
    name: string;
    velocity: number; // 0-100
    quality: number; // 0-100
    satisfaction: number; // 0-5
    efficiency: number; // 0-100
    innovation: number; // 0-100
    collaboration: number; // 0-100
    rank: number;
    trend: 'up' | 'down' | 'stable';
}

/**
 * Trend data point for time series
 */
export interface TrendDataPoint {
    date: string;
    value: number;
}

/**
 * Department trend analysis
 */
export interface DepartmentTrend {
    departmentId: string;
    departmentName: string;
    metric: string;
    data: TrendDataPoint[];
    trend: 'improving' | 'declining' | 'stable';
    changePercent: number;
}

/**
 * Cross-functional insight
 */
export interface CrossFunctionalInsight {
    id: string;
    title: string;
    description: string;
    type: 'opportunity' | 'risk' | 'achievement';
    severity: 'high' | 'medium' | 'low';
    affectedDepartments: string[];
    recommendedAction: string;
}

/**
 * Benchmark comparison
 */
export interface BenchmarkComparison {
    metric: string;
    unit: string;
    company: number;
    industry: number;
    topPerformer: number;
    status: 'leading' | 'meeting' | 'below';
}

/**
 * Metrics summary aggregation
 */
export interface MetricsSummary {
    totalDepartments: number;
    avgVelocity: number;
    avgQuality: number;
    avgSatisfaction: number;
    avgEfficiency: number;
    topPerformer: string;
    needsAttention: number;
}

/**
 * Cross-Functional Metrics Props
 */
export interface CrossFunctionalMetricsProps {
    /** Metrics summary aggregation */
    metricsSummary: MetricsSummary;
    /** Department comparison data */
    departmentComparisons: DepartmentComparison[];
    /** Department trend analysis */
    trends: DepartmentTrend[];
    /** Cross-functional insights */
    insights: CrossFunctionalInsight[];
    /** Benchmark comparisons */
    benchmarks: BenchmarkComparison[];
    /** Callback when department is selected */
    onDepartmentClick?: (departmentId: string) => void;
    /** Callback when insight is selected */
    onInsightClick?: (insightId: string) => void;
    /** Callback when export metrics is clicked */
    onExportMetrics?: () => void;
}

/**
 * Cross-Functional Metrics Component
 *
 * Provides VP-level cross-department analysis with:
 * - Metrics summary KPIs (avg velocity, quality, satisfaction)
 * - Department comparison table with rankings
 * - Trend analysis charts
 * - Cross-functional insights and recommendations
 * - Industry benchmark comparisons
 *
 * Reuses @ghatana/design-system components and shared org KPI cards:
 * - KpiCard (4 summary KPIs)
 * - Grid (responsive layouts)
 * - Card (comparison table, trends, insights)
 * - Chip (status, trend indicators)
 * - LinearProgress (metric bars)
 * - Tabs (Comparison/Trends/Insights/Benchmarks navigation)
 * - Table (department comparison table)
 * - Alert (warnings, insights)
 *
 * @example
 * ```tsx
 * <CrossFunctionalMetrics
 *   metricsSummary={summary}
 *   departmentComparisons={comparisons}
 *   trends={trendData}
 *   insights={insightList}
 *   benchmarks={benchmarkData}
 *   onDepartmentClick={(id) => navigate(`/vp/departments/${id}`)}
 * />
 * ```
 */
export const CrossFunctionalMetrics: React.FC<CrossFunctionalMetricsProps> = ({
    metricsSummary,
    departmentComparisons,
    trends,
    insights,
    benchmarks,
    onDepartmentClick,
    onInsightClick,
    onExportMetrics,
}) => {
    const [selectedTab, setSelectedTab] = useState<'comparison' | 'trends' | 'insights' | 'benchmarks'>('comparison');

    // Get trend color
    const getTrendColor = (trend: 'up' | 'down' | 'stable'): string => {
        switch (trend) {
            case 'up':
                return 'text-green-600 dark:text-green-400';
            case 'down':
                return 'text-red-600 dark:text-red-400';
            case 'stable':
                return 'text-slate-600 dark:text-neutral-400';
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

    // Get insight type color
    const getInsightTypeColor = (type: 'opportunity' | 'risk' | 'achievement'): 'success' | 'warning' | 'error' => {
        switch (type) {
            case 'opportunity':
                return 'success';
            case 'risk':
                return 'error';
            case 'achievement':
                return 'success';
        }
    };

    // Get severity color
    const getSeverityColor = (severity: 'high' | 'medium' | 'low'): 'error' | 'warning' | 'default' => {
        switch (severity) {
            case 'high':
                return 'error';
            case 'medium':
                return 'warning';
            case 'low':
                return 'default';
        }
    };

    // Get benchmark status color
    const getBenchmarkStatusColor = (status: 'leading' | 'meeting' | 'below'): 'success' | 'warning' | 'error' => {
        switch (status) {
            case 'leading':
                return 'success';
            case 'meeting':
                return 'warning';
            case 'below':
                return 'error';
        }
    };

    // Get department trend analysis color
    const getDepartmentTrendColor = (trend: 'improving' | 'declining' | 'stable'): 'success' | 'error' | 'default' => {
        switch (trend) {
            case 'improving':
                return 'success';
            case 'declining':
                return 'error';
            case 'stable':
                return 'default';
        }
    };

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Cross-Functional Metrics
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Department comparison, trends, and benchmarking
                    </Typography>
                </Box>
                <Stack direction="row" spacing={2}>
                    {onExportMetrics && (
                        <Button variant="primary" size="md" onClick={onExportMetrics}>
                            Export Metrics
                        </Button>
                    )}
                </Stack>
            </Box>

            {/* Summary KPIs */}
            <Grid columns={4} gap={4}>
                <KpiCard
                    label="Avg Velocity"
                    value={`${Math.round(metricsSummary.avgVelocity)}%`}
                    description={`Across ${metricsSummary.totalDepartments} departments`}
                    status={metricsSummary.avgVelocity >= 80 ? 'healthy' : 'warning'}
                />

                <KpiCard
                    label="Avg Quality"
                    value={`${Math.round(metricsSummary.avgQuality)}%`}
                    description="Organization average"
                    status={metricsSummary.avgQuality >= 85 ? 'healthy' : 'warning'}
                />

                <KpiCard
                    label="Satisfaction"
                    value={metricsSummary.avgSatisfaction.toFixed(1)}
                    description="Employee satisfaction"
                    status={metricsSummary.avgSatisfaction >= 4.0 ? 'healthy' : 'warning'}
                />

                <KpiCard
                    label="Needs Attention"
                    value={metricsSummary.needsAttention}
                    description="Departments below threshold"
                    status={metricsSummary.needsAttention === 0 ? 'healthy' : 'warning'}
                />
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`Comparison (${departmentComparisons.length})`} value="comparison" />
                    <Tab label={`Trends (${trends.length})`} value="trends" />
                    <Tab label={`Insights (${insights.length})`} value="insights" />
                    <Tab label={`Benchmarks (${benchmarks.length})`} value="benchmarks" />
                </Tabs>

                {/* Comparison Tab */}
                {selectedTab === 'comparison' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Department Performance Comparison
                        </Typography>

                        <Box className="overflow-x-auto">
                            <Table>
                                <TableHead>
                                    <TableRow>
                                        <TableCell>Rank</TableCell>
                                        <TableCell>Department</TableCell>
                                        <TableCell>Velocity</TableCell>
                                        <TableCell>Quality</TableCell>
                                        <TableCell>Satisfaction</TableCell>
                                        <TableCell>Efficiency</TableCell>
                                        <TableCell>Innovation</TableCell>
                                        <TableCell>Collaboration</TableCell>
                                        <TableCell>Trend</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {departmentComparisons.map((dept) => (
                                        <TableRow
                                            key={dept.id}
                                            className="cursor-pointer hover:bg-slate-50 dark:hover:bg-neutral-800"
                                            onClick={() => onDepartmentClick?.(dept.id)}
                                        >
                                            <TableCell>
                                                <Box className="flex items-center gap-2">
                                                    <Typography variant="body2" className="font-medium">
                                                        #{dept.rank}
                                                    </Typography>
                                                    {dept.rank === 1 && (
                                                        <Chip label="Top" color="success" size="small" />
                                                    )}
                                                </Box>
                                            </TableCell>
                                            <TableCell>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {dept.name}
                                                </Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Box>
                                                    <Typography variant="body2" className="mb-1">
                                                        {dept.velocity}%
                                                    </Typography>
                                                    <LinearProgress
                                                        variant="determinate"
                                                        value={dept.velocity}
                                                        color={dept.velocity >= 80 ? 'success' : dept.velocity >= 60 ? 'warning' : 'error'}
                                                        className="w-20"
                                                    />
                                                </Box>
                                            </TableCell>
                                            <TableCell>
                                                <Box>
                                                    <Typography variant="body2" className="mb-1">
                                                        {dept.quality}%
                                                    </Typography>
                                                    <LinearProgress
                                                        variant="determinate"
                                                        value={dept.quality}
                                                        color={dept.quality >= 85 ? 'success' : dept.quality >= 70 ? 'warning' : 'error'}
                                                        className="w-20"
                                                    />
                                                </Box>
                                            </TableCell>
                                            <TableCell>
                                                <Typography variant="body2">
                                                    {dept.satisfaction.toFixed(1)}/5.0
                                                </Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Typography variant="body2">
                                                    {dept.efficiency}%
                                                </Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Typography variant="body2">
                                                    {dept.innovation}%
                                                </Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Typography variant="body2">
                                                    {dept.collaboration}%
                                                </Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Typography variant="body2" className={getTrendColor(dept.trend)}>
                                                    {getTrendIcon(dept.trend)}
                                                </Typography>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </Box>
                    </Box>
                )}

                {/* Trends Tab */}
                {selectedTab === 'trends' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Department Trends
                        </Typography>

                        {trends.length === 0 ? (
                            <Alert severity="info">
                                No trend data available
                            </Alert>
                        ) : (
                            <Stack spacing={3}>
                                {trends.map((trend) => (
                                    <Card key={`${trend.departmentId}-${trend.metric}`}>
                                        <Box className="p-4">
                                            <Box className="flex items-start justify-between mb-3">
                                                <Box>
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {trend.departmentName} - {trend.metric}
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {Math.abs(trend.changePercent)}% {trend.changePercent >= 0 ? 'increase' : 'decrease'} over last 30 days
                                                    </Typography>
                                                </Box>
                                                <Chip
                                                    label={trend.trend}
                                                    color={getDepartmentTrendColor(trend.trend)}
                                                    size="small"
                                                />
                                            </Box>

                                            {/* Simple trend visualization */}
                                            <Box className="mt-4">
                                                <Box className="flex items-end gap-1 h-24">
                                                    {trend.data.slice(-12).map((point, index) => {
                                                        const maxValue = Math.max(...trend.data.map(d => d.value));
                                                        const height = (point.value / maxValue) * 100;
                                                        return (
                                                            <Box
                                                                key={index}
                                                                className="flex-1 bg-blue-500 dark:bg-blue-400 rounded-t"
                                                                style={{ height: `${height}%` }}
                                                                title={`${new Date(point.date).toLocaleDateString()}: ${point.value}`}
                                                            />
                                                        );
                                                    })}
                                                </Box>
                                                <Box className="flex items-center justify-between mt-2 text-xs text-slate-500 dark:text-neutral-400">
                                                    <span>{new Date(trend.data[0].date).toLocaleDateString()}</span>
                                                    <span>{new Date(trend.data[trend.data.length - 1].date).toLocaleDateString()}</span>
                                                </Box>
                                            </Box>
                                        </Box>
                                    </Card>
                                ))}
                            </Stack>
                        )}
                    </Box>
                )}

                {/* Insights Tab */}
                {selectedTab === 'insights' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Cross-Functional Insights
                        </Typography>

                        {insights.length === 0 ? (
                            <Alert severity="info">
                                No insights available
                            </Alert>
                        ) : (
                            <Stack spacing={3}>
                                {insights.map((insight) => (
                                    <Card
                                        key={insight.id}
                                        className="cursor-pointer hover:shadow-md transition-shadow"
                                        onClick={() => onInsightClick?.(insight.id)}
                                    >
                                        <Box className="p-4">
                                            <Box className="flex items-start justify-between mb-2">
                                                <Box className="flex items-center gap-2 flex-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {insight.title}
                                                    </Typography>
                                                    <Chip
                                                        label={insight.type}
                                                        color={getInsightTypeColor(insight.type)}
                                                        size="small"
                                                    />
                                                    <Chip
                                                        label={insight.severity}
                                                        color={getSeverityColor(insight.severity)}
                                                        size="small"
                                                    />
                                                </Box>
                                            </Box>

                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-3">
                                                {insight.description}
                                            </Typography>

                                            <Box className="p-3 bg-slate-50 dark:bg-neutral-800 rounded-lg mb-3">
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100 mb-1">
                                                    Recommended Action:
                                                </Typography>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {insight.recommendedAction}
                                                </Typography>
                                            </Box>

                                            <Box className="flex flex-wrap gap-2">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Affected:
                                                </Typography>
                                                {insight.affectedDepartments.map((dept, index) => (
                                                    <Chip key={index} label={dept} size="small" />
                                                ))}
                                            </Box>
                                        </Box>
                                    </Card>
                                ))}
                            </Stack>
                        )}
                    </Box>
                )}

                {/* Benchmarks Tab */}
                {selectedTab === 'benchmarks' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Industry Benchmarks
                        </Typography>

                        <Box className="overflow-x-auto">
                            <Table>
                                <TableHead>
                                    <TableRow>
                                        <TableCell>Metric</TableCell>
                                        <TableCell>Our Company</TableCell>
                                        <TableCell>Industry Average</TableCell>
                                        <TableCell>Top Performer</TableCell>
                                        <TableCell>Status</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {benchmarks.map((benchmark, index) => (
                                        <TableRow key={index}>
                                            <TableCell>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {benchmark.metric}
                                                </Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Typography variant="body2" className="font-medium">
                                                    {benchmark.company} {benchmark.unit}
                                                </Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {benchmark.industry} {benchmark.unit}
                                                </Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {benchmark.topPerformer} {benchmark.unit}
                                                </Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Chip
                                                    label={benchmark.status}
                                                    color={getBenchmarkStatusColor(benchmark.status)}
                                                    size="small"
                                                />
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </Box>
                    </Box>
                )}
            </Card>
        </Box>
    );
};

/**
 * Mock data for development/testing
 */
export const mockCrossFunctionalMetricsData = {
    metricsSummary: {
        totalDepartments: 5,
        avgVelocity: 84,
        avgQuality: 89,
        avgSatisfaction: 4.2,
        avgEfficiency: 82,
        topPerformer: 'Engineering',
        needsAttention: 2,
    } as MetricsSummary,

    departmentComparisons: [
        { id: 'dept-eng', name: 'Engineering', velocity: 92, quality: 95, satisfaction: 4.5, efficiency: 88, innovation: 90, collaboration: 85, rank: 1, trend: 'up' },
        { id: 'dept-devops', name: 'DevOps', velocity: 90, quality: 92, satisfaction: 4.4, efficiency: 90, innovation: 85, collaboration: 88, rank: 2, trend: 'up' },
        { id: 'dept-product', name: 'Product', velocity: 88, quality: 90, satisfaction: 4.3, efficiency: 85, innovation: 92, collaboration: 90, rank: 3, trend: 'stable' },
        { id: 'dept-qa', name: 'QA', velocity: 78, quality: 85, satisfaction: 4.0, efficiency: 75, innovation: 70, collaboration: 80, rank: 4, trend: 'down' },
        { id: 'dept-design', name: 'Design', velocity: 75, quality: 82, satisfaction: 3.8, efficiency: 72, innovation: 88, collaboration: 75, rank: 5, trend: 'stable' },
    ] as DepartmentComparison[],

    trends: [
        {
            departmentId: 'dept-eng',
            departmentName: 'Engineering',
            metric: 'Velocity',
            data: Array.from({ length: 30 }, (_, i) => ({
                date: new Date(Date.now() - (29 - i) * 24 * 60 * 60 * 1000).toISOString(),
                value: 85 + Math.random() * 10,
            })),
            trend: 'improving',
            changePercent: 8,
        },
        {
            departmentId: 'dept-qa',
            departmentName: 'QA',
            metric: 'Quality Score',
            data: Array.from({ length: 30 }, (_, i) => ({
                date: new Date(Date.now() - (29 - i) * 24 * 60 * 60 * 1000).toISOString(),
                value: 85 - Math.random() * 5,
            })),
            trend: 'declining',
            changePercent: -5,
        },
    ] as DepartmentTrend[],

    insights: [
        {
            id: 'insight-1',
            title: 'Engineering Velocity Leading Organization',
            description: 'Engineering department has achieved 92% velocity, 15% above organization average',
            type: 'achievement',
            severity: 'low',
            affectedDepartments: ['Engineering'],
            recommendedAction: 'Share best practices with other departments through knowledge transfer sessions',
        },
        {
            id: 'insight-2',
            title: 'QA Department Collaboration Below Target',
            description: 'QA collaboration score has declined 12% over the last quarter, affecting cross-team initiatives',
            type: 'risk',
            severity: 'high',
            affectedDepartments: ['QA', 'Engineering', 'Product'],
            recommendedAction: 'Schedule cross-department workshops and implement pairing sessions between QA and Engineering teams',
        },
        {
            id: 'insight-3',
            title: 'Opportunity for Design-Product Alignment',
            description: 'Design and Product departments show high innovation scores but moderate collaboration metrics',
            type: 'opportunity',
            severity: 'medium',
            affectedDepartments: ['Design', 'Product'],
            recommendedAction: 'Create joint design-product review sessions and establish shared OKRs',
        },
    ] as CrossFunctionalInsight[],

    benchmarks: [
        { metric: 'Deployment Frequency', unit: 'deploys/day', company: 4.2, industry: 3.5, topPerformer: 6.0, status: 'meeting' },
        { metric: 'Lead Time', unit: 'hours', company: 24, industry: 32, topPerformer: 12, status: 'meeting' },
        { metric: 'MTTR', unit: 'hours', company: 1.5, industry: 2.5, topPerformer: 0.5, status: 'meeting' },
        { metric: 'Change Failure Rate', unit: '%', company: 3, industry: 5, topPerformer: 1, status: 'leading' },
        { metric: 'Code Coverage', unit: '%', company: 85, industry: 75, topPerformer: 95, status: 'meeting' },
        { metric: 'Employee Satisfaction', unit: '/5', company: 4.2, industry: 3.8, topPerformer: 4.7, status: 'meeting' },
    ] as BenchmarkComparison[],
};

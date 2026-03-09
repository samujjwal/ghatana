/**
 * Platform Insights Component
 *
 * Root-level platform insights component with usage analytics, platform health metrics,
 * cost optimization, and capacity planning.
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
    Button,
    Typography,
    Stack,
    Table,
    TableHead,
    TableBody,
    TableRow,
    TableCell,
} from '@ghatana/ui';

/**
 * Platform usage metrics
 */
export interface PlatformUsageMetrics {
    activeUsers: number;
    totalApiCalls: number;
    storageUsed: number; // GB
    bandwidthUsed: number; // GB
    averageResponseTime: number; // ms
    errorRate: number; // Percentage
    peakConcurrentUsers: number;
}

/**
 * Service cost breakdown
 */
export interface ServiceCost {
    id: string;
    serviceName: string;
    category: 'compute' | 'storage' | 'network' | 'database' | 'other';
    monthlyCost: number;
    trend: 'up' | 'down' | 'stable';
    percentageOfTotal: number;
    previousMonthCost?: number;
}

/**
 * Capacity metric
 */
export interface CapacityMetric {
    id: string;
    resourceName: string;
    type: 'compute' | 'storage' | 'network' | 'database';
    current: number;
    capacity: number;
    unit: string;
    projectedFull: string; // Date when capacity will be full
    recommendedAction?: string;
}

/**
 * Platform health indicator
 */
export interface PlatformHealthIndicator {
    id: string;
    metric: string;
    category: 'performance' | 'reliability' | 'security' | 'cost';
    status: 'healthy' | 'warning' | 'critical';
    currentValue: number;
    threshold: number;
    unit: string;
    description: string;
}

/**
 * Optimization recommendation
 */
export interface OptimizationRecommendation {
    id: string;
    title: string;
    category: 'cost' | 'performance' | 'security' | 'reliability';
    priority: 'high' | 'medium' | 'low';
    estimatedSavings?: number; // Monthly cost savings
    estimatedImpact: string;
    description: string;
    actionItems: string[];
}

/**
 * Platform Insights Props
 */
export interface PlatformInsightsProps {
    /** Platform usage metrics */
    usageMetrics: PlatformUsageMetrics;
    /** Service cost breakdown */
    serviceCosts: ServiceCost[];
    /** Capacity metrics */
    capacityMetrics: CapacityMetric[];
    /** Platform health indicators */
    healthIndicators: PlatformHealthIndicator[];
    /** Optimization recommendations */
    recommendations: OptimizationRecommendation[];
    /** Callback when cost item is clicked */
    onCostClick?: (costId: string) => void;
    /** Callback when capacity item is clicked */
    onCapacityClick?: (capacityId: string) => void;
    /** Callback when health indicator is clicked */
    onHealthClick?: (indicatorId: string) => void;
    /** Callback when recommendation is clicked */
    onRecommendationClick?: (recommendationId: string) => void;
    /** Callback when export report is clicked */
    onExportReport?: () => void;
}

/**
 * Platform Insights Component
 *
 * Provides Root-level platform analytics with:
 * - Usage metrics (active users, API calls, storage, bandwidth)
 * - Cost analysis by service category
 * - Capacity planning metrics
 * - Platform health monitoring
 * - Optimization recommendations
 * - Tab-based navigation (Usage, Costs, Capacity, Health)
 *
 * Reuses @ghatana/ui components:
 * - KpiCard (usage metrics, cost summary)
 * - Grid (responsive layouts)
 * - Card (cost cards, capacity cards, recommendation cards)
 * - Table (health indicators)
 * - Chip (categories, status, priority indicators)
 * - LinearProgress (capacity usage)
 *
 * @example
 * ```tsx
 * <PlatformInsights
 *   usageMetrics={metrics}
 *   serviceCosts={costs}
 *   capacityMetrics={capacity}
 *   healthIndicators={health}
 *   recommendations={recs}
 *   onCostClick={(id) => navigate(`/platform/costs/${id}`)}
 * />
 * ```
 */
export const PlatformInsights: React.FC<PlatformInsightsProps> = ({
    usageMetrics,
    serviceCosts,
    capacityMetrics,
    healthIndicators,
    recommendations,
    onCostClick,
    onCapacityClick,
    onHealthClick,
    onRecommendationClick,
    onExportReport,
}) => {
    const [selectedTab, setSelectedTab] = useState<'usage' | 'costs' | 'capacity' | 'health'>('usage');
    const [costFilter, setCostFilter] = useState<'all' | 'compute' | 'storage' | 'network' | 'database' | 'other'>('all');

    // Get category color
    const getCategoryColor = (category: string): 'error' | 'warning' | 'default' => {
        switch (category) {
            case 'compute':
            case 'cost':
            case 'security':
                return 'error';
            case 'storage':
            case 'network':
            case 'performance':
                return 'warning';
            case 'database':
            case 'reliability':
            case 'other':
                return 'default';
            default:
                return 'default';
        }
    };

    // Get status color
    const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'healthy':
                return 'success';
            case 'warning':
                return 'warning';
            case 'critical':
                return 'error';
            default:
                return 'default';
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

    // Filter costs
    const filteredCosts = costFilter === 'all' ? serviceCosts : serviceCosts.filter((c) => c.category === costFilter);

    // Calculate totals
    const totalMonthlyCost = serviceCosts.reduce((sum, cost) => sum + cost.monthlyCost, 0);
    const totalEstimatedSavings = recommendations.reduce((sum, rec) => sum + (rec.estimatedSavings || 0), 0);

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Platform Insights
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Usage analytics, cost optimization, and capacity planning
                    </Typography>
                </Box>
                {onExportReport && (
                    <Button variant="primary" size="md" onClick={onExportReport}>
                        Export Report
                    </Button>
                )}
            </Box>

            {/* Usage Overview */}
            <Grid columns={4} gap={4}>
                <KpiCard
                    label="Active Users"
                    value={usageMetrics.activeUsers.toLocaleString()}
                    description={`Peak: ${usageMetrics.peakConcurrentUsers.toLocaleString()}`}
                    status="healthy"
                />

                <KpiCard
                    label="API Calls"
                    value={`${(usageMetrics.totalApiCalls / 1000000).toFixed(1)}M`}
                    description={`Avg ${usageMetrics.averageResponseTime}ms response`}
                    status={usageMetrics.averageResponseTime < 200 ? 'healthy' : 'warning'}
                />

                <KpiCard
                    label="Storage Used"
                    value={`${usageMetrics.storageUsed.toLocaleString()} GB`}
                    description={`Bandwidth: ${usageMetrics.bandwidthUsed.toLocaleString()} GB`}
                    status="healthy"
                />

                <KpiCard
                    label="Error Rate"
                    value={`${usageMetrics.errorRate}%`}
                    description="Last 24 hours"
                    status={usageMetrics.errorRate < 0.1 ? 'healthy' : usageMetrics.errorRate < 0.5 ? 'warning' : 'error'}
                />
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label="Usage Analytics" value="usage" />
                    <Tab label={`Costs ($${(totalMonthlyCost / 1000).toFixed(1)}k)`} value="costs" />
                    <Tab label={`Capacity (${capacityMetrics.length})`} value="capacity" />
                    <Tab label={`Health (${healthIndicators.length})`} value="health" />
                </Tabs>

                {/* Usage Tab */}
                {selectedTab === 'usage' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Platform Usage Trends
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {/* User Activity Card */}
                            <Card>
                                <Box className="p-4">
                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-3">
                                        User Activity
                                    </Typography>
                                    <Grid columns={2} gap={3}>
                                        <Box>
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                Active Users (30d)
                                            </Typography>
                                            <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                                {usageMetrics.activeUsers.toLocaleString()}
                                            </Typography>
                                        </Box>
                                        <Box>
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                Peak Concurrent
                                            </Typography>
                                            <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                                {usageMetrics.peakConcurrentUsers.toLocaleString()}
                                            </Typography>
                                        </Box>
                                    </Grid>
                                </Box>
                            </Card>

                            {/* API Performance Card */}
                            <Card>
                                <Box className="p-4">
                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-3">
                                        API Performance
                                    </Typography>
                                    <Grid columns={2} gap={3}>
                                        <Box>
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                Total Calls (30d)
                                            </Typography>
                                            <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                                {(usageMetrics.totalApiCalls / 1000000).toFixed(1)}M
                                            </Typography>
                                        </Box>
                                        <Box>
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                Avg Response Time
                                            </Typography>
                                            <Typography variant="h5" className={usageMetrics.averageResponseTime > 200 ? 'text-orange-600' : 'text-slate-900 dark:text-neutral-100'}>
                                                {usageMetrics.averageResponseTime}ms
                                            </Typography>
                                        </Box>
                                    </Grid>
                                </Box>
                            </Card>

                            {/* Storage & Bandwidth Card */}
                            <Card>
                                <Box className="p-4">
                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-3">
                                        Storage & Bandwidth
                                    </Typography>
                                    <Grid columns={2} gap={3}>
                                        <Box>
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                Storage Used
                                            </Typography>
                                            <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                                {usageMetrics.storageUsed.toLocaleString()} GB
                                            </Typography>
                                        </Box>
                                        <Box>
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                Bandwidth (30d)
                                            </Typography>
                                            <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                                {usageMetrics.bandwidthUsed.toLocaleString()} GB
                                            </Typography>
                                        </Box>
                                    </Grid>
                                </Box>
                            </Card>

                            {/* Reliability Card */}
                            <Card>
                                <Box className="p-4">
                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-3">
                                        Reliability Metrics
                                    </Typography>
                                    <Grid columns={2} gap={3}>
                                        <Box>
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                Error Rate (24h)
                                            </Typography>
                                            <Typography variant="h5" className={usageMetrics.errorRate > 0.5 ? 'text-red-600' : 'text-slate-900 dark:text-neutral-100'}>
                                                {usageMetrics.errorRate}%
                                            </Typography>
                                        </Box>
                                        <Box>
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                Success Rate
                                            </Typography>
                                            <Typography variant="h5" className="text-green-600">
                                                {(100 - usageMetrics.errorRate).toFixed(2)}%
                                            </Typography>
                                        </Box>
                                    </Grid>
                                </Box>
                            </Card>
                        </Grid>
                    </Box>
                )}

                {/* Costs Tab */}
                {selectedTab === 'costs' && (
                    <Box className="p-4">
                        {/* Cost Summary */}
                        <Box className="mb-4">
                            <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-2">
                                Cost Analysis
                            </Typography>
                            <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                                ${totalMonthlyCost.toLocaleString()} / month
                            </Typography>
                        </Box>

                        {/* Cost Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Chip label="All" color={costFilter === 'all' ? 'error' : 'default'} onClick={() => setCostFilter('all')} />
                            <Chip label="Compute" color={costFilter === 'compute' ? 'error' : 'default'} onClick={() => setCostFilter('compute')} />
                            <Chip label="Storage" color={costFilter === 'storage' ? 'warning' : 'default'} onClick={() => setCostFilter('storage')} />
                            <Chip label="Network" color={costFilter === 'network' ? 'warning' : 'default'} onClick={() => setCostFilter('network')} />
                            <Chip label="Database" color={costFilter === 'database' ? 'default' : 'default'} onClick={() => setCostFilter('database')} />
                            <Chip label="Other" color={costFilter === 'other' ? 'default' : 'default'} onClick={() => setCostFilter('other')} />
                        </Stack>

                        {/* Cost Grid */}
                        <Grid columns={2} gap={4}>
                            {filteredCosts.map((cost) => (
                                <Card key={cost.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onCostClick?.(cost.id)}>
                                    <Box className="p-4">
                                        {/* Cost Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box>
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-1">
                                                    {cost.serviceName}
                                                </Typography>
                                                <Chip label={cost.category} color={getCategoryColor(cost.category)} size="small" />
                                            </Box>
                                            <Chip
                                                label={`${getTrendIcon(cost.trend)} ${cost.trend}`}
                                                color={cost.trend === 'up' ? 'error' : cost.trend === 'down' ? 'success' : 'default'}
                                                size="small"
                                            />
                                        </Box>

                                        {/* Cost Amount */}
                                        <Typography variant="h4" className="text-slate-900 dark:text-neutral-100 mb-1">
                                            ${cost.monthlyCost.toLocaleString()}
                                        </Typography>
                                        <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                            {cost.percentageOfTotal.toFixed(1)}% of total budget
                                        </Typography>

                                        {/* Previous Month Comparison */}
                                        {cost.previousMonthCost && (
                                            <Box className="mt-2 pt-2 border-t border-slate-200 dark:border-neutral-700">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Previous month: ${cost.previousMonthCost.toLocaleString()}
                                                </Typography>
                                            </Box>
                                        )}
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}

                {/* Capacity Tab */}
                {selectedTab === 'capacity' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Capacity Planning
                        </Typography>

                        <Stack spacing={3}>
                            {capacityMetrics.map((capacity) => (
                                <Card key={capacity.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onCapacityClick?.(capacity.id)}>
                                    <Box className="p-4">
                                        {/* Capacity Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {capacity.resourceName}
                                                    </Typography>
                                                    <Chip label={capacity.type} color={getCategoryColor(capacity.type)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    Projected full: {capacity.projectedFull}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Current Usage */}
                                        <Box className="mb-3">
                                            <Box className="flex items-baseline gap-2 mb-1">
                                                <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                                                    {capacity.current} {capacity.unit}
                                                </Typography>
                                                <Typography variant="body2" className="text-slate-500 dark:text-neutral-400">
                                                    / {capacity.capacity} {capacity.unit}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Usage Progress */}
                                        <Box className="mb-3">
                                            <Box className="flex items-center justify-between mb-1">
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    Utilization
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {Math.round((capacity.current / capacity.capacity) * 100)}%
                                                </Typography>
                                            </Box>
                                            <LinearProgress
                                                variant="determinate"
                                                value={Math.min((capacity.current / capacity.capacity) * 100, 100)}
                                                color={
                                                    (capacity.current / capacity.capacity) * 100 < 70
                                                        ? 'success'
                                                        : (capacity.current / capacity.capacity) * 100 < 85
                                                            ? 'warning'
                                                            : 'error'
                                                }
                                            />
                                        </Box>

                                        {/* Recommendation */}
                                        {capacity.recommendedAction && (
                                            <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Recommendation
                                                </Typography>
                                                <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                    {capacity.recommendedAction}
                                                </Typography>
                                            </Box>
                                        )}
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    </Box>
                )}

                {/* Health Tab */}
                {selectedTab === 'health' && (
                    <Box className="p-4">
                        <Box className="flex items-center justify-between mb-4">
                            <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                Platform Health Indicators
                            </Typography>
                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                Potential savings: ${totalEstimatedSavings.toLocaleString()}/month
                            </Typography>
                        </Box>

                        {/* Health Indicators Table */}
                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Metric</TableCell>
                                    <TableCell>Category</TableCell>
                                    <TableCell>Current</TableCell>
                                    <TableCell>Threshold</TableCell>
                                    <TableCell>Status</TableCell>
                                    <TableCell>Description</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {healthIndicators.map((indicator) => (
                                    <TableRow
                                        key={indicator.id}
                                        className="cursor-pointer hover:bg-slate-50 dark:hover:bg-neutral-800"
                                        onClick={() => onHealthClick?.(indicator.id)}
                                    >
                                        <TableCell>
                                            <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                {indicator.metric}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={indicator.category} color={getCategoryColor(indicator.category)} size="small" />
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                {indicator.currentValue} {indicator.unit}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {indicator.threshold} {indicator.unit}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={indicator.status} color={getStatusColor(indicator.status)} size="small" />
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {indicator.description}
                                            </Typography>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>

                        {/* Optimization Recommendations */}
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mt-6 mb-4">
                            Optimization Recommendations
                        </Typography>

                        <Stack spacing={3}>
                            {recommendations.map((rec) => (
                                <Card key={rec.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onRecommendationClick?.(rec.id)}>
                                    <Box className="p-4">
                                        {/* Recommendation Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {rec.title}
                                                    </Typography>
                                                    <Chip label={rec.category} color={getCategoryColor(rec.category)} size="small" />
                                                    <Chip label={rec.priority} color={getPriorityColor(rec.priority)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {rec.description}
                                                </Typography>
                                            </Box>
                                            {rec.estimatedSavings && (
                                                <Box className="text-right">
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Est. Savings
                                                    </Typography>
                                                    <Typography variant="h6" className="text-green-600">
                                                        ${rec.estimatedSavings.toLocaleString()}/mo
                                                    </Typography>
                                                </Box>
                                            )}
                                        </Box>

                                        {/* Impact & Actions */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                Estimated Impact: {rec.estimatedImpact}
                                            </Typography>
                                            {rec.actionItems.length > 0 && (
                                                <Box className="mt-2">
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Action Items:
                                                    </Typography>
                                                    <ul className="list-disc list-inside mt-1 space-y-1">
                                                        {rec.actionItems.map((action, index) => (
                                                            <li key={index}>
                                                                <Typography variant="body2" className="inline text-slate-600 dark:text-neutral-400">
                                                                    {action}
                                                                </Typography>
                                                            </li>
                                                        ))}
                                                    </ul>
                                                </Box>
                                            )}
                                        </Box>
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
export const mockPlatformInsightsData = {
    usageMetrics: {
        activeUsers: 12450,
        totalApiCalls: 45600000,
        storageUsed: 1850,
        bandwidthUsed: 3420,
        averageResponseTime: 145,
        errorRate: 0.08,
        peakConcurrentUsers: 3250,
    } as PlatformUsageMetrics,

    serviceCosts: [
        {
            id: 'cost-1',
            serviceName: 'Compute Instances',
            category: 'compute',
            monthlyCost: 8500,
            trend: 'stable',
            percentageOfTotal: 42.5,
            previousMonthCost: 8300,
        },
        {
            id: 'cost-2',
            serviceName: 'Object Storage',
            category: 'storage',
            monthlyCost: 4200,
            trend: 'up',
            percentageOfTotal: 21.0,
            previousMonthCost: 3800,
        },
        {
            id: 'cost-3',
            serviceName: 'Database Clusters',
            category: 'database',
            monthlyCost: 5800,
            trend: 'stable',
            percentageOfTotal: 29.0,
            previousMonthCost: 5750,
        },
    ] as ServiceCost[],

    capacityMetrics: [
        {
            id: 'cap-1',
            resourceName: 'Database Storage',
            type: 'database',
            current: 850,
            capacity: 1000,
            unit: 'GB',
            projectedFull: 'March 2026',
            recommendedAction: 'Plan capacity increase or implement data archival',
        },
        {
            id: 'cap-2',
            resourceName: 'Compute Instances',
            type: 'compute',
            current: 180,
            capacity: 250,
            unit: 'instances',
            projectedFull: 'June 2026',
        },
    ] as CapacityMetric[],

    healthIndicators: [
        {
            id: 'health-1',
            metric: 'API Response Time',
            category: 'performance',
            status: 'healthy',
            currentValue: 145,
            threshold: 200,
            unit: 'ms',
            description: 'Avg response time within acceptable range',
        },
        {
            id: 'health-2',
            metric: 'Error Rate',
            category: 'reliability',
            status: 'healthy',
            currentValue: 0.08,
            threshold: 0.5,
            unit: '%',
            description: 'Error rate below threshold',
        },
        {
            id: 'health-3',
            metric: 'Unused Resources',
            category: 'cost',
            status: 'warning',
            currentValue: 15,
            threshold: 5,
            unit: '%',
            description: '15% of provisioned resources underutilized',
        },
    ] as PlatformHealthIndicator[],

    recommendations: [
        {
            id: 'rec-1',
            title: 'Right-size Compute Instances',
            category: 'cost',
            priority: 'high',
            estimatedSavings: 1200,
            estimatedImpact: 'Reduce monthly costs by 14% with no performance impact',
            description: 'Analysis shows 12 instances consistently using <30% capacity',
            actionItems: ['Review instance utilization metrics', 'Test downsized instances in staging', 'Implement auto-scaling policies'],
        },
        {
            id: 'rec-2',
            title: 'Implement Database Query Caching',
            category: 'performance',
            priority: 'medium',
            estimatedSavings: 450,
            estimatedImpact: 'Reduce database load by 35% and improve response times',
            description: 'Repetitive queries account for 40% of database traffic',
            actionItems: ['Enable Redis caching layer', 'Implement query result caching', 'Monitor cache hit rates'],
        },
    ] as OptimizationRecommendation[],
};

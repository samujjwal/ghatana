/**
 * Agent Analytics Component
 *
 * Component for analyzing agent performance with usage metrics, conversation analytics,
 * success rates, and improvement recommendations.
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
 * Agent analytics metrics
 */
export interface AnalyticsMetrics {
    totalConversations: number;
    activeUsers: number;
    averageSessionDuration: number; // minutes
    successRate: number; // Percentage
    customerSatisfaction: number; // 0-5 rating
    averageResponseTime: number; // ms
}

/**
 * Usage trend data
 */
export interface UsageTrend {
    id: string;
    period: string;
    conversations: number;
    users: number;
    successRate: number;
    avgDuration: number; // minutes
    trend: 'up' | 'down' | 'stable';
}

/**
 * Conversation analytics
 */
export interface ConversationAnalytics {
    id: string;
    agentName: string;
    totalMessages: number;
    averageMessagesPerConversation: number;
    completionRate: number; // Percentage
    escalationRate: number; // Percentage
    commonTopics: string[];
}

/**
 * Performance metric
 */
export interface PerformanceMetric {
    id: string;
    metric: string;
    category: 'speed' | 'accuracy' | 'satisfaction' | 'efficiency';
    currentValue: number;
    targetValue: number;
    unit: string;
    status: 'good' | 'warning' | 'poor';
    trend: 'improving' | 'declining' | 'stable';
}

/**
 * Improvement recommendation
 */
export interface ImprovementRecommendation {
    id: string;
    title: string;
    category: 'performance' | 'accuracy' | 'engagement' | 'efficiency';
    priority: 'high' | 'medium' | 'low';
    currentScore: number;
    potentialScore: number;
    description: string;
    actionItems: string[];
    estimatedImpact: string;
}

/**
 * Agent Analytics Props
 */
export interface AgentAnalyticsProps {
    /** Analytics metrics */
    metrics: AnalyticsMetrics;
    /** Usage trends */
    usageTrends: UsageTrend[];
    /** Conversation analytics */
    conversationAnalytics: ConversationAnalytics[];
    /** Performance metrics */
    performanceMetrics: PerformanceMetric[];
    /** Improvement recommendations */
    recommendations: ImprovementRecommendation[];
    /** Callback when trend is clicked */
    onTrendClick?: (trendId: string) => void;
    /** Callback when agent analytics is clicked */
    onAgentAnalyticsClick?: (agentId: string) => void;
    /** Callback when metric is clicked */
    onMetricClick?: (metricId: string) => void;
    /** Callback when recommendation is clicked */
    onRecommendationClick?: (recommendationId: string) => void;
    /** Callback when export report is clicked */
    onExportReport?: () => void;
}

/**
 * Agent Analytics Component
 *
 * Provides comprehensive agent analytics with:
 * - Usage metrics (conversations, users, duration, success rate)
 * - Usage trends over time
 * - Conversation analytics by agent
 * - Performance metrics tracking
 * - Improvement recommendations
 * - Tab-based navigation (Usage, Conversations, Performance, Recommendations)
 *
 * Reuses @ghatana/design-system components and shared org KPI cards:
 * - KpiCard (metrics)
 * - Grid (responsive layouts)
 * - Card (trend cards, agent cards, recommendation cards)
 * - Table (performance metrics)
 * - Chip (category, status, priority indicators)
 * - LinearProgress (performance progress)
 *
 * @example
 * ```tsx
 * <AgentAnalytics
 *   metrics={analyticsMetrics}
 *   usageTrends={trends}
 *   conversationAnalytics={conversations}
 *   performanceMetrics={performance}
 *   recommendations={recommendations}
 *   onExportReport={() => exportData()}
 * />
 * ```
 */
export const AgentAnalytics: React.FC<AgentAnalyticsProps> = ({
    metrics,
    usageTrends,
    conversationAnalytics,
    performanceMetrics,
    recommendations,
    onTrendClick,
    onAgentAnalyticsClick,
    onMetricClick,
    onRecommendationClick,
    onExportReport,
}) => {
    const [selectedTab, setSelectedTab] = useState<'usage' | 'conversations' | 'performance' | 'recommendations'>('usage');

    // Get trend icon
    const getTrendIcon = (trend: 'up' | 'down' | 'stable' | 'improving' | 'declining'): string => {
        switch (trend) {
            case 'up':
            case 'improving':
                return '↑';
            case 'down':
            case 'declining':
                return '↓';
            case 'stable':
                return '→';
        }
    };

    // Get trend color
    const getTrendColor = (trend: 'up' | 'down' | 'stable' | 'improving' | 'declining'): 'success' | 'error' | 'default' => {
        switch (trend) {
            case 'up':
            case 'improving':
                return 'success';
            case 'down':
            case 'declining':
                return 'error';
            case 'stable':
                return 'default';
        }
    };

    // Get status color
    const getStatusColor = (status: 'good' | 'warning' | 'poor'): 'success' | 'warning' | 'error' => {
        switch (status) {
            case 'good':
                return 'success';
            case 'warning':
                return 'warning';
            case 'poor':
                return 'error';
        }
    };

    // Get category color
    const getCategoryColor = (category: string): 'error' | 'warning' | 'default' => {
        switch (category) {
            case 'speed':
            case 'performance':
                return 'error';
            case 'accuracy':
            case 'satisfaction':
            case 'engagement':
                return 'warning';
            case 'efficiency':
                return 'default';
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

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Agent Analytics
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Performance insights and improvement recommendations
                    </Typography>
                </Box>
                {onExportReport && (
                    <Button variant="primary" size="md" onClick={onExportReport}>
                        Export Report
                    </Button>
                )}
            </Box>

            {/* Metrics */}
            <Grid columns={4} gap={4}>
                <KpiCard
                    label="Total Conversations"
                    value={metrics.totalConversations.toLocaleString()}
                    description={`${metrics.activeUsers.toLocaleString()} active users`}
                    status="healthy"
                />

                <KpiCard
                    label="Success Rate"
                    value={`${metrics.successRate}%`}
                    description="Completion rate"
                    status={metrics.successRate >= 80 ? 'healthy' : metrics.successRate >= 60 ? 'warning' : 'error'}
                />

                <KpiCard
                    label="Avg Response Time"
                    value={`${metrics.averageResponseTime}ms`}
                    description="Last 24 hours"
                    status={metrics.averageResponseTime < 500 ? 'healthy' : metrics.averageResponseTime < 1000 ? 'warning' : 'error'}
                />

                <KpiCard
                    label="Satisfaction Score"
                    value={metrics.customerSatisfaction.toFixed(1)}
                    description="Out of 5.0"
                    status={metrics.customerSatisfaction >= 4.0 ? 'healthy' : metrics.customerSatisfaction >= 3.0 ? 'warning' : 'error'}
                />
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label="Usage Trends" value="usage" />
                    <Tab label={`Conversations (${conversationAnalytics.length})`} value="conversations" />
                    <Tab label={`Performance (${performanceMetrics.length})`} value="performance" />
                    <Tab label={`Recommendations (${recommendations.length})`} value="recommendations" />
                </Tabs>

                {/* Usage Tab */}
                {selectedTab === 'usage' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Usage Trends Over Time
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {usageTrends.map((trend) => (
                                <Card key={trend.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onTrendClick?.(trend.id)}>
                                    <Box className="p-4">
                                        {/* Trend Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box>
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                    {trend.period}
                                                </Typography>
                                            </Box>
                                            <Chip
                                                label={`${getTrendIcon(trend.trend)} ${trend.trend}`}
                                                color={getTrendColor(trend.trend)}
                                                size="small"
                                            />
                                        </Box>

                                        {/* Trend Metrics */}
                                        <Grid columns={2} gap={3}>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Conversations
                                                </Typography>
                                                <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                                    {trend.conversations.toLocaleString()}
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Active Users
                                                </Typography>
                                                <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                                    {trend.users.toLocaleString()}
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Success Rate
                                                </Typography>
                                                <Typography variant="h5" className={trend.successRate >= 80 ? 'text-green-600' : 'text-orange-600'}>
                                                    {trend.successRate}%
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Avg Duration
                                                </Typography>
                                                <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                                    {trend.avgDuration} min
                                                </Typography>
                                            </Box>
                                        </Grid>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}

                {/* Conversations Tab */}
                {selectedTab === 'conversations' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Conversation Analytics by Agent
                        </Typography>

                        <Stack spacing={3}>
                            {conversationAnalytics.map((analytics) => (
                                <Card
                                    key={analytics.id}
                                    className="cursor-pointer hover:shadow-md transition-shadow"
                                    onClick={() => onAgentAnalyticsClick?.(analytics.id)}
                                >
                                    <Box className="p-4">
                                        {/* Analytics Header */}
                                        <Box className="mb-3">
                                            <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-1">
                                                {analytics.agentName}
                                            </Typography>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {analytics.totalMessages.toLocaleString()} total messages
                                            </Typography>
                                        </Box>

                                        {/* Analytics Metrics */}
                                        <Grid columns={4} gap={3} className="mb-3">
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Avg Messages/Conv
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {analytics.averageMessagesPerConversation.toFixed(1)}
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Completion Rate
                                                </Typography>
                                                <Typography variant="body2" className={`font-medium ${analytics.completionRate >= 80 ? 'text-green-600' : 'text-orange-600'}`}>
                                                    {analytics.completionRate}%
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Escalation Rate
                                                </Typography>
                                                <Typography variant="body2" className={`font-medium ${analytics.escalationRate < 10 ? 'text-green-600' : 'text-red-600'}`}>
                                                    {analytics.escalationRate}%
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Topics
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {analytics.commonTopics.length}
                                                </Typography>
                                            </Box>
                                        </Grid>

                                        {/* Common Topics */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 mb-2 block">
                                                Common Topics
                                            </Typography>
                                            <Stack direction="row" spacing={1} className="flex-wrap">
                                                {analytics.commonTopics.slice(0, 5).map((topic, i) => (
                                                    <Chip key={i} label={topic} size="small" />
                                                ))}
                                                {analytics.commonTopics.length > 5 && (
                                                    <Chip label={`+${analytics.commonTopics.length - 5} more`} size="small" />
                                                )}
                                            </Stack>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    </Box>
                )}

                {/* Performance Tab */}
                {selectedTab === 'performance' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Performance Metrics
                        </Typography>

                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Metric</TableCell>
                                    <TableCell>Category</TableCell>
                                    <TableCell>Current</TableCell>
                                    <TableCell>Target</TableCell>
                                    <TableCell>Progress</TableCell>
                                    <TableCell>Status</TableCell>
                                    <TableCell>Trend</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {performanceMetrics.map((metric) => (
                                    <TableRow
                                        key={metric.id}
                                        className="cursor-pointer hover:bg-slate-50 dark:hover:bg-neutral-800"
                                        onClick={() => onMetricClick?.(metric.id)}
                                    >
                                        <TableCell>
                                            <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                {metric.metric}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={metric.category} color={getCategoryColor(metric.category)} size="small" />
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                {metric.currentValue} {metric.unit}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {metric.targetValue} {metric.unit}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Box className="w-32">
                                                <LinearProgress
                                                    variant="determinate"
                                                    value={Math.min((metric.currentValue / metric.targetValue) * 100, 100)}
                                                    color={metric.status === 'good' ? 'success' : metric.status === 'warning' ? 'warning' : 'error'}
                                                />
                                            </Box>
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={metric.status} color={getStatusColor(metric.status)} size="small" />
                                        </TableCell>
                                        <TableCell>
                                            <Chip
                                                label={`${getTrendIcon(metric.trend)} ${metric.trend}`}
                                                color={getTrendColor(metric.trend)}
                                                size="small"
                                            />
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </Box>
                )}

                {/* Recommendations Tab */}
                {selectedTab === 'recommendations' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Improvement Recommendations
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
                                            <Box className="text-right">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Potential Score
                                                </Typography>
                                                <Typography variant="h6" className="text-green-600">
                                                    {rec.potentialScore}%
                                                </Typography>
                                                <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                                                    from {rec.currentScore}%
                                                </Typography>
                                            </Box>
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
export const mockAgentAnalyticsData = {
    metrics: {
        totalConversations: 12450,
        activeUsers: 3250,
        averageSessionDuration: 8.5,
        successRate: 87.5,
        customerSatisfaction: 4.2,
        averageResponseTime: 320,
    } as AnalyticsMetrics,

    usageTrends: [
        {
            id: 'trend-1',
            period: 'Last 7 Days',
            conversations: 2840,
            users: 820,
            successRate: 89,
            avgDuration: 8.2,
            trend: 'up',
        },
        {
            id: 'trend-2',
            period: 'Last 30 Days',
            conversations: 12450,
            users: 3250,
            successRate: 87.5,
            avgDuration: 8.5,
            trend: 'stable',
        },
        {
            id: 'trend-3',
            period: 'Last 90 Days',
            conversations: 38200,
            users: 9500,
            successRate: 85,
            avgDuration: 9.1,
            trend: 'down',
        },
    ] as UsageTrend[],

    conversationAnalytics: [
        {
            id: 'conv-1',
            agentName: 'Customer Support Agent',
            totalMessages: 45680,
            averageMessagesPerConversation: 6.8,
            completionRate: 92,
            escalationRate: 5.2,
            commonTopics: ['billing', 'technical-support', 'account-management', 'refunds', 'product-features'],
        },
        {
            id: 'conv-2',
            agentName: 'Sales Assistant',
            totalMessages: 28340,
            averageMessagesPerConversation: 8.2,
            completionRate: 85,
            escalationRate: 8.5,
            commonTopics: ['product-info', 'pricing', 'demos', 'comparisons', 'trials'],
        },
    ] as ConversationAnalytics[],

    performanceMetrics: [
        {
            id: 'metric-1',
            metric: 'Response Time',
            category: 'speed',
            currentValue: 320,
            targetValue: 300,
            unit: 'ms',
            status: 'warning',
            trend: 'improving',
        },
        {
            id: 'metric-2',
            metric: 'First Contact Resolution',
            category: 'accuracy',
            currentValue: 78,
            targetValue: 85,
            unit: '%',
            status: 'warning',
            trend: 'stable',
        },
        {
            id: 'metric-3',
            metric: 'Customer Satisfaction',
            category: 'satisfaction',
            currentValue: 4.2,
            targetValue: 4.5,
            unit: '/5',
            status: 'good',
            trend: 'improving',
        },
    ] as PerformanceMetric[],

    recommendations: [
        {
            id: 'rec-1',
            title: 'Improve Response Accuracy',
            category: 'accuracy',
            priority: 'high',
            currentScore: 78,
            potentialScore: 92,
            description: 'Enhance knowledge base coverage for common queries',
            actionItems: [
                'Add 50+ FAQ entries for frequent questions',
                'Update product documentation with latest features',
                'Train model on recent customer interactions',
            ],
            estimatedImpact: 'Increase first contact resolution by 14%',
        },
        {
            id: 'rec-2',
            title: 'Optimize Response Speed',
            category: 'performance',
            priority: 'medium',
            currentScore: 85,
            potentialScore: 95,
            description: 'Reduce average response time to under 250ms',
            actionItems: [
                'Implement response caching for common queries',
                'Optimize model inference pipeline',
                'Pre-load frequently accessed knowledge base items',
            ],
            estimatedImpact: 'Reduce response time by 70ms on average',
        },
    ] as ImprovementRecommendation[],
};

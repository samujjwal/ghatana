/**
 * Cross-Functional Dashboard Component
 *
 * Component for viewing integrated metrics and insights across all persona layers
 * (IC, Manager, Director, VP, CXO, Root, Agent).
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
    Tabs,
    Tab,
    Button,
    Typography,
    Stack,
} from '@ghatana/ui';

/**
 * Organization-wide metrics
 */
export interface OrganizationMetrics {
    totalEmployees: number;
    activeProjects: number;
    overallHealth: number; // 0-100
    avgProductivity: number; // 0-100
}

/**
 * Layer-specific metrics
 */
export interface LayerMetrics {
    id: string;
    layer: 'ic' | 'manager' | 'director' | 'vp' | 'cxo' | 'root' | 'agent';
    count: number;
    activeCount: number;
    productivity: number; // 0-100
    satisfaction: number; // 0-5
    keyMetric: {
        label: string;
        value: string;
        trend: 'up' | 'down' | 'stable';
    };
}

/**
 * Team performance summary
 */
export interface TeamPerformance {
    id: string;
    teamName: string;
    department: string;
    headCount: number;
    productivity: number; // 0-100
    velocity: number;
    quality: number; // 0-100
    collaboration: number; // 0-100
    status: 'excellent' | 'good' | 'needs-attention' | 'critical';
}

/**
 * Cross-functional initiative
 */
export interface Initiative {
    id: string;
    name: string;
    type: 'strategic' | 'operational' | 'innovation' | 'transformation';
    involvedLayers: string[];
    progress: number; // 0-100
    status: 'on-track' | 'at-risk' | 'delayed' | 'completed';
    impact: 'high' | 'medium' | 'low';
    startDate: string;
    targetDate: string;
}

/**
 * Collaboration metric
 */
export interface CollaborationMetric {
    id: string;
    category: 'communication' | 'knowledge-sharing' | 'coordination' | 'decision-making';
    score: number; // 0-100
    trend: 'improving' | 'stable' | 'declining';
    topContributors: string[];
    recommendations: string[];
}

/**
 * Cross-Functional Dashboard Props
 */
export interface CrossFunctionalDashboardProps {
    /** Organization-wide metrics */
    orgMetrics: OrganizationMetrics;
    /** Layer-specific metrics */
    layerMetrics: LayerMetrics[];
    /** Team performance summaries */
    teamPerformance: TeamPerformance[];
    /** Cross-functional initiatives */
    initiatives: Initiative[];
    /** Collaboration metrics */
    collaborationMetrics: CollaborationMetric[];
    /** Callback when layer is clicked */
    onLayerClick?: (layerId: string) => void;
    /** Callback when team is clicked */
    onTeamClick?: (teamId: string) => void;
    /** Callback when initiative is clicked */
    onInitiativeClick?: (initiativeId: string) => void;
    /** Callback when collaboration metric is clicked */
    onCollaborationClick?: (metricId: string) => void;
    /** Callback when export is clicked */
    onExportDashboard?: () => void;
}

/**
 * Cross-Functional Dashboard Component
 *
 * Provides comprehensive organization-wide view with:
 * - Organization-wide KPI metrics
 * - Layer-specific performance breakdown
 * - Team performance summaries
 * - Cross-functional initiatives tracking
 * - Collaboration effectiveness metrics
 * - Tab-based navigation (Layers, Teams, Initiatives, Collaboration)
 *
 * Reuses @ghatana/ui components:
 * - KpiCard (metrics)
 * - Grid (responsive layouts)
 * - Card (layer cards, team cards, initiative cards)
 * - Chip (status, type, trend indicators)
 *
 * @example
 * ```tsx
 * <CrossFunctionalDashboard
 *   orgMetrics={organizationMetrics}
 *   layerMetrics={layerData}
 *   teamPerformance={teamData}
 *   initiatives={initiativeData}
 *   collaborationMetrics={collaborationData}
 *   onLayerClick={(id) => navigate(`/layers/${id}`)}
 * />
 * ```
 */
export const CrossFunctionalDashboard: React.FC<CrossFunctionalDashboardProps> = ({
    orgMetrics,
    layerMetrics,
    teamPerformance,
    initiatives,
    collaborationMetrics,
    onLayerClick,
    onTeamClick,
    onInitiativeClick,
    onCollaborationClick,
    onExportDashboard,
}) => {
    const [selectedTab, setSelectedTab] = useState<'layers' | 'teams' | 'initiatives' | 'collaboration'>('layers');
    const [statusFilter, setStatusFilter] = useState<'all' | 'excellent' | 'good' | 'needs-attention' | 'critical'>('all');

    // Get status color
    const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'excellent':
            case 'on-track':
            case 'completed':
                return 'success';
            case 'good':
            case 'at-risk':
                return 'warning';
            case 'needs-attention':
            case 'delayed':
                return 'warning';
            case 'critical':
                return 'error';
            default:
                return 'default';
        }
    };

    // Get trend color
    const getTrendColor = (trend: string): 'success' | 'error' | 'default' => {
        switch (trend) {
            case 'up':
            case 'improving':
                return 'success';
            case 'down':
            case 'declining':
                return 'error';
            case 'stable':
            default:
                return 'default';
        }
    };

    // Get trend icon
    const getTrendIcon = (trend: string): string => {
        switch (trend) {
            case 'up':
            case 'improving':
                return '↑';
            case 'down':
            case 'declining':
                return '↓';
            case 'stable':
            default:
                return '→';
        }
    };

    // Get type color
    const getTypeColor = (type: string): 'error' | 'warning' | 'default' => {
        switch (type) {
            case 'strategic':
            case 'transformation':
                return 'error';
            case 'operational':
            case 'innovation':
                return 'warning';
            default:
                return 'default';
        }
    };

    // Get impact color
    const getImpactColor = (impact: string): 'error' | 'warning' | 'default' => {
        switch (impact) {
            case 'high':
                return 'error';
            case 'medium':
                return 'warning';
            case 'low':
            default:
                return 'default';
        }
    };

    // Get category color
    const getCategoryColor = (category: string): 'error' | 'warning' | 'default' => {
        switch (category) {
            case 'communication':
            case 'decision-making':
                return 'error';
            case 'knowledge-sharing':
            case 'coordination':
                return 'warning';
            default:
                return 'default';
        }
    };

    // Format layer name
    const formatLayerName = (layer: string): string => {
        const layerNames: Record<string, string> = {
            ic: 'Individual Contributors',
            manager: 'Managers',
            director: 'Directors',
            vp: 'Vice Presidents',
            cxo: 'C-Level Executives',
            root: 'System Admins',
            agent: 'AI Agents',
        };
        return layerNames[layer] || layer.toUpperCase();
    };

    // Format date
    const formatDate = (dateString: string): string => {
        const date = new Date(dateString);
        return date.toLocaleDateString();
    };

    // Filter teams
    const filteredTeams = statusFilter === 'all' ? teamPerformance : teamPerformance.filter((t) => t.status === statusFilter);

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Cross-Functional Dashboard
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Organization-wide metrics and insights across all layers
                    </Typography>
                </Box>
                {onExportDashboard && (
                    <Button variant="primary" size="md" onClick={onExportDashboard}>
                        Export Dashboard
                    </Button>
                )}
            </Box>

            {/* Organization Metrics */}
            <Grid columns={4} gap={4}>
                <KpiCard
                    label="Total Employees"
                    value={orgMetrics.totalEmployees.toLocaleString()}
                    description={`${layerMetrics.reduce((sum, l) => sum + l.activeCount, 0)} active`}
                    status="healthy"
                />

                <KpiCard
                    label="Active Projects"
                    value={orgMetrics.activeProjects.toLocaleString()}
                    description={`${initiatives.filter((i) => i.status !== 'completed').length} initiatives in progress`}
                    status="healthy"
                />

                <KpiCard
                    label="Overall Health"
                    value={`${orgMetrics.overallHealth}%`}
                    description="Organization-wide"
                    status={orgMetrics.overallHealth >= 80 ? 'healthy' : orgMetrics.overallHealth >= 60 ? 'warning' : 'error'}
                />

                <KpiCard
                    label="Avg Productivity"
                    value={`${orgMetrics.avgProductivity}%`}
                    description="Across all teams"
                    status={orgMetrics.avgProductivity >= 75 ? 'healthy' : orgMetrics.avgProductivity >= 50 ? 'warning' : 'error'}
                />
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`Layers (${layerMetrics.length})`} value="layers" />
                    <Tab label={`Teams (${teamPerformance.length})`} value="teams" />
                    <Tab label={`Initiatives (${initiatives.length})`} value="initiatives" />
                    <Tab label={`Collaboration (${collaborationMetrics.length})`} value="collaboration" />
                </Tabs>

                {/* Layers Tab */}
                {selectedTab === 'layers' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Layer Performance Overview
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {layerMetrics.map((layer) => (
                                <Card key={layer.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onLayerClick?.(layer.id)}>
                                    <Box className="p-4">
                                        {/* Layer Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-1">
                                                    {formatLayerName(layer.layer)}
                                                </Typography>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {layer.activeCount} of {layer.count} active
                                                </Typography>
                                            </Box>
                                            <Box className="text-right">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    {layer.keyMetric.label}
                                                </Typography>
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                    {layer.keyMetric.value}
                                                </Typography>
                                                <Chip label={`${getTrendIcon(layer.keyMetric.trend)} ${layer.keyMetric.trend}`} color={getTrendColor(layer.keyMetric.trend)} size="small" />
                                            </Box>
                                        </Box>

                                        {/* Layer Metrics */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={2} gap={3}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Productivity
                                                    </Typography>
                                                    <Typography
                                                        variant="body2"
                                                        className={`font-medium ${layer.productivity >= 75 ? 'text-green-600' : layer.productivity >= 50 ? 'text-orange-600' : 'text-red-600'}`}
                                                    >
                                                        {layer.productivity}%
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Satisfaction
                                                    </Typography>
                                                    <Typography
                                                        variant="body2"
                                                        className={`font-medium ${layer.satisfaction >= 4.0 ? 'text-green-600' : layer.satisfaction >= 3.0 ? 'text-orange-600' : 'text-red-600'}`}
                                                    >
                                                        {layer.satisfaction.toFixed(1)}/5.0
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

                {/* Teams Tab */}
                {selectedTab === 'teams' && (
                    <Box className="p-4">
                        {/* Team Status Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Chip label={`All (${teamPerformance.length})`} color={statusFilter === 'all' ? 'error' : 'default'} onClick={() => setStatusFilter('all')} />
                            <Chip
                                label={`Excellent (${teamPerformance.filter((t) => t.status === 'excellent').length})`}
                                color={statusFilter === 'excellent' ? 'success' : 'default'}
                                onClick={() => setStatusFilter('excellent')}
                            />
                            <Chip
                                label={`Good (${teamPerformance.filter((t) => t.status === 'good').length})`}
                                color={statusFilter === 'good' ? 'success' : 'default'}
                                onClick={() => setStatusFilter('good')}
                            />
                            <Chip
                                label={`Needs Attention (${teamPerformance.filter((t) => t.status === 'needs-attention').length})`}
                                color={statusFilter === 'needs-attention' ? 'warning' : 'default'}
                                onClick={() => setStatusFilter('needs-attention')}
                            />
                            <Chip
                                label={`Critical (${teamPerformance.filter((t) => t.status === 'critical').length})`}
                                color={statusFilter === 'critical' ? 'error' : 'default'}
                                onClick={() => setStatusFilter('critical')}
                            />
                        </Stack>

                        {/* Team List */}
                        <Grid columns={2} gap={4}>
                            {filteredTeams.map((team) => (
                                <Card key={team.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onTeamClick?.(team.id)}>
                                    <Box className="p-4">
                                        {/* Team Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {team.teamName}
                                                    </Typography>
                                                    <Chip label={team.status} color={getStatusColor(team.status)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {team.department} • {team.headCount} members
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Team Metrics */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={4} gap={2}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Productivity
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {team.productivity}%
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Velocity
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {team.velocity}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Quality
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {team.quality}%
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Collaboration
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {team.collaboration}%
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

                {/* Initiatives Tab */}
                {selectedTab === 'initiatives' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Cross-Functional Initiatives
                        </Typography>

                        <Stack spacing={3}>
                            {initiatives.map((initiative) => (
                                <Card key={initiative.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onInitiativeClick?.(initiative.id)}>
                                    <Box className="p-4">
                                        {/* Initiative Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {initiative.name}
                                                    </Typography>
                                                    <Chip label={initiative.type} color={getTypeColor(initiative.type)} size="small" />
                                                    <Chip label={initiative.status} color={getStatusColor(initiative.status)} size="small" />
                                                    <Chip label={`${initiative.impact} impact`} color={getImpactColor(initiative.impact)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {formatDate(initiative.startDate)} → {formatDate(initiative.targetDate)}
                                                </Typography>
                                            </Box>
                                            <Box className="text-right">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Progress
                                                </Typography>
                                                <Typography
                                                    variant="h6"
                                                    className={
                                                        initiative.progress >= 75 ? 'text-green-600' : initiative.progress >= 50 ? 'text-orange-600' : 'text-red-600'
                                                    }
                                                >
                                                    {initiative.progress}%
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Involved Layers */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 mb-2 block">
                                                Involved Layers ({initiative.involvedLayers.length})
                                            </Typography>
                                            <Stack direction="row" spacing={1} className="flex-wrap">
                                                {initiative.involvedLayers.map((layer, i) => (
                                                    <Chip key={i} label={formatLayerName(layer)} size="small" />
                                                ))}
                                            </Stack>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    </Box>
                )}

                {/* Collaboration Tab */}
                {selectedTab === 'collaboration' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Collaboration Effectiveness
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {collaborationMetrics.map((metric) => (
                                <Card key={metric.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onCollaborationClick?.(metric.id)}>
                                    <Box className="p-4">
                                        {/* Metric Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {metric.category.split('-').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')}
                                                    </Typography>
                                                    <Chip label={metric.category} color={getCategoryColor(metric.category)} size="small" />
                                                    <Chip label={`${getTrendIcon(metric.trend)} ${metric.trend}`} color={getTrendColor(metric.trend)} size="small" />
                                                </Box>
                                            </Box>
                                            <Box className="text-right">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Score
                                                </Typography>
                                                <Typography
                                                    variant="h6"
                                                    className={metric.score >= 80 ? 'text-green-600' : metric.score >= 60 ? 'text-orange-600' : 'text-red-600'}
                                                >
                                                    {metric.score}%
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Top Contributors */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 mb-2 block">
                                                Top Contributors
                                            </Typography>
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                {metric.topContributors.join(', ')}
                                            </Typography>
                                        </Box>

                                        {/* Recommendations */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 mb-2 block">
                                                Recommendations
                                            </Typography>
                                            <ul className="list-disc list-inside space-y-1">
                                                {metric.recommendations.slice(0, 2).map((rec, i) => (
                                                    <li key={i}>
                                                        <Typography variant="body2" className="inline text-slate-900 dark:text-neutral-100">
                                                            {rec}
                                                        </Typography>
                                                    </li>
                                                ))}
                                                {metric.recommendations.length > 2 && (
                                                    <li>
                                                        <Typography variant="body2" className="inline text-slate-600 dark:text-neutral-400">
                                                            +{metric.recommendations.length - 2} more
                                                        </Typography>
                                                    </li>
                                                )}
                                            </ul>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}
            </Card>
        </Box>
    );
};

/**
 * Mock data for development/testing
 */
export const mockCrossFunctionalDashboardData = {
    orgMetrics: {
        totalEmployees: 1250,
        activeProjects: 48,
        overallHealth: 85,
        avgProductivity: 78,
    } as OrganizationMetrics,

    layerMetrics: [
        {
            id: 'layer-1',
            layer: 'ic',
            count: 850,
            activeCount: 820,
            productivity: 82,
            satisfaction: 4.2,
            keyMetric: {
                label: 'Completed Tasks',
                value: '3,420',
                trend: 'up',
            },
        },
        {
            id: 'layer-2',
            layer: 'manager',
            count: 180,
            activeCount: 175,
            productivity: 78,
            satisfaction: 4.0,
            keyMetric: {
                label: 'Team Velocity',
                value: '42 pts',
                trend: 'stable',
            },
        },
        {
            id: 'layer-3',
            layer: 'director',
            count: 85,
            activeCount: 82,
            productivity: 75,
            satisfaction: 4.1,
            keyMetric: {
                label: 'Budget Utilization',
                value: '92%',
                trend: 'up',
            },
        },
        {
            id: 'layer-4',
            layer: 'vp',
            count: 45,
            activeCount: 44,
            productivity: 80,
            satisfaction: 4.3,
            keyMetric: {
                label: 'Strategic Goals',
                value: '85%',
                trend: 'up',
            },
        },
        {
            id: 'layer-5',
            layer: 'cxo',
            count: 12,
            activeCount: 12,
            productivity: 88,
            satisfaction: 4.5,
            keyMetric: {
                label: 'Revenue Growth',
                value: '+18%',
                trend: 'up',
            },
        },
        {
            id: 'layer-6',
            layer: 'root',
            count: 8,
            activeCount: 8,
            productivity: 92,
            satisfaction: 4.4,
            keyMetric: {
                label: 'Uptime',
                value: '99.9%',
                trend: 'stable',
            },
        },
        {
            id: 'layer-7',
            layer: 'agent',
            count: 70,
            activeCount: 65,
            productivity: 95,
            satisfaction: 4.6,
            keyMetric: {
                label: 'Response Time',
                value: '320ms',
                trend: 'improving',
            },
        },
    ] as LayerMetrics[],

    teamPerformance: [
        {
            id: 'team-1',
            teamName: 'Platform Engineering',
            department: 'Engineering',
            headCount: 25,
            productivity: 88,
            velocity: 45,
            quality: 92,
            collaboration: 85,
            status: 'excellent',
        },
        {
            id: 'team-2',
            teamName: 'Product Design',
            department: 'Product',
            headCount: 15,
            productivity: 82,
            velocity: 38,
            quality: 90,
            collaboration: 88,
            status: 'excellent',
        },
        {
            id: 'team-3',
            teamName: 'Data Science',
            department: 'Analytics',
            headCount: 18,
            productivity: 75,
            velocity: 32,
            quality: 85,
            collaboration: 78,
            status: 'good',
        },
        {
            id: 'team-4',
            teamName: 'Customer Success',
            department: 'Sales',
            headCount: 22,
            productivity: 68,
            velocity: 28,
            quality: 72,
            collaboration: 70,
            status: 'needs-attention',
        },
    ] as TeamPerformance[],

    initiatives: [
        {
            id: 'init-1',
            name: 'AI-Powered Platform Transformation',
            type: 'transformation',
            involvedLayers: ['ic', 'manager', 'director', 'vp', 'cxo', 'agent'],
            progress: 65,
            status: 'on-track',
            impact: 'high',
            startDate: '2025-01-15T00:00:00Z',
            targetDate: '2025-12-31T00:00:00Z',
        },
        {
            id: 'init-2',
            name: 'Cross-Functional Collaboration Framework',
            type: 'operational',
            involvedLayers: ['manager', 'director', 'vp'],
            progress: 42,
            status: 'at-risk',
            impact: 'medium',
            startDate: '2025-03-01T00:00:00Z',
            targetDate: '2025-09-30T00:00:00Z',
        },
        {
            id: 'init-3',
            name: 'Innovation Lab Launch',
            type: 'innovation',
            involvedLayers: ['ic', 'manager', 'director'],
            progress: 88,
            status: 'on-track',
            impact: 'high',
            startDate: '2025-02-01T00:00:00Z',
            targetDate: '2025-06-30T00:00:00Z',
        },
    ] as Initiative[],

    collaborationMetrics: [
        {
            id: 'collab-1',
            category: 'communication',
            score: 85,
            trend: 'improving',
            topContributors: ['Platform Engineering', 'Product Design', 'Data Science'],
            recommendations: ['Increase daily standup participation', 'Implement async communication guidelines', 'Set up team communication channels'],
        },
        {
            id: 'collab-2',
            category: 'knowledge-sharing',
            score: 72,
            trend: 'stable',
            topContributors: ['Data Science', 'Platform Engineering', 'Customer Success'],
            recommendations: ['Create knowledge base documentation', 'Schedule regular tech talks', 'Implement pair programming sessions'],
        },
        {
            id: 'collab-3',
            category: 'coordination',
            score: 78,
            trend: 'improving',
            topContributors: ['Product Design', 'Platform Engineering', 'Data Science'],
            recommendations: ['Align sprint planning across teams', 'Improve dependency tracking', 'Set up cross-team checkpoints'],
        },
        {
            id: 'collab-4',
            category: 'decision-making',
            score: 68,
            trend: 'declining',
            topContributors: ['Platform Engineering', 'Product Design'],
            recommendations: ['Clarify decision-making authority', 'Implement RACI framework', 'Speed up approval processes', 'Create escalation paths'],
        },
    ] as CollaborationMetric[],
};

/**
 * Performance Dashboard Component
 *
 * Comprehensive dashboard for monitoring multiple performance metrics
 * with real-time updates, historical analysis, and predictive insights.
 */

import { LayoutDashboard as DashboardIcon, Activity as TimelineIcon, Gauge as SpeedIcon, Hammer as BuildIcon, CloudUpload as DeploymentIcon, FlaskConical as TestIcon, AlertTriangle as WarningIcon, CheckCircle as CheckCircleIcon, AlertCircle as ErrorIcon, RefreshCw as RefreshIcon, Settings as SettingsIcon, TrendingUp as TrendingUpIcon, TrendingDown as TrendingDownIcon } from 'lucide-react';
import { Box, Grid, Typography, Card, CardContent, FormControl, InputLabel, Select, MenuItem, Chip, Alert, IconButton, Tooltip, Tab, Tabs, Switch, FormControlLabel, Divider, InteractiveList as List, ListItem, ListItemText, ListItemIcon, Surface as Paper } from '@ghatana/ui';
import { resolveMuiColor } from '../../utils/safePalette';
import React, { useState, useCallback, useMemo } from 'react';

import { PerformanceTrendingChart } from './PerformanceTrendingChart';
import { usePerformanceMonitoring } from '../../hooks/performance/usePerformanceMonitoring';
import { wrapForTooltip } from '../../utils/accessibility';

/**
 *
 */
export interface PerformanceDashboardProps {
    /** Dashboard title */
    title?: string;
    /** Default time range */
    defaultTimeRange?: '1h' | '6h' | '24h' | '7d' | '30d';
    /** Metrics to display */
    metrics?: string[];
    /** Whether to show real-time updates */
    realTimeUpdates?: boolean;
    /** Alert thresholds for metrics */
    alertThresholds?: Record<string, { warning: number; critical: number }>;
    /** Baseline values for comparison */
    baselines?: Record<string, number>;
    /** Custom styling */
    className?: string;
}

/**
 *
 */
export interface MetricSummary {
    name: string;
    icon: React.ReactNode;
    current: number;
    trend: 'up' | 'down' | 'stable';
    changePercent: number;
    status: 'good' | 'warning' | 'critical';
    unit: string;
}

const metricConfigs = {
    buildTime: {
        title: 'Build Time',
        icon: <BuildIcon />,
        unit: 'minutes',
        description: 'Time taken to complete builds'
    },
    deployTime: {
        title: 'Deploy Time',
        icon: <DeploymentIcon />,
        unit: 'minutes',
        description: 'Time taken for deployments'
    },
    testTime: {
        title: 'Test Execution',
        icon: <TestIcon />,
        unit: 'minutes',
        description: 'Time taken to run test suites'
    },
    bundleSize: {
        title: 'Bundle Size',
        icon: <SpeedIcon />,
        unit: 'MB',
        description: 'Application bundle size'
    },
    memoryUsage: {
        title: 'Memory Usage',
        icon: <TimelineIcon />,
        unit: 'MB',
        description: 'Peak memory consumption'
    },
    cpuUsage: {
        title: 'CPU Usage',
        icon: <TimelineIcon />,
        unit: '%',
        description: 'Average CPU utilization'
    }
};

export const PerformanceDashboard: React.FC<PerformanceDashboardProps> = ({
    title = 'Performance Dashboard',
    defaultTimeRange = '24h',
    metrics = ['buildTime', 'deployTime', 'testTime', 'bundleSize'],
    realTimeUpdates = true,
    alertThresholds = {},
    baselines = {},
    className
}) => {
    const [selectedTab, setSelectedTab] = useState(0);
    const [timeRange, setTimeRange] = useState(defaultTimeRange);
    const [enableRealTime, setEnableRealTime] = useState(realTimeUpdates);

    const {
        getMetricHistory,
        getTrendAnalysis,
        alerts,
        isMonitoring,
        lastUpdate
    } = usePerformanceMonitoring({
        realTimeUpdates: enableRealTime,
        trendAnalysis: true,
        regressionDetection: true,
        alertThresholds: Object.values(alertThresholds).reduce((acc, threshold) => ({ ...acc, ...threshold }), {})
    });

    // Calculate metric summaries
    const metricSummaries: MetricSummary[] = useMemo(() => {
        return metrics.map(metric => {
            const history = getMetricHistory(metric, timeRange);
            const trend = getTrendAnalysis(metric);
            const config = metricConfigs[metric as keyof typeof metricConfigs];

            const current = history.length > 0 ? history[history.length - 1].value : 0;
            const thresholds = alertThresholds[metric];

            let status: 'good' | 'warning' | 'critical' = 'good';
            if (thresholds) {
                if (current >= thresholds.critical) status = 'critical';
                else if (current >= thresholds.warning) status = 'warning';
            }

            return {
                name: config?.title || metric,
                icon: config?.icon || <TimelineIcon />,
                current,
                trend: trend?.direction === 'improving' ? 'down' :
                    trend?.direction === 'degrading' ? 'up' : 'stable',
                changePercent: trend?.changePercent || 0,
                status,
                unit: config?.unit || 'units'
            };
        });
    }, [metrics, getMetricHistory, getTrendAnalysis, timeRange, alertThresholds, lastUpdate]);

    // Active alerts
    const activeAlerts = useMemo(() =>
        alerts.filter(alert => !alert.resolved),
        [alerts]
    );

    // Handle tab change
    const handleTabChange = useCallback((_: React.SyntheticEvent, newValue: number) => {
        setSelectedTab(newValue);
    }, []);

    // Handle time range change
    const handleTimeRangeChange = useCallback((event: unknown) => {
        setTimeRange(event.target.value);
    }, []);

    // Toggle real-time updates
    const handleRealTimeToggle = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
        setEnableRealTime(event.target.checked);
    }, []);

    // Render metric summary card
    const renderMetricSummary = (summary: MetricSummary) => {
        const getTrendIcon = () => {
            switch (summary.trend) {
            case 'up': return <TrendingUpIcon color={resolveMuiColor(useTheme(), summary.status === 'critical' ? 'error' : 'primary', 'default') as unknown} />;
                case 'down': return <TrendingDownIcon color={resolveMuiColor(useTheme(), 'success', 'default') as unknown} />;
                default: return null;
            }
        };

        const getStatusColor = () => {
            switch (summary.status) {
                case 'critical': return 'error';
                case 'warning': return 'warning';
                default: return 'success';
            }
        };

        return (
            <Card key={summary.name} className="h-full">
                <CardContent>
                    <Box className="flex items-center justify-between mb-2">
                        <Box className="flex items-center gap-2">
                            {summary.icon}
                            <Typography as="h6" component="div">
                                {summary.name}
                            </Typography>
                        </Box>
                        {getTrendIcon()}
                    </Box>

                    <Typography as="h4" component="div" color={getStatusColor()}>
                        {summary.current.toFixed(2)} {summary.unit}
                    </Typography>

                    <Box className="flex items-center gap-2 mt-2">
                            <Chip
                                size="sm"
                                label={`${summary.changePercent >= 0 ? '+' : ''}${summary.changePercent.toFixed(1)}%`}
                                color={resolveMuiColor(useTheme(), summary.trend === 'down' ? 'success' : summary.trend === 'up' ? 'error' : 'default', 'default')}
                                variant="outlined"
                            />
                            <Chip
                                size="sm"
                                label={summary.status}
                                color={resolveMuiColor(useTheme(), getStatusColor(), 'default')}
                                variant="filled"
                            />
                    </Box>
                </CardContent>
            </Card>
        );
    };

    return (
        <Box className={className}>
            {/* Header */}
            <Box className="flex items-center justify-between mb-6">
                <Box className="flex items-center gap-2">
                    <DashboardIcon color={resolveMuiColor(useTheme(), 'primary', 'default') as unknown} />
                    <Typography as="h4" component="h1">
                        {title}
                    </Typography>
                    {isMonitoring && (
                        <Chip size="sm" label="Live" color={resolveMuiColor(useTheme(), 'success', 'default') as unknown} variant="outlined" />
                    )}
                </Box>

                <Box className="flex items-center gap-4">
                    <FormControl size="sm" className="min-w-[120px]">
                        <InputLabel>Time Range</InputLabel>
                        <Select value={timeRange} onChange={handleTimeRangeChange} label="Time Range">
                            <MenuItem value="1h">Last Hour</MenuItem>
                            <MenuItem value="6h">Last 6 Hours</MenuItem>
                            <MenuItem value="24h">Last 24 Hours</MenuItem>
                            <MenuItem value="7d">Last 7 Days</MenuItem>
                            <MenuItem value="30d">Last 30 Days</MenuItem>
                        </Select>
                    </FormControl>

                    <FormControlLabel
                        control={
                            <Switch
                                checked={enableRealTime}
                                onChange={handleRealTimeToggle}
                                size="sm"
                            />
                        }
                        label="Real-time"
                    />

                    <Tooltip title="Refresh">
                        {wrapForTooltip(
                            <IconButton>
                                <RefreshIcon aria-hidden={true} />
                            </IconButton>,
                            { 'aria-describedby': `performance-dashboard-refresh` }
                        )}
                    </Tooltip>

                    <Tooltip title="Settings">
                        {wrapForTooltip(
                            <IconButton>
                                <SettingsIcon aria-hidden={true} />
                            </IconButton>,
                            { 'aria-describedby': `performance-dashboard-settings` }
                        )}
                    </Tooltip>
                </Box>
            </Box>

            {/* Active Alerts */}
            {activeAlerts.length > 0 && (
                <Alert
                    severity={activeAlerts.some(a => a.severity === 'critical') ? 'error' : 'warning'}
                    className="mb-6"
                >
                    <Typography as="p" className="text-sm font-medium" gutterBottom>
                        {activeAlerts.length} Active Alert{activeAlerts.length !== 1 ? 's' : ''}
                    </Typography>
                    {activeAlerts.slice(0, 3).map(alert => (
                        <Typography key={alert.id} as="p" className="text-sm">
                            • {alert.message}
                        </Typography>
                    ))}
                    {activeAlerts.length > 3 && (
                        <Typography as="p" className="text-sm" color="text.secondary">
                            ... and {activeAlerts.length - 3} more
                        </Typography>
                    )}
                </Alert>
            )}

            {/* Tabs */}
            <Box className="mb-6 border-gray-200 dark:border-gray-700 border-b" >
                <Tabs value={selectedTab} onChange={handleTabChange}>
                    <Tab label="Overview" />
                    <Tab label="Detailed Charts" />
                    <Tab label="Alerts & Issues" />
                </Tabs>
            </Box>

            {/* Tab Content */}
            {selectedTab === 0 && (
                <Box>
                    {/* Metric Summary Cards */}
                    <Box
                        className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6 mb-8"
                    >
                        {metricSummaries.map(summary => (
                            <Box key={summary.name}>
                                {renderMetricSummary(summary)}
                            </Box>
                        ))}
                    </Box>

                    {/* Main Charts Grid */}
                    <Box
                        className="grid grid-cols-1 lg:grid-cols-2 gap-6"
                    >
                        {metrics.slice(0, 4).map(metric => (
                            <Box key={metric}>
                                <PerformanceTrendingChart
                                    metric={metric}
                                    title={metricConfigs[metric as keyof typeof metricConfigs]?.title}
                                    timeRange={timeRange}
                                    realTime={enableRealTime}
                                    baseline={baselines[metric]}
                                    alertThresholds={alertThresholds[metric]}
                                    height={250}
                                />
                            </Box>
                        ))}
                    </Box>
                </Box>
            )}

            {selectedTab === 1 && (
                <Box>
                    <Box className="flex flex-col gap-6">
                        {metrics.map(metric => (
                            <Box key={metric}>
                                <PerformanceTrendingChart
                                    metric={metric}
                                    title={metricConfigs[metric as keyof typeof metricConfigs]?.title}
                                    timeRange={timeRange}
                                    realTime={enableRealTime}
                                    baseline={baselines[metric]}
                                    alertThresholds={alertThresholds[metric]}
                                    height={400}
                                    showTrend={true}
                                    showRegressions={true}
                                />
                            </Box>
                        ))}
                    </Box>
                </Box>
            )}

            {selectedTab === 2 && (
                <Box>
                    <Box className="grid grid-cols-1 md:grid-cols-[2fr_1fr] gap-6">
                        <Box>
                            <Paper className="p-4">
                                <Typography as="h6" gutterBottom>
                                    Recent Alerts
                                </Typography>
                                <Divider className="mb-4" />

                                {alerts.length === 0 ? (
                                    <Box className="text-center py-8 text-gray-500 dark:text-gray-400">
                                        <CheckCircleIcon className="mb-2 text-5xl" />
                                        <Typography>No alerts detected</Typography>
                                    </Box>
                                ) : (
                                    <List>
                                        {alerts.slice(0, 10).map(alert => (
                                            <ListItem key={alert.id}>
                                                <ListItemIcon>
                                                    {alert.severity === 'critical' ? (
                                                        <ErrorIcon tone="danger" />
                                                    ) : (
                                                        <WarningIcon tone="warning" />
                                                    )}
                                                </ListItemIcon>
                                                <ListItemText
                                                    primary={alert.message}
                                                    secondary={`${alert.timestamp.toLocaleString()} • ${alert.metric}`}
                                                />
                                                <Chip
                                                    size="sm"
                                                    label={alert.resolved ? 'Resolved' : alert.severity}
                                                    color={resolveMuiColor(useTheme(), alert.resolved ? 'success' : alert.severity === 'critical' ? 'error' : 'warning', 'default')}
                                                    variant={alert.resolved ? 'outlined' : 'filled'}
                                                />
                                            </ListItem>
                                        ))}
                                    </List>
                                )}
                            </Paper>
                        </Box>

                        <Box>
                            <Paper className="p-4">
                                <Typography as="h6" gutterBottom>
                                    Alert Summary
                                </Typography>
                                <Divider className="mb-4" />

                                <Box className="flex flex-col gap-4">
                                    <Box className="flex justify-between items-center">
                                        <Typography as="p" className="text-sm">Critical Alerts</Typography>
                                        <Chip
                                            size="sm"
                                            label={alerts.filter(a => !a.resolved && a.severity === 'critical').length}
                                            tone="danger"
                                        />
                                    </Box>

                                    <Box className="flex justify-between items-center">
                                        <Typography as="p" className="text-sm">Warning Alerts</Typography>
                                        <Chip
                                            size="sm"
                                            label={alerts.filter(a => !a.resolved && a.severity === 'high').length}
                                            tone="warning"
                                        />
                                    </Box>

                                    <Box className="flex justify-between items-center">
                                        <Typography as="p" className="text-sm">Total Alerts</Typography>
                                        <Chip
                                            size="sm"
                                            label={alerts.length}
                                            variant="outlined"
                                        />
                                    </Box>
                                </Box>
                            </Paper>
                        </Box>
                    </Box>
                </Box>
            )}

            {/* Last Update */}
            {lastUpdate && (
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="block mt-4 text-center">
                    Last updated: {lastUpdate.toLocaleString()}
                </Typography>
            )}
        </Box>
    );
};

/**
 * Performance Trending Chart Component
 * 
 * Interactive chart component for visualizing performance metrics over time
 * with trend analysis, regression detection, and comparative insights.
 * Supports real-time updates and historical data exploration.
 */

import { TrendingUp as TrendingUpIcon, TrendingDown as TrendingDownIcon, MoveRight as TrendingFlatIcon, AlertTriangle as WarningIcon, Info as InfoIcon, RefreshCw as RefreshIcon, Download as DownloadIcon, Activity as TimelineIcon } from 'lucide-react';
import { Box, Typography, Card, CardContent, FormControl, InputLabel, Select, MenuItem, IconButton, Tooltip, Chip, Alert, LinearProgress, ToggleButton, ToggleButtonGroup } from '@ghatana/ui';
import { resolveMuiColor } from '../../utils/safePalette';
import React, { useMemo, useState, useCallback } from 'react';

import { usePerformanceMonitoring } from '../../hooks/performance/usePerformanceMonitoring';
import { wrapForTooltip } from '../../utils/accessibility';


import type { PerformanceTrend } from '../../hooks/performance/usePerformanceMonitoring';


/**
 *
 */
export interface TrendingChartProps {
    /** Metric name to display */
    metric: string;
    /** Chart title */
    title?: string;
    /** Chart height in pixels */
    height?: number;
    /** Time range for data display */
    timeRange?: '1h' | '6h' | '24h' | '7d' | '30d';
    /** Whether to show trend analysis */
    showTrend?: boolean;
    /** Whether to show regression markers */
    showRegressions?: boolean;
    /** Whether to enable real-time updates */
    realTime?: boolean;
    /** Baseline threshold for comparison */
    baseline?: number;
    /** Alert thresholds */
    alertThresholds?: {
        warning: number;
        critical: number;
    };
    /** Custom styling */
    className?: string;
}

/**
 *
 */
export interface ChartDataPoint {
    timestamp: Date;
    value: number;
    isRegression?: boolean;
    baseline?: number;
    severity?: 'normal' | 'warning' | 'critical';
}

/**
 *
 */
export interface TrendIndicatorProps {
    trend: PerformanceTrend | null;
    compact?: boolean;
}

// Simple SVG-based line chart component
const SimpleLineChart = React.memo<{
    data: ChartDataPoint[];
    width: number;
    height: number;
    baseline?: number;
    alertThresholds?: { warning: number; critical: number };
}>(({ data, width, height, baseline, alertThresholds }) => {
    if (data.length === 0) {
        return (
            <Box
                className="flex items-center justify-center text-gray-500 dark:text-gray-400"
            >
                No data available
            </Box>
        );
    }

    const values = data.map(d => d.value);
    const minValue = Math.min(...values, baseline || 0, alertThresholds?.warning || 0);
    const maxValue = Math.max(...values, baseline || 0, alertThresholds?.critical || 0);
    const range = maxValue - minValue || 1;

    const points = data.map((point, index) => ({
        x: (index / (data.length - 1 || 1)) * width,
        y: height - ((point.value - minValue) / range) * height,
        ...point
    }));

    const pathData = points
        .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`)
        .join(' ');

    return (
        <svg width={width} height={height} style={{ overflow: 'visible' }}>
            {/* Baseline line */}
            {baseline && (
                <line
                    x1={0}
                    y1={height - ((baseline - minValue) / range) * height}
                    x2={width}
                    y2={height - ((baseline - minValue) / range) * height}
                    stroke="var(--color-info-main, #2196f3)"
                    strokeWidth={1}
                    strokeDasharray="4,4"
                />
            )}

            {/* Alert threshold lines */}
            {alertThresholds?.warning && (
                <line
                    x1={0}
                    y1={height - ((alertThresholds.warning - minValue) / range) * height}
                    x2={width}
                    y2={height - ((alertThresholds.warning - minValue) / range) * height}
                    stroke="var(--color-warning-main, #ff9800)"
                    strokeWidth={1}
                    strokeDasharray="2,2"
                />
            )}

            {alertThresholds?.critical && (
                <line
                    x1={0}
                    y1={height - ((alertThresholds.critical - minValue) / range) * height}
                    x2={width}
                    y2={height - ((alertThresholds.critical - minValue) / range) * height}
                    stroke="var(--color-error-main, #f44336)"
                    strokeWidth={1}
                    strokeDasharray="2,2"
                />
            )}

            {/* Main trend line */}
            <path
                d={pathData}
                fill="none"
                stroke="var(--color-primary-dark, #1976d2)"
                strokeWidth={2}
            />

            {/* Data points */}
            {points.map((point, index) => (
                <circle
                    key={index}
                    cx={point.x}
                    cy={point.y}
                    r={point.isRegression ? 6 : 3}
                    fill={
                        point.isRegression ? 'var(--color-error-main, #f44336)' :
                            point.severity === 'critical' ? 'var(--color-error-main, #f44336)' :
                                point.severity === 'warning' ? 'var(--color-warning-main, #ff9800)' : 'var(--color-primary-dark, #1976d2)'
                    }
                    stroke={point.isRegression ? 'var(--color-common-white, #fff)' : 'none'}
                    strokeWidth={point.isRegression ? 2 : 0}
                >
                    <title>
                        {`${point.timestamp.toLocaleString()}: ${point.value.toFixed(2)}`}
                    </title>
                </circle>
            ))}
        </svg>
    );
});

// Trend indicator component
const TrendIndicator = React.memo<TrendIndicatorProps>(({ trend, compact = false }) => {
    if (!trend) {
        return (
            <Chip
                size="sm"
                icon={<TrendingFlatIcon />}
                label="No trend"
                variant="outlined"
            />
        );
    }

    const getTrendIcon = () => {
        switch (trend.direction) {
            case 'improving': return <TrendingDownIcon />;
            case 'degrading': return <TrendingUpIcon />;
            default: return <TrendingFlatIcon />;
        }
    };

    const getTrendColor = () => {
        switch (trend.direction) {
            case 'improving': return 'success';
            case 'degrading': return 'error';
            default: return 'default';
        }
    };

    const label = compact
        ? `${Math.abs(trend.changePercent).toFixed(1)}%`
        : `${trend.direction} ${Math.abs(trend.changePercent).toFixed(1)}%`;

    const theme = useTheme();
    return (
        <Chip
            size="sm"
            icon={getTrendIcon()}
            label={label}
            color={resolveMuiColor(theme, getTrendColor(), 'default') as unknown}
            variant={trend.confidence > 0.7 ? 'filled' : 'outlined'}
        />
    );
});

export const PerformanceTrendingChart: React.FC<TrendingChartProps> = ({
    metric,
    title,
    height = 300,
    timeRange = '24h',
    showTrend = true,
    showRegressions = true,
    realTime = true,
    baseline,
    alertThresholds,
    className
}) => {
    const [selectedTimeRange, setSelectedTimeRange] = useState(timeRange);
    const [chartType, setChartType] = useState<'line' | 'area'>('line');

    const {
        getMetricHistory,
        getTrendAnalysis,
        detectRegressions,
        isMonitoring,
        lastUpdate,
        exportData
    } = usePerformanceMonitoring({
        realTimeUpdates: realTime,
        trendAnalysis: showTrend,
        regressionDetection: showRegressions
    });

    // Get performance data for the selected metric and time range
    const metricData = useMemo(() =>
        getMetricHistory(metric, selectedTimeRange),
        [getMetricHistory, metric, selectedTimeRange, lastUpdate]
    );

    // Get trend analysis
    const trendAnalysis = useMemo(() =>
        showTrend ? getTrendAnalysis(metric) : null,
        [getTrendAnalysis, metric, showTrend, lastUpdate]
    );

    // Detect regressions
    const regressionPoints = useMemo(() =>
        showRegressions ? detectRegressions(metric) : [],
        [detectRegressions, metric, showRegressions, lastUpdate]
    );

    // Transform data for chart
    const chartData: ChartDataPoint[] = useMemo(() => {
        return metricData.map(dataPoint => ({
            timestamp: dataPoint.timestamp,
            value: dataPoint.value,
            baseline,
            isRegression: regressionPoints.some(
                regPoint => Math.abs(regPoint.getTime() - dataPoint.timestamp.getTime()) < 60000
            ),
            severity: alertThresholds ? (
                dataPoint.value >= alertThresholds.critical ? 'critical' :
                    dataPoint.value >= alertThresholds.warning ? 'warning' : 'normal'
            ) : 'normal'
        }));
    }, [metricData, baseline, regressionPoints, alertThresholds]);

    // Handle time range change
    const handleTimeRangeChange = useCallback((newTimeRange: string) => {
        setSelectedTimeRange(newTimeRange as typeof selectedTimeRange);
    }, []);

    // Handle chart type change
    const handleChartTypeChange = useCallback((_: React.MouseEvent<HTMLElement>, newType: string | null) => {
        if (newType) setChartType(newType as typeof chartType);
    }, []);

    // Handle data export
    const handleExport = useCallback(() => {
        const data = exportData('csv');
        const blob = new Blob([data], { type: 'text/csv' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `performance_${metric}_${selectedTimeRange}.csv`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }, [exportData, metric, selectedTimeRange]);

    const hasAlerts = chartData.some(point => point.severity !== 'normal');
    const criticalCount = chartData.filter(point => point.severity === 'critical').length;
    const warningCount = chartData.filter(point => point.severity === 'warning').length;

    return (
        <Card className={className} className="h-full">
            <CardContent>
                {/* Header */}
                <Box className="flex items-center justify-between mb-4">
                    <Box className="flex items-center gap-2">
                        <TimelineIcon color={resolveMuiColor(useTheme(), 'primary', 'default') as unknown} />
                        <Typography as="h6" component="h2">
                            {title || `Performance Trend: ${metric}`}
                        </Typography>
                        {isMonitoring && (
                            <Chip size="sm" label="Live" color={resolveMuiColor(useTheme(), 'success', 'default') as unknown} variant="outlined" />
                        )}
                    </Box>

                    <Box className="flex items-center gap-2">
                        {showTrend && <TrendIndicator trend={trendAnalysis} />}

                        <Tooltip title="Export data">
                            {wrapForTooltip(
                                    <IconButton size="sm" onClick={handleExport}>
                                        <DownloadIcon aria-hidden={true} />
                                    </IconButton>,
                                    { 'aria-describedby': `performance-export-tooltip-${metric}` }
                                )}
                        </Tooltip>

                        <Tooltip title="Refresh">
                            {wrapForTooltip(
                                    <IconButton size="sm" onClick={() => { /* noop for now */ }}>
                                        <RefreshIcon aria-hidden={true} />
                                    </IconButton>,
                                    { 'aria-describedby': `performance-refresh-tooltip-${metric}` }
                                )}
                        </Tooltip>
                    </Box>
                </Box>

                {/* Controls */}
                <Box className="flex items-center gap-4 mb-4">
                    <FormControl size="sm" className="min-w-[120px]">
                        <InputLabel>Time Range</InputLabel>
                        <Select
                            value={selectedTimeRange}
                            onChange={(e) => handleTimeRangeChange(e.target.value)}
                            label="Time Range"
                        >
                            <MenuItem value="1h">Last Hour</MenuItem>
                            <MenuItem value="6h">Last 6 Hours</MenuItem>
                            <MenuItem value="24h">Last 24 Hours</MenuItem>
                            <MenuItem value="7d">Last 7 Days</MenuItem>
                            <MenuItem value="30d">Last 30 Days</MenuItem>
                        </Select>
                    </FormControl>

                    <ToggleButtonGroup
                        value={chartType}
                        exclusive
                        onChange={handleChartTypeChange}
                        size="sm"
                    >
                        <ToggleButton value="line">Line</ToggleButton>
                        <ToggleButton value="area">Area</ToggleButton>
                    </ToggleButtonGroup>
                </Box>

                {/* Alerts Summary */}
                {hasAlerts && (
                    <Box className="mb-4">
                        {criticalCount > 0 && (
                            <Alert severity="error" className="mb-2">
                                <Box className="flex items-center gap-2">
                                    <WarningIcon />
                                    <Typography as="p" className="text-sm">
                                        {criticalCount} critical threshold breach{criticalCount !== 1 ? 'es' : ''} detected
                                    </Typography>
                                </Box>
                            </Alert>
                        )}
                        {warningCount > 0 && (
                            <Alert severity="warning">
                                <Box className="flex items-center gap-2">
                                    <InfoIcon />
                                    <Typography as="p" className="text-sm">
                                        {warningCount} warning threshold breach{warningCount !== 1 ? 'es' : ''} detected
                                    </Typography>
                                </Box>
                            </Alert>
                        )}
                    </Box>
                )}

                {/* Loading indicator */}
                {isMonitoring && (
                    <LinearProgress className="mb-2" />
                )}

                {/* Chart */}
                <Box className="w-full">
                    <SimpleLineChart
                        data={chartData}
                        width={800}
                        height={height}
                        baseline={baseline}
                        alertThresholds={alertThresholds}
                    />
                </Box>

                {/* Chart Legend */}
                <Box className="flex items-center gap-4 mt-4 flex-wrap">
                    <Box className="flex items-center gap-1">
                        <Box className="w-[12px] h-[2px] bg-blue-800" />
                        <Typography as="span" className="text-xs text-gray-500">Performance</Typography>
                    </Box>

                    {baseline && (
                        <Box className="flex items-center gap-1">
                            <Box className="w-[12px] h-[1px] bg-sky-600" />
                            <Typography as="span" className="text-xs text-gray-500">Baseline</Typography>
                        </Box>
                    )}

                    {alertThresholds?.warning && (
                        <Box className="flex items-center gap-1">
                            <Box className="w-[12px] h-[1px] bg-amber-600" style={{ borderTop: '1px dashed', borderTop: '1px dashed' }} />
                            <Typography as="span" className="text-xs text-gray-500">Warning</Typography>
                        </Box>
                    )}

                    {alertThresholds?.critical && (
                        <Box className="flex items-center gap-1">
                            <Box className="w-[12px] h-[1px] bg-red-600" />
                            <Typography as="span" className="text-xs text-gray-500">Critical</Typography>
                        </Box>
                    )}

                    {showRegressions && (
                        <Box className="flex items-center gap-1">
                            <Box className="rounded-full w-[8px] h-[8px] bg-red-600 border-[2px_solid]" style={{ borderColor: 'common.white', borderTop: '1px dashed' }} />
                            <Typography as="span" className="text-xs text-gray-500">Regression</Typography>
                        </Box>
                    )}
                </Box>

                {/* Trend Analysis */}
                {trendAnalysis && (
                    <Box className="mt-4 p-2 rounded bg-gray-50 dark:bg-gray-950">
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            Trend Analysis: {trendAnalysis.direction} trajectory with {(trendAnalysis.confidence * 100).toFixed(0)}% confidence
                            {trendAnalysis.regressionPoints && trendAnalysis.regressionPoints.length > 0 &&
                                ` • ${trendAnalysis.regressionPoints.length} regression point${trendAnalysis.regressionPoints.length !== 1 ? 's' : ''} detected`
                            }
                        </Typography>
                    </Box>
                )}
            </CardContent>
        </Card>
    );
};

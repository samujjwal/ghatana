/**
 * Chart data utilities for timeline, reports, and compliance visualization.
 *
 * <p><b>Purpose</b><br>
 * Provides mock data generators and formatters for various chart types
 * (timeline, trends, gauge, distributions). Ready for Recharts/Chart.js integration.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { chartDataService } from '@/services/chartDataService';
 *
 * // Timeline data
 * const timeline = chartDataService.generateTimeline(100, '1d');
 *
 * // KPI trends
 * const trends = chartDataService.generateTrendData('deployments', 30);
 *
 * // Compliance gauge
 * const compliance = chartDataService.generateComplianceData();
 * }</pre>
 *
 * @doc.type service
 * @doc.purpose Chart data generation and formatting
 * @doc.layer product
 * @doc.pattern Service
 */

/**
 * Timeline data point for charts.
 *
 * @doc.type type
 * @doc.purpose Single point in timeline
 * @doc.layer product
 * @doc.pattern Type Definition
 */
export interface TimelineDataPoint {
    timestamp: string;
    date: string;
    time: string;
    type: 'deployment' | 'test' | 'feature' | 'incident' | 'config';
    severity?: 'critical' | 'warning' | 'info';
    title: string;
    description?: string;
    value?: number;
}

/**
 * Trend data for KPI charts.
 *
 * @doc.type type
 * @doc.purpose KPI trend data structure
 * @doc.layer product
 * @doc.pattern Type Definition
 */
export interface TrendDataPoint {
    date: string;
    value: number;
    target?: number;
    min?: number;
    max?: number;
}

/**
 * Compliance gauge data.
 *
 * @doc.type type
 * @doc.purpose Compliance status for gauge charts
 * @doc.layer product
 * @doc.pattern Type Definition
 */
export interface ComplianceData {
    total: number;
    compliant: number;
    warning: number;
    critical: number;
    complianceRate: number;
    categories: Array<{
        name: string;
        compliance: number;
        status: 'compliant' | 'warning' | 'critical';
    }>;
}

/**
 * Chart data service with mock data generators.
 *
 * @doc.type object
 * @doc.purpose Chart data utilities
 * @doc.layer product
 * @doc.pattern Service
 */
export const chartDataService = {
    /**
     * Generates mock timeline data with various event types.
     *
     * @param count - Number of events to generate
     * @param timeRange - Time range ('1d', '7d', '30d', '90d')
     * @returns Array of timeline data points
     */
    generateTimeline: (count: number = 50, timeRange: string = '7d'): TimelineDataPoint[] => {
        const eventTypes: Array<'deployment' | 'test' | 'feature' | 'incident' | 'config'> = [
            'deployment',
            'test',
            'feature',
            'incident',
            'config',
        ];
        const severities: Array<'critical' | 'warning' | 'info'> = [
            'critical',
            'warning',
            'info',
        ];
        const descriptions = {
            deployment: 'App deployed to production',
            test: 'Test suite execution completed',
            feature: 'New feature released',
            incident: 'Production incident detected',
            config: 'Configuration updated',
        };

        const now = new Date();
        const rangeMs = {
            '1d': 24 * 60 * 60 * 1000,
            '7d': 7 * 24 * 60 * 60 * 1000,
            '30d': 30 * 24 * 60 * 60 * 1000,
            '90d': 90 * 24 * 60 * 60 * 1000,
        }[timeRange] || 7 * 24 * 60 * 60 * 1000;

        const events: TimelineDataPoint[] = [];
        for (let i = 0; i < count; i++) {
            const randomTime = now.getTime() - Math.random() * rangeMs;
            const timestamp = new Date(randomTime);
            const type = eventTypes[Math.floor(Math.random() * eventTypes.length)];
            const isCritical = type === 'incident' || Math.random() < 0.1;

            events.push({
                timestamp: timestamp.toISOString(),
                date: timestamp.toLocaleDateString(),
                time: timestamp.toLocaleTimeString(),
                type,
                severity: isCritical ? 'critical' : Math.random() < 0.3 ? 'warning' : 'info',
                title: `${type.charAt(0).toUpperCase() + type.slice(1)} #${Math.floor(Math.random() * 10000)}`,
                description: descriptions[type as keyof typeof descriptions],
                value: Math.floor(Math.random() * 100),
            });
        }

        return events.sort(
            (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
        );
    },

    /**
     * Generates KPI trend data for line/area charts.
     *
     * @param metricName - Name of metric
     * @param days - Number of days to generate
     * @returns Array of trend data points
     */
    generateTrendData: (metricName: string, days: number = 30): TrendDataPoint[] => {
        const data: TrendDataPoint[] = [];
        const now = new Date();

        // Base values by metric
        const baseValues = {
            deployments: { base: 150, target: 150, variance: 20 },
            'lead-time': { base: 3.2, target: 2, variance: 0.5 },
            'change-failure-rate': { base: 3.2, target: 5, variance: 1 },
            'mttr': { base: 45, target: 30, variance: 10 },
            'throughput': { base: 1200, target: 1500, variance: 200 },
        };

        const config = baseValues[metricName as keyof typeof baseValues] || {
            base: 100,
            target: 100,
            variance: 10,
        };

        for (let i = days; i >= 0; i--) {
            const date = new Date(now);
            date.setDate(date.getDate() - i);

            const noise = (Math.random() - 0.5) * 2 * config.variance;
            const trend = (days - i) * (config.target - config.base) / (days || 1);

            data.push({
                date: date.toLocaleDateString('en-US', {
                    month: 'short',
                    day: 'numeric',
                }),
                value: Math.round((config.base + trend + noise) * 10) / 10,
                target: config.target,
                min: config.base - config.variance,
                max: config.base + config.variance,
            });
        }

        return data;
    },

    /**
     * Generates compliance gauge data.
     *
     * @returns Compliance status data
     */
    generateComplianceData: (): ComplianceData => {
        const categories = [
            { name: 'HIPAA', compliance: 98 },
            { name: 'SOC 2', compliance: 95 },
            { name: 'GDPR', compliance: 100 },
            { name: 'PCI-DSS', compliance: 92 },
            { name: 'ISO 27001', compliance: 97 },
        ];

        const total = categories.length;
        const compliant = categories.filter((c) => c.compliance >= 95).length;
        const warning = categories.filter((c) => c.compliance >= 90 && c.compliance < 95).length;
        const critical = total - compliant - warning;
        const complianceRate =
            Math.round(
                (categories.reduce((sum, c) => sum + c.compliance, 0) / total) * 100
            ) / 100;

        return {
            total,
            compliant,
            warning,
            critical,
            complianceRate,
            categories: categories.map((c) => ({
                name: c.name,
                compliance: c.compliance,
                status:
                    c.compliance >= 95
                        ? 'compliant'
                        : c.compliance >= 90
                          ? 'warning'
                          : 'critical',
            })),
        };
    },

    /**
     * Generates distribution data for histogram.
     *
     * @param bins - Number of bins
     * @param mean - Mean value
     * @param stdDev - Standard deviation
     * @returns Array of bin data
     */
    generateDistribution: (bins: number = 10, mean: number = 50, stdDev: number = 15) => {
        const data: Array<{ bin: string; count: number }> = [];
        const binSize = (mean + stdDev * 4) / bins;

        for (let i = 0; i < bins; i++) {
            const binStart = i * binSize;
            const binEnd = (i + 1) * binSize;
            const binMid = (binStart + binEnd) / 2;

            // Normal distribution approximation
            const count = Math.round(
                100 *
                    Math.exp(-Math.pow((binMid - mean) / stdDev, 2) / 2) /
                    (stdDev * Math.sqrt(2 * Math.PI))
            );

            data.push({
                bin: `${Math.round(binStart)}-${Math.round(binEnd)}`,
                count: Math.max(0, count),
            });
        }

        return data;
    },

    /**
     * Generates status breakdown data (pie chart).
     *
     * @returns Array of status categories with counts
     */
    generateStatusBreakdown: () => {
        return [
            { name: 'Healthy', value: 120, color: '#10b981' },
            { name: 'Warning', value: 45, color: '#f59e0b' },
            { name: 'Critical', value: 12, color: '#ef4444' },
            { name: 'Maintenance', value: 8, color: '#6b7280' },
        ];
    },

    /**
     * Formats number for chart display.
     *
     * @param value - Number to format
     * @param type - Format type ('decimal', 'percent', 'duration', 'count')
     * @returns Formatted string
     */
    formatChartValue: (
        value: number,
        type: 'decimal' | 'percent' | 'duration' | 'count' = 'decimal'
    ): string => {
        switch (type) {
            case 'percent':
                return `${Math.round(value * 100) / 100}%`;
            case 'duration':
                const hours = Math.floor(value / 60);
                const mins = value % 60;
                return hours > 0 ? `${hours}h ${mins}m` : `${mins}m`;
            case 'count':
                return value.toLocaleString();
            case 'decimal':
            default:
                return (Math.round(value * 100) / 100).toString();
        }
    },
};

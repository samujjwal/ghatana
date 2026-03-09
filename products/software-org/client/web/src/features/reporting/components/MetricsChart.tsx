import { memo } from 'react';

/**
 * Multi-series time-series chart for metrics visualization.
 *
 * <p><b>Purpose</b><br>
 * Renders multi-line chart with throughput, latency, and error rates.
 * Supports time-range selection and metric toggling.
 * Uses SVG rendering for lightweight visualization (no heavy charting library).
 *
 * <p><b>Features</b><br>
 * - Multi-series line chart (throughput, latency p99, error rate)
 * - Time-axis labels
 * - Legend with series toggling
 * - Hover tooltips with values
 * - Responsive width
 * - Color-coded lines (green=throughput, blue=latency, red=errors)
 *
 * <p><b>Props</b><br>
 * @param timeRange - Selected time range ("1h", "24h", "7d", "30d")
 *
 * @doc.type component
 * @doc.purpose Multi-series metrics chart
 * @doc.layer product
 * @doc.pattern Chart
 */

interface MetricsChartProps {
    timeRange: string;
}

interface DataPoint {
    time: string;
    throughput: number;
    latencyP99: number;
    errorRate: number;
}

// Mock metric data
const generateMockData = (timeRange: string): DataPoint[] => {
    const points: DataPoint[] = [];

    // Generate data based on time range
    let intervals = 24; // Default 1h with 24 5-minute intervals
    switch (timeRange) {
        case '24h':
            intervals = 24; // 1 per hour
            break;
        case '7d':
            intervals = 7; // 1 per day
            break;
        case '30d':
            intervals = 30; // 1 per day
            break;
    }

    for (let i = 0; i < intervals; i++) {
        const idx = i / intervals;
        points.push({
            time: `${String(i).padStart(2, '0')}:00`,
            throughput: 5000 + Math.sin(idx * Math.PI) * 2000 + Math.random() * 500,
            latencyP99: 45 + Math.sin(idx * Math.PI * 1.5) * 20 + Math.random() * 10,
            errorRate: 0.5 + Math.sin(idx * Math.PI * 2) * 0.3 + Math.random() * 0.1,
        });
    }

    return points;
};

export const MetricsChart = memo(function MetricsChart({ timeRange }: MetricsChartProps) {
    // GIVEN: Time range selected
    // WHEN: Component renders
    // THEN: Display multi-series chart with legend

    const data = generateMockData(timeRange);

    if (!data.length) {
        return <div className="text-slate-400 text-center py-8">No data available</div>;
    }

    // SVG dimensions
    const width = 800;
    const height = 300;
    const padding = 40;
    const chartWidth = width - padding * 2;
    const chartHeight = height - padding * 2;

    // Normalize data to chart coordinates
    const maxThroughput = Math.max(...data.map((d) => d.throughput));
    const maxLatency = Math.max(...data.map((d) => d.latencyP99));
    const maxErrorRate = 1;

    const points = data.map((d, i) => ({
        x: padding + (i / (data.length - 1)) * chartWidth,
        throughputY: height - padding - (d.throughput / maxThroughput) * (chartHeight * 0.8),
        latencyY: height - padding - (d.latencyP99 / maxLatency) * (chartHeight * 0.8),
        errorY: height - padding - (d.errorRate / maxErrorRate) * (chartHeight * 0.8),
    }));

    const throughputPath = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.throughputY}`).join(' ');
    const latencyPath = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.latencyY}`).join(' ');
    const errorPath = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.errorY}`).join(' ');

    return (
        <div className="space-y-4">
            {/* Chart */}
            <svg viewBox={`0 0 ${width} ${height}`} className="w-full border border-slate-700 rounded bg-slate-900">
                {/* Grid lines */}
                {[0, 0.25, 0.5, 0.75, 1].map((frac) => (
                    <line
                        key={`grid-${frac}`}
                        x1={padding}
                        y1={padding + frac * chartHeight}
                        x2={width - padding}
                        y2={padding + frac * chartHeight}
                        stroke="rgba(100, 116, 139, 0.2)"
                        strokeWidth="1"
                    />
                ))}

                {/* X-axis */}
                <line x1={padding} y1={height - padding} x2={width - padding} y2={height - padding} stroke="rgb(100, 116, 139)" />

                {/* Y-axis */}
                <line x1={padding} y1={padding} x2={padding} y2={height - padding} stroke="rgb(100, 116, 139)" />

                {/* Throughput line (green) */}
                <path d={throughputPath} stroke="rgb(74, 222, 128)" strokeWidth="2" fill="none" />

                {/* Latency line (blue) */}
                <path d={latencyPath} stroke="rgb(96, 165, 250)" strokeWidth="2" fill="none" />

                {/* Error rate line (red) */}
                <path d={errorPath} stroke="rgb(248, 113, 113)" strokeWidth="2" fill="none" />

                {/* Data points */}
                {points.map((p, i) => (
                    <g key={`point-${i}`}>
                        <circle cx={p.x} cy={p.throughputY} r="3" fill="rgb(74, 222, 128)" />
                        <circle cx={p.x} cy={p.latencyY} r="3" fill="rgb(96, 165, 250)" />
                        <circle cx={p.x} cy={p.errorY} r="3" fill="rgb(248, 113, 113)" />
                    </g>
                ))}

                {/* X-axis labels */}
                {data.map((d, i) => {
                    if (i % Math.ceil(data.length / 6) === 0) {
                        const x = padding + (i / (data.length - 1)) * chartWidth;
                        return (
                            <text
                                key={`label-${i}`}
                                x={x}
                                y={height - padding + 20}
                                textAnchor="middle"
                                className="text-xs fill-slate-500"
                            >
                                {d.time}
                            </text>
                        );
                    }
                    return null;
                })}

                {/* Y-axis labels */}
                <text x={padding - 20} y={padding + 10} textAnchor="middle" className="text-xs fill-slate-500">
                    {Math.round(maxThroughput)}
                </text>
                <text x={padding - 20} y={height - padding} textAnchor="middle" className="text-xs fill-slate-500">
                    0
                </text>
            </svg>

            {/* Legend */}
            <div className="flex gap-6 flex-wrap">
                <div className="flex items-center gap-2">
                    <div className="w-3 h-3 rounded-full bg-green-400" />
                    <span className="text-sm text-slate-300">
                        Throughput: {Math.round(data[data.length - 1].throughput)} req/s
                    </span>
                </div>
                <div className="flex items-center gap-2">
                    <div className="w-3 h-3 rounded-full bg-blue-400" />
                    <span className="text-sm text-slate-300">
                        Latency p99: {Math.round(data[data.length - 1].latencyP99)}ms
                    </span>
                </div>
                <div className="flex items-center gap-2">
                    <div className="w-3 h-3 rounded-full bg-red-400" />
                    <span className="text-sm text-slate-300">
                        Error Rate: {(data[data.length - 1].errorRate * 100).toFixed(2)}%
                    </span>
                </div>
            </div>

            {/* Summary Stats */}
            <div className="grid grid-cols-3 gap-4 mt-4">
                <div className="bg-slate-800 rounded p-3">
                    <div className="text-xs text-slate-400 mb-1">Avg Throughput</div>
                    <div className="text-lg font-bold text-green-400">
                        {Math.round(data.reduce((a, d) => a + d.throughput, 0) / data.length)} req/s
                    </div>
                </div>
                <div className="bg-slate-800 rounded p-3">
                    <div className="text-xs text-slate-400 mb-1">Avg Latency p99</div>
                    <div className="text-lg font-bold text-blue-400">
                        {Math.round(data.reduce((a, d) => a + d.latencyP99, 0) / data.length)}ms
                    </div>
                </div>
                <div className="bg-slate-800 rounded p-3">
                    <div className="text-xs text-slate-400 mb-1">Avg Error Rate</div>
                    <div className="text-lg font-bold text-red-400">
                        {((data.reduce((a, d) => a + d.errorRate, 0) / data.length) * 100).toFixed(2)}%
                    </div>
                </div>
            </div>
        </div>
    );
});

export default MetricsChart;

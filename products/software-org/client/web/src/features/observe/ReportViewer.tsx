import { useParams, useNavigate } from "react-router";
import { ArrowLeft, Download, FileText, TrendingUp, TrendingDown, Calendar, BarChart3 } from 'lucide-react';
import { Badge } from "@/components/ui";

/**
 * Report Viewer
 *
 * <p><b>Purpose</b><br>
 * Display generated report details with metrics, charts, and export options.
 * Provides detailed insights into reliability, change management, and incident data.
 *
 * <p><b>Features</b><br>
 * - Report metadata (type, period, scope)
 * - Key metrics summary
 * - Time series visualizations
 * - Export capabilities (PDF, CSV)
 *
 * @doc.type component
 * @doc.purpose Report detail viewer
 * @doc.layer product
 * @doc.pattern Detail
 */
export function ReportViewer() {
    const { reportId } = useParams();
    const navigate = useNavigate();

    // Mock report data (in production, use useReport(reportId) hook)
    const report = {
        id: reportId || '1',
        name: 'Weekly Reliability Report',
        type: 'reliability',
        scope: 'tenant',
        period: {
            start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
            end: new Date().toISOString(),
        },
        metrics: {
            availability: { value: 99.97, target: 99.95, unit: '%', trend: 'up' },
            mttr: { value: 23, target: 30, unit: 'minutes', trend: 'down' },
            incidentCount: { value: 12, target: 15, unit: 'count', trend: 'down' },
            deploymentFrequency: { value: 45, target: 40, unit: 'per week', trend: 'up' },
        },
        charts: [
            {
                id: 'availability',
                title: 'Availability Over Time',
                type: 'line',
                data: Array.from({ length: 7 }, (_, i) => ({
                    date: new Date(Date.now() - (6 - i) * 24 * 60 * 60 * 1000).toISOString(),
                    value: 99.9 + Math.random() * 0.1,
                })),
            },
            {
                id: 'mttr',
                title: 'Mean Time to Recovery',
                type: 'line',
                data: Array.from({ length: 7 }, (_, i) => ({
                    date: new Date(Date.now() - (6 - i) * 24 * 60 * 60 * 1000).toISOString(),
                    value: 20 + Math.random() * 10,
                })),
            },
        ],
        summary: `This weekly reliability report covers the period from ${new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toLocaleDateString()} to ${new Date().toLocaleDateString()}. Overall system availability exceeded target at 99.97%, with 12 incidents recorded. Mean time to recovery improved to 23 minutes, well within target thresholds. Deployment frequency increased to 45 per week, indicating healthy development velocity.`,
        createdAt: new Date().toISOString(),
    };

    const formatDate = (dateStr: string) => {
        return new Date(dateStr).toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
        });
    };

    const renderChart = (chart: typeof report.charts[0]) => {
        const maxValue = Math.max(...chart.data.map(p => p.value));
        const minValue = Math.min(...chart.data.map(p => p.value));
        const valueRange = maxValue - minValue || 1;

        return (
            <div key={chart.id} className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4 flex items-center gap-2">
                    <BarChart3 className="h-5 w-5 text-blue-500" />
                    {chart.title}
                </h3>
                <div className="h-64">
                    <svg viewBox="0 0 800 300" className="w-full h-full" preserveAspectRatio="none">
                        {/* Grid lines */}
                        <line x1="0" y1="0" x2="800" y2="0" stroke="currentColor" strokeWidth="1" className="text-slate-200 dark:text-slate-700" opacity="0.3" />
                        <line x1="0" y1="150" x2="800" y2="150" stroke="currentColor" strokeWidth="1" className="text-slate-200 dark:text-slate-700" opacity="0.3" />
                        <line x1="0" y1="300" x2="800" y2="300" stroke="currentColor" strokeWidth="1" className="text-slate-200 dark:text-slate-700" opacity="0.3" />

                        {/* Y-axis labels */}
                        <text x="5" y="15" fontSize="12" fill="currentColor" className="text-slate-600 dark:text-neutral-400">
                            {maxValue.toFixed(2)}
                        </text>
                        <text x="5" y="155" fontSize="12" fill="currentColor" className="text-slate-600 dark:text-neutral-400">
                            {((maxValue + minValue) / 2).toFixed(2)}
                        </text>
                        <text x="5" y="295" fontSize="12" fill="currentColor" className="text-slate-600 dark:text-neutral-400">
                            {minValue.toFixed(2)}
                        </text>

                        {/* Chart line */}
                        <polyline
                            points={chart.data.map((point, i) => {
                                const x = (i / (chart.data.length - 1)) * 800;
                                const y = 300 - ((point.value - minValue) / valueRange) * 300;
                                return `${x},${y}`;
                            }).join(' ')}
                            fill="none"
                            stroke="#3b82f6"
                            strokeWidth="2"
                        />

                        {/* Data points */}
                        {chart.data.map((point, i) => {
                            const x = (i / (chart.data.length - 1)) * 800;
                            const y = 300 - ((point.value - minValue) / valueRange) * 300;
                            return (
                                <circle
                                    key={i}
                                    cx={x}
                                    cy={y}
                                    r="4"
                                    fill="#3b82f6"
                                />
                            );
                        })}
                    </svg>

                    {/* X-axis labels */}
                    <div className="flex justify-between mt-2 text-xs text-slate-600 dark:text-neutral-400">
                        <span>{formatDate(chart.data[0].date)}</span>
                        <span>{formatDate(chart.data[Math.floor(chart.data.length / 2)].date)}</span>
                        <span>{formatDate(chart.data[chart.data.length - 1].date)}</span>
                    </div>
                </div>
            </div>
        );
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-4">
                    <button
                        onClick={() => navigate('/observe/reports')}
                        className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors"
                    >
                        <ArrowLeft className="h-5 w-5 text-slate-600 dark:text-neutral-400" />
                    </button>
                    <div>
                        <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">{report.name}</h1>
                        <p className="text-slate-600 dark:text-neutral-400 mt-1">
                            Generated on {formatDate(report.createdAt)}
                        </p>
                    </div>
                </div>
                <div className="flex items-center gap-3">
                    <button className="inline-flex items-center gap-2 px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors">
                        <Download className="h-4 w-4" />
                        Export PDF
                    </button>
                    <button className="inline-flex items-center gap-2 px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors">
                        <Download className="h-4 w-4" />
                        Export CSV
                    </button>
                </div>
            </div>

            {/* Metadata */}
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
                    <div>
                        <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Report Type</div>
                        <Badge variant="neutral">
                            {report.type.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')}
                        </Badge>
                    </div>
                    <div>
                        <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Scope</div>
                        <div className="font-medium text-slate-900 dark:text-neutral-100 capitalize">{report.scope}</div>
                    </div>
                    <div>
                        <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1 flex items-center gap-1">
                            <Calendar className="h-4 w-4" />
                            Period
                        </div>
                        <div className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                            {formatDate(report.period.start)} - {formatDate(report.period.end)}
                        </div>
                    </div>
                    <div>
                        <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Generated</div>
                        <div className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                            {formatDate(report.createdAt)}
                        </div>
                    </div>
                </div>
            </div>

            {/* Key Metrics */}
            <div>
                <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100 mb-4 flex items-center gap-2">
                    <FileText className="h-5 w-5" />
                    Key Metrics
                </h2>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                    {Object.entries(report.metrics).map(([key, metric]) => (
                        <div key={key} className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
                            <div className="flex items-center justify-between mb-2">
                                <div className="text-sm text-slate-600 dark:text-neutral-400 capitalize">
                                    {key.replace(/([A-Z])/g, ' $1').trim()}
                                </div>
                                {metric.trend === 'up' ? (
                                    <TrendingUp className="h-4 w-4 text-green-500" />
                                ) : (
                                    <TrendingDown className="h-4 w-4 text-green-500" />
                                )}
                            </div>
                            <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                {metric.value}
                                <span className="text-sm text-slate-500 dark:text-neutral-500 ml-1">{metric.unit}</span>
                            </div>
                            <div className="text-xs text-slate-500 dark:text-neutral-500 mt-1">
                                Target: {metric.target} {metric.unit}
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            {/* Charts */}
            <div>
                <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                    Visualizations
                </h2>
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    {report.charts.map(chart => renderChart(chart))}
                </div>
            </div>

            {/* Summary */}
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                    Executive Summary
                </h2>
                <p className="text-slate-600 dark:text-neutral-400 leading-relaxed">
                    {report.summary}
                </p>
            </div>
        </div>
    );
}

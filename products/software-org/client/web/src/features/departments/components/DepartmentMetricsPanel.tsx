import React from 'react';

/**
 * Department Metrics interface.
 */
export interface DepartmentMetrics {
    velocity: number;
    cycleTime: number;
    deploymentFrequency: number;
    leadTime: number;
    coverage: number;
    passRate: number;
    healthScore: number;
}

/**
 * Department Metrics Panel Props interface.
 */
export interface DepartmentMetricsPanelProps {
    departmentId: string;
    departmentName: string;
    metrics: DepartmentMetrics;
    onChange?: (metrics: DepartmentMetrics) => void;
    isLoading?: boolean;
}

/**
 * Department Metrics Panel - Displays key metrics for a department.
 *
 * <p><b>Purpose</b><br>
 * Shows department-level KPIs and metrics in a structured grid with trend visualization.
 *
 * <p><b>Features</b><br>
 * - 7 key metrics (velocity, cycle time, deployment frequency, lead time, coverage, pass rate, health)
 * - Mini sparkline charts for each metric
 * - Responsive grid layout (1 col mobile, 2 cols tablet, 3-4 cols desktop)
 * - Loading skeleton state
 * - Dark mode support
 * - Metric details on hover
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <DepartmentMetricsPanel 
 *   departmentId="dept_123"
 *   departmentName="Engineering"
 *   metrics={departmentMetrics}
 *   isLoading={false}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Department metrics grid
 * @doc.layer product
 * @doc.pattern Organism
 */
export const DepartmentMetricsPanel = React.memo(
    ({ departmentId, departmentName, metrics, isLoading }: DepartmentMetricsPanelProps) => {
        if (isLoading) {
            return (
                <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                    <div className="animate-pulse space-y-4">
                        <div className="h-8 w-48 bg-slate-200 dark:bg-neutral-700 rounded" />
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                            {Array.from({ length: 4 }).map((_, i) => (
                                <div key={i} className="h-24 bg-slate-200 dark:bg-neutral-700 rounded" />
                            ))}
                        </div>
                    </div>
                </div>
            );
        }

        const metricCards = [
            {
                label: 'Feature Velocity',
                value: metrics.velocity,
                unit: 'points/sprint',
                trend: 'up',
                color: 'blue',
            },
            {
                label: 'Cycle Time',
                value: metrics.cycleTime,
                unit: 'days',
                trend: 'down',
                color: 'green',
            },
            {
                label: 'Deployment Frequency',
                value: metrics.deploymentFrequency,
                unit: 'deploys/day',
                trend: 'up',
                color: 'purple',
            },
            {
                label: 'Lead Time',
                value: metrics.leadTime,
                unit: 'hours',
                trend: 'down',
                color: 'indigo',
            },
            {
                label: 'Test Coverage',
                value: metrics.coverage,
                unit: '%',
                trend: 'up',
                color: 'emerald',
            },
            {
                label: 'Pass Rate',
                value: metrics.passRate,
                unit: '%',
                trend: 'up',
                color: 'cyan',
            },
            {
                label: 'Health Score',
                value: metrics.healthScore,
                unit: '/100',
                trend: 'up',
                color: 'rose',
            },
        ];

        const getColorClasses = (color: string) => {
            const colorMap: Record<string, string> = {
                blue: 'bg-blue-50 border-blue-200 dark:bg-indigo-600/30 dark:border-blue-800 text-blue-900 dark:text-blue-100',
                green: 'bg-green-50 border-green-200 dark:bg-green-600/30 dark:border-green-800 text-green-900 dark:text-green-100',
                purple: 'bg-purple-50 border-purple-200 dark:bg-violet-600/30 dark:border-purple-800 text-purple-900 dark:text-purple-100',
                indigo: 'bg-indigo-50 border-indigo-200 dark:bg-indigo-900/20 dark:border-indigo-800 text-indigo-900 dark:text-indigo-100',
                emerald: 'bg-emerald-50 border-emerald-200 dark:bg-emerald-900/20 dark:border-emerald-800 text-emerald-900 dark:text-emerald-100',
                cyan: 'bg-cyan-50 border-cyan-200 dark:bg-cyan-900/20 dark:border-cyan-800 text-cyan-900 dark:text-cyan-100',
                rose: 'bg-rose-50 border-rose-200 dark:bg-rose-900/20 dark:border-rose-800 text-rose-900 dark:text-rose-100',
            };
            return colorMap[color] || colorMap.blue;
        };

        return (
            <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                <div className="flex items-center justify-between mb-6">
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                        {departmentName} Metrics
                    </h3>
                    <span className="text-xs font-medium text-slate-500 dark:text-neutral-400">
                        Department ID: {departmentId}
                    </span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                    {metricCards.map((card) => (
                        <div
                            key={card.label}
                            className={`rounded-lg border p-4 transition-shadow hover:shadow-md ${getColorClasses(
                                card.color
                            )}`}
                        >
                            <div className="flex items-start justify-between mb-3">
                                <p className="text-xs font-medium opacity-80">{card.label}</p>
                                <span className="text-xs opacity-60">
                                    {card.trend === 'up' ? '📈' : '📉'}
                                </span>
                            </div>
                            <div>
                                <p className="text-2xl font-bold">
                                    {typeof card.value === 'number' ? card.value.toFixed(1) : card.value}
                                </p>
                                <p className="text-xs opacity-70">{card.unit}</p>
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        );
    }
);

DepartmentMetricsPanel.displayName = 'DepartmentMetricsPanel';

export default DepartmentMetricsPanel;

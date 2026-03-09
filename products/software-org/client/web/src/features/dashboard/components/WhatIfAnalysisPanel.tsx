import { memo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';

/**
 * What-if analysis panel for scenario simulation.
 *
 * <p><b>Purpose</b><br>
 * Allows users to adjust key metrics and see projected impact on dashboards.
 * Enables scenario planning and forecasting without touching production data.
 *
 * <p><b>Features</b><br>
 * - Adjustable parameter sliders
 * - Real-time simulation results
 * - Scenario comparison (baseline vs what-if)
 * - Export scenario results
 * - Scenario history
 *
 * <p><b>Interactions</b><br>
 * - Drag sliders to adjust parameters
 * - View predicted changes in real-time
 * - Compare against baseline
 * - Save scenarios for later reference
 *
 * @doc.type component
 * @doc.purpose Scenario simulation and forecasting
 * @doc.layer product
 * @doc.pattern Organism
 */

interface ScenarioParameter {
    name: string;
    label: string;
    value: number;
    min: number;
    max: number;
    step: number;
    unit: string;
}

interface MetricsData {
    reliabilityScore: number;
    customerSatisfaction: number;
    incidentRate: number;
    productivityGain: number;
}

interface ScenarioResult {
    baseline: MetricsData;
    projected: MetricsData;
    change: MetricsData;
    confidence: number;
}

interface WhatIfAnalysisPanelProps {
    onScenarioSelect?: (scenario: ScenarioResult) => void;
}

export const WhatIfAnalysisPanel = memo(function WhatIfAnalysisPanel({
    onScenarioSelect,
}: WhatIfAnalysisPanelProps) {
    const [isExpanded, setIsExpanded] = useState(false);
    const [parameters, setParameters] = useState<ScenarioParameter[]>([
        {
            name: 'deploymentFrequency',
            label: 'Deployment Frequency',
            value: 100,
            min: 50,
            max: 300,
            step: 10,
            unit: 'deployments/week',
        },
        {
            name: 'mttr',
            label: 'Mean Time To Recover',
            value: 45,
            min: 10,
            max: 120,
            step: 5,
            unit: 'minutes',
        },
        {
            name: 'changeFailureRate',
            label: 'Change Failure Rate',
            value: 15,
            min: 0,
            max: 50,
            step: 1,
            unit: '%',
        },
        {
            name: 'leadTime',
            label: 'Lead Time for Changes',
            value: 24,
            min: 4,
            max: 168,
            step: 2,
            unit: 'hours',
        },
    ]);

    // Simulate what-if analysis
    const { data: result, isLoading } = useQuery({
        queryKey: ['whatIfAnalysis', parameters],
        queryFn: async () => {
            // Simulate API delay
            await new Promise((resolve) => setTimeout(resolve, 500));

            // TODO: Replace with actual API endpoint
            // POST /api/v1/scenarios/simulate
            // Body: { parameters: Record<string, number> }

            const baseline = {
                reliabilityScore: 92,
                customerSatisfaction: 88,
                incidentRate: 2.3,
                productivityGain: 45,
            };

            // Simulate projected values based on parameters
            const deploymentMult = parameters.find((p) => p.name === 'deploymentFrequency')!.value / 100;
            const mttrDiv = parameters.find((p) => p.name === 'mttr')!.value / 45;
            const cfrDiv = parameters.find((p) => p.name === 'changeFailureRate')!.value / 15;

            const projected = {
                reliabilityScore: Math.round(baseline.reliabilityScore * deploymentMult * (1 / cfrDiv)),
                customerSatisfaction: Math.round(baseline.customerSatisfaction * (1 / mttrDiv)),
                incidentRate: Math.round(baseline.incidentRate * cfrDiv * 10) / 10,
                productivityGain: Math.round(baseline.productivityGain * deploymentMult * 0.8),
            };

            const pctChange = (base: number, proj: number) => Math.round(((proj - base) / base) * 100);

            return {
                baseline,
                projected,
                change: {
                    reliabilityScore: pctChange(baseline.reliabilityScore, projected.reliabilityScore),
                    customerSatisfaction: pctChange(baseline.customerSatisfaction, projected.customerSatisfaction),
                    incidentRate: pctChange(baseline.incidentRate, projected.incidentRate),
                    productivityGain: pctChange(baseline.productivityGain, projected.productivityGain),
                },
                confidence: 0.82,
            };
        },
        enabled: isExpanded,
        staleTime: 30 * 1000,
        gcTime: 60 * 1000,
    });

    const handleParameterChange = (name: string, value: number) => {
        setParameters(
            parameters.map((p) => (p.name === name ? { ...p, value } : p))
        );
    };

    const handleExportScenario = () => {
        if (result) {
            onScenarioSelect?.(result);
        }
    };

    return (
        <div className="space-y-3 rounded-lg border border-slate-200 bg-white p-4 dark:border-neutral-600 dark:bg-slate-900">
            {/* Header */}
            <button
                onClick={() => setIsExpanded(!isExpanded)}
                className="flex w-full items-center justify-between hover:opacity-70"
            >
                <div className="flex items-center gap-2">
                    <span className="text-lg">🔮</span>
                    <h3 className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                        What-If Analysis
                    </h3>
                </div>
                <span className="text-slate-500 dark:text-neutral-400">
                    {isExpanded ? '▼' : '▶'}
                </span>
            </button>

            {/* Expandable Content */}
            {isExpanded && (
                <div className="space-y-4 border-t border-slate-200 pt-4 dark:border-neutral-600">
                    {/* Parameter Sliders */}
                    <div className="space-y-3">
                        {parameters.map((param) => (
                            <div key={param.name} className="space-y-1">
                                <div className="flex items-center justify-between">
                                    <label className="text-xs font-medium text-slate-700 dark:text-neutral-300">
                                        {param.label}
                                    </label>
                                    <span className="text-xs font-semibold text-slate-900 dark:text-neutral-100">
                                        {param.value} {param.unit}
                                    </span>
                                </div>
                                <input
                                    type="range"
                                    min={param.min}
                                    max={param.max}
                                    step={param.step}
                                    value={param.value}
                                    onChange={(e) =>
                                        handleParameterChange(param.name, Number(e.target.value))
                                    }
                                    className="w-full"
                                />
                                <div className="flex justify-between text-xs text-slate-500 dark:text-neutral-400">
                                    <span>{param.min}</span>
                                    <span>{param.max}</span>
                                </div>
                            </div>
                        ))}
                    </div>

                    {/* Results */}
                    {isLoading && (
                        <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-neutral-400">
                            <span className="inline-block animate-spin">⏳</span>
                            Simulating scenario...
                        </div>
                    )}

                    {result && !isLoading && (
                        <div className="space-y-3 border-t border-slate-200 pt-3 dark:border-neutral-600">
                            {/* Confidence */}
                            <div className="flex items-center justify-between">
                                <span className="text-xs font-medium text-slate-700 dark:text-neutral-300">
                                    Confidence
                                </span>
                                <div className="flex items-center gap-1">
                                    <div className="h-1.5 w-16 rounded-full bg-slate-200 dark:bg-neutral-700">
                                        <div
                                            className="h-full rounded-full bg-green-500"
                                            style={{ width: `${result.confidence * 100}%` }}
                                        />
                                    </div>
                                    <span className="text-xs text-slate-500 dark:text-neutral-400">
                                        {Math.round(result.confidence * 100)}%
                                    </span>
                                </div>
                            </div>

                            {/* Impact Summary */}
                            <div className="grid grid-cols-2 gap-2">
                                {(Object.entries(result.baseline) as [keyof MetricsData, number][]).map(([key, baseline]) => {
                                    const projected = result.projected[key];
                                    const change = result.change[key];
                                    const isPositive = change >= 0;

                                    return (
                                        <div
                                            key={key}
                                            className="rounded bg-slate-50 p-2 dark:bg-neutral-800"
                                        >
                                            <div className="text-xs font-medium text-slate-700 dark:text-neutral-300">
                                                {key.replace(/([A-Z])/g, ' $1').trim()}
                                            </div>
                                            <div className="mt-1 flex items-baseline gap-1">
                                                <span className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                                                    {projected}
                                                </span>
                                                <span
                                                    className={`text-xs font-medium ${isPositive
                                                        ? 'text-green-600 dark:text-green-400'
                                                        : 'text-red-600 dark:text-rose-400'
                                                        }`}
                                                >
                                                    {isPositive ? '+' : ''}{change}%
                                                </span>
                                            </div>
                                            <div className="mt-1 text-xs text-slate-500 dark:text-neutral-400">
                                                was: {baseline}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>

                            {/* Export Button */}
                            <button
                                onClick={handleExportScenario}
                                className="w-full rounded bg-blue-600 px-3 py-2 text-xs font-medium text-white hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-800"
                            >
                                📊 Save & Compare Scenario
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
});

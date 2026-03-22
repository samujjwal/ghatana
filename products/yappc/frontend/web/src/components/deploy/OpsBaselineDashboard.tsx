/**
 * Ops Baseline Dashboard
 * 
 * Operational baseline metrics dashboard for OBSERVE phase.
 * Shows SLI/SLO tracking, adoption metrics, and system health.
 * 
 * @doc.type component
 * @doc.purpose OBSERVE phase operational monitoring
 * @doc.layer product
 * @doc.pattern Dashboard Component
 */

import React, { useState } from 'react';
import { TrendingUp, TrendingDown, Gauge as Speed, Cpu as Memory, AlertTriangle as Warning, CheckCircle } from 'lucide-react';
import { useLifecycleArtifacts } from '../../services/canvas/lifecycle';
import { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';

export interface OpsBaselineDashboardProps {
    projectId: string;
}

interface Metric {
    name: string;
    current: number;
    target: number;
    unit: string;
    trend: 'up' | 'down' | 'stable';
    status: 'healthy' | 'warning' | 'critical';
}

interface SLO {
    name: string;
    target: number;
    current: number;
    period: string;
    violations: number;
}

export const OpsBaselineDashboard: React.FC<OpsBaselineDashboardProps> = ({ projectId }) => {
    const { artifacts, createArtifact, updateArtifact } = useLifecycleArtifacts(projectId);
    const [activeTab, setActiveTab] = useState<'metrics' | 'slo' | 'adoption' | 'health'>('metrics');

    // Mock baseline metrics (in production, fetch from monitoring system)
    const [metrics] = useState<Metric[]>([
        {
            name: 'Response Time (p95)',
            current: 145,
            target: 200,
            unit: 'ms',
            trend: 'down',
            status: 'healthy',
        },
        {
            name: 'Error Rate',
            current: 0.08,
            target: 0.1,
            unit: '%',
            trend: 'stable',
            status: 'healthy',
        },
        {
            name: 'CPU Usage (avg)',
            current: 42,
            target: 70,
            unit: '%',
            trend: 'up',
            status: 'healthy',
        },
        {
            name: 'Memory Usage (avg)',
            current: 68,
            target: 80,
            unit: '%',
            trend: 'up',
            status: 'warning',
        },
        {
            name: 'Request Throughput',
            current: 1250,
            target: 1000,
            unit: 'req/s',
            trend: 'up',
            status: 'healthy',
        },
    ]);

    const [slos] = useState<SLO[]>([
        {
            name: 'API Availability',
            target: 99.9,
            current: 99.95,
            period: '30 days',
            violations: 0,
        },
        {
            name: 'Response Time SLO',
            target: 95.0,
            current: 97.2,
            period: '30 days',
            violations: 0,
        },
        {
            name: 'Error Budget',
            target: 0.1,
            current: 0.05,
            period: '30 days',
            violations: 0,
        },
    ]);

    const handleSaveBaseline = () => {
        const userId = 'current-user'; // NOTE: Get from auth
        const existingBaseline = artifacts.find(a => a.kind === LifecycleArtifactKind.OPS_BASELINE);

        const baselinePayload = {
            timestamp: new Date().toISOString(),
            metrics: metrics.map(m => ({
                name: m.name,
                current: m.current,
                target: m.target,
                unit: m.unit,
                status: m.status,
            })),
            slos: slos.map(s => ({
                name: s.name,
                target: s.target,
                current: s.current,
                violations: s.violations,
            })),
            systemHealth: 'healthy',
            recommendations: [
                'Monitor memory usage trend',
                'Review SLO compliance weekly',
            ],
        };

        if (existingBaseline) {
            updateArtifact(existingBaseline.id, { payload: baselinePayload }, userId);
        } else {
            createArtifact(LifecycleArtifactKind.OPS_BASELINE, userId);
        }
    };

    const getTrendIcon = (trend: Metric['trend']) => {
        switch (trend) {
            case 'up': return <TrendingUp className="w-4 h-4 text-primary-600" />;
            case 'down': return <TrendingDown className="w-4 h-4 text-primary-600" />;
            default: return <div className="w-4 h-4" />;
        }
    };

    const getStatusColor = (status: Metric['status']) => {
        switch (status) {
            case 'healthy': return 'text-success-color';
            case 'warning': return 'text-warning-color';
            case 'critical': return 'text-error-color';
        }
    };

    return (
        <div className="flex flex-col h-full bg-bg-default">
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-divider bg-bg-paper">
                <div>
                    <h2 className="text-lg font-semibold text-text-primary">Operational Baseline</h2>
                    <p className="text-sm text-text-secondary">OBSERVE Phase - System Health</p>
                </div>
                <button
                    onClick={handleSaveBaseline}
                    className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700 transition-colors"
                >
                    Save Baseline
                </button>
            </div>

            {/* Tabs */}
            <div className="flex gap-1 px-4 py-2 border-b border-divider bg-bg-default">
                {(['metrics', 'slo', 'adoption', 'health'] as const).map((tab) => (
                    <button
                        key={tab}
                        onClick={() => setActiveTab(tab)}
                        className={`px-3 py-1.5 text-sm font-medium rounded-md transition-colors capitalize ${activeTab === tab
                            ? 'bg-primary-50 text-primary-600'
                            : 'text-text-secondary hover:text-text-primary hover:bg-grey-100'
                            }`}
                    >
                        {tab === 'slo' ? 'SLO' : tab}
                    </button>
                ))}
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-4">
                {activeTab === 'metrics' && (
                    <div className="space-y-4">
                        <h3 className="text-sm font-medium text-text-primary">Key Performance Indicators</h3>
                        <div className="grid grid-cols-2 gap-4">
                            {metrics.map((metric) => (
                                <div
                                    key={metric.name}
                                    className="p-4 rounded-lg border border-divider bg-bg-paper"
                                >
                                    <div className="flex items-start justify-between mb-2">
                                        <span className="text-sm font-medium text-text-secondary">{metric.name}</span>
                                        {getTrendIcon(metric.trend)}
                                    </div>
                                    <div className="flex items-baseline gap-2">
                                        <span className={`text-3xl font-bold ${getStatusColor(metric.status)}`}>
                                            {metric.current}
                                        </span>
                                        <span className="text-sm text-text-secondary">{metric.unit}</span>
                                    </div>
                                    <div className="mt-2 text-xs text-text-secondary">
                                        Target: {metric.target} {metric.unit}
                                    </div>
                                    <div className="mt-2 w-full h-2 bg-grey-200 rounded-full overflow-hidden">
                                        <div
                                            className={`h-full ${metric.status === 'healthy' ? 'bg-success-color' :
                                                metric.status === 'warning' ? 'bg-warning-color' :
                                                    'bg-error-color'
                                                }`}
                                            style={{ width: `${Math.min((metric.current / metric.target) * 100, 100)}%` }}
                                        />
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {activeTab === 'slo' && (
                    <div className="space-y-4">
                        <h3 className="text-sm font-medium text-text-primary">Service Level Objectives</h3>
                        <div className="space-y-3">
                            {slos.map((slo) => (
                                <div
                                    key={slo.name}
                                    className="p-4 rounded-lg border border-divider bg-bg-paper"
                                >
                                    <div className="flex items-center justify-between mb-3">
                                        <h4 className="text-sm font-medium text-text-primary">{slo.name}</h4>
                                        {slo.violations === 0 ? (
                                            <CheckCircle className="w-5 h-5 text-success-color" />
                                        ) : (
                                            <Warning className="w-5 h-5 text-warning-color" />
                                        )}
                                    </div>
                                    <div className="grid grid-cols-3 gap-4 text-center">
                                        <div>
                                            <div className="text-2xl font-bold text-text-primary">
                                                {slo.current}%
                                            </div>
                                            <div className="text-xs text-text-secondary">Current</div>
                                        </div>
                                        <div>
                                            <div className="text-2xl font-bold text-text-secondary">
                                                {slo.target}%
                                            </div>
                                            <div className="text-xs text-text-secondary">Target</div>
                                        </div>
                                        <div>
                                            <div className="text-2xl font-bold text-error-color">
                                                {slo.violations}
                                            </div>
                                            <div className="text-xs text-text-secondary">Violations</div>
                                        </div>
                                    </div>
                                    <div className="mt-3 text-xs text-text-secondary text-center">
                                        Period: {slo.period}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {activeTab === 'adoption' && (
                    <div className="space-y-4">
                        <h3 className="text-sm font-medium text-text-primary">User Adoption Metrics</h3>
                        <div className="grid grid-cols-2 gap-4">
                            <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                <div className="text-sm text-text-secondary mb-2">Daily Active Users</div>
                                <div className="text-3xl font-bold text-primary-600">12,458</div>
                                <div className="text-xs text-success-color mt-1">↑ 15.3% vs last week</div>
                            </div>
                            <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                <div className="text-sm text-text-secondary mb-2">Monthly Active Users</div>
                                <div className="text-3xl font-bold text-primary-600">45,892</div>
                                <div className="text-xs text-success-color mt-1">↑ 8.7% vs last month</div>
                            </div>
                            <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                <div className="text-sm text-text-secondary mb-2">User Retention</div>
                                <div className="text-3xl font-bold text-success-color">87.2%</div>
                                <div className="text-xs text-success-color mt-1">↑ 2.1% vs last month</div>
                            </div>
                            <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                <div className="text-sm text-text-secondary mb-2">NPS Score</div>
                                <div className="text-3xl font-bold text-success-color">72</div>
                                <div className="text-xs text-text-secondary mt-1">Promoters: 85%</div>
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === 'health' && (
                    <div className="space-y-4">
                        <h3 className="text-sm font-medium text-text-primary">System Health</h3>
                        <div className="p-6 rounded-lg border border-divider bg-bg-paper text-center">
                            <CheckCircle className="w-16 h-16 text-success-color mx-auto mb-4" />
                            <div className="text-2xl font-bold text-success-color mb-2">All Systems Operational</div>
                            <p className="text-sm text-text-secondary">No critical issues detected</p>
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                            <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                <div className="flex items-center gap-2 mb-2">
                                    <Speed className="w-4 h-4 text-text-secondary" />
                                    <span className="text-sm font-medium text-text-secondary">API Performance</span>
                                </div>
                                <div className="text-2xl font-bold text-success-color">Healthy</div>
                            </div>
                            <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                <div className="flex items-center gap-2 mb-2">
                                    <Memory className="w-4 h-4 text-text-secondary" />
                                    <span className="text-sm font-medium text-text-secondary">Database</span>
                                </div>
                                <div className="text-2xl font-bold text-warning-color">Warning</div>
                                <div className="text-xs text-text-secondary mt-1">Connection pool at 78%</div>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

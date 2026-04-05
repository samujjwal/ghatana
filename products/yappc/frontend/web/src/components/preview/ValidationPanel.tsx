/**
 * Validation Panel
 * 
 * Comprehensive validation dashboard for the VALIDATE phase.
 * Shows behavior simulation, edge cases, accessibility checks, and performance projections.
 * 
 * @doc.type component
 * @doc.purpose VALIDATE phase validation dashboard
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useCallback } from 'react';
import { Play as PlayArrow, Bug as BugReport, Accessibility as Accessible, Gauge as Speed, CheckCircle, AlertTriangle as Warning, AlertCircle as ErrorIcon } from 'lucide-react';
import { useLifecycleArtifacts } from '../../services/canvas/lifecycle';
import { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';

export interface ValidationPanelProps {
    projectId: string;
}

interface ValidationCheck {
    id: string;
    category: 'behavior' | 'accessibility' | 'performance' | 'security';
    name: string;
    status: 'pass' | 'fail' | 'warning' | 'pending';
    message: string;
    severity?: 'critical' | 'high' | 'medium' | 'low';
}

interface SimulationScenario {
    id: string;
    name: string;
    description: string;
    status: 'pending' | 'running' | 'pass' | 'fail';
    steps: number;
    completedSteps: number;
}

export const ValidationPanel: React.FC<ValidationPanelProps> = ({
    projectId,
}) => {
    const { artifacts, createArtifact, updateArtifact } = useLifecycleArtifacts(projectId);
    const [activeTab, setActiveTab] = useState<'overview' | 'simulation' | 'accessibility' | 'performance'>('overview');
    const [running, setRunning] = useState(false);

    // Mock validation checks (in production, fetch from backend)
    const [validationChecks] = useState<ValidationCheck[]>([
        {
            id: 'v1',
            category: 'behavior',
            name: 'Happy path validation',
            status: 'pass',
            message: 'All primary user flows validated successfully',
        },
        {
            id: 'v2',
            category: 'behavior',
            name: 'Edge case coverage',
            status: 'warning',
            message: '15 edge cases found, 3 need attention',
            severity: 'medium',
        },
        {
            id: 'v3',
            category: 'accessibility',
            name: 'WCAG 2.1 AA compliance',
            status: 'fail',
            message: '8 accessibility violations found',
            severity: 'high',
        },
        {
            id: 'v4',
            category: 'performance',
            name: 'Load time projection',
            status: 'pass',
            message: 'Estimated FCP: 1.2s, LCP: 2.1s (within targets)',
        },
        {
            id: 'v5',
            category: 'security',
            name: 'Threat model validation',
            status: 'warning',
            message: '2 threats require mitigation review',
            severity: 'high',
        },
    ]);

    // Mock simulation scenarios
    const [scenarios, setScenarios] = useState<SimulationScenario[]>([
        {
            id: 's1',
            name: 'User onboarding flow',
            description: 'Complete user registration and first login',
            status: 'pass',
            steps: 8,
            completedSteps: 8,
        },
        {
            id: 's2',
            name: 'Payment processing',
            description: 'Add payment method and complete transaction',
            status: 'pass',
            steps: 12,
            completedSteps: 12,
        },
        {
            id: 's3',
            name: 'Error recovery',
            description: 'Handle network failures gracefully',
            status: 'pending',
            steps: 6,
            completedSteps: 0,
        },
    ]);

    const handleRunValidation = useCallback(async () => {
        setRunning(true);

        // Simulate validation run (in production, call backend)
        setTimeout(() => {
            // Update scenarios
            setScenarios(prev => prev.map(s =>
                s.status === 'pending'
                    ? { ...s, status: 'running', completedSteps: 0 }
                    : s
            ));

            // Generate validation report artifact
            const userId = 'current-user'; // NOTE: Get from auth
            const existingReport = artifacts.find(a => a.kind === LifecycleArtifactKind.VALIDATION_REPORT);

            const reportPayload = {
                timestamp: new Date().toISOString(),
                overallStatus: 'warning',
                checksRun: validationChecks.length,
                checksPassed: validationChecks.filter(c => c.status === 'pass').length,
                scenariosRun: scenarios.length,
                scenariosPassed: scenarios.filter(s => s.status === 'pass').length,
                criticalIssues: validationChecks.filter(c => c.severity === 'critical').length,
                recommendations: [
                    'Fix accessibility violations before proceeding to GENERATE phase',
                    'Review threat model mitigations',
                    'Complete error recovery simulation',
                ],
            };

            if (existingReport) {
                updateArtifact(existingReport.id, { payload: reportPayload }, userId);
            } else {
                createArtifact(LifecycleArtifactKind.VALIDATION_REPORT, userId);
            }

            setRunning(false);
        }, 2000);
    }, [artifacts, createArtifact, updateArtifact, validationChecks, scenarios]);

    const getStatusIcon = (status: ValidationCheck['status']) => {
        switch (status) {
            case 'pass': return <CheckCircle className="w-5 h-5 text-success-color" />;
            case 'fail': return <ErrorIcon className="w-5 h-5 text-error-color" />;
            case 'warning': return <Warning className="w-5 h-5 text-warning-color" />;
            default: return <div className="w-5 h-5 rounded-full bg-grey-300" />;
        }
    };

    const getCategoryIcon = (category: ValidationCheck['category']) => {
        switch (category) {
            case 'behavior': return <BugReport className="w-4 h-4" />;
            case 'accessibility': return <Accessible className="w-4 h-4" />;
            case 'performance': return <Speed className="w-4 h-4" />;
            case 'security': return <ErrorIcon className="w-4 h-4" />;
        }
    };

    const passCount = validationChecks.filter(c => c.status === 'pass').length;
    const failCount = validationChecks.filter(c => c.status === 'fail').length;
    const warningCount = validationChecks.filter(c => c.status === 'warning').length;
    const totalCount = validationChecks.length;

    return (
        <div className="flex flex-col h-full bg-bg-default">
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-divider bg-bg-paper">
                <div>
                    <h2 className="text-lg font-semibold text-text-primary">Validation Dashboard</h2>
                    <p className="text-sm text-text-secondary">VALIDATE Phase - Quality Assurance</p>
                </div>
                <button
                    onClick={handleRunValidation}
                    disabled={running}
                    className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                    <PlayArrow className="w-4 h-4" />
                    {running ? 'Running...' : 'Run Validation'}
                </button>
            </div>

            {/* Tabs */}
            <div className="flex gap-1 px-4 py-2 border-b border-divider bg-bg-default">
                {(['overview', 'simulation', 'accessibility', 'performance'] as const).map((tab) => (
                    <button
                        key={tab}
                        onClick={() => setActiveTab(tab)}
                        className={`px-3 py-1.5 text-sm font-medium rounded-md transition-colors capitalize ${activeTab === tab
                            ? 'bg-primary-50 text-primary-600'
                            : 'text-text-secondary hover:text-text-primary hover:bg-grey-100'
                            }`}
                    >
                        {tab}
                    </button>
                ))}
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-4">
                {activeTab === 'overview' && (
                    <div className="space-y-6">
                        {/* Summary Cards */}
                        <div className="grid grid-cols-3 gap-4">
                            <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                <div className="text-2xl font-bold text-success-color">{passCount}</div>
                                <div className="text-sm text-text-secondary">Passed</div>
                            </div>
                            <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                <div className="text-2xl font-bold text-warning-color">{warningCount}</div>
                                <div className="text-sm text-text-secondary">Warnings</div>
                            </div>
                            <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                                <div className="text-2xl font-bold text-error-color">{failCount}</div>
                                <div className="text-sm text-text-secondary">Failed</div>
                            </div>
                        </div>

                        {/* All Checks */}
                        <div>
                            <h3 className="text-sm font-medium text-text-primary mb-3">Validation Checks</h3>
                            <div className="space-y-2">
                                {validationChecks.map((check) => (
                                    <div
                                        key={check.id}
                                        className="flex items-start gap-3 p-3 rounded-lg border border-divider bg-bg-paper hover:border-primary-200 transition-colors"
                                    >
                                        {getStatusIcon(check.status)}
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2">
                                                {getCategoryIcon(check.category)}
                                                <span className="text-sm font-medium text-text-primary">{check.name}</span>
                                                {check.severity && (
                                                    <span className={`text-xs px-2 py-0.5 rounded-full ${check.severity === 'critical' ? 'bg-error-color text-white' :
                                                        check.severity === 'high' ? 'bg-warning-color text-white' :
                                                            'bg-grey-200 text-text-secondary'
                                                        }`}>
                                                        {check.severity}
                                                    </span>
                                                )}
                                            </div>
                                            <p className="text-sm text-text-secondary mt-1">{check.message}</p>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === 'simulation' && (
                    <div className="space-y-4">
                        <h3 className="text-sm font-medium text-text-primary">Simulation Scenarios</h3>
                        <div className="space-y-3">
                            {scenarios.map((scenario) => (
                                <div
                                    key={scenario.id}
                                    className="p-4 rounded-lg border border-divider bg-bg-paper"
                                >
                                    <div className="flex items-start justify-between mb-2">
                                        <div>
                                            <h4 className="text-sm font-medium text-text-primary">{scenario.name}</h4>
                                            <p className="text-xs text-text-secondary mt-1">{scenario.description}</p>
                                        </div>
                                        <span className={`text-xs px-2 py-1 rounded-full font-medium ${scenario.status === 'pass' ? 'bg-success-color text-white' :
                                            scenario.status === 'fail' ? 'bg-error-color text-white' :
                                                scenario.status === 'running' ? 'bg-primary-600 text-white' :
                                                    'bg-grey-200 text-text-secondary'
                                            }`}>
                                            {scenario.status}
                                        </span>
                                    </div>
                                    <div className="mt-3">
                                        <div className="flex items-center gap-2 text-xs text-text-secondary mb-1">
                                            <span>{scenario.completedSteps} / {scenario.steps} steps</span>
                                            <span>•</span>
                                            <span>{Math.round((scenario.completedSteps / scenario.steps) * 100)}%</span>
                                        </div>
                                        <div className="w-full h-2 bg-grey-200 rounded-full overflow-hidden">
                                            <div
                                                className="h-full bg-primary-600 transition-all duration-300"
                                                style={{ width: `${(scenario.completedSteps / scenario.steps) * 100}%` }}
                                            />
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {activeTab === 'accessibility' && (
                    <div className="space-y-4">
                        <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                            <h3 className="text-sm font-medium text-text-primary mb-2">WCAG 2.1 Compliance</h3>
                            <p className="text-sm text-text-secondary mb-4">
                                8 violations found (5 Level A, 3 Level AA)
                            </p>
                            <div className="space-y-2">
                                <div className="text-sm">
                                    <span className="font-medium">Missing alt text:</span> 12 images
                                </div>
                                <div className="text-sm">
                                    <span className="font-medium">Color contrast:</span> 5 instances below 4.5:1
                                </div>
                                <div className="text-sm">
                                    <span className="font-medium">Keyboard navigation:</span> 3 interactive elements not focusable
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === 'performance' && (
                    <div className="space-y-4">
                        <div className="p-4 rounded-lg border border-divider bg-bg-paper">
                            <h3 className="text-sm font-medium text-text-primary mb-2">Performance Projections</h3>
                            <div className="grid grid-cols-2 gap-4 mt-4">
                                <div>
                                    <div className="text-xs text-text-secondary">First Contentful Paint</div>
                                    <div className="text-2xl font-bold text-success-color">1.2s</div>
                                </div>
                                <div>
                                    <div className="text-xs text-text-secondary">Largest Contentful Paint</div>
                                    <div className="text-2xl font-bold text-success-color">2.1s</div>
                                </div>
                                <div>
                                    <div className="text-xs text-text-secondary">Time to Interactive</div>
                                    <div className="text-2xl font-bold text-warning-color">3.4s</div>
                                </div>
                                <div>
                                    <div className="text-xs text-text-secondary">Total Blocking Time</div>
                                    <div className="text-2xl font-bold text-success-color">180ms</div>
                                </div>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

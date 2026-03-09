/**
 * Release Strategy Configurator Component
 *
 * Configurator for release strategies, environments, and rollout plans.
 * Used in Deploy surface Configure segment.
 *
 * @doc.type component
 * @doc.purpose GENERATE phase release strategy configuration
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useCallback } from 'react';
import { Plus as Add, Minus as Remove, Save, Sparkles as AutoAwesome, Rocket as RocketLaunch, Cloud, Settings, Shield as Security } from 'lucide-react';
import type { ReleaseStrategyPayload } from '@/shared/types/lifecycle-artifacts';

export interface ReleaseStrategyConfiguratorProps {
    data?: ReleaseStrategyPayload;
    onSave: (data: ReleaseStrategyPayload) => Promise<void>;
    onAIAssist?: (context: { strategy?: ReleaseStrategyPayload }) => Promise<Partial<ReleaseStrategyPayload> | null>;
    isLoading?: boolean;
}

type ReleaseType = 'blue_green' | 'canary' | 'rolling' | 'feature_flag' | 'big_bang';
type EnvironmentType = 'development' | 'staging' | 'production';

const RELEASE_TYPE_INFO: Record<ReleaseType, { label: string; description: string }> = {
    blue_green: {
        label: 'Blue-Green',
        description: 'Two identical environments, instant switch between them',
    },
    canary: {
        label: 'Canary',
        description: 'Gradual rollout to a subset of users before full deployment',
    },
    rolling: {
        label: 'Rolling',
        description: 'Incremental update across instances with zero downtime',
    },
    feature_flag: {
        label: 'Feature Flag',
        description: 'Deploy code but control feature visibility via flags',
    },
    big_bang: {
        label: 'Big Bang',
        description: 'All-at-once deployment (use with caution)',
    },
};

const ENVIRONMENT_COLORS: Record<EnvironmentType, string> = {
    development: 'bg-grey-100 text-grey-700 dark:bg-grey-800 dark:text-grey-300',
    staging: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
    production: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
};

const defaultData: ReleaseStrategyPayload = {
    releaseType: 'canary',
    environments: [],
    rolloutSteps: [],
    rollbackPlan: '',
    approvalGates: [],
    notificationChannels: [],
};

/**
 * Release Strategy Configurator for GENERATE phase.
 */
export const ReleaseStrategyConfigurator: React.FC<ReleaseStrategyConfiguratorProps> = ({
    data,
    onSave,
    onAIAssist,
    isLoading = false,
}) => {
    const [strategy, setStrategy] = useState<ReleaseStrategyPayload>({
        ...defaultData,
        ...data,
        environments: data?.environments || [],
        rolloutSteps: data?.rolloutSteps || [],
        approvalGates: data?.approvalGates || [],
        notificationChannels: data?.notificationChannels || [],
    });
    const [isSaving, setIsSaving] = useState(false);
    const [isAILoading, setIsAILoading] = useState(false);
    const [activeTab, setActiveTab] = useState<'strategy' | 'environments' | 'rollout'>('strategy');

    // Environment operations
    const addEnvironment = useCallback(() => {
        setStrategy((prev) => ({
            ...prev,
            environments: [
                ...prev.environments,
                {
                    name: '',
                    type: 'development' as EnvironmentType,
                    url: '',
                    variables: [],
                },
            ],
        }));
    }, []);

    const updateEnvironment = useCallback((index: number, updates: Partial<ReleaseStrategyPayload['environments'][0]>) => {
        setStrategy((prev) => ({
            ...prev,
            environments: prev.environments.map((e, i) => (i === index ? { ...e, ...updates } : e)),
        }));
    }, []);

    const removeEnvironment = useCallback((index: number) => {
        setStrategy((prev) => ({
            ...prev,
            environments: prev.environments.filter((_, i) => i !== index),
        }));
    }, []);

    // Environment variable operations
    const addEnvVariable = useCallback((envIndex: number) => {
        setStrategy((prev) => ({
            ...prev,
            environments: prev.environments.map((e, i) =>
                i === envIndex
                    ? { ...e, variables: [...e.variables, { key: '', value: '', isSecret: false }] }
                    : e,
            ),
        }));
    }, []);

    const updateEnvVariable = useCallback(
        (envIndex: number, varIndex: number, updates: Partial<{ key: string; value: string; isSecret: boolean }>) => {
            setStrategy((prev) => ({
                ...prev,
                environments: prev.environments.map((e, i) =>
                    i === envIndex
                        ? {
                            ...e,
                            variables: e.variables.map((v, j) => (j === varIndex ? { ...v, ...updates } : v)),
                        }
                        : e,
                ),
            }));
        },
        [],
    );

    const removeEnvVariable = useCallback((envIndex: number, varIndex: number) => {
        setStrategy((prev) => ({
            ...prev,
            environments: prev.environments.map((e, i) =>
                i === envIndex
                    ? { ...e, variables: e.variables.filter((_, j) => j !== varIndex) }
                    : e,
            ),
        }));
    }, []);

    // Rollout step operations
    const addRolloutStep = useCallback(() => {
        setStrategy((prev) => ({
            ...prev,
            rolloutSteps: [
                ...prev.rolloutSteps,
                {
                    percentage: 0,
                    duration: '',
                    successCriteria: [],
                },
            ],
        }));
    }, []);

    const updateRolloutStep = useCallback((index: number, updates: Partial<ReleaseStrategyPayload['rolloutSteps'][0]>) => {
        setStrategy((prev) => ({
            ...prev,
            rolloutSteps: prev.rolloutSteps.map((s, i) => (i === index ? { ...s, ...updates } : s)),
        }));
    }, []);

    const removeRolloutStep = useCallback((index: number) => {
        setStrategy((prev) => ({
            ...prev,
            rolloutSteps: prev.rolloutSteps.filter((_, i) => i !== index),
        }));
    }, []);

    // Approval gate operations
    const addApprovalGate = useCallback(() => {
        setStrategy((prev) => ({
            ...prev,
            approvalGates: [...prev.approvalGates, { name: '', approvers: [], required: true }],
        }));
    }, []);

    const updateApprovalGate = useCallback((index: number, updates: Partial<ReleaseStrategyPayload['approvalGates'][0]>) => {
        setStrategy((prev) => ({
            ...prev,
            approvalGates: prev.approvalGates.map((g, i) => (i === index ? { ...g, ...updates } : g)),
        }));
    }, []);

    const removeApprovalGate = useCallback((index: number) => {
        setStrategy((prev) => ({
            ...prev,
            approvalGates: prev.approvalGates.filter((_, i) => i !== index),
        }));
    }, []);

    const handleAIAssist = useCallback(async () => {
        if (!onAIAssist) return;
        setIsAILoading(true);
        try {
            const result = await onAIAssist({ strategy });
            if (result) {
                setStrategy((prev) => ({
                    ...prev,
                    ...result,
                }));
            }
        } finally {
            setIsAILoading(false);
        }
    }, [onAIAssist, strategy]);

    const handleSave = useCallback(async () => {
        setIsSaving(true);
        try {
            await onSave(strategy);
        } finally {
            setIsSaving(false);
        }
    }, [strategy, onSave]);

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-divider">
                <div className="flex items-center gap-2">
                    <RocketLaunch className="w-5 h-5 text-orange-600" />
                    <div>
                        <h3 className="font-semibold text-text-primary">Release Strategy</h3>
                        <p className="text-xs text-text-secondary">
                            {RELEASE_TYPE_INFO[strategy.releaseType].label} deployment
                        </p>
                    </div>
                </div>
                <div className="flex gap-2">
                    {onAIAssist && (
                        <button
                            onClick={handleAIAssist}
                            disabled={isAILoading || isSaving}
                            className="flex items-center gap-1 px-3 py-1.5 text-sm text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded-lg transition-colors disabled:opacity-50"
                        >
                            <AutoAwesome className="w-4 h-4" />
                            {isAILoading ? 'Generating...' : 'AI Assist'}
                        </button>
                    )}
                    <button
                        onClick={handleSave}
                        disabled={isSaving || isLoading}
                        className="flex items-center gap-1 px-3 py-1.5 text-sm bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50"
                    >
                        <Save className="w-4 h-4" />
                        {isSaving ? 'Saving...' : 'Save'}
                    </button>
                </div>
            </div>

            {/* Tabs */}
            <div className="flex border-b border-divider">
                <button
                    onClick={() => setActiveTab('strategy')}
                    className={`flex items-center gap-1 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'strategy'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    <Settings className="w-4 h-4" /> Strategy
                </button>
                <button
                    onClick={() => setActiveTab('environments')}
                    className={`flex items-center gap-1 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'environments'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    <Cloud className="w-4 h-4" /> Environments ({strategy.environments.length})
                </button>
                <button
                    onClick={() => setActiveTab('rollout')}
                    className={`flex items-center gap-1 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'rollout'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    <RocketLaunch className="w-4 h-4" /> Rollout
                </button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-4">
                {activeTab === 'strategy' && (
                    <div className="space-y-6">
                        {/* Release Type Selection */}
                        <div>
                            <h4 className="text-sm font-medium text-text-primary mb-3">Release Type</h4>
                            <div className="grid grid-cols-1 gap-2">
                                {Object.entries(RELEASE_TYPE_INFO).map(([type, info]) => (
                                    <button
                                        key={type}
                                        onClick={() => setStrategy((prev) => ({ ...prev, releaseType: type as ReleaseType }))}
                                        className={`flex items-start gap-3 p-3 text-left rounded-lg border-2 transition-all ${strategy.releaseType === type
                                                ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                                                : 'border-divider hover:border-grey-300 dark:hover:border-grey-600'
                                            }`}
                                    >
                                        <div
                                            className={`w-4 h-4 mt-0.5 rounded-full border-2 flex items-center justify-center ${strategy.releaseType === type
                                                    ? 'border-primary-500 bg-primary-500'
                                                    : 'border-grey-300 dark:border-grey-600'
                                                }`}
                                        >
                                            {strategy.releaseType === type && (
                                                <div className="w-2 h-2 rounded-full bg-white" />
                                            )}
                                        </div>
                                        <div>
                                            <div className="font-medium text-text-primary">{info.label}</div>
                                            <div className="text-xs text-text-secondary">{info.description}</div>
                                        </div>
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Approval Gates */}
                        <div>
                            <h4 className="text-sm font-medium text-text-primary mb-2 flex items-center gap-2">
                                <Security className="w-4 h-4" /> Approval Gates
                            </h4>
                            <div className="space-y-2">
                                {strategy.approvalGates.map((gate, idx) => (
                                    <div key={idx} className="flex gap-2 items-start p-3 border border-divider rounded-lg bg-bg-paper">
                                        <input
                                            type="text"
                                            value={gate.name}
                                            onChange={(e) => updateApprovalGate(idx, { name: e.target.value })}
                                            placeholder="Gate name (e.g., QA Approval)"
                                            className="flex-1 px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                        />
                                        <input
                                            type="text"
                                            value={gate.approvers.join(', ')}
                                            onChange={(e) =>
                                                updateApprovalGate(idx, {
                                                    approvers: e.target.value.split(',').map((s) => s.trim()).filter(Boolean),
                                                })
                                            }
                                            placeholder="Approvers (comma-separated)"
                                            className="flex-1 px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                        />
                                        <label className="flex items-center gap-1 text-xs text-text-secondary">
                                            <input
                                                type="checkbox"
                                                checked={gate.required}
                                                onChange={(e) => updateApprovalGate(idx, { required: e.target.checked })}
                                                className="rounded"
                                            />
                                            Required
                                        </label>
                                        <button
                                            onClick={() => removeApprovalGate(idx)}
                                            className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                        >
                                            <Remove className="w-4 h-4" />
                                        </button>
                                    </div>
                                ))}
                                <button
                                    onClick={addApprovalGate}
                                    className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                                >
                                    <Add className="w-3 h-3" /> Add approval gate
                                </button>
                            </div>
                        </div>

                        {/* Rollback Plan */}
                        <div>
                            <h4 className="text-sm font-medium text-text-primary mb-2">Rollback Plan</h4>
                            <textarea
                                value={strategy.rollbackPlan}
                                onChange={(e) => setStrategy((prev) => ({ ...prev, rollbackPlan: e.target.value }))}
                                placeholder="Describe the rollback procedure..."
                                rows={4}
                                className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                            />
                        </div>
                    </div>
                )}

                {activeTab === 'environments' && (
                    <div className="space-y-4">
                        {strategy.environments.length === 0 ? (
                            <div className="text-center py-8 text-text-secondary">
                                <Cloud className="w-12 h-12 mx-auto mb-2 opacity-50" />
                                <p className="text-sm">No environments configured</p>
                                <button
                                    onClick={addEnvironment}
                                    className="mt-2 text-sm text-primary-600 hover:text-primary-700"
                                >
                                    Add first environment
                                </button>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {strategy.environments.map((env, idx) => (
                                    <div key={idx} className="border border-divider rounded-lg bg-bg-paper overflow-hidden">
                                        <div className="flex items-center gap-2 p-3 bg-grey-50 dark:bg-grey-800/50">
                                            <input
                                                type="text"
                                                value={env.name}
                                                onChange={(e) => updateEnvironment(idx, { name: e.target.value })}
                                                placeholder="Environment name"
                                                className="flex-1 px-2 py-1 text-sm font-medium border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                            />
                                            <select
                                                value={env.type}
                                                onChange={(e) => updateEnvironment(idx, { type: e.target.value as EnvironmentType })}
                                                className={`px-2 py-1 text-xs rounded ${ENVIRONMENT_COLORS[env.type]}`}
                                            >
                                                <option value="development">Development</option>
                                                <option value="staging">Staging</option>
                                                <option value="production">Production</option>
                                            </select>
                                            <button
                                                onClick={() => removeEnvironment(idx)}
                                                className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                            >
                                                <Remove className="w-4 h-4" />
                                            </button>
                                        </div>
                                        <div className="p-3 space-y-3">
                                            <input
                                                type="url"
                                                value={env.url}
                                                onChange={(e) => updateEnvironment(idx, { url: e.target.value })}
                                                placeholder="Environment URL"
                                                className="w-full px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                            />
                                            <div>
                                                <h5 className="text-xs font-medium text-text-secondary mb-2">
                                                    Environment Variables
                                                </h5>
                                                <div className="space-y-1">
                                                    {env.variables.map((variable, vIdx) => (
                                                        <div key={vIdx} className="flex gap-2 items-center">
                                                            <input
                                                                type="text"
                                                                value={variable.key}
                                                                onChange={(e) =>
                                                                    updateEnvVariable(idx, vIdx, { key: e.target.value })
                                                                }
                                                                placeholder="KEY"
                                                                className="w-1/3 px-2 py-1 text-xs font-mono border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                            />
                                                            <input
                                                                type={variable.isSecret ? 'password' : 'text'}
                                                                value={variable.value}
                                                                onChange={(e) =>
                                                                    updateEnvVariable(idx, vIdx, { value: e.target.value })
                                                                }
                                                                placeholder="value"
                                                                className="flex-1 px-2 py-1 text-xs font-mono border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                            />
                                                            <label className="flex items-center gap-1 text-xs text-text-secondary">
                                                                <input
                                                                    type="checkbox"
                                                                    checked={variable.isSecret}
                                                                    onChange={(e) =>
                                                                        updateEnvVariable(idx, vIdx, { isSecret: e.target.checked })
                                                                    }
                                                                    className="rounded"
                                                                />
                                                                🔒
                                                            </label>
                                                            <button
                                                                onClick={() => removeEnvVariable(idx, vIdx)}
                                                                className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                                            >
                                                                <Remove className="w-3 h-3" />
                                                            </button>
                                                        </div>
                                                    ))}
                                                    <button
                                                        onClick={() => addEnvVariable(idx)}
                                                        className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                                                    >
                                                        <Add className="w-3 h-3" /> Add variable
                                                    </button>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                        <button
                            onClick={addEnvironment}
                            className="w-full flex items-center justify-center gap-2 p-3 border-2 border-dashed border-divider rounded-lg text-text-secondary hover:text-primary-600 hover:border-primary-300 transition-colors"
                        >
                            <Add className="w-5 h-5" /> Add Environment
                        </button>
                    </div>
                )}

                {activeTab === 'rollout' && (
                    <div className="space-y-4">
                        <p className="text-sm text-text-secondary">
                            Define the rollout steps for your {RELEASE_TYPE_INFO[strategy.releaseType].label} deployment.
                        </p>
                        {strategy.rolloutSteps.length === 0 ? (
                            <div className="text-center py-8 text-text-secondary">
                                <RocketLaunch className="w-12 h-12 mx-auto mb-2 opacity-50" />
                                <p className="text-sm">No rollout steps defined</p>
                                <button
                                    onClick={addRolloutStep}
                                    className="mt-2 text-sm text-primary-600 hover:text-primary-700"
                                >
                                    Add first step
                                </button>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {strategy.rolloutSteps.map((step, idx) => (
                                    <div
                                        key={idx}
                                        className="flex items-start gap-3 p-3 border border-divider rounded-lg bg-bg-paper"
                                    >
                                        <div className="w-8 h-8 flex items-center justify-center bg-primary-100 dark:bg-primary-900/30 text-primary-600 rounded-full font-medium text-sm">
                                            {idx + 1}
                                        </div>
                                        <div className="flex-1 space-y-2">
                                            <div className="flex gap-2">
                                                <div className="flex-1">
                                                    <label className="block text-xs text-text-secondary mb-1">
                                                        Traffic %
                                                    </label>
                                                    <input
                                                        type="number"
                                                        min={0}
                                                        max={100}
                                                        value={step.percentage}
                                                        onChange={(e) =>
                                                            updateRolloutStep(idx, {
                                                                percentage: parseInt(e.target.value, 10) || 0,
                                                            })
                                                        }
                                                        className="w-full px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                    />
                                                </div>
                                                <div className="flex-1">
                                                    <label className="block text-xs text-text-secondary mb-1">
                                                        Duration
                                                    </label>
                                                    <input
                                                        type="text"
                                                        value={step.duration}
                                                        onChange={(e) => updateRolloutStep(idx, { duration: e.target.value })}
                                                        placeholder="e.g., 1h, 30m"
                                                        className="w-full px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                    />
                                                </div>
                                            </div>
                                            <div>
                                                <label className="block text-xs text-text-secondary mb-1">
                                                    Success Criteria (one per line)
                                                </label>
                                                <textarea
                                                    value={step.successCriteria.join('\n')}
                                                    onChange={(e) =>
                                                        updateRolloutStep(idx, {
                                                            successCriteria: e.target.value
                                                                .split('\n')
                                                                .filter((l) => l.trim()),
                                                        })
                                                    }
                                                    placeholder="Error rate < 1%&#10;P99 latency < 500ms"
                                                    rows={2}
                                                    className="w-full px-2 py-1 text-xs border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none"
                                                />
                                            </div>
                                        </div>
                                        <button
                                            onClick={() => removeRolloutStep(idx)}
                                            className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                        >
                                            <Remove className="w-4 h-4" />
                                        </button>
                                    </div>
                                ))}
                            </div>
                        )}
                        <button
                            onClick={addRolloutStep}
                            className="w-full flex items-center justify-center gap-2 p-3 border-2 border-dashed border-divider rounded-lg text-text-secondary hover:text-primary-600 hover:border-primary-300 transition-colors"
                        >
                            <Add className="w-5 h-5" /> Add Rollout Step
                        </button>

                        {/* Progress Visualization */}
                        {strategy.rolloutSteps.length > 0 && (
                            <div className="mt-4 p-4 bg-grey-50 dark:bg-grey-800/50 rounded-lg">
                                <h5 className="text-xs font-medium text-text-secondary mb-2">Rollout Preview</h5>
                                <div className="flex items-center gap-1">
                                    {strategy.rolloutSteps.map((step, idx) => (
                                        <React.Fragment key={idx}>
                                            <div
                                                className="h-2 bg-primary-500 rounded"
                                                style={{ width: `${step.percentage}%`, minWidth: '4px' }}
                                                title={`${step.percentage}% for ${step.duration || 'TBD'}`}
                                            />
                                            {idx < strategy.rolloutSteps.length - 1 && (
                                                <div className="text-xs text-text-secondary">→</div>
                                            )}
                                        </React.Fragment>
                                    ))}
                                </div>
                                <div className="flex justify-between text-xs text-text-secondary mt-1">
                                    <span>0%</span>
                                    <span>100%</span>
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
};

export default ReleaseStrategyConfigurator;

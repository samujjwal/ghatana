/**
 * Threat Model Panel Component
 *
 * STRIDE-based threat modeling editor with assets, actors, and threats.
 *
 * @doc.type component
 * @doc.purpose SHAPE phase threat model editor
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useCallback } from 'react';
import { Plus as Add, Minus as Remove, Sparkles as AutoAwesome, Save, Shield as Security, AlertTriangle as Warning } from 'lucide-react';
import type { ThreatModelPayload } from '@/shared/types/lifecycle-artifacts';

export interface ThreatModelPanelProps {
    data?: ThreatModelPayload;
    onSave: (data: ThreatModelPayload) => Promise<void>;
    onAIAssist?: (context: { threatModel?: ThreatModelPayload }) => Promise<Partial<ThreatModelPayload> | null>;
    onClose: () => void;
    isLoading?: boolean;
}

type StrideCategory = 'spoofing' | 'tampering' | 'repudiation' | 'info_disclosure' | 'denial_of_service' | 'elevation';
type Severity = 'low' | 'medium' | 'high' | 'critical';

const STRIDE_LABELS: Record<StrideCategory, { label: string; abbrev: string }> = {
    spoofing: { label: 'Spoofing', abbrev: 'SP' },
    tampering: { label: 'Tampering', abbrev: 'TA' },
    repudiation: { label: 'Repudiation', abbrev: 'RE' },
    info_disclosure: { label: 'Info Disclosure', abbrev: 'ID' },
    denial_of_service: { label: 'Denial of Service', abbrev: 'DOS' },
    elevation: { label: 'Elevation of Privilege', abbrev: 'EP' },
};

const SEVERITY_COLORS: Record<Severity, string> = {
    low: 'bg-grey-100 text-grey-700 dark:bg-grey-800 dark:text-grey-300',
    medium: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
    high: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300',
    critical: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
};

const defaultData: ThreatModelPayload = {
    assets: [{ name: '', description: '' }],
    actors: [{ name: '', description: '', type: 'external' }],
    threats: [],
    mitigations: [],
    residualRisk: '',
};

/**
 * Threat Model Panel for SHAPE phase.
 */
export const ThreatModelPanel: React.FC<ThreatModelPanelProps> = ({
    data,
    onSave,
    onAIAssist,
    onClose,
    isLoading = false,
}) => {
    const [model, setModel] = useState<ThreatModelPayload>({
        ...defaultData,
        ...data,
        assets: data?.assets?.length ? data.assets : defaultData.assets,
        actors: data?.actors?.length ? data.actors : defaultData.actors,
        threats: data?.threats || [],
        mitigations: data?.mitigations || [],
    });
    const [isSaving, setIsSaving] = useState(false);
    const [isAILoading, setIsAILoading] = useState(false);
    const [activeTab, setActiveTab] = useState<'assets' | 'threats' | 'mitigations'>('assets');

    // Asset operations
    const updateAsset = useCallback((index: number, updates: Partial<{ name: string; description: string }>) => {
        setModel((prev) => ({
            ...prev,
            assets: prev.assets.map((a, i) => (i === index ? { ...a, ...updates } : a)),
        }));
    }, []);

    const addAsset = useCallback(() => {
        setModel((prev) => ({
            ...prev,
            assets: [...prev.assets, { name: '', description: '' }],
        }));
    }, []);

    const removeAsset = useCallback((index: number) => {
        setModel((prev) => ({
            ...prev,
            assets: prev.assets.filter((_, i) => i !== index),
        }));
    }, []);

    // Actor operations
    const updateActor = useCallback((index: number, updates: Partial<{ name: string; description: string; type: 'internal' | 'external' }>) => {
        setModel((prev) => ({
            ...prev,
            actors: prev.actors.map((a, i) => (i === index ? { ...a, ...updates } : a)),
        }));
    }, []);

    const addActor = useCallback(() => {
        setModel((prev) => ({
            ...prev,
            actors: [...prev.actors, { name: '', description: '', type: 'external' }],
        }));
    }, []);

    const removeActor = useCallback((index: number) => {
        setModel((prev) => ({
            ...prev,
            actors: prev.actors.filter((_, i) => i !== index),
        }));
    }, []);

    // Threat operations
    const addThreat = useCallback(() => {
        setModel((prev) => ({
            ...prev,
            threats: [
                ...prev.threats,
                { asset: '', category: 'spoofing' as StrideCategory, description: '', severity: 'medium' as Severity },
            ],
        }));
    }, []);

    const updateThreat = useCallback((index: number, updates: Partial<ThreatModelPayload['threats'][0]>) => {
        setModel((prev) => ({
            ...prev,
            threats: prev.threats.map((t, i) => (i === index ? { ...t, ...updates } : t)),
        }));
    }, []);

    const removeThreat = useCallback((index: number) => {
        setModel((prev) => ({
            ...prev,
            threats: prev.threats.filter((_, i) => i !== index),
        }));
    }, []);

    // Mitigation operations
    const addMitigation = useCallback((threatDescription?: string) => {
        setModel((prev) => ({
            ...prev,
            mitigations: [
                ...prev.mitigations,
                { threat: threatDescription || '', control: '', status: 'planned' as const },
            ],
        }));
    }, []);

    const updateMitigation = useCallback((index: number, updates: Partial<ThreatModelPayload['mitigations'][0]>) => {
        setModel((prev) => ({
            ...prev,
            mitigations: prev.mitigations.map((m, i) => (i === index ? { ...m, ...updates } : m)),
        }));
    }, []);

    const removeMitigation = useCallback((index: number) => {
        setModel((prev) => ({
            ...prev,
            mitigations: prev.mitigations.filter((_, i) => i !== index),
        }));
    }, []);

    const handleAIAssist = useCallback(async () => {
        if (!onAIAssist) return;
        setIsAILoading(true);
        try {
            const result = await onAIAssist({ threatModel: model });
            if (result) {
                setModel((prev) => ({
                    ...prev,
                    ...result,
                    assets: result.assets?.length ? result.assets : prev.assets,
                    actors: result.actors?.length ? result.actors : prev.actors,
                    threats: result.threats?.length ? result.threats : prev.threats,
                    mitigations: result.mitigations?.length ? result.mitigations : prev.mitigations,
                }));
            }
        } finally {
            setIsAILoading(false);
        }
    }, [onAIAssist, model]);

    const handleSave = useCallback(async () => {
        setIsSaving(true);
        try {
            const cleanModel: ThreatModelPayload = {
                ...model,
                assets: model.assets.filter((a) => a.name.trim()),
                actors: model.actors.filter((a) => a.name.trim()),
                threats: model.threats.filter((t) => t.description.trim()),
                mitigations: model.mitigations.filter((m) => m.control.trim()),
            };
            await onSave(cleanModel);
        } finally {
            setIsSaving(false);
        }
    }, [model, onSave]);

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-divider">
                <div className="flex items-center gap-2">
                    <Security className="w-5 h-5 text-primary-600" />
                    <div>
                        <h3 className="font-semibold text-text-primary">Threat Model</h3>
                        <p className="text-xs text-text-secondary">STRIDE-based security analysis</p>
                    </div>
                </div>
                <div className="flex gap-2">
                    {onAIAssist && (
                        <button
                            onClick={handleAIAssist}
                            disabled={isAILoading || isSaving}
                            className="flex items-center gap-1 px-3 py-1.5 text-sm font-medium text-text-secondary border border-transparent hover:border-divider hover:text-text-primary hover:bg-grey-50 dark:hover:bg-grey-800/40 rounded-lg transition-colors disabled:opacity-50"
                        >
                            <AutoAwesome className="w-4 h-4" />
                            {isAILoading ? 'Analyzing...' : 'AI Assist'}
                        </button>
                    )}
                    <button
                        onClick={handleSave}
                        disabled={isSaving || isLoading}
                        className="flex items-center gap-1 px-3 py-1.5 text-sm font-semibold bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50"
                    >
                        <Save className="w-4 h-4" />
                        {isSaving ? 'Saving...' : 'Save'}
                    </button>
                </div>
            </div>

            {/* Tabs */}
            <div className="flex border-b border-divider">
                <button
                    onClick={() => setActiveTab('assets')}
                    className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'assets'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    Assets & Actors
                </button>
                <button
                    onClick={() => setActiveTab('threats')}
                    className={`flex items-center gap-1 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'threats'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    <Warning className="w-4 h-4" />
                    Threats ({model.threats.length})
                </button>
                <button
                    onClick={() => setActiveTab('mitigations')}
                    className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'mitigations'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    Mitigations ({model.mitigations.length})
                </button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-4">
                {activeTab === 'assets' && (
                    <div className="space-y-6">
                        {/* Assets */}
                        <div>
                            <h4 className="text-sm font-semibold text-text-primary mb-3">Assets (What to protect)</h4>
                            <div className="space-y-2">
                                {model.assets.map((asset, idx) => (
                                    <div key={idx} className="flex gap-2 items-start">
                                        <div className="flex-1 grid grid-cols-2 gap-2">
                                            <input
                                                type="text"
                                                value={asset.name}
                                                onChange={(e) => updateAsset(idx, { name: e.target.value })}
                                                placeholder="Asset name"
                                                className="px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                            />
                                            <input
                                                type="text"
                                                value={asset.description}
                                                onChange={(e) => updateAsset(idx, { description: e.target.value })}
                                                placeholder="Description"
                                                className="px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                            />
                                        </div>
                                        {model.assets.length > 1 && (
                                            <button
                                                onClick={() => removeAsset(idx)}
                                                className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                            >
                                                <Remove className="w-4 h-4" />
                                            </button>
                                        )}
                                    </div>
                                ))}
                                <button
                                    onClick={addAsset}
                                    className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                                >
                                    <Add className="w-3 h-3" /> Add asset
                                </button>
                            </div>
                        </div>

                        {/* Actors */}
                        <div>
                            <h4 className="text-sm font-semibold text-text-primary mb-3">Threat Actors</h4>
                            <div className="space-y-2">
                                {model.actors.map((actor, idx) => (
                                    <div key={idx} className="flex gap-2 items-start">
                                        <div className="flex-1 grid grid-cols-3 gap-2">
                                            <input
                                                type="text"
                                                value={actor.name}
                                                onChange={(e) => updateActor(idx, { name: e.target.value })}
                                                placeholder="Actor name"
                                                className="px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                            />
                                            <input
                                                type="text"
                                                value={actor.description}
                                                onChange={(e) => updateActor(idx, { description: e.target.value })}
                                                placeholder="Description"
                                                className="px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                            />
                                            <select
                                                value={actor.type}
                                                onChange={(e) => updateActor(idx, { type: e.target.value as 'internal' | 'external' })}
                                                className="px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                            >
                                                <option value="external">External</option>
                                                <option value="internal">Internal</option>
                                            </select>
                                        </div>
                                        {model.actors.length > 1 && (
                                            <button
                                                onClick={() => removeActor(idx)}
                                                className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                            >
                                                <Remove className="w-4 h-4" />
                                            </button>
                                        )}
                                    </div>
                                ))}
                                <button
                                    onClick={addActor}
                                    className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                                >
                                    <Add className="w-3 h-3" /> Add actor
                                </button>
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === 'threats' && (
                    <div className="space-y-4">
                        {/* STRIDE Legend */}
                        <div className="flex flex-wrap gap-2 p-3 bg-grey-50 dark:bg-grey-800/50 rounded-lg">
                            {Object.entries(STRIDE_LABELS).map(([key, { label, abbrev }]) => (
                                <span key={key} className="text-xs text-text-secondary flex items-center gap-2">
                                    <span className="inline-flex items-center justify-center min-w-7 px-1.5 py-0.5 rounded bg-grey-200 text-grey-700 dark:bg-grey-700 dark:text-grey-100 text-[10px] font-semibold">
                                        {abbrev}
                                    </span>
                                    {label}
                                </span>
                            ))}
                        </div>

                        {/* Threats List */}
                        {model.threats.length === 0 ? (
                            <div className="text-center py-8 text-text-secondary">
                                <Warning className="w-12 h-12 mx-auto mb-2 opacity-50" />
                                <p className="text-sm">No threats yet</p>
                                <button
                                    onClick={addThreat}
                                    className="mt-2 text-sm text-primary-600 hover:text-primary-700"
                                >
                                    Add first threat
                                </button>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {model.threats.map((threat, idx) => (
                                    <div key={idx} className="border border-divider rounded-lg bg-bg-paper p-3 space-y-2">
                                        <div className="flex items-start gap-2">
                                            <select
                                                value={threat.category}
                                                onChange={(e) => updateThreat(idx, { category: e.target.value as StrideCategory })}
                                                className="px-2 py-1 text-xs border border-divider rounded bg-bg-default text-text-primary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                            >
                                                {Object.entries(STRIDE_LABELS).map(([key, { label }]) => (
                                                    <option key={key} value={key}>{label}</option>
                                                ))}
                                            </select>
                                            <select
                                                value={threat.asset}
                                                onChange={(e) => updateThreat(idx, { asset: e.target.value })}
                                                className="px-2 py-1 text-xs border border-divider rounded bg-bg-default text-text-primary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                            >
                                                <option value="">Select asset</option>
                                                {model.assets.filter((a) => a.name.trim()).map((a, i) => (
                                                    <option key={i} value={a.name}>{a.name}</option>
                                                ))}
                                            </select>
                                            <select
                                                value={threat.severity}
                                                onChange={(e) => updateThreat(idx, { severity: e.target.value as Severity })}
                                                className={`px-2 py-1 text-xs rounded ${SEVERITY_COLORS[threat.severity]}`}
                                            >
                                                <option value="low">Low</option>
                                                <option value="medium">Medium</option>
                                                <option value="high">High</option>
                                                <option value="critical">Critical</option>
                                            </select>
                                            <button
                                                onClick={() => removeThreat(idx)}
                                                className="ml-auto p-1 text-text-secondary hover:text-error-color transition-colors"
                                            >
                                                <Remove className="w-4 h-4" />
                                            </button>
                                        </div>
                                        <textarea
                                            value={threat.description}
                                            onChange={(e) => updateThreat(idx, { description: e.target.value })}
                                            placeholder="Describe the threat..."
                                            rows={2}
                                            className="w-full px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none"
                                        />
                                        <button
                                            onClick={() => addMitigation(threat.description)}
                                            className="text-xs text-primary-600 hover:text-primary-700"
                                        >
                                            + Add mitigation
                                        </button>
                                    </div>
                                ))}
                            </div>
                        )}
                        <button
                            onClick={addThreat}
                            className="w-full flex items-center justify-center gap-2 p-3 border-2 border-dashed border-divider rounded-lg text-text-secondary hover:text-primary-600 hover:border-primary-300 transition-colors"
                        >
                            <Add className="w-5 h-5" /> Add Threat
                        </button>
                    </div>
                )}

                {activeTab === 'mitigations' && (
                    <div className="space-y-4">
                        {model.mitigations.length === 0 ? (
                            <div className="text-center py-8 text-text-secondary">
                                <p className="text-sm">No mitigations yet</p>
                                <button
                                    onClick={() => addMitigation()}
                                    className="mt-2 text-sm text-primary-600 hover:text-primary-700"
                                >
                                    Add first mitigation
                                </button>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {model.mitigations.map((mit, idx) => (
                                    <div key={idx} className="border border-divider rounded-lg bg-bg-paper p-3 space-y-2">
                                        <div className="flex items-start gap-2">
                                            <input
                                                type="text"
                                                value={mit.threat}
                                                onChange={(e) => updateMitigation(idx, { threat: e.target.value })}
                                                placeholder="Related threat"
                                                className="flex-1 px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                            />
                                            <select
                                                value={mit.status}
                                                onChange={(e) => updateMitigation(idx, { status: e.target.value as 'planned' | 'implemented' | 'verified' })}
                                                className={`px-2 py-1 text-xs rounded ${mit.status === 'verified'
                                                        ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
                                                        : mit.status === 'implemented'
                                                            ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
                                                            : 'bg-grey-100 text-grey-700 dark:bg-grey-800 dark:text-grey-300'
                                                    }`}
                                            >
                                                <option value="planned">Planned</option>
                                                <option value="implemented">Implemented</option>
                                                <option value="verified">Verified</option>
                                            </select>
                                            <button
                                                onClick={() => removeMitigation(idx)}
                                                className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                            >
                                                <Remove className="w-4 h-4" />
                                            </button>
                                        </div>
                                        <textarea
                                            value={mit.control}
                                            onChange={(e) => updateMitigation(idx, { control: e.target.value })}
                                            placeholder="Describe the security control..."
                                            rows={2}
                                            className="w-full px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none"
                                        />
                                    </div>
                                ))}
                            </div>
                        )}
                        <button
                            onClick={() => addMitigation()}
                            className="w-full flex items-center justify-center gap-2 p-3 border-2 border-dashed border-divider rounded-lg text-text-secondary hover:text-primary-600 hover:border-primary-300 transition-colors"
                        >
                            <Add className="w-5 h-5" /> Add Mitigation
                        </button>

                        {/* Residual Risk */}
                        <div className="pt-4 border-t border-divider">
                            <label className="block text-sm font-medium text-text-primary mb-1">
                                Residual Risk Assessment
                            </label>
                            <textarea
                                value={model.residualRisk}
                                onChange={(e) => setModel((prev) => ({ ...prev, residualRisk: e.target.value }))}
                                placeholder="Describe remaining risks after mitigations are applied..."
                                rows={3}
                                className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                            />
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

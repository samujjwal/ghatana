/**
 * UX Spec Panel Component
 *
 * Editor for UX specifications including flows, accessibility, and content notes.
 *
 * @doc.type component
 * @doc.purpose SHAPE phase UX specification editor
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useCallback } from 'react';
import { Plus as Add, Minus as Remove, Sparkles as AutoAwesome, Save, Accessibility, Route as RouteOutlined, FileText as Description, AlertTriangle as WarningAmber } from 'lucide-react';
import type { UxSpecPayload } from '@/shared/types/lifecycle-artifacts';

export interface UxSpecPanelProps {
    data?: UxSpecPayload;
    onSave: (data: UxSpecPayload) => Promise<void>;
    onAIAssist?: (context: { uxSpec?: UxSpecPayload }) => Promise<Partial<UxSpecPayload> | null>;
    onClose: () => void;
    isLoading?: boolean;
}

interface Flow {
    name: string;
    steps: string[];
    notes?: string;
}

const defaultFlow = (): Flow => ({
    name: '',
    steps: [''],
    notes: '',
});

const defaultData: UxSpecPayload = {
    primaryFlows: [defaultFlow()],
    iaNotes: '',
    a11yNotes: '',
    contentNotes: '',
    edgeCases: [''],
};

/**
 * UX Spec Panel for SHAPE phase.
 */
export const UxSpecPanel: React.FC<UxSpecPanelProps> = ({
    data,
    onSave,
    onAIAssist,
    onClose,
    isLoading = false,
}) => {
    const [uxSpec, setUxSpec] = useState<UxSpecPayload>({
        ...defaultData,
        ...data,
        primaryFlows: data?.primaryFlows?.length ? data.primaryFlows : defaultData.primaryFlows,
        edgeCases: data?.edgeCases?.length ? data.edgeCases : defaultData.edgeCases,
    });
    const [isSaving, setIsSaving] = useState(false);
    const [isAILoading, setIsAILoading] = useState(false);
    const [activeTab, setActiveTab] = useState<'flows' | 'a11y' | 'content'>('flows');

    const updateField = useCallback(<K extends keyof UxSpecPayload>(field: K, value: UxSpecPayload[K]) => {
        setUxSpec((prev) => ({ ...prev, [field]: value }));
    }, []);

    const updateFlow = useCallback((index: number, updates: Partial<Flow>) => {
        setUxSpec((prev) => ({
            ...prev,
            primaryFlows: prev.primaryFlows.map((f, i) => (i === index ? { ...f, ...updates } : f)),
        }));
    }, []);

    const addFlow = useCallback(() => {
        setUxSpec((prev) => ({
            ...prev,
            primaryFlows: [...prev.primaryFlows, defaultFlow()],
        }));
    }, []);

    const removeFlow = useCallback((index: number) => {
        setUxSpec((prev) => ({
            ...prev,
            primaryFlows: prev.primaryFlows.filter((_, i) => i !== index),
        }));
    }, []);

    const updateFlowStep = useCallback((flowIndex: number, stepIndex: number, value: string) => {
        setUxSpec((prev) => ({
            ...prev,
            primaryFlows: prev.primaryFlows.map((flow, i) => {
                if (i !== flowIndex) return flow;
                const steps = [...flow.steps];
                steps[stepIndex] = value;
                return { ...flow, steps };
            }),
        }));
    }, []);

    const addFlowStep = useCallback((flowIndex: number) => {
        setUxSpec((prev) => ({
            ...prev,
            primaryFlows: prev.primaryFlows.map((flow, i) => {
                if (i !== flowIndex) return flow;
                return { ...flow, steps: [...flow.steps, ''] };
            }),
        }));
    }, []);

    const removeFlowStep = useCallback((flowIndex: number, stepIndex: number) => {
        setUxSpec((prev) => ({
            ...prev,
            primaryFlows: prev.primaryFlows.map((flow, i) => {
                if (i !== flowIndex) return flow;
                return { ...flow, steps: flow.steps.filter((_, j) => j !== stepIndex) };
            }),
        }));
    }, []);

    const updateEdgeCase = useCallback((index: number, value: string) => {
        setUxSpec((prev) => {
            const edgeCases = [...prev.edgeCases];
            edgeCases[index] = value;
            return { ...prev, edgeCases };
        });
    }, []);

    const addEdgeCase = useCallback(() => {
        setUxSpec((prev) => ({
            ...prev,
            edgeCases: [...prev.edgeCases, ''],
        }));
    }, []);

    const removeEdgeCase = useCallback((index: number) => {
        setUxSpec((prev) => ({
            ...prev,
            edgeCases: prev.edgeCases.filter((_, i) => i !== index),
        }));
    }, []);

    const handleAIAssist = useCallback(async () => {
        if (!onAIAssist) return;
        setIsAILoading(true);
        try {
            const result = await onAIAssist({ uxSpec });
            if (result) {
                setUxSpec((prev) => ({
                    ...prev,
                    ...result,
                    primaryFlows: result.primaryFlows?.length ? result.primaryFlows : prev.primaryFlows,
                    edgeCases: result.edgeCases?.length ? result.edgeCases : prev.edgeCases,
                }));
            }
        } finally {
            setIsAILoading(false);
        }
    }, [onAIAssist, uxSpec]);

    const handleSave = useCallback(async () => {
        setIsSaving(true);
        try {
            const cleanSpec: UxSpecPayload = {
                ...uxSpec,
                primaryFlows: uxSpec.primaryFlows
                    .filter((f) => f.name.trim())
                    .map((f) => ({
                        ...f,
                        steps: f.steps.filter((s) => s.trim()),
                    })),
                edgeCases: uxSpec.edgeCases.filter((e) => e.trim()),
            };
            await onSave(cleanSpec);
        } finally {
            setIsSaving(false);
        }
    }, [uxSpec, onSave]);

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-divider">
                <div>
                    <h3 className="font-semibold text-text-primary">UX Specification</h3>
                    <p className="text-xs text-text-secondary">
                        User flows, accessibility, and content guidelines
                    </p>
                </div>
                <div className="flex gap-2">
                    {onAIAssist && (
                        <button
                            onClick={handleAIAssist}
                            disabled={isAILoading || isSaving}
                            className="flex items-center gap-1 px-3 py-1.5 text-sm font-medium text-text-secondary border border-transparent hover:border-divider hover:text-text-primary hover:bg-grey-50 dark:hover:bg-grey-800/40 rounded-lg transition-colors disabled:opacity-50"
                        >
                            <AutoAwesome className="w-4 h-4" />
                            {isAILoading ? 'Analyzing...' : 'AI Critique'}
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
                    onClick={() => setActiveTab('flows')}
                    className={`flex items-center gap-1.5 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'flows'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    <RouteOutlined className="w-4 h-4" />
                    Flows
                </button>
                <button
                    onClick={() => setActiveTab('a11y')}
                    className={`flex items-center gap-1.5 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'a11y'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    <Accessibility className="w-4 h-4" />
                    Accessibility
                </button>
                <button
                    onClick={() => setActiveTab('content')}
                    className={`flex items-center gap-1.5 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'content'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    <Description className="w-4 h-4" />
                    Content
                </button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-4">
                {activeTab === 'flows' && (
                    <div className="space-y-4">
                        {uxSpec.primaryFlows.map((flow, flowIdx) => (
                            <div key={flowIdx} className="border border-divider rounded-lg bg-bg-paper">
                                <div className="flex items-center gap-2 p-3 border-b border-divider">
                                    <input
                                        type="text"
                                        value={flow.name}
                                        onChange={(e) => updateFlow(flowIdx, { name: e.target.value })}
                                        placeholder="Flow name (e.g., User Registration)"
                                        className="flex-1 px-2 py-1 text-sm font-medium border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                    />
                                    {uxSpec.primaryFlows.length > 1 && (
                                        <button
                                            onClick={() => removeFlow(flowIdx)}
                                            className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                            aria-label="Remove flow"
                                        >
                                            <Remove className="w-4 h-4" />
                                        </button>
                                    )}
                                </div>
                                <div className="p-3 space-y-2">
                                    {flow.steps.map((step, stepIdx) => (
                                        <div key={stepIdx} className="flex items-center gap-2">
                                            <span className="w-6 h-6 flex items-center justify-center text-xs font-medium bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300 rounded-full">
                                                {stepIdx + 1}
                                            </span>
                                            <input
                                                type="text"
                                                value={step}
                                                onChange={(e) => updateFlowStep(flowIdx, stepIdx, e.target.value)}
                                                placeholder="Step description"
                                                className="flex-1 px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                            />
                                            {flow.steps.length > 1 && (
                                                <button
                                                    onClick={() => removeFlowStep(flowIdx, stepIdx)}
                                                    className="p-0.5 text-text-secondary hover:text-error-color transition-colors"
                                                >
                                                    <Remove className="w-4 h-4" />
                                                </button>
                                            )}
                                        </div>
                                    ))}
                                    <button
                                        onClick={() => addFlowStep(flowIdx)}
                                        className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors ml-8"
                                    >
                                        <Add className="w-3 h-3" /> Add step
                                    </button>
                                    <textarea
                                        value={flow.notes || ''}
                                        onChange={(e) => updateFlow(flowIdx, { notes: e.target.value })}
                                        placeholder="Additional notes for this flow..."
                                        rows={2}
                                        className="w-full px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none mt-2"
                                    />
                                </div>
                            </div>
                        ))}
                        <button
                            onClick={addFlow}
                            className="w-full flex items-center justify-center gap-2 p-3 border-2 border-dashed border-divider rounded-lg text-text-secondary hover:text-primary-600 hover:border-primary-300 transition-colors"
                        >
                            <Add className="w-5 h-5" /> Add Flow
                        </button>

                        {/* Edge Cases */}
                        <div className="pt-4 border-t border-divider">
                            <h4 className="text-sm font-semibold text-text-primary mb-3">Edge Cases</h4>
                            <div className="space-y-2">
                                {uxSpec.edgeCases.map((edgeCase, idx) => (
                                    <div key={idx} className="flex items-center gap-2">
                                        <WarningAmber className="w-4 h-4 text-warning-600" />
                                        <input
                                            type="text"
                                            value={edgeCase}
                                            onChange={(e) => updateEdgeCase(idx, e.target.value)}
                                            placeholder="Edge case scenario"
                                            className="flex-1 px-2 py-1 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                        />
                                        {uxSpec.edgeCases.length > 1 && (
                                            <button
                                                onClick={() => removeEdgeCase(idx)}
                                                className="p-0.5 text-text-secondary hover:text-error-color transition-colors"
                                            >
                                                <Remove className="w-4 h-4" />
                                            </button>
                                        )}
                                    </div>
                                ))}
                                <button
                                    onClick={addEdgeCase}
                                    className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                                >
                                    <Add className="w-3 h-3" /> Add edge case
                                </button>
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === 'a11y' && (
                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">
                                Accessibility Notes (WCAG)
                            </label>
                            <textarea
                                value={uxSpec.a11yNotes}
                                onChange={(e) => updateField('a11yNotes', e.target.value)}
                                placeholder="Document accessibility considerations, keyboard navigation, screen reader support, color contrast requirements..."
                                rows={8}
                                className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                            />
                        </div>
                        <div className="p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
                            <h4 className="text-sm font-medium text-blue-700 dark:text-blue-300 mb-2">
                                WCAG Quick Reference
                            </h4>
                            <ul className="text-xs text-blue-600 dark:text-blue-400 space-y-1">
                                <li>• Perceivable: Text alternatives, captions, adaptable content</li>
                                <li>• Operable: Keyboard accessible, enough time, navigable</li>
                                <li>• Understandable: Readable, predictable, input assistance</li>
                                <li>• Robust: Compatible with assistive technologies</li>
                            </ul>
                        </div>
                    </div>
                )}

                {activeTab === 'content' && (
                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">
                                IA Notes (Information Architecture)
                            </label>
                            <textarea
                                value={uxSpec.iaNotes}
                                onChange={(e) => updateField('iaNotes', e.target.value)}
                                placeholder="Navigation structure, page hierarchy, content organization..."
                                rows={5}
                                className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">
                                Content Notes
                            </label>
                            <textarea
                                value={uxSpec.contentNotes}
                                onChange={(e) => updateField('contentNotes', e.target.value)}
                                placeholder="Tone of voice, microcopy guidelines, error messages, empty states..."
                                rows={5}
                                className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                            />
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

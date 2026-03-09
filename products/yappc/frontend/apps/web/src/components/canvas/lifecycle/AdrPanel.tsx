/**
 * ADR Panel Component
 *
 * Architecture Decision Record editor with options comparison.
 * Supports AI-assisted pros/cons generation.
 *
 * @doc.type component
 * @doc.purpose SHAPE phase ADR editor
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useCallback } from 'react';
import { Plus as Add, Minus as Remove, Sparkles as AutoAwesome, Save, ThumbsUp as ThumbUp, ThumbsDown as ThumbDown } from 'lucide-react';
import type { AdrPayload } from '@/shared/types/lifecycle-artifacts';

export interface AdrPanelProps {
    data?: AdrPayload;
    onSave: (data: AdrPayload) => Promise<void>;
    onAIAssist?: (context: { adr?: AdrPayload }) => Promise<Partial<AdrPayload> | null>;
    onClose: () => void;
    isLoading?: boolean;
}

interface Option {
    name: string;
    pros: string[];
    cons: string[];
}

const STATUS_COLORS = {
    proposed: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
    accepted: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
    superseded: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
    deprecated: 'bg-grey-100 text-grey-700 dark:bg-grey-900/30 dark:text-grey-300',
};

const defaultOption = (): Option => ({
    name: '',
    pros: [''],
    cons: [''],
});

const defaultData: AdrPayload = {
    context: '',
    decision: '',
    options: [defaultOption()],
    consequences: '',
    status: 'proposed',
};

/**
 * ADR Panel for SHAPE phase.
 */
export const AdrPanel: React.FC<AdrPanelProps> = ({
    data,
    onSave,
    onAIAssist,
    onClose,
    isLoading = false,
}) => {
    const [adr, setAdr] = useState<AdrPayload>({
        ...defaultData,
        ...data,
        options: data?.options?.length ? data.options : defaultData.options,
    });
    const [isSaving, setIsSaving] = useState(false);
    const [isAILoading, setIsAILoading] = useState(false);

    const updateField = useCallback(<K extends keyof AdrPayload>(field: K, value: AdrPayload[K]) => {
        setAdr((prev) => ({ ...prev, [field]: value }));
    }, []);

    const updateOption = useCallback((index: number, updates: Partial<Option>) => {
        setAdr((prev) => ({
            ...prev,
            options: prev.options.map((opt, i) => (i === index ? { ...opt, ...updates } : opt)),
        }));
    }, []);

    const addOption = useCallback(() => {
        setAdr((prev) => ({
            ...prev,
            options: [...prev.options, defaultOption()],
        }));
    }, []);

    const removeOption = useCallback((index: number) => {
        setAdr((prev) => ({
            ...prev,
            options: prev.options.filter((_, i) => i !== index),
        }));
    }, []);

    const updateProCon = useCallback((
        optionIndex: number,
        type: 'pros' | 'cons',
        itemIndex: number,
        value: string
    ) => {
        setAdr((prev) => ({
            ...prev,
            options: prev.options.map((opt, i) => {
                if (i !== optionIndex) return opt;
                const items = [...opt[type]];
                items[itemIndex] = value;
                return { ...opt, [type]: items };
            }),
        }));
    }, []);

    const addProCon = useCallback((optionIndex: number, type: 'pros' | 'cons') => {
        setAdr((prev) => ({
            ...prev,
            options: prev.options.map((opt, i) => {
                if (i !== optionIndex) return opt;
                return { ...opt, [type]: [...opt[type], ''] };
            }),
        }));
    }, []);

    const removeProCon = useCallback((optionIndex: number, type: 'pros' | 'cons', itemIndex: number) => {
        setAdr((prev) => ({
            ...prev,
            options: prev.options.map((opt, i) => {
                if (i !== optionIndex) return opt;
                return { ...opt, [type]: opt[type].filter((_, j) => j !== itemIndex) };
            }),
        }));
    }, []);

    const handleAIAssist = useCallback(async () => {
        if (!onAIAssist) return;
        setIsAILoading(true);
        try {
            const result = await onAIAssist({ adr });
            if (result) {
                setAdr((prev) => ({
                    ...prev,
                    ...result,
                    options: result.options?.length ? result.options : prev.options,
                }));
            }
        } finally {
            setIsAILoading(false);
        }
    }, [onAIAssist, adr]);

    const handleSave = useCallback(async () => {
        setIsSaving(true);
        try {
            // Clean up empty items
            const cleanAdr: AdrPayload = {
                ...adr,
                options: adr.options
                    .filter((opt) => opt.name.trim())
                    .map((opt) => ({
                        ...opt,
                        pros: opt.pros.filter((p) => p.trim()),
                        cons: opt.cons.filter((c) => c.trim()),
                    })),
            };
            await onSave(cleanAdr);
        } finally {
            setIsSaving(false);
        }
    }, [adr, onSave]);

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-divider">
                <div>
                    <h3 className="font-semibold text-text-primary">Architecture Decision Record</h3>
                    <p className="text-xs text-text-secondary">
                        Document key architectural decisions
                    </p>
                </div>
                <div className="flex gap-2">
                    {onAIAssist && (
                        <button
                            onClick={handleAIAssist}
                            disabled={isAILoading || isSaving}
                            className="flex items-center gap-1 px-3 py-1.5 text-sm text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded-lg transition-colors disabled:opacity-50"
                        >
                            <AutoAwesome className="w-4 h-4" />
                            {isAILoading ? 'Analyzing...' : 'AI Assist'}
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

            {/* Content */}
            <div className="flex-1 overflow-auto p-4 space-y-6">
                {/* Status */}
                <div>
                    <label className="block text-sm font-medium text-text-primary mb-2">Status</label>
                    <div className="flex gap-2">
                        {(['proposed', 'accepted', 'superseded', 'deprecated'] as const).map((status) => (
                            <button
                                key={status}
                                onClick={() => updateField('status', status)}
                                className={`px-3 py-1.5 text-sm rounded-full transition-colors ${adr.status === status
                                        ? STATUS_COLORS[status]
                                        : 'bg-grey-100 text-grey-600 dark:bg-grey-800 dark:text-grey-400 hover:bg-grey-200 dark:hover:bg-grey-700'
                                    }`}
                            >
                                {status.charAt(0).toUpperCase() + status.slice(1)}
                            </button>
                        ))}
                    </div>
                </div>

                {/* Context */}
                <div>
                    <label
                        htmlFor="adr-context"
                        className="block text-sm font-medium text-text-primary mb-1"
                    >
                        Context
                    </label>
                    <textarea
                        id="adr-context"
                        value={adr.context}
                        onChange={(e) => updateField('context', e.target.value)}
                        placeholder="What is the issue that we're seeing that is motivating this decision?"
                        rows={4}
                        className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                    />
                </div>

                {/* Options */}
                <div>
                    <label className="block text-sm font-medium text-text-primary mb-2">
                        Options Considered
                    </label>
                    <div className="space-y-4">
                        {adr.options.map((option, optIdx) => (
                            <div key={optIdx} className="border border-divider rounded-lg bg-bg-paper">
                                <div className="flex items-center gap-2 p-3 border-b border-divider">
                                    <span className="text-sm font-medium text-text-secondary">
                                        Option {optIdx + 1}
                                    </span>
                                    <input
                                        type="text"
                                        value={option.name}
                                        onChange={(e) => updateOption(optIdx, { name: e.target.value })}
                                        placeholder="Option name"
                                        className="flex-1 px-2 py-1 border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                    />
                                    {adr.options.length > 1 && (
                                        <button
                                            onClick={() => removeOption(optIdx)}
                                            className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                            aria-label="Remove option"
                                        >
                                            <Remove className="w-4 h-4" />
                                        </button>
                                    )}
                                </div>
                                <div className="grid grid-cols-2 divide-x divide-divider">
                                    {/* Pros */}
                                    <div className="p-3 space-y-2">
                                        <div className="flex items-center gap-1 text-sm font-medium text-success-600 dark:text-success-400">
                                            <ThumbUp className="w-4 h-4" />
                                            Pros
                                        </div>
                                        {option.pros.map((pro, proIdx) => (
                                            <div key={proIdx} className="flex items-center gap-1">
                                                <input
                                                    type="text"
                                                    value={pro}
                                                    onChange={(e) =>
                                                        updateProCon(optIdx, 'pros', proIdx, e.target.value)
                                                    }
                                                    placeholder="Pro..."
                                                    className="flex-1 px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                />
                                                {option.pros.length > 1 && (
                                                    <button
                                                        onClick={() => removeProCon(optIdx, 'pros', proIdx)}
                                                        className="p-0.5 text-text-secondary hover:text-error-color transition-colors"
                                                    >
                                                        <Remove className="w-3 h-3" />
                                                    </button>
                                                )}
                                            </div>
                                        ))}
                                        <button
                                            onClick={() => addProCon(optIdx, 'pros')}
                                            className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                                        >
                                            <Add className="w-3 h-3" /> Add pro
                                        </button>
                                    </div>
                                    {/* Cons */}
                                    <div className="p-3 space-y-2">
                                        <div className="flex items-center gap-1 text-sm font-medium text-error-color">
                                            <ThumbDown className="w-4 h-4" />
                                            Cons
                                        </div>
                                        {option.cons.map((con, conIdx) => (
                                            <div key={conIdx} className="flex items-center gap-1">
                                                <input
                                                    type="text"
                                                    value={con}
                                                    onChange={(e) =>
                                                        updateProCon(optIdx, 'cons', conIdx, e.target.value)
                                                    }
                                                    placeholder="Con..."
                                                    className="flex-1 px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                />
                                                {option.cons.length > 1 && (
                                                    <button
                                                        onClick={() => removeProCon(optIdx, 'cons', conIdx)}
                                                        className="p-0.5 text-text-secondary hover:text-error-color transition-colors"
                                                    >
                                                        <Remove className="w-3 h-3" />
                                                    </button>
                                                )}
                                            </div>
                                        ))}
                                        <button
                                            onClick={() => addProCon(optIdx, 'cons')}
                                            className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                                        >
                                            <Add className="w-3 h-3" /> Add con
                                        </button>
                                    </div>
                                </div>
                            </div>
                        ))}
                        <button
                            onClick={addOption}
                            className="w-full flex items-center justify-center gap-2 p-3 border-2 border-dashed border-divider rounded-lg text-text-secondary hover:text-primary-600 hover:border-primary-300 transition-colors"
                        >
                            <Add className="w-5 h-5" /> Add Option
                        </button>
                    </div>
                </div>

                {/* Decision */}
                <div>
                    <label
                        htmlFor="adr-decision"
                        className="block text-sm font-medium text-text-primary mb-1"
                    >
                        Decision
                    </label>
                    <textarea
                        id="adr-decision"
                        value={adr.decision}
                        onChange={(e) => updateField('decision', e.target.value)}
                        placeholder="What is the change that we're proposing and/or doing?"
                        rows={4}
                        className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                    />
                </div>

                {/* Consequences */}
                <div>
                    <label
                        htmlFor="adr-consequences"
                        className="block text-sm font-medium text-text-primary mb-1"
                    >
                        Consequences
                    </label>
                    <textarea
                        id="adr-consequences"
                        value={adr.consequences}
                        onChange={(e) => updateField('consequences', e.target.value)}
                        placeholder="What becomes easier or more difficult to do because of this change?"
                        rows={4}
                        className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                    />
                </div>
            </div>
        </div>
    );
};

/**
 * Research Pack Editor Component
 *
 * Structured editor for capturing research findings and user insights.
 * Supports file uploads, AI summarization, and source management.
 *
 * @doc.type component
 * @doc.purpose INTENT phase research capture
 * @doc.layer product
 * @doc.pattern Form Component
 */

import React, { useState, useCallback } from 'react';
import { Sparkles as AutoAwesome, Plus as Add, Minus as Remove, Paperclip as AttachFile, Link } from 'lucide-react';
import type { ResearchPackPayload } from '@/shared/types/lifecycle-artifacts';

export interface ResearchPackEditorProps {
    initialData?: Partial<ResearchPackPayload>;
    onSubmit: (data: ResearchPackPayload) => Promise<void>;
    onAIAssist?: () => Promise<Partial<ResearchPackPayload> | null>;
    onCancel?: () => void;
    isSubmitting?: boolean;
}

interface Source {
    name: string;
    url?: string;
    type: string;
}

const defaultData: ResearchPackPayload = {
    sources: [{ name: '', url: '', type: 'article' }],
    marketNotes: '',
    userInsights: [''],
    risks: [''],
    openQuestions: [''],
};

const sourceTypes = ['article', 'interview', 'survey', 'competitor', 'report', 'other'];

/**
 * Research Pack Editor for INTENT phase.
 */
export const ResearchPackEditor: React.FC<ResearchPackEditorProps> = ({
    initialData,
    onSubmit,
    onAIAssist,
    onCancel,
    isSubmitting = false,
}) => {
    const [data, setData] = useState<ResearchPackPayload>({
        ...defaultData,
        ...initialData,
        sources: initialData?.sources?.length ? initialData.sources : defaultData.sources,
        userInsights: initialData?.userInsights?.length ? initialData.userInsights : defaultData.userInsights,
        risks: initialData?.risks?.length ? initialData.risks : defaultData.risks,
        openQuestions: initialData?.openQuestions?.length ? initialData.openQuestions : defaultData.openQuestions,
    });
    const [isAILoading, setIsAILoading] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({});

    const updateField = useCallback(<K extends keyof ResearchPackPayload>(
        field: K,
        value: ResearchPackPayload[K]
    ) => {
        setData((prev) => ({ ...prev, [field]: value }));
        setErrors((prev) => ({ ...prev, [field]: '' }));
    }, []);

    const updateSource = useCallback((index: number, updates: Partial<Source>) => {
        setData((prev) => {
            const sources = [...prev.sources];
            sources[index] = { ...sources[index], ...updates };
            return { ...prev, sources };
        });
    }, []);

    const addSource = useCallback(() => {
        setData((prev) => ({
            ...prev,
            sources: [...prev.sources, { name: '', url: '', type: 'article' }],
        }));
    }, []);

    const removeSource = useCallback((index: number) => {
        setData((prev) => ({
            ...prev,
            sources: prev.sources.filter((_, i) => i !== index),
        }));
    }, []);

    const updateArrayItem = useCallback((
        field: 'userInsights' | 'risks' | 'openQuestions',
        index: number,
        value: string
    ) => {
        setData((prev) => {
            const arr = [...prev[field]];
            arr[index] = value;
            return { ...prev, [field]: arr };
        });
    }, []);

    const addArrayItem = useCallback((field: 'userInsights' | 'risks' | 'openQuestions') => {
        setData((prev) => ({
            ...prev,
            [field]: [...prev[field], ''],
        }));
    }, []);

    const removeArrayItem = useCallback((
        field: 'userInsights' | 'risks' | 'openQuestions',
        index: number
    ) => {
        setData((prev) => ({
            ...prev,
            [field]: prev[field].filter((_, i) => i !== index),
        }));
    }, []);

    const handleAIAssist = useCallback(async () => {
        if (!onAIAssist) return;
        setIsAILoading(true);
        try {
            const suggestions = await onAIAssist();
            if (suggestions) {
                setData((prev) => ({
                    ...prev,
                    ...suggestions,
                    sources: suggestions.sources?.length ? suggestions.sources : prev.sources,
                    userInsights: suggestions.userInsights?.length ? suggestions.userInsights : prev.userInsights,
                    risks: suggestions.risks?.length ? suggestions.risks : prev.risks,
                    openQuestions: suggestions.openQuestions?.length ? suggestions.openQuestions : prev.openQuestions,
                }));
            }
        } finally {
            setIsAILoading(false);
        }
    }, [onAIAssist]);

    const validate = useCallback((): boolean => {
        const newErrors: Record<string, string> = {};

        const validSources = data.sources.filter((s) => s.name.trim());
        if (validSources.length === 0) {
            newErrors.sources = 'At least one research source is required';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    }, [data]);

    const handleSubmit = useCallback(async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validate()) return;

        const cleanData: ResearchPackPayload = {
            ...data,
            sources: data.sources.filter((s) => s.name.trim()),
            userInsights: data.userInsights.filter((i) => i.trim()),
            risks: data.risks.filter((r) => r.trim()),
            openQuestions: data.openQuestions.filter((q) => q.trim()),
        };

        await onSubmit(cleanData);
    }, [data, validate, onSubmit]);

    return (
        <form onSubmit={handleSubmit} className="space-y-6">
            {/* Sources Section */}
            <div>
                <label className="block text-sm font-medium text-text-primary mb-2">
                    Research Sources *
                </label>
                <div className="space-y-3">
                    {data.sources.map((source, index) => (
                        <div key={index} className="p-3 border border-divider rounded-lg bg-bg-paper space-y-2">
                            <div className="flex gap-2">
                                <input
                                    type="text"
                                    value={source.name}
                                    onChange={(e) => updateSource(index, { name: e.target.value })}
                                    placeholder="Source name"
                                    className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                    disabled={isSubmitting}
                                />
                                <select
                                    value={source.type}
                                    onChange={(e) => updateSource(index, { type: e.target.value })}
                                    className="px-3 py-2 border border-divider rounded-lg bg-bg-default text-text-primary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                    disabled={isSubmitting}
                                >
                                    {sourceTypes.map((type) => (
                                        <option key={type} value={type}>
                                            {type.charAt(0).toUpperCase() + type.slice(1)}
                                        </option>
                                    ))}
                                </select>
                                {data.sources.length > 1 && (
                                    <button
                                        type="button"
                                        onClick={() => removeSource(index)}
                                        className="p-2 text-text-secondary hover:text-error-color transition-colors"
                                        aria-label="Remove source"
                                        disabled={isSubmitting}
                                    >
                                        <Remove className="w-5 h-5" />
                                    </button>
                                )}
                            </div>
                            <div className="flex gap-2">
                                <Link className="w-5 h-5 text-text-secondary mt-2" />
                                <input
                                    type="url"
                                    value={source.url || ''}
                                    onChange={(e) => updateSource(index, { url: e.target.value })}
                                    placeholder="URL (optional)"
                                    className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                    disabled={isSubmitting}
                                />
                            </div>
                        </div>
                    ))}
                    <button
                        type="button"
                        onClick={addSource}
                        className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                        disabled={isSubmitting}
                    >
                        <Add className="w-4 h-4" /> Add source
                    </button>
                </div>
                {errors.sources && (
                    <p className="text-sm text-error-color mt-1">{errors.sources}</p>
                )}
            </div>

            {/* Market Notes */}
            <div>
                <label
                    htmlFor="research-market"
                    className="block text-sm font-medium text-text-primary mb-1"
                >
                    Market Notes
                </label>
                <textarea
                    id="research-market"
                    value={data.marketNotes}
                    onChange={(e) => updateField('marketNotes', e.target.value)}
                    placeholder="Key market observations, trends, and competitive landscape..."
                    rows={4}
                    className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                    disabled={isSubmitting}
                />
            </div>

            {/* User Insights */}
            <div>
                <label className="block text-sm font-medium text-text-primary mb-1">
                    User Insights
                </label>
                <div className="space-y-2">
                    {data.userInsights.map((insight, index) => (
                        <div key={index} className="flex gap-2">
                            <input
                                type="text"
                                value={insight}
                                onChange={(e) => updateArrayItem('userInsights', index, e.target.value)}
                                placeholder="Key user insight or finding"
                                className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                disabled={isSubmitting}
                            />
                            {data.userInsights.length > 1 && (
                                <button
                                    type="button"
                                    onClick={() => removeArrayItem('userInsights', index)}
                                    className="p-2 text-text-secondary hover:text-error-color transition-colors"
                                    aria-label="Remove insight"
                                    disabled={isSubmitting}
                                >
                                    <Remove className="w-5 h-5" />
                                </button>
                            )}
                        </div>
                    ))}
                    <button
                        type="button"
                        onClick={() => addArrayItem('userInsights')}
                        className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                        disabled={isSubmitting}
                    >
                        <Add className="w-4 h-4" /> Add insight
                    </button>
                </div>
            </div>

            {/* Risks */}
            <div>
                <label className="block text-sm font-medium text-text-primary mb-1">
                    Identified Risks
                </label>
                <div className="space-y-2">
                    {data.risks.map((risk, index) => (
                        <div key={index} className="flex gap-2">
                            <input
                                type="text"
                                value={risk}
                                onChange={(e) => updateArrayItem('risks', index, e.target.value)}
                                placeholder="Potential risk or concern"
                                className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                disabled={isSubmitting}
                            />
                            {data.risks.length > 1 && (
                                <button
                                    type="button"
                                    onClick={() => removeArrayItem('risks', index)}
                                    className="p-2 text-text-secondary hover:text-error-color transition-colors"
                                    aria-label="Remove risk"
                                    disabled={isSubmitting}
                                >
                                    <Remove className="w-5 h-5" />
                                </button>
                            )}
                        </div>
                    ))}
                    <button
                        type="button"
                        onClick={() => addArrayItem('risks')}
                        className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                        disabled={isSubmitting}
                    >
                        <Add className="w-4 h-4" /> Add risk
                    </button>
                </div>
            </div>

            {/* Open Questions */}
            <div>
                <label className="block text-sm font-medium text-text-primary mb-1">
                    Open Questions
                </label>
                <div className="space-y-2">
                    {data.openQuestions.map((question, index) => (
                        <div key={index} className="flex gap-2">
                            <input
                                type="text"
                                value={question}
                                onChange={(e) => updateArrayItem('openQuestions', index, e.target.value)}
                                placeholder="Question that needs further investigation"
                                className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                disabled={isSubmitting}
                            />
                            {data.openQuestions.length > 1 && (
                                <button
                                    type="button"
                                    onClick={() => removeArrayItem('openQuestions', index)}
                                    className="p-2 text-text-secondary hover:text-error-color transition-colors"
                                    aria-label="Remove question"
                                    disabled={isSubmitting}
                                >
                                    <Remove className="w-5 h-5" />
                                </button>
                            )}
                        </div>
                    ))}
                    <button
                        type="button"
                        onClick={() => addArrayItem('openQuestions')}
                        className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                        disabled={isSubmitting}
                    >
                        <Add className="w-4 h-4" /> Add question
                    </button>
                </div>
            </div>

            {/* Actions */}
            <div className="flex items-center justify-between pt-4 border-t border-divider">
                {onAIAssist && (
                    <button
                        type="button"
                        onClick={handleAIAssist}
                        disabled={isSubmitting || isAILoading}
                        className="flex items-center gap-2 px-4 py-2 text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded-lg transition-colors disabled:opacity-50"
                    >
                        <AutoAwesome className="w-5 h-5" />
                        {isAILoading ? 'Analyzing...' : 'AI Analysis'}
                    </button>
                )}
                <div className="flex gap-3 ml-auto">
                    {onCancel && (
                        <button
                            type="button"
                            onClick={onCancel}
                            disabled={isSubmitting}
                            className="px-4 py-2 text-text-secondary hover:text-text-primary transition-colors"
                        >
                            Cancel
                        </button>
                    )}
                    <button
                        type="submit"
                        disabled={isSubmitting}
                        className="px-6 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50"
                    >
                        {isSubmitting ? 'Saving...' : 'Save Research Pack'}
                    </button>
                </div>
            </div>
        </form>
    );
};

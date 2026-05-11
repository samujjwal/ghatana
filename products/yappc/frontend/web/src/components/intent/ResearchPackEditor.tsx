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
import { Sparkles as AutoAwesome, Plus as Add, Minus as Remove, Link } from 'lucide-react';
import type { ResearchPackPayload } from '@/shared/types/lifecycle-artifacts';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { Select } from '../ui/Select';
import { Textarea } from '../ui/Textarea';
import { useTranslation } from '@ghatana/i18n';

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

interface ResearchPackFormData extends Omit<ResearchPackPayload, 'sources'> {
    sources: Source[];
}

const defaultData: ResearchPackFormData = {
    sources: [{ name: '', url: '', type: 'article' }],
    marketNotes: '',
    userInsights: [''],
    risks: [''],
    openQuestions: [''],
};

const sourceTypes = ['article', 'interview', 'survey', 'competitor', 'report', 'other'];

const toSourceFormData = (source: string | Source): Source => {
    if (typeof source !== 'string') {
        return source;
    }

    return { name: source, url: '', type: 'article' };
};

const toSourcePayload = (source: Source): string => {
    const name = source.name.trim();
    const type = source.type.trim();
    const url = source.url?.trim();

    if (url) {
        return `${name} (${type}) - ${url}`;
    }

    return type ? `${name} (${type})` : name;
};

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
    const { t } = useTranslation('common');
    const [data, setData] = useState<ResearchPackFormData>({
        ...defaultData,
        ...initialData,
        sources: initialData?.sources?.length ? initialData.sources.map(toSourceFormData) : defaultData.sources,
        userInsights: initialData?.userInsights?.length ? initialData.userInsights : defaultData.userInsights,
        risks: initialData?.risks?.length ? initialData.risks : defaultData.risks,
        openQuestions: initialData?.openQuestions?.length ? initialData.openQuestions : defaultData.openQuestions,
    });
    const [isAILoading, setIsAILoading] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({});

    const updateField = useCallback(<K extends keyof ResearchPackFormData>(
        field: K,
        value: ResearchPackFormData[K]
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
                    sources: suggestions.sources?.length ? suggestions.sources.map(toSourceFormData) : prev.sources,
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
            sources: data.sources.filter((s) => s.name.trim()).map(toSourcePayload),
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
                                <Input
                                    type="text"
                                    value={source.name}
                                    onChange={(e) => updateSource(index, { name: e.target.value })}
                                    placeholder={t('intent.research.sourceNamePlaceholder')}
                                    className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                    disabled={isSubmitting}
                                />
                                <Select
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
                                </Select>
                                {data.sources.length > 1 && (
                                    <Button
                                        type="button"
                                        onClick={() => removeSource(index)}
                                        variant="ghost"
                                        size="small"
                                        className="p-2 text-text-secondary hover:text-error-color transition-colors"
                                        aria-label={t('intent.research.removeSource')}
                                        disabled={isSubmitting}
                                    >
                                        <Remove className="w-5 h-5" />
                                    </Button>
                                )}
                            </div>
                            <div className="flex gap-2">
                                <Link className="w-5 h-5 text-text-secondary mt-2" />
                                <Input
                                    type="url"
                                    value={source.url || ''}
                                    onChange={(e) => updateSource(index, { url: e.target.value })}
                                    placeholder={t('intent.research.sourceUrlPlaceholder')}
                                    className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                    disabled={isSubmitting}
                                />
                            </div>
                        </div>
                    ))}
                    <Button
                        type="button"
                        onClick={addSource}
                        variant="ghost"
                        size="small"
                        className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                        disabled={isSubmitting}
                    >
                        <Add className="w-4 h-4" /> Add source
                    </Button>
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
                <Textarea
                    id="research-market"
                    value={data.marketNotes}
                    onChange={(e) => updateField('marketNotes', e.target.value)}
                    placeholder={t('intent.research.marketNotesPlaceholder')}
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
                            <Input
                                type="text"
                                value={insight}
                                onChange={(e) => updateArrayItem('userInsights', index, e.target.value)}
                                placeholder={t('intent.research.userInsightPlaceholder')}
                                className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                disabled={isSubmitting}
                            />
                            {data.userInsights.length > 1 && (
                                <Button
                                    type="button"
                                    onClick={() => removeArrayItem('userInsights', index)}
                                    variant="ghost"
                                    size="small"
                                    className="p-2 text-text-secondary hover:text-error-color transition-colors"
                                    aria-label={t('intent.research.removeInsight')}
                                    disabled={isSubmitting}
                                >
                                    <Remove className="w-5 h-5" />
                                </Button>
                            )}
                        </div>
                    ))}
                    <Button
                        type="button"
                        onClick={() => addArrayItem('userInsights')}
                        variant="ghost"
                        size="small"
                        className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                        disabled={isSubmitting}
                    >
                        <Add className="w-4 h-4" /> Add insight
                    </Button>
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
                            <Input
                                type="text"
                                value={risk}
                                onChange={(e) => updateArrayItem('risks', index, e.target.value)}
                                placeholder={t('intent.research.riskPlaceholder')}
                                className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                disabled={isSubmitting}
                            />
                            {data.risks.length > 1 && (
                                <Button
                                    type="button"
                                    onClick={() => removeArrayItem('risks', index)}
                                    variant="ghost"
                                    size="small"
                                    className="p-2 text-text-secondary hover:text-error-color transition-colors"
                                    aria-label={t('intent.research.removeRisk')}
                                    disabled={isSubmitting}
                                >
                                    <Remove className="w-5 h-5" />
                                </Button>
                            )}
                        </div>
                    ))}
                    <Button
                        type="button"
                        onClick={() => addArrayItem('risks')}
                        variant="ghost"
                        size="small"
                        className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                        disabled={isSubmitting}
                    >
                        <Add className="w-4 h-4" /> Add risk
                    </Button>
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
                            <Input
                                type="text"
                                value={question}
                                onChange={(e) => updateArrayItem('openQuestions', index, e.target.value)}
                                placeholder={t('intent.research.openQuestionPlaceholder')}
                                className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                disabled={isSubmitting}
                            />
                            {data.openQuestions.length > 1 && (
                                <Button
                                    type="button"
                                    onClick={() => removeArrayItem('openQuestions', index)}
                                    variant="ghost"
                                    size="small"
                                    className="p-2 text-text-secondary hover:text-error-color transition-colors"
                                    aria-label={t('intent.research.removeQuestion')}
                                    disabled={isSubmitting}
                                >
                                    <Remove className="w-5 h-5" />
                                </Button>
                            )}
                        </div>
                    ))}
                    <Button
                        type="button"
                        onClick={() => addArrayItem('openQuestions')}
                        variant="ghost"
                        size="small"
                        className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                        disabled={isSubmitting}
                    >
                        <Add className="w-4 h-4" /> Add question
                    </Button>
                </div>
            </div>

            {/* Actions */}
            <div className="flex items-center justify-between pt-4 border-t border-divider">
                {onAIAssist && (
                    <Button
                        type="button"
                        onClick={handleAIAssist}
                        disabled={isSubmitting || isAILoading}
                        variant="ghost"
                        className="flex items-center gap-2 px-4 py-2 text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded-lg transition-colors disabled:opacity-50"
                    >
                        <AutoAwesome className="w-5 h-5" />
                        {isAILoading ? 'Analyzing...' : 'Guided Analysis'}
                    </Button>
                )}
                <div className="flex gap-3 ml-auto">
                    {onCancel && (
                        <Button
                            type="button"
                            onClick={onCancel}
                            disabled={isSubmitting}
                            variant="ghost"
                            className="px-4 py-2 text-text-secondary hover:text-text-primary transition-colors"
                        >
                            Cancel
                        </Button>
                    )}
                    <Button
                        type="submit"
                        disabled={isSubmitting}
                        variant="solid"
                        className="px-6 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50"
                    >
                        {isSubmitting ? 'Saving...' : 'Save Research Pack'}
                    </Button>
                </div>
            </div>
        </form>
    );
};

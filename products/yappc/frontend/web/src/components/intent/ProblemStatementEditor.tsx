/**
 * Problem Statement Editor Component
 *
 * Structured editor for capturing the refined problem statement.
 * Includes success metrics builder and feasibility indicators.
 *
 * @doc.type component
 * @doc.purpose INTENT phase problem definition
 * @doc.layer product
 * @doc.pattern Form Component
 */

import React, { useState, useCallback } from 'react';
import { Sparkles as AutoAwesome, Plus as Add, Minus as Remove, Check } from 'lucide-react';
import type { ProblemStatementPayload } from '@/shared/types/lifecycle-artifacts';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';

export interface ProblemStatementEditorProps {
    initialData?: Partial<ProblemStatementPayload>;
    onSubmit: (data: ProblemStatementPayload) => Promise<void>;
    onAIAssist?: () => Promise<Partial<ProblemStatementPayload> | null>;
    onCancel?: () => void;
    isSubmitting?: boolean;
}

interface SuccessMetric {
    name: string;
    target: string;
    current?: string;
}

interface ProblemStatementFormData extends Omit<ProblemStatementPayload, 'successMetrics'> {
    successMetrics: SuccessMetric[];
}

const defaultData: ProblemStatementFormData = {
    problem: '',
    who: '',
    when: '',
    whyNow: '',
    successMetrics: [{ name: '', target: '', current: '' }],
    nonGoals: [''],
};

const toMetricFormData = (metric: string | SuccessMetric): SuccessMetric => {
    if (typeof metric !== 'string') {
        return metric;
    }

    return { name: metric, target: '', current: '' };
};

const toMetricPayload = (metric: SuccessMetric): string => {
    const current = metric.current?.trim();
    return current
        ? `${metric.name.trim()}: ${metric.target.trim()} (current: ${current})`
        : `${metric.name.trim()}: ${metric.target.trim()}`;
};

/**
 * Problem Statement Editor for INTENT phase.
 */
export const ProblemStatementEditor: React.FC<ProblemStatementEditorProps> = ({
    initialData,
    onSubmit,
    onAIAssist,
    onCancel,
    isSubmitting = false,
}) => {
    const [data, setData] = useState<ProblemStatementFormData>({
        ...defaultData,
        ...initialData,
        successMetrics: initialData?.successMetrics?.length
            ? initialData.successMetrics.map(toMetricFormData)
            : defaultData.successMetrics,
        nonGoals: initialData?.nonGoals?.length ? initialData.nonGoals : defaultData.nonGoals,
    });
    const [isAILoading, setIsAILoading] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({});

    const updateField = useCallback(<K extends keyof ProblemStatementFormData>(
        field: K,
        value: ProblemStatementFormData[K]
    ) => {
        setData((prev) => ({ ...prev, [field]: value }));
        setErrors((prev) => ({ ...prev, [field]: '' }));
    }, []);

    const updateMetric = useCallback((index: number, updates: Partial<SuccessMetric>) => {
        setData((prev) => {
            const metrics = [...prev.successMetrics];
            metrics[index] = { ...metrics[index], ...updates };
            return { ...prev, successMetrics: metrics };
        });
    }, []);

    const addMetric = useCallback(() => {
        setData((prev) => ({
            ...prev,
            successMetrics: [...prev.successMetrics, { name: '', target: '', current: '' }],
        }));
    }, []);

    const removeMetric = useCallback((index: number) => {
        setData((prev) => ({
            ...prev,
            successMetrics: prev.successMetrics.filter((_, i) => i !== index),
        }));
    }, []);

    const updateNonGoal = useCallback((index: number, value: string) => {
        setData((prev) => {
            const nonGoals = [...prev.nonGoals];
            nonGoals[index] = value;
            return { ...prev, nonGoals };
        });
    }, []);

    const addNonGoal = useCallback(() => {
        setData((prev) => ({
            ...prev,
            nonGoals: [...prev.nonGoals, ''],
        }));
    }, []);

    const removeNonGoal = useCallback((index: number) => {
        setData((prev) => ({
            ...prev,
            nonGoals: prev.nonGoals.filter((_, i) => i !== index),
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
                    successMetrics: suggestions.successMetrics?.length
                        ? suggestions.successMetrics.map(toMetricFormData)
                        : prev.successMetrics,
                    nonGoals: suggestions.nonGoals?.length ? suggestions.nonGoals : prev.nonGoals,
                }));
            }
        } finally {
            setIsAILoading(false);
        }
    }, [onAIAssist]);

    const validate = useCallback((): boolean => {
        const newErrors: Record<string, string> = {};

        if (!data.problem.trim()) {
            newErrors.problem = 'Problem statement is required';
        }
        if (!data.who.trim()) {
            newErrors.who = 'Target audience is required';
        }
        if (!data.whyNow.trim()) {
            newErrors.whyNow = 'Urgency explanation is required';
        }
        const validMetrics = data.successMetrics.filter((m) => m.name.trim() && m.target.trim());
        if (validMetrics.length === 0) {
            newErrors.successMetrics = 'At least one success metric is required';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    }, [data]);

    const handleSubmit = useCallback(async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validate()) return;

        const cleanData: ProblemStatementPayload = {
            ...data,
            successMetrics: data.successMetrics
                .filter((m) => m.name.trim() && m.target.trim())
                .map(toMetricPayload),
            nonGoals: data.nonGoals.filter((g) => g.trim()),
        };

        await onSubmit(cleanData);
    }, [data, validate, onSubmit]);

    return (
        <form onSubmit={handleSubmit} className="space-y-6">
            {/* Problem Statement */}
            <div>
                <label
                    htmlFor="problem-statement"
                    className="block text-sm font-medium text-text-primary mb-1"
                >
                    Problem Statement *
                </label>
                <Textarea
                    id="problem-statement"
                    value={data.problem}
                    onChange={(e) => updateField('problem', e.target.value)}
                    placeholder="What is the core problem we are trying to solve?"
                    rows={4}
                    className={`w-full px-3 py-2 border rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none ${errors.problem ? 'border-error-color' : 'border-divider'
                        }`}
                    disabled={isSubmitting}
                />
                {errors.problem && (
                    <p className="text-sm text-error-color mt-1">{errors.problem}</p>
                )}
            </div>

            {/* Who */}
            <div>
                <label
                    htmlFor="problem-who"
                    className="block text-sm font-medium text-text-primary mb-1"
                >
                    Who experiences this problem? *
                </label>
                <Input
                    id="problem-who"
                    type="text"
                    value={data.who}
                    onChange={(e) => updateField('who', e.target.value)}
                    placeholder="e.g., Small business owners who manage remote teams"
                    className={`w-full px-3 py-2 border rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 ${errors.who ? 'border-error-color' : 'border-divider'
                        }`}
                    disabled={isSubmitting}
                />
                {errors.who && (
                    <p className="text-sm text-error-color mt-1">{errors.who}</p>
                )}
            </div>

            {/* When */}
            <div>
                <label
                    htmlFor="problem-when"
                    className="block text-sm font-medium text-text-primary mb-1"
                >
                    When does this problem occur?
                </label>
                <Input
                    id="problem-when"
                    type="text"
                    value={data.when}
                    onChange={(e) => updateField('when', e.target.value)}
                    placeholder="e.g., During quarterly planning cycles"
                    className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                    disabled={isSubmitting}
                />
            </div>

            {/* Why Now */}
            <div>
                <label
                    htmlFor="problem-whynow"
                    className="block text-sm font-medium text-text-primary mb-1"
                >
                    Why solve this now? *
                </label>
                <Textarea
                    id="problem-whynow"
                    value={data.whyNow}
                    onChange={(e) => updateField('whyNow', e.target.value)}
                    placeholder="What makes this problem urgent or timely?"
                    rows={3}
                    className={`w-full px-3 py-2 border rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none ${errors.whyNow ? 'border-error-color' : 'border-divider'
                        }`}
                    disabled={isSubmitting}
                />
                {errors.whyNow && (
                    <p className="text-sm text-error-color mt-1">{errors.whyNow}</p>
                )}
            </div>

            {/* Success Metrics */}
            <div>
                <label className="block text-sm font-medium text-text-primary mb-2">
                    Success Metrics (SMART) *
                </label>
                <div className="space-y-3">
                    {data.successMetrics.map((metric, index) => (
                        <div
                            key={index}
                            className="p-3 border border-divider rounded-lg bg-bg-paper space-y-2"
                        >
                            <div className="flex gap-2">
                                <Input
                                    type="text"
                                    value={metric.name}
                                    onChange={(e) => updateMetric(index, { name: e.target.value })}
                                    placeholder="Metric name (e.g., Time to complete task)"
                                    className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                    disabled={isSubmitting}
                                />
                                {data.successMetrics.length > 1 && (
                                    <Button
                                        type="button"
                                        onClick={() => removeMetric(index)}
                                        variant="ghost"
                                        size="small"
                                        className="p-2 text-text-secondary hover:text-error-color transition-colors"
                                        aria-label="Remove metric"
                                        disabled={isSubmitting}
                                    >
                                        <Remove className="w-5 h-5" />
                                    </Button>
                                )}
                            </div>
                            <div className="grid grid-cols-2 gap-2">
                                <div>
                                    <label className="text-xs text-text-secondary">Target</label>
                                    <Input
                                        type="text"
                                        value={metric.target}
                                        onChange={(e) => updateMetric(index, { target: e.target.value })}
                                        placeholder="e.g., < 5 minutes"
                                        className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                        disabled={isSubmitting}
                                    />
                                </div>
                                <div>
                                    <label className="text-xs text-text-secondary">Current (optional)</label>
                                    <Input
                                        type="text"
                                        value={metric.current || ''}
                                        onChange={(e) => updateMetric(index, { current: e.target.value })}
                                        placeholder="e.g., 30 minutes"
                                        className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                        disabled={isSubmitting}
                                    />
                                </div>
                            </div>
                        </div>
                    ))}
                    <Button
                        type="button"
                        onClick={addMetric}
                        variant="ghost"
                        size="small"
                        className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                        disabled={isSubmitting}
                    >
                        <Add className="w-4 h-4" /> Add metric
                    </Button>
                </div>
                {errors.successMetrics && (
                    <p className="text-sm text-error-color mt-1">{errors.successMetrics}</p>
                )}
            </div>

            {/* Non-Goals */}
            <div>
                <label className="block text-sm font-medium text-text-primary mb-1">
                    Non-Goals (what we're NOT solving)
                </label>
                <div className="space-y-2">
                    {data.nonGoals.map((nonGoal, index) => (
                        <div key={index} className="flex gap-2">
                            <Input
                                type="text"
                                value={nonGoal}
                                onChange={(e) => updateNonGoal(index, e.target.value)}
                                placeholder="e.g., We are not building a mobile app in v1"
                                className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                disabled={isSubmitting}
                            />
                            {data.nonGoals.length > 1 && (
                                <Button
                                    type="button"
                                    onClick={() => removeNonGoal(index)}
                                    variant="ghost"
                                    size="small"
                                    className="p-2 text-text-secondary hover:text-error-color transition-colors"
                                    aria-label="Remove non-goal"
                                    disabled={isSubmitting}
                                >
                                    <Remove className="w-5 h-5" />
                                </Button>
                            )}
                        </div>
                    ))}
                    <Button
                        type="button"
                        onClick={addNonGoal}
                        variant="ghost"
                        size="small"
                        className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                        disabled={isSubmitting}
                    >
                        <Add className="w-4 h-4" /> Add non-goal
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
                        {isAILoading ? 'Synthesizing...' : 'AI Synthesis'}
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
                        className="px-6 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50 flex items-center gap-2"
                    >
                        <Check className="w-5 h-5" />
                        {isSubmitting ? 'Saving...' : 'Save Problem Statement'}
                    </Button>
                </div>
            </div>
        </form>
    );
};

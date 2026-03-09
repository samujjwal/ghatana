/**
 * Idea Brief Form Component
 *
 * Structured form for capturing the initial idea brief artifact.
 * Supports AI-assisted suggestions and triage decisions.
 *
 * @doc.type component
 * @doc.purpose INTENT phase idea capture
 * @doc.layer product
 * @doc.pattern Form Component
 */

import React, { useState, useCallback } from 'react';
import { Sparkles as AutoAwesome, Plus as Add, Minus as Remove } from 'lucide-react';
import type { IdeaBriefPayload } from '@/shared/types/lifecycle-artifacts';

export interface IdeaBriefFormProps {
    initialData?: Partial<IdeaBriefPayload>;
    onSubmit: (data: IdeaBriefPayload) => Promise<void>;
    onAIAssist?: () => Promise<Partial<IdeaBriefPayload> | null>;
    onCancel?: () => void;
    isSubmitting?: boolean;
}

const defaultData: IdeaBriefPayload = {
    title: '',
    oneLiner: '',
    targetUsers: [''],
    businessValue: '',
    constraints: [''],
    assumptions: [''],
};

/**
 * Idea Brief Form for INTENT phase.
 */
export const IdeaBriefForm: React.FC<IdeaBriefFormProps> = ({
    initialData,
    onSubmit,
    onAIAssist,
    onCancel,
    isSubmitting = false,
}) => {
    const [data, setData] = useState<IdeaBriefPayload>({
        ...defaultData,
        ...initialData,
    });
    const [isAILoading, setIsAILoading] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({});

    const updateField = useCallback(<K extends keyof IdeaBriefPayload>(
        field: K,
        value: IdeaBriefPayload[K]
    ) => {
        setData((prev) => ({ ...prev, [field]: value }));
        setErrors((prev) => ({ ...prev, [field]: '' }));
    }, []);

    const updateArrayItem = useCallback((
        field: 'targetUsers' | 'constraints' | 'assumptions',
        index: number,
        value: string
    ) => {
        setData((prev) => {
            const arr = [...prev[field]];
            arr[index] = value;
            return { ...prev, [field]: arr };
        });
    }, []);

    const addArrayItem = useCallback((field: 'targetUsers' | 'constraints' | 'assumptions') => {
        setData((prev) => ({
            ...prev,
            [field]: [...prev[field], ''],
        }));
    }, []);

    const removeArrayItem = useCallback((
        field: 'targetUsers' | 'constraints' | 'assumptions',
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
                    targetUsers: suggestions.targetUsers?.length
                        ? suggestions.targetUsers
                        : prev.targetUsers,
                    constraints: suggestions.constraints?.length
                        ? suggestions.constraints
                        : prev.constraints,
                    assumptions: suggestions.assumptions?.length
                        ? suggestions.assumptions
                        : prev.assumptions,
                }));
            }
        } finally {
            setIsAILoading(false);
        }
    }, [onAIAssist]);

    const validate = useCallback((): boolean => {
        const newErrors: Record<string, string> = {};

        if (!data.title.trim()) {
            newErrors.title = 'Title is required';
        }
        if (!data.oneLiner.trim()) {
            newErrors.oneLiner = 'One-liner description is required';
        }
        if (!data.businessValue.trim()) {
            newErrors.businessValue = 'Business value is required';
        }
        if (data.targetUsers.filter((u) => u.trim()).length === 0) {
            newErrors.targetUsers = 'At least one target user is required';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    }, [data]);

    const handleSubmit = useCallback(async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validate()) return;

        // Clean up empty array items before submit
        const cleanData: IdeaBriefPayload = {
            ...data,
            targetUsers: data.targetUsers.filter((u) => u.trim()),
            constraints: data.constraints.filter((c) => c.trim()),
            assumptions: data.assumptions.filter((a) => a.trim()),
        };

        await onSubmit(cleanData);
    }, [data, validate, onSubmit]);

    return (
        <form onSubmit={handleSubmit} className="space-y-6">
            {/* Title */}
            <div>
                <label
                    htmlFor="idea-title"
                    className="block text-sm font-medium text-text-primary mb-1"
                >
                    Idea Title *
                </label>
                <input
                    id="idea-title"
                    type="text"
                    value={data.title}
                    onChange={(e) => updateField('title', e.target.value)}
                    placeholder="e.g., Smart Task Manager"
                    className={`w-full px-3 py-2 border rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 ${errors.title ? 'border-error-color' : 'border-divider'
                        }`}
                    disabled={isSubmitting}
                />
                {errors.title && (
                    <p className="text-sm text-error-color mt-1">{errors.title}</p>
                )}
            </div>

            {/* One-liner */}
            <div>
                <label
                    htmlFor="idea-oneliner"
                    className="block text-sm font-medium text-text-primary mb-1"
                >
                    One-liner Description *
                </label>
                <input
                    id="idea-oneliner"
                    type="text"
                    value={data.oneLiner}
                    onChange={(e) => updateField('oneLiner', e.target.value)}
                    placeholder="A brief description of what this idea solves"
                    className={`w-full px-3 py-2 border rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 ${errors.oneLiner ? 'border-error-color' : 'border-divider'
                        }`}
                    disabled={isSubmitting}
                />
                {errors.oneLiner && (
                    <p className="text-sm text-error-color mt-1">{errors.oneLiner}</p>
                )}
            </div>

            {/* Target Users */}
            <div>
                <label className="block text-sm font-medium text-text-primary mb-1">
                    Target Users *
                </label>
                <div className="space-y-2">
                    {data.targetUsers.map((user, index) => (
                        <div key={index} className="flex gap-2">
                            <input
                                type="text"
                                value={user}
                                onChange={(e) => updateArrayItem('targetUsers', index, e.target.value)}
                                placeholder="e.g., Product managers, Developers"
                                className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                disabled={isSubmitting}
                            />
                            {data.targetUsers.length > 1 && (
                                <button
                                    type="button"
                                    onClick={() => removeArrayItem('targetUsers', index)}
                                    className="p-2 text-text-secondary hover:text-error-color transition-colors"
                                    aria-label="Remove target user"
                                    disabled={isSubmitting}
                                >
                                    <Remove className="w-5 h-5" />
                                </button>
                            )}
                        </div>
                    ))}
                    <button
                        type="button"
                        onClick={() => addArrayItem('targetUsers')}
                        className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                        disabled={isSubmitting}
                    >
                        <Add className="w-4 h-4" /> Add target user
                    </button>
                </div>
                {errors.targetUsers && (
                    <p className="text-sm text-error-color mt-1">{errors.targetUsers}</p>
                )}
            </div>

            {/* Business Value */}
            <div>
                <label
                    htmlFor="idea-value"
                    className="block text-sm font-medium text-text-primary mb-1"
                >
                    Business Value *
                </label>
                <textarea
                    id="idea-value"
                    value={data.businessValue}
                    onChange={(e) => updateField('businessValue', e.target.value)}
                    placeholder="What value does this bring to users and the business?"
                    rows={3}
                    className={`w-full px-3 py-2 border rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none ${errors.businessValue ? 'border-error-color' : 'border-divider'
                        }`}
                    disabled={isSubmitting}
                />
                {errors.businessValue && (
                    <p className="text-sm text-error-color mt-1">{errors.businessValue}</p>
                )}
            </div>

            {/* Constraints */}
            <div>
                <label className="block text-sm font-medium text-text-primary mb-1">
                    Constraints (optional)
                </label>
                <div className="space-y-2">
                    {data.constraints.map((constraint, index) => (
                        <div key={index} className="flex gap-2">
                            <input
                                type="text"
                                value={constraint}
                                onChange={(e) => updateArrayItem('constraints', index, e.target.value)}
                                placeholder="e.g., Must work offline, Budget < $10k"
                                className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                disabled={isSubmitting}
                            />
                            {data.constraints.length > 1 && (
                                <button
                                    type="button"
                                    onClick={() => removeArrayItem('constraints', index)}
                                    className="p-2 text-text-secondary hover:text-error-color transition-colors"
                                    aria-label="Remove constraint"
                                    disabled={isSubmitting}
                                >
                                    <Remove className="w-5 h-5" />
                                </button>
                            )}
                        </div>
                    ))}
                    <button
                        type="button"
                        onClick={() => addArrayItem('constraints')}
                        className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                        disabled={isSubmitting}
                    >
                        <Add className="w-4 h-4" /> Add constraint
                    </button>
                </div>
            </div>

            {/* Assumptions */}
            <div>
                <label className="block text-sm font-medium text-text-primary mb-1">
                    Assumptions (optional)
                </label>
                <div className="space-y-2">
                    {data.assumptions.map((assumption, index) => (
                        <div key={index} className="flex gap-2">
                            <input
                                type="text"
                                value={assumption}
                                onChange={(e) => updateArrayItem('assumptions', index, e.target.value)}
                                placeholder="e.g., Users have internet access"
                                className="flex-1 px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-primary-500"
                                disabled={isSubmitting}
                            />
                            {data.assumptions.length > 1 && (
                                <button
                                    type="button"
                                    onClick={() => removeArrayItem('assumptions', index)}
                                    className="p-2 text-text-secondary hover:text-error-color transition-colors"
                                    aria-label="Remove assumption"
                                    disabled={isSubmitting}
                                >
                                    <Remove className="w-5 h-5" />
                                </button>
                            )}
                        </div>
                    ))}
                    <button
                        type="button"
                        onClick={() => addArrayItem('assumptions')}
                        className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                        disabled={isSubmitting}
                    >
                        <Add className="w-4 h-4" /> Add assumption
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
                        {isAILoading ? 'Getting suggestions...' : 'AI Suggestions'}
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
                        {isSubmitting ? 'Saving...' : 'Save Idea Brief'}
                    </button>
                </div>
            </div>
        </form>
    );
};

/**
 * Improve Panel Component
 *
 * Enhancement backlog and learning record editor for IMPROVE phase.
 *
 * @doc.type component
 * @doc.purpose IMPROVE phase enhancement and learning editor
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useCallback } from 'react';
import { Plus as Add, Minus as Remove, Sparkles as AutoAwesome, Save, Lightbulb, GraduationCap as School, TrendingUp, AlertCircle as ErrorOutline, MessageCircle as Feedback, BarChart3 as BarChart, Users as Group, Bot as SmartToy } from 'lucide-react';
import type { EnhancementBacklogPayload, LearningRecordPayload } from '@/shared/types/lifecycle-artifacts';
import { Button } from '../../ui/Button';
import { Input } from '../../ui/Input';
import { Select } from '../../ui/Select';
import { Textarea } from '../../ui/Textarea';

export interface ImprovePanelProps {
    enhancements?: EnhancementBacklogPayload;
    learnings?: LearningRecordPayload;
    onSave: (data: { enhancements: EnhancementBacklogPayload; learnings: LearningRecordPayload }) => Promise<void>;
    onAIAssist?: (context: {
        enhancements?: EnhancementBacklogPayload;
        learnings?: LearningRecordPayload;
    }) => Promise<Partial<{ enhancements: EnhancementBacklogPayload; learnings: LearningRecordPayload }> | null>;
    onClose: () => void;
    isLoading?: boolean;
}

type EnhancementStatus = 'proposed' | 'approved' | 'in_progress' | 'completed' | 'declined';
type EnhancementSource = 'incident' | 'feedback' | 'metrics' | 'team' | 'ai_suggestion';

const STATUS_COLORS: Record<EnhancementStatus, string> = {
    proposed: 'bg-surface-muted text-fg-muted dark:bg-surface-muted dark:text-fg-muted',
    approved: 'bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color',
    in_progress: 'bg-warning-bg text-warning-color dark:bg-warning-bg/30 dark:text-warning-color',
    completed: 'bg-success-bg text-success-color dark:bg-success-bg/30 dark:text-success-color',
    declined: 'bg-destructive-bg text-destructive dark:bg-destructive-bg/30 dark:text-destructive',
};

const SOURCE_ICONS: Record<EnhancementSource, React.ReactNode> = {
    incident: <ErrorOutline size={16} />,
    feedback: <Feedback size={16} />,
    metrics: <BarChart size={16} />,
    team: <Group size={16} />,
    ai_suggestion: <SmartToy size={16} />,
};

const STATUS_OPTIONS = [
    { value: 'proposed', label: 'Proposed' },
    { value: 'approved', label: 'Approved' },
    { value: 'in_progress', label: 'In Progress' },
    { value: 'completed', label: 'Completed' },
    { value: 'declined', label: 'Declined' },
];

const SOURCE_OPTIONS = Object.entries(SOURCE_ICONS).map(([value]) => ({
    value,
    label: value.charAt(0).toUpperCase() + value.slice(1).replace('_', ' '),
}));

const PRIORITY_OPTIONS = [
    { value: 'low', label: 'Low Priority' },
    { value: 'medium', label: 'Medium Priority' },
    { value: 'high', label: 'High Priority' },
];

const defaultEnhancements: EnhancementBacklogPayload = {
    items: [],
};

const defaultLearnings: LearningRecordPayload = {
    retrospectives: [],
    insights: [],
    recommendations: [],
};

/**
 * Improve Panel for IMPROVE phase with enhancement backlog and learning record.
 */
export const ImprovePanel: React.FC<ImprovePanelProps> = ({
    enhancements,
    learnings,
    onSave,
    onAIAssist,
    onClose,
    isLoading = false,
}) => {
    const [enhancementData, setEnhancementData] = useState<EnhancementBacklogPayload>({
        ...defaultEnhancements,
        ...enhancements,
        items: enhancements?.items || [],
    });
    const [learningData, setLearningData] = useState<LearningRecordPayload>({
        ...defaultLearnings,
        ...learnings,
        retrospectives: learnings?.retrospectives || [],
        insights: learnings?.insights || [],
        recommendations: learnings?.recommendations || [],
    });
    const [isSaving, setIsSaving] = useState(false);
    const [isAILoading, setIsAILoading] = useState(false);
    const [activeTab, setActiveTab] = useState<'enhancements' | 'learnings'>('enhancements');

    // Enhancement operations
    const addEnhancement = useCallback(() => {
        setEnhancementData((prev) => ({
            ...prev,
            items: [
                ...prev.items,
                {
                    title: '',
                    description: '',
                    source: 'team' as EnhancementSource,
                    status: 'proposed' as EnhancementStatus,
                    priority: 'medium',
                },
            ],
        }));
    }, []);

    const updateEnhancement = useCallback((index: number, updates: Partial<EnhancementBacklogPayload['items'][0]>) => {
        setEnhancementData((prev) => ({
            ...prev,
            items: prev.items.map((item, i) => (i === index ? { ...item, ...updates } : item)),
        }));
    }, []);

    const removeEnhancement = useCallback((index: number) => {
        setEnhancementData((prev) => ({
            ...prev,
            items: prev.items.filter((_, i) => i !== index),
        }));
    }, []);

    // Retrospective operations
    const addRetrospective = useCallback(() => {
        setLearningData((prev) => ({
            ...prev,
            retrospectives: [
                ...prev.retrospectives,
                {
                    date: new Date().toISOString().split('T')[0],
                    wentWell: [],
                    improvements: [],
                    actions: [],
                },
            ],
        }));
    }, []);

    const updateRetrospective = useCallback((index: number, updates: Partial<LearningRecordPayload['retrospectives'][0]>) => {
        setLearningData((prev) => ({
            ...prev,
            retrospectives: prev.retrospectives.map((r, i) => (i === index ? { ...r, ...updates } : r)),
        }));
    }, []);

    const removeRetrospective = useCallback((index: number) => {
        setLearningData((prev) => ({
            ...prev,
            retrospectives: prev.retrospectives.filter((_, i) => i !== index),
        }));
    }, []);

    // Insights operations
    const addInsight = useCallback(() => {
        setLearningData((prev) => ({
            ...prev,
            insights: [...prev.insights, ''],
        }));
    }, []);

    const updateInsight = useCallback((index: number, value: string) => {
        setLearningData((prev) => ({
            ...prev,
            insights: prev.insights.map((i, idx) => (idx === index ? value : i)),
        }));
    }, []);

    const removeInsight = useCallback((index: number) => {
        setLearningData((prev) => ({
            ...prev,
            insights: prev.insights.filter((_, i) => i !== index),
        }));
    }, []);

    // Recommendation operations
    const addRecommendation = useCallback(() => {
        setLearningData((prev) => ({
            ...prev,
            recommendations: [...prev.recommendations, ''],
        }));
    }, []);

    const updateRecommendation = useCallback((index: number, value: string) => {
        setLearningData((prev) => ({
            ...prev,
            recommendations: prev.recommendations.map((r, idx) => (idx === index ? value : r)),
        }));
    }, []);

    const removeRecommendation = useCallback((index: number) => {
        setLearningData((prev) => ({
            ...prev,
            recommendations: prev.recommendations.filter((_, i) => i !== index),
        }));
    }, []);

    const handleAIAssist = useCallback(async () => {
        if (!onAIAssist) return;
        setIsAILoading(true);
        try {
            const result = await onAIAssist({
                enhancements: enhancementData,
                learnings: learningData,
            });
            if (result) {
                if (result.enhancements) {
                    setEnhancementData((prev) => ({
                        ...prev,
                        ...result.enhancements,
                        items: result.enhancements?.items?.length ? result.enhancements.items : prev.items,
                    }));
                }
                if (result.learnings) {
                    setLearningData((prev) => ({
                        ...prev,
                        ...result.learnings,
                    }));
                }
            }
        } finally {
            setIsAILoading(false);
        }
    }, [onAIAssist, enhancementData, learningData]);

    const handleSave = useCallback(async () => {
        setIsSaving(true);
        try {
            const cleanEnhancements: EnhancementBacklogPayload = {
                ...enhancementData,
                items: enhancementData.items.filter((item) => item.title.trim()),
            };
            const cleanLearnings: LearningRecordPayload = {
                ...learningData,
                insights: learningData.insights.filter((i) => i.trim()),
                recommendations: learningData.recommendations.filter((r) => r.trim()),
            };
            await onSave({ enhancements: cleanEnhancements, learnings: cleanLearnings });
        } finally {
            setIsSaving(false);
        }
    }, [enhancementData, learningData, onSave]);

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-divider">
                <div className="flex items-center gap-2">
                    <TrendingUp className="w-5 h-5 text-info-color" />
                    <div>
                        <h3 className="font-semibold text-text-primary">Improve</h3>
                        <p className="text-xs text-text-secondary">Enhancements & Learnings</p>
                    </div>
                </div>
                <div className="flex gap-2">
                    {onAIAssist && (
                        <Button
                            onClick={handleAIAssist}
                            disabled={isAILoading || isSaving}
                            variant="ghost"
                            size="sm"
                            className="flex items-center gap-1 px-3 py-1.5 text-sm text-info-color hover:bg-info-bg dark:hover:bg-info-bg/20 rounded-lg transition-colors disabled:opacity-50"
                        >
                            <AutoAwesome className="w-4 h-4" />
                            {isAILoading ? 'Analyzing...' : 'AI Assist'}
                        </Button>
                    )}
                    <Button
                        onClick={handleSave}
                        disabled={isSaving || isLoading}
                        variant="solid"
                        size="sm"
                        className="flex items-center gap-1 px-3 py-1.5 text-sm bg-info-color text-white rounded-lg hover:bg-info-color/90 transition-colors disabled:opacity-50"
                    >
                        <Save className="w-4 h-4" />
                        {isSaving ? 'Saving...' : 'Save'}
                    </Button>
                </div>
            </div>

            {/* Tabs */}
            <div className="flex border-b border-divider">
                <Button
                    onClick={() => setActiveTab('enhancements')}
                    variant="ghost"
                    size="sm"
                    className={`flex items-center gap-1 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'enhancements'
                            ? 'border-info-border text-info-color'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    <Lightbulb className="w-4 h-4" />
                    Enhancements ({enhancementData.items.length})
                </Button>
                <Button
                    onClick={() => setActiveTab('learnings')}
                    variant="ghost"
                    size="sm"
                    className={`flex items-center gap-1 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'learnings'
                            ? 'border-info-border text-info-color'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    <School className="w-4 h-4" />
                    Learnings
                </Button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-4">
                {activeTab === 'enhancements' && (
                    <div className="space-y-4">
                        {enhancementData.items.length === 0 ? (
                            <div className="text-center py-8 text-text-secondary">
                                <Lightbulb className="w-12 h-12 mx-auto mb-2 opacity-50" />
                                <p className="text-sm">No enhancements yet</p>
                                <Button
                                    onClick={addEnhancement}
                                    variant="link"
                                    size="sm"
                                    className="mt-2 text-sm text-info-color hover:text-info-color"
                                >
                                    Add first enhancement
                                </Button>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {enhancementData.items.map((item, idx) => (
                                    <div key={idx} className="border border-divider rounded-lg bg-bg-paper p-3 space-y-2">
                                        <div className="flex items-start gap-2">
                                            <Input
                                                type="text"
                                                value={item.title}
                                                onChange={(e) => updateEnhancement(idx, { title: e.target.value })}
                                                placeholder="Enhancement title"
                                                fullWidth
                                                size="sm"
                                                className="flex-1 px-2 py-1 text-sm font-medium border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-info-border"
                                            />
                                            <Select
                                                value={item.status}
                                                onChange={(e) => updateEnhancement(idx, { status: e.target.value as EnhancementStatus })}
                                                options={STATUS_OPTIONS}
                                                size="sm"
                                                className={`px-2 py-1 text-xs rounded ${STATUS_COLORS[item.status]}`}
                                            />
                                            <Button
                                                onClick={() => removeEnhancement(idx)}
                                                variant="ghost"
                                                size="sm"
                                                aria-label={`Remove enhancement ${idx + 1}`}
                                                className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                            >
                                                <Remove className="w-4 h-4" />
                                            </Button>
                                        </div>
                                        <Textarea
                                            value={item.description}
                                            onChange={(e) => updateEnhancement(idx, { description: e.target.value })}
                                            placeholder="Describe the enhancement..."
                                            rows={2}
                                            fullWidth
                                            resize="none"
                                            size="sm"
                                            className="w-full px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-info-border resize-none"
                                        />
                                        <div className="flex items-center gap-2">
                                            <Select
                                                value={item.source}
                                                onChange={(e) => updateEnhancement(idx, { source: e.target.value as EnhancementSource })}
                                                options={SOURCE_OPTIONS}
                                                size="sm"
                                                className="px-2 py-1 text-xs border border-divider rounded bg-bg-default text-text-primary focus:outline-none focus:ring-1 focus:ring-info-border"
                                            />
                                            <Select
                                                value={item.priority}
                                                onChange={(e) => updateEnhancement(idx, { priority: e.target.value as 'low' | 'medium' | 'high' })}
                                                options={PRIORITY_OPTIONS}
                                                size="sm"
                                                className={`px-2 py-1 text-xs rounded ${item.priority === 'high'
                                                        ? 'bg-destructive-bg text-destructive dark:bg-destructive-bg/30 dark:text-destructive'
                                                        : item.priority === 'medium'
                                                            ? 'bg-warning-bg text-warning-color dark:bg-warning-bg/30 dark:text-warning-color'
                                                            : 'bg-surface-muted text-fg-muted dark:bg-surface-muted dark:text-fg-muted'
                                                    }`}
                                            />
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                        <Button
                            onClick={addEnhancement}
                            variant="ghost"
                            size="sm"
                            fullWidth
                            className="w-full flex items-center justify-center gap-2 p-3 border-2 border-dashed border-divider rounded-lg text-text-secondary hover:text-info-color hover:border-info-border transition-colors"
                        >
                            <Add className="w-5 h-5" /> Add Enhancement
                        </Button>
                    </div>
                )}

                {activeTab === 'learnings' && (
                    <div className="space-y-6">
                        {/* Retrospectives */}
                        <div>
                            <h4 className="text-sm font-medium text-text-primary mb-2">Retrospectives</h4>
                            {learningData.retrospectives.length === 0 ? (
                                <div className="text-center py-4 bg-surface-muted dark:bg-surface-muted rounded-lg">
                                    <p className="text-sm text-text-secondary">No retrospectives yet</p>
                                    <Button
                                        onClick={addRetrospective}
                                        variant="link"
                                        size="sm"
                                        className="mt-1 text-xs text-info-color hover:text-info-color"
                                    >
                                        Add retrospective
                                    </Button>
                                </div>
                            ) : (
                                <div className="space-y-3">
                                    {learningData.retrospectives.map((retro, idx) => (
                                        <div key={idx} className="border border-divider rounded-lg bg-bg-paper p-3 space-y-2">
                                            <div className="flex items-center justify-between">
                                                <Input
                                                    type="date"
                                                    value={retro.date}
                                                    onChange={(e) => updateRetrospective(idx, { date: e.target.value })}
                                                    size="sm"
                                                    className="px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary focus:outline-none focus:ring-1 focus:ring-info-border"
                                                />
                                                <Button
                                                    onClick={() => removeRetrospective(idx)}
                                                    variant="ghost"
                                                    size="sm"
                                                    aria-label={`Remove retrospective ${idx + 1}`}
                                                    className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                                >
                                                    <Remove className="w-4 h-4" />
                                                </Button>
                                            </div>
                                            <div className="grid grid-cols-3 gap-2">
                                                <div>
                                                    <label className="block text-xs text-text-secondary mb-1">Went Well</label>
                                                    <Textarea
                                                        value={retro.wentWell.join('\n')}
                                                        onChange={(e) =>
                                                            updateRetrospective(idx, {
                                                                wentWell: e.target.value.split('\n').filter((l) => l.trim()),
                                                            })
                                                        }
                                                        placeholder="One per line..."
                                                        rows={3}
                                                        fullWidth
                                                        resize="none"
                                                        size="sm"
                                                        className="w-full px-2 py-1 text-xs border border-divider rounded bg-success-bg dark:bg-success-bg/20 text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-success-border resize-none"
                                                    />
                                                </div>
                                                <div>
                                                    <label className="block text-xs text-text-secondary mb-1">Improvements</label>
                                                    <Textarea
                                                        value={retro.improvements.join('\n')}
                                                        onChange={(e) =>
                                                            updateRetrospective(idx, {
                                                                improvements: e.target.value.split('\n').filter((l) => l.trim()),
                                                            })
                                                        }
                                                        placeholder="One per line..."
                                                        rows={3}
                                                        fullWidth
                                                        resize="none"
                                                        size="sm"
                                                        className="w-full px-2 py-1 text-xs border border-divider rounded bg-warning-bg dark:bg-warning-bg/20 text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-warning-border resize-none"
                                                    />
                                                </div>
                                                <div>
                                                    <label className="block text-xs text-text-secondary mb-1">Actions</label>
                                                    <Textarea
                                                        value={retro.actions.join('\n')}
                                                        onChange={(e) =>
                                                            updateRetrospective(idx, {
                                                                actions: e.target.value.split('\n').filter((l) => l.trim()),
                                                            })
                                                        }
                                                        placeholder="One per line..."
                                                        rows={3}
                                                        fullWidth
                                                        resize="none"
                                                        size="sm"
                                                        className="w-full px-2 py-1 text-xs border border-divider rounded bg-info-bg dark:bg-info-bg/20 text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-info-border resize-none"
                                                    />
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                            <Button
                                onClick={addRetrospective}
                                variant="link"
                                size="sm"
                                className="mt-2 flex items-center gap-1 text-xs text-info-color hover:text-info-color transition-colors"
                            >
                                <Add className="w-3 h-3" /> Add retrospective
                            </Button>
                        </div>

                        {/* Insights */}
                        <div>
                            <h4 className="text-sm font-medium text-text-primary mb-2">Key Insights</h4>
                            <div className="space-y-2">
                                {learningData.insights.map((insight, idx) => (
                                    <div key={idx} className="flex gap-2">
                                        <Input
                                            type="text"
                                            value={insight}
                                            onChange={(e) => updateInsight(idx, e.target.value)}
                                            placeholder="Insight..."
                                            fullWidth
                                            size="sm"
                                            className="flex-1 px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-info-border"
                                        />
                                        <Button
                                            onClick={() => removeInsight(idx)}
                                            variant="ghost"
                                            size="sm"
                                            aria-label={`Remove insight ${idx + 1}`}
                                            className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                        >
                                            <Remove className="w-4 h-4" />
                                        </Button>
                                    </div>
                                ))}
                                <Button
                                    onClick={addInsight}
                                    variant="link"
                                    size="sm"
                                    className="flex items-center gap-1 text-xs text-info-color hover:text-info-color transition-colors"
                                >
                                    <Add className="w-3 h-3" /> Add insight
                                </Button>
                            </div>
                        </div>

                        {/* Recommendations */}
                        <div>
                            <h4 className="text-sm font-medium text-text-primary mb-2">Recommendations</h4>
                            <div className="space-y-2">
                                {learningData.recommendations.map((rec, idx) => (
                                    <div key={idx} className="flex gap-2">
                                        <Input
                                            type="text"
                                            value={rec}
                                            onChange={(e) => updateRecommendation(idx, e.target.value)}
                                            placeholder="Recommendation..."
                                            fullWidth
                                            size="sm"
                                            className="flex-1 px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-info-border"
                                        />
                                        <Button
                                            onClick={() => removeRecommendation(idx)}
                                            variant="ghost"
                                            size="sm"
                                            aria-label={`Remove recommendation ${idx + 1}`}
                                            className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                        >
                                            <Remove className="w-4 h-4" />
                                        </Button>
                                    </div>
                                ))}
                                <Button
                                    onClick={addRecommendation}
                                    variant="link"
                                    size="sm"
                                    className="flex items-center gap-1 text-xs text-info-color hover:text-info-color transition-colors"
                                >
                                    <Add className="w-3 h-3" /> Add recommendation
                                </Button>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

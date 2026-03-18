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
    proposed: 'bg-grey-100 text-grey-700 dark:bg-grey-800 dark:text-grey-300',
    approved: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
    in_progress: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
    completed: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
    declined: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
};

const SOURCE_ICONS: Record<EnhancementSource, React.ReactNode> = {
    incident: <ErrorOutline size={16} />,
    feedback: <Feedback size={16} />,
    metrics: <BarChart size={16} />,
    team: <Group size={16} />,
    ai_suggestion: <SmartToy size={16} />,
};

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
                    <TrendingUp className="w-5 h-5 text-purple-600" />
                    <div>
                        <h3 className="font-semibold text-text-primary">Improve</h3>
                        <p className="text-xs text-text-secondary">Enhancements & Learnings</p>
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

            {/* Tabs */}
            <div className="flex border-b border-divider">
                <button
                    onClick={() => setActiveTab('enhancements')}
                    className={`flex items-center gap-1 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'enhancements'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    <Lightbulb className="w-4 h-4" />
                    Enhancements ({enhancementData.items.length})
                </button>
                <button
                    onClick={() => setActiveTab('learnings')}
                    className={`flex items-center gap-1 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'learnings'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    <School className="w-4 h-4" />
                    Learnings
                </button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-4">
                {activeTab === 'enhancements' && (
                    <div className="space-y-4">
                        {enhancementData.items.length === 0 ? (
                            <div className="text-center py-8 text-text-secondary">
                                <Lightbulb className="w-12 h-12 mx-auto mb-2 opacity-50" />
                                <p className="text-sm">No enhancements yet</p>
                                <button
                                    onClick={addEnhancement}
                                    className="mt-2 text-sm text-primary-600 hover:text-primary-700"
                                >
                                    Add first enhancement
                                </button>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {enhancementData.items.map((item, idx) => (
                                    <div key={idx} className="border border-divider rounded-lg bg-bg-paper p-3 space-y-2">
                                        <div className="flex items-start gap-2">
                                            <input
                                                type="text"
                                                value={item.title}
                                                onChange={(e) => updateEnhancement(idx, { title: e.target.value })}
                                                placeholder="Enhancement title"
                                                className="flex-1 px-2 py-1 text-sm font-medium border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                            />
                                            <select
                                                value={item.status}
                                                onChange={(e) => updateEnhancement(idx, { status: e.target.value as EnhancementStatus })}
                                                className={`px-2 py-1 text-xs rounded ${STATUS_COLORS[item.status]}`}
                                            >
                                                <option value="proposed">Proposed</option>
                                                <option value="approved">Approved</option>
                                                <option value="in_progress">In Progress</option>
                                                <option value="completed">Completed</option>
                                                <option value="declined">Declined</option>
                                            </select>
                                            <button
                                                onClick={() => removeEnhancement(idx)}
                                                className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                            >
                                                <Remove className="w-4 h-4" />
                                            </button>
                                        </div>
                                        <textarea
                                            value={item.description}
                                            onChange={(e) => updateEnhancement(idx, { description: e.target.value })}
                                            placeholder="Describe the enhancement..."
                                            rows={2}
                                            className="w-full px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none"
                                        />
                                        <div className="flex items-center gap-2">
                                            <select
                                                value={item.source}
                                                onChange={(e) => updateEnhancement(idx, { source: e.target.value as EnhancementSource })}
                                                className="px-2 py-1 text-xs border border-divider rounded bg-bg-default text-text-primary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                            >
                                                {Object.entries(SOURCE_ICONS).map(([key]) => (
                                                    <option key={key} value={key}>
                                                        {key.charAt(0).toUpperCase() + key.slice(1).replace('_', ' ')}
                                                    </option>
                                                ))}
                                            </select>
                                            <select
                                                value={item.priority}
                                                onChange={(e) => updateEnhancement(idx, { priority: e.target.value as 'low' | 'medium' | 'high' })}
                                                className={`px-2 py-1 text-xs rounded ${item.priority === 'high'
                                                        ? 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300'
                                                        : item.priority === 'medium'
                                                            ? 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300'
                                                            : 'bg-grey-100 text-grey-700 dark:bg-grey-800 dark:text-grey-300'
                                                    }`}
                                            >
                                                <option value="low">Low Priority</option>
                                                <option value="medium">Medium Priority</option>
                                                <option value="high">High Priority</option>
                                            </select>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                        <button
                            onClick={addEnhancement}
                            className="w-full flex items-center justify-center gap-2 p-3 border-2 border-dashed border-divider rounded-lg text-text-secondary hover:text-primary-600 hover:border-primary-300 transition-colors"
                        >
                            <Add className="w-5 h-5" /> Add Enhancement
                        </button>
                    </div>
                )}

                {activeTab === 'learnings' && (
                    <div className="space-y-6">
                        {/* Retrospectives */}
                        <div>
                            <h4 className="text-sm font-medium text-text-primary mb-2">Retrospectives</h4>
                            {learningData.retrospectives.length === 0 ? (
                                <div className="text-center py-4 bg-grey-50 dark:bg-grey-800/50 rounded-lg">
                                    <p className="text-sm text-text-secondary">No retrospectives yet</p>
                                    <button
                                        onClick={addRetrospective}
                                        className="mt-1 text-xs text-primary-600 hover:text-primary-700"
                                    >
                                        Add retrospective
                                    </button>
                                </div>
                            ) : (
                                <div className="space-y-3">
                                    {learningData.retrospectives.map((retro, idx) => (
                                        <div key={idx} className="border border-divider rounded-lg bg-bg-paper p-3 space-y-2">
                                            <div className="flex items-center justify-between">
                                                <input
                                                    type="date"
                                                    value={retro.date}
                                                    onChange={(e) => updateRetrospective(idx, { date: e.target.value })}
                                                    className="px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                />
                                                <button
                                                    onClick={() => removeRetrospective(idx)}
                                                    className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                                >
                                                    <Remove className="w-4 h-4" />
                                                </button>
                                            </div>
                                            <div className="grid grid-cols-3 gap-2">
                                                <div>
                                                    <label className="block text-xs text-text-secondary mb-1">Went Well</label>
                                                    <textarea
                                                        value={retro.wentWell.join('\n')}
                                                        onChange={(e) =>
                                                            updateRetrospective(idx, {
                                                                wentWell: e.target.value.split('\n').filter((l) => l.trim()),
                                                            })
                                                        }
                                                        placeholder="One per line..."
                                                        rows={3}
                                                        className="w-full px-2 py-1 text-xs border border-divider rounded bg-green-50 dark:bg-green-900/20 text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-green-500 resize-none"
                                                    />
                                                </div>
                                                <div>
                                                    <label className="block text-xs text-text-secondary mb-1">Improvements</label>
                                                    <textarea
                                                        value={retro.improvements.join('\n')}
                                                        onChange={(e) =>
                                                            updateRetrospective(idx, {
                                                                improvements: e.target.value.split('\n').filter((l) => l.trim()),
                                                            })
                                                        }
                                                        placeholder="One per line..."
                                                        rows={3}
                                                        className="w-full px-2 py-1 text-xs border border-divider rounded bg-yellow-50 dark:bg-yellow-900/20 text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-yellow-500 resize-none"
                                                    />
                                                </div>
                                                <div>
                                                    <label className="block text-xs text-text-secondary mb-1">Actions</label>
                                                    <textarea
                                                        value={retro.actions.join('\n')}
                                                        onChange={(e) =>
                                                            updateRetrospective(idx, {
                                                                actions: e.target.value.split('\n').filter((l) => l.trim()),
                                                            })
                                                        }
                                                        placeholder="One per line..."
                                                        rows={3}
                                                        className="w-full px-2 py-1 text-xs border border-divider rounded bg-blue-50 dark:bg-blue-900/20 text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-blue-500 resize-none"
                                                    />
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                            <button
                                onClick={addRetrospective}
                                className="mt-2 flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                            >
                                <Add className="w-3 h-3" /> Add retrospective
                            </button>
                        </div>

                        {/* Insights */}
                        <div>
                            <h4 className="text-sm font-medium text-text-primary mb-2">Key Insights</h4>
                            <div className="space-y-2">
                                {learningData.insights.map((insight, idx) => (
                                    <div key={idx} className="flex gap-2">
                                        <input
                                            type="text"
                                            value={insight}
                                            onChange={(e) => updateInsight(idx, e.target.value)}
                                            placeholder="Insight..."
                                            className="flex-1 px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                        />
                                        <button
                                            onClick={() => removeInsight(idx)}
                                            className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                        >
                                            <Remove className="w-4 h-4" />
                                        </button>
                                    </div>
                                ))}
                                <button
                                    onClick={addInsight}
                                    className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                                >
                                    <Add className="w-3 h-3" /> Add insight
                                </button>
                            </div>
                        </div>

                        {/* Recommendations */}
                        <div>
                            <h4 className="text-sm font-medium text-text-primary mb-2">Recommendations</h4>
                            <div className="space-y-2">
                                {learningData.recommendations.map((rec, idx) => (
                                    <div key={idx} className="flex gap-2">
                                        <input
                                            type="text"
                                            value={rec}
                                            onChange={(e) => updateRecommendation(idx, e.target.value)}
                                            placeholder="Recommendation..."
                                            className="flex-1 px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                        />
                                        <button
                                            onClick={() => removeRecommendation(idx)}
                                            className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                        >
                                            <Remove className="w-4 h-4" />
                                        </button>
                                    </div>
                                ))}
                                <button
                                    onClick={addRecommendation}
                                    className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                                >
                                    <Add className="w-3 h-3" /> Add recommendation
                                </button>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

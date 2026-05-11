// @ts-nocheck
/**
 * Delivery Plan Editor Component
 *
 * Editor for creating and managing delivery plans with milestones and tasks.
 * Used in Deploy surface Configure segment with ?segment=configure.
 *
 * @doc.type component
 * @doc.purpose GENERATE phase delivery plan editor
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useCallback } from 'react';
import { Button } from '../ui/Button';
import { Plus as Add, Minus as Remove, Save, Sparkles as AutoAwesome, Flag, ClipboardList as Assignment, Calendar as CalendarToday, GripVertical as DragIndicator } from 'lucide-react';
import type { DeliveryPlanPayload } from '@/shared/types/lifecycle-artifacts';
import { useTranslation } from '@ghatana/i18n';

export interface DeliveryPlanEditorProps {
    data?: DeliveryPlanPayload;
    onSave: (data: DeliveryPlanPayload) => Promise<void>;
    onAIAssist?: (context: { plan?: DeliveryPlanPayload }) => Promise<Partial<DeliveryPlanPayload> | null>;
    isLoading?: boolean;
}

type MilestoneStatus = 'planned' | 'in_progress' | 'completed' | 'delayed';
type TaskStatus = 'todo' | 'in_progress' | 'done' | 'blocked';

const MILESTONE_STATUS_COLORS: Record<MilestoneStatus, string> = {
    planned: 'bg-grey-100 text-grey-700 dark:bg-grey-800 dark:text-grey-300',
    in_progress: 'bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color',
    completed: 'bg-success-bg text-success-color dark:bg-success-bg/30 dark:text-success-color',
    delayed: 'bg-destructive-bg text-destructive dark:bg-destructive-bg/30 dark:text-destructive',
};

const TASK_STATUS_COLORS: Record<TaskStatus, string> = {
    todo: 'bg-grey-100 text-grey-700 dark:bg-grey-800 dark:text-grey-300',
    in_progress: 'bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color',
    done: 'bg-success-bg text-success-color dark:bg-success-bg/30 dark:text-success-color',
    blocked: 'bg-destructive-bg text-destructive dark:bg-destructive-bg/30 dark:text-destructive',
};

const NativeInput = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>((props, ref) =>
    React.createElement('input', { ...props, ref }),
);
NativeInput.displayName = 'NativeInput';

const NativeSelect = React.forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>((props, ref) =>
    React.createElement('select', { ...props, ref }),
);
NativeSelect.displayName = 'NativeSelect';

const NativeTextarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>((props, ref) =>
    React.createElement('textarea', { ...props, ref }),
);
NativeTextarea.displayName = 'NativeTextarea';

const defaultData: DeliveryPlanPayload = {
    milestones: [],
    dependencies: [],
    risks: [],
    assumptions: [],
};

/**
 * Delivery Plan Editor for GENERATE phase.
 */
export const DeliveryPlanEditor: React.FC<DeliveryPlanEditorProps> = ({
    data,
    onSave,
    onAIAssist,
    isLoading = false,
}) => {
    const { t } = useTranslation('common');
    const [plan, setPlan] = useState<DeliveryPlanPayload>({
        ...defaultData,
        ...data,
        milestones: data?.milestones || [],
        dependencies: data?.dependencies || [],
        risks: data?.risks || [],
        assumptions: data?.assumptions || [],
    });
    const [isSaving, setIsSaving] = useState(false);
    const [isAILoading, setIsAILoading] = useState(false);
    const [expandedMilestone, setExpandedMilestone] = useState<number | null>(null);

    // Milestone operations
    const addMilestone = useCallback(() => {
        setPlan((prev) => ({
            ...prev,
            milestones: [
                ...prev.milestones,
                {
                    name: '',
                    description: '',
                    targetDate: '',
                    status: 'planned' as MilestoneStatus,
                    tasks: [],
                },
            ],
        }));
        setExpandedMilestone(plan.milestones.length);
    }, [plan.milestones.length]);

    const updateMilestone = useCallback((index: number, updates: Partial<DeliveryPlanPayload['milestones'][0]>) => {
        setPlan((prev) => ({
            ...prev,
            milestones: prev.milestones.map((m, i) => (i === index ? { ...m, ...updates } : m)),
        }));
    }, []);

    const removeMilestone = useCallback((index: number) => {
        setPlan((prev) => ({
            ...prev,
            milestones: prev.milestones.filter((_, i) => i !== index),
        }));
        if (expandedMilestone === index) {
            setExpandedMilestone(null);
        }
    }, [expandedMilestone]);

    // Task operations
    const addTask = useCallback((milestoneIndex: number) => {
        setPlan((prev) => ({
            ...prev,
            milestones: prev.milestones.map((m, i) =>
                i === milestoneIndex
                    ? {
                        ...m,
                        tasks: [
                            ...m.tasks,
                            { name: '', assignee: '', status: 'todo' as TaskStatus, estimate: '' },
                        ],
                    }
                    : m,
            ),
        }));
    }, []);

    const updateTask = useCallback(
        (milestoneIndex: number, taskIndex: number, updates: Partial<DeliveryPlanPayload['milestones'][0]['tasks'][0]>) => {
            setPlan((prev) => ({
                ...prev,
                milestones: prev.milestones.map((m, i) =>
                    i === milestoneIndex
                        ? {
                            ...m,
                            tasks: m.tasks.map((t, j) => (j === taskIndex ? { ...t, ...updates } : t)),
                        }
                        : m,
                ),
            }));
        },
        [],
    );

    const removeTask = useCallback((milestoneIndex: number, taskIndex: number) => {
        setPlan((prev) => ({
            ...prev,
            milestones: prev.milestones.map((m, i) =>
                i === milestoneIndex
                    ? { ...m, tasks: m.tasks.filter((_, j) => j !== taskIndex) }
                    : m,
            ),
        }));
    }, []);

    // List operations for dependencies, risks, assumptions
    const addListItem = useCallback((field: 'dependencies' | 'risks' | 'assumptions') => {
        setPlan((prev) => ({
            ...prev,
            [field]: [...prev[field], ''],
        }));
    }, []);

    const updateListItem = useCallback((field: 'dependencies' | 'risks' | 'assumptions', index: number, value: string) => {
        setPlan((prev) => ({
            ...prev,
            [field]: prev[field].map((item, i) => (i === index ? value : item)),
        }));
    }, []);

    const removeListItem = useCallback((field: 'dependencies' | 'risks' | 'assumptions', index: number) => {
        setPlan((prev) => ({
            ...prev,
            [field]: prev[field].filter((_, i) => i !== index),
        }));
    }, []);

    const handleAIAssist = useCallback(async () => {
        if (!onAIAssist) return;
        setIsAILoading(true);
        try {
            const result = await onAIAssist({ plan });
            if (result) {
                setPlan((prev) => ({
                    ...prev,
                    ...result,
                    milestones: result.milestones?.length ? result.milestones : prev.milestones,
                    dependencies: result.dependencies?.length ? result.dependencies : prev.dependencies,
                    risks: result.risks?.length ? result.risks : prev.risks,
                    assumptions: result.assumptions?.length ? result.assumptions : prev.assumptions,
                }));
            }
        } finally {
            setIsAILoading(false);
        }
    }, [onAIAssist, plan]);

    const handleSave = useCallback(async () => {
        setIsSaving(true);
        try {
            const cleanPlan: DeliveryPlanPayload = {
                ...plan,
                milestones: plan.milestones.filter((m) => m.name.trim()),
                dependencies: plan.dependencies.filter((d) => d.trim()),
                risks: plan.risks.filter((r) => r.trim()),
                assumptions: plan.assumptions.filter((a) => a.trim()),
            };
            await onSave(cleanPlan);
        } finally {
            setIsSaving(false);
        }
    }, [plan, onSave]);

    const totalTasks = plan.milestones.reduce((sum, m) => sum + m.tasks.length, 0);
    const completedTasks = plan.milestones.reduce(
        (sum, m) => sum + m.tasks.filter((t) => t.status === 'done').length,
        0,
    );

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-divider">
                <div className="flex items-center gap-2">
                    <Flag className="w-5 h-5 text-success-color" />
                    <div>
                        <h3 className="font-semibold text-text-primary">Delivery Plan</h3>
                        <p className="text-xs text-text-secondary">
                            {plan.milestones.length} milestones • {completedTasks}/{totalTasks} tasks done
                        </p>
                    </div>
                </div>
                <div className="flex gap-2">
                    {onAIAssist && (
                        <Button
                            onClick={handleAIAssist}
                            disabled={isAILoading || isSaving}
                            className="flex items-center gap-1 px-3 py-1.5 text-sm text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded-lg transition-colors disabled:opacity-50"
                        >
                            <AutoAwesome className="w-4 h-4" />
                            {isAILoading ? 'Generating...' : 'Assist'}
                        </Button>
                    )}
                    <Button
                        onClick={handleSave}
                        disabled={isSaving || isLoading}
                        className="flex items-center gap-1 px-3 py-1.5 text-sm bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50"
                    >
                        <Save className="w-4 h-4" />
                        {isSaving ? 'Saving...' : 'Save'}
                    </Button>
                </div>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-4 space-y-6">
                {/* Milestones */}
                <div>
                    <h4 className="text-sm font-medium text-text-primary mb-3 flex items-center gap-2">
                        <Flag className="w-4 h-4" /> Milestones
                    </h4>
                    <div className="space-y-3">
                        {plan.milestones.map((milestone, mIdx) => (
                            <div
                                key={mIdx}
                                className="border border-divider rounded-lg bg-bg-paper overflow-hidden"
                            >
                                {/* Milestone Header */}
                                <div className="flex items-center gap-2 p-3 bg-grey-50 dark:bg-grey-800/50">
                                    <DragIndicator className="w-4 h-4 text-text-secondary cursor-move" />
                                    <NativeInput
                                        type="text"
                                        value={milestone.name}
                                        onChange={(e) => updateMilestone(mIdx, { name: e.target.value })}
                                        placeholder={t('deploy.delivery.milestoneNamePlaceholder')}
                                        className="flex-1 px-2 py-1 text-sm font-medium border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                    />
                                    <NativeInput
                                        type="date"
                                        value={milestone.targetDate}
                                        onChange={(e) => updateMilestone(mIdx, { targetDate: e.target.value })}
                                        className="px-2 py-1 text-xs border border-divider rounded bg-bg-paper text-text-primary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                    />
                                    <NativeSelect
                                        value={milestone.status}
                                        onChange={(e) => updateMilestone(mIdx, { status: e.target.value as MilestoneStatus })}
                                        className={`px-2 py-1 text-xs rounded ${MILESTONE_STATUS_COLORS[milestone.status]}`}
                                    >
                                        <option value="planned">Planned</option>
                                        <option value="in_progress">In Progress</option>
                                        <option value="completed">Completed</option>
                                        <option value="delayed">Delayed</option>
                                    </NativeSelect>
                                    <Button
                                        onClick={() => setExpandedMilestone(expandedMilestone === mIdx ? null : mIdx)}
                                        className="p-1 text-text-secondary hover:text-text-primary transition-colors"
                                    >
                                        {expandedMilestone === mIdx ? '−' : '+'}
                                    </Button>
                                    <Button
                                        onClick={() => removeMilestone(mIdx)}
                                        className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                    >
                                        <Remove className="w-4 h-4" />
                                    </Button>
                                </div>

                                {/* Milestone Details */}
                                {expandedMilestone === mIdx && (
                                    <div className="p-3 space-y-3 border-t border-divider">
                                        <NativeTextarea
                                            value={milestone.description}
                                            onChange={(e) => updateMilestone(mIdx, { description: e.target.value })}
                                            placeholder={t('deploy.delivery.milestoneDescriptionPlaceholder')}
                                            rows={2}
                                            className="w-full px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none"
                                        />

                                        {/* Tasks */}
                                        <div>
                                            <h5 className="text-xs font-medium text-text-secondary mb-2 flex items-center gap-1">
                                                <Assignment className="w-3 h-3" /> Tasks ({milestone.tasks.length})
                                            </h5>
                                            <div className="space-y-2">
                                                {milestone.tasks.map((task, tIdx) => (
                                                    <div key={tIdx} className="flex gap-2 items-start">
                                                        <NativeInput
                                                            type="text"
                                                            value={task.name}
                                                            onChange={(e) => updateTask(mIdx, tIdx, { name: e.target.value })}
                                                            placeholder={t('deploy.delivery.taskNamePlaceholder')}
                                                            className="flex-1 px-2 py-1 text-xs border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                        />
                                                        <NativeInput
                                                            type="text"
                                                            value={task.assignee}
                                                            onChange={(e) => updateTask(mIdx, tIdx, { assignee: e.target.value })}
                                                            placeholder={t('deploy.delivery.assigneePlaceholder')}
                                                            className="w-24 px-2 py-1 text-xs border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                        />
                                                        <NativeInput
                                                            type="text"
                                                            value={task.estimate}
                                                            onChange={(e) => updateTask(mIdx, tIdx, { estimate: e.target.value })}
                                                            placeholder={t('deploy.delivery.estimatePlaceholder')}
                                                            className="w-16 px-2 py-1 text-xs border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                        />
                                                        <NativeSelect
                                                            value={task.status}
                                                            onChange={(e) => updateTask(mIdx, tIdx, { status: e.target.value as TaskStatus })}
                                                            className={`px-2 py-1 text-xs rounded ${TASK_STATUS_COLORS[task.status]}`}
                                                        >
                                                            <option value="todo">To Do</option>
                                                            <option value="in_progress">In Progress</option>
                                                            <option value="done">Done</option>
                                                            <option value="blocked">Blocked</option>
                                                        </NativeSelect>
                                                        <Button
                                                            onClick={() => removeTask(mIdx, tIdx)}
                                                            className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                                        >
                                                            <Remove className="w-3 h-3" />
                                                        </Button>
                                                    </div>
                                                ))}
                                                <Button
                                                    onClick={() => addTask(mIdx)}
                                                    className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                                                >
                                                    <Add className="w-3 h-3" /> Add task
                                                </Button>
                                            </div>
                                        </div>
                                    </div>
                                )}
                            </div>
                        ))}
                        <Button
                            onClick={addMilestone}
                            className="w-full flex items-center justify-center gap-2 p-3 border-2 border-dashed border-divider rounded-lg text-text-secondary hover:text-primary-600 hover:border-primary-300 transition-colors"
                        >
                            <Add className="w-5 h-5" /> Add Milestone
                        </Button>
                    </div>
                </div>

                {/* Dependencies */}
                <div>
                    <h4 className="text-sm font-medium text-text-primary mb-2">Dependencies</h4>
                    <div className="space-y-2">
                        {plan.dependencies.map((dep, idx) => (
                            <div key={idx} className="flex gap-2">
                                <NativeInput
                                    type="text"
                                    value={dep}
                                    onChange={(e) => updateListItem('dependencies', idx, e.target.value)}
                                    placeholder={t('deploy.delivery.dependencyPlaceholder')}
                                    className="flex-1 px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                />
                                <Button
                                    onClick={() => removeListItem('dependencies', idx)}
                                    className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                >
                                    <Remove className="w-4 h-4" />
                                </Button>
                            </div>
                        ))}
                        <Button
                            onClick={() => addListItem('dependencies')}
                            className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                        >
                            <Add className="w-3 h-3" /> Add dependency
                        </Button>
                    </div>
                </div>

                {/* Risks */}
                <div>
                    <h4 className="text-sm font-medium text-text-primary mb-2">Risks</h4>
                    <div className="space-y-2">
                        {plan.risks.map((risk, idx) => (
                            <div key={idx} className="flex gap-2">
                                <NativeInput
                                    type="text"
                                    value={risk}
                                    onChange={(e) => updateListItem('risks', idx, e.target.value)}
                                    placeholder={t('deploy.delivery.riskPlaceholder')}
                                    className="flex-1 px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                />
                                <Button
                                    onClick={() => removeListItem('risks', idx)}
                                    className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                >
                                    <Remove className="w-4 h-4" />
                                </Button>
                            </div>
                        ))}
                        <Button
                            onClick={() => addListItem('risks')}
                            className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                        >
                            <Add className="w-3 h-3" /> Add risk
                        </Button>
                    </div>
                </div>

                {/* Assumptions */}
                <div>
                    <h4 className="text-sm font-medium text-text-primary mb-2">Assumptions</h4>
                    <div className="space-y-2">
                        {plan.assumptions.map((assumption, idx) => (
                            <div key={idx} className="flex gap-2">
                                <NativeInput
                                    type="text"
                                    value={assumption}
                                    onChange={(e) => updateListItem('assumptions', idx, e.target.value)}
                                    placeholder={t('deploy.delivery.assumptionPlaceholder')}
                                    className="flex-1 px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                />
                                <Button
                                    onClick={() => removeListItem('assumptions', idx)}
                                    className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                >
                                    <Remove className="w-4 h-4" />
                                </Button>
                            </div>
                        ))}
                        <Button
                            onClick={() => addListItem('assumptions')}
                            className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                        >
                            <Add className="w-3 h-3" /> Add assumption
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default DeliveryPlanEditor;

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
import { Plus as Add, Minus as Remove, Save, Sparkles as AutoAwesome, Flag, ClipboardList as Assignment, Calendar as CalendarToday, GripVertical as DragIndicator } from 'lucide-react';
import type { DeliveryPlanPayload } from '@/shared/types/lifecycle-artifacts';

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
    in_progress: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
    completed: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
    delayed: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
};

const TASK_STATUS_COLORS: Record<TaskStatus, string> = {
    todo: 'bg-grey-100 text-grey-700 dark:bg-grey-800 dark:text-grey-300',
    in_progress: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
    done: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
    blocked: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
};

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
                    <Flag className="w-5 h-5 text-green-600" />
                    <div>
                        <h3 className="font-semibold text-text-primary">Delivery Plan</h3>
                        <p className="text-xs text-text-secondary">
                            {plan.milestones.length} milestones • {completedTasks}/{totalTasks} tasks done
                        </p>
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
                            {isAILoading ? 'Generating...' : 'AI Assist'}
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
                                    <input
                                        type="text"
                                        value={milestone.name}
                                        onChange={(e) => updateMilestone(mIdx, { name: e.target.value })}
                                        placeholder="Milestone name"
                                        className="flex-1 px-2 py-1 text-sm font-medium border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                    />
                                    <input
                                        type="date"
                                        value={milestone.targetDate}
                                        onChange={(e) => updateMilestone(mIdx, { targetDate: e.target.value })}
                                        className="px-2 py-1 text-xs border border-divider rounded bg-bg-paper text-text-primary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                    />
                                    <select
                                        value={milestone.status}
                                        onChange={(e) => updateMilestone(mIdx, { status: e.target.value as MilestoneStatus })}
                                        className={`px-2 py-1 text-xs rounded ${MILESTONE_STATUS_COLORS[milestone.status]}`}
                                    >
                                        <option value="planned">Planned</option>
                                        <option value="in_progress">In Progress</option>
                                        <option value="completed">Completed</option>
                                        <option value="delayed">Delayed</option>
                                    </select>
                                    <button
                                        onClick={() => setExpandedMilestone(expandedMilestone === mIdx ? null : mIdx)}
                                        className="p-1 text-text-secondary hover:text-text-primary transition-colors"
                                    >
                                        {expandedMilestone === mIdx ? '−' : '+'}
                                    </button>
                                    <button
                                        onClick={() => removeMilestone(mIdx)}
                                        className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                    >
                                        <Remove className="w-4 h-4" />
                                    </button>
                                </div>

                                {/* Milestone Details */}
                                {expandedMilestone === mIdx && (
                                    <div className="p-3 space-y-3 border-t border-divider">
                                        <textarea
                                            value={milestone.description}
                                            onChange={(e) => updateMilestone(mIdx, { description: e.target.value })}
                                            placeholder="Milestone description..."
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
                                                        <input
                                                            type="text"
                                                            value={task.name}
                                                            onChange={(e) => updateTask(mIdx, tIdx, { name: e.target.value })}
                                                            placeholder="Task name"
                                                            className="flex-1 px-2 py-1 text-xs border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                        />
                                                        <input
                                                            type="text"
                                                            value={task.assignee}
                                                            onChange={(e) => updateTask(mIdx, tIdx, { assignee: e.target.value })}
                                                            placeholder="Assignee"
                                                            className="w-24 px-2 py-1 text-xs border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                        />
                                                        <input
                                                            type="text"
                                                            value={task.estimate}
                                                            onChange={(e) => updateTask(mIdx, tIdx, { estimate: e.target.value })}
                                                            placeholder="Est."
                                                            className="w-16 px-2 py-1 text-xs border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                        />
                                                        <select
                                                            value={task.status}
                                                            onChange={(e) => updateTask(mIdx, tIdx, { status: e.target.value as TaskStatus })}
                                                            className={`px-2 py-1 text-xs rounded ${TASK_STATUS_COLORS[task.status]}`}
                                                        >
                                                            <option value="todo">To Do</option>
                                                            <option value="in_progress">In Progress</option>
                                                            <option value="done">Done</option>
                                                            <option value="blocked">Blocked</option>
                                                        </select>
                                                        <button
                                                            onClick={() => removeTask(mIdx, tIdx)}
                                                            className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                                        >
                                                            <Remove className="w-3 h-3" />
                                                        </button>
                                                    </div>
                                                ))}
                                                <button
                                                    onClick={() => addTask(mIdx)}
                                                    className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                                                >
                                                    <Add className="w-3 h-3" /> Add task
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                )}
                            </div>
                        ))}
                        <button
                            onClick={addMilestone}
                            className="w-full flex items-center justify-center gap-2 p-3 border-2 border-dashed border-divider rounded-lg text-text-secondary hover:text-primary-600 hover:border-primary-300 transition-colors"
                        >
                            <Add className="w-5 h-5" /> Add Milestone
                        </button>
                    </div>
                </div>

                {/* Dependencies */}
                <div>
                    <h4 className="text-sm font-medium text-text-primary mb-2">Dependencies</h4>
                    <div className="space-y-2">
                        {plan.dependencies.map((dep, idx) => (
                            <div key={idx} className="flex gap-2">
                                <input
                                    type="text"
                                    value={dep}
                                    onChange={(e) => updateListItem('dependencies', idx, e.target.value)}
                                    placeholder="External dependency..."
                                    className="flex-1 px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                />
                                <button
                                    onClick={() => removeListItem('dependencies', idx)}
                                    className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                >
                                    <Remove className="w-4 h-4" />
                                </button>
                            </div>
                        ))}
                        <button
                            onClick={() => addListItem('dependencies')}
                            className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                        >
                            <Add className="w-3 h-3" /> Add dependency
                        </button>
                    </div>
                </div>

                {/* Risks */}
                <div>
                    <h4 className="text-sm font-medium text-text-primary mb-2">Risks</h4>
                    <div className="space-y-2">
                        {plan.risks.map((risk, idx) => (
                            <div key={idx} className="flex gap-2">
                                <input
                                    type="text"
                                    value={risk}
                                    onChange={(e) => updateListItem('risks', idx, e.target.value)}
                                    placeholder="Potential risk..."
                                    className="flex-1 px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                />
                                <button
                                    onClick={() => removeListItem('risks', idx)}
                                    className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                >
                                    <Remove className="w-4 h-4" />
                                </button>
                            </div>
                        ))}
                        <button
                            onClick={() => addListItem('risks')}
                            className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                        >
                            <Add className="w-3 h-3" /> Add risk
                        </button>
                    </div>
                </div>

                {/* Assumptions */}
                <div>
                    <h4 className="text-sm font-medium text-text-primary mb-2">Assumptions</h4>
                    <div className="space-y-2">
                        {plan.assumptions.map((assumption, idx) => (
                            <div key={idx} className="flex gap-2">
                                <input
                                    type="text"
                                    value={assumption}
                                    onChange={(e) => updateListItem('assumptions', idx, e.target.value)}
                                    placeholder="Assumption..."
                                    className="flex-1 px-2 py-1.5 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                />
                                <button
                                    onClick={() => removeListItem('assumptions', idx)}
                                    className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                >
                                    <Remove className="w-4 h-4" />
                                </button>
                            </div>
                        ))}
                        <button
                            onClick={() => addListItem('assumptions')}
                            className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                        >
                            <Add className="w-3 h-3" /> Add assumption
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default DeliveryPlanEditor;

import React from 'react';
import { CheckSquare, Plus, Trash2, Link, Settings } from 'lucide-react';
import type { Task, Claim, Evidence } from '@ghatana/tutorputor-contracts/v1/learning-unit';

interface TasksEditorProps {
    tasks: Task[];
    claims: Claim[];
    evidence: Evidence[];
    onChange: (tasks: Task[]) => void;
}

const TASK_TYPES = ['prediction', 'simulation', 'explanation'];

export function TasksEditor({ tasks, claims, evidence, onChange }: TasksEditorProps) {
    const handleAddTask = () => {
        const newId = `T${tasks.length + 1}`;
        const newTask: Task = {
            id: newId,
            type: 'prediction',
            claimRef: claims[0]?.id || '',
            evidenceRef: evidence[0]?.id || '',
            prompt: '',
            confidenceRequired: true,
        };
        onChange([...tasks, newTask]);
    };

    const handleUpdateTask = (index: number, updates: Partial<Task>) => {
        const updated = [...tasks];
        updated[index] = { ...updated[index], ...updates };
        onChange(updated);
    };

    const handleRemoveTask = (index: number) => {
        const updated = [...tasks];
        updated.splice(index, 1);
        onChange(updated);
    };

    return (
        <div className="space-y-6">
            <div className="bg-orange-50 dark:bg-orange-900/20 p-4 rounded-lg border border-orange-100 dark:border-orange-800">
                <h3 className="text-lg font-medium text-orange-900 dark:text-orange-300 flex items-center gap-2">
                    <CheckSquare className="w-5 h-5" />
                    Learning Tasks
                </h3>
                <p className="text-sm text-orange-700 dark:text-orange-400 mt-1">
                    What will the learner DO to generate the evidence?
                </p>
            </div>

            <div className="space-y-4">
                {tasks.map((task, index) => (
                    <div key={task.id} className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 shadow-sm">
                        <div className="flex items-start gap-4">
                            <div className="pt-2">
                                <span className="flex items-center justify-center w-8 h-8 rounded-full bg-orange-100 text-orange-700 dark:bg-orange-900/50 dark:text-orange-300 font-bold text-sm">
                                    {task.id}
                                </span>
                            </div>

                            <div className="flex-1 space-y-4">
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                    <div>
                                        <label className="block text-xs font-medium text-gray-500 uppercase mb-1">
                                            Task Type
                                        </label>
                                        <select
                                            value={task.type}
                                            onChange={(e) => handleUpdateTask(index, { type: e.target.value })}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 bg-transparent capitalize"
                                        >
                                            {TASK_TYPES.map(t => (
                                                <option key={t} value={t}>{t}</option>
                                            ))}
                                        </select>
                                    </div>

                                    <div>
                                        <label className="block text-xs font-medium text-gray-500 uppercase mb-1">
                                            Linked Claim
                                        </label>
                                        <select
                                            value={task.claimRef}
                                            onChange={(e) => handleUpdateTask(index, { claimRef: e.target.value })}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 bg-transparent"
                                        >
                                            <option value="">Select Claim...</option>
                                            {claims.map(c => (
                                                <option key={c.id} value={c.id}>{c.id}: {c.text.substring(0, 20)}...</option>
                                            ))}
                                        </select>
                                    </div>

                                    <div>
                                        <label className="block text-xs font-medium text-gray-500 uppercase mb-1">
                                            Linked Evidence
                                        </label>
                                        <select
                                            value={task.evidenceRef}
                                            onChange={(e) => handleUpdateTask(index, { evidenceRef: e.target.value })}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 bg-transparent"
                                        >
                                            <option value="">Select Evidence...</option>
                                            {evidence
                                                .filter(e => !task.claimRef || e.claimRef === task.claimRef)
                                                .map(e => (
                                                    <option key={e.id} value={e.id}>{e.id}: {e.type}</option>
                                                ))}
                                        </select>
                                    </div>
                                </div>

                                <div>
                                    <label className="block text-xs font-medium text-gray-500 uppercase mb-1">
                                        Prompt / Instruction
                                    </label>
                                    <textarea
                                        value={task.prompt}
                                        onChange={(e) => handleUpdateTask(index, { prompt: e.target.value })}
                                        placeholder="What should the learner do?"
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 bg-transparent h-20"
                                    />
                                </div>

                                {/* Type-specific fields */}
                                {task.type === 'prediction' && (
                                    <div className="bg-gray-50 dark:bg-gray-900/50 rounded-lg p-3 space-y-3">
                                        <div className="flex items-center justify-between">
                                            <label className="text-sm font-medium text-gray-700 dark:text-gray-300">Prediction Options</label>
                                            <label className="flex items-center gap-2 text-xs text-gray-600 dark:text-gray-400">
                                                <input
                                                    type="checkbox"
                                                    checked={task.confidenceRequired}
                                                    onChange={(e) => handleUpdateTask(index, { confidenceRequired: e.target.checked })}
                                                    className="rounded border-gray-300 text-blue-600"
                                                />
                                                Require Confidence (CBM)
                                            </label>
                                        </div>
                                        <div className="space-y-2">
                                            {(task.options || []).map((opt, optIndex) => (
                                                <div key={optIndex} className="flex gap-2">
                                                    <input
                                                        type="text"
                                                        value={opt}
                                                        onChange={(e) => {
                                                            const newOptions = [...(task.options || [])];
                                                            newOptions[optIndex] = e.target.value;
                                                            handleUpdateTask(index, { options: newOptions });
                                                        }}
                                                        className="flex-1 px-2 py-1 text-sm border rounded"
                                                    />
                                                    <button
                                                        onClick={() => {
                                                            const newOptions = [...(task.options || [])];
                                                            newOptions.splice(optIndex, 1);
                                                            handleUpdateTask(index, { options: newOptions });
                                                        }}
                                                        className="text-gray-400 hover:text-red-500"
                                                    >
                                                        <Trash2 className="w-4 h-4" />
                                                    </button>
                                                </div>
                                            ))}
                                            <button
                                                onClick={() => handleUpdateTask(index, { options: [...(task.options || []), ''] })}
                                                className="text-xs text-blue-600 flex items-center gap-1"
                                            >
                                                <Plus className="w-3 h-3" /> Add Option
                                            </button>
                                        </div>
                                        <div>
                                            <label className="block text-xs font-medium text-gray-500 uppercase mb-1">Correct Answer</label>
                                            <select
                                                value={task.correctAnswer || ''}
                                                onChange={(e) => handleUpdateTask(index, { correctAnswer: e.target.value })}
                                                className="w-full px-2 py-1 text-sm border rounded"
                                            >
                                                <option value="">Select correct option...</option>
                                                {(task.options || []).map(opt => (
                                                    <option key={opt} value={opt}>{opt}</option>
                                                ))}
                                            </select>
                                        </div>
                                    </div>
                                )}

                                {task.type === 'simulation' && (
                                    <div className="bg-gray-50 dark:bg-gray-900/50 rounded-lg p-3 space-y-3">
                                        <div>
                                            <label className="block text-xs font-medium text-gray-500 uppercase mb-1">Simulation Reference ID</label>
                                            <input
                                                type="text"
                                                value={task.simulationRef || ''}
                                                onChange={(e) => handleUpdateTask(index, { simulationRef: e.target.value })}
                                                placeholder="e.g. sim-falling-objects"
                                                className="w-full px-2 py-1 text-sm border rounded"
                                            />
                                        </div>
                                        <div>
                                            <label className="block text-xs font-medium text-gray-500 uppercase mb-1">Goal Description</label>
                                            <input
                                                type="text"
                                                value={task.goal || ''}
                                                onChange={(e) => handleUpdateTask(index, { goal: e.target.value })}
                                                placeholder="e.g. Match the target curve"
                                                className="w-full px-2 py-1 text-sm border rounded"
                                            />
                                        </div>
                                    </div>
                                )}
                            </div>

                            <button
                                onClick={() => handleRemoveTask(index)}
                                className="text-gray-400 hover:text-red-500 p-2"
                                title="Remove Task"
                            >
                                <Trash2 className="w-5 h-5" />
                            </button>
                        </div>
                    </div>
                ))}

                <button
                    onClick={handleAddTask}
                    className="w-full py-3 border-2 border-dashed border-gray-300 dark:border-gray-700 rounded-xl text-gray-500 hover:border-orange-500 hover:text-orange-500 transition-colors flex items-center justify-center gap-2"
                >
                    <Plus className="w-5 h-5" />
                    Add New Task
                </button>
            </div>
        </div>
    );
}

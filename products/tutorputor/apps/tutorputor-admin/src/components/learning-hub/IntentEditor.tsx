import React, { useState } from 'react';
import { Target, Lightbulb, AlertTriangle, Plus, X } from 'lucide-react';
import type { Intent } from '@ghatana/tutorputor-contracts/v1/learning-unit';

interface IntentEditorProps {
    intent: Intent;
    onChange: (intent: Intent) => void;
}

export function IntentEditor({ intent, onChange }: IntentEditorProps) {
    const [newMisconception, setNewMisconception] = useState('');

    const handleAddMisconception = () => {
        if (newMisconception.trim()) {
            const updated = [
                ...(intent.targetMisconceptions || []),
                newMisconception.trim().replace(/\s+/g, '_').toLowerCase()
            ];
            onChange({ ...intent, targetMisconceptions: updated });
            setNewMisconception('');
        }
    };

    const handleRemoveMisconception = (index: number) => {
        const updated = [...(intent.targetMisconceptions || [])];
        updated.splice(index, 1);
        onChange({ ...intent, targetMisconceptions: updated });
    };

    return (
        <div className="space-y-6">
            <div className="bg-blue-50 dark:bg-blue-900/20 p-4 rounded-lg border border-blue-100 dark:border-blue-800">
                <h3 className="text-lg font-medium text-blue-900 dark:text-blue-300 flex items-center gap-2">
                    <Target className="w-5 h-5" />
                    Pedagogical Intent
                </h3>
                <p className="text-sm text-blue-700 dark:text-blue-400 mt-1">
                    Define the core problem and motivation. What specific misconception are we addressing?
                </p>
            </div>

            <div className="space-y-4">
                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        Problem Statement
                    </label>
                    <div className="relative">
                        <AlertTriangle className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
                        <textarea
                            value={intent.problem || ''}
                            onChange={(e) => onChange({ ...intent, problem: e.target.value })}
                            placeholder="e.g., Students believe heavier objects fall faster than lighter objects..."
                            className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-800 text-gray-900 dark:text-white h-24"
                        />
                    </div>
                    <p className="text-xs text-gray-500 mt-1">
                        Describe the gap or misunderstanding in student knowledge.
                    </p>
                </div>

                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        Motivation
                    </label>
                    <div className="relative">
                        <Lightbulb className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
                        <textarea
                            value={intent.motivation || ''}
                            onChange={(e) => onChange({ ...intent, motivation: e.target.value })}
                            placeholder="e.g., Critical for understanding gravity, free fall, and Galilean physics..."
                            className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-800 text-gray-900 dark:text-white h-24"
                        />
                    </div>
                    <p className="text-xs text-gray-500 mt-1">
                        Why is this important? How does it connect to broader learning goals?
                    </p>
                </div>

                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        Target Misconceptions
                    </label>
                    <div className="flex flex-wrap gap-2 mb-2">
                        {intent.targetMisconceptions?.map((m, i) => (
                            <span
                                key={i}
                                className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-sm bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400"
                            >
                                {m}
                                <button
                                    onClick={() => handleRemoveMisconception(i)}
                                    className="hover:text-red-900 dark:hover:text-red-200"
                                >
                                    <X className="w-3 h-3" />
                                </button>
                            </span>
                        ))}
                    </div>
                    <div className="flex gap-2">
                        <input
                            type="text"
                            value={newMisconception}
                            onChange={(e) => setNewMisconception(e.target.value)}
                            onKeyDown={(e) => e.key === 'Enter' && handleAddMisconception()}
                            placeholder="Add misconception tag (e.g. mass_affects_fall)"
                            className="flex-1 px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-800 text-gray-900 dark:text-white"
                        />
                        <button
                            onClick={handleAddMisconception}
                            className="px-4 py-2 bg-gray-100 hover:bg-gray-200 dark:bg-gray-700 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 rounded-lg"
                        >
                            <Plus className="w-5 h-5" />
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}

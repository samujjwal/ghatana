import React from 'react';
import { Brain, AlertTriangle, Settings } from 'lucide-react';
import type { AssessmentConfig } from '@ghatana/tutorputor-contracts/v1/learning-unit';

interface AssessmentEditorProps {
    assessment: AssessmentConfig;
    onChange: (assessment: AssessmentConfig) => void;
}

export function AssessmentEditor({ assessment, onChange }: AssessmentEditorProps) {
    const handleScoringChange = (field: keyof AssessmentConfig['scoring'], value: number) => {
        onChange({
            ...assessment,
            scoring: {
                ...assessment.scoring,
                [field]: value
            }
        });
    };

    const toggleViva = (enabled: boolean) => {
        if (enabled) {
            onChange({
                ...assessment,
                vivaTrigger: {
                    conditions: [
                        { type: 'overconfident_wrong', threshold: 2, description: '2+ high confidence wrong answers' }
                    ]
                }
            });
        } else {
            const { vivaTrigger, ...rest } = assessment;
            onChange(rest as AssessmentConfig);
        }
    };

    return (
        <div className="space-y-6">
            <div className="bg-indigo-50 dark:bg-indigo-900/20 p-4 rounded-lg border border-indigo-100 dark:border-indigo-800">
                <h3 className="text-lg font-medium text-indigo-900 dark:text-indigo-300 flex items-center gap-2">
                    <Brain className="w-5 h-5" />
                    Assessment & CBM Configuration
                </h3>
                <p className="text-sm text-indigo-700 dark:text-indigo-400 mt-1">
                    Configure Confidence-Based Marking (CBM) scoring and Viva triggers.
                </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* CBM Scoring Matrix */}
                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 shadow-sm">
                    <h4 className="font-medium text-gray-900 dark:text-white mb-4 flex items-center gap-2">
                        <Settings className="w-4 h-4" />
                        CBM Scoring Matrix
                    </h4>

                    <div className="grid grid-cols-3 gap-4 text-center text-sm">
                        <div className="col-span-1"></div>
                        <div className="font-medium text-green-600">Correct</div>
                        <div className="font-medium text-red-600">Incorrect</div>

                        <div className="font-medium text-gray-600 dark:text-gray-400 flex items-center justify-end">High Conf.</div>
                        <input
                            type="number"
                            value={assessment.scoring?.correctHighConfidence ?? 3}
                            onChange={(e) => handleScoringChange('correctHighConfidence', parseInt(e.target.value))}
                            className="w-full px-2 py-1 border rounded text-center text-green-600 font-bold"
                        />
                        <input
                            type="number"
                            value={assessment.scoring?.incorrectHighConfidence ?? -6}
                            onChange={(e) => handleScoringChange('incorrectHighConfidence', parseInt(e.target.value))}
                            className="w-full px-2 py-1 border rounded text-center text-red-600 font-bold"
                        />

                        <div className="font-medium text-gray-600 dark:text-gray-400 flex items-center justify-end">Med Conf.</div>
                        <input
                            type="number"
                            value={assessment.scoring?.correctMediumConfidence ?? 2}
                            onChange={(e) => handleScoringChange('correctMediumConfidence', parseInt(e.target.value))}
                            className="w-full px-2 py-1 border rounded text-center text-green-600"
                        />
                        <input
                            type="number"
                            value={assessment.scoring?.incorrectMediumConfidence ?? -2}
                            onChange={(e) => handleScoringChange('incorrectMediumConfidence', parseInt(e.target.value))}
                            className="w-full px-2 py-1 border rounded text-center text-red-600"
                        />

                        <div className="font-medium text-gray-600 dark:text-gray-400 flex items-center justify-end">Low Conf.</div>
                        <input
                            type="number"
                            value={assessment.scoring?.correctLowConfidence ?? 1}
                            onChange={(e) => handleScoringChange('correctLowConfidence', parseInt(e.target.value))}
                            className="w-full px-2 py-1 border rounded text-center text-green-600"
                        />
                        <input
                            type="number"
                            value={assessment.scoring?.incorrectLowConfidence ?? 0}
                            onChange={(e) => handleScoringChange('incorrectLowConfidence', parseInt(e.target.value))}
                            className="w-full px-2 py-1 border rounded text-center text-gray-500"
                        />
                    </div>
                    <p className="text-xs text-gray-500 mt-4">
                        Standard CBM penalizes high-confidence errors heavily (-6) to discourage guessing and promote calibration.
                    </p>
                </div>

                {/* Viva Triggers */}
                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center justify-between mb-4">
                        <h4 className="font-medium text-gray-900 dark:text-white flex items-center gap-2">
                            <AlertTriangle className="w-4 h-4" />
                            Viva Triggers
                        </h4>
                        <label className="relative inline-flex items-center cursor-pointer">
                            <input
                                type="checkbox"
                                checked={!!assessment.vivaTrigger}
                                onChange={(e) => toggleViva(e.target.checked)}
                                className="sr-only peer"
                            />
                            <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 dark:peer-focus:ring-blue-800 rounded-full peer dark:bg-gray-700 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-blue-600"></div>
                        </label>
                    </div>

                    {assessment.vivaTrigger ? (
                        <div className="space-y-3">
                            <p className="text-sm text-gray-600 dark:text-gray-400">
                                Triggers an oral interview (Viva) when specific patterns are detected.
                            </p>

                            <div className="space-y-2">
                                {(assessment.vivaTrigger.conditions || []).map((cond, i) => (
                                    <div key={i} className="flex items-center gap-2 p-2 bg-amber-50 dark:bg-amber-900/20 rounded border border-amber-100 dark:border-amber-800">
                                        <AlertTriangle className="w-4 h-4 text-amber-600" />
                                        <div className="flex-1 text-sm">
                                            <span className="font-medium text-amber-900 dark:text-amber-300 capitalize">
                                                {cond.type.replace(/_/g, ' ')}
                                            </span>
                                            {cond.threshold && (
                                                <span className="text-amber-700 dark:text-amber-400 ml-1">
                                                    (Threshold: {cond.threshold})
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    ) : (
                        <div className="text-center py-8 text-gray-400 text-sm">
                            Viva triggers disabled.
                            <br />
                            Enable to detect anomalies like overconfidence or cheating.
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

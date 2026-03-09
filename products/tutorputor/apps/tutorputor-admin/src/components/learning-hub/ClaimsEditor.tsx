import React from 'react';
import { CheckCircle, Plus, Trash2, ArrowUp } from 'lucide-react';
import type { Claim } from '@ghatana/tutorputor-contracts/v1/learning-unit';

interface ClaimsEditorProps {
    claims: Claim[];
    onChange: (claims: Claim[]) => void;
}

const BLOOM_LEVELS = ['remember', 'understand', 'apply', 'analyze', 'evaluate', 'create'];

export function ClaimsEditor({ claims, onChange }: ClaimsEditorProps) {
    const handleAddClaim = () => {
        const newId = `C${claims.length + 1}`;
        const newClaim: Claim = {
            id: newId,
            text: '',
            bloom: 'understand',
            prerequisites: [],
        };
        onChange([...claims, newClaim]);
    };

    const handleUpdateClaim = (index: number, updates: Partial<Claim>) => {
        const updated = [...claims];
        updated[index] = { ...updated[index], ...updates };
        onChange(updated);
    };

    const handleRemoveClaim = (index: number) => {
        const updated = [...claims];
        updated.splice(index, 1);
        onChange(updated);
    };

    const togglePrerequisite = (claimIndex: number, prereqId: string) => {
        const claim = claims[claimIndex];
        const currentPrereqs = claim.prerequisites || [];
        const newPrereqs = currentPrereqs.includes(prereqId)
            ? currentPrereqs.filter(id => id !== prereqId)
            : [...currentPrereqs, prereqId];

        handleUpdateClaim(claimIndex, { prerequisites: newPrereqs });
    };

    return (
        <div className="space-y-6">
            <div className="bg-green-50 dark:bg-green-900/20 p-4 rounded-lg border border-green-100 dark:border-green-800">
                <h3 className="text-lg font-medium text-green-900 dark:text-green-300 flex items-center gap-2">
                    <CheckCircle className="w-5 h-5" />
                    Learning Claims
                </h3>
                <p className="text-sm text-green-700 dark:text-green-400 mt-1">
                    What will the learner be able to DO or PROVE? These must be observable.
                </p>
            </div>

            <div className="space-y-4">
                {claims.map((claim, index) => (
                    <div key={claim.id} className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 shadow-sm">
                        <div className="flex items-start gap-4">
                            <div className="pt-2">
                                <span className="flex items-center justify-center w-8 h-8 rounded-full bg-blue-100 text-blue-700 dark:bg-blue-900/50 dark:text-blue-300 font-bold text-sm">
                                    {claim.id}
                                </span>
                            </div>

                            <div className="flex-1 space-y-4">
                                <div>
                                    <label className="block text-xs font-medium text-gray-500 uppercase mb-1">
                                        Claim Text
                                    </label>
                                    <input
                                        type="text"
                                        value={claim.text}
                                        onChange={(e) => handleUpdateClaim(index, { text: e.target.value })}
                                        placeholder="e.g., Predict which object hits the ground first..."
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 bg-transparent"
                                    />
                                </div>

                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-xs font-medium text-gray-500 uppercase mb-1">
                                            Bloom's Taxonomy
                                        </label>
                                        <select
                                            value={claim.bloom}
                                            onChange={(e) => handleUpdateClaim(index, { bloom: e.target.value })}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 bg-transparent capitalize"
                                        >
                                            {BLOOM_LEVELS.map(level => (
                                                <option key={level} value={level}>{level}</option>
                                            ))}
                                        </select>
                                    </div>

                                    <div>
                                        <label className="block text-xs font-medium text-gray-500 uppercase mb-1">
                                            Prerequisites
                                        </label>
                                        <div className="flex flex-wrap gap-2">
                                            {claims.filter(c => c.id !== claim.id).map(other => (
                                                <button
                                                    key={other.id}
                                                    onClick={() => togglePrerequisite(index, other.id)}
                                                    className={`px-2 py-1 text-xs rounded border transition-colors
                                                        ${(claim.prerequisites || []).includes(other.id)
                                                            ? 'bg-blue-100 border-blue-200 text-blue-700 dark:bg-blue-900/30 dark:border-blue-800 dark:text-blue-300'
                                                            : 'bg-gray-50 border-gray-200 text-gray-500 hover:bg-gray-100 dark:bg-gray-800 dark:border-gray-700 dark:text-gray-400'
                                                        }`}
                                                >
                                                    <ArrowUp className="w-3 h-3 inline mr-1" />
                                                    Requires {other.id}
                                                </button>
                                            ))}
                                            {claims.length <= 1 && (
                                                <span className="text-xs text-gray-400 italic py-1">No other claims available</span>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <button
                                onClick={() => handleRemoveClaim(index)}
                                className="text-gray-400 hover:text-red-500 p-2"
                                title="Remove Claim"
                            >
                                <Trash2 className="w-5 h-5" />
                            </button>
                        </div>
                    </div>
                ))}

                <button
                    onClick={handleAddClaim}
                    className="w-full py-3 border-2 border-dashed border-gray-300 dark:border-gray-700 rounded-xl text-gray-500 hover:border-blue-500 hover:text-blue-500 transition-colors flex items-center justify-center gap-2"
                >
                    <Plus className="w-5 h-5" />
                    Add New Claim
                </button>
            </div>
        </div>
    );
}

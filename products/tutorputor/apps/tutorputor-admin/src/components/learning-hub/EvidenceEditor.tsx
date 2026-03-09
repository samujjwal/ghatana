import React from 'react';
import { Eye, Plus, Trash2, Link, List } from 'lucide-react';
import type { Evidence, Claim } from '@ghatana/tutorputor-contracts/v1/learning-unit';

interface EvidenceEditorProps {
    evidence: Evidence[];
    claims: Claim[];
    onChange: (evidence: Evidence[]) => void;
}

const EVIDENCE_TYPES = [
    'prediction_vs_outcome',
    'parameter_targeting',
    'explanation_quality',
    'construction_artifact'
];

const OBSERVABLE_TYPES = ['string', 'number', 'boolean', 'enum', 'vector2d'];

export function EvidenceEditor({ evidence, claims, onChange }: EvidenceEditorProps) {
    const handleAddEvidence = () => {
        const newId = `E${evidence.length + 1}`;
        const newEvidence: Evidence = {
            id: newId,
            claimRef: claims[0]?.id || '',
            type: 'prediction_vs_outcome',
            description: '',
            observables: [],
        };
        onChange([...evidence, newEvidence]);
    };

    const handleUpdateEvidence = (index: number, updates: Partial<Evidence>) => {
        const updated = [...evidence];
        updated[index] = { ...updated[index], ...updates };
        onChange(updated);
    };

    const handleRemoveEvidence = (index: number) => {
        const updated = [...evidence];
        updated.splice(index, 1);
        onChange(updated);
    };

    const handleAddObservable = (evidenceIndex: number) => {
        const ev = evidence[evidenceIndex];
        const newObservable = { name: '', type: 'string' };
        handleUpdateEvidence(evidenceIndex, {
            observables: [...(ev.observables || []), newObservable]
        });
    };

    const handleUpdateObservable = (evidenceIndex: number, obsIndex: number, updates: any) => {
        const ev = evidence[evidenceIndex];
        const newObservables = [...(ev.observables || [])];
        newObservables[obsIndex] = { ...newObservables[obsIndex], ...updates };
        handleUpdateEvidence(evidenceIndex, { observables: newObservables });
    };

    const handleRemoveObservable = (evidenceIndex: number, obsIndex: number) => {
        const ev = evidence[evidenceIndex];
        const newObservables = [...(ev.observables || [])];
        newObservables.splice(obsIndex, 1);
        handleUpdateEvidence(evidenceIndex, { observables: newObservables });
    };

    return (
        <div className="space-y-6">
            <div className="bg-purple-50 dark:bg-purple-900/20 p-4 rounded-lg border border-purple-100 dark:border-purple-800">
                <h3 className="text-lg font-medium text-purple-900 dark:text-purple-300 flex items-center gap-2">
                    <Eye className="w-5 h-5" />
                    Evidence Collection
                </h3>
                <p className="text-sm text-purple-700 dark:text-purple-400 mt-1">
                    What data will we collect to prove the claims? Define observables.
                </p>
            </div>

            <div className="space-y-4">
                {evidence.map((ev, index) => (
                    <div key={ev.id} className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 shadow-sm">
                        <div className="flex items-start gap-4">
                            <div className="pt-2">
                                <span className="flex items-center justify-center w-8 h-8 rounded-full bg-purple-100 text-purple-700 dark:bg-purple-900/50 dark:text-purple-300 font-bold text-sm">
                                    {ev.id}
                                </span>
                            </div>

                            <div className="flex-1 space-y-4">
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-xs font-medium text-gray-500 uppercase mb-1">
                                            Linked Claim
                                        </label>
                                        <div className="relative">
                                            <Link className="absolute left-3 top-2.5 w-4 h-4 text-gray-400" />
                                            <select
                                                value={ev.claimRef}
                                                onChange={(e) => handleUpdateEvidence(index, { claimRef: e.target.value })}
                                                className="w-full pl-9 pr-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 bg-transparent"
                                            >
                                                <option value="">Select Claim...</option>
                                                {claims.map(c => (
                                                    <option key={c.id} value={c.id}>{c.id}: {c.text.substring(0, 40)}...</option>
                                                ))}
                                            </select>
                                        </div>
                                    </div>

                                    <div>
                                        <label className="block text-xs font-medium text-gray-500 uppercase mb-1">
                                            Evidence Type
                                        </label>
                                        <select
                                            value={ev.type}
                                            onChange={(e) => handleUpdateEvidence(index, { type: e.target.value })}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 bg-transparent capitalize"
                                        >
                                            {EVIDENCE_TYPES.map(t => (
                                                <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>
                                            ))}
                                        </select>
                                    </div>
                                </div>

                                <div>
                                    <label className="block text-xs font-medium text-gray-500 uppercase mb-1">
                                        Description
                                    </label>
                                    <input
                                        type="text"
                                        value={ev.description}
                                        onChange={(e) => handleUpdateEvidence(index, { description: e.target.value })}
                                        placeholder="Describe what this evidence represents..."
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 bg-transparent"
                                    />
                                </div>

                                {/* Observables List */}
                                <div className="bg-gray-50 dark:bg-gray-900/50 rounded-lg p-3">
                                    <label className="block text-xs font-medium text-gray-500 uppercase mb-2 flex items-center gap-2">
                                        <List className="w-3 h-3" />
                                        Observables (Data Points)
                                    </label>

                                    <div className="space-y-2">
                                        {ev.observables?.map((obs, obsIndex) => (
                                            <div key={obsIndex} className="flex items-center gap-2">
                                                <input
                                                    type="text"
                                                    value={obs.name}
                                                    onChange={(e) => handleUpdateObservable(index, obsIndex, { name: e.target.value })}
                                                    placeholder="name (e.g. confidence)"
                                                    className="flex-1 px-2 py-1 text-sm border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800"
                                                />
                                                <select
                                                    value={obs.type}
                                                    onChange={(e) => handleUpdateObservable(index, obsIndex, { type: e.target.value })}
                                                    className="w-24 px-2 py-1 text-sm border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800"
                                                >
                                                    {OBSERVABLE_TYPES.map(t => (
                                                        <option key={t} value={t}>{t}</option>
                                                    ))}
                                                </select>
                                                <button
                                                    onClick={() => handleRemoveObservable(index, obsIndex)}
                                                    className="text-gray-400 hover:text-red-500"
                                                >
                                                    <Trash2 className="w-4 h-4" />
                                                </button>
                                            </div>
                                        ))}
                                        <button
                                            onClick={() => handleAddObservable(index)}
                                            className="text-xs text-blue-600 hover:text-blue-700 dark:text-blue-400 flex items-center gap-1 mt-2"
                                        >
                                            <Plus className="w-3 h-3" /> Add Observable
                                        </button>
                                    </div>
                                </div>
                            </div>

                            <button
                                onClick={() => handleRemoveEvidence(index)}
                                className="text-gray-400 hover:text-red-500 p-2"
                                title="Remove Evidence"
                            >
                                <Trash2 className="w-5 h-5" />
                            </button>
                        </div>
                    </div>
                ))}

                <button
                    onClick={handleAddEvidence}
                    className="w-full py-3 border-2 border-dashed border-gray-300 dark:border-gray-700 rounded-xl text-gray-500 hover:border-purple-500 hover:text-purple-500 transition-colors flex items-center justify-center gap-2"
                >
                    <Plus className="w-5 h-5" />
                    Add New Evidence
                </button>
            </div>
        </div>
    );
}

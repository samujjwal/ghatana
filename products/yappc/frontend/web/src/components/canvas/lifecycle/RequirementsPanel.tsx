/**
 * Requirements Panel Component
 *
 * Hierarchical editor for requirements (Epics → Capabilities → Requirements).
 * Supports AI-assisted acceptance criteria generation.
 *
 * @doc.type component
 * @doc.purpose SHAPE phase requirements editor
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useCallback } from 'react';
import { Plus as Add, Minus as Remove, ChevronDown as ExpandMore, ChevronUp as ExpandLess, Sparkles as AutoAwesome, Save, Check } from 'lucide-react';
import type { RequirementsPayload } from '@/shared/types/lifecycle-artifacts';

export interface RequirementsPanelProps {
    data?: RequirementsPayload;
    onSave: (data: RequirementsPayload) => Promise<void>;
    onAIAssist?: (context: { requirements?: RequirementsPayload }) => Promise<Partial<RequirementsPayload> | null>;
    onClose: () => void;
    isLoading?: boolean;
}

interface Epic {
    id: string;
    title: string;
    description?: string;
    capabilities: Capability[];
}

interface Capability {
    id: string;
    title: string;
    requirements: Requirement[];
}

interface Requirement {
    id: string;
    statement: string;
    priority: 'must' | 'should' | 'could' | 'wont';
    acceptanceCriteria: string[];
    nfrTags: string[];
}

const generateId = () => `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

const defaultEpic = (): Epic => ({
    id: generateId(),
    title: '',
    description: '',
    capabilities: [defaultCapability()],
});

const defaultCapability = (): Capability => ({
    id: generateId(),
    title: '',
    requirements: [defaultRequirement()],
});

const defaultRequirement = (): Requirement => ({
    id: generateId(),
    statement: '',
    priority: 'must',
    acceptanceCriteria: [''],
    nfrTags: [],
});

const PRIORITY_COLORS = {
    must: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
    should: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
    could: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
    wont: 'bg-grey-100 text-grey-700 dark:bg-grey-900/30 dark:text-grey-300',
};

const NFR_OPTIONS = ['Performance', 'Security', 'Accessibility', 'Scalability', 'Reliability', 'Usability'];

/**
 * Requirements Panel for SHAPE phase.
 */
export const RequirementsPanel: React.FC<RequirementsPanelProps> = ({
    data,
    onSave,
    onAIAssist,
    onClose,
    isLoading = false,
}) => {
    const [epics, setEpics] = useState<Epic[]>(
        data?.epics?.length ? data.epics : [defaultEpic()]
    );
    const [expandedEpics, setExpandedEpics] = useState<Set<string>>(new Set(epics.map((e) => e.id)));
    const [expandedCapabilities, setExpandedCapabilities] = useState<Set<string>>(new Set());
    const [isSaving, setIsSaving] = useState(false);
    const [isAILoading, setIsAILoading] = useState(false);

    const toggleExpanded = (set: Set<string>, id: string): Set<string> => {
        const next = new Set(set);
        if (next.has(id)) {
            next.delete(id);
        } else {
            next.add(id);
        }
        return next;
    };

    const updateEpic = useCallback((epicId: string, updates: Partial<Epic>) => {
        setEpics((prev) =>
            prev.map((e) => (e.id === epicId ? { ...e, ...updates } : e))
        );
    }, []);

    const addEpic = useCallback(() => {
        const newEpic = defaultEpic();
        setEpics((prev) => [...prev, newEpic]);
        setExpandedEpics((prev) => new Set([...prev, newEpic.id]));
    }, []);

    const removeEpic = useCallback((epicId: string) => {
        setEpics((prev) => prev.filter((e) => e.id !== epicId));
    }, []);

    const updateCapability = useCallback((epicId: string, capId: string, updates: Partial<Capability>) => {
        setEpics((prev) =>
            prev.map((e) => {
                if (e.id !== epicId) return e;
                return {
                    ...e,
                    capabilities: e.capabilities.map((c) =>
                        c.id === capId ? { ...c, ...updates } : c
                    ),
                };
            })
        );
    }, []);

    const addCapability = useCallback((epicId: string) => {
        const newCap = defaultCapability();
        setEpics((prev) =>
            prev.map((e) => {
                if (e.id !== epicId) return e;
                return { ...e, capabilities: [...e.capabilities, newCap] };
            })
        );
        setExpandedCapabilities((prev) => new Set([...prev, newCap.id]));
    }, []);

    const removeCapability = useCallback((epicId: string, capId: string) => {
        setEpics((prev) =>
            prev.map((e) => {
                if (e.id !== epicId) return e;
                return { ...e, capabilities: e.capabilities.filter((c) => c.id !== capId) };
            })
        );
    }, []);

    const updateRequirement = useCallback((epicId: string, capId: string, reqId: string, updates: Partial<Requirement>) => {
        setEpics((prev) =>
            prev.map((e) => {
                if (e.id !== epicId) return e;
                return {
                    ...e,
                    capabilities: e.capabilities.map((c) => {
                        if (c.id !== capId) return c;
                        return {
                            ...c,
                            requirements: c.requirements.map((r) =>
                                r.id === reqId ? { ...r, ...updates } : r
                            ),
                        };
                    }),
                };
            })
        );
    }, []);

    const addRequirement = useCallback((epicId: string, capId: string) => {
        setEpics((prev) =>
            prev.map((e) => {
                if (e.id !== epicId) return e;
                return {
                    ...e,
                    capabilities: e.capabilities.map((c) => {
                        if (c.id !== capId) return c;
                        return { ...c, requirements: [...c.requirements, defaultRequirement()] };
                    }),
                };
            })
        );
    }, []);

    const removeRequirement = useCallback((epicId: string, capId: string, reqId: string) => {
        setEpics((prev) =>
            prev.map((e) => {
                if (e.id !== epicId) return e;
                return {
                    ...e,
                    capabilities: e.capabilities.map((c) => {
                        if (c.id !== capId) return c;
                        return { ...c, requirements: c.requirements.filter((r) => r.id !== reqId) };
                    }),
                };
            })
        );
    }, []);

    const handleAIAssist = useCallback(async () => {
        if (!onAIAssist) return;
        setIsAILoading(true);
        try {
            const result = await onAIAssist({ requirements: { epics } });
            if (result?.epics) {
                setEpics(result.epics);
            }
        } finally {
            setIsAILoading(false);
        }
    }, [onAIAssist, epics]);

    const handleSave = useCallback(async () => {
        setIsSaving(true);
        try {
            await onSave({ epics });
        } finally {
            setIsSaving(false);
        }
    }, [epics, onSave]);

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-divider">
                <div>
                    <h3 className="font-semibold text-text-primary">Requirements</h3>
                    <p className="text-xs text-text-secondary">
                        Epics → Capabilities → Requirements
                    </p>
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
            <div className="flex-1 overflow-auto p-4 space-y-4">
                {epics.map((epic) => (
                    <div key={epic.id} className="border border-divider rounded-lg bg-bg-paper">
                        {/* Epic Header */}
                        <div className="flex items-start gap-2 p-3 border-b border-divider bg-grey-50 dark:bg-grey-800/50 rounded-t-lg">
                            <button
                                onClick={() => setExpandedEpics((prev) => toggleExpanded(prev, epic.id))}
                                className="p-1 text-text-secondary hover:text-text-primary transition-colors mt-0.5"
                            >
                                {expandedEpics.has(epic.id) ? (
                                    <ExpandLess className="w-5 h-5" />
                                ) : (
                                    <ExpandMore className="w-5 h-5" />
                                )}
                            </button>
                            <div className="flex-1">
                                <input
                                    type="text"
                                    value={epic.title}
                                    onChange={(e) => updateEpic(epic.id, { title: e.target.value })}
                                    placeholder="Epic title"
                                    className="w-full px-2 py-1 text-sm font-medium border-none bg-transparent text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-0"
                                />
                                <input
                                    type="text"
                                    value={epic.description || ''}
                                    onChange={(e) => updateEpic(epic.id, { description: e.target.value })}
                                    placeholder="Epic description (optional)"
                                    className="w-full px-2 py-0.5 text-xs border-none bg-transparent text-text-secondary placeholder:text-text-secondary focus:outline-none focus:ring-0"
                                />
                            </div>
                            {epics.length > 1 && (
                                <button
                                    onClick={() => removeEpic(epic.id)}
                                    className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                    aria-label="Remove epic"
                                >
                                    <Remove className="w-4 h-4" />
                                </button>
                            )}
                        </div>

                        {/* Epic Content */}
                        {expandedEpics.has(epic.id) && (
                            <div className="p-3 space-y-3">
                                {epic.capabilities.map((cap) => (
                                    <div key={cap.id} className="border border-divider rounded-lg">
                                        {/* Capability Header */}
                                        <div className="flex items-center gap-2 p-2 bg-grey-50 dark:bg-grey-800/30 rounded-t-lg">
                                            <button
                                                onClick={() =>
                                                    setExpandedCapabilities((prev) => toggleExpanded(prev, cap.id))
                                                }
                                                className="p-0.5 text-text-secondary hover:text-text-primary transition-colors"
                                            >
                                                {expandedCapabilities.has(cap.id) ? (
                                                    <ExpandLess className="w-4 h-4" />
                                                ) : (
                                                    <ExpandMore className="w-4 h-4" />
                                                )}
                                            </button>
                                            <input
                                                type="text"
                                                value={cap.title}
                                                onChange={(e) =>
                                                    updateCapability(epic.id, cap.id, { title: e.target.value })
                                                }
                                                placeholder="Capability title"
                                                className="flex-1 px-2 py-0.5 text-sm border-none bg-transparent text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-0"
                                            />
                                            {epic.capabilities.length > 1 && (
                                                <button
                                                    onClick={() => removeCapability(epic.id, cap.id)}
                                                    className="p-0.5 text-text-secondary hover:text-error-color transition-colors"
                                                    aria-label="Remove capability"
                                                >
                                                    <Remove className="w-4 h-4" />
                                                </button>
                                            )}
                                        </div>

                                        {/* Requirements */}
                                        {expandedCapabilities.has(cap.id) && (
                                            <div className="p-2 space-y-2">
                                                {cap.requirements.map((req, reqIdx) => (
                                                    <div
                                                        key={req.id}
                                                        className="p-2 bg-bg-default rounded border border-divider"
                                                    >
                                                        <div className="flex items-start gap-2 mb-2">
                                                            <span className="text-xs text-text-secondary mt-1">
                                                                R{reqIdx + 1}
                                                            </span>
                                                            <textarea
                                                                value={req.statement}
                                                                onChange={(e) =>
                                                                    updateRequirement(epic.id, cap.id, req.id, {
                                                                        statement: e.target.value,
                                                                    })
                                                                }
                                                                placeholder="Requirement statement"
                                                                rows={2}
                                                                className="flex-1 px-2 py-1 text-sm border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none"
                                                            />
                                                            <select
                                                                value={req.priority}
                                                                onChange={(e) =>
                                                                    updateRequirement(epic.id, cap.id, req.id, {
                                                                        priority: e.target.value as Requirement['priority'],
                                                                    })
                                                                }
                                                                className={`px-2 py-1 text-xs rounded ${PRIORITY_COLORS[req.priority]}`}
                                                            >
                                                                <option value="must">Must</option>
                                                                <option value="should">Should</option>
                                                                <option value="could">Could</option>
                                                                <option value="wont">Won't</option>
                                                            </select>
                                                            {cap.requirements.length > 1 && (
                                                                <button
                                                                    onClick={() =>
                                                                        removeRequirement(epic.id, cap.id, req.id)
                                                                    }
                                                                    className="p-0.5 text-text-secondary hover:text-error-color transition-colors"
                                                                    aria-label="Remove requirement"
                                                                >
                                                                    <Remove className="w-4 h-4" />
                                                                </button>
                                                            )}
                                                        </div>

                                                        {/* NFR Tags */}
                                                        <div className="flex flex-wrap gap-1 mb-2">
                                                            {NFR_OPTIONS.map((nfr) => {
                                                                const isSelected = req.nfrTags.includes(nfr);
                                                                return (
                                                                    <button
                                                                        key={nfr}
                                                                        type="button"
                                                                        onClick={() => {
                                                                            const next = isSelected
                                                                                ? req.nfrTags.filter((t) => t !== nfr)
                                                                                : [...req.nfrTags, nfr];
                                                                            updateRequirement(epic.id, cap.id, req.id, {
                                                                                nfrTags: next,
                                                                            });
                                                                        }}
                                                                        className={`px-1.5 py-0.5 text-xs rounded transition-colors ${isSelected
                                                                                ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                                                                                : 'bg-grey-100 text-grey-600 dark:bg-grey-800 dark:text-grey-400 hover:bg-grey-200 dark:hover:bg-grey-700'
                                                                            }`}
                                                                    >
                                                                        {nfr}
                                                                    </button>
                                                                );
                                                            })}
                                                        </div>

                                                        {/* Acceptance Criteria */}
                                                        <div className="space-y-1">
                                                            <span className="text-xs text-text-secondary">
                                                                Acceptance Criteria
                                                            </span>
                                                            {req.acceptanceCriteria.map((ac, acIdx) => (
                                                                <div key={acIdx} className="flex items-center gap-1">
                                                                    <Check className="w-3 h-3 text-text-secondary" />
                                                                    <input
                                                                        type="text"
                                                                        value={ac}
                                                                        onChange={(e) => {
                                                                            const next = [...req.acceptanceCriteria];
                                                                            next[acIdx] = e.target.value;
                                                                            updateRequirement(epic.id, cap.id, req.id, {
                                                                                acceptanceCriteria: next,
                                                                            });
                                                                        }}
                                                                        placeholder="Given... When... Then..."
                                                                        className="flex-1 px-2 py-0.5 text-xs border border-divider rounded bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-primary-500"
                                                                    />
                                                                    {req.acceptanceCriteria.length > 1 && (
                                                                        <button
                                                                            onClick={() => {
                                                                                const next = req.acceptanceCriteria.filter(
                                                                                    (_, i) => i !== acIdx
                                                                                );
                                                                                updateRequirement(epic.id, cap.id, req.id, {
                                                                                    acceptanceCriteria: next,
                                                                                });
                                                                            }}
                                                                            className="p-0.5 text-text-secondary hover:text-error-color transition-colors"
                                                                        >
                                                                            <Remove className="w-3 h-3" />
                                                                        </button>
                                                                    )}
                                                                </div>
                                                            ))}
                                                            <button
                                                                onClick={() => {
                                                                    updateRequirement(epic.id, cap.id, req.id, {
                                                                        acceptanceCriteria: [...req.acceptanceCriteria, ''],
                                                                    });
                                                                }}
                                                                className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                                                            >
                                                                <Add className="w-3 h-3" /> Add criterion
                                                            </button>
                                                        </div>
                                                    </div>
                                                ))}
                                                <button
                                                    onClick={() => addRequirement(epic.id, cap.id)}
                                                    className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                                                >
                                                    <Add className="w-3 h-3" /> Add requirement
                                                </button>
                                            </div>
                                        )}
                                    </div>
                                ))}
                                <button
                                    onClick={() => addCapability(epic.id)}
                                    className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 transition-colors"
                                >
                                    <Add className="w-4 h-4" /> Add capability
                                </button>
                            </div>
                        )}
                    </div>
                ))}

                <button
                    onClick={addEpic}
                    className="w-full flex items-center justify-center gap-2 p-3 border-2 border-dashed border-divider rounded-lg text-text-secondary hover:text-primary-600 hover:border-primary-300 transition-colors"
                >
                    <Add className="w-5 h-5" /> Add Epic
                </button>
            </div>
        </div>
    );
};

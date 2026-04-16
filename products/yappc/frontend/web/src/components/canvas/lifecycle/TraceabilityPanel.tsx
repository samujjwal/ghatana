/**
 * Traceability Panel Component
 *
 * Artifact dependency graph and traceability matrix visualization.
 *
 * @doc.type component
 * @doc.purpose SHAPE phase traceability and dependency visualization
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useCallback, useMemo } from 'react';
import { GitBranch as AccountTree, Activity as Timeline, Table as TableChart, Link, Unlink as LinkOff, Sparkles as AutoAwesome, RefreshCw as Refresh } from 'lucide-react';
import type { LifecyclePhase } from '@/shared/types/lifecycle';
import { LifecycleArtifactKind, getArtifactsForPhase, LIFECYCLE_ARTIFACT_CATALOG } from '@/shared/types/lifecycle-artifacts';

export interface ArtifactNode {
    id: string;
    kind: LifecycleArtifactKind;
    title: string;
    phase: LifecyclePhase;
    status: 'missing' | 'draft' | 'complete';
    linkedTo: string[]; // IDs of artifacts this links to
}

export interface TraceabilityPanelProps {
    artifacts: ArtifactNode[];
    onLinkArtifacts: (sourceId: string, targetId: string) => Promise<void>;
    onUnlinkArtifacts: (sourceId: string, targetId: string) => Promise<void>;
    onRefresh: () => Promise<void>;
    onAIAnalyze?: (artifacts: ArtifactNode[]) => Promise<{ gaps: string[]; suggestions: string[] } | null>;
    isLoading?: boolean;
}

type ViewMode = 'graph' | 'matrix';

const PHASE_COLORS: Record<LifecyclePhase, string> = {
    [LifecyclePhase.INTENT]: 'bg-blue-100 border-blue-300 dark:bg-blue-900/30 dark:border-blue-700',
    [LifecyclePhase.SHAPE]: 'bg-purple-100 border-purple-300 dark:bg-purple-900/30 dark:border-purple-700',
    [LifecyclePhase.VALIDATE]: 'bg-yellow-100 border-yellow-300 dark:bg-yellow-900/30 dark:border-yellow-700',
    [LifecyclePhase.GENERATE]: 'bg-green-100 border-green-300 dark:bg-green-900/30 dark:border-green-700',
    [LifecyclePhase.RUN]: 'bg-orange-100 border-orange-300 dark:bg-orange-900/30 dark:border-orange-700',
    [LifecyclePhase.OBSERVE]: 'bg-red-100 border-red-300 dark:bg-red-900/30 dark:border-red-700',
    [LifecyclePhase.IMPROVE]: 'bg-pink-100 border-pink-300 dark:bg-pink-900/30 dark:border-pink-700',
};

const STATUS_INDICATORS: Record<string, { label: string; className: string }> = {
    missing: { label: 'M', className: 'bg-grey-200 text-grey-700 dark:bg-grey-700 dark:text-grey-200' },
    draft: { label: 'D', className: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-200' },
    complete: { label: 'C', className: 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-200' },
};

/**
 * Traceability Panel for visualizing artifact dependencies.
 */
export const TraceabilityPanel: React.FC<TraceabilityPanelProps> = ({
    artifacts,
    onLinkArtifacts,
    onUnlinkArtifacts,
    onRefresh,
    onAIAnalyze,
    isLoading = false,
}) => {
    const [viewMode, setViewMode] = useState<ViewMode>('graph');
    const [selectedArtifact, setSelectedArtifact] = useState<string | null>(null);
    const [linkingFrom, setLinkingFrom] = useState<string | null>(null);
    const [aiAnalysis, setAiAnalysis] = useState<{ gaps: string[]; suggestions: string[] } | null>(null);
    const [isAnalyzing, setIsAnalyzing] = useState(false);

    // Group artifacts by phase
    const artifactsByPhase = useMemo(() => {
        const grouped = new Map<LifecyclePhase, ArtifactNode[]>();
        Object.values(LifecyclePhase).forEach((phase) => {
            grouped.set(phase, []);
        });
        artifacts.forEach((artifact) => {
            const phaseArtifacts = grouped.get(artifact.phase) || [];
            phaseArtifacts.push(artifact);
            grouped.set(artifact.phase, phaseArtifacts);
        });
        return grouped;
    }, [artifacts]);

    // Build traceability matrix
    const traceabilityMatrix = useMemo(() => {
        const matrix: { row: ArtifactNode; columns: { artifact: ArtifactNode; linked: boolean }[] }[] = [];
        artifacts.forEach((rowArtifact) => {
            const columns = artifacts.map((colArtifact) => ({
                artifact: colArtifact,
                linked: rowArtifact.linkedTo.includes(colArtifact.id),
            }));
            matrix.push({ row: rowArtifact, columns });
        });
        return matrix;
    }, [artifacts]);

    const handleArtifactClick = useCallback(
        (artifactId: string) => {
            if (linkingFrom) {
                if (linkingFrom !== artifactId) {
                    const source = artifacts.find((a) => a.id === linkingFrom);
                    if (source?.linkedTo.includes(artifactId)) {
                        onUnlinkArtifacts(linkingFrom, artifactId);
                    } else {
                        onLinkArtifacts(linkingFrom, artifactId);
                    }
                }
                setLinkingFrom(null);
            } else {
                setSelectedArtifact((prev) => (prev === artifactId ? null : artifactId));
            }
        },
        [linkingFrom, artifacts, onLinkArtifacts, onUnlinkArtifacts],
    );

    const handleStartLinking = useCallback((artifactId: string) => {
        setLinkingFrom(artifactId);
        setSelectedArtifact(null);
    }, []);

    const handleCancelLinking = useCallback(() => {
        setLinkingFrom(null);
    }, []);

    const handleAIAnalyze = useCallback(async () => {
        if (!onAIAnalyze) return;
        setIsAnalyzing(true);
        try {
            const result = await onAIAnalyze(artifacts);
            setAiAnalysis(result);
        } finally {
            setIsAnalyzing(false);
        }
    }, [onAIAnalyze, artifacts]);

    const selectedArtifactData = useMemo(() => {
        return artifacts.find((a) => a.id === selectedArtifact);
    }, [artifacts, selectedArtifact]);

    const linkedToSelected = useMemo(() => {
        if (!selectedArtifactData) return [];
        return artifacts.filter((a) => selectedArtifactData.linkedTo.includes(a.id));
    }, [artifacts, selectedArtifactData]);

    const linkedFromSelected = useMemo(() => {
        if (!selectedArtifactData) return [];
        return artifacts.filter((a) => a.linkedTo.includes(selectedArtifactData.id));
    }, [artifacts, selectedArtifactData]);

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-divider">
                <div className="flex items-center gap-2">
                    <AccountTree className="w-5 h-5 text-indigo-600" />
                    <div>
                        <h3 className="font-semibold text-text-primary">Traceability</h3>
                        <p className="text-xs text-text-secondary">Artifact dependencies & coverage</p>
                    </div>
                </div>
                <div className="flex gap-2">
                    {onAIAnalyze && (
                        <button
                            onClick={handleAIAnalyze}
                            disabled={isAnalyzing || isLoading}
                            className="flex items-center gap-1 px-3 py-1.5 text-sm font-medium text-text-secondary border border-transparent hover:border-divider hover:text-text-primary hover:bg-grey-50 dark:hover:bg-grey-800/40 rounded-lg transition-colors disabled:opacity-50"
                        >
                            <AutoAwesome className="w-4 h-4" />
                            {isAnalyzing ? 'Analyzing...' : 'AI Analyze'}
                        </button>
                    )}
                    <button
                        onClick={onRefresh}
                        disabled={isLoading}
                        className="flex items-center gap-1 px-3 py-1.5 text-sm text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 rounded-lg transition-colors disabled:opacity-50"
                    >
                        <Refresh className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
                    </button>
                </div>
            </div>

            {/* View Toggle */}
            <div className="flex items-center justify-between px-4 py-2 border-b border-divider bg-grey-50 dark:bg-grey-800/50">
                <div className="flex gap-1">
                    <button
                        onClick={() => setViewMode('graph')}
                        className={`flex items-center gap-1 px-3 py-1 text-xs rounded-lg transition-colors ${viewMode === 'graph'
                                ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                                : 'text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800'
                            }`}
                    >
                        <Timeline className="w-3 h-3" /> Graph View
                    </button>
                    <button
                        onClick={() => setViewMode('matrix')}
                        className={`flex items-center gap-1 px-3 py-1 text-xs rounded-lg transition-colors ${viewMode === 'matrix'
                                ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                                : 'text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800'
                            }`}
                    >
                        <TableChart className="w-3 h-3" /> Matrix View
                    </button>
                </div>
                {linkingFrom && (
                    <div className="flex items-center gap-2">
                        <span className="text-xs text-text-secondary">Click artifact to link...</span>
                        <button
                            onClick={handleCancelLinking}
                            className="text-xs text-error-color hover:text-error-dark"
                        >
                            Cancel
                        </button>
                    </div>
                )}
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-4">
                {viewMode === 'graph' && (
                    <div className="space-y-4">
                        {/* Phase Swimlanes */}
                        {Object.values(LifecyclePhase).map((phase) => {
                            const phaseArtifacts = artifactsByPhase.get(phase) || [];
                            if (phaseArtifacts.length === 0) return null;
                            return (
                                <div key={phase} className="space-y-2">
                                    <h4 className="text-xs font-semibold text-text-secondary uppercase tracking-wider">
                                        {phase}
                                    </h4>
                                    <div className="flex flex-wrap gap-2">
                                        {phaseArtifacts.map((artifact) => (
                                            <button
                                                key={artifact.id}
                                                onClick={() => handleArtifactClick(artifact.id)}
                                                className={`relative px-3 py-2 text-sm rounded-lg border-2 transition-all ${PHASE_COLORS[artifact.phase]
                                                    } ${selectedArtifact === artifact.id
                                                        ? 'ring-2 ring-primary-500 ring-offset-2'
                                                        : ''
                                                    } ${linkingFrom === artifact.id
                                                        ? 'ring-2 ring-blue-500 ring-offset-2'
                                                        : ''
                                                    } ${linkingFrom && linkingFrom !== artifact.id
                                                        ? 'hover:ring-2 hover:ring-green-500 cursor-crosshair'
                                                        : 'hover:shadow-md'
                                                    }`}
                                            >
                                                <div className="flex items-center gap-2">
                                                    <span
                                                        className={`inline-flex items-center justify-center w-5 h-5 rounded text-[10px] font-semibold ${STATUS_INDICATORS[artifact.status].className}`}
                                                    >
                                                        {STATUS_INDICATORS[artifact.status].label}
                                                    </span>
                                                    <span className="font-medium text-text-primary">
                                                        {artifact.title}
                                                    </span>
                                                </div>
                                                {artifact.linkedTo.length > 0 && (
                                                    <div className="absolute -bottom-1 -right-1 w-5 h-5 flex items-center justify-center bg-primary-500 text-white text-xs rounded-full">
                                                        {artifact.linkedTo.length}
                                                    </div>
                                                )}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            );
                        })}

                        {/* Selected Artifact Details */}
                        {selectedArtifactData && (
                            <div className="mt-4 p-4 border border-divider rounded-lg bg-bg-paper space-y-3">
                                <div className="flex items-center justify-between">
                                    <h4 className="text-sm font-semibold text-text-primary">{selectedArtifactData.title}</h4>
                                    <button
                                        onClick={() => handleStartLinking(selectedArtifactData.id)}
                                        className="flex items-center gap-1 px-2 py-1 text-xs text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded transition-colors"
                                    >
                                        <Link className="w-3 h-3" /> Create Link
                                    </button>
                                </div>
                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <h5 className="text-xs text-text-secondary mb-1">Links To ({linkedToSelected.length})</h5>
                                        {linkedToSelected.length === 0 ? (
                                            <p className="text-xs text-text-secondary italic">No outgoing links yet</p>
                                        ) : (
                                            <div className="space-y-1">
                                                {linkedToSelected.map((linked) => (
                                                    <div
                                                        key={linked.id}
                                                        className="flex items-center justify-between text-xs bg-grey-50 dark:bg-grey-800/50 px-2 py-1 rounded"
                                                    >
                                                        <span className="text-text-primary">{linked.title}</span>
                                                        <button
                                                            onClick={() => onUnlinkArtifacts(selectedArtifactData.id, linked.id)}
                                                            className="text-error-color hover:text-error-dark"
                                                        >
                                                            <LinkOff className="w-3 h-3" />
                                                        </button>
                                                    </div>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                    <div>
                                        <h5 className="text-xs text-text-secondary mb-1">Links From ({linkedFromSelected.length})</h5>
                                        {linkedFromSelected.length === 0 ? (
                                            <p className="text-xs text-text-secondary italic">No incoming links yet</p>
                                        ) : (
                                            <div className="space-y-1">
                                                {linkedFromSelected.map((linked) => (
                                                    <div
                                                        key={linked.id}
                                                        className="flex items-center justify-between text-xs bg-grey-50 dark:bg-grey-800/50 px-2 py-1 rounded"
                                                    >
                                                        <span className="text-text-primary">{linked.title}</span>
                                                        <button
                                                            onClick={() => onUnlinkArtifacts(linked.id, selectedArtifactData.id)}
                                                            className="text-error-color hover:text-error-dark"
                                                        >
                                                            <LinkOff className="w-3 h-3" />
                                                        </button>
                                                    </div>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {viewMode === 'matrix' && (
                    <div className="overflow-auto">
                        <table className="min-w-full border-collapse">
                            <thead>
                                <tr>
                                        <th className="sticky top-0 left-0 z-20 bg-bg-default p-2 text-[11px] font-semibold text-text-secondary border border-divider">
                                            From \ To
                                        </th>
                                    {artifacts.map((artifact) => (
                                        <th
                                            key={artifact.id}
                                            className="sticky top-0 z-10 bg-bg-default p-2 text-[11px] font-medium text-text-secondary border border-divider whitespace-nowrap"
                                        >
                                            <div className="flex flex-col items-center">
                                                <span
                                                    className={`inline-flex items-center justify-center w-5 h-5 rounded text-[10px] font-semibold ${STATUS_INDICATORS[artifact.status].className}`}
                                                >
                                                    {STATUS_INDICATORS[artifact.status].label}
                                                </span>
                                                <span className="truncate max-w-28" title={artifact.title}>
                                                    {artifact.title}
                                                </span>
                                            </div>
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {traceabilityMatrix.map(({ row, columns }) => (
                                    <tr key={row.id}>
                                        <td className="sticky left-0 z-10 bg-bg-default p-2 text-[11px] font-medium text-text-primary border border-divider whitespace-nowrap">
                                            <div className="flex items-center gap-2">
                                                <span
                                                    className={`inline-flex items-center justify-center w-5 h-5 rounded text-[10px] font-semibold ${STATUS_INDICATORS[row.status].className}`}
                                                >
                                                    {STATUS_INDICATORS[row.status].label}
                                                </span>
                                                <span className="truncate max-w-36" title={row.title}>
                                                    {row.title}
                                                </span>
                                            </div>
                                        </td>
                                        {columns.map(({ artifact, linked }) => (
                                            <td
                                                key={`${row.id}-${artifact.id}`}
                                                className={`p-2 text-center border border-divider ${row.id === artifact.id
                                                        ? 'bg-grey-200 dark:bg-grey-700'
                                                        : linked
                                                            ? 'bg-green-100 dark:bg-green-900/30 cursor-pointer hover:bg-green-200 dark:hover:bg-green-900/50'
                                                            : 'cursor-pointer hover:bg-grey-100 dark:hover:bg-grey-800'
                                                    }`}
                                                onClick={() => {
                                                    if (row.id !== artifact.id) {
                                                        if (linked) {
                                                            onUnlinkArtifacts(row.id, artifact.id);
                                                        } else {
                                                            onLinkArtifacts(row.id, artifact.id);
                                                        }
                                                    }
                                                }}
                                            >
                                                {row.id === artifact.id ? (
                                                    <span className="text-grey-400">—</span>
                                                ) : linked ? (
                                                    <Link className="w-4 h-4 text-green-600 mx-auto" />
                                                ) : (
                                                    <span className="text-grey-300 dark:text-grey-600">·</span>
                                                )}
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {/* AI Analysis Results */}
                {aiAnalysis && (
                    <div className="mt-4 p-4 border border-primary-200 dark:border-primary-800 rounded-lg bg-primary-50 dark:bg-primary-900/20 space-y-3">
                        <h4 className="font-medium text-text-primary flex items-center gap-2">
                            <AutoAwesome className="w-4 h-4 text-primary-600" /> AI Analysis
                        </h4>
                        {aiAnalysis.gaps.length > 0 && (
                            <div>
                                <h5 className="text-xs font-medium text-text-secondary mb-1">Coverage Gaps</h5>
                                <ul className="list-disc list-inside text-sm text-text-primary space-y-1">
                                    {aiAnalysis.gaps.map((gap, idx) => (
                                        <li key={idx}>{gap}</li>
                                    ))}
                                </ul>
                            </div>
                        )}
                        {aiAnalysis.suggestions.length > 0 && (
                            <div>
                                <h5 className="text-xs font-medium text-text-secondary mb-1">Suggestions</h5>
                                <ul className="list-disc list-inside text-sm text-text-primary space-y-1">
                                    {aiAnalysis.suggestions.map((suggestion, idx) => (
                                        <li key={idx}>{suggestion}</li>
                                    ))}
                                </ul>
                            </div>
                        )}
                        <button
                            onClick={() => setAiAnalysis(null)}
                            className="text-xs text-text-secondary hover:text-text-primary"
                        >
                            Dismiss
                        </button>
                    </div>
                )}
            </div>

            {/* Legend */}
            <div className="px-4 py-2 border-t border-divider bg-grey-50 dark:bg-grey-800/50">
                <div className="flex flex-wrap gap-4 text-xs text-text-secondary">
                    {(['missing', 'draft', 'complete'] as const).map((status) => (
                        <span key={status} className="inline-flex items-center gap-2">
                            <span
                                className={`inline-flex items-center justify-center w-5 h-5 rounded text-[10px] font-semibold ${STATUS_INDICATORS[status].className}`}
                            >
                                {STATUS_INDICATORS[status].label}
                            </span>
                            {status === 'missing' ? 'Missing' : status === 'draft' ? 'Draft' : 'Complete'}
                        </span>
                    ))}
                </div>
            </div>
        </div>
    );
};

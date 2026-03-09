/**
 * Lifecycle Explorer Component
 *
 * Interactive explorer for all 7 lifecycle phases, artifacts, and phase gates.
 * Provides a complete overview of the FOW lifecycle with drill-down capabilities.
 *
 * @doc.type component
 * @doc.purpose Visual navigator for all lifecycle phases and artifacts
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useMemo, useEffect } from 'react';
import { ChevronRight, Circle, Lock, CheckCircle, Info, Copy as ContentCopy } from 'lucide-react';
import { useLifecycleArtifacts, usePhaseGates } from '../../services/canvas/lifecycle';
import { useSearchParams } from 'react-router';
import { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';
import type { LifecyclePhase } from '@/shared/types/lifecycle';
import { ArtifactDetailPanel } from '../shared/ArtifactDetailPanel';
import { FilterPanel, type FilterConfig } from '../shared/FilterPanel';
import { AISuggestionPanel } from '../shared/AISuggestionPanel';
import { LifecycleBreadcrumb } from './LifecycleBreadcrumb';
import { PhaseContextPanel } from './PhaseContextPanel';
import { parseLifecycleURL, updateLifecycleURL, scrollToPhase, scrollToArtifact, copyLifecycleLink } from '../../utils/lifecycleDeepLinking';
import { ExportButton } from '../shared/ExportButton';
import type { ArtifactSuggestion, SuggestionContext } from '../../services/ai';
import { exportLifecycleReport, type LifecycleExportData } from '../../services/export/LifecycleExportService';
import { PHASE_LABELS, PHASE_DESCRIPTIONS, PHASE_COLORS } from '../../styles/design-tokens';

interface LifecycleExplorerProps {
    projectId: string;
    onPhaseSelect?: (phase: LifecyclePhase) => void;
    onArtifactSelect?: (kind: LifecycleArtifactKind) => void;
}

interface PhaseGroup {
    phase: LifecyclePhase;
    title: string;
    description: string;
    artifacts: LifecycleArtifactKind[];
    gateCount: number;
    color: string;
    icon: string;
}

const PHASE_GROUPS: PhaseGroup[] = [
    {
        phase: 'INTENT' as LifecyclePhase,
        title: `1. ${PHASE_LABELS.INTENT}`,
        description: PHASE_DESCRIPTIONS.INTENT,
        artifacts: [LifecycleArtifactKind.IDEA_BRIEF, LifecycleArtifactKind.RESEARCH_PACK, LifecycleArtifactKind.PROBLEM_STATEMENT],
        gateCount: 1,
        color: PHASE_COLORS.INTENT.gradient,
        icon: PHASE_COLORS.INTENT.icon,
    },
    {
        phase: 'SHAPE' as LifecyclePhase,
        title: `2. ${PHASE_LABELS.SHAPE}`,
        description: PHASE_DESCRIPTIONS.SHAPE,
        artifacts: [LifecycleArtifactKind.REQUIREMENTS, LifecycleArtifactKind.ADR, LifecycleArtifactKind.UX_SPEC, LifecycleArtifactKind.THREAT_MODEL],
        gateCount: 1,
        color: PHASE_COLORS.SHAPE.gradient,
        icon: PHASE_COLORS.SHAPE.icon,
    },
    {
        phase: 'VALIDATE' as LifecyclePhase,
        title: `3. ${PHASE_LABELS.VALIDATE}`,
        description: PHASE_DESCRIPTIONS.VALIDATE,
        artifacts: [LifecycleArtifactKind.VALIDATION_REPORT, LifecycleArtifactKind.SIMULATION_RESULTS],
        gateCount: 1,
        color: PHASE_COLORS.VALIDATE.gradient,
        icon: PHASE_COLORS.VALIDATE.icon,
    },
    {
        phase: 'GENERATE' as LifecyclePhase,
        title: `4. ${PHASE_LABELS.GENERATE}`,
        description: PHASE_DESCRIPTIONS.GENERATE,
        artifacts: [LifecycleArtifactKind.DELIVERY_PLAN, LifecycleArtifactKind.RELEASE_STRATEGY],
        gateCount: 1,
        color: PHASE_COLORS.GENERATE.gradient,
        icon: PHASE_COLORS.GENERATE.icon,
    },
    {
        phase: 'RUN' as LifecyclePhase,
        title: `5. ${PHASE_LABELS.RUN}`,
        description: PHASE_DESCRIPTIONS.RUN,
        artifacts: [LifecycleArtifactKind.EVIDENCE_PACK, LifecycleArtifactKind.RELEASE_PACKET],
        gateCount: 1,
        color: PHASE_COLORS.RUN.gradient,
        icon: PHASE_COLORS.RUN.icon,
    },
    {
        phase: 'OBSERVE' as LifecyclePhase,
        title: `6. ${PHASE_LABELS.OBSERVE}`,
        description: PHASE_DESCRIPTIONS.OBSERVE,
        artifacts: [LifecycleArtifactKind.OPS_BASELINE, LifecycleArtifactKind.INCIDENT_REPORT],
        gateCount: 1,
        color: PHASE_COLORS.OBSERVE.gradient,
        icon: PHASE_COLORS.OBSERVE.icon,
    },
    {
        phase: 'IMPROVE' as LifecyclePhase,
        title: `7. ${PHASE_LABELS.IMPROVE}`,
        description: PHASE_DESCRIPTIONS.IMPROVE,
        artifacts: [LifecycleArtifactKind.ENHANCEMENT_REQUESTS, LifecycleArtifactKind.LEARNING_RECORD],
        gateCount: 0,
        color: PHASE_COLORS.IMPROVE.gradient,
        icon: PHASE_COLORS.IMPROVE.icon,
    },
];

export const LifecycleExplorer: React.FC<LifecycleExplorerProps> = ({
    projectId,
    onPhaseSelect,
    onArtifactSelect,
}) => {
    const [searchParams] = useSearchParams();
    const [expandedPhase, setExpandedPhase] = useState<LifecyclePhase | null>(null);
    const [selectedArtifactId, setSelectedArtifactId] = useState<string | null>(null);
    const [filterValues, setFilterValues] = useState<Record<string, unknown>>({
        search: '',
        phase: '',
        status: '',
        showMissingOnly: false,
    });
    const [copiedLink, setCopiedLink] = useState(false);

    // Load artifacts and phase gates
    const { artifacts, loading: artifactsLoading, deleteArtifact, createArtifact } = useLifecycleArtifacts(projectId);
    const { currentPhase, gateStatuses, loading: gatesLoading } = usePhaseGates(projectId);

    // Initialize from URL params (deep linking)
    useEffect(() => {
        const urlState = parseLifecycleURL(searchParams);
        if (urlState.phase) {
            setExpandedPhase(urlState.phase);
            setTimeout(() => scrollToPhase(urlState.phase!), 100);
        }
        if (urlState.artifactId) {
            setSelectedArtifactId(urlState.artifactId);
            setTimeout(() => scrollToArtifact(urlState.artifactId!), 200);
        }
    }, []);

    // Update URL when artifact is selected
    useEffect(() => {
        if (selectedArtifactId) {
            const artifact = artifacts.find(a => a.id === selectedArtifactId);
            if (artifact) {
                updateLifecycleURL({ phase: artifact.phase, artifactId: artifact.id, artifactKind: artifact.kind });
            }
        } else if (expandedPhase) {
            updateLifecycleURL({ phase: expandedPhase });
        }
    }, [selectedArtifactId, expandedPhase, artifacts]);

    // AI suggestion context
    const suggestionContext: SuggestionContext = {
        projectId,
        currentPhase: currentPhase || 'INTENT',
        existingArtifacts: artifacts.map(a => ({ kind: a.kind, payload: {} })),
    };

    // Filter configuration
    const filterConfig: FilterConfig[] = [
        {
            id: 'search',
            label: 'Search',
            type: 'search',
            placeholder: 'Search artifacts...',
        },
        {
            id: 'phase',
            label: 'Phase',
            type: 'select',
            options: PHASE_GROUPS.map(g => ({ label: g.title, value: g.phase })),
        },
        {
            id: 'status',
            label: 'Status',
            type: 'select',
            options: [
                { label: 'Draft', value: 'draft' },
                { label: 'Complete', value: 'complete' },
                { label: 'Validated', value: 'validated' },
                { label: 'Archived', value: 'archived' },
            ],
        },
        {
            id: 'showMissingOnly',
            label: 'Show Missing Only',
            type: 'toggle',
            placeholder: 'Show only missing artifacts',
        },
    ];

    // Apply filters
    const filteredArtifacts = useMemo(() => {
        let filtered = [...artifacts];

        // Search filter
        if (filterValues.search) {
            const search = filterValues.search.toLowerCase();
            filtered = filtered.filter(a => a.title.toLowerCase().includes(search));
        }

        // Phase filter
        if (filterValues.phase) {
            filtered = filtered.filter(a => a.phase === filterValues.phase);
        }

        // Status filter
        if (filterValues.status) {
            filtered = filtered.filter(a => a.status === filterValues.status);
        }

        return filtered;
    }, [artifacts, filterValues]);

    const selectedArtifact = selectedArtifactId
        ? artifacts.find(a => a.id === selectedArtifactId)
        : null;

    // Group artifacts by phase (using filtered artifacts)
    const artifactsByPhase = useMemo(() => {
        const grouped = new Map<LifecyclePhase, typeof filteredArtifacts>();
        filteredArtifacts.forEach((artifact) => {
            if (!grouped.has(artifact.phase)) {
                grouped.set(artifact.phase, []);
            }
            grouped.get(artifact.phase)!.push(artifact);
        });
        return grouped;
    }, [filteredArtifacts]);

    const handleArtifactClick = (artifactId: string, kind: LifecycleArtifactKind) => {
        setSelectedArtifactId(artifactId);
        onArtifactSelect?.(kind);
    };

    const handleFilterChange = (id: string, value: unknown) => {
        setFilterValues(prev => ({ ...prev, [id]: value }));
    };

    const handleClearFilters = () => {
        setFilterValues({
            search: '',
            phase: '',
            status: '',
            showMissingOnly: false,
        });
    };

    const handleAcceptSuggestion = async (suggestion: ArtifactSuggestion) => {
        try {
            const userId = 'current-user'; // NOTE: Get from auth context
            await createArtifact?.(suggestion.kind, userId);
        } catch (err) {
            console.error('Failed to create artifact from suggestion:', err);
            alert('Failed to create artifact. Please try again.');
        }
    };

    const handleExport = async (format: 'json' | 'markdown' | 'pdf') => {
        const exportData: LifecycleExportData = {
            projectId,
            exportDate: new Date().toISOString(),
            phases: PHASE_GROUPS.map(group => ({
                phase: group.phase,
                artifacts: artifacts
                    .filter(a => a.phase === group.phase)
                    .map(a => ({
                        id: a.id,
                        kind: a.kind,
                        title: a.title,
                        status: a.status,
                        payload: {},
                        createdAt: new Date().toISOString(),
                        updatedAt: a.updatedAt,
                    })),
            })),
        };

        try {
            await exportLifecycleReport(exportData, format);
        } catch (err) {
            console.error('Export failed:', err);
            alert('Export failed. Please try again.');
        }
    };

    const isPhaseActive = (phase: LifecyclePhase) => currentPhase === phase;
    const isPhaseCompleted = (phase: LifecyclePhase) => {
        const phaseOrder = ['INTENT', 'SHAPE', 'VALIDATE', 'GENERATE', 'RUN', 'OBSERVE', 'IMPROVE'];
        return phaseOrder.indexOf(phase) < phaseOrder.indexOf(currentPhase || 'INTENT');
    };

    const handleCopyLink = async (artifactId: string, kind: LifecycleArtifactKind) => {
        const artifact = artifacts.find(a => a.id === artifactId);
        const success = await copyLifecycleLink(projectId, {
            phase: artifact?.phase,
            artifactId,
            artifactKind: kind,
        });
        if (success) {
            setCopiedLink(true);
            setTimeout(() => setCopiedLink(false), 2000);
        }
    };

    const handlePhaseClick = (phase: LifecyclePhase) => {
        if (expandedPhase === phase) {
            setExpandedPhase(null);
            updateLifecycleURL({});
        } else {
            setExpandedPhase(phase);
            updateLifecycleURL({ phase });
            setTimeout(() => scrollToPhase(phase), 100);
        }
        onPhaseSelect?.(phase);
    };

    return (
        <div className="w-full max-w-6xl mx-auto p-6 space-y-6">
            {/* Breadcrumb Navigation */}
            <LifecycleBreadcrumb
                projectName="Project"
                currentPhase={expandedPhase || undefined}
                selectedArtifactKind={selectedArtifact?.kind}
                selectedArtifactTitle={selectedArtifact?.title}
                onNavigateToRoot={() => {
                    setExpandedPhase(null);
                    setSelectedArtifactId(null);
                    updateLifecycleURL({});
                }}
                onNavigateToPhase={(phase) => {
                    setExpandedPhase(phase);
                    setSelectedArtifactId(null);
                    updateLifecycleURL({ phase });
                    scrollToPhase(phase);
                }}
                onClearArtifact={() => {
                    setSelectedArtifactId(null);
                    updateLifecycleURL({ phase: expandedPhase || undefined });
                }}
            />

            {/* Copy Link Confirmation */}
            {copiedLink && (
                <div className="fixed top-4 right-4 bg-success-500 text-white px-4 py-2 rounded-lg shadow-lg flex items-center gap-2 z-50 animate-fade-in">
                    <CheckCircle className="w-5 h-5" />
                    <span>Link copied to clipboard!</span>
                </div>
            )}

            {/* Header */}
            <div className="flex items-center justify-between">
                <div className="flex-1">
                    <h1 className="text-3xl font-bold text-text-primary">
                        FOW Lifecycle Explorer
                    </h1>
                    <p className="text-text-secondary">
                        Navigate through all 7 phases, view artifacts, and understand phase transitions
                    </p>
                </div>
                <ExportButton onExport={handleExport} />
            </div>

            {/* Filters */}
            <FilterPanel
                filters={filterConfig}
                values={filterValues}
                onChange={handleFilterChange}
                onClear={handleClearFilters}
            />

            {/* AI Suggestions */}
            <AISuggestionPanel
                context={suggestionContext}
                onAccept={handleAcceptSuggestion}
            />

            {/* Current Phase Indicator */}
            <div className="bg-bg-paper border border-divider rounded-lg p-4">
                <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <Circle
                            style={{
                                color: currentPhase
                                    ? `var(--color-${currentPhase.toLowerCase()}-500)`
                                    : 'var(--color-primary-500)',
                            }}
                        />
                        <div>
                            <div className="text-sm font-medium text-text-secondary">Current Phase</div>
                            <div className="text-xl font-semibold text-text-primary">
                                {currentPhase ? PHASE_GROUPS.find(p => p.phase === currentPhase)?.title : 'Not started'}
                            </div>
                        </div>
                    </div>
                    <div className="text-right text-sm text-text-secondary">
                        <div>{filteredArtifacts.length} of {artifacts.length} artifacts</div>
                        <div className="text-xs">showing</div>
                    </div>
                </div>
            </div>

            {/* Phase Timeline */}
            <div className="space-y-3">
                {PHASE_GROUPS.map((group, index) => {
                    const phaseArtifacts = artifactsByPhase.get(group.phase) || [];
                    const isExpanded = expandedPhase === group.phase;
                    const isActive = isPhaseActive(group.phase);
                    const isCompleted = isPhaseCompleted(group.phase);
                    const phaseGates = Object.entries(gateStatuses)
                        .filter(([, status]: [string, any]) => status.phase === group.phase)
                        .map(([id, status]: [string, any]) => ({ id, ...status }));

                    return (
                        <div key={group.phase} id={`phase-${group.phase}`} className="space-y-2 scroll-mt-6">
                            {/* Phase Card */}
                            <button
                                onClick={() => handlePhaseClick(group.phase)}
                                className={`w-full text-left p-4 rounded-lg border transition-all ${isExpanded
                                    ? `border-primary-500 bg-gradient-to-r ${group.color} bg-opacity-10`
                                    : 'border-divider bg-bg-paper hover:bg-grey-50 dark:hover:bg-grey-800/50'
                                    } ${isActive ? 'ring-2 ring-primary-500 ring-offset-2' : ''}`}
                            >
                                <div className="flex items-start justify-between">
                                    <div className="flex items-start gap-4 flex-1">
                                        {/* Phase Number and Icon */}
                                        <div className="flex items-center gap-2 mt-0.5">
                                            <div
                                                className={`w-10 h-10 rounded-full flex items-center justify-center text-lg font-bold text-white bg-gradient-to-r ${group.color}`}
                                            >
                                                {group.icon}
                                            </div>
                                            {isCompleted && <CheckCircle className="text-green-500" />}
                                            {isActive && <Info className="text-primary-500" />}
                                        </div>

                                        {/* Phase Info */}
                                        <div className="flex-1 min-w-0">
                                            <h3 className="font-semibold text-text-primary">{group.title}</h3>
                                            <p className="text-sm text-text-secondary mt-0.5">{group.description}</p>
                                            <div className="flex gap-3 mt-2 text-xs text-text-secondary">
                                                <span>📦 {phaseArtifacts.length} of {group.artifacts.length}</span>
                                                <span>🚪 {phaseGates.length} Gates</span>
                                            </div>
                                        </div>
                                    </div>

                                    <ChevronRight className={`transition-transform mt-1 ${isExpanded ? 'rotate-90' : ''}`} />
                                </div>
                            </button>

                            {/* Expanded Content */}
                            {isExpanded && (
                                <div className="ml-8 space-y-3 pb-3">
                                    {/* Phase Context Panel */}
                                    <PhaseContextPanel
                                        phase={group.phase}
                                        existingArtifacts={phaseArtifacts.map(a => a.kind)}
                                        onActionClick={(_action, artifactKind) => {
                                            if (artifactKind) {
                                                const artifact = phaseArtifacts.find(a => a.kind === artifactKind);
                                                if (artifact) {
                                                    handleArtifactClick(artifact.id, artifact.kind);
                                                }
                                            }
                                        }}
                                    />

                                    {/* Artifacts Section */}
                                    {phaseArtifacts.length > 0 && (
                                        <div className="space-y-2">
                                            <h4 className="text-sm font-semibold text-text-primary flex items-center gap-2">
                                                📋 Artifacts ({phaseArtifacts.length})
                                            </h4>
                                            <div className="space-y-1">
                                                {phaseArtifacts.map((artifact) => (
                                                    <div
                                                        key={artifact.id}
                                                        id={`artifact-${artifact.id}`}
                                                        className={`group relative rounded border transition-all ${selectedArtifactId === artifact.id
                                                            ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                                                            : 'border-divider bg-bg-default hover:bg-grey-50 dark:hover:bg-grey-800/50'
                                                            }`}
                                                    >
                                                        <button
                                                            onClick={() => handleArtifactClick(artifact.id, artifact.kind)}
                                                            className="w-full text-left p-3 flex items-start gap-3"
                                                        >
                                                            <div className="text-lg">✓</div>
                                                            <div className="flex-1 min-w-0">
                                                                <div className="text-sm font-medium text-text-primary">
                                                                    {artifact.title}
                                                                </div>
                                                                <div className="text-xs text-text-secondary mt-1 flex gap-2">
                                                                    <span>Status: {artifact.status}</span>
                                                                    <span>Updated: {new Date(artifact.updatedAt).toLocaleDateString()}</span>
                                                                </div>
                                                            </div>
                                                        </button>
                                                        {/* Copy Link Button */}
                                                        <button
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                handleCopyLink(artifact.id, artifact.kind);
                                                            }}
                                                            className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity p-2 rounded hover:bg-grey-100 dark:hover:bg-grey-700"
                                                            title="Copy link to artifact"
                                                        >
                                                            <ContentCopy className="w-4 h-4 text-text-secondary hover:text-primary-500" />
                                                        </button>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    )}

                                    {/* Phase Gates Section */}
                                    {phaseGates.length > 0 && (
                                        <div className="space-y-2">
                                            <h4 className="text-sm font-semibold text-text-primary flex items-center gap-2">
                                                🚪 Phase Gates
                                            </h4>
                                            <div className="space-y-1">
                                                {phaseGates.map((gate: unknown) => (
                                                    <div
                                                        key={gate.id}
                                                        className={`p-3 rounded border ${gate.status === 'passed'
                                                            ? 'border-green-300 bg-green-50 dark:bg-green-900/20'
                                                            : gate.status === 'failed'
                                                                ? 'border-red-300 bg-red-50 dark:bg-red-900/20'
                                                                : 'border-amber-300 bg-amber-50 dark:bg-amber-900/20'
                                                            }`}
                                                    >
                                                        <div className="text-sm font-medium text-text-primary flex items-center gap-2">
                                                            {gate.status === 'passed' && '✓'}
                                                            {gate.status === 'failed' && '✗'}
                                                            {gate.status === 'blocked' && <Lock className="w-4 h-4" />}
                                                            {gate.name || gate.id}
                                                        </div>
                                                        {gate.blockers && gate.blockers.length > 0 && (
                                                            <div className="text-xs text-text-secondary mt-1">
                                                                Blockers: {gate.blockers.join(', ')}
                                                            </div>
                                                        )}
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    )}

                                    {/* Next Actions */}
                                    {isActive && (
                                        <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-300 rounded p-3">
                                            <div className="text-sm font-semibold text-blue-900 dark:text-blue-100 mb-2">
                                                Next Steps
                                            </div>
                                            <ul className="text-sm text-blue-800 dark:text-blue-200 space-y-1 list-disc list-inside">
                                                <li>Create missing artifacts</li>
                                                <li>Satisfy phase gates</li>
                                                <li>Complete validation</li>
                                                <li>Transition to next phase</li>
                                            </ul>
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* Phase Connector */}
                            {index < PHASE_GROUPS.length - 1 && (
                                <div className="flex justify-center py-1">
                                    <ChevronRight className="text-text-secondary rotate-90" />
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>

            {/* Legend */}
            <div className="bg-bg-paper border border-divider rounded-lg p-4 mt-8">
                <h3 className="text-sm font-semibold text-text-primary mb-3">Legend</h3>
                <div className="grid grid-cols-2 gap-4 text-sm">
                    <div className="flex items-center gap-2">
                        <CheckCircle className="text-green-500 w-5 h-5" />

                        {/* Artifact Detail Panel */}
                        {selectedArtifact && (
                            <ArtifactDetailPanel
                                artifactId={selectedArtifact.id}
                                kind={selectedArtifact.kind}
                                title={selectedArtifact.title}
                                status={selectedArtifact.status}
                                payload={{}}
                                createdAt={new Date().toISOString()}
                                updatedAt={selectedArtifact.updatedAt}
                                onClose={() => setSelectedArtifactId(null)}
                                onSave={() => {
                                    setSelectedArtifactId(null);
                                }}
                                onDelete={() => {
                                    if (confirm('Are you sure you want to delete this artifact?')) {
                                        deleteArtifact?.(selectedArtifact.id);
                                        setSelectedArtifactId(null);
                                    }
                                }}
                            />
                        )}
                        <span className="text-text-secondary">Completed Phase</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <Info className="text-primary-500 w-5 h-5" />
                        <span className="text-text-secondary">Active Phase</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-lg">✓</span>
                        <span className="text-text-secondary">Artifact Exists</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-lg">○</span>
                        <span className="text-text-secondary">Missing Artifact</span>
                    </div>
                </div>
            </div>

            {/* Loading State */}
            {(artifactsLoading || gatesLoading) && (
                <div className="text-center py-8">
                    <div className="text-text-secondary">Loading lifecycle data...</div>
                </div>
            )}
        </div>
    );
};

export default LifecycleExplorer;

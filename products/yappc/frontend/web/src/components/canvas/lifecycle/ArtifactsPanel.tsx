/**
 * Artifacts Panel Component
 *
 * Lists all lifecycle artifacts for the current project.
 * Provides entry points to specific artifact editors.
 *
 * @doc.type component
 * @doc.purpose Canvas lifecycle artifacts overview
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useMemo } from 'react';
import { useSearchParams } from 'react-router';
import { Plus as Add, ChevronRight, Check, Clock as Schedule, Share2 as Share } from 'lucide-react';
import {
    LifecycleArtifactKind,
    LIFECYCLE_ARTIFACT_CATALOG,
    getArtifactsForSurface,
} from '@/shared/types/lifecycle-artifacts';
import { LifecyclePhase, PHASE_LABELS } from '../../../types/lifecycle';
import { Button } from '../../ui/Button';

export interface ArtifactItem {
    kind: LifecycleArtifactKind;
    itemId?: string;
    status: 'missing' | 'draft' | 'complete';
    lastUpdated?: string;
}

export interface ArtifactsPanelProps {
    artifacts: ArtifactItem[];
    currentPhase: LifecyclePhase;
    onCreateArtifact: (kind: LifecycleArtifactKind) => void;
}

/**
 * Artifacts Panel for Canvas.
 */
export const ArtifactsPanel: React.FC<ArtifactsPanelProps> = ({
    artifacts,
    currentPhase,
    onCreateArtifact,
}) => {
    const [searchParams, setSearchParams] = useSearchParams();

    const canvasArtifacts = useMemo(() => getArtifactsForSurface('canvas'), []);

    const artifactsByPhase = useMemo(() => {
        const grouped: Record<LifecyclePhase, typeof canvasArtifacts> = {} as unknown;
        for (const meta of canvasArtifacts) {
            if (!grouped[meta.phase]) {
                grouped[meta.phase] = [];
            }
            grouped[meta.phase].push(meta);
        }
        return grouped;
    }, [canvasArtifacts]);

    const getArtifactStatus = (kind: LifecycleArtifactKind): ArtifactItem | undefined => {
        return artifacts.find((a) => a.kind === kind);
    };

    const openArtifactPanel = (panelId: string) => {
        const next = new URLSearchParams(searchParams);
        next.set('panel', panelId);
        setSearchParams(next, { replace: true });
    };

    const getStatusBadge = (status: ArtifactItem['status']) => {
        switch (status) {
            case 'complete':
                return (
                    <span className="flex items-center gap-1 text-xs text-success-600 dark:text-success-400">
                        <Check className="w-3.5 h-3.5" />
                        Complete
                    </span>
                );
            case 'draft':
                return (
                    <span className="flex items-center gap-1 text-xs text-warning-600 dark:text-warning-400">
                        <Schedule className="w-3.5 h-3.5" />
                        Draft
                    </span>
                );
            default:
                return null;
        }
    };

    const phases = [LifecyclePhase.SHAPE, LifecyclePhase.IMPROVE];

    return (
        <div className="space-y-6">
            <div>
                <h3 className="text-sm font-medium text-text-secondary mb-1">Lifecycle Artifacts</h3>
                <p className="text-xs text-text-secondary">
                    Create and manage structured artifacts for your project.
                </p>
            </div>

            {phases.map((phase) => {
                const phaseArtifacts = artifactsByPhase[phase] || [];
                if (phaseArtifacts.length === 0) return null;

                return (
                    <div key={phase}>
                        <h4 className="text-xs font-semibold text-text-secondary uppercase tracking-wider mb-2">
                            {PHASE_LABELS[phase]} Phase
                        </h4>
                        <div className="space-y-1">
                            {phaseArtifacts.map((meta) => {
                                const artifact = getArtifactStatus(meta.kind);
                                const hasPanelId = meta.placement.param;

                                return (
                                    <Button
                                        key={meta.kind}
                                        variant="ghost"
                                        size="small"
                                        onClick={() => {
                                            if (artifact?.status === 'missing') {
                                                onCreateArtifact(meta.kind);
                                            } else {
                                                openArtifactPanel(hasPanelId);
                                            }
                                        }}
                                        className="w-full min-h-0 justify-start gap-3 p-3 rounded-lg border border-divider hover:border-info-border dark:hover:border-info-border bg-bg-paper hover:bg-surface-muted dark:hover:bg-surface-muted transition-colors text-left group"
                                    >
                                        <span className="text-sm font-semibold text-text-secondary" aria-hidden="true">
                                            {meta.label.slice(0, 3).toUpperCase()}
                                        </span>
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2">
                                                <span className="font-medium text-text-primary text-sm">
                                                    {meta.label}
                                                </span>
                                                {artifact && getStatusBadge(artifact.status)}
                                            </div>
                                            <p className="text-xs text-text-secondary truncate">
                                                {meta.description}
                                            </p>
                                        </div>
                                        {!artifact || artifact.status === 'missing' ? (
                                            <Add className="w-5 h-5 text-info-color opacity-0 group-hover:opacity-100 transition-opacity" />
                                        ) : (
                                            <ChevronRight className="w-5 h-5 text-text-secondary opacity-0 group-hover:opacity-100 transition-opacity" />
                                        )}
                                    </Button>
                                );
                            })}
                        </div>
                    </div>
                );
            })}

            {/* Quick Actions */}
            <div className="pt-4 border-t border-divider">
                <h4 className="text-xs font-semibold text-text-secondary uppercase tracking-wider mb-2">
                    Quick Actions
                </h4>
                <div className="space-y-1">
                    <Button
                        variant="ghost"
                        size="small"
                        onClick={() => openArtifactPanel('traceability')}
                        className="w-full min-h-0 justify-start gap-2 px-3 py-2 text-sm text-text-secondary hover:text-text-primary hover:bg-surface-muted dark:hover:bg-surface-muted rounded-lg transition-colors"
                    >
                        <Share className="w-4 h-4" />
                        View Traceability Graph
                    </Button>
                </div>
            </div>
        </div>
    );
};

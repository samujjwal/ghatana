/**
 * Intent Drawer Component
 *
 * URL-driven drawer for INTENT phase artifact creation.
 * Controlled by ?drawer=idea|research|problem query param.
 *
 * @doc.type component
 * @doc.purpose INTENT phase drawer host
 * @doc.layer product
 * @doc.pattern Container Component
 */

import React, { useCallback, useMemo } from 'react';
import { useSearchParams, useNavigate } from 'react-router';
import { X as Close, ArrowLeft as ArrowBack, ArrowRight as ArrowForward } from 'lucide-react';
import { IdeaBriefForm } from './IdeaBriefForm';
import { ResearchPackEditor } from './ResearchPackEditor';
import { ProblemStatementEditor } from './ProblemStatementEditor';
import {
    LifecycleArtifactKind,
    LIFECYCLE_ARTIFACT_CATALOG,
    type IdeaBriefPayload,
    type ResearchPackPayload,
    type ProblemStatementPayload,
} from '@/shared/types/lifecycle-artifacts';
import { PANELS, RADIUS, TRANSITIONS } from '../../styles/design-tokens';

type DrawerId = 'idea' | 'research' | 'problem';

const DRAWER_CONFIG: Record<
    DrawerId,
    {
        kind: LifecycleArtifactKind;
        title: string;
        description: string;
        prev?: DrawerId;
        next?: DrawerId;
    }
> = {
    idea: {
        kind: LifecycleArtifactKind.IDEA_BRIEF,
        title: 'Idea Brief',
        description: 'Capture the initial idea with target users and value proposition',
        next: 'research',
    },
    research: {
        kind: LifecycleArtifactKind.RESEARCH_PACK,
        title: 'Research Pack',
        description: 'Document research findings, user insights, and market analysis',
        prev: 'idea',
        next: 'problem',
    },
    problem: {
        kind: LifecycleArtifactKind.PROBLEM_STATEMENT,
        title: 'Problem Statement',
        description: 'Refine the problem with success metrics and non-goals',
        prev: 'research',
    },
};

export interface IntentDrawerProps {
    /**
     * Callback when an artifact is saved.
     * Should handle creating/updating the Item and Artifact in the backend.
     */
    onSave: (kind: LifecycleArtifactKind, data: unknown) => Promise<{ projectId: string }>;
    /**
     * AI assist callback for generating suggestions.
     */
    onAIAssist?: (kind: LifecycleArtifactKind) => Promise<unknown | null>;
    /**
     * Existing artifact data keyed by kind.
     */
    existingData?: Partial<Record<LifecycleArtifactKind, unknown>>;
}

/**
 * Intent Drawer for INTENT phase.
 * Opens as a slide-out drawer from the right side.
 */
export const IntentDrawer: React.FC<IntentDrawerProps> = ({
    onSave,
    onAIAssist,
    existingData = {},
}) => {
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const [isSubmitting, setIsSubmitting] = React.useState(false);

    const drawerId = useMemo((): DrawerId | null => {
        const id = searchParams.get('drawer');
        if (id && ['idea', 'research', 'problem'].includes(id)) {
            return id as DrawerId;
        }
        return null;
    }, [searchParams]);

    const config = drawerId ? DRAWER_CONFIG[drawerId] : null;

    const closeDrawer = useCallback(() => {
        const next = new URLSearchParams(searchParams);
        next.delete('drawer');
        setSearchParams(next, { replace: true });
    }, [searchParams, setSearchParams]);

    const navigateToDrawer = useCallback((id: DrawerId) => {
        const next = new URLSearchParams(searchParams);
        next.set('drawer', id);
        setSearchParams(next, { replace: true });
    }, [searchParams, setSearchParams]);

    const handleSave = useCallback(async (kind: LifecycleArtifactKind, data: unknown) => {
        setIsSubmitting(true);
        try {
            const result = await onSave(kind, data);
            // After saving, navigate to the project canvas with artifacts panel
            navigate(`/app/p/${result.projectId}/canvas?panel=artifacts`);
        } finally {
            setIsSubmitting(false);
        }
    }, [onSave, navigate]);

    const handleAIAssist = useCallback(async (kind: LifecycleArtifactKind) => {
        if (!onAIAssist) return null;
        return onAIAssist(kind);
    }, [onAIAssist]);

    if (!drawerId || !config) {
        return null;
    }

    const meta = LIFECYCLE_ARTIFACT_CATALOG[config.kind];

    return (
        <>
            {/* Backdrop */}
            <div
                className="fixed inset-0 bg-black/30 z-40"
                onClick={closeDrawer}
                aria-hidden="true"
            />

            {/* Drawer */}
            <div
                className={`fixed right-0 top-0 h-full ${PANELS.wideWidth} bg-bg-paper border-l border-divider z-50 flex flex-col ${TRANSITIONS.default}`}
                role="dialog"
                aria-modal="true"
                aria-labelledby="intent-drawer-title"
            >
                {/* Header */}
                <div className="flex items-center justify-between p-4 border-b border-divider">
                    <div className="flex items-center gap-3">
                        <span className="text-2xl" aria-hidden="true">{meta.icon}</span>
                        <div>
                            <h2 id="intent-drawer-title" className="text-lg font-semibold text-text-primary">
                                {config.title}
                            </h2>
                            <p className="text-sm text-text-secondary">{config.description}</p>
                        </div>
                    </div>
                    <button
                        onClick={closeDrawer}
                        className="p-2 text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 rounded-lg transition-colors"
                        aria-label="Close drawer"
                    >
                        <Close className="w-5 h-5" />
                    </button>
                </div>

                {/* Navigation Pills */}
                <div className="flex items-center justify-center gap-2 p-3 border-b border-divider bg-bg-default">
                    {Object.entries(DRAWER_CONFIG).map(([id, cfg]) => {
                        const isActive = id === drawerId;
                        const artifactMeta = LIFECYCLE_ARTIFACT_CATALOG[cfg.kind];
                        return (
                            <button
                                key={id}
                                onClick={() => navigateToDrawer(id as DrawerId)}
                                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm transition-colors ${isActive
                                        ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                                        : 'text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800'
                                    }`}
                            >
                                <span aria-hidden="true">{artifactMeta.icon}</span>
                                <span>{cfg.title}</span>
                            </button>
                        );
                    })}
                </div>

                {/* Form Content */}
                <div className="flex-1 overflow-auto p-4">
                    {drawerId === 'idea' && (
                        <IdeaBriefForm
                            initialData={existingData[LifecycleArtifactKind.IDEA_BRIEF] as IdeaBriefPayload | undefined}
                            onSubmit={async (data) => handleSave(LifecycleArtifactKind.IDEA_BRIEF, data)}
                            onAIAssist={
                                onAIAssist
                                    ? async () => handleAIAssist(LifecycleArtifactKind.IDEA_BRIEF) as Promise<Partial<IdeaBriefPayload> | null>
                                    : undefined
                            }
                            onCancel={closeDrawer}
                            isSubmitting={isSubmitting}
                        />
                    )}
                    {drawerId === 'research' && (
                        <ResearchPackEditor
                            initialData={existingData[LifecycleArtifactKind.RESEARCH_PACK] as ResearchPackPayload | undefined}
                            onSubmit={async (data) => handleSave(LifecycleArtifactKind.RESEARCH_PACK, data)}
                            onAIAssist={
                                onAIAssist
                                    ? async () => handleAIAssist(LifecycleArtifactKind.RESEARCH_PACK) as Promise<Partial<ResearchPackPayload> | null>
                                    : undefined
                            }
                            onCancel={closeDrawer}
                            isSubmitting={isSubmitting}
                        />
                    )}
                    {drawerId === 'problem' && (
                        <ProblemStatementEditor
                            initialData={existingData[LifecycleArtifactKind.PROBLEM_STATEMENT] as ProblemStatementPayload | undefined}
                            onSubmit={async (data) => handleSave(LifecycleArtifactKind.PROBLEM_STATEMENT, data)}
                            onAIAssist={
                                onAIAssist
                                    ? async () => handleAIAssist(LifecycleArtifactKind.PROBLEM_STATEMENT) as Promise<Partial<ProblemStatementPayload> | null>
                                    : undefined
                            }
                            onCancel={closeDrawer}
                            isSubmitting={isSubmitting}
                        />
                    )}
                </div>

                {/* Footer Navigation */}
                <div className="flex items-center justify-between p-4 border-t border-divider bg-bg-default">
                    <div>
                        {config.prev && (
                            <button
                                onClick={() => navigateToDrawer(config.prev!)}
                                className="flex items-center gap-1 text-sm text-text-secondary hover:text-text-primary transition-colors"
                            >
                                <ArrowBack className="w-4 h-4" />
                                {DRAWER_CONFIG[config.prev].title}
                            </button>
                        )}
                    </div>
                    <div>
                        {config.next && (
                            <button
                                onClick={() => navigateToDrawer(config.next!)}
                                className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                            >
                                {DRAWER_CONFIG[config.next].title}
                                <ArrowForward className="w-4 h-4" />
                            </button>
                        )}
                    </div>
                </div>
            </div>
        </>
    );
};

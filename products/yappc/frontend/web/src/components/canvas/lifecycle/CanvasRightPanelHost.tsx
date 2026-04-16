/**
 * Canvas Right Panel Host Component
 *
 * URL-driven panel router for the Canvas scene's right panel.
 * Reads `?panel=` query parameter and renders appropriate lifecycle panel.
 *
 * @doc.type component
 * @doc.purpose Panel host for canvas surface
 * @doc.layer product
 * @doc.pattern Container Component
 */

import React, { useCallback, useMemo, Suspense, lazy } from 'react';
import { useSearchParams } from 'react-router-dom';
import { X as Close, ChevronLeft, ChevronRight } from 'lucide-react';
import { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';

// Lazy load panels for code splitting
const ArtifactsPanel = lazy(() => import('./ArtifactsPanel').then((m) => ({ default: m.ArtifactsPanel })));
const RequirementsPanel = lazy(() => import('./RequirementsPanel').then((m) => ({ default: m.RequirementsPanel })));
const AdrPanel = lazy(() => import('./AdrPanel').then((m) => ({ default: m.AdrPanel })));
const UxSpecPanel = lazy(() => import('./UxSpecPanel').then((m) => ({ default: m.UxSpecPanel })));
const ThreatModelPanel = lazy(() => import('./ThreatModelPanel').then((m) => ({ default: m.ThreatModelPanel })));
const ImprovePanel = lazy(() => import('./ImprovePanel').then((m) => ({ default: m.ImprovePanel })));
const TraceabilityPanel = lazy(() => import('./TraceabilityPanel').then((m) => ({ default: m.TraceabilityPanel })));

export type PanelType =
    | 'artifacts'
    | 'requirements'
    | 'adr'
    | 'ux-spec'
    | 'threat-model'
    | 'improve'
    | 'traceability';

const PANEL_ORDER: PanelType[] = [
    'artifacts',
    'requirements',
    'adr',
    'ux-spec',
    'threat-model',
    'traceability',
    'improve',
];

const PANEL_TITLES: Record<PanelType, string> = {
    artifacts: 'Artifacts',
    requirements: 'Requirements',
    adr: 'Architecture Decisions',
    'ux-spec': 'UX Specification',
    'threat-model': 'Threat Model',
    improve: 'Improve',
    traceability: 'Traceability',
};

export interface CanvasRightPanelHostProps {
    /** Project ID for data fetching */
    projectId: string;
    /** Width of the panel (controlled externally for resizing) */
    width?: number;
    /** Callback when panel width changes */
    onWidthChange?: (width: number) => void;
    /** Whether the panel is collapsed */
    isCollapsed?: boolean;
    /** Callback to toggle collapse state */
    onToggleCollapse?: () => void;
    /** Data loaders and handlers - injected from parent */
    dataContext?: {
        requirements?: unknown;
        adr?: unknown;
        uxSpec?: unknown;
        threatModel?: unknown;
        enhancements?: unknown;
        learnings?: unknown;
        artifacts?: unknown[];
        onSaveRequirements?: (data: unknown) => Promise<void>;
        onSaveAdr?: (data: unknown) => Promise<void>;
        onSaveUxSpec?: (data: unknown) => Promise<void>;
        onSaveThreatModel?: (data: unknown) => Promise<void>;
        onSaveImprove?: (data: unknown) => Promise<void>;
        onLinkArtifacts?: (sourceId: string, targetId: string) => Promise<void>;
        onUnlinkArtifacts?: (sourceId: string, targetId: string) => Promise<void>;
        onRefreshArtifacts?: () => Promise<void>;
        onAIAssist?: (context: unknown) => Promise<unknown>;
    };
}

const PanelLoadingFallback: React.FC = () => (
    <div className="flex items-center justify-center h-full">
        <div className="animate-pulse text-text-secondary">Loading panel...</div>
    </div>
);

/**
 * Canvas Right Panel Host - URL-driven panel router.
 */
export const CanvasRightPanelHost: React.FC<CanvasRightPanelHostProps> = ({
    projectId,
    width = 400,
    onWidthChange,
    isCollapsed = false,
    onToggleCollapse,
    dataContext = {},
}) => {
    const [searchParams, setSearchParams] = useSearchParams();
    const currentPanel = (searchParams.get('panel') as PanelType) || null;

    const handleClose = useCallback(() => {
        const newParams = new URLSearchParams(searchParams);
        newParams.delete('panel');
        setSearchParams(newParams);
    }, [searchParams, setSearchParams]);

    const handleNavigatePanel = useCallback(
        (direction: 'prev' | 'next') => {
            if (!currentPanel) return;
            const currentIndex = PANEL_ORDER.indexOf(currentPanel);
            const newIndex =
                direction === 'prev'
                    ? Math.max(0, currentIndex - 1)
                    : Math.min(PANEL_ORDER.length - 1, currentIndex + 1);
            const newPanel = PANEL_ORDER[newIndex];
            const newParams = new URLSearchParams(searchParams);
            newParams.set('panel', newPanel);
            setSearchParams(newParams);
        },
        [currentPanel, searchParams, setSearchParams],
    );

    const handleOpenPanel = useCallback(
        (panel: PanelType) => {
            const newParams = new URLSearchParams(searchParams);
            newParams.set('panel', panel);
            setSearchParams(newParams);
        },
        [searchParams, setSearchParams],
    );

    const currentIndex = currentPanel ? PANEL_ORDER.indexOf(currentPanel) : -1;
    const hasPrev = currentIndex > 0;
    const hasNext = currentIndex < PANEL_ORDER.length - 1 && currentIndex >= 0;

    // Default handlers for when dataContext doesn't provide them
    const noOpAsync = useCallback(async () => { }, []);
    const noOpAsyncNull = useCallback(async () => null, []);

    // Render nothing if no panel is open
    if (!currentPanel) {
        return null;
    }

    if (isCollapsed) {
        return (
            <div className="w-10 h-full bg-bg-paper border-l border-divider flex flex-col items-center py-2">
                <button
                    onClick={onToggleCollapse}
                    className="p-2 text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 rounded transition-colors"
                    title="Expand panel"
                >
                    <ChevronLeft className="w-4 h-4" />
                </button>
            </div>
        );
    }

    return (
        <div
            className="h-full bg-bg-paper border-l border-divider flex flex-col overflow-hidden"
            style={{ width: `${width}px` }}
        >
            {/* Panel Header with Navigation */}
            <div className="flex items-center justify-between px-3 py-2 border-b border-divider bg-grey-50 dark:bg-grey-800/50">
                <div className="flex items-center gap-2">
                    <button
                        onClick={() => handleNavigatePanel('prev')}
                        disabled={!hasPrev}
                        className="p-1 text-text-secondary hover:text-text-primary disabled:opacity-30 disabled:cursor-not-allowed rounded transition-colors"
                        title="Previous panel"
                    >
                        <ChevronLeft className="w-4 h-4" />
                    </button>
                    <span className="text-xs text-text-secondary">
                        {currentIndex + 1}/{PANEL_ORDER.length}
                    </span>
                    <button
                        onClick={() => handleNavigatePanel('next')}
                        disabled={!hasNext}
                        className="p-1 text-text-secondary hover:text-text-primary disabled:opacity-30 disabled:cursor-not-allowed rounded transition-colors"
                        title="Next panel"
                    >
                        <ChevronRight className="w-4 h-4" />
                    </button>
                </div>
                <div className="flex items-center gap-2">
                    {onToggleCollapse && (
                        <button
                            onClick={onToggleCollapse}
                            className="p-1 text-text-secondary hover:text-text-primary rounded transition-colors"
                            title="Collapse panel"
                        >
                            <ChevronRight className="w-4 h-4" />
                        </button>
                    )}
                    <button
                        onClick={handleClose}
                        className="p-1 text-text-secondary hover:text-text-primary rounded transition-colors"
                        title="Close panel"
                    >
                        <Close className="w-4 h-4" />
                    </button>
                </div>
            </div>

            {/* Panel Content */}
            <div className="flex-1 overflow-hidden">
                <Suspense fallback={<PanelLoadingFallback />}>
                    {currentPanel === 'artifacts' && (
                        <ArtifactsPanel
                            projectId={projectId}
                            artifacts={dataContext.artifacts as unknown || []}
                            onOpenPanel={handleOpenPanel}
                        />
                    )}
                    {currentPanel === 'requirements' && (
                        <RequirementsPanel
                            data={dataContext.requirements as unknown}
                            onSave={dataContext.onSaveRequirements || noOpAsync}
                            onAIAssist={dataContext.onAIAssist as unknown}
                            onClose={handleClose}
                        />
                    )}
                    {currentPanel === 'adr' && (
                        <AdrPanel
                            data={dataContext.adr as unknown}
                            onSave={dataContext.onSaveAdr || noOpAsync}
                            onAIAssist={dataContext.onAIAssist as unknown}
                            onClose={handleClose}
                        />
                    )}
                    {currentPanel === 'ux-spec' && (
                        <UxSpecPanel
                            data={dataContext.uxSpec as unknown}
                            onSave={dataContext.onSaveUxSpec || noOpAsync}
                            onAIAssist={dataContext.onAIAssist as unknown}
                            onClose={handleClose}
                        />
                    )}
                    {currentPanel === 'threat-model' && (
                        <ThreatModelPanel
                            data={dataContext.threatModel as unknown}
                            onSave={dataContext.onSaveThreatModel || noOpAsync}
                            onAIAssist={dataContext.onAIAssist as unknown}
                            onClose={handleClose}
                        />
                    )}
                    {currentPanel === 'improve' && (
                        <ImprovePanel
                            enhancements={dataContext.enhancements as unknown}
                            learnings={dataContext.learnings as unknown}
                            onSave={dataContext.onSaveImprove || noOpAsync}
                            onAIAssist={dataContext.onAIAssist as unknown}
                            onClose={handleClose}
                        />
                    )}
                    {currentPanel === 'traceability' && (
                        <TraceabilityPanel
                            artifacts={dataContext.artifacts as unknown || []}
                            onLinkArtifacts={dataContext.onLinkArtifacts || noOpAsync}
                            onUnlinkArtifacts={dataContext.onUnlinkArtifacts || noOpAsync}
                            onRefresh={dataContext.onRefreshArtifacts || noOpAsync}
                            onAIAnalyze={dataContext.onAIAssist as unknown}
                        />
                    )}
                </Suspense>
            </div>

            {/* Quick Panel Navigation Tabs */}
            <div className="flex items-center gap-1 px-2 py-2 border-t border-divider bg-grey-50 dark:bg-grey-800/50 overflow-x-auto">
                {PANEL_ORDER.map((panel) => (
                    <button
                        key={panel}
                        onClick={() => handleOpenPanel(panel)}
                        className={`px-2 py-1 text-xs rounded whitespace-nowrap transition-colors ${currentPanel === panel
                                ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                                : 'text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800'
                            }`}
                    >
                        {PANEL_TITLES[panel]}
                    </button>
                ))}
            </div>
        </div>
    );
};

export default CanvasRightPanelHost;

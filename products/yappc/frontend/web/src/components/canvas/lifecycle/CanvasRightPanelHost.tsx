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

import React, { useCallback, Suspense, lazy } from 'react';
import { useSearchParams } from 'react-router';
import { X as Close, ChevronLeft, ChevronRight } from 'lucide-react';

async function loadArtifactsPanel() {
    const module = await import('./ArtifactsPanel');
    return { default: module.ArtifactsPanel };
}

async function loadRequirementsPanel() {
    const module = await import('./RequirementsPanel');
    return { default: module.RequirementsPanel };
}

async function loadAdrPanel() {
    const module = await import('./AdrPanel');
    return { default: module.AdrPanel };
}

async function loadUxSpecPanel() {
    const module = await import('./UxSpecPanel');
    return { default: module.UxSpecPanel };
}

async function loadThreatModelPanel() {
    const module = await import('./ThreatModelPanel');
    return { default: module.ThreatModelPanel };
}

async function loadImprovePanel() {
    const module = await import('./ImprovePanel');
    return { default: module.ImprovePanel };
}

async function loadTraceabilityPanel() {
    const module = await import('./TraceabilityPanel');
    return { default: module.TraceabilityPanel };
}

// Lazy load panels for code splitting
const ArtifactsPanel = lazy(loadArtifactsPanel);
const RequirementsPanel = lazy(loadRequirementsPanel);
const AdrPanel = lazy(loadAdrPanel);
const UxSpecPanel = lazy(loadUxSpecPanel);
const ThreatModelPanel = lazy(loadThreatModelPanel);
const ImprovePanel = lazy(loadImprovePanel);
const TraceabilityPanel = lazy(loadTraceabilityPanel);

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

// P2-8: Read-only fallback when save handler is not available
const ReadOnlyPanelFallback: React.FC<{ title: string; reason: string }> = ({ title, reason }) => (
    <div className="flex flex-col items-center justify-center h-full p-6 text-center">
        <div className="w-12 h-12 rounded-full bg-grey-100 dark:bg-grey-800 flex items-center justify-center mb-4">
            <svg className="w-6 h-6 text-text-secondary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
        </div>
        <h3 className="text-sm font-medium text-text-primary mb-1">{title}</h3>
        <p className="text-xs text-text-secondary mb-2">Read-only mode</p>
        <p className="text-xs text-text-tertiary">{reason}</p>
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

    // P2-8: Check which panels have persistence handlers
    const hasSaveRequirements = !!dataContext.onSaveRequirements;
    const hasSaveAdr = !!dataContext.onSaveAdr;
    const hasSaveUxSpec = !!dataContext.onSaveUxSpec;
    const hasSaveThreatModel = !!dataContext.onSaveThreatModel;
    const hasSaveImprove = !!dataContext.onSaveImprove;
    const hasLinkArtifacts = !!dataContext.onLinkArtifacts;

    // Map panels to their save handler availability
    const panelSaveAvailability: Record<PanelType, boolean> = {
        artifacts: true, // Artifacts panel has its own internal handling
        requirements: hasSaveRequirements,
        adr: hasSaveAdr,
        'ux-spec': hasSaveUxSpec,
        'threat-model': hasSaveThreatModel,
        improve: hasSaveImprove,
        traceability: hasLinkArtifacts,
    };

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
                    {/* P2-8: Only render panels that have persistence handlers */}
                    {currentPanel === 'requirements' && hasSaveRequirements && (
                        <RequirementsPanel
                            data={dataContext.requirements as unknown}
                            onSave={dataContext.onSaveRequirements!}
                            onAIAssist={dataContext.onAIAssist as unknown}
                            onClose={handleClose}
                        />
                    )}
                    {currentPanel === 'requirements' && !hasSaveRequirements && (
                        <ReadOnlyPanelFallback title="Requirements" reason="Save handler not configured" />
                    )}
                    {currentPanel === 'adr' && hasSaveAdr && (
                        <AdrPanel
                            data={dataContext.adr as unknown}
                            onSave={dataContext.onSaveAdr!}
                            onAIAssist={dataContext.onAIAssist as unknown}
                            onClose={handleClose}
                        />
                    )}
                    {currentPanel === 'adr' && !hasSaveAdr && (
                        <ReadOnlyPanelFallback title="Architecture Decisions" reason="Save handler not configured" />
                    )}
                    {currentPanel === 'ux-spec' && hasSaveUxSpec && (
                        <UxSpecPanel
                            data={dataContext.uxSpec as unknown}
                            onSave={dataContext.onSaveUxSpec!}
                            onAIAssist={dataContext.onAIAssist as unknown}
                            onClose={handleClose}
                        />
                    )}
                    {currentPanel === 'ux-spec' && !hasSaveUxSpec && (
                        <ReadOnlyPanelFallback title="UX Specification" reason="Save handler not configured" />
                    )}
                    {currentPanel === 'threat-model' && hasSaveThreatModel && (
                        <ThreatModelPanel
                            data={dataContext.threatModel as unknown}
                            onSave={dataContext.onSaveThreatModel!}
                            onAIAssist={dataContext.onAIAssist as unknown}
                            onClose={handleClose}
                        />
                    )}
                    {currentPanel === 'threat-model' && !hasSaveThreatModel && (
                        <ReadOnlyPanelFallback title="Threat Model" reason="Save handler not configured" />
                    )}
                    {currentPanel === 'improve' && hasSaveImprove && (
                        <ImprovePanel
                            enhancements={dataContext.enhancements as unknown}
                            learnings={dataContext.learnings as unknown}
                            onSave={dataContext.onSaveImprove!}
                            onAIAssist={dataContext.onAIAssist as unknown}
                            onClose={handleClose}
                        />
                    )}
                    {currentPanel === 'improve' && !hasSaveImprove && (
                        <ReadOnlyPanelFallback title="Improve" reason="Save handler not configured" />
                    )}
                    {currentPanel === 'traceability' && hasLinkArtifacts && (
                        <TraceabilityPanel
                            artifacts={dataContext.artifacts as unknown || []}
                            onLinkArtifacts={dataContext.onLinkArtifacts!}
                            onUnlinkArtifacts={dataContext.onUnlinkArtifacts || (async () => {})}
                            onRefresh={dataContext.onRefreshArtifacts || (async () => {})}
                            onAIAnalyze={dataContext.onAIAssist as unknown}
                        />
                    )}
                    {currentPanel === 'traceability' && !hasLinkArtifacts && (
                        <ReadOnlyPanelFallback title="Traceability" reason="Link handler not configured" />
                    )}
                </Suspense>
            </div>

            {/* Quick Panel Navigation Tabs - P2-8: Disable tabs without save handlers */}
            <div className="flex items-center gap-1 px-2 py-2 border-t border-divider bg-grey-50 dark:bg-grey-800/50 overflow-x-auto">
                {PANEL_ORDER.map((panel) => {
                    const hasHandler = panelSaveAvailability[panel];
                    return (
                        <button
                            key={panel}
                            onClick={() => hasHandler && handleOpenPanel(panel)}
                            disabled={!hasHandler}
                            title={hasHandler ? PANEL_TITLES[panel] : `${PANEL_TITLES[panel]} (Save handler not configured)`}
                            className={`px-2 py-1 text-xs rounded whitespace-nowrap transition-colors ${
                                currentPanel === panel
                                    ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                                    : hasHandler
                                        ? 'text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800'
                                        : 'text-text-tertiary cursor-not-allowed opacity-50'
                            }`}
                        >
                            {PANEL_TITLES[panel]}
                            {!hasHandler && ' 🔒'}
                        </button>
                    );
                })}
            </div>
        </div>
    );
};

export default CanvasRightPanelHost;

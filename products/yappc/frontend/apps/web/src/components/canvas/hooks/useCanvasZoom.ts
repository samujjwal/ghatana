/**
 * Canvas Zoom & Navigation Hook
 * 
 * Handles viewport camera operations: zoom-to-phase (⌘1-7), fit view (⌘0),
 * prev/next phase (⌘←/→), and abstraction level drill-down/zoom-out.
 * Uses shared PHASE_ZONE_CENTERS constant for consistent zone positions.
 * 
 * @doc.type hook
 * @doc.purpose Canvas camera and viewport navigation
 * @doc.layer product
 * @doc.pattern Extracted Hook
 */

import { useCallback, useRef, useState, useEffect } from 'react';
import { type ReactFlowInstance } from '@xyflow/react';
import { useAtomValue, useSetAtom } from 'jotai';
import { LifecyclePhase } from '@/types/lifecycle';
import { PHASE_ZONE_CENTERS, prefersReducedMotionAtom, canvasAnnouncementAtom, selectedNodesAtom } from '../workspace';
import {
    AbstractionLevel,
    type AbstractionBreadcrumb,
    getNextLevel,
    getPreviousLevel,
    canDrillDown,
    canZoomOut,
} from '../../../types/abstractionLevel';

// ============================================================================
// Constants
// ============================================================================

const PHASE_ORDER: LifecyclePhase[] = [
    LifecyclePhase.INTENT,
    LifecyclePhase.SHAPE,
    LifecyclePhase.VALIDATE,
    LifecyclePhase.GENERATE,
    LifecyclePhase.RUN,
    LifecyclePhase.OBSERVE,
    LifecyclePhase.IMPROVE,
];

// ============================================================================
// Hook
// ============================================================================

export interface UseCanvasZoomConfig {
    reactFlowInstance: ReactFlowInstance | null;
    currentPhase: LifecyclePhase;
}

export function useCanvasZoom(config: UseCanvasZoomConfig) {
    const { reactFlowInstance, currentPhase } = config;

    const reducedMotion = useAtomValue(prefersReducedMotionAtom);
    const announce = useSetAtom(canvasAnnouncementAtom);
    const selectedNodes = useAtomValue(selectedNodesAtom);
    /** Animation duration: full when motion is allowed, instant when prefers-reduced-motion. */
    const animDuration = reducedMotion ? 0 : 400; // 400ms — industry standard (was 800ms, felt slow)

    // ── Debounce handle for rapid phase-zoom keypresses (⌘ 1–7) ─────────
    const phaseZoomTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    // ── Breadcrumb trail (persisted in sessionStorage for deep-link/tab reload) ─
    const SESSION_KEY = 'canvas-breadcrumbs';

    const loadBreadcrumbs = (): AbstractionBreadcrumb[] => {
        try {
            const raw = sessionStorage.getItem(SESSION_KEY);
            return raw ? (JSON.parse(raw) as AbstractionBreadcrumb[]) : [];
        } catch {
            return [];
        }
    };

    const [currentAbstractionLevel, setCurrentAbstractionLevel] = useState<AbstractionLevel>(() => {
        const stored = loadBreadcrumbs();
        return stored.length > 0 ? stored[stored.length - 1].level : AbstractionLevel.MACRO;
    });
    const [breadcrumbs, setBreadcrumbs] = useState<AbstractionBreadcrumb[]>(() => {
        const stored = loadBreadcrumbs();
        return stored.length > 0 ? stored : [{ id: 'root', label: 'Product', level: AbstractionLevel.MACRO }];
    });

    // Persist breadcrumbs whenever they change
    useEffect(() => {
        try { sessionStorage.setItem(SESSION_KEY, JSON.stringify(breadcrumbs)); } catch { /* quota */ }
    }, [breadcrumbs]);

    /**
     * Zoom to a specific lifecycle phase using measured node bounding boxes.
     * Debounced by 80ms so rapid ⌘+1–7 keypresses don't stack animations.
     */
    const handleZoomToPhase = useCallback((phase: LifecyclePhase) => {
        if (!reactFlowInstance) return;
        if (phaseZoomTimerRef.current) clearTimeout(phaseZoomTimerRef.current);
        phaseZoomTimerRef.current = setTimeout(() => {
            phaseZoomTimerRef.current = null;
            const rfNodes = reactFlowInstance.getNodes();
            const phaseNodes = rfNodes.filter(
                (n) =>
                    (n.data as Record<string, unknown>)?.phase === phase &&
                    n.measured?.width &&
                    n.measured?.height,
            );

            if (phaseNodes.length > 0) {
                const minX = Math.min(...phaseNodes.map((n) => n.position.x));
                const maxX = Math.max(...phaseNodes.map((n) => n.position.x + n.measured!.width!));
                const minY = Math.min(...phaseNodes.map((n) => n.position.y));
                const maxY = Math.max(...phaseNodes.map((n) => n.position.y + n.measured!.height!));
                reactFlowInstance.fitBounds(
                    { x: minX, y: minY, width: maxX - minX, height: maxY - minY },
                    { padding: 0.15, duration: animDuration },
                );
            } else {
                const target = PHASE_ZONE_CENTERS[phase];
                if (target) {
                    reactFlowInstance.setCenter(target.x, target.y, { zoom: 0.8, duration: animDuration });
                }
            }
            announce(`Navigated to ${phase} phase`);
        }, 80);
    }, [reactFlowInstance, animDuration, announce]);

    /**
     * Fit the view to show all content.
     */
    const handleFitView = useCallback(() => {
        reactFlowInstance?.fitView({ padding: 0.1, duration: animDuration, minZoom: 0.1, maxZoom: 2 });
        announce('Fit view to all content');
    }, [reactFlowInstance, animDuration, announce]);

    /**
     * Fit the viewport to the current selection.
     * Falls back to fit-all when no nodes are selected.
     * Bound to the `F` key in useCanvasShortcuts.
     */
    const handleFitSelection = useCallback(() => {
        if (!reactFlowInstance) return;
        if (selectedNodes.length === 0) {
            // No selection — fall back to fit-all
            reactFlowInstance.fitView({ padding: 0.1, duration: animDuration, minZoom: 0.1, maxZoom: 2 });
            announce('Fit view to all content');
            return;
        }
        const rfNodes = reactFlowInstance
            .getNodes()
            .filter(
                (n) => selectedNodes.includes(n.id) && n.measured?.width && n.measured?.height,
            );
        if (rfNodes.length === 0) return;
        const minX = Math.min(...rfNodes.map((n) => n.position.x));
        const maxX = Math.max(...rfNodes.map((n) => n.position.x + n.measured!.width!));
        const minY = Math.min(...rfNodes.map((n) => n.position.y));
        const maxY = Math.max(...rfNodes.map((n) => n.position.y + n.measured!.height!));
        reactFlowInstance.fitBounds(
            { x: minX, y: minY, width: maxX - minX, height: maxY - minY },
            { padding: 0.2, duration: animDuration },
        );
        announce(`Fit view to ${selectedNodes.length} selected node${selectedNodes.length > 1 ? 's' : ''}`);
    }, [reactFlowInstance, selectedNodes, animDuration, announce]);

    /**
     * Navigate to the previous phase in sequence.
     */
    const handlePrevPhase = useCallback(() => {
        const index = PHASE_ORDER.indexOf(currentPhase);
        if (index > 0) {
            handleZoomToPhase(PHASE_ORDER[index - 1]);
        }
    }, [currentPhase, handleZoomToPhase]);

    /**
     * Navigate to the next phase in sequence.
     */
    const handleNextPhase = useCallback(() => {
        const index = PHASE_ORDER.indexOf(currentPhase);
        if (index < PHASE_ORDER.length - 1) {
            handleZoomToPhase(PHASE_ORDER[index + 1]);
        }
    }, [currentPhase, handleZoomToPhase]);

    /**
     * Drill down into a node's sub-canvas.
     * Uses fitBounds with the node's measured dimensions instead of hardcoded
     * offsets to ensure consistent framing regardless of node size.
     */
    const handleDrillDown = useCallback((nodeId: string, label: string) => {
        const next = getNextLevel(currentAbstractionLevel);
        if (next) {
            setCurrentAbstractionLevel(next);
            setBreadcrumbs((prev) => [...prev, { id: nodeId, label, level: next }]);

            if (reactFlowInstance) {
                const node = reactFlowInstance.getNodes().find((n) => n.id === nodeId);
                if (node) {
                    const w = node.measured?.width ?? 300;
                    const h = node.measured?.height ?? 200;
                    reactFlowInstance.fitBounds(
                        { x: node.position.x, y: node.position.y, width: w, height: h },
                        { padding: 0.3, duration: animDuration },
                    );
                }
            }
        }
    }, [currentAbstractionLevel, reactFlowInstance, animDuration]);


    /**
     * Zoom out one abstraction level.
     */
    const handleZoomOut = useCallback(() => {
        const prev = getPreviousLevel(currentAbstractionLevel);
        if (prev) {
            setCurrentAbstractionLevel(prev);
            setBreadcrumbs((history) => history.slice(0, -1));
        }
    }, [currentAbstractionLevel]);

    const handleLevelChange = useCallback((level: AbstractionLevel) => {
        setCurrentAbstractionLevel(level);
        setBreadcrumbs((prev) => {
            const index = prev.findIndex((b) => b.level === level);
            return index >= 0 ? prev.slice(0, index + 1) : prev;
        });
    }, []);

    const handleBreadcrumbClick = useCallback((index: number) => {
        const item = breadcrumbs[index];
        setCurrentAbstractionLevel(item.level);
        setBreadcrumbs((prev) => prev.slice(0, index + 1));
    }, [breadcrumbs]);

    return {
        handleZoomToPhase,
        handleFitView,
        handleFitSelection,
        handlePrevPhase,
        handleNextPhase,
        handleDrillDown,
        handleZoomOut,
        handleLevelChange,
        handleBreadcrumbClick,
        currentAbstractionLevel,
        breadcrumbs,
        canDrillDown: canDrillDown(currentAbstractionLevel),
        canZoomOut: canZoomOut(currentAbstractionLevel),
    };
}

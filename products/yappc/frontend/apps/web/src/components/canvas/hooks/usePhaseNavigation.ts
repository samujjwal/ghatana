/**
 * Phase Navigation Hook
 * 
 * Provides phase-based navigation utilities for the canvas.
 * Maps lifecycle phases to spatial zone positions and provides
 * navigation functions.
 * 
 * @doc.type hook
 * @doc.purpose Phase-based canvas navigation
 * @doc.layer product
 * @doc.pattern Navigation
 */

import { useMemo, useCallback } from 'react';
import { useAtomValue } from 'jotai';
import { LifecyclePhase } from '@/types/lifecycle';
import { cameraAtom } from '../workspace/canvasAtoms';

// ============================================================================
// Constants
// ============================================================================

/**
 * Zone X positions for each phase (from SpatialZones.tsx)
 */
export const PHASE_ZONE_POSITIONS: Record<LifecyclePhase, { start: number; width: number; center: number }> = {
    [LifecyclePhase.INTENT]: { start: 0, width: 800, center: 400 },
    [LifecyclePhase.SHAPE]: { start: 800, width: 900, center: 1250 },
    [LifecyclePhase.VALIDATE]: { start: 1700, width: 1000, center: 2200 },
    [LifecyclePhase.GENERATE]: { start: 2700, width: 800, center: 3100 },
    [LifecyclePhase.RUN]: { start: 3500, width: 700, center: 3850 },
    [LifecyclePhase.OBSERVE]: { start: 4200, width: 700, center: 4550 },
    [LifecyclePhase.IMPROVE]: { start: 4900, width: 800, center: 5300 },
};

export const PHASE_ORDER: LifecyclePhase[] = [
    LifecyclePhase.INTENT,
    LifecyclePhase.SHAPE,
    LifecyclePhase.VALIDATE,
    LifecyclePhase.GENERATE,
    LifecyclePhase.RUN,
    LifecyclePhase.OBSERVE,
    LifecyclePhase.IMPROVE,
];

// ============================================================================
// Types
// ============================================================================

export interface PhaseNavigationState {
    /** Current phase based on viewport position */
    currentPhase: LifecyclePhase;
    /** Previous phase (or null if at start) */
    previousPhase: LifecyclePhase | null;
    /** Next phase (or null if at end) */
    nextPhase: LifecyclePhase | null;
    /** Current phase index (0-6) */
    phaseIndex: number;
    /** Phases that are visible in current viewport */
    visiblePhases: LifecyclePhase[];
    /** Progress through current phase (0-1) */
    phaseProgress: number;
}

export interface UsePhaseNavigationResult extends PhaseNavigationState {
    /** Check if a specific phase is visible */
    isPhaseVisible: (phase: LifecyclePhase) => boolean;
    /** Check if a specific phase is the current one */
    isCurrentPhase: (phase: LifecyclePhase) => boolean;
    /** Get position info for a phase */
    getPhasePosition: (phase: LifecyclePhase) => { start: number; width: number; center: number };
    /** Get phase from X position */
    getPhaseFromPosition: (x: number) => LifecyclePhase;
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Determine which phase a given X position falls into
 */
function getPhaseFromX(x: number): LifecyclePhase {
    for (const phase of PHASE_ORDER) {
        const { start, width } = PHASE_ZONE_POSITIONS[phase];
        if (x >= start && x < start + width) {
            return phase;
        }
    }
    // Default to IMPROVE if past all zones
    return LifecyclePhase.IMPROVE;
}

/**
 * Get visible phases based on viewport
 */
function getVisiblePhases(
    viewport: { x: number; y: number; zoom: number },
    viewportWidth: number = 1920
): LifecyclePhase[] {
    const visibleLeft = -viewport.x / viewport.zoom;
    const visibleRight = (-viewport.x + viewportWidth) / viewport.zoom;

    return PHASE_ORDER.filter(phase => {
        const { start, width } = PHASE_ZONE_POSITIONS[phase];
        const end = start + width;
        return end >= visibleLeft && start <= visibleRight;
    });
}

// ============================================================================
// Hook
// ============================================================================

export function usePhaseNavigation(): UsePhaseNavigationResult {
    const viewport = useAtomValue(cameraAtom);

    // Calculate viewport width (client-side only)
    const viewportWidth = typeof window !== 'undefined' ? window.innerWidth : 1920;

    // Calculate current state
    const state = useMemo<PhaseNavigationState>(() => {
        // Get center of viewport
        const viewportCenterX = (-viewport.x + viewportWidth / 2) / viewport.zoom;

        // Determine current phase from center position
        const currentPhase = getPhaseFromX(viewportCenterX);
        const phaseIndex = PHASE_ORDER.indexOf(currentPhase);

        // Calculate previous/next phases
        const previousPhase = phaseIndex > 0 ? PHASE_ORDER[phaseIndex - 1] : null;
        const nextPhase = phaseIndex < PHASE_ORDER.length - 1 ? PHASE_ORDER[phaseIndex + 1] : null;

        // Get visible phases
        const visiblePhases = getVisiblePhases(viewport, viewportWidth);

        // Calculate progress through current phase
        const { start, width } = PHASE_ZONE_POSITIONS[currentPhase];
        const phaseProgress = Math.max(0, Math.min(1, (viewportCenterX - start) / width));

        return {
            currentPhase,
            previousPhase,
            nextPhase,
            phaseIndex,
            visiblePhases,
            phaseProgress,
        };
    }, [viewport, viewportWidth]);

    // Helper functions
    const isPhaseVisible = useCallback(
        (phase: LifecyclePhase) => state.visiblePhases.includes(phase),
        [state.visiblePhases]
    );

    const isCurrentPhase = useCallback(
        (phase: LifecyclePhase) => state.currentPhase === phase,
        [state.currentPhase]
    );

    const getPhasePosition = useCallback(
        (phase: LifecyclePhase) => PHASE_ZONE_POSITIONS[phase],
        []
    );

    const getPhaseFromPosition = useCallback(
        (x: number) => getPhaseFromX(x),
        []
    );

    return {
        ...state,
        isPhaseVisible,
        isCurrentPhase,
        getPhasePosition,
        getPhaseFromPosition,
    };
}

export default usePhaseNavigation;

/**
 * Lifecycle Phase Hook
 * 
 * Manages lifecycle phase context and transitions.
 * Provides phase-aware utilities for route components.
 * 
 * @doc.type hook
 * @doc.purpose Lifecycle phase management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useLocation, useNavigate, useParams } from 'react-router';
import { useMemo, useCallback } from 'react';
import {
    LifecyclePhase,
    getPhaseFromRoute,
    getRouteForPhase,
    canTransitionToPhase as canTransitionTo,
    PHASE_LABELS,
    PHASE_DESCRIPTIONS,
} from '../types/lifecycle';
import { useWorkspaceContext } from './useWorkspaceData';

/**
 * Hook result type
 */
export interface UseLifecyclePhaseResult {
    /** Current lifecycle phase (from route) */
    currentPhase: LifecyclePhase | null;

    /** Current project's stored lifecycle phase */
    projectPhase: LifecyclePhase | null;

    /** Navigate to a specific lifecycle phase */
    navigateToPhase: (phase: LifecyclePhase) => void;

    /** Check if transition to phase is allowed */
    canTransitionTo: (phase: LifecyclePhase) => boolean;

    /** Get label for current phase */
    currentLabel: string;

    /** Get description for current phase */
    currentDescription: string;

    /** Check if current route matches a specific phase */
    isPhase: (phase: LifecyclePhase) => boolean;

    /** Project ID from route params */
    projectId: string | undefined;

    /** Loading state */
    isLoading: boolean;
}

/**
 * Hook for managing lifecycle phase context.
 * 
 * Usage:
 * ```tsx
 * const { currentPhase, navigateToPhase, isPhase } = useLifecyclePhase();
 * 
 * if (isPhase(LifecyclePhase.SHAPE)) {
 *   // Render canvas-specific UI
 * }
 * ```
 */
export function useLifecyclePhase(): UseLifecyclePhaseResult {
    const location = useLocation();
    const navigate = useNavigate();
    const { projectId } = useParams<{ projectId: string }>();
    const { ownedProjects, includedProjects, isLoading } = useWorkspaceContext();

    // Ensure projectId is defined
    const projectIdSafe = projectId ?? '';

    // Extract current phase from route
    const currentPhase = useMemo(
        () => getPhaseFromRoute(location.pathname),
        [location.pathname]
    );

    // Get project's stored lifecycle phase
    const projectPhase = useMemo(() => {
        if (!projectId) return null;

        const allProjects = [...ownedProjects, ...includedProjects];
        const project = allProjects.find(p => p.id === projectId);

        return project?.lifecyclePhase ?? null;
    }, [projectId, ownedProjects, includedProjects]);

    // Navigate to a specific phase
    const navigateToPhase = useCallback(
        (phase: LifecyclePhase) => {
            if (!projectIdSafe && phase !== LifecyclePhase.INTENT) {
                console.warn('Cannot navigate to phase without project ID');
                return;
            }

            const route = getRouteForPhase(projectIdSafe, phase);
            navigate(route);
        },
        [projectIdSafe, navigate]
    );

    // Check if transition is allowed
    const canTransition = useCallback(
        (toPhase: LifecyclePhase) => {
            if (!currentPhase) return true; // Allow any transition from unknown state
            return canTransitionTo(currentPhase, toPhase);
        },
        [currentPhase]
    );

    // Get current phase label
    const currentLabel = useMemo(
        () => (currentPhase ? PHASE_LABELS[currentPhase] : 'Unknown'),
        [currentPhase]
    );

    // Get current phase description
    const currentDescription = useMemo(
        () => (currentPhase ? PHASE_DESCRIPTIONS[currentPhase] : ''),
        [currentPhase]
    );

    // Check if current route is a specific phase
    const isPhase = useCallback(
        (phase: LifecyclePhase) => currentPhase === phase,
        [currentPhase]
    );

    return {
        currentPhase,
        projectPhase,
        navigateToPhase,
        canTransitionTo: canTransition,
        currentLabel,
        currentDescription,
        isPhase,
        projectId,
        isLoading,
    };
}

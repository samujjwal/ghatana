/**
 * @doc.type hook
 * @doc.purpose Manage abstraction level navigation for canvas
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useCallback, useEffect, useMemo } from 'react';
import { atom, useAtom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import {
    AbstractionLevel,
    AbstractionBreadcrumb,
    AbstractionNavigationState,
    LevelContext,
    getLevelInfo,
    getNextLevel,
    getPreviousLevel,
    canDrillDown,
    canZoomOut,
    ABSTRACTION_LEVELS,
    DEFAULT_LEVEL_CONTEXT,
} from '../types/abstractionLevel';

// Persisted state per project
const abstractionLevelAtomFamily = new Map<string, ReturnType<typeof atomWithStorage<AbstractionNavigationState>>>();

function getAbstractionAtom(projectId: string) {
    if (!abstractionLevelAtomFamily.has(projectId)) {
        const defaultState: AbstractionNavigationState = {
            currentLevel: 'component', // Start at component level by default
            breadcrumbs: [{ level: 'component', label: 'Project' }],
            history: ['component'],
            selectedContext: undefined,
        };

        abstractionLevelAtomFamily.set(
            projectId,
            atomWithStorage<AbstractionNavigationState>(
                `ghatana-abstraction-level-${projectId}`,
                defaultState
            )
        );
    }
    return abstractionLevelAtomFamily.get(projectId)!;
}

// Animation state for level transitions
const transitionAtom = atom<{
    isTransitioning: boolean;
    direction: 'in' | 'out' | null;
    fromLevel: AbstractionLevel | null;
    toLevel: AbstractionLevel | null;
}>({
    isTransitioning: false,
    direction: null,
    fromLevel: null,
    toLevel: null,
});

export interface UseAbstractionLevelOptions {
    onLevelChange?: (level: AbstractionLevel, context?: LevelContext) => void;
}

export function useAbstractionLevel(projectId: string, options: UseAbstractionLevelOptions = {}) {
    const { onLevelChange } = options;

    const abstractionAtom = useMemo(() => getAbstractionAtom(projectId), [projectId]);
    const [state, setState] = useAtom(abstractionAtom);
    const [transition, setTransition] = useAtom(transitionAtom);

    // Get current level info
    const levelInfo = useMemo(() => getLevelInfo(state.currentLevel), [state.currentLevel]);

    // Check navigation capabilities
    const canGoDeeper = useMemo(() => canDrillDown(state.currentLevel), [state.currentLevel]);
    const canGoHigher = useMemo(() => canZoomOut(state.currentLevel), [state.currentLevel]);

    // Set level directly
    const setLevel = useCallback((level: AbstractionLevel, context?: LevelContext) => {
        if (level === state.currentLevel) return;

        const fromLevel = state.currentLevel;
        const direction = getLevelInfo(level).order > getLevelInfo(fromLevel).order ? 'in' : 'out';

        // Start transition
        setTransition({
            isTransitioning: true,
            direction,
            fromLevel,
            toLevel: level,
        });

        // Update state
        setState(prev => ({
            currentLevel: level,
            breadcrumbs: updateBreadcrumbs(prev.breadcrumbs, level, context),
            history: [...prev.history.slice(-9), level], // Keep last 10
            selectedContext: context,
        }));

        // Notify callback
        onLevelChange?.(level, context);

        // End transition after animation
        setTimeout(() => {
            setTransition({
                isTransitioning: false,
                direction: null,
                fromLevel: null,
                toLevel: null,
            });
        }, 300);
    }, [state.currentLevel, setState, setTransition, onLevelChange]);

    // Drill down to next level
    const drillDown = useCallback((context?: LevelContext) => {
        const nextLevel = getNextLevel(state.currentLevel);
        if (nextLevel) {
            setLevel(nextLevel, context);
        }
    }, [state.currentLevel, setLevel]);

    // Zoom out to previous level
    const zoomOut = useCallback(() => {
        const prevLevel = getPreviousLevel(state.currentLevel);
        if (prevLevel) {
            setLevel(prevLevel);
        }
    }, [state.currentLevel, setLevel]);

    // Navigate to specific breadcrumb
    const navigateToBreadcrumb = useCallback((index: number) => {
        const breadcrumb = state.breadcrumbs[index];
        if (breadcrumb && breadcrumb.level !== state.currentLevel) {
            setLevel(breadcrumb.level, breadcrumb.context ? {
                level: breadcrumb.level,
                data: breadcrumb.context as unknown as LevelContext['data'],
            } as LevelContext : undefined);
        }
    }, [state.breadcrumbs, state.currentLevel, setLevel]);

    // Go back in history
    const goBack = useCallback(() => {
        if (state.history.length > 1) {
            const previousLevel = state.history[state.history.length - 2];
            setState(prev => ({
                ...prev,
                currentLevel: previousLevel,
                history: prev.history.slice(0, -1),
            }));
        }
    }, [state.history, setState]);

    // Reset to default level
    const reset = useCallback(() => {
        setState({
            currentLevel: 'component',
            breadcrumbs: [{ level: 'component', label: 'Project' }],
            history: ['component'],
            selectedContext: undefined,
        });
    }, [setState]);

    // Keyboard shortcuts
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            // Alt+1/2/3/4 for direct level access
            if (e.altKey && !e.ctrlKey && !e.metaKey) {
                const levelNum = parseInt(e.key);
                if (levelNum >= 1 && levelNum <= 4) {
                    const level = ABSTRACTION_LEVELS[levelNum - 1]?.level;
                    if (level) {
                        e.preventDefault();
                        setLevel(level);
                    }
                }
            }

            // Alt+Up = zoom out, Alt+Down = drill down
            if (e.altKey && e.key === 'ArrowUp' && canGoHigher) {
                e.preventDefault();
                zoomOut();
            }
            if (e.altKey && e.key === 'ArrowDown' && canGoDeeper) {
                e.preventDefault();
                drillDown();
            }

            // Alt+Left = go back in history
            if (e.altKey && e.key === 'ArrowLeft' && state.history.length > 1) {
                e.preventDefault();
                goBack();
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [setLevel, zoomOut, drillDown, goBack, canGoHigher, canGoDeeper, state.history.length]);

    return {
        // State
        currentLevel: state.currentLevel,
        levelInfo,
        breadcrumbs: state.breadcrumbs,
        history: state.history,
        selectedContext: state.selectedContext,

        // Navigation capabilities
        canDrillDown: canGoDeeper,
        canZoomOut: canGoHigher,
        canGoBack: state.history.length > 1,

        // Transition state
        isTransitioning: transition.isTransitioning,
        transitionDirection: transition.direction,

        // Actions
        setLevel,
        drillDown,
        zoomOut,
        navigateToBreadcrumb,
        goBack,
        reset,

        // Constants
        allLevels: ABSTRACTION_LEVELS,
        defaultContexts: DEFAULT_LEVEL_CONTEXT,
    };
}

// Helper to update breadcrumbs when level changes
function updateBreadcrumbs(
    current: AbstractionBreadcrumb[],
    newLevel: AbstractionLevel,
    context?: LevelContext
): AbstractionBreadcrumb[] {
    const newOrder = getLevelInfo(newLevel).order;

    // Find if we're navigating to an existing level in breadcrumbs
    const existingIndex = current.findIndex(b => b.level === newLevel);
    if (existingIndex >= 0) {
        // Navigating back - truncate to that point
        return current.slice(0, existingIndex + 1);
    }

    // Going deeper - add new breadcrumb
    const filtered = current.filter(b => getLevelInfo(b.level).order < newOrder);
    return [
        ...filtered,
        {
            level: newLevel,
            label: context ? getLabelFromContext(context) : getLevelInfo(newLevel).label,
            context: context?.data as Record<string, unknown> | undefined,
        },
    ];
}

// Get label from context for breadcrumb
function getLabelFromContext(context: LevelContext): string {
    switch (context.level) {
        case 'system':
            return `System (${context.data.services.length} services)`;
        case 'component':
            return `${context.data.components.length || 1} components`;
        case 'file':
            return context.data.currentFile || 'Files';
        case 'code':
            return context.data.currentSymbol || 'Code';
        default:
            return 'Unknown';
    }
}

// Hook for shortcuts only (when level management is handled elsewhere)
export function useAbstractionLevelShortcuts(
    setLevel: (level: AbstractionLevel) => void,
    drillDown: () => void,
    zoomOut: () => void,
    goBack: () => void,
    options: { canDrillDown: boolean; canZoomOut: boolean; canGoBack: boolean }
) {
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.altKey && !e.ctrlKey && !e.metaKey) {
                const levelNum = parseInt(e.key);
                if (levelNum >= 1 && levelNum <= 4) {
                    const level = ABSTRACTION_LEVELS[levelNum - 1]?.level;
                    if (level) {
                        e.preventDefault();
                        setLevel(level);
                    }
                }
            }

            if (e.altKey && e.key === 'ArrowUp' && options.canZoomOut) {
                e.preventDefault();
                zoomOut();
            }
            if (e.altKey && e.key === 'ArrowDown' && options.canDrillDown) {
                e.preventDefault();
                drillDown();
            }
            if (e.altKey && e.key === 'ArrowLeft' && options.canGoBack) {
                e.preventDefault();
                goBack();
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [setLevel, drillDown, zoomOut, goBack, options]);
}

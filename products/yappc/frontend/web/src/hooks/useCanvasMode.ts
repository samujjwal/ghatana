/**
 * Canvas Mode Hook
 * 
 * Manages canvas mode state, switching logic, and auto-suggestions.
 * Integrates with lifecycle phases to suggest appropriate modes.
 * 
 * @doc.type hook
 * @doc.purpose Canvas mode state management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback, useMemo, useEffect } from 'react';
import { useAtom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import {
    CanvasMode,
    CANVAS_MODES,
    CANVAS_MODE_CONFIG,
    getPrimaryModeForPhase,
    getModesForPhase,
    getModeConfig,
    isNodeTypeAllowed,
    getToolsForMode,
    getAIContextForMode,
    type CanvasModeConfig,
} from '../types/canvasMode';
import { LifecyclePhase } from '../types/lifecycle';
import { useLifecyclePhase } from './useLifecyclePhase';

// ============================================================================
// Atoms (Persisted State)
// ============================================================================

/**
 * Persisted canvas mode per project
 * Map of projectId -> mode
 */
const projectModeAtom = atomWithStorage<Record<string, CanvasMode>>(
    'canvas-mode-by-project',
    {}
);

/**
 * Whether to auto-switch modes based on lifecycle phase
 */
const autoSwitchModeAtom = atomWithStorage<boolean>(
    'canvas-mode-auto-switch',
    true
);

// ============================================================================
// Hook Interface
// ============================================================================

export interface UseCanvasModeResult {
    /** Current canvas mode */
    currentMode: CanvasMode;

    /** Current mode configuration */
    modeConfig: CanvasModeConfig;

    /** All available canvas modes */
    allModes: CanvasMode[];

    /** Modes recommended for current lifecycle phase */
    recommendedModes: CanvasMode[];

    /** Switch to a specific mode */
    setMode: (mode: CanvasMode) => void;

    /** Whether auto-switch is enabled */
    autoSwitchEnabled: boolean;

    /** Toggle auto-switch behavior */
    setAutoSwitch: (enabled: boolean) => void;

    /** Check if a node type is allowed in current mode */
    canAddNodeType: (nodeType: string) => boolean;

    /** Get available tools for current mode */
    availableTools: string[];

    /** Get AI context for current mode */
    aiContext: string;

    /** Current lifecycle phase (if available) */
    lifecyclePhase: LifecyclePhase | null;

    /** Whether current mode matches recommended for phase */
    isRecommendedMode: boolean;

    /** Keyboard shortcuts for mode switching */
    shortcuts: Array<{ key: string; mode: CanvasMode; label: string }>;
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * Hook for managing canvas mode state.
 * 
 * @param projectId - Optional project ID (defaults to route param)
 * 
 * Usage:
 * ```tsx
 * const { currentMode, setMode, modeConfig, availableTools } = useCanvasMode();
 * 
 * // Switch mode
 * setMode('code');
 * 
 * // Check if node type is allowed
 * if (canAddNodeType('function')) {
 *   // Allow adding function node
 * }
 * ```
 */
export function useCanvasMode(projectId?: string): UseCanvasModeResult {
    const { currentPhase, projectId: routeProjectId } = useLifecyclePhase();
    const effectiveProjectId = projectId || routeProjectId || 'default';

    const [projectModes, setProjectModes] = useAtom(projectModeAtom);
    const [autoSwitchEnabled, setAutoSwitch] = useAtom(autoSwitchModeAtom);

    // Get stored mode for this project, or derive from lifecycle phase
    const storedMode = projectModes[effectiveProjectId];
    const phaseMode = currentPhase ? getPrimaryModeForPhase(currentPhase) : 'diagram';

    // Current mode - use stored or derive from phase
    const currentMode = storedMode || phaseMode;

    // Current mode configuration
    const modeConfig = useMemo(() => getModeConfig(currentMode), [currentMode]);

    // Modes recommended for current lifecycle phase
    const recommendedModes = useMemo(() => {
        if (!currentPhase) return CANVAS_MODES;
        return getModesForPhase(currentPhase);
    }, [currentPhase]);

    // Check if current mode is recommended
    const isRecommendedMode = recommendedModes.includes(currentMode);

    // Auto-switch mode when phase changes (if enabled and no stored preference)
    useEffect(() => {
        if (autoSwitchEnabled && currentPhase && !storedMode) {
            const suggestedMode = getPrimaryModeForPhase(currentPhase);
            if (suggestedMode !== currentMode) {
                // Don't auto-persist, just use the phase-derived mode
                // This allows user to override without losing phase awareness
            }
        }
    }, [autoSwitchEnabled, currentPhase, storedMode, currentMode]);

    // Set mode for current project
    const setMode = useCallback((mode: CanvasMode) => {
        setProjectModes((prev) => ({
            ...prev,
            [effectiveProjectId]: mode,
        }));
    }, [effectiveProjectId, setProjectModes]);

    // Check if node type is allowed
    const canAddNodeType = useCallback(
        (nodeType: string) => isNodeTypeAllowed(currentMode, nodeType),
        [currentMode]
    );

    // Available tools for current mode
    const availableTools = useMemo(
        () => getToolsForMode(currentMode),
        [currentMode]
    );

    // AI context for current mode
    const aiContext = useMemo(
        () => getAIContextForMode(currentMode),
        [currentMode]
    );

    // Keyboard shortcuts
    const shortcuts = useMemo(() =>
        CANVAS_MODES.map((mode) => ({
            key: CANVAS_MODE_CONFIG[mode].shortcut,
            mode,
            label: CANVAS_MODE_CONFIG[mode].label,
        })),
        []
    );

    return {
        currentMode,
        modeConfig,
        allModes: CANVAS_MODES,
        recommendedModes,
        setMode,
        autoSwitchEnabled,
        setAutoSwitch,
        canAddNodeType,
        availableTools,
        aiContext,
        lifecyclePhase: currentPhase,
        isRecommendedMode,
        shortcuts,
    };
}

// ============================================================================
// Utility Hooks
// ============================================================================

/**
 * Hook for mode-specific keyboard shortcuts
 * 
 * Usage:
 * ```tsx
 * useCanvasModeShortcuts(); // Enables 1-7 key switching
 * ```
 */
export function useCanvasModeShortcuts() {
    const { setMode, shortcuts } = useCanvasMode();

    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            // Only trigger if not in an input/textarea
            if (
                e.target instanceof HTMLInputElement ||
                e.target instanceof HTMLTextAreaElement ||
                e.target instanceof HTMLSelectElement
            ) {
                return;
            }

            // Check for number keys 1-7
            const shortcut = shortcuts.find((s) => s.key === e.key);
            if (shortcut) {
                e.preventDefault();
                setMode(shortcut.mode);
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [setMode, shortcuts]);
}

/**
 * Hook to get mode-specific node registry
 * 
 * Usage:
 * ```tsx
 * const { nodeTypes, defaultNodeType } = useCanvasModeNodes();
 * ```
 */
export function useCanvasModeNodes() {
    const { modeConfig } = useCanvasMode();

    return useMemo(() => ({
        nodeTypes: modeConfig.nodeTypes,
        defaultNodeType: modeConfig.nodeTypes[0] || 'note',
        canAddNode: (type: string) => modeConfig.nodeTypes.includes(type),
    }), [modeConfig]);
}

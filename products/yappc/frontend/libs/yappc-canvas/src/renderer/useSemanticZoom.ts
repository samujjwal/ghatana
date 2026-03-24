/**
 * Semantic Zoom Hook
 *
 * Provides zoom-level aware rendering capabilities.
 * Changes detail level, visibility, and representation based on zoom level.
 *
 * @doc.type hook
 * @doc.purpose Zoom-aware rendering
 * @doc.layer core
 * @doc.pattern Strategy Pattern
 */

import { useMemo, useCallback } from 'react';
import type { ArtifactContract, UniversalNode } from '../model/contracts';

// ============================================================================
// Semantic Zoom Types
// ============================================================================

/**
 * Detail level thresholds
 */
export enum DetailLevel {
    /** Minimal representation (icon/placeholder) */
    Minimal = 'minimal',
    /** Reduced details (basic shape, no content) */
    Reduced = 'reduced',
    /** Standard detail level */
    Standard = 'standard',
    /** Full details with all features */
    Full = 'full',
    /** Maximum detail with expanded information */
    Expanded = 'expanded',
}

/**
 * Visibility categories
 */
export interface VisibilityState {
    /** Whether to show the node at all */
    visible: boolean;
    /** Whether to show node content */
    showContent: boolean;
    /** Whether to show node name/label */
    showLabel: boolean;
    /** Whether to show resize handles */
    showHandles: boolean;
    /** Whether to show connection points */
    showConnectors: boolean;
    /** Whether to show annotations */
    showAnnotations: boolean;
    /** Whether to show children */
    showChildren: boolean;
    /** Whether node is interactive */
    interactive: boolean;
    /** Text font size multiplier */
    fontScale: number;
    /** Border width in pixels */
    borderWidth: number;
}

/**
 * Zoom range definition
 */
export interface ZoomRange {
    /** Minimum zoom value for this range */
    min: number;
    /** Maximum zoom value for this range */
    max: number;
    /** Detail level for this range */
    detailLevel: DetailLevel;
}

/**
 * Default zoom ranges
 */
export const DEFAULT_ZOOM_RANGES: ZoomRange[] = [
    { min: 0, max: 0.15, detailLevel: DetailLevel.Minimal },
    { min: 0.15, max: 0.35, detailLevel: DetailLevel.Reduced },
    { min: 0.35, max: 0.8, detailLevel: DetailLevel.Standard },
    { min: 0.8, max: 2, detailLevel: DetailLevel.Full },
    { min: 2, max: Infinity, detailLevel: DetailLevel.Expanded },
];

/**
 * Semantic zoom configuration
 */
export interface SemanticZoomConfig {
    /** Custom zoom ranges */
    zoomRanges?: ZoomRange[];
    /** Minimum node size to be visible (in screen pixels) */
    minVisibleSize?: number;
    /** Whether to collapse nodes below threshold */
    collapseSmallNodes?: boolean;
    /** Custom visibility resolver */
    visibilityResolver?: (
        node: UniversalNode,
        contract: ArtifactContract | undefined,
        zoom: number,
        detailLevel: DetailLevel
    ) => Partial<VisibilityState>;
}

/**
 * Semantic zoom result
 */
export interface SemanticZoomResult {
    /** Current detail level */
    detailLevel: DetailLevel;
    /** Visibility state for rendering decisions */
    getVisibility: (
        node: UniversalNode,
        contract?: ArtifactContract
    ) => VisibilityState;
    /** Whether a node should be rendered */
    shouldRender: (node: UniversalNode) => boolean;
    /** Get appropriate representation for a node */
    getRepresentation: (
        node: UniversalNode,
        contract?: ArtifactContract
    ) => NodeRepresentation;
    /** Check if we're at a semantic boundary */
    isAtBoundary: (direction: 'in' | 'out') => boolean;
    /** Get next semantic zoom target */
    getNextZoomTarget: (direction: 'in' | 'out') => number | null;
}

/**
 * Node representation type
 */
export type NodeRepresentation =
    | 'full'
    | 'simplified'
    | 'icon'
    | 'dot'
    | 'hidden';

// ============================================================================
// Semantic Zoom Hook
// ============================================================================

/**
 * Hook for semantic zoom functionality
 *
 * @param zoom - Current zoom level
 * @param config - Optional configuration
 * @returns Semantic zoom utilities
 */
export function useSemanticZoom(
    zoom: number,
    config?: SemanticZoomConfig
): SemanticZoomResult {
    const {
        zoomRanges = DEFAULT_ZOOM_RANGES,
        minVisibleSize = 4,
        collapseSmallNodes = true,
        visibilityResolver,
    } = config ?? {};

    // Determine current detail level
    const detailLevel = useMemo((): DetailLevel => {
        const range = zoomRanges.find((r) => zoom >= r.min && zoom < r.max);
        return range?.detailLevel ?? DetailLevel.Standard;
    }, [zoom, zoomRanges]);

    // Get visibility state for a node
    const getVisibility = useCallback(
        (
            node: UniversalNode,
            contract?: ArtifactContract
        ): VisibilityState => {
            // Base visibility from detail level
            const baseVisibility = getBaseVisibility(detailLevel);

            // Check if node is too small to be visible
            const screenWidth = node.transform.width * zoom;
            const screenHeight = node.transform.height * zoom;
            const minDimension = Math.min(screenWidth, screenHeight);

            if (collapseSmallNodes && minDimension < minVisibleSize) {
                return {
                    ...baseVisibility,
                    visible: minDimension >= 1, // Still render as dot if very small
                    showContent: false,
                    showLabel: false,
                    showHandles: false,
                    showConnectors: false,
                    showAnnotations: false,
                    showChildren: false,
                    interactive: false,
                };
            }

            // Apply custom visibility resolver
            if (visibilityResolver) {
                const custom = visibilityResolver(node, contract, zoom, detailLevel);
                return { ...baseVisibility, ...custom };
            }

            // Apply contract-specific visibility rules
            if (contract?.renderContract.zoomBehavior) {
                const behavior = contract.renderContract.zoomBehavior;

                // Check if there's a threshold for showing this node type
                if (
                    behavior.hideAtZoom !== undefined &&
                    zoom < behavior.hideAtZoom
                ) {
                    return { ...baseVisibility, visible: false };
                }

                // Check if there's a collapse threshold
                if (
                    behavior.collapseAtZoom !== undefined &&
                    zoom < behavior.collapseAtZoom
                ) {
                    return {
                        ...baseVisibility,
                        showContent: false,
                        showChildren: false,
                    };
                }
            }

            return baseVisibility;
        },
        [
            zoom,
            detailLevel,
            minVisibleSize,
            collapseSmallNodes,
            visibilityResolver,
        ]
    );

    // Check if a node should be rendered
    const shouldRender = useCallback(
        (node: UniversalNode): boolean => {
            // Always render if visible flag is set
            if (node.visible === false) return false;

            // Check minimum screen size
            const screenWidth = node.transform.width * zoom;
            const screenHeight = node.transform.height * zoom;

            // Don't render nodes smaller than 1 pixel
            return Math.max(screenWidth, screenHeight) >= 1;
        },
        [zoom]
    );

    // Get appropriate representation for a node
    const getRepresentation = useCallback(
        (
            node: UniversalNode,
            contract?: ArtifactContract
        ): NodeRepresentation => {
            const screenWidth = node.transform.width * zoom;
            const screenHeight = node.transform.height * zoom;
            const minDimension = Math.min(screenWidth, screenHeight);

            // Very small nodes become dots
            if (minDimension < 4) {
                return 'dot';
            }

            // Small nodes become icons
            if (minDimension < 24) {
                return 'icon';
            }

            // Medium nodes get simplified rendering
            if (detailLevel === DetailLevel.Minimal || minDimension < 48) {
                return 'simplified';
            }

            // Check if hidden by zoom behavior
            if (contract?.renderContract.zoomBehavior?.hideAtZoom !== undefined) {
                if (zoom < contract.renderContract.zoomBehavior.hideAtZoom) {
                    return 'hidden';
                }
            }

            return 'full';
        },
        [zoom, detailLevel]
    );

    // Check if we're at a semantic boundary
    const isAtBoundary = useCallback(
        (direction: 'in' | 'out'): boolean => {
            const currentRange = zoomRanges.find(
                (r) => zoom >= r.min && zoom < r.max
            );
            if (!currentRange) return false;

            const threshold = 0.05; // 5% of range

            if (direction === 'in') {
                const rangeSize = currentRange.max - currentRange.min;
                return currentRange.max - zoom < rangeSize * threshold;
            } else {
                const rangeSize = currentRange.max - currentRange.min;
                return zoom - currentRange.min < rangeSize * threshold;
            }
        },
        [zoom, zoomRanges]
    );

    // Get next semantic zoom target
    const getNextZoomTarget = useCallback(
        (direction: 'in' | 'out'): number | null => {
            const currentIndex = zoomRanges.findIndex(
                (r) => zoom >= r.min && zoom < r.max
            );

            if (currentIndex === -1) return null;

            if (direction === 'in') {
                const nextRange = zoomRanges[currentIndex + 1];
                if (!nextRange || nextRange.max === Infinity) return null;
                // Target middle of next range
                return (nextRange.min + nextRange.max) / 2;
            } else {
                const prevRange = zoomRanges[currentIndex - 1];
                if (!prevRange) return null;
                // Target middle of previous range
                return (prevRange.min + prevRange.max) / 2;
            }
        },
        [zoom, zoomRanges]
    );

    return {
        detailLevel,
        getVisibility,
        shouldRender,
        getRepresentation,
        isAtBoundary,
        getNextZoomTarget,
    };
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Get base visibility state for a detail level
 */
function getBaseVisibility(level: DetailLevel): VisibilityState {
    switch (level) {
        case DetailLevel.Minimal:
            return {
                visible: true,
                showContent: false,
                showLabel: false,
                showHandles: false,
                showConnectors: false,
                showAnnotations: false,
                showChildren: false,
                interactive: false,
                fontScale: 0.5,
                borderWidth: 1,
            };

        case DetailLevel.Reduced:
            return {
                visible: true,
                showContent: false,
                showLabel: true,
                showHandles: false,
                showConnectors: false,
                showAnnotations: false,
                showChildren: true,
                interactive: true,
                fontScale: 0.75,
                borderWidth: 1,
            };

        case DetailLevel.Standard:
            return {
                visible: true,
                showContent: true,
                showLabel: true,
                showHandles: true,
                showConnectors: true,
                showAnnotations: false,
                showChildren: true,
                interactive: true,
                fontScale: 1,
                borderWidth: 1,
            };

        case DetailLevel.Full:
            return {
                visible: true,
                showContent: true,
                showLabel: true,
                showHandles: true,
                showConnectors: true,
                showAnnotations: true,
                showChildren: true,
                interactive: true,
                fontScale: 1,
                borderWidth: 1,
            };

        case DetailLevel.Expanded:
            return {
                visible: true,
                showContent: true,
                showLabel: true,
                showHandles: true,
                showConnectors: true,
                showAnnotations: true,
                showChildren: true,
                interactive: true,
                fontScale: 1.25,
                borderWidth: 2,
            };

        default:
            return {
                visible: true,
                showContent: true,
                showLabel: true,
                showHandles: true,
                showConnectors: true,
                showAnnotations: false,
                showChildren: true,
                interactive: true,
                fontScale: 1,
                borderWidth: 1,
            };
    }
}

/**
 * Calculate optimal zoom level to fit a node in viewport
 */
export function calculateFitZoom(
    nodeWidth: number,
    nodeHeight: number,
    viewportWidth: number,
    viewportHeight: number,
    padding: number = 0.1
): number {
    const paddedViewportWidth = viewportWidth * (1 - padding * 2);
    const paddedViewportHeight = viewportHeight * (1 - padding * 2);

    const scaleX = paddedViewportWidth / nodeWidth;
    const scaleY = paddedViewportHeight / nodeHeight;

    return Math.min(scaleX, scaleY);
}

/**
 * Get semantic zoom description for UI feedback
 */
export function getZoomDescription(level: DetailLevel): string {
    switch (level) {
        case DetailLevel.Minimal:
            return 'Overview - Minimal details';
        case DetailLevel.Reduced:
            return 'Summary - Basic shapes and labels';
        case DetailLevel.Standard:
            return 'Normal - Standard content view';
        case DetailLevel.Full:
            return 'Detailed - All information visible';
        case DetailLevel.Expanded:
            return 'Focused - Maximum detail level';
        default:
            return 'Unknown zoom level';
    }
}

export default useSemanticZoom;

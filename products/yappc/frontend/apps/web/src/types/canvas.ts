/**
 * Canvas Types
 * 
 * Re-exports and additional canvas-related type definitions.
 * 
 * @doc.type types
 * @doc.purpose Canvas type definitions
 * @doc.layer product
 * @doc.pattern Type Definitions
 */

// Re-export canvas mode types
export type { CanvasMode, CanvasModeConfig } from './canvasMode';
export { CANVAS_MODES, CANVAS_MODE_CONFIG } from './canvasMode';

// ============================================================================
// Abstraction Levels
// ============================================================================

/**
 * Abstraction levels for canvas navigation.
 * 
 * - system: High-level architecture view
 * - component: Component-level relationships
 * - file: File-level details
 * - code: Implementation details
 */
export type AbstractionLevel = 'system' | 'component' | 'file' | 'code';

/**
 * Complete set of abstraction levels
 */
export const ABSTRACTION_LEVELS: AbstractionLevel[] = [
    'system',
    'component',
    'file',
    'code',
];

/**
 * Metadata for each abstraction level
 */
export interface AbstractionLevelConfig {
    /** Level identifier */
    id: AbstractionLevel;
    /** Display label */
    label: string;
    /** Description */
    description: string;
    /** Icon name */
    icon: string;
    /** Zoom factor */
    zoomFactor: number;
}

export const ABSTRACTION_LEVEL_CONFIG: Record<AbstractionLevel, AbstractionLevelConfig> = {
    system: {
        id: 'system',
        label: 'System',
        description: 'High-level architecture view',
        icon: 'Public',
        zoomFactor: 0.25,
    },
    component: {
        id: 'component',
        label: 'Component',
        description: 'Component relationships',
        icon: 'Apps',
        zoomFactor: 0.5,
    },
    file: {
        id: 'file',
        label: 'File',
        description: 'File-level details',
        icon: 'InsertDriveFile',
        zoomFactor: 0.75,
    },
    code: {
        id: 'code',
        label: 'Code',
        description: 'Implementation details',
        icon: 'DataObject',
        zoomFactor: 1.0,
    },
};

// ============================================================================
// Canvas Element Types
// ============================================================================

/**
 * Position on canvas
 */
export interface CanvasPosition {
    x: number;
    y: number;
}

/**
 * Canvas viewport
 */
export interface CanvasViewport {
    x: number;
    y: number;
    zoom: number;
}

/**
 * Canvas selection state
 */
export interface CanvasSelection {
    nodeIds: string[];
    edgeIds: string[];
}

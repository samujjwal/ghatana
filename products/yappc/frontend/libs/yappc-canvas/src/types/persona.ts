/**
 * @doc.type interface
 * @doc.purpose Persona-specific canvas configuration types for YAPPC
 * @doc.layer product
 * @doc.pattern ValueObject
 */

import type { Node, Edge } from '@xyflow/react';

/**
 * YAPPC Persona types aligned with user journeys
 */
export type PersonaType = 'PM' | 'Architect' | 'Developer' | 'QA';

/**
 * View mode specific to each persona's workflow
 */
export type PersonaViewMode =
    | 'roadmap' // PM: Timeline-based roadmap view
    | 'system-design' // Architect: Layered architecture view
    | 'code-tree' // Developer: File/code hierarchy view
    | 'test-coverage'; // QA: Coverage and test status view

/**
 * Canvas layout configuration for persona-specific views
 */
export interface PersonaCanvasLayout {
    /** Layout algorithm: hierarchical, force-directed, grid */
    algorithm: 'hierarchical' | 'dagre' | 'grid' | 'timeline';
    /** Direction: vertical, horizontal */
    direction: 'TB' | 'LR' | 'BT' | 'RL';
    /** Spacing between nodes */
    spacing: { x: number; y: number };
    /** Enable automatic layout on changes */
    autoLayout: boolean;
}

/**
 * Toolbar configuration for persona-specific actions
 */
export interface PersonaToolbarConfig {
    /** Visible toolbar sections */
    sections: {
        grouping?: boolean;
        testGeneration?: boolean;
        deployment?: boolean;
        documentation?: boolean;
        codeGen?: boolean;
    };
    /** Custom actions for this persona */
    customActions?: Array<{
        id: string;
        label: string;
        icon: string;
        action: string;
    }>;
}

/**
 * Node styling configuration for persona views
 */
export interface PersonaNodeStyle {
    /** Default node colors */
    colors: {
        default: string;
        selected: string;
        grouped: string;
        testing: string;
        deployed: string;
    };
    /** Node shape: rectangle, rounded, circle */
    shape: 'rectangle' | 'rounded' | 'circle' | 'diamond';
    /** Show node icons */
    showIcons: boolean;
    /** Show node badges (status, coverage, etc.) */
    showBadges: boolean;
}

/**
 * Panel configuration for persona-specific side panels
 */
export interface PersonaPanelConfig {
    /** Left sidebar panels */
    left?: Array<{
        id: string;
        title: string;
        component: string;
        defaultOpen?: boolean;
    }>;
    /** Right sidebar panels */
    right?: Array<{
        id: string;
        title: string;
        component: string;
        defaultOpen?: boolean;
    }>;
    /** Bottom panel configuration */
    bottom?: {
        id: string;
        title: string;
        component: string;
        defaultHeight?: number;
    };
}

/**
 * Complete persona canvas configuration
 */
export interface PersonaCanvasConfig {
    /** Persona identifier */
    type: PersonaType;
    /** Human-readable name */
    name: string;
    /** View mode for this persona */
    viewMode: PersonaViewMode;
    /** Layout configuration */
    layout: PersonaCanvasLayout;
    /** Toolbar configuration */
    toolbar: PersonaToolbarConfig;
    /** Node styling */
    nodeStyle: PersonaNodeStyle;
    /** Panel configuration */
    panels: PersonaPanelConfig;
    /** Enabled features */
    features: {
        grouping: boolean;
        testGeneration: boolean;
        codeGeneration: boolean;
        deployment: boolean;
        versioning: boolean;
        collaboration: boolean;
    };
}

/**
 * Node filter function for persona-specific views
 */
export type PersonaNodeFilter = (node: Node) => boolean;

/**
 * Edge filter function for persona-specific views
 */
export type PersonaEdgeFilter = (edge: Edge) => boolean;

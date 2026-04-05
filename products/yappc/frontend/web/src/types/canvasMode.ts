/**
 * Canvas Mode Types
 * 
 * Defines activity-based canvas modes that determine available tools,
 * node types, and AI behaviors. Each mode optimizes the canvas for
 * a specific type of work.
 * 
 * @doc.type types
 * @doc.purpose Canvas mode definitions and utilities
 * @doc.layer product
 * @doc.pattern Type Definitions
 */

import { LifecyclePhase } from './lifecycle';

// ============================================================================
// Core Types
// ============================================================================

/**
 * Canvas modes based on activity type.
 * 
 * - brainstorm: Free-form idea capture, mind mapping
 * - diagram: Architecture diagrams, flow charts, ERD
 * - design: UI/UX design, component layouts
 * - code: Code generation, snippets, API endpoints
 * - test: Test case design, coverage visualization
 * - deploy: Infrastructure, CI/CD, deployment configs
 * - observe: Monitoring, metrics, log visualization
 */
export type CanvasMode =
    | 'brainstorm'
    | 'diagram'
    | 'design'
    | 'code'
    | 'test'
    | 'deploy'
    | 'observe';

/**
 * Complete set of canvas modes
 */
export const CANVAS_MODES: CanvasMode[] = [
    'brainstorm',
    'diagram',
    'design',
    'code',
    'test',
    'deploy',
    'observe',
];

/**
 * Metadata for each canvas mode
 */
export interface CanvasModeConfig {
    /** Mode identifier */
    id: CanvasMode;
    /** Display label */
    label: string;
    /** Short description */
    description: string;
    /** Icon name (Material Icons) */
    icon: string;
    /** Primary color (for badges, accents) */
    color: string;
    /** Dark mode color */
    colorDark: string;
    /** Background gradient */
    gradient: string;
    /** Keyboard shortcut */
    shortcut: string;
    /** Allowed node types */
    nodeTypes: string[];
    /** Default tools shown in palette */
    defaultTools: string[];
    /** Lifecycle phases where this mode is primary */
    primaryPhases: LifecyclePhase[];
    /** AI prompt context for this mode */
    aiContext: string;
}

// ============================================================================
// Mode Configurations
// ============================================================================

export const CANVAS_MODE_CONFIG: Record<CanvasMode, CanvasModeConfig> = {
    brainstorm: {
        id: 'brainstorm',
        label: 'Brainstorm',
        description: 'Capture ideas, mind map, explore concepts',
        icon: 'Lightbulb',
        color: 'amber-500',
        colorDark: 'amber-400',
        gradient: 'from-amber-500 to-orange-500',
        shortcut: '1',
        nodeTypes: ['idea', 'note', 'question', 'cluster', 'connection'],
        defaultTools: ['add-idea', 'add-note', 'add-question', 'group', 'connect'],
        primaryPhases: [LifecyclePhase.INTENT],
        aiContext: 'Help brainstorm and explore ideas. Suggest related concepts, ask clarifying questions, and identify potential challenges.',
    },
    diagram: {
        id: 'diagram',
        label: 'Diagram',
        description: 'Architecture, flows, ERD, system design',
        icon: 'AccountTree',
        color: 'blue-500',
        colorDark: 'blue-400',
        gradient: 'from-blue-500 to-cyan-500',
        shortcut: '2',
        nodeTypes: ['component', 'service', 'database', 'api', 'queue', 'flow', 'decision', 'entity'],
        defaultTools: ['add-component', 'add-service', 'add-database', 'add-api', 'connect', 'group'],
        primaryPhases: [LifecyclePhase.SHAPE],
        aiContext: 'Help design system architecture. Suggest patterns, identify dependencies, validate connections, and recommend improvements.',
    },
    design: {
        id: 'design',
        label: 'Design',
        description: 'UI layouts, wireframes, component design',
        icon: 'Palette',
        color: 'purple-500',
        colorDark: 'purple-400',
        gradient: 'from-purple-500 to-pink-500',
        shortcut: '3',
        nodeTypes: ['screen', 'component', 'layout', 'widget', 'navigation', 'interaction'],
        defaultTools: ['add-screen', 'add-component', 'add-widget', 'add-navigation', 'connect'],
        primaryPhases: [LifecyclePhase.SHAPE],
        aiContext: 'Help design user interfaces. Suggest layouts, recommend components, ensure accessibility, and validate UX patterns.',
    },
    code: {
        id: 'code',
        label: 'Code',
        description: 'Code snippets, APIs, endpoints, logic',
        icon: 'Code',
        color: 'green-500',
        colorDark: 'green-400',
        gradient: 'from-green-500 to-emerald-500',
        shortcut: '4',
        nodeTypes: ['function', 'class', 'module', 'endpoint', 'hook', 'type', 'config'],
        defaultTools: ['add-function', 'add-class', 'add-endpoint', 'add-hook', 'generate'],
        primaryPhases: [LifecyclePhase.GENERATE],
        aiContext: 'Help write and generate code. Suggest implementations, identify bugs, optimize performance, and ensure best practices.',
    },
    test: {
        id: 'test',
        label: 'Test',
        description: 'Test cases, coverage, quality assurance',
        icon: 'Science',
        color: 'teal-500',
        colorDark: 'teal-400',
        gradient: 'from-teal-500 to-cyan-500',
        shortcut: '5',
        nodeTypes: ['test-suite', 'test-case', 'fixture', 'mock', 'assertion', 'coverage'],
        defaultTools: ['add-test', 'add-suite', 'add-mock', 'run-tests', 'coverage'],
        primaryPhases: [LifecyclePhase.VALIDATE],
        aiContext: 'Help design and generate tests. Suggest test cases, identify edge cases, ensure coverage, and validate quality.',
    },
    deploy: {
        id: 'deploy',
        label: 'Deploy',
        description: 'Infrastructure, CI/CD, deployment',
        icon: 'CloudUpload',
        color: 'indigo-500',
        colorDark: 'indigo-400',
        gradient: 'from-indigo-500 to-violet-500',
        shortcut: '6',
        nodeTypes: ['environment', 'service', 'pipeline', 'stage', 'artifact', 'secret', 'resource'],
        defaultTools: ['add-environment', 'add-pipeline', 'add-stage', 'configure', 'deploy'],
        primaryPhases: [LifecyclePhase.RUN],
        aiContext: 'Help configure deployments. Suggest infrastructure, optimize pipelines, ensure security, and validate configurations.',
    },
    observe: {
        id: 'observe',
        label: 'Observe',
        description: 'Monitoring, metrics, logs, insights',
        icon: 'Visibility',
        color: 'rose-500',
        colorDark: 'rose-400',
        gradient: 'from-rose-500 to-pink-500',
        shortcut: '7',
        nodeTypes: ['metric', 'alert', 'dashboard', 'log', 'trace', 'insight'],
        defaultTools: ['add-metric', 'add-alert', 'add-dashboard', 'query', 'analyze'],
        primaryPhases: [LifecyclePhase.OBSERVE, LifecyclePhase.IMPROVE],
        aiContext: 'Help analyze and improve. Identify patterns, suggest optimizations, detect anomalies, and recommend actions.',
    },
};

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Get the primary canvas mode for a lifecycle phase
 */
export function getPrimaryModeForPhase(phase: LifecyclePhase): CanvasMode {
    switch (phase) {
        case LifecyclePhase.INTENT:
            return 'brainstorm';
        case LifecyclePhase.SHAPE:
            return 'diagram';
        case LifecyclePhase.VALIDATE:
            return 'test';
        case LifecyclePhase.GENERATE:
            return 'code';
        case LifecyclePhase.RUN:
            return 'deploy';
        case LifecyclePhase.OBSERVE:
        case LifecyclePhase.IMPROVE:
            return 'observe';
        default:
            return 'diagram';
    }
}

/**
 * Get all canvas modes available for a lifecycle phase
 */
export function getModesForPhase(phase: LifecyclePhase): CanvasMode[] {
    const primary = getPrimaryModeForPhase(phase);
    const all = CANVAS_MODES.filter(mode => {
        const config = CANVAS_MODE_CONFIG[mode];
        return config.primaryPhases.includes(phase);
    });

    // Ensure primary is first, then add others
    if (!all.includes(primary)) {
        all.unshift(primary);
    } else {
        all.sort((a, b) => (a === primary ? -1 : b === primary ? 1 : 0));
    }

    return all;
}

/**
 * Check if a node type is allowed in a canvas mode
 */
export function isNodeTypeAllowed(mode: CanvasMode, nodeType: string): boolean {
    const config = CANVAS_MODE_CONFIG[mode];
    return config.nodeTypes.includes(nodeType);
}

/**
 * Get the default tools for a canvas mode
 */
export function getToolsForMode(mode: CanvasMode): string[] {
    return CANVAS_MODE_CONFIG[mode].defaultTools;
}

/**
 * Get the AI context prompt for a canvas mode
 */
export function getAIContextForMode(mode: CanvasMode): string {
    return CANVAS_MODE_CONFIG[mode].aiContext;
}

/**
 * Get mode configuration
 */
export function getModeConfig(mode: CanvasMode): CanvasModeConfig {
    return CANVAS_MODE_CONFIG[mode];
}

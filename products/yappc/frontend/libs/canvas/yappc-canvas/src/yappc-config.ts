/**
 * YAPPC Canvas Configuration
 * 
 * YAPPC-specific implementation of the canvas configuration.
 * Defines YAPPC's 5 semantic layers, 7 lifecycle phases, and 9 persona roles.
 * 
 * @doc.type yappc
 * @doc.purpose YAPPC-specific canvas configuration
 * @doc.layer application
 */

import { CanvasConfig, LayerConfig, PhaseConfig, RoleConfig } from '../core/canvas-config';

/**
 * YAPPC Semantic Layers
 */
export type YAPPCLayer =
    | 'architecture'
    | 'design'
    | 'component'
    | 'implementation'
    | 'detail';

/**
 * YAPPC Lifecycle Phases
 */
export type YAPPCPhase =
    | 'INTENT'
    | 'SHAPE'
    | 'VALIDATE'
    | 'GENERATE'
    | 'RUN'
    | 'OBSERVE'
    | 'IMPROVE';

/**
 * YAPPC Persona Roles
 */
export type YAPPCRole =
    | 'product_owner'
    | 'architect'
    | 'developer'
    | 'qa_engineer'
    | 'devops_engineer'
    | 'security_engineer'
    | 'ux_designer'
    | 'data_engineer'
    | 'business_analyst';

/**
 * YAPPC Action Context
 */
export interface YAPPCActionContext {
    layer?: YAPPCLayer;
    phase?: YAPPCPhase;
    roles?: YAPPCRole[];
    selection?: 'none' | 'single' | 'multiple';
    elementType?: string;
}

/**
 * YAPPC Layer Configurations
 */
export const YAPPC_LAYERS: Record<YAPPCLayer, LayerConfig<YAPPCLayer>> = {
    architecture: {
        name: 'architecture',
        zoomRange: [0.1, 0.5],
        description: 'System design, high-level flows',
        primaryFocus: 'Services, databases, system boundaries',
    },
    design: {
        name: 'design',
        zoomRange: [0.5, 1.0],
        description: 'Component design, wireframes',
        primaryFocus: 'UI components, screens, user flows',
    },
    component: {
        name: 'component',
        zoomRange: [1.0, 2.0],
        description: 'Detailed components, interactions',
        primaryFocus: 'Component details, state, events',
    },
    implementation: {
        name: 'implementation',
        zoomRange: [2.0, 5.0],
        description: 'Code, logic, data structures',
        primaryFocus: 'Code blocks, functions, classes',
    },
    detail: {
        name: 'detail',
        zoomRange: [5.0, Infinity],
        description: 'Line-by-line code, debugging',
        primaryFocus: 'Inline code, breakpoints, variables',
    },
};

/**
 * YAPPC Phase Configurations
 */
export const YAPPC_PHASES: Record<YAPPCPhase, PhaseConfig<YAPPCPhase>> = {
    INTENT: {
        name: 'INTENT',
        displayName: 'Intent',
        color: {
            primary: '#8e24aa',
            background: '#f3e5f5',
            text: '#4a148c',
        },
        description: 'Ideation, requirements, vision',
    },
    SHAPE: {
        name: 'SHAPE',
        displayName: 'Shape',
        color: {
            primary: '#1976d2',
            background: '#e3f2fd',
            text: '#0d47a1',
        },
        description: 'Architecture, design, structure',
    },
    VALIDATE: {
        name: 'VALIDATE',
        displayName: 'Validate',
        color: {
            primary: '#00897b',
            background: '#e0f2f1',
            text: '#004d40',
        },
        description: 'Testing, verification, validation',
    },
    GENERATE: {
        name: 'GENERATE',
        displayName: 'Generate',
        color: {
            primary: '#388e3c',
            background: '#e8f5e9',
            text: '#1b5e20',
        },
        description: 'Code generation, scaffolding',
    },
    RUN: {
        name: 'RUN',
        displayName: 'Run',
        color: {
            primary: '#f57c00',
            background: '#fff3e0',
            text: '#e65100',
        },
        description: 'Execution, deployment, operations',
    },
    OBSERVE: {
        name: 'OBSERVE',
        displayName: 'Observe',
        color: {
            primary: '#5e35b1',
            background: '#ede7f6',
            text: '#311b92',
        },
        description: 'Monitoring, metrics, observability',
    },
    IMPROVE: {
        name: 'IMPROVE',
        displayName: 'Improve',
        color: {
            primary: '#c62828',
            background: '#ffebee',
            text: '#b71c1c',
        },
        description: 'Optimization, refactoring, enhancement',
    },
};

/**
 * YAPPC Role Configurations
 */
export const YAPPC_ROLES: Record<YAPPCRole, RoleConfig<YAPPCRole>> = {
    product_owner: {
        name: 'product_owner',
        displayName: 'Product Owner',
        icon: '📋',
        color: '#8e24aa',
        description: 'Product vision and requirements',
    },
    architect: {
        name: 'architect',
        displayName: 'Architect',
        icon: '🏗️',
        color: '#1976d2',
        description: 'System architecture and design',
    },
    developer: {
        name: 'developer',
        displayName: 'Developer',
        icon: '💻',
        color: '#388e3c',
        description: 'Code implementation',
    },
    qa_engineer: {
        name: 'qa_engineer',
        displayName: 'QA Engineer',
        icon: '✓',
        color: '#f57c00',
        description: 'Quality assurance and testing',
    },
    devops_engineer: {
        name: 'devops_engineer',
        displayName: 'DevOps Engineer',
        icon: '⚙️',
        color: '#c62828',
        description: 'Deployment and operations',
    },
    security_engineer: {
        name: 'security_engineer',
        displayName: 'Security Engineer',
        icon: '🔒',
        color: '#5e35b1',
        description: 'Security and compliance',
    },
    ux_designer: {
        name: 'ux_designer',
        displayName: 'UX Designer',
        icon: '🎨',
        color: '#00897b',
        description: 'User experience design',
    },
    data_engineer: {
        name: 'data_engineer',
        displayName: 'Data Engineer',
        icon: '📊',
        color: '#0288d1',
        description: 'Data pipelines and analytics',
    },
    business_analyst: {
        name: 'business_analyst',
        displayName: 'Business Analyst',
        icon: '📈',
        color: '#7b1fa2',
        description: 'Business analysis and requirements',
    },
};

/**
 * YAPPC layer detection from zoom
 */
export function getYAPPCLayerFromZoom(zoom: number): YAPPCLayer {
    if (zoom < 0.5) return 'architecture';
    if (zoom < 1.0) return 'design';
    if (zoom < 2.0) return 'component';
    if (zoom < 5.0) return 'implementation';
    return 'detail';
}

/**
 * Create YAPPC canvas configuration
 * 
 * This function creates a complete canvas configuration for YAPPC.
 * Actions are provided separately to allow customization.
 */
export function createYAPPCConfig(options: {
    layerActions: Record<YAPPCLayer, unknown[]>;
    phaseActions: Record<YAPPCPhase, unknown[]>;
    roleActions: Record<YAPPCRole, unknown[]>;
    universalActions: unknown[];
}): CanvasConfig<YAPPCLayer, YAPPCPhase, YAPPCRole, YAPPCActionContext> {
    return {
        appName: 'YAPPC',
        layers: YAPPC_LAYERS,
        phases: YAPPC_PHASES,
        roles: YAPPC_ROLES,
        layerActions: options.layerActions,
        phaseActions: options.phaseActions,
        roleActions: options.roleActions,
        universalActions: options.universalActions,
        defaultLayer: 'architecture',
        defaultPhase: 'SHAPE',
        defaultRoles: ['architect'],
        getLayerFromZoom: getYAPPCLayerFromZoom,
    };
}

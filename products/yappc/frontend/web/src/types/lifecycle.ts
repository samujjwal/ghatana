/**
 * Lifecycle Phase Types
 * 
 * @doc.type types
 * @doc.purpose Define lifecycle phases for canvas operations
 * @doc.layer product
 * @doc.pattern Type Definitions
 */

import {
    LIFECYCLE_PHASE,
    PHASE_LABELS,
    PHASE_DESCRIPTIONS,
    PHASE_COLORS,
    PHASE_ICONS,
} from '../styles/design-tokens';

/**
 * Lifecycle phases based on APP_ROUTE_FLOW_DOCUMENTATION_CANONICAL.md
 * 
 * INTENT: User expresses what they want to build
 * SHAPE: User designs components, flows, and architecture
 * VALIDATE: AI validates design, checks for gaps and risks
 * GENERATE: AI generates code, configs, and resources
 * RUN: System deploys and executes generated code
 * OBSERVE: User monitors app performance and behavior
 * IMPROVE: User iterates based on observations
 */
export enum LifecyclePhase {
    INTENT = 'INTENT',
    SHAPE = 'SHAPE',
    VALIDATE = 'VALIDATE',
    GENERATE = 'GENERATE',
    RUN = 'RUN',
    OBSERVE = 'OBSERVE',
    IMPROVE = 'IMPROVE',
}

/**
 * Human-readable labels for lifecycle phases
 * @deprecated Import PHASE_LABELS from design-tokens instead
 */
export const PHASE_LABELS_DEPRECATED: Record<LifecyclePhase, string> = PHASE_LABELS;

/**
 * Export centralized phase labels for convenience
 */
export { PHASE_LABELS, PHASE_DESCRIPTIONS, PHASE_COLORS, PHASE_ICONS };

/**
 * Metadata about a lifecycle phase transition
 */
export interface PhaseTransition {
    fromPhase: LifecyclePhase;
    toPhase: LifecyclePhase;
    timestamp: number;
    triggeredBy: 'user' | 'agent' | 'system';
    reason?: string;
}

/**
 * Operations allowed in each lifecycle phase
 */
export interface PhaseOperations {
    /** Can create/modify canvas elements */
    canEdit: boolean;
    /** Can run AI validation */
    canValidate: boolean;
    /** Can generate code */
    canGenerate: boolean;
    /** Can deploy to runtime */
    canDeploy: boolean;
    /** Can view observability data */
    canObserve: boolean;
    /** AI suggestions are active */
    aiActive: boolean;
}

/**
 * Get allowed operations for a lifecycle phase
 */
export function getOperationsForPhase(phase: LifecyclePhase): PhaseOperations {
    switch (phase) {
        case LifecyclePhase.INTENT:
            return {
                canEdit: true,
                canValidate: false,
                canGenerate: false,
                canDeploy: false,
                canObserve: false,
                aiActive: true, // AI helps understand intent
            };

        case LifecyclePhase.SHAPE:
            return {
                canEdit: true,
                canValidate: true,
                canGenerate: false,
                canDeploy: false,
                canObserve: false,
                aiActive: true, // AI suggests patterns and connections
            };

        case LifecyclePhase.VALIDATE:
            return {
                canEdit: false, // Read-only during validation
                canValidate: true,
                canGenerate: false,
                canDeploy: false,
                canObserve: false,
                aiActive: true, // AI performs validation
            };

        case LifecyclePhase.GENERATE:
            return {
                canEdit: false, // Read-only during generation
                canValidate: false,
                canGenerate: true,
                canDeploy: false,
                canObserve: false,
                aiActive: true, // AI generates code
            };

        case LifecyclePhase.RUN:
            return {
                canEdit: false, // Read-only during deployment
                canValidate: false,
                canGenerate: false,
                canDeploy: true,
                canObserve: true,
                aiActive: false,
            };

        case LifecyclePhase.OBSERVE:
            return {
                canEdit: false, // Read-only while observing
                canValidate: false,
                canGenerate: false,
                canDeploy: false,
                canObserve: true,
                aiActive: true, // AI analyzes observability data
            };

        case LifecyclePhase.IMPROVE:
            return {
                canEdit: true, // Can iterate on design
                canValidate: true,
                canGenerate: false,
                canDeploy: false,
                canObserve: true,
                aiActive: true, // AI suggests improvements
            };

        default:
            return {
                canEdit: false,
                canValidate: false,
                canGenerate: false,
                canDeploy: false,
                canObserve: false,
                aiActive: false,
            };
    }
}

/**
 * Get human-readable phase description
 */
export function getPhaseDescription(phase: LifecyclePhase): string {
    return PHASE_DESCRIPTIONS[phase] || 'Unknown phase';
}

/**
 * Get phase color for UI
 */
export function getPhaseColor(phase: LifecyclePhase): string {
    return PHASE_COLORS[phase]?.primary || '#9e9e9e';
}

/**
 * Validate phase transition
 */
export function canTransitionToPhase(
    fromPhase: LifecyclePhase,
    toPhase: LifecyclePhase,
): boolean {
    // Define valid transitions
    const validTransitions: Record<LifecyclePhase, LifecyclePhase[]> = {
        [LifecyclePhase.INTENT]: [LifecyclePhase.SHAPE],
        [LifecyclePhase.SHAPE]: [LifecyclePhase.INTENT, LifecyclePhase.VALIDATE],
        [LifecyclePhase.VALIDATE]: [LifecyclePhase.SHAPE, LifecyclePhase.GENERATE],
        [LifecyclePhase.GENERATE]: [LifecyclePhase.SHAPE, LifecyclePhase.RUN],
        [LifecyclePhase.RUN]: [LifecyclePhase.OBSERVE],
        [LifecyclePhase.OBSERVE]: [LifecyclePhase.IMPROVE, LifecyclePhase.SHAPE],
        [LifecyclePhase.IMPROVE]: [LifecyclePhase.SHAPE, LifecyclePhase.VALIDATE],
    };

    const allowedTransitions = validTransitions[fromPhase] || [];
    return allowedTransitions.includes(toPhase);
}

/**
 * Get lifecycle phase from route path
 */
export function getPhaseFromRoute(pathname: string): LifecyclePhase | null {
    // Extract phase from route like /app/p/{projectId}/canvas
    if (pathname.includes('/canvas')) return LifecyclePhase.SHAPE;
    if (pathname.includes('/validate')) return LifecyclePhase.VALIDATE;
    if (pathname.includes('/generate')) return LifecyclePhase.GENERATE;
    if (pathname.includes('/preview')) return LifecyclePhase.RUN;
    if (pathname.includes('/observe')) return LifecyclePhase.OBSERVE;
    if (pathname.includes('/improve')) return LifecyclePhase.IMPROVE;

    // Default to INTENT for root project page
    if (pathname.includes('/app/p/') && !pathname.includes('/settings')) {
        return LifecyclePhase.INTENT;
    }

    return null;
}

/**
 * Get route path for a lifecycle phase
 */
export function getRouteForPhase(projectId: string, phase: LifecyclePhase): string {
    const base = `/app/p/${projectId}`;

    switch (phase) {
        case LifecyclePhase.INTENT:
            return base;
        case LifecyclePhase.SHAPE:
            return `${base}/canvas`;
        case LifecyclePhase.VALIDATE:
            return `${base}/validate`;
        case LifecyclePhase.GENERATE:
            return `${base}/generate`;
        case LifecyclePhase.RUN:
            return `${base}/preview`;
        case LifecyclePhase.OBSERVE:
            return `${base}/observe`;
        case LifecyclePhase.IMPROVE:
            return `${base}/improve`;
        default:
            return base;
    }
}

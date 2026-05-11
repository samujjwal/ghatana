/**
 * Lifecycle Phase Types
 *
 * @doc.type types
 * @doc.purpose Define lifecycle phases for canvas operations
 * @doc.layer product
 * @doc.pattern Type Definitions
 */

/**
 * Canonical lifecycle phases for YAPPC.
 *
 * Legacy phase names are preserved as aliases so older callers keep compiling
 * while runtime values converge on the canonical 8-phase model.
 */
export const LifecyclePhase = {
    INTENT: 'INTENT',
    CONTEXT: 'CONTEXT',
    PLAN: 'PLAN',
    EXECUTE: 'EXECUTE',
    VERIFY: 'VERIFY',
    OBSERVE: 'OBSERVE',
    LEARN: 'LEARN',
    INSTITUTIONALIZE: 'INSTITUTIONALIZE',
    SHAPE: 'CONTEXT',
    VALIDATE: 'PLAN',
    GENERATE: 'EXECUTE',
    RUN: 'VERIFY',
    IMPROVE: 'LEARN',
} as const;

export type LifecyclePhase = (typeof LifecyclePhase)[keyof typeof LifecyclePhase];

export const LIFECYCLE_PHASE = [
    LifecyclePhase.INTENT,
    LifecyclePhase.CONTEXT,
    LifecyclePhase.PLAN,
    LifecyclePhase.EXECUTE,
    LifecyclePhase.VERIFY,
    LifecyclePhase.OBSERVE,
    LifecyclePhase.LEARN,
    LifecyclePhase.INSTITUTIONALIZE,
] as const;

export const PHASE_LABELS: Record<LifecyclePhase, string> = {
    [LifecyclePhase.INTENT]: 'Intent',
    [LifecyclePhase.CONTEXT]: 'Context',
    [LifecyclePhase.PLAN]: 'Plan',
    [LifecyclePhase.EXECUTE]: 'Execute',
    [LifecyclePhase.VERIFY]: 'Verify',
    [LifecyclePhase.OBSERVE]: 'Observe',
    [LifecyclePhase.LEARN]: 'Learn',
    [LifecyclePhase.INSTITUTIONALIZE]: 'Institutionalize',
};

export const PHASE_DESCRIPTIONS: Record<LifecyclePhase, string> = {
    [LifecyclePhase.INTENT]: 'Define what should be built and why it matters.',
    [LifecyclePhase.CONTEXT]: 'Capture requirements, architecture, and operating context.',
    [LifecyclePhase.PLAN]: 'Validate the approach and plan the work before execution.',
    [LifecyclePhase.EXECUTE]: 'Build and deliver the implementation plan.',
    [LifecyclePhase.VERIFY]: 'Verify release readiness with evidence and operational checks.',
    [LifecyclePhase.OBSERVE]: 'Observe real-world behavior after release.',
    [LifecyclePhase.LEARN]: 'Turn outcomes into concrete lessons and improvement inputs.',
    [LifecyclePhase.INSTITUTIONALIZE]: 'Bake validated practices back into the system of work.',
};

export const PHASE_COLORS: Record<LifecyclePhase, { primary: string }> = {
    [LifecyclePhase.INTENT]: { primary: '#3b82f6' },
    [LifecyclePhase.CONTEXT]: { primary: '#8b5cf6' },
    [LifecyclePhase.PLAN]: { primary: '#10b981' },
    [LifecyclePhase.EXECUTE]: { primary: '#f59e0b' },
    [LifecyclePhase.VERIFY]: { primary: '#ef4444' },
    [LifecyclePhase.OBSERVE]: { primary: '#6366f1' },
    [LifecyclePhase.LEARN]: { primary: '#ec4899' },
    [LifecyclePhase.INSTITUTIONALIZE]: { primary: '#14b8a6' },
};

export const PHASE_ICONS: Record<LifecyclePhase, string> = {
    [LifecyclePhase.INTENT]: 'lightbulb',
    [LifecyclePhase.CONTEXT]: 'layers',
    [LifecyclePhase.PLAN]: 'check-square',
    [LifecyclePhase.EXECUTE]: 'play-circle',
    [LifecyclePhase.VERIFY]: 'shield-check',
    [LifecyclePhase.OBSERVE]: 'eye',
    [LifecyclePhase.LEARN]: 'book-open',
    [LifecyclePhase.INSTITUTIONALIZE]: 'library',
};

/**
 * Human-readable labels for lifecycle phases
 * @deprecated Import PHASE_LABELS from design-tokens instead
 */
export const PHASE_LABELS_DEPRECATED: Record<LifecyclePhase, string> = PHASE_LABELS;

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

        case LifecyclePhase.CONTEXT:
            return {
                canEdit: true,
                canValidate: true,
                canGenerate: false,
                canDeploy: false,
                canObserve: false,
                aiActive: true, // AI suggests patterns and connections
            };

        case LifecyclePhase.PLAN:
            return {
                canEdit: true,
                canValidate: true,
                canGenerate: false,
                canDeploy: false,
                canObserve: false,
                aiActive: true,
            };

        case LifecyclePhase.EXECUTE:
            return {
                canEdit: true,
                canValidate: true,
                canGenerate: true,
                canDeploy: true,
                canObserve: false,
                aiActive: true,
            };

        case LifecyclePhase.VERIFY:
            return {
                canEdit: false,
                canValidate: true,
                canGenerate: true,
                canDeploy: true,
                canObserve: true,
                aiActive: true,
            };

        case LifecyclePhase.OBSERVE:
            return {
                canEdit: false,
                canValidate: false,
                canGenerate: false,
                canDeploy: false,
                canObserve: true,
                aiActive: true,
            };

        case LifecyclePhase.LEARN:
            return {
                canEdit: true,
                canValidate: true,
                canGenerate: false,
                canDeploy: false,
                canObserve: true,
                aiActive: true,
            };

        case LifecyclePhase.INSTITUTIONALIZE:
            return {
                canEdit: true,
                canValidate: true,
                canGenerate: false,
                canDeploy: false,
                canObserve: true,
                aiActive: true,
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
    const normalizedFromPhase = normalizeLifecyclePhase(fromPhase);
    const normalizedToPhase = normalizeLifecyclePhase(toPhase);

    const validTransitions: Record<LifecyclePhase, LifecyclePhase[]> = {
        [LifecyclePhase.INTENT]: [LifecyclePhase.CONTEXT],
        [LifecyclePhase.CONTEXT]: [LifecyclePhase.INTENT, LifecyclePhase.PLAN],
        [LifecyclePhase.PLAN]: [LifecyclePhase.CONTEXT, LifecyclePhase.EXECUTE],
        [LifecyclePhase.EXECUTE]: [LifecyclePhase.PLAN, LifecyclePhase.VERIFY],
        [LifecyclePhase.VERIFY]: [LifecyclePhase.EXECUTE, LifecyclePhase.OBSERVE],
        [LifecyclePhase.OBSERVE]: [LifecyclePhase.VERIFY, LifecyclePhase.LEARN],
        [LifecyclePhase.LEARN]: [LifecyclePhase.OBSERVE, LifecyclePhase.INSTITUTIONALIZE],
        [LifecyclePhase.INSTITUTIONALIZE]: [LifecyclePhase.LEARN],
    };

    const allowedTransitions = validTransitions[normalizedFromPhase] || [];
    return allowedTransitions.includes(normalizedToPhase);
}

function normalizeLifecyclePhase(phase: LifecyclePhase): LifecyclePhase {
    // Return canonical phase as-is - no mapping needed
    // Legacy phase names are handled by the backend compatibility adapter
    return phase;
}

/**
 * Get lifecycle phase from route path
 */
export function getPhaseFromRoute(pathname: string): LifecyclePhase | null {
    // Extract phase from route like /p/{projectId}/canvas
    if (pathname.includes('/canvas')) return LifecyclePhase.CONTEXT;
    if (pathname.includes('/validate')) return LifecyclePhase.PLAN;
    if (pathname.includes('/generate')) return LifecyclePhase.EXECUTE;
    if (pathname.includes('/preview')) return LifecyclePhase.VERIFY;
    if (pathname.includes('/deploy')) return LifecyclePhase.EXECUTE;
    if (pathname.includes('/observe')) return LifecyclePhase.OBSERVE;
    if (pathname.includes('/improve')) return LifecyclePhase.LEARN;
    if (pathname.includes('/lifecycle')) return LifecyclePhase.OBSERVE;

    // Default to INTENT for root project page
    if (pathname.includes('/p/') && !pathname.includes('/settings')) {
        return LifecyclePhase.INTENT;
    }

    return null;
}

/**
 * Get route path for a lifecycle phase
 */
export function getRouteForPhase(projectId: string, phase: LifecyclePhase): string {
    const base = `/p/${projectId}`;

    switch (phase) {
        case LifecyclePhase.INTENT:
            return base;
        case LifecyclePhase.CONTEXT:
            return `${base}/canvas`;
        case LifecyclePhase.PLAN:
            return `${base}/validate`;
        case LifecyclePhase.EXECUTE:
            return `${base}/deploy`;
        case LifecyclePhase.VERIFY:
            return `${base}/generate`;
        case LifecyclePhase.OBSERVE:
            return `${base}/observe`;
        case LifecyclePhase.LEARN:
            return `${base}/improve`;
        case LifecyclePhase.INSTITUTIONALIZE:
            return `${base}/lifecycle`;
        default:
            return base;
    }
}

/**
 * Lifecycle Phase Type System
 * 
 * Shared types for YAPPC's 7-phase lifecycle model.
 * Used across frontend, backend, and AI services.
 * 
 * @doc.type shared-types
 * @doc.purpose Lifecycle phase system definitions
 * @doc.layer shared
 * @doc.pattern Type System
 * 
 * NOTE: Phase labels, descriptions, and colors are centralized in design-tokens.ts
 * Import from there for consistent UI representation.
 */

/**
 * The seven phases of the YAPPC product lifecycle.
 * 
 * All products flow through these phases:
 * Intent → Shape → Validate → Generate → Run → Observe → Improve
 * 
 * Phases are non-linear - users can move backward freely.
 */
export enum LifecyclePhase {
    /**
     * User describes desired outcome in natural language.
     * No persistence yet. AI classifies intent.
     * Route: /app
     */
    INTENT = 'INTENT',

    /**
     * User structures the idea visually on canvas.
     * All changes versioned. AI suggests improvements.
     * Route: /app/p/:id/canvas
     */
    SHAPE = 'SHAPE',

    /**
     * System validates structure and simulates behavior.
     * Read-only phase. AI highlights risks.
     * Route: /app/p/:id/preview
     */
    VALIDATE = 'VALIDATE',

    /**
     * AI generates deployment configuration.
     * Snapshot created. User can override defaults.
     * Route: /app/p/:id/deploy
     */
    GENERATE = 'GENERATE',

    /**
     * System is deployed and running.
     * User monitors status and logs.
     * Route: /app/p/:id/deploy
     */
    RUN = 'RUN',

    /**
     * Passive learning from user behavior.
     * No UI. Feeds into IMPROVE phase.
     * Route: implicit
     */
    OBSERVE = 'OBSERVE',

    /**
     * AI proposes enhancements based on observations.
     * User accepts/rejects suggestions.
     * Route: /app/p/:id/canvas
     */
    IMPROVE = 'IMPROVE',
}

/**
 * Route to lifecycle phase mapping.
 * Used for phase-aware routing and navigation.
 */
export const ROUTE_TO_PHASE: Record<string, LifecyclePhase> = {
    '/app': LifecyclePhase.INTENT,
    '/app/p/:id/canvas': LifecyclePhase.SHAPE,
    '/app/p/:id/preview': LifecyclePhase.VALIDATE,
    '/app/p/:id/deploy/configure': LifecyclePhase.GENERATE,
    '/app/p/:id/deploy': LifecyclePhase.RUN,
};

/**
 * Phase to route mapping.
 * Used for programmatic navigation based on phase.
 */
export const PHASE_TO_ROUTE: Record<LifecyclePhase, string> = {
    [LifecyclePhase.INTENT]: '/app',
    [LifecyclePhase.SHAPE]: '/app/p/:id/canvas',
    [LifecyclePhase.VALIDATE]: '/app/p/:id/preview',
    [LifecyclePhase.GENERATE]: '/app/p/:id/deploy',
    [LifecyclePhase.RUN]: '/app/p/:id/deploy',
    [LifecyclePhase.OBSERVE]: '/app/p/:id/canvas', // implicit, falls back to canvas
    [LifecyclePhase.IMPROVE]: '/app/p/:id/canvas',
};

/**
 * @deprecated User-facing labels moved to design-tokens.ts for consistency
 * Import PHASE_LABELS from '../web/src/styles/design-tokens' instead
 * 
 * These legacy labels are inconsistent with the standardized design system:
 * - "Describe" should be "Ideate"
 * - "Build" should be "Design"
 * - "Configure" should be "Generate"
 * - "Learn" should be "Monitor"
 * - "Improve" should be "Enhance"
 */
export const PHASE_LABELS_LEGACY: Record<LifecyclePhase, string> = {
    [LifecyclePhase.INTENT]: 'Describe',
    [LifecyclePhase.SHAPE]: 'Build',
    [LifecyclePhase.VALIDATE]: 'Validate',
    [LifecyclePhase.GENERATE]: 'Configure',
    [LifecyclePhase.RUN]: 'Deploy',
    [LifecyclePhase.OBSERVE]: 'Learn',
    [LifecyclePhase.IMPROVE]: 'Improve',
};

/**
 * @deprecated Phase descriptions moved to design-tokens.ts for consistency
 * Import PHASE_DESCRIPTIONS from '../web/src/styles/design-tokens' instead
 */
export const PHASE_DESCRIPTIONS_LEGACY: Record<LifecyclePhase, string> = {
    [LifecyclePhase.INTENT]: 'Describe what you want to build',
    [LifecyclePhase.SHAPE]: 'Structure your idea visually',
    [LifecyclePhase.VALIDATE]: 'Review and validate behavior',
    [LifecyclePhase.GENERATE]: 'Generate deployment configuration',
    [LifecyclePhase.RUN]: 'Deploy and monitor your product',
    [LifecyclePhase.OBSERVE]: 'System learns from behavior',
    [LifecyclePhase.IMPROVE]: 'Enhance based on insights',
};

/**
 * Allowed phase transitions.
 * Used for validation and UI state management.
 */
export const PHASE_TRANSITIONS: Record<LifecyclePhase, LifecyclePhase[]> = {
    [LifecyclePhase.INTENT]: [LifecyclePhase.SHAPE],
    [LifecyclePhase.SHAPE]: [
        LifecyclePhase.INTENT,
        LifecyclePhase.VALIDATE,
        LifecyclePhase.GENERATE,
    ],
    [LifecyclePhase.VALIDATE]: [
        LifecyclePhase.SHAPE,
        LifecyclePhase.GENERATE,
    ],
    [LifecyclePhase.GENERATE]: [
        LifecyclePhase.SHAPE,
        LifecyclePhase.VALIDATE,
        LifecyclePhase.RUN,
    ],
    [LifecyclePhase.RUN]: [
        LifecyclePhase.SHAPE,
        LifecyclePhase.OBSERVE,
        LifecyclePhase.IMPROVE,
    ],
    [LifecyclePhase.OBSERVE]: [LifecyclePhase.IMPROVE],
    [LifecyclePhase.IMPROVE]: [
        LifecyclePhase.SHAPE,
        LifecyclePhase.VALIDATE,
    ],
};

/**
 * Check if a phase transition is valid.
 */
export function canTransitionTo(
    from: LifecyclePhase,
    to: LifecyclePhase
): boolean {
    return PHASE_TRANSITIONS[from]?.includes(to) ?? false;
}

/**
 * Get route for a lifecycle phase with projectId substitution.
 */
export function getRouteForPhase(
    phase: LifecyclePhase,
    projectId?: string
): string {
    const route = PHASE_TO_ROUTE[phase];
    return projectId ? route.replace(':id', projectId) : route;
}

/**
 * Extract lifecycle phase from current route path.
 */
export function getPhaseFromRoute(pathname: string): LifecyclePhase | null {
    if (pathname === '/app' || pathname === '/app/') {
        return LifecyclePhase.INTENT;
    }

    if (pathname.includes('/canvas')) {
        return LifecyclePhase.SHAPE;
    }

    if (pathname.includes('/preview')) {
        return LifecyclePhase.VALIDATE;
    }

    if (pathname.includes('/deploy')) {
        // Could be GENERATE or RUN depending on state
        // Default to RUN for now
        return LifecyclePhase.RUN;
    }

    return null;
}

/**
 * Default lifecycle phase for new projects.
 */
export const DEFAULT_LIFECYCLE_PHASE = LifecyclePhase.SHAPE;

/**
 * Type guard for LifecyclePhase.
 */
export function isLifecyclePhase(value: unknown): value is LifecyclePhase {
    return (
        typeof value === 'string' &&
        Object.values(LifecyclePhase).includes(value as LifecyclePhase)
    );
}

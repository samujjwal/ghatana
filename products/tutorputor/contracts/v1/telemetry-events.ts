/**
 * Telemetry Events (xAPI Profile)
 *
 * Defines the standard telemetry events emitted by the platform.
 * Aligned with xAPI (Experience API) vocabulary.
 *
 * @doc.type module
 * @doc.purpose xAPI telemetry event definitions
 * @doc.layer contracts
 * @doc.pattern ValueObject
 */

// ============================================================================
// xAPI Verb Mappings
// ============================================================================

/**
 * Standard xAPI verbs used by TutorPutor
 */
export const XAPI_VERBS = {
    // Simulation Events
    'sim.start': 'http://adlnet.gov/expapi/verbs/initialized',
    'sim.control.change': 'http://adlnet.gov/expapi/verbs/interacted',
    'sim.goal.achieved': 'http://adlnet.gov/expapi/verbs/completed',
    'sim.goal.failed': 'http://adlnet.gov/expapi/verbs/failed',
    'sim.pause': 'http://adlnet.gov/expapi/verbs/suspended',
    'sim.resume': 'http://adlnet.gov/expapi/verbs/resumed',

    // Assessment Events
    'assess.answer.submit': 'http://adlnet.gov/expapi/verbs/answered',
    'assess.confidence.submit': 'http://adlnet.gov/expapi/verbs/rated',
    'assess.hint.request': 'http://adlnet.gov/expapi/verbs/asked',

    // Content Events
    'content.video.start': 'http://adlnet.gov/expapi/verbs/initialized',
    'content.video.complete': 'http://adlnet.gov/expapi/verbs/completed',
    'content.video.pause': 'http://adlnet.gov/expapi/verbs/suspended',
    'content.article.open': 'http://adlnet.gov/expapi/verbs/launched',
    'content.article.complete': 'http://adlnet.gov/expapi/verbs/completed',

    // Credential Events
    'credential.badge.issued': 'http://adlnet.gov/expapi/verbs/earned',
    'credential.skill.mastered': 'http://adlnet.gov/expapi/verbs/mastered',
} as const;

export type TelemetryEventType = keyof typeof XAPI_VERBS;

// ============================================================================
// Event Payloads
// ============================================================================

/**
 * Base event structure (all events extend this)
 */
export interface BaseTelemetryEvent {
    /** Event type identifier */
    type: TelemetryEventType;
    /** ISO 8601 timestamp */
    timestamp: string;
    /** Actor (learner) */
    actor: {
        id: string;
        name?: string;
        email?: string;
    };
    /** Context */
    context: {
        tenantId: string;
        learningUnitId?: string;
        claimId?: string;
        sessionId: string;
        platform: 'web' | 'mobile' | 'vr';
    };
}

// ============================================================================
// Simulation Events
// ============================================================================

export interface SimStartEvent extends BaseTelemetryEvent {
    type: 'sim.start';
    object: {
        id: string; // Simulation ID
        name: string;
        blueprintId: string;
    };
}

export interface SimControlChangeEvent extends BaseTelemetryEvent {
    type: 'sim.control.change';
    object: {
        simulationId: string;
        parameter: string;
        previousValue: number | string | boolean;
        newValue: number | string | boolean;
    };
    result: {
        attemptNumber: number;
        elapsedTimeMs: number;
    };
}

export interface SimGoalAchievedEvent extends BaseTelemetryEvent {
    type: 'sim.goal.achieved';
    object: {
        simulationId: string;
        goalId: string;
    };
    result: {
        success: true;
        score: number;
        attempts: number;
        timeOnTaskMs: number;
        finalParameterValues: Record<string, number | string | boolean>;
    };
}

export interface SimGoalFailedEvent extends BaseTelemetryEvent {
    type: 'sim.goal.failed';
    object: {
        simulationId: string;
        goalId: string;
    };
    result: {
        success: false;
        attempts: number;
        timeOnTaskMs: number;
        reason: 'max_attempts' | 'timeout' | 'user_abort';
    };
}

// ============================================================================
// Assessment Events
// ============================================================================

export interface AssessAnswerSubmitEvent extends BaseTelemetryEvent {
    type: 'assess.answer.submit';
    object: {
        taskId: string;
        taskType: 'prediction' | 'explanation' | 'construction';
        prompt: string;
    };
    result: {
        response: string | string[];
        correct?: boolean;
        score?: number;
        maxScore?: number;
        duration: number;
    };
}

export interface AssessConfidenceSubmitEvent extends BaseTelemetryEvent {
    type: 'assess.confidence.submit';
    object: {
        taskId: string;
        linkedAnswerId: string;
    };
    result: {
        confidence: 'low' | 'medium' | 'high';
    };
}

// ============================================================================
// Content Events
// ============================================================================

export interface ContentVideoCompleteEvent extends BaseTelemetryEvent {
    type: 'content.video.complete';
    object: {
        videoId: string;
        title: string;
        durationSeconds: number;
    };
    result: {
        watchedPercentage: number;
        pauseCount: number;
        replayCount: number;
    };
}

// ============================================================================
// Credential Events
// ============================================================================

export interface CredentialBadgeIssuedEvent extends BaseTelemetryEvent {
    type: 'credential.badge.issued';
    object: {
        badgeId: string;
        badgeName: string;
        learningUnitId: string;
    };
    result: {
        issuedAt: string;
        masteryScore: number;
        claimsAchieved: string[];
    };
}

// ============================================================================
// Union Type for All Events
// ============================================================================

export type TelemetryEvent =
    | SimStartEvent
    | SimControlChangeEvent
    | SimGoalAchievedEvent
    | SimGoalFailedEvent
    | AssessAnswerSubmitEvent
    | AssessConfidenceSubmitEvent
    | ContentVideoCompleteEvent
    | CredentialBadgeIssuedEvent;

// ============================================================================
// Process Features (Derived Metrics)
// ============================================================================

/**
 * Process features are derived from sequences of events.
 * They are computed by the analytics pipeline, not emitted directly.
 */
export interface ProcessFeatures {
    /** Total interaction attempts */
    totalAttempts: number;
    /** Number of times user overshot the goal */
    overshootEvents: number;
    /** Total time spent on the task in seconds */
    timeOnTaskSeconds: number;
    /** Number of pauses during interaction */
    pauseCount: number;
    /** Sequence of parameter changes (for pattern analysis) */
    parameterChangeSequence: string[];
    /** Average time between actions in ms */
    avgTimeBetweenActionsMs: number;
    /** Whether user showed random exploration pattern */
    randomExplorationDetected: boolean;
}

// ============================================================================
// Event Builder Helpers
// ============================================================================

/**
 * Creates a base event structure with common fields populated.
 */
export function createBaseEvent(
    type: TelemetryEventType,
    actorId: string,
    tenantId: string,
    sessionId: string
): BaseTelemetryEvent {
    return {
        type,
        timestamp: new Date().toISOString(),
        actor: { id: actorId },
        context: {
            tenantId,
            sessionId,
            platform: 'web',
        },
    };
}

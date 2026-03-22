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
/**
 * Standard xAPI verbs used by TutorPutor
 */
export declare const XAPI_VERBS: {
    readonly 'sim.start': "http://adlnet.gov/expapi/verbs/initialized";
    readonly 'sim.control.change': "http://adlnet.gov/expapi/verbs/interacted";
    readonly 'sim.goal.achieved': "http://adlnet.gov/expapi/verbs/completed";
    readonly 'sim.goal.failed': "http://adlnet.gov/expapi/verbs/failed";
    readonly 'sim.pause': "http://adlnet.gov/expapi/verbs/suspended";
    readonly 'sim.resume': "http://adlnet.gov/expapi/verbs/resumed";
    readonly 'assess.answer.submit': "http://adlnet.gov/expapi/verbs/answered";
    readonly 'assess.confidence.submit': "http://adlnet.gov/expapi/verbs/rated";
    readonly 'assess.hint.request': "http://adlnet.gov/expapi/verbs/asked";
    readonly 'content.video.start': "http://adlnet.gov/expapi/verbs/initialized";
    readonly 'content.video.complete': "http://adlnet.gov/expapi/verbs/completed";
    readonly 'content.video.pause': "http://adlnet.gov/expapi/verbs/suspended";
    readonly 'content.article.open': "http://adlnet.gov/expapi/verbs/launched";
    readonly 'content.article.complete': "http://adlnet.gov/expapi/verbs/completed";
    readonly 'credential.badge.issued': "http://adlnet.gov/expapi/verbs/earned";
    readonly 'credential.skill.mastered': "http://adlnet.gov/expapi/verbs/mastered";
};
export type TelemetryEventType = keyof typeof XAPI_VERBS;
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
export interface SimStartEvent extends BaseTelemetryEvent {
    type: 'sim.start';
    object: {
        id: string;
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
export type TelemetryEvent = SimStartEvent | SimControlChangeEvent | SimGoalAchievedEvent | SimGoalFailedEvent | AssessAnswerSubmitEvent | AssessConfidenceSubmitEvent | ContentVideoCompleteEvent | CredentialBadgeIssuedEvent;
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
/**
 * Creates a base event structure with common fields populated.
 */
export declare function createBaseEvent(type: TelemetryEventType, actorId: string, tenantId: string, sessionId: string): BaseTelemetryEvent;
//# sourceMappingURL=telemetry-events.d.ts.map
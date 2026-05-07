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
    'sim.snapshot': 'https://w3id.org/xapi/tutorputor/verbs/simulation-snapshot',
    'sim.capture': 'https://w3id.org/xapi/tutorputor/verbs/simulation-capture',

    // Assessment Events
    'assess.answer': 'http://adlnet.gov/expapi/verbs/answered',
    'assess.answer.submit': 'http://adlnet.gov/expapi/verbs/answered',
    'assess.confidence.submit': 'http://adlnet.gov/expapi/verbs/rated',
    'assess.hint.request': 'http://adlnet.gov/expapi/verbs/asked',
    'assist.hint': 'http://adlnet.gov/expapi/verbs/asked',

    // Content Events
    'content.video.start': 'http://adlnet.gov/expapi/verbs/initialized',
    'content.video.complete': 'http://adlnet.gov/expapi/verbs/completed',
    'content.video.pause': 'http://adlnet.gov/expapi/verbs/suspended',
    'content.article.open': 'http://adlnet.gov/expapi/verbs/launched',
    'content.article.complete': 'http://adlnet.gov/expapi/verbs/completed',

    // Credential Events
    'credential.badge.issued': 'http://adlnet.gov/expapi/verbs/earned',
    'credential.skill.mastered': 'http://adlnet.gov/expapi/verbs/mastered',

    // AI Interaction Events
    'ai.tutor.response': 'https://w3id.org/xapi/tutorputor/verbs/ai-tutor-response',
    'ai.generation.created': 'https://w3id.org/xapi/tutorputor/verbs/ai-generation-created',
    'ai.governance.blocked': 'https://w3id.org/xapi/tutorputor/verbs/ai-governance-blocked',
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
        offlineSync?: OfflineSyncContext;
    };
}

export interface OfflineSyncContext {
    clientMutationId: string;
    syncStatus: 'online' | 'queued' | 'replayed' | 'conflict';
    baseServerVersion?: number;
    localVersion: number;
    conflictPolicy: 'max-progress' | 'idempotent-hash' | 'submitted-attempt-lock' | 'most-restrictive-consent' | 'event-id-dedupe';
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

export interface SimSnapshotEvent extends BaseTelemetryEvent {
    type: 'sim.snapshot';
    object: {
        simulationId: string;
        runId: string;
        seed: string;
    };
    result: {
        state: Record<string, number | string | boolean>;
        elapsedTimeMs: number;
        deterministicHash: string;
    };
}

export interface SimCaptureEvent extends BaseTelemetryEvent {
    type: 'sim.capture';
    object: {
        simulationId: string;
        runId: string;
        captureId: string;
        claimId: string;
        evidenceId: string;
        taskId: string;
    };
    result: {
        processFeatures: Record<string, number | string | boolean>;
        outputState: Record<string, number | string | boolean>;
        validEvidence: boolean;
    };
}

// ============================================================================
// Assessment Events
// ============================================================================

export interface AssessAnswerEvent extends BaseTelemetryEvent {
    type: 'assess.answer';
    object: {
        assessmentId: string;
        attemptId: string;
        itemId: string;
        taskId: string;
        claimId: string;
        evidenceId: string;
    };
    result: {
        response: string | string[] | number | boolean;
        correct?: boolean;
        score?: number;
        maxScore?: number;
        confidence: 'low' | 'medium' | 'high';
        durationMs: number;
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

export interface AssistHintEvent extends BaseTelemetryEvent {
    type: 'assist.hint';
    object: {
        moduleId: string;
        claimId: string;
        taskId?: string;
        hintId: string;
    };
    result: {
        source: 'ai_tutor' | 'static_hint' | 'teacher';
        level: 'nudge' | 'conceptual' | 'worked_step';
        accepted: boolean;
    };
}

// ============================================================================
// AI Interaction Events
// ============================================================================

export interface AIInteractionTelemetryMetadata {
    consentState: 'granted' | 'missing' | 'revoked' | 'not_required';
    learnerContextScope: 'none' | 'module' | 'claim' | 'simulation' | 'assessment' | 'course';
    promptVersion: string;
    modelVersion: string;
    retrievedContentIds: string[];
    safetyFilterResult: 'passed' | 'blocked' | 'redacted' | 'human_review_required';
    latencyMs: number;
    tokenUsage?: {
        inputTokens: number;
        outputTokens: number;
        totalTokens: number;
    };
    costUsd?: number;
    confidence?: number;
    humanReviewRequired: boolean;
    containsDirectPii: false;
}

export interface AITutorResponseEvent extends BaseTelemetryEvent {
    type: 'ai.tutor.response';
    object: {
        moduleId?: string;
        claimIds: string[];
        responseId: string;
    };
    result: AIInteractionTelemetryMetadata & {
        blocked: false;
    };
}

export interface AIGenerationCreatedEvent extends BaseTelemetryEvent {
    type: 'ai.generation.created';
    object: {
        artifactId: string;
        artifactType: 'lesson' | 'simulation' | 'assessment' | 'rubric' | 'explanation';
    };
    result: AIInteractionTelemetryMetadata & {
        validationStatus: 'pending' | 'passed' | 'failed';
        smeReviewStatus: 'pending' | 'approved' | 'rejected';
    };
}

export interface AIGovernanceBlockedEvent extends BaseTelemetryEvent {
    type: 'ai.governance.blocked';
    object: {
        useCase: 'tutor' | 'recommender' | 'grading' | 'content_generation' | 'analytics';
    };
    result: AIInteractionTelemetryMetadata & {
        blocked: true;
        reason: 'consent_missing' | 'consent_revoked' | 'safety_filter' | 'human_review_required';
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
    | SimSnapshotEvent
    | SimCaptureEvent
    | AssessAnswerEvent
    | AssessAnswerSubmitEvent
    | AssessConfidenceSubmitEvent
    | AssistHintEvent
    | ContentVideoCompleteEvent
    | CredentialBadgeIssuedEvent
    | AITutorResponseEvent
    | AIGenerationCreatedEvent
    | AIGovernanceBlockedEvent;

export type SimulationTelemetryEvent =
    | SimStartEvent
    | SimControlChangeEvent
    | SimGoalAchievedEvent
    | SimGoalFailedEvent
    | SimSnapshotEvent
    | SimCaptureEvent;

export type AssessmentTelemetryEvent =
    | AssessAnswerEvent
    | AssessAnswerSubmitEvent
    | AssessConfidenceSubmitEvent;

export type AssistanceTelemetryEvent = AssistHintEvent;

export type AITelemetryEvent =
    | AITutorResponseEvent
    | AIGenerationCreatedEvent
    | AIGovernanceBlockedEvent;

export type LearningTelemetryEvent =
    | SimulationTelemetryEvent
    | AssessmentTelemetryEvent
    | AssistanceTelemetryEvent
    | AITelemetryEvent
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

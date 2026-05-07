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
};
// ============================================================================
// Event Builder Helpers
// ============================================================================
/**
 * Creates a base event structure with common fields populated.
 */
export function createBaseEvent(type, actorId, tenantId, sessionId) {
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
//# sourceMappingURL=telemetry-events.js.map

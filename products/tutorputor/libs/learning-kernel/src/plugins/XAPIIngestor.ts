/**
 * xAPI Ingestor Plugin.
 *
 * Ingests xAPI-formatted learning telemetry events from various sources.
 * Normalizes events to the internal EvidenceEvent format and routes them
 * to the processing pipeline.
 *
 * Supports:
 * - Standard xAPI 1.0.3 statements
 * - CMI5 statements
 * - Custom TutorPutor xAPI profile events
 *
 * @doc.type class
 * @doc.purpose Ingest and normalize xAPI telemetry
 * @doc.layer plugin
 * @doc.pattern Ingestor
 */

import type {
    Ingestor,
    PluginMetadata,
    EvidenceEvent,
    RawEvent,
} from '@ghatana/tutorputor-contracts/v1/plugin-interfaces';

/**
 * Standard xAPI statement structure.
 */
export interface XAPIStatement {
    /** Unique identifier */
    readonly id?: string;
    /** Actor who performed the action */
    readonly actor: XAPIActor;
    /** The verb (action) */
    readonly verb: XAPIVerb;
    /** The object of the action */
    readonly object: XAPIObject;
    /** Optional result */
    readonly result?: XAPIResult;
    /** Optional context */
    readonly context?: XAPIContext;
    /** Timestamp */
    readonly timestamp?: string;
}

/**
 * xAPI Actor (learner/user).
 */
export interface XAPIActor {
    readonly objectType?: 'Agent' | 'Group';
    readonly name?: string;
    readonly mbox?: string;
    readonly mbox_sha1sum?: string;
    readonly openid?: string;
    readonly account?: {
        readonly homePage: string;
        readonly name: string;
    };
}

/**
 * xAPI Verb.
 */
export interface XAPIVerb {
    readonly id: string;
    readonly display?: Record<string, string>;
}

/**
 * xAPI Object (activity, agent, or statement reference).
 */
export interface XAPIObject {
    readonly objectType?: 'Activity' | 'Agent' | 'Group' | 'SubStatement' | 'StatementRef';
    readonly id: string;
    readonly definition?: {
        readonly type?: string;
        readonly name?: Record<string, string>;
        readonly description?: Record<string, string>;
        readonly extensions?: Record<string, unknown>;
    };
}

/**
 * xAPI Result.
 */
export interface XAPIResult {
    readonly score?: {
        readonly scaled?: number;
        readonly raw?: number;
        readonly min?: number;
        readonly max?: number;
    };
    readonly success?: boolean;
    readonly completion?: boolean;
    readonly response?: string;
    readonly duration?: string;
    readonly extensions?: Record<string, unknown>;
}

/**
 * xAPI Context.
 */
export interface XAPIContext {
    readonly registration?: string;
    readonly contextActivities?: {
        readonly parent?: readonly XAPIObject[];
        readonly grouping?: readonly XAPIObject[];
    };
    readonly extensions?: Record<string, unknown>;
}

/**
 * Configuration for the xAPI ingestor.
 */
export interface XAPIIngestorConfig {
    /** Whether to validate statements strictly */
    readonly strictValidation?: boolean;
}

/**
 * xAPI Ingestor Plugin.
 *
 * Features:
 * - Validates xAPI statements
 * - Normalizes to internal EvidenceEvent format
 * - Maps verbs to TutorPutor xAPI profile
 * - Extracts structured data from extensions
 *
 * @example
 * ```typescript
 * const ingestor = new XAPIIngestor();
 * registry.registerIngestor(ingestor);
 *
 * // Ingest a raw xAPI event
 * const evidence = await ingestor.ingest({
 *   source: 'xapi-lrs',
 *   format: 'xapi',
 *   payload: xapiStatement,
 *   receivedAt: new Date(),
 * });
 * ```
 */
export class XAPIIngestor implements Ingestor {
    readonly metadata: PluginMetadata = {
        id: 'xapi-ingestor',
        name: 'xAPI Statement Ingestor',
        version: '1.0.0',
        type: 'ingestor',
        priority: 100,
        description: 'Ingests xAPI statements and converts them to internal evidence events',
        author: 'TutorPutor Core Team',
        tags: ['xapi', 'telemetry', 'ingest', 'lrs'],
        enabled: true,
    };

    private readonly config: Required<XAPIIngestorConfig>;

    constructor(config?: XAPIIngestorConfig) {
        this.config = {
            strictValidation: config?.strictValidation ?? false,
        };
    }

    /**
     * Check if this ingestor can handle the given event format.
     */
    supports(event: RawEvent): boolean {
        return event.format === 'xapi';
    }

    /**
     * Transform raw event into normalized EvidenceEvent.
     */
    async ingest(event: RawEvent): Promise<EvidenceEvent | null> {
        const statement = event.payload as XAPIStatement;

        // Validate statement
        const validationError = this.validateStatement(statement);
        if (validationError) {
            console.warn(`xAPI validation failed: ${validationError}`);
            return null;
        }

        // Convert to EvidenceEvent
        return this.convertToEvent(statement, event.receivedAt);
    }

    /**
     * Validate an xAPI statement.
     */
    private validateStatement(statement: XAPIStatement): string | null {
        // Required fields
        if (!statement.actor) {
            return 'Missing required field: actor';
        }
        if (!statement.verb?.id) {
            return 'Missing required field: verb.id';
        }
        if (!statement.object?.id) {
            return 'Missing required field: object.id';
        }

        // Actor validation
        if (!this.hasIdentifier(statement.actor)) {
            return 'Actor must have at least one identifier';
        }

        // Strict validation
        if (this.config.strictValidation) {
            if (!this.isValidIRI(statement.verb.id)) {
                return `Invalid verb IRI: ${statement.verb.id}`;
            }
        }

        return null;
    }

    /**
     * Check if actor has an identifier.
     */
    private hasIdentifier(actor: XAPIActor): boolean {
        return !!(actor.mbox || actor.mbox_sha1sum || actor.openid || actor.account);
    }

    /**
     * Basic IRI validation.
     */
    private isValidIRI(iri: string): boolean {
        try {
            new URL(iri);
            return true;
        } catch {
            return false;
        }
    }

    /**
     * Convert xAPI statement to internal EvidenceEvent.
     */
    private convertToEvent(statement: XAPIStatement, receivedAt: Date): EvidenceEvent {
        // Extract actor ID
        const learnerId = this.extractActorId(statement.actor);

        // Extract type from verb
        const type = this.extractType(statement.verb);

        // Extract IDs from object and context
        const learningUnitId = statement.object.id;
        const claimId = statement.context?.contextActivities?.parent?.[0]?.id ?? 'unknown';

        // Build payload from result and extensions
        const payload: Record<string, unknown> = {};
        if (statement.result) {
            if (statement.result.success !== undefined) {
                payload.correct = statement.result.success;
            }
            if (statement.result.score?.scaled !== undefined) {
                payload.score = statement.result.score.scaled;
            }
            if (statement.result.response !== undefined) {
                payload.response = statement.result.response;
            }
            if (statement.result.extensions) {
                // Extract confidence if present
                const confidenceKey = 'http://tutorputor.com/xapi/extensions/confidence';
                if (confidenceKey in statement.result.extensions) {
                    payload.confidence = statement.result.extensions[confidenceKey];
                }
                payload.extensions = statement.result.extensions;
            }
        }

        return {
            id: statement.id ?? crypto.randomUUID(),
            learnerId,
            learningUnitId,
            claimId,
            evidenceId: statement.id ?? crypto.randomUUID(),
            type,
            timestamp: statement.timestamp ? new Date(statement.timestamp) : receivedAt,
            payload,
        };
    }

    /**
     * Extract actor ID from xAPI actor.
     */
    private extractActorId(actor: XAPIActor): string {
        if (actor.account) {
            return `${actor.account.homePage}::${actor.account.name}`;
        }
        if (actor.mbox) {
            return actor.mbox.replace('mailto:', '');
        }
        if (actor.openid) {
            return actor.openid;
        }
        if (actor.mbox_sha1sum) {
            return `sha1:${actor.mbox_sha1sum}`;
        }
        return 'unknown';
    }

    /**
     * Extract event type from verb.
     */
    private extractType(verb: XAPIVerb): string {
        // Extract verb name from IRI
        const match = verb.id.match(/\/([^\/]+)$/);
        if (match?.[1]) {
            return match[1];
        }
        return 'unknown';
    }
}

/**
 * Factory function to create xAPI ingestor.
 */
export function createXAPIIngestor(config?: XAPIIngestorConfig): XAPIIngestor {
    return new XAPIIngestor(config);
}

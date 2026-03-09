/**
 * BKT (Bayesian Knowledge Tracing) Evidence Processor Plugin.
 *
 * Implements the classic Bayesian Knowledge Tracing model for mastery estimation.
 * Tracks P(L) — the probability a learner has learned a skill — based on
 * observed correct/incorrect responses using four parameters:
 *   - P(L0): prior probability of knowing the skill
 *   - P(T):  probability of learning the skill on each opportunity
 *   - P(G):  probability of guessing correctly without knowing
 *   - P(S):  probability of slipping (incorrect despite knowing)
 *
 * @doc.type class
 * @doc.purpose Process evidence through Bayesian Knowledge Tracing model
 * @doc.layer plugin
 * @doc.pattern EvidenceProcessor
 *
 * @see https://en.wikipedia.org/wiki/Bayesian_knowledge_tracing
 */

import type {
    EvidenceProcessor,
    PluginMetadata,
    EvidenceEvent,
    ProcessingResult,
    ProcessingContext,
} from '@ghatana/tutorputor-contracts/v1/plugin-interfaces';

// ============================================================================
// Types
// ============================================================================

/**
 * BKT model parameters for a single skill/claim.
 */
export interface BKTParams {
    /** P(L0) — prior probability of knowing the skill (default 0.3) */
    readonly pInit: number;
    /** P(T) — probability of learning on each opportunity (default 0.1) */
    readonly pTransit: number;
    /** P(G) — probability of guessing correctly (default 0.2) */
    readonly pGuess: number;
    /** P(S) — probability of slipping despite knowing (default 0.1) */
    readonly pSlip: number;
}

/**
 * BKT configuration for the processor.
 */
export interface BKTConfig {
    /** Default parameters when skill-specific ones aren't available */
    readonly defaultParams: BKTParams;
    /** Mastery threshold — P(L) above this is considered mastered */
    readonly masteryThreshold: number;
    /** Per-skill parameter overrides keyed by claimId */
    readonly skillParams?: Record<string, Partial<BKTParams>>;
}

/**
 * BKT computation result for a single observation.
 */
export interface BKTResult {
    /** Updated P(L) — probability of mastery */
    readonly pLearned: number;
    /** Previous P(L) before this observation */
    readonly previousPLearned: number;
    /** Whether the skill is now considered mastered */
    readonly mastered: boolean;
    /** Observation correctness */
    readonly correct: boolean;
    /** Skill/claim ID */
    readonly claimId: string;
    /** Number of observations processed for this skill */
    readonly observationCount: number;
}

const DEFAULT_BKT_PARAMS: BKTParams = {
    pInit: 0.3,
    pTransit: 0.1,
    pGuess: 0.2,
    pSlip: 0.1,
};

const DEFAULT_BKT_CONFIG: BKTConfig = {
    defaultParams: DEFAULT_BKT_PARAMS,
    masteryThreshold: 0.95,
};

// ============================================================================
// BKT Processor Plugin
// ============================================================================

export class BKTProcessor implements EvidenceProcessor {
    readonly metadata: PluginMetadata = {
        id: 'bkt-processor',
        name: 'Bayesian Knowledge Tracing Processor',
        version: '1.0.0',
        type: 'evidence_processor',
        priority: 80,
        description: 'Estimates skill mastery using Bayesian Knowledge Tracing (BKT) model',
        author: 'TutorPutor Core Team',
        tags: ['mastery', 'bayesian', 'knowledge-tracing', 'adaptive'],
        enabled: true,
    };

    private readonly config: BKTConfig;
    /** Current P(L) per skill, keyed by `learnerId::claimId` */
    private state: Map<string, { pLearned: number; observations: number }> = new Map();

    constructor(config?: Partial<BKTConfig>) {
        this.config = {
            ...DEFAULT_BKT_CONFIG,
            ...config,
            defaultParams: { ...DEFAULT_BKT_PARAMS, ...config?.defaultParams },
        };
    }

    async initialize(): Promise<void> {
        this.state.clear();
    }

    async shutdown(): Promise<void> {
        this.state.clear();
    }

    supports(evidence: EvidenceEvent): boolean {
        const relevantTypes = [
            'answer_submission',
            'assessment_response',
            'sim_goal_achieved',
            'sim_goal_failed',
        ];
        return relevantTypes.includes(evidence.type);
    }

    async process(
        context: ProcessingContext,
        evidence: EvidenceEvent,
    ): Promise<ProcessingResult> {
        const startTime = Date.now();
        const pluginId = this.metadata.id;

        try {
            const { payload } = evidence;
            if (!payload) {
                return { pluginId, status: 'skipped', durationMs: Date.now() - startTime };
            }

            const learnerId = (evidence as any).actorId ?? (context as any).learnerId;
            const claimId = evidence.claimId ?? (payload as any).claimId;
            if (!learnerId || !claimId) {
                return { pluginId, status: 'skipped', durationMs: Date.now() - startTime };
            }

            const correct = this.extractCorrectness(payload);
            const params = this.getParams(claimId);
            const key = `${learnerId}::${claimId}`;

            // Get or initialize state
            const current = this.state.get(key) ?? {
                pLearned: params.pInit,
                observations: 0,
            };

            const previousPLearned = current.pLearned;

            // BKT update step
            const pLearned = this.updateBKT(current.pLearned, correct, params);

            current.pLearned = pLearned;
            current.observations += 1;
            this.state.set(key, current);

            const result: BKTResult = {
                pLearned,
                previousPLearned,
                mastered: pLearned >= this.config.masteryThreshold,
                correct,
                claimId,
                observationCount: current.observations,
            };

            // Store result in context for downstream plugins
            const contextResults = ((context as any).bktResults ?? []) as BKTResult[];
            contextResults.push(result);
            (context as any).bktResults = contextResults;

            return {
                pluginId,
                status: 'success',
                durationMs: Date.now() - startTime,
                data: { ...result },
            };
        } catch (error: any) {
            return {
                pluginId,
                status: 'error',
                durationMs: Date.now() - startTime,
                error: error.message,
            };
        }
    }

    /**
     * Get the current P(L) for a learner-skill pair.
     */
    getPLearned(learnerId: string, claimId: string): number {
        const key = `${learnerId}::${claimId}`;
        return this.state.get(key)?.pLearned ?? this.getParams(claimId).pInit;
    }

    /**
     * Check if a skill is mastered for a learner.
     */
    isMastered(learnerId: string, claimId: string): boolean {
        return this.getPLearned(learnerId, claimId) >= this.config.masteryThreshold;
    }

    // =========================================================================
    // Private
    // =========================================================================

    /**
     * Core BKT update: compute P(L_n | obs_n) from P(L_{n-1}).
     */
    private updateBKT(pL: number, correct: boolean, params: BKTParams): number {
        const { pGuess, pSlip, pTransit } = params;

        // Posterior: P(L | obs)
        let pLGivenObs: number;
        if (correct) {
            // P(L | correct) = P(L) * (1 - P(S)) / P(correct)
            const pCorrect = pL * (1 - pSlip) + (1 - pL) * pGuess;
            pLGivenObs = pCorrect > 0 ? (pL * (1 - pSlip)) / pCorrect : pL;
        } else {
            // P(L | incorrect) = P(L) * P(S) / P(incorrect)
            const pIncorrect = pL * pSlip + (1 - pL) * (1 - pGuess);
            pLGivenObs = pIncorrect > 0 ? (pL * pSlip) / pIncorrect : pL;
        }

        // Transition: P(L_n) = P(L | obs) + (1 - P(L | obs)) * P(T)
        const pLNew = pLGivenObs + (1 - pLGivenObs) * pTransit;

        // Clamp to [0, 1]
        return Math.max(0, Math.min(1, pLNew));
    }

    private getParams(claimId: string): BKTParams {
        const overrides = this.config.skillParams?.[claimId];
        if (overrides) {
            return { ...this.config.defaultParams, ...overrides };
        }
        return this.config.defaultParams;
    }

    private extractCorrectness(payload: Record<string, unknown>): boolean {
        if ('correct' in payload) return Boolean(payload.correct);
        if ('isCorrect' in payload) return Boolean(payload.isCorrect);
        if ('success' in payload) return Boolean(payload.success);
        if ('score' in payload && 'maxScore' in payload) {
            return (payload.score as number) >= (payload.maxScore as number) * 0.5;
        }
        return false;
    }
}

/**
 * Factory function.
 */
export function createBKTProcessor(config?: Partial<BKTConfig>): BKTProcessor {
    return new BKTProcessor(config);
}

/**
 * IRT (Item Response Theory) Evidence Processor Plugin.
 *
 * Implements a 2-Parameter Logistic (2PL) IRT model for adaptive item
 * calibration and learner ability estimation. Each item has:
 *   - difficulty (b): the ability level at which P(correct) = 0.5
 *   - discrimination (a): how sharply the item differentiates learners
 *
 * Learner ability (θ) is estimated via maximum likelihood after each response.
 *
 * @doc.type class
 * @doc.purpose Estimate learner ability and item parameters using IRT 2PL model
 * @doc.layer plugin
 * @doc.pattern EvidenceProcessor
 *
 * @see https://en.wikipedia.org/wiki/Item_response_theory
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
 * Item parameters for the 2PL model.
 */
export interface IRTItemParams {
    /** Item ID (taskId or evidence identifier) */
    readonly itemId: string;
    /** Discrimination parameter (a) — how sharply the item differentiates */
    discrimination: number;
    /** Difficulty parameter (b) — ability at which P(correct) = 0.5 */
    difficulty: number;
}

/**
 * IRT processor configuration.
 */
export interface IRTConfig {
    /** Default discrimination for new items */
    readonly defaultDiscrimination: number;
    /** Default difficulty for new items */
    readonly defaultDifficulty: number;
    /** Initial ability estimate for new learners */
    readonly initialAbility: number;
    /** Step size for Newton-Raphson ability update */
    readonly learningRate: number;
    /** Max Newton-Raphson iterations per update */
    readonly maxIterations: number;
    /** Convergence tolerance for ability estimation */
    readonly convergenceTol: number;
    /** Pre-calibrated item parameters (optional) */
    readonly itemBank?: IRTItemParams[];
}

/**
 * IRT computation result for a single response.
 */
export interface IRTResult {
    /** Updated ability estimate (θ) */
    readonly ability: number;
    /** Previous ability estimate */
    readonly previousAbility: number;
    /** Standard error of ability estimate */
    readonly standardError: number;
    /** Predicted probability of correct response */
    readonly predictedProbability: number;
    /** Actual response correctness */
    readonly correct: boolean;
    /** Item ID */
    readonly itemId: string;
    /** Claim ID */
    readonly claimId: string;
    /** Number of responses used in estimation */
    readonly responseCount: number;
}

/**
 * Learner response record for ability estimation.
 */
interface ResponseRecord {
    itemId: string;
    correct: boolean;
}

const DEFAULT_IRT_CONFIG: IRTConfig = {
    defaultDiscrimination: 1.0,
    defaultDifficulty: 0.0,
    initialAbility: 0.0,
    learningRate: 1.0,
    maxIterations: 20,
    convergenceTol: 0.001,
};

// ============================================================================
// IRT Processor Plugin
// ============================================================================

export class IRTProcessor implements EvidenceProcessor {
    readonly metadata: PluginMetadata = {
        id: 'irt-processor',
        name: 'Item Response Theory (2PL) Processor',
        version: '1.0.0',
        type: 'evidence_processor',
        priority: 70,
        description: 'Estimates learner ability and item difficulty using 2-Parameter Logistic IRT model',
        author: 'TutorPutor Core Team',
        tags: ['mastery', 'irt', 'adaptive', 'item-calibration'],
        enabled: true,
    };

    private readonly config: IRTConfig;
    /** Item parameters keyed by itemId */
    private items: Map<string, IRTItemParams> = new Map();
    /** Learner state keyed by learnerId */
    private learners: Map<string, { ability: number; responses: ResponseRecord[] }> = new Map();

    constructor(config?: Partial<IRTConfig>) {
        this.config = { ...DEFAULT_IRT_CONFIG, ...config };
    }

    async initialize(): Promise<void> {
        this.items.clear();
        this.learners.clear();

        // Load pre-calibrated item bank if provided
        if (this.config.itemBank) {
            for (const item of this.config.itemBank) {
                this.items.set(item.itemId, { ...item });
            }
        }
    }

    async shutdown(): Promise<void> {
        this.items.clear();
        this.learners.clear();
    }

    supports(evidence: EvidenceEvent): boolean {
        const relevantTypes = [
            'answer_submission',
            'assessment_response',
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
            const itemId = (payload as any).taskId ?? (payload as any).itemId ?? claimId;

            if (!learnerId || !itemId) {
                return { pluginId, status: 'skipped', durationMs: Date.now() - startTime };
            }

            const correct = this.extractCorrectness(payload);

            // Ensure item exists
            if (!this.items.has(itemId)) {
                this.items.set(itemId, {
                    itemId,
                    discrimination: this.config.defaultDiscrimination,
                    difficulty: this.config.defaultDifficulty,
                });
            }

            // Get or initialize learner
            if (!this.learners.has(learnerId)) {
                this.learners.set(learnerId, {
                    ability: this.config.initialAbility,
                    responses: [],
                });
            }

            const learner = this.learners.get(learnerId)!;
            const previousAbility = learner.ability;

            // Record response
            learner.responses.push({ itemId, correct });

            // Compute predicted probability before update
            const item = this.items.get(itemId)!;
            const predictedProbability = this.iccProbability(previousAbility, item);

            // Update ability via MLE (Newton-Raphson)
            learner.ability = this.estimateAbility(learner.responses);

            // Compute standard error
            const standardError = this.computeStandardError(learner.ability, learner.responses);

            const result: IRTResult = {
                ability: learner.ability,
                previousAbility,
                standardError,
                predictedProbability,
                correct,
                itemId,
                claimId: claimId ?? itemId,
                responseCount: learner.responses.length,
            };

            // Store in context
            const contextResults = ((context as any).irtResults ?? []) as IRTResult[];
            contextResults.push(result);
            (context as any).irtResults = contextResults;

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
     * Get current ability estimate for a learner.
     */
    getAbility(learnerId: string): number {
        return this.learners.get(learnerId)?.ability ?? this.config.initialAbility;
    }

    /**
     * Get item parameters.
     */
    getItemParams(itemId: string): IRTItemParams | undefined {
        return this.items.get(itemId);
    }

    /**
     * Select the next optimal item for a learner (max information at current ability).
     */
    selectNextItem(learnerId: string, availableItemIds: string[]): string | null {
        const ability = this.getAbility(learnerId);
        let bestItemId: string | null = null;
        let bestInfo = -Infinity;

        for (const itemId of availableItemIds) {
            const item = this.items.get(itemId);
            if (!item) continue;

            const info = this.itemInformation(ability, item);
            if (info > bestInfo) {
                bestInfo = info;
                bestItemId = itemId;
            }
        }

        return bestItemId;
    }

    // =========================================================================
    // IRT Math
    // =========================================================================

    /**
     * 2PL Item Characteristic Curve: P(correct | θ, a, b)
     */
    private iccProbability(ability: number, item: IRTItemParams): number {
        const exponent = item.discrimination * (ability - item.difficulty);
        return 1 / (1 + Math.exp(-exponent));
    }

    /**
     * Fisher Information for an item at a given ability level.
     * I(θ) = a² * P(θ) * Q(θ)
     */
    private itemInformation(ability: number, item: IRTItemParams): number {
        const p = this.iccProbability(ability, item);
        const q = 1 - p;
        return item.discrimination * item.discrimination * p * q;
    }

    /**
     * Estimate ability via Maximum Likelihood Estimation (Newton-Raphson).
     */
    private estimateAbility(responses: ResponseRecord[]): number {
        if (responses.length === 0) return this.config.initialAbility;

        // Check degenerate case: all correct or all incorrect
        const allCorrect = responses.every((r) => r.correct);
        const allIncorrect = responses.every((r) => !r.correct);
        if (allCorrect) return Math.min(4, this.config.initialAbility + responses.length * 0.3);
        if (allIncorrect) return Math.max(-4, this.config.initialAbility - responses.length * 0.3);

        let theta = this.config.initialAbility;

        for (let iter = 0; iter < this.config.maxIterations; iter++) {
            let numerator = 0;
            let denominator = 0;

            for (const response of responses) {
                const item = this.items.get(response.itemId);
                if (!item) continue;

                const p = this.iccProbability(theta, item);
                const q = 1 - p;
                const u = response.correct ? 1 : 0;

                // First derivative of log-likelihood
                numerator += item.discrimination * (u - p);
                // Negative second derivative (information)
                denominator += item.discrimination * item.discrimination * p * q;
            }

            if (denominator === 0) break;

            const delta = (this.config.learningRate * numerator) / denominator;
            theta += delta;

            // Clamp to reasonable range
            theta = Math.max(-4, Math.min(4, theta));

            if (Math.abs(delta) < this.config.convergenceTol) break;
        }

        return theta;
    }

    /**
     * Compute standard error of ability estimate.
     * SE(θ) = 1 / sqrt(I(θ))  where I is total test information.
     */
    private computeStandardError(ability: number, responses: ResponseRecord[]): number {
        let totalInfo = 0;

        for (const response of responses) {
            const item = this.items.get(response.itemId);
            if (!item) continue;
            totalInfo += this.itemInformation(ability, item);
        }

        return totalInfo > 0 ? 1 / Math.sqrt(totalInfo) : 999;
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
export function createIRTProcessor(config?: Partial<IRTConfig>): IRTProcessor {
    return new IRTProcessor(config);
}

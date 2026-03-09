/**
 * CBM (Confidence-Based Marking) Evidence Processor Plugin.
 *
 * Implements confidence-weighted scoring and calibration metrics.
 * This plugin processes answer + confidence submissions and produces
 * evidence about learner calibration and mastery.
 *
 * @doc.type class
 * @doc.purpose Process CBM evidence from assessment submissions
 * @doc.layer plugin
 * @doc.pattern EvidenceProcessor
 *
 * @see https://en.wikipedia.org/wiki/Confidence-based_marking
 */

import type {
    EvidenceProcessor,
    PluginMetadata,
    EvidenceEvent,
    ProcessingResult,
    ProcessingContext,
} from '@ghatana/tutorputor-contracts/v1/plugin-interfaces';
import { CANONICAL_CBM_SCORING } from '@ghatana/tutorputor-contracts/v1/learning-unit';

/**
 * CBM scoring configuration.
 */
export interface CBMConfig {
    /** Points for correct + high confidence */
    readonly correctHighConfidence: number;
    /** Points for correct + medium confidence */
    readonly correctMediumConfidence: number;
    /** Points for correct + low confidence */
    readonly correctLowConfidence: number;
    /** Points for incorrect + high confidence (penalty) */
    readonly incorrectHighConfidence: number;
    /** Points for incorrect + medium confidence */
    readonly incorrectMediumConfidence: number;
    /** Points for incorrect + low confidence */
    readonly incorrectLowConfidence: number;
}

/**
 * Default CBM+ scoring matrix.
 * Uses the canonical values from contracts as the single source of truth.
 */
const DEFAULT_CBM_CONFIG: CBMConfig = { ...CANONICAL_CBM_SCORING };

/**
 * Confidence level thresholds.
 */
export type ConfidenceLevel = 'low' | 'medium' | 'high';

/**
 * CBM calculation result.
 */
export interface CBMResult {
    /** Raw CBM score for this item */
    readonly score: number;
    /** Whether the answer was correct */
    readonly isCorrect: boolean;
    /** Confidence level */
    readonly confidenceLevel: ConfidenceLevel;
    /** Raw confidence value (0-1) */
    readonly confidenceValue: number;
    /** Calibration indicator (-1 to 1, 0 = well calibrated) */
    readonly calibrationDelta: number;
}

/**
 * Aggregated CBM metrics for a session or topic.
 */
export interface CBMAggregateMetrics {
    /** Total CBM score */
    readonly totalScore: number;
    /** Number of items */
    readonly itemCount: number;
    /** Average score per item */
    readonly averageScore: number;
    /** Accuracy (proportion correct) */
    readonly accuracy: number;
    /** Average confidence */
    readonly averageConfidence: number;
    /** Calibration index (-1 = underconfident, 0 = calibrated, 1 = overconfident) */
    readonly calibrationIndex: number;
    /** Brier score (lower is better, measures probabilistic accuracy) */
    readonly brierScore: number;
}

/**
 * CBM Evidence Processor Plugin.
 *
 * Features:
 * - Confidence-weighted scoring using CBM+ matrix
 * - Calibration index calculation
 * - Brier score for probabilistic accuracy
 * - Session-level aggregation
 * - Topic-level mastery tracking
 *
 * @example
 * ```typescript
 * const cbm = new CBMProcessor();
 * registry.registerEvidenceProcessor(cbm);
 *
 * // Will be called for confidence-based assessment events
 * ```
 */
export class CBMProcessor implements EvidenceProcessor {
    readonly metadata: PluginMetadata = {
        id: 'cbm-processor',
        name: 'Confidence-Based Marking Processor',
        version: '1.0.0',
        type: 'evidence_processor',
        priority: 100, // High priority - runs early
        description: 'Processes assessment submissions with confidence-weighted scoring and calibration metrics',
        author: 'TutorPutor Core Team',
        tags: ['assessment', 'confidence', 'calibration', 'mastery'],
        enabled: true,
    };

    private readonly config: CBMConfig;

    constructor(config?: Partial<CBMConfig>) {
        this.config = { ...DEFAULT_CBM_CONFIG, ...config };
    }

    /**
     * Initialize the processor.
     */
    async initialize(): Promise<void> {
        // No initialization needed for this processor
    }

    /**
     * Shutdown the processor.
     */
    async shutdown(): Promise<void> {
        // No cleanup needed
    }

    /**
     * Check if this processor can handle the given evidence.
     */
    supports(evidence: EvidenceEvent): boolean {
        // Handle answer submissions with confidence data
        const relevantTypes = [
            'answer_submission',
            'confidence_submission',
            'assessment_response',
        ];

        return relevantTypes.includes(evidence.type);
    }

    /**
     * Process the evidence and update the context.
     */
    async process(
        context: ProcessingContext,
        evidence: EvidenceEvent
    ): Promise<ProcessingResult> {
        const startTime = Date.now();
        const pluginId = this.metadata.id;

        try {
            // Extract data from evidence payload
            const { payload } = evidence;

            if (!payload) {
                return {
                    pluginId,
                    status: 'skipped',
                    durationMs: Date.now() - startTime,
                };
            }

            // Get answer correctness and confidence
            const isCorrect = this.extractCorrectness(payload);
            const confidenceValue = this.extractConfidence(payload, context);

            if (isCorrect === undefined || confidenceValue === undefined) {
                // Need both answer and confidence to calculate CBM
                // Store partial data for later
                if (isCorrect !== undefined) {
                    context.data[`cbm.pending.${evidence.evidenceId}.isCorrect`] = isCorrect;
                }
                if (confidenceValue !== undefined) {
                    context.data[`cbm.pending.${evidence.evidenceId}.confidence`] = confidenceValue;
                }

                return {
                    pluginId,
                    status: 'success',
                    data: { pendingCBM: true },
                    durationMs: Date.now() - startTime,
                };
            }

            // Calculate CBM metrics
            const cbmResult = this.calculateCBM(isCorrect, confidenceValue);

            // Store in context for other plugins
            context.data[`cbm.item.${evidence.evidenceId}`] = cbmResult;

            // Update aggregate metrics
            this.updateAggregates(context, cbmResult, evidence);

            return {
                pluginId,
                status: 'success',
                data: {
                    cbmScore: cbmResult.score,
                    calibrationDelta: cbmResult.calibrationDelta,
                    confidenceLevel: cbmResult.confidenceLevel,
                },
                durationMs: Date.now() - startTime,
            };
        } catch (error) {
            return {
                pluginId,
                status: 'error',
                error: {
                    pluginId,
                    code: 'CBM_PROCESSING_ERROR',
                    message: error instanceof Error ? error.message : String(error),
                },
                durationMs: Date.now() - startTime,
            };
        }
    }

    /**
     * Extract correctness from evidence payload.
     */
    private extractCorrectness(payload: Record<string, unknown>): boolean | undefined {
        if ('correct' in payload) {
            return Boolean(payload.correct);
        }
        if ('isCorrect' in payload) {
            return Boolean(payload.isCorrect);
        }
        if ('score' in payload && typeof payload.score === 'number') {
            return payload.score > 0;
        }
        return undefined;
    }

    /**
     * Extract confidence from evidence payload or context.
     */
    private extractConfidence(
        payload: Record<string, unknown>,
        context: ProcessingContext
    ): number | undefined {
        // Check payload first
        if ('confidence' in payload && typeof payload.confidence === 'number') {
            return this.normalizeConfidence(payload.confidence);
        }

        // Check context for pending confidence (from earlier event)
        const evidenceId = (payload as { evidenceId?: string }).evidenceId;
        if (evidenceId) {
            const pending = context.data[`cbm.pending.${evidenceId}.confidence`];
            if (typeof pending === 'number') {
                return pending;
            }
        }

        return undefined;
    }

    /**
     * Normalize confidence to 0-1 range.
     */
    private normalizeConfidence(value: number): number {
        // Handle different scales
        if (value > 1) {
            // Assume percentage (0-100)
            return Math.min(1, Math.max(0, value / 100));
        }
        return Math.min(1, Math.max(0, value));
    }

    /**
     * Convert confidence value to level.
     */
    private confidenceToLevel(value: number): ConfidenceLevel {
        if (value >= 0.67) return 'high';
        if (value >= 0.34) return 'medium';
        return 'low';
    }

    /**
     * Calculate CBM score and metrics.
     */
    private calculateCBM(isCorrect: boolean, confidenceValue: number): CBMResult {
        const level = this.confidenceToLevel(confidenceValue);

        // Get score from matrix
        let score: number;
        if (isCorrect) {
            switch (level) {
                case 'high':
                    score = this.config.correctHighConfidence;
                    break;
                case 'medium':
                    score = this.config.correctMediumConfidence;
                    break;
                case 'low':
                    score = this.config.correctLowConfidence;
                    break;
            }
        } else {
            switch (level) {
                case 'high':
                    score = this.config.incorrectHighConfidence;
                    break;
                case 'medium':
                    score = this.config.incorrectMediumConfidence;
                    break;
                case 'low':
                    score = this.config.incorrectLowConfidence;
                    break;
            }
        }

        // Calibration delta: confidence - accuracy
        // Positive = overconfident, Negative = underconfident
        const calibrationDelta = confidenceValue - (isCorrect ? 1 : 0);

        return {
            score,
            isCorrect,
            confidenceLevel: level,
            confidenceValue,
            calibrationDelta,
        };
    }

    /**
     * Update aggregate metrics in context.
     */
    private updateAggregates(
        context: ProcessingContext,
        result: CBMResult,
        _evidence: EvidenceEvent
    ): void {
        // Session-level aggregation
        const sessionKey = 'cbm.aggregate.session';
        const existing = (context.data[sessionKey] as CBMItemList | undefined) ?? { items: [] };
        existing.items.push(result);
        context.data[sessionKey] = existing;

        // Calculate aggregate metrics
        const aggregate = this.calculateAggregate(existing.items);

        // Store aggregate
        context.data['cbm.aggregate.metrics'] = aggregate;

        // Check for viva trigger (significant miscalibration)
        if (aggregate.itemCount >= 3 && Math.abs(aggregate.calibrationIndex) > 0.3) {
            context.data['cbm.trigger.viva'] = {
                reason: aggregate.calibrationIndex > 0 ? 'overconfidence' : 'underconfidence',
                calibrationIndex: aggregate.calibrationIndex,
                threshold: 0.3,
            };
        }
    }

    /**
     * Calculate aggregate metrics from items.
     */
    private calculateAggregate(items: CBMResult[]): CBMAggregateMetrics {
        if (items.length === 0) {
            return {
                totalScore: 0,
                itemCount: 0,
                averageScore: 0,
                accuracy: 0,
                averageConfidence: 0,
                calibrationIndex: 0,
                brierScore: 0,
            };
        }

        const totalScore = items.reduce((sum, i) => sum + i.score, 0);
        const correctCount = items.filter((i) => i.isCorrect).length;
        const totalConfidence = items.reduce((sum, i) => sum + i.confidenceValue, 0);

        // Brier score: mean squared error between confidence and outcome
        const brierScore =
            items.reduce((sum, i) => {
                const outcome = i.isCorrect ? 1 : 0;
                return sum + Math.pow(i.confidenceValue - outcome, 2);
            }, 0) / items.length;

        // Calibration index: average calibration delta
        const calibrationIndex =
            items.reduce((sum, i) => sum + i.calibrationDelta, 0) / items.length;

        return {
            totalScore,
            itemCount: items.length,
            averageScore: totalScore / items.length,
            accuracy: correctCount / items.length,
            averageConfidence: totalConfidence / items.length,
            calibrationIndex,
            brierScore,
        };
    }
}

/**
 * Internal type for storing item list.
 */
interface CBMItemList {
    items: CBMResult[];
}

/**
 * Factory function to create CBM processor.
 */
export function createCBMProcessor(config?: Partial<CBMConfig>): CBMProcessor {
    return new CBMProcessor(config);
}

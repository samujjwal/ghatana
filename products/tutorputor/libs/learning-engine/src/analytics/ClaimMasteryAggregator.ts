/**
 * Claim Mastery Aggregator
 *
 * Bridges raw telemetry events to typed evidence records and computes
 * aggregate mastery scores per claim per learner. This is the central
 * evidence pipeline component that sits between the telemetry ingestion
 * layer and the ClaimMasteryCalculator.
 *
 * @doc.type class
 * @doc.purpose Map telemetry events to evidence, aggregate mastery per claim
 * @doc.layer core
 * @doc.pattern Service
 */

import {
    type ConfidenceLevel,
    type EvidenceType,
    getCBMScore,
    normalizeCBMScore,
    CANONICAL_EVIDENCE_WEIGHTS,
} from '@ghatana/tutorputor-contracts/v1/learning-unit';

import type {
    BaseTelemetryEvent,
    SimGoalAchievedEvent,
    SimGoalFailedEvent,
    AssessAnswerSubmitEvent,
    AssessConfidenceSubmitEvent,
} from '@ghatana/tutorputor-contracts/v1/telemetry-events';

import { ClaimMasteryCalculator, type EvidenceRecord } from './ClaimMastery';

// ============================================================================
// Types
// ============================================================================

/**
 * Evidence-Centered Design (ECD) metadata attached to each evidence record.
 * Captures the measurement context for audit and analytics.
 */
export interface ECDMetadata {
    /** Evidence model: what observable behavior is being measured */
    evidenceModel: string;
    /** Task model: what task generated this evidence */
    taskModel: string;
    /** Student model: what claim/competency this maps to */
    studentModel: string;
    /** Reliability estimate (0-1) for this evidence type */
    reliability: number;
    /** Whether this evidence was auto-scored or human-scored */
    scoringMethod: 'auto' | 'human' | 'hybrid';
}

/**
 * Enriched evidence record with ECD metadata and source event reference.
 */
export interface EnrichedEvidenceRecord extends EvidenceRecord {
    learnerId: string;
    sessionId: string;
    timestamp: Date;
    sourceEventType: string;
    ecd: ECDMetadata;
}

/**
 * Aggregated mastery result for a single claim.
 */
export interface ClaimMasteryResult {
    claimId: string;
    learnerId: string;
    masteryScore: number;
    confidence: number;
    evidenceCount: number;
    lastUpdated: Date;
    breakdown: {
        evidenceType: EvidenceType;
        score: number;
        weight: number;
        count: number;
    }[];
    calibration: {
        overconfidenceRate: number;
        underconfidenceRate: number;
        calibrationDelta: number;
    };
}

/**
 * Pending answer awaiting confidence rating.
 */
interface PendingAnswer {
    event: AssessAnswerSubmitEvent;
    claimId: string;
    receivedAt: Date;
}

// ============================================================================
// Telemetry → Evidence Mapper
// ============================================================================

/**
 * Maps a telemetry event to zero or more evidence records.
 * Returns null if the event doesn't map to evidence.
 */
function mapTelemetryToEvidence(
    event: BaseTelemetryEvent,
    pendingAnswers: Map<string, PendingAnswer>,
): EnrichedEvidenceRecord | null {
    const learnerId = event.actor.id;
    const claimId = event.context.claimId;
    const sessionId = event.context.sessionId;

    if (!claimId) return null;

    switch (event.type) {
        case 'sim.goal.achieved': {
            const e = event as SimGoalAchievedEvent;
            return {
                evidenceId: `${sessionId}:${e.object.goalId}:achieved`,
                claimId,
                type: 'parameter_targeting',
                correct: true,
                attempts: e.result.attempts,
                rmse: e.result.score,
                tolerance: 0.1,
                learnerId,
                sessionId,
                timestamp: new Date(event.timestamp),
                sourceEventType: event.type,
                ecd: {
                    evidenceModel: 'simulation_goal_achievement',
                    taskModel: `simulation:${e.object.simulationId}:goal:${e.object.goalId}`,
                    studentModel: `claim:${claimId}`,
                    reliability: 0.85,
                    scoringMethod: 'auto',
                },
            };
        }

        case 'sim.goal.failed': {
            const e = event as SimGoalFailedEvent;
            return {
                evidenceId: `${sessionId}:${e.object.goalId}:failed`,
                claimId,
                type: 'parameter_targeting',
                correct: false,
                attempts: e.result.attempts,
                learnerId,
                sessionId,
                timestamp: new Date(event.timestamp),
                sourceEventType: event.type,
                ecd: {
                    evidenceModel: 'simulation_goal_failure',
                    taskModel: `simulation:${e.object.simulationId}:goal:${e.object.goalId}`,
                    studentModel: `claim:${claimId}`,
                    reliability: 0.80,
                    scoringMethod: 'auto',
                },
            };
        }

        case 'assess.answer.submit': {
            const e = event as AssessAnswerSubmitEvent;
            // Store as pending — we need confidence to complete the CBM record
            pendingAnswers.set(e.object.taskId, {
                event: e,
                claimId,
                receivedAt: new Date(event.timestamp),
            });
            // If the task is an explanation, emit explanation_quality evidence immediately
            if (e.object.taskType === 'explanation') {
                return {
                    evidenceId: `${sessionId}:${e.object.taskId}:explanation`,
                    claimId,
                    type: 'explanation_quality',
                    rubricScore: e.result.score,
                    maxRubricScore: e.result.maxScore,
                    learnerId,
                    sessionId,
                    timestamp: new Date(event.timestamp),
                    sourceEventType: event.type,
                    ecd: {
                        evidenceModel: 'explanation_rubric',
                        taskModel: `task:${e.object.taskId}`,
                        studentModel: `claim:${claimId}`,
                        reliability: e.result.score !== undefined ? 0.70 : 0.50,
                        scoringMethod: e.result.score !== undefined ? 'auto' : 'human',
                    },
                };
            }
            return null;
        }

        case 'assess.confidence.submit': {
            const e = event as AssessConfidenceSubmitEvent;
            const pending = pendingAnswers.get(e.object.linkedAnswerId);
            if (!pending) return null;

            pendingAnswers.delete(e.object.linkedAnswerId);

            return {
                evidenceId: `${sessionId}:${e.object.linkedAnswerId}:cbm`,
                claimId: pending.claimId,
                type: 'prediction_vs_outcome',
                correct: pending.event.result.correct,
                confidence: e.result.confidence as ConfidenceLevel,
                learnerId,
                sessionId,
                timestamp: new Date(event.timestamp),
                sourceEventType: 'assess.cbm',
                ecd: {
                    evidenceModel: 'confidence_based_marking',
                    taskModel: `task:${e.object.linkedAnswerId}`,
                    studentModel: `claim:${pending.claimId}`,
                    reliability: 0.90,
                    scoringMethod: 'auto',
                },
            };
        }

        default:
            return null;
    }
}

// ============================================================================
// Aggregator
// ============================================================================

export class ClaimMasteryAggregator {
    private calculator: ClaimMasteryCalculator;
    private pendingAnswers: Map<string, PendingAnswer>;
    /** evidence records grouped by `learnerId::claimId` */
    private evidenceStore: Map<string, EnrichedEvidenceRecord[]>;

    constructor() {
        this.calculator = new ClaimMasteryCalculator();
        this.pendingAnswers = new Map();
        this.evidenceStore = new Map();
    }

    /**
     * Ingest a batch of telemetry events.
     * Maps each to evidence and stores for aggregation.
     */
    ingest(events: BaseTelemetryEvent[]): EnrichedEvidenceRecord[] {
        const newEvidence: EnrichedEvidenceRecord[] = [];

        for (const event of events) {
            const record = mapTelemetryToEvidence(event, this.pendingAnswers);
            if (record) {
                const key = `${record.learnerId}::${record.claimId}`;
                if (!this.evidenceStore.has(key)) {
                    this.evidenceStore.set(key, []);
                }
                this.evidenceStore.get(key)!.push(record);
                newEvidence.push(record);
            }
        }

        return newEvidence;
    }

    /**
     * Compute aggregate mastery for a specific learner + claim.
     */
    computeMastery(learnerId: string, claimId: string): ClaimMasteryResult | null {
        const key = `${learnerId}::${claimId}`;
        const records = this.evidenceStore.get(key);
        if (!records || records.length === 0) return null;

        // Group by evidence type
        const byType = new Map<EvidenceType, EnrichedEvidenceRecord[]>();
        for (const r of records) {
            if (!byType.has(r.type)) byType.set(r.type, []);
            byType.get(r.type)!.push(r);
        }

        // Compute weighted score per type
        const breakdown: ClaimMasteryResult['breakdown'] = [];
        let weightedSum = 0;
        let totalWeight = 0;

        for (const [type, typeRecords] of byType) {
            const weight = CANONICAL_EVIDENCE_WEIGHTS[type] ?? 0.2;
            const typeScore = this.computeTypeScore(type, typeRecords);

            breakdown.push({
                evidenceType: type,
                score: typeScore,
                weight,
                count: typeRecords.length,
            });

            weightedSum += typeScore * weight;
            totalWeight += weight;
        }

        const masteryScore = totalWeight > 0 ? weightedSum / totalWeight : 0;

        // Compute calibration metrics from CBM records
        const cbmRecords = records.filter(
            (r) => r.type === 'prediction_vs_outcome' && r.confidence !== undefined,
        );
        const calibration = this.computeCalibration(cbmRecords);

        // Confidence in the mastery estimate (based on evidence count and diversity)
        const typeCount = byType.size;
        const evidenceCount = records.length;
        const confidence = Math.min(1, (evidenceCount / 10) * (typeCount / 3));

        return {
            claimId,
            learnerId,
            masteryScore: Math.max(0, Math.min(1, masteryScore)),
            confidence: Math.max(0, Math.min(1, confidence)),
            evidenceCount,
            lastUpdated: records[records.length - 1].timestamp,
            breakdown,
            calibration,
        };
    }

    /**
     * Compute mastery for all claims for a learner.
     */
    computeAllMastery(learnerId: string): ClaimMasteryResult[] {
        const results: ClaimMasteryResult[] = [];
        const prefix = `${learnerId}::`;

        for (const key of this.evidenceStore.keys()) {
            if (key.startsWith(prefix)) {
                const claimId = key.slice(prefix.length);
                const result = this.computeMastery(learnerId, claimId);
                if (result) results.push(result);
            }
        }

        return results;
    }

    /**
     * Get all enriched evidence for a learner + claim (for audit/debugging).
     */
    getEvidence(learnerId: string, claimId: string): EnrichedEvidenceRecord[] {
        return this.evidenceStore.get(`${learnerId}::${claimId}`) ?? [];
    }

    /**
     * Clear all stored evidence (for testing or session reset).
     */
    clear(): void {
        this.evidenceStore.clear();
        this.pendingAnswers.clear();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private computeTypeScore(type: EvidenceType, records: EnrichedEvidenceRecord[]): number {
        switch (type) {
            case 'prediction_vs_outcome': {
                // Average normalized CBM score
                const cbmScores = records
                    .filter((r) => r.correct !== undefined && r.confidence !== undefined)
                    .map((r) => {
                        const raw = getCBMScore(r.correct!, r.confidence!);
                        return normalizeCBMScore(raw);
                    });
                return cbmScores.length > 0
                    ? cbmScores.reduce((a, b) => a + b, 0) / cbmScores.length
                    : 0;
            }

            case 'parameter_targeting': {
                // Use calculator's parameter targeting method
                const scores = records.map((r) =>
                    this.calculator.calculateParameterTargetingScore(
                        r.correct ?? false,
                        r.attempts ?? 1,
                        r.rmse ?? 1,
                        r.tolerance ?? 0.1,
                    ),
                );
                return scores.length > 0
                    ? scores.reduce((a, b) => a + b, 0) / scores.length
                    : 0;
            }

            case 'explanation_quality': {
                // Average rubric score
                const rubricScores = records
                    .filter((r) => r.rubricScore !== undefined && r.maxRubricScore !== undefined)
                    .map((r) => r.rubricScore! / r.maxRubricScore!);
                return rubricScores.length > 0
                    ? rubricScores.reduce((a, b) => a + b, 0) / rubricScores.length
                    : 0;
            }

            case 'construction_artifact': {
                // Binary: any successful construction counts
                const successCount = records.filter((r) => r.correct).length;
                return records.length > 0 ? successCount / records.length : 0;
            }

            case 'observation':
            case 'diagnosis': {
                // Generic: average of correct/incorrect binary
                const correctCount = records.filter((r) => r.correct).length;
                return records.length > 0 ? correctCount / records.length : 0;
            }

            default:
                return 0;
        }
    }

    private computeCalibration(
        cbmRecords: EnrichedEvidenceRecord[],
    ): ClaimMasteryResult['calibration'] {
        if (cbmRecords.length === 0) {
            return { overconfidenceRate: 0, underconfidenceRate: 0, calibrationDelta: 0 };
        }

        let overconfident = 0;
        let underconfident = 0;

        for (const r of cbmRecords) {
            if (!r.correct && r.confidence === 'high') overconfident++;
            if (r.correct && r.confidence === 'low') underconfident++;
        }

        const overconfidenceRate = overconfident / cbmRecords.length;
        const underconfidenceRate = underconfident / cbmRecords.length;
        const calibrationDelta = overconfidenceRate - underconfidenceRate;

        return { overconfidenceRate, underconfidenceRate, calibrationDelta };
    }
}

/**
 * Factory function.
 */
export function createClaimMasteryAggregator(): ClaimMasteryAggregator {
    return new ClaimMasteryAggregator();
}

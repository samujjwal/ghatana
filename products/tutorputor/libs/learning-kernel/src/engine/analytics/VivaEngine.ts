/**
 * Viva Engine
 * 
 * Identifies learners who need oral verification based on
 * overconfidence patterns, speed anomalies, and behavior mismatches.
 * 
 * @doc.type class
 * @doc.purpose Identify viva candidates from evidence patterns
 * @doc.layer core
 * @doc.pattern Service
 */

import type {
    VivaCandidate,
    VivaCondition,
    VivaConditionType,
    ConfidenceLevel,
} from '@ghatana/tutorputor-contracts/v1/learning-unit';

/**
 * Prediction record for viva analysis
 */
export interface PredictionRecord {
    learnerId: string;
    claimId: string;
    correct: boolean;
    confidence: ConfidenceLevel;
    completionTimeSeconds: number;
    timestamp: Date;
}

/**
 * Simulation interaction record
 */
export interface SimulationRecord {
    learnerId: string;
    claimId: string;
    goalAchieved: boolean;
    attempts: number;
    parameterChanges: number;
    completionTimeSeconds: number;
    timestamp: Date;
}

/**
 * Explanation task record for avoidance detection
 */
export interface ExplanationRecord {
    learnerId: string;
    claimId: string;
    /** Whether the learner attempted the explanation task */
    attempted: boolean;
    /** Whether the learner skipped the explanation task */
    skipped: boolean;
    /** Rubric score (0-1) if attempted */
    rubricScore?: number;
    /** Time spent in seconds */
    timeSpentSeconds: number;
    timestamp: Date;
}

/**
 * Cohort statistics for percentile calculations
 */
export interface CohortStats {
    completionTimeP10: number;
    completionTimeP50: number;
    completionTimeP90: number;
    avgAttempts: number;
    avgParameterChanges: number;
    /** Average explanation skip rate in cohort (0-1) */
    explanationSkipRate?: number;
    /** Average explanation time in seconds */
    avgExplanationTimeSeconds?: number;
}

/**
 * Priority levels for viva queue
 */
const PRIORITY = {
    CRITICAL: 1,
    HIGH: 2,
    MEDIUM: 3,
    LOW: 4,
} as const;

export class VivaEngine {
    private cohortStats: CohortStats;
    private vivaConditions: VivaCondition[];

    constructor(
        cohortStats: CohortStats,
        vivaConditions: VivaCondition[] = []
    ) {
        this.cohortStats = cohortStats;
        this.vivaConditions = vivaConditions.length > 0 ? vivaConditions : this.defaultConditions();
    }

    /**
     * Default viva trigger conditions
     */
    private defaultConditions(): VivaCondition[] {
        return [
            { type: 'overconfident_wrong', threshold: 2 },
            { type: 'speed_anomaly', completionTimePercentile: '<= 10' },
            { type: 'pattern_mismatch', description: 'Correct but random exploration' },
            { type: 'explanation_avoidance' },
        ];
    }

    /**
     * Analyze a learner's predictions for viva triggers
     */
    identifyVivaCandidates(
        predictions: PredictionRecord[],
        simulations: SimulationRecord[],
        explanations: ExplanationRecord[] = []
    ): VivaCandidate[] {
        const candidates: VivaCandidate[] = [];

        // Group by learner and claim
        const byLearnerClaim = this.groupByLearnerClaim(predictions, simulations, explanations);

        for (const [key, data] of byLearnerClaim) {
            const [learnerId, claimId] = key.split('::');
            const triggers = this.evaluateTriggers(data.predictions, data.simulations, data.explanations);

            for (const trigger of triggers) {
                candidates.push({
                    learnerId,
                    claimId,
                    reason: trigger.type,
                    priority: trigger.priority,
                });
            }
        }

        // Sort by priority (1 = highest)
        return candidates.sort((a, b) => a.priority - b.priority);
    }

    /**
     * Evaluate all viva conditions for a learner's data
     */
    private evaluateTriggers(
        predictions: PredictionRecord[],
        simulations: SimulationRecord[],
        explanations: ExplanationRecord[]
    ): Array<{ type: VivaConditionType; priority: number }> {
        const triggers: Array<{ type: VivaConditionType; priority: number }> = [];

        // Rule 1: Overconfident wrong (high confidence + incorrect, N times)
        if (this.checkOverconfidentWrong(predictions)) {
            triggers.push({ type: 'overconfident_wrong', priority: PRIORITY.CRITICAL });
        }

        // Rule 2: Speed anomaly (completion time in bottom 10%)
        if (this.checkSpeedAnomaly(predictions, simulations)) {
            triggers.push({ type: 'speed_anomaly', priority: PRIORITY.HIGH });
        }

        // Rule 3: Pattern mismatch (correct answer but random exploration)
        if (this.checkPatternMismatch(predictions, simulations)) {
            triggers.push({ type: 'pattern_mismatch', priority: PRIORITY.MEDIUM });
        }

        // Rule 4: Explanation avoidance (skipped all or most explanation tasks)
        if (this.checkExplanationAvoidance(explanations)) {
            triggers.push({ type: 'explanation_avoidance', priority: PRIORITY.HIGH });
        }

        // Rule 5: Gaming detection (rapid correct answers with no exploration)
        if (this.checkGamingDetection(predictions, simulations)) {
            triggers.push({ type: 'gaming_detection', priority: PRIORITY.CRITICAL });
        }

        // Rule 6: Simulation-evidence contradiction
        if (this.checkSimEvidenceContradiction(predictions, simulations)) {
            triggers.push({ type: 'sim_evidence_contradiction', priority: PRIORITY.HIGH });
        }

        return triggers;
    }

    /**
     * Check for overconfident wrong pattern
     */
    private checkOverconfidentWrong(predictions: PredictionRecord[]): boolean {
        const condition = this.vivaConditions.find((c) => c.type === 'overconfident_wrong');
        const threshold = condition?.threshold ?? 2;

        let consecutiveOverconfidentWrong = 0;

        for (const p of predictions) {
            if (!p.correct && p.confidence === 'high') {
                consecutiveOverconfidentWrong++;
                if (consecutiveOverconfidentWrong >= threshold) {
                    return true;
                }
            } else if (p.correct) {
                // Reset counter on correct answer
                consecutiveOverconfidentWrong = 0;
            }
        }

        return false;
    }

    /**
     * Check for suspiciously fast completion
     */
    private checkSpeedAnomaly(
        predictions: PredictionRecord[],
        simulations: SimulationRecord[]
    ): boolean {
        // Calculate average completion time
        const allTimes = [
            ...predictions.map((p) => p.completionTimeSeconds),
            ...simulations.map((s) => s.completionTimeSeconds),
        ];

        if (allTimes.length === 0) {
            return false;
        }

        const avgTime = allTimes.reduce((a, b) => a + b, 0) / allTimes.length;

        // Check if below P10 threshold
        return avgTime < this.cohortStats.completionTimeP10;
    }

    /**
     * Check for pattern mismatch (correct but random exploration)
     */
    private checkPatternMismatch(
        predictions: PredictionRecord[],
        simulations: SimulationRecord[]
    ): boolean {
        // All predictions correct
        const allPredictionsCorrect = predictions.every((p) => p.correct);
        if (!allPredictionsCorrect) {
            return false;
        }

        // But simulation exploration looks random (too many parameter changes)
        const avgParamChanges = simulations.reduce((sum, s) => sum + s.parameterChanges, 0) /
            (simulations.length || 1);

        const isRandomExploration = avgParamChanges > this.cohortStats.avgParameterChanges * 2;

        // Low confidence on correct answers (underconfidence + correct = possible pattern matching)
        const lowConfidenceCorrect = predictions.filter(
            (p) => p.correct && p.confidence === 'low'
        ).length;
        const suspiciousConfidence = lowConfidenceCorrect > predictions.length * 0.5;

        return isRandomExploration || suspiciousConfidence;
    }

    /**
     * Check for explanation avoidance.
     * Triggers when a learner skips all or most explanation tasks.
     */
    private checkExplanationAvoidance(explanations: ExplanationRecord[]): boolean {
        if (explanations.length === 0) return false;

        const skipped = explanations.filter((e) => e.skipped).length;
        const skipRate = skipped / explanations.length;

        // Trigger if >75% of explanation tasks were skipped
        if (skipRate > 0.75) return true;

        // Also trigger if attempted but with suspiciously low time (< 5 seconds avg)
        const attempted = explanations.filter((e) => e.attempted && !e.skipped);
        if (attempted.length > 0) {
            const avgTime = attempted.reduce((sum, e) => sum + e.timeSpentSeconds, 0) / attempted.length;
            const cohortAvg = this.cohortStats.avgExplanationTimeSeconds ?? 60;
            // Less than 10% of cohort average time suggests not genuinely attempting
            if (avgTime < cohortAvg * 0.1) return true;
        }

        return false;
    }

    /**
     * Check for gaming behavior.
     * Triggers when a learner achieves correct answers suspiciously fast
     * with minimal simulation exploration, suggesting answer lookup or copying.
     */
    private checkGamingDetection(
        predictions: PredictionRecord[],
        simulations: SimulationRecord[]
    ): boolean {
        if (predictions.length < 3) return false;

        // All correct with high confidence
        const allCorrectHighConf = predictions.every(
            (p) => p.correct && p.confidence === 'high'
        );
        if (!allCorrectHighConf) return false;

        // Suspiciously fast completion (below P10 for ALL items, not just average)
        const allFast = predictions.every(
            (p) => p.completionTimeSeconds < this.cohortStats.completionTimeP10
        );
        if (!allFast) return false;

        // Minimal or no simulation exploration
        if (simulations.length === 0) return true;

        const totalParamChanges = simulations.reduce((sum, s) => sum + s.parameterChanges, 0);
        const avgParamChanges = totalParamChanges / simulations.length;

        // Less than 25% of cohort average parameter changes
        return avgParamChanges < this.cohortStats.avgParameterChanges * 0.25;
    }

    /**
     * Check for simulation-evidence contradiction.
     * Triggers when prediction correctness contradicts simulation performance,
     * e.g., correct predictions but failed simulations or vice versa.
     */
    private checkSimEvidenceContradiction(
        predictions: PredictionRecord[],
        simulations: SimulationRecord[]
    ): boolean {
        if (predictions.length < 2 || simulations.length < 1) return false;

        const predictionAccuracy = predictions.filter((p) => p.correct).length / predictions.length;
        const simSuccessRate = simulations.filter((s) => s.goalAchieved).length / simulations.length;

        // Large discrepancy between prediction accuracy and simulation success
        // High prediction accuracy but low simulation success suggests guessing/copying
        const gap = predictionAccuracy - simSuccessRate;
        if (gap > 0.5) return true;

        // Reverse: low prediction accuracy but high simulation success suggests
        // the learner may understand but not be able to articulate (less severe, but worth flagging)
        if (simSuccessRate - predictionAccuracy > 0.6) return true;

        return false;
    }

    /**
     * Group records by learner and claim
     */
    private groupByLearnerClaim(
        predictions: PredictionRecord[],
        simulations: SimulationRecord[],
        explanations: ExplanationRecord[] = []
    ): Map<string, { predictions: PredictionRecord[]; simulations: SimulationRecord[]; explanations: ExplanationRecord[] }> {
        const map = new Map<string, { predictions: PredictionRecord[]; simulations: SimulationRecord[]; explanations: ExplanationRecord[] }>();

        const ensureKey = (key: string) => {
            if (!map.has(key)) {
                map.set(key, { predictions: [], simulations: [], explanations: [] });
            }
        };

        for (const p of predictions) {
            const key = `${p.learnerId}::${p.claimId}`;
            ensureKey(key);
            map.get(key)!.predictions.push(p);
        }

        for (const s of simulations) {
            const key = `${s.learnerId}::${s.claimId}`;
            ensureKey(key);
            map.get(key)!.simulations.push(s);
        }

        for (const e of explanations) {
            const key = `${e.learnerId}::${e.claimId}`;
            ensureKey(key);
            map.get(key)!.explanations.push(e);
        }

        return map;
    }
}

/**
 * Factory function
 */
export function identifyVivaCandidates(
    predictions: PredictionRecord[],
    simulations: SimulationRecord[],
    cohortStats: CohortStats,
    vivaConditions?: VivaCondition[],
    explanations?: ExplanationRecord[]
): VivaCandidate[] {
    const engine = new VivaEngine(cohortStats, vivaConditions);
    return engine.identifyVivaCandidates(predictions, simulations, explanations);
}

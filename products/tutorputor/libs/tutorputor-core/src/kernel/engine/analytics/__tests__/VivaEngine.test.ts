/**
 * VivaEngine Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test all 6 viva detection triggers
 * @doc.layer core
 * @doc.pattern UnitTest
 */
import { describe, it, expect } from 'vitest';
import {
    VivaEngine,
    type PredictionRecord,
    type SimulationRecord,
    type ExplanationRecord,
    type CohortStats,
} from '../VivaEngine';

const baseCohort: CohortStats = {
    completionTimeP10: 10,
    completionTimeP50: 30,
    completionTimeP90: 60,
    avgAttempts: 3,
    avgParameterChanges: 5,
    explanationSkipRate: 0.1,
    avgExplanationTimeSeconds: 60,
};

function makePrediction(overrides: Partial<PredictionRecord> = {}): PredictionRecord {
    return {
        learnerId: 'L1',
        claimId: 'C1',
        correct: true,
        confidence: 'high',
        completionTimeSeconds: 30,
        timestamp: new Date(),
        ...overrides,
    };
}

function makeSim(overrides: Partial<SimulationRecord> = {}): SimulationRecord {
    return {
        learnerId: 'L1',
        claimId: 'C1',
        goalAchieved: true,
        attempts: 2,
        parameterChanges: 4,
        completionTimeSeconds: 30,
        timestamp: new Date(),
        ...overrides,
    };
}

function makeExplanation(overrides: Partial<ExplanationRecord> = {}): ExplanationRecord {
    return {
        learnerId: 'L1',
        claimId: 'C1',
        attempted: true,
        skipped: false,
        rubricScore: 0.7,
        timeSpentSeconds: 45,
        timestamp: new Date(),
        ...overrides,
    };
}

describe('VivaEngine', () => {
    // =========================================================================
    // Rule 1: Overconfident Wrong
    // =========================================================================
    describe('overconfident_wrong', () => {
        it('triggers when 2+ consecutive high-confidence wrong answers', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: false, confidence: 'high' }),
                makePrediction({ correct: false, confidence: 'high' }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, []);

            expect(candidates.some((c) => c.reason === 'overconfident_wrong')).toBe(true);
            expect(candidates.find((c) => c.reason === 'overconfident_wrong')?.priority).toBe(1); // CRITICAL
        });

        it('does not trigger for medium-confidence wrong answers', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: false, confidence: 'medium' }),
                makePrediction({ correct: false, confidence: 'medium' }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, []);
            expect(candidates.some((c) => c.reason === 'overconfident_wrong')).toBe(false);
        });

        it('resets counter after a correct answer', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: false, confidence: 'high' }),
                makePrediction({ correct: true, confidence: 'high' }),
                makePrediction({ correct: false, confidence: 'high' }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, []);
            expect(candidates.some((c) => c.reason === 'overconfident_wrong')).toBe(false);
        });

        it('respects configurable threshold', () => {
            const engine = new VivaEngine(baseCohort, [
                { type: 'overconfident_wrong', threshold: 3 },
            ]);
            const predictions = [
                makePrediction({ correct: false, confidence: 'high' }),
                makePrediction({ correct: false, confidence: 'high' }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, []);
            expect(candidates.some((c) => c.reason === 'overconfident_wrong')).toBe(false);
        });
    });

    // =========================================================================
    // Rule 2: Speed Anomaly
    // =========================================================================
    describe('speed_anomaly', () => {
        it('triggers when avg completion time is below P10', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ completionTimeSeconds: 5 }),
                makePrediction({ completionTimeSeconds: 3 }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, []);
            expect(candidates.some((c) => c.reason === 'speed_anomaly')).toBe(true);
            expect(candidates.find((c) => c.reason === 'speed_anomaly')?.priority).toBe(2); // HIGH
        });

        it('does not trigger with normal completion times', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ completionTimeSeconds: 30 }),
                makePrediction({ completionTimeSeconds: 25 }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, []);
            expect(candidates.some((c) => c.reason === 'speed_anomaly')).toBe(false);
        });

        it('considers both prediction and simulation times', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [makePrediction({ completionTimeSeconds: 5 })];
            const simulations = [makeSim({ completionTimeSeconds: 5 })];

            const candidates = engine.identifyVivaCandidates(predictions, simulations);
            expect(candidates.some((c) => c.reason === 'speed_anomaly')).toBe(true);
        });
    });

    // =========================================================================
    // Rule 3: Pattern Mismatch
    // =========================================================================
    describe('pattern_mismatch', () => {
        it('triggers when all correct but with excessive random exploration', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: true, confidence: 'high' }),
                makePrediction({ correct: true, confidence: 'high' }),
            ];
            const simulations = [
                makeSim({ parameterChanges: 20 }), // 4x cohort avg of 5
            ];

            const candidates = engine.identifyVivaCandidates(predictions, simulations);
            expect(candidates.some((c) => c.reason === 'pattern_mismatch')).toBe(true);
            expect(candidates.find((c) => c.reason === 'pattern_mismatch')?.priority).toBe(3); // MEDIUM
        });

        it('triggers when all correct with low confidence (suspicious)', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: true, confidence: 'low' }),
                makePrediction({ correct: true, confidence: 'low' }),
                makePrediction({ correct: true, confidence: 'low' }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, []);
            expect(candidates.some((c) => c.reason === 'pattern_mismatch')).toBe(true);
        });

        it('does not trigger when some predictions are incorrect', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: true, confidence: 'high' }),
                makePrediction({ correct: false, confidence: 'high' }),
            ];
            const simulations = [makeSim({ parameterChanges: 20 })];

            const candidates = engine.identifyVivaCandidates(predictions, simulations);
            expect(candidates.some((c) => c.reason === 'pattern_mismatch')).toBe(false);
        });
    });

    // =========================================================================
    // Rule 4: Explanation Avoidance
    // =========================================================================
    describe('explanation_avoidance', () => {
        it('triggers when >75% of explanation tasks are skipped', () => {
            const engine = new VivaEngine(baseCohort);
            const explanations = [
                makeExplanation({ skipped: true, attempted: false }),
                makeExplanation({ skipped: true, attempted: false }),
                makeExplanation({ skipped: true, attempted: false }),
                makeExplanation({ skipped: false, attempted: true }),
            ];

            const candidates = engine.identifyVivaCandidates(
                [makePrediction()],
                [],
                explanations,
            );
            expect(candidates.some((c) => c.reason === 'explanation_avoidance')).toBe(true);
            expect(candidates.find((c) => c.reason === 'explanation_avoidance')?.priority).toBe(2); // HIGH
        });

        it('triggers for suspiciously low time on attempted explanations', () => {
            const engine = new VivaEngine(baseCohort);
            const explanations = [
                makeExplanation({ attempted: true, skipped: false, timeSpentSeconds: 2 }),
                makeExplanation({ attempted: true, skipped: false, timeSpentSeconds: 3 }),
            ];

            const candidates = engine.identifyVivaCandidates(
                [makePrediction()],
                [],
                explanations,
            );
            expect(candidates.some((c) => c.reason === 'explanation_avoidance')).toBe(true);
        });

        it('does not trigger when no explanations exist', () => {
            const engine = new VivaEngine(baseCohort);
            const candidates = engine.identifyVivaCandidates([makePrediction()], [], []);
            expect(candidates.some((c) => c.reason === 'explanation_avoidance')).toBe(false);
        });

        it('does not trigger with normal explanation attempts', () => {
            const engine = new VivaEngine(baseCohort);
            const explanations = [
                makeExplanation({ attempted: true, skipped: false, timeSpentSeconds: 45 }),
                makeExplanation({ attempted: true, skipped: false, timeSpentSeconds: 60 }),
            ];

            const candidates = engine.identifyVivaCandidates(
                [makePrediction()],
                [],
                explanations,
            );
            expect(candidates.some((c) => c.reason === 'explanation_avoidance')).toBe(false);
        });
    });

    // =========================================================================
    // Rule 5: Gaming Detection
    // =========================================================================
    describe('gaming_detection', () => {
        it('triggers on all-correct high-conf fast answers with no exploration', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: true, confidence: 'high', completionTimeSeconds: 3 }),
                makePrediction({ correct: true, confidence: 'high', completionTimeSeconds: 4 }),
                makePrediction({ correct: true, confidence: 'high', completionTimeSeconds: 2 }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, []);
            expect(candidates.some((c) => c.reason === 'gaming_detection')).toBe(true);
            expect(candidates.find((c) => c.reason === 'gaming_detection')?.priority).toBe(1); // CRITICAL
        });

        it('triggers with minimal simulation exploration', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: true, confidence: 'high', completionTimeSeconds: 3 }),
                makePrediction({ correct: true, confidence: 'high', completionTimeSeconds: 4 }),
                makePrediction({ correct: true, confidence: 'high', completionTimeSeconds: 2 }),
            ];
            const simulations = [
                makeSim({ parameterChanges: 0 }), // <25% of avg (5*0.25=1.25)
            ];

            const candidates = engine.identifyVivaCandidates(predictions, simulations);
            expect(candidates.some((c) => c.reason === 'gaming_detection')).toBe(true);
        });

        it('does not trigger with fewer than 3 predictions', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: true, confidence: 'high', completionTimeSeconds: 3 }),
                makePrediction({ correct: true, confidence: 'high', completionTimeSeconds: 4 }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, []);
            expect(candidates.some((c) => c.reason === 'gaming_detection')).toBe(false);
        });

        it('does not trigger when completion times are normal', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: true, confidence: 'high', completionTimeSeconds: 30 }),
                makePrediction({ correct: true, confidence: 'high', completionTimeSeconds: 25 }),
                makePrediction({ correct: true, confidence: 'high', completionTimeSeconds: 35 }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, []);
            expect(candidates.some((c) => c.reason === 'gaming_detection')).toBe(false);
        });
    });

    // =========================================================================
    // Rule 6: Simulation-Evidence Contradiction
    // =========================================================================
    describe('sim_evidence_contradiction', () => {
        it('triggers when predictions correct but simulations failed', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: true }),
                makePrediction({ correct: true }),
            ];
            const simulations = [
                makeSim({ goalAchieved: false }),
                makeSim({ goalAchieved: false }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, simulations);
            expect(candidates.some((c) => c.reason === 'sim_evidence_contradiction')).toBe(true);
            expect(candidates.find((c) => c.reason === 'sim_evidence_contradiction')?.priority).toBe(2); // HIGH
        });

        it('triggers when simulations succeeded but predictions wrong', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: false }),
                makePrediction({ correct: false }),
                makePrediction({ correct: false }),
            ];
            const simulations = [
                makeSim({ goalAchieved: true }),
                makeSim({ goalAchieved: true }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, simulations);
            expect(candidates.some((c) => c.reason === 'sim_evidence_contradiction')).toBe(true);
        });

        it('does not trigger when prediction and simulation align', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: true }),
                makePrediction({ correct: true }),
            ];
            const simulations = [
                makeSim({ goalAchieved: true }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, simulations);
            expect(candidates.some((c) => c.reason === 'sim_evidence_contradiction')).toBe(false);
        });

        it('requires minimum data (2 predictions, 1 simulation)', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [makePrediction({ correct: true })]; // Only 1
            const simulations = [makeSim({ goalAchieved: false })];

            const candidates = engine.identifyVivaCandidates(predictions, simulations);
            expect(candidates.some((c) => c.reason === 'sim_evidence_contradiction')).toBe(false);
        });
    });

    // =========================================================================
    // Cross-cutting
    // =========================================================================
    describe('cross-cutting', () => {
        it('returns candidates sorted by priority (lowest number = highest priority)', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: false, confidence: 'high', completionTimeSeconds: 3 }),
                makePrediction({ correct: false, confidence: 'high', completionTimeSeconds: 4 }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, []);

            for (let i = 1; i < candidates.length; i++) {
                expect(candidates[i].priority).toBeGreaterThanOrEqual(candidates[i - 1].priority);
            }
        });

        it('groups by learner and claim independently', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ learnerId: 'L1', claimId: 'C1', correct: false, confidence: 'high' }),
                makePrediction({ learnerId: 'L1', claimId: 'C1', correct: false, confidence: 'high' }),
                makePrediction({ learnerId: 'L2', claimId: 'C1', correct: true, confidence: 'high' }),
                makePrediction({ learnerId: 'L2', claimId: 'C1', correct: true, confidence: 'high' }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, []);

            const l1Candidates = candidates.filter((c) => c.learnerId === 'L1');
            const l2Candidates = candidates.filter((c) => c.learnerId === 'L2');

            expect(l1Candidates.some((c) => c.reason === 'overconfident_wrong')).toBe(true);
            expect(l2Candidates.some((c) => c.reason === 'overconfident_wrong')).toBe(false);
        });

        it('returns empty array when no triggers fire', () => {
            const engine = new VivaEngine(baseCohort);
            const predictions = [
                makePrediction({ correct: true, confidence: 'high', completionTimeSeconds: 30 }),
            ];
            const simulations = [makeSim({ goalAchieved: true, parameterChanges: 4 })];

            const candidates = engine.identifyVivaCandidates(predictions, simulations);
            expect(candidates).toHaveLength(0);
        });

        it('can fire multiple triggers for the same learner-claim', () => {
            const engine = new VivaEngine(baseCohort);
            // Overconfident wrong + speed anomaly
            const predictions = [
                makePrediction({ correct: false, confidence: 'high', completionTimeSeconds: 3 }),
                makePrediction({ correct: false, confidence: 'high', completionTimeSeconds: 4 }),
            ];

            const candidates = engine.identifyVivaCandidates(predictions, []);

            const reasons = candidates.map((c) => c.reason);
            expect(reasons).toContain('overconfident_wrong');
            expect(reasons).toContain('speed_anomaly');
        });
    });
});

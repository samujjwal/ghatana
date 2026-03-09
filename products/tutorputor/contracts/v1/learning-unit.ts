/**
 * Canonical Learning Unit Type Definitions
 * 
 * This is the single source of truth for Learning Unit structures across:
 * - CMS authoring
 * - Simulation generation
 * - AI tutoring
 * - Assessment
 * - Telemetry
 * - Credentials
 * 
 * @doc.type module
 * @doc.purpose Canonical Learning Unit type definitions
 * @doc.layer contracts
 * @doc.pattern ValueObject
 */

// ============================================================================
// Enums & Literal Types
// ============================================================================

export type BloomLevel =
    | 'remember'
    | 'understand'
    | 'apply'
    | 'analyze'
    | 'evaluate'
    | 'create';

export type EvidenceType =
    | 'prediction_vs_outcome'
    | 'parameter_targeting'
    | 'explanation_quality'
    | 'construction_artifact'
    | 'observation'
    | 'diagnosis';

export type TaskType =
    | 'prediction'
    | 'simulation'
    | 'explanation'
    | 'construction';

export type ConfidenceLevel = 'low' | 'medium' | 'high';

export type LearningUnitStatus = 'draft' | 'review' | 'published' | 'archived';

export type ArtifactType = 'simulation' | 'explainer_video' | 'visual_article' | 'infographic';

export type AssessmentModel = 'cbm' | 'cbm_plus_process' | 'mastery_grid';

export type VivaConditionType =
    | 'overconfident_wrong'
    | 'speed_anomaly'
    | 'pattern_mismatch'
    | 'explanation_avoidance'
    | 'gaming_detection'
    | 'sim_evidence_contradiction';

export type CredentialIssueCondition =
    | 'all_claims_mastered'
    | 'primary_claim_mastered'
    | 'completion';

// ============================================================================
// Intent
// ============================================================================

/**
 * Why does this Learning Unit exist?
 */
export interface Intent {
    /** The misconception or gap being addressed */
    problem: string;
    /** Real-world motivation for learning this */
    motivation: string;
    /** Specific misconceptions this LU targets */
    targetMisconceptions?: string[];
}

// ============================================================================
// Claims
// ============================================================================

/**
 * What can the learner predict, explain, or construct after this?
 */
export interface Claim {
    /** Unique identifier within the LU (e.g., C1, C2) */
    id: string;
    /** Testable statement using action verbs */
    text: string;
    /** Bloom's taxonomy level */
    bloom: BloomLevel;
    /** Prerequisites from other Learning Units (format: LU_id.claim_id) */
    prerequisites?: string[];
    /** Content needs analysis for this claim */
    contentNeeds?: ContentNeeds;
}

// ============================================================================
// Content Needs
// ============================================================================

export type ExampleType =
    | 'real_world_application'
    | 'step_by_step'
    | 'visual_representation'
    | 'problem_solving'
    | 'analogy'
    | 'case_study'
    | 'comparison'
    | 'counter_example';

export type SimulationInteractionType =
    | 'demonstration'
    | 'parameter_exploration'
    | 'guided_discovery'
    | 'open_ended'
    | 'prediction'
    | 'construction'
    | 'diagnosis';

export type AnimationContentType =
    | 'process_visualization'
    | 'timeline'
    | 'spatial_relationship'
    | 'cause_effect'
    | 'transformation'
    | 'comparison'
    | 'concept_visualization'
    | 'data_representation'
    | 'process_walkthrough';

export type ContentComplexity = 'simple' | 'moderate' | 'complex';
export type ScaffoldingLevel = 'high' | 'medium' | 'low' | 'none';
export type SimulationComplexity = 'basic' | 'intermediate' | 'advanced';

/**
 * Content needs analysis result for a claim.
 * Specifies which content modalities are required and their characteristics.
 */
export interface ContentNeeds {
    /** Example content requirements */
    examples: {
        required: boolean;
        types: ExampleType[];
        count: number;
        complexity: ContentComplexity;
        scaffolding: ScaffoldingLevel;
    };
    /** Simulation content requirements */
    simulation: {
        required: boolean;
        interactionType: SimulationInteractionType;
        complexity: SimulationComplexity;
        estimatedTimeMinutes: number;
        entities: string[];
    };
    /** Animation content requirements */
    animation: {
        required: boolean;
        type: AnimationContentType;
        durationSeconds: number;
        complexity: ContentComplexity;
    };
}

// ============================================================================
// Evidence
// ============================================================================

/**
 * Definition of an observable data point
 */
export interface Observable {
    /** Field name (e.g., predicted_trend) */
    name: string;
    /** Data type */
    type: 'string' | 'number' | 'boolean' | 'enum';
    /** Allowed values for enum type */
    enumValues?: string[];
    /** Unit of measurement (e.g., m/s, kg) */
    unit?: string;
}

/**
 * What observable learner behavior proves the claim?
 */
export interface Evidence {
    /** Unique identifier within the LU (e.g., E1, E2) */
    id: string;
    /** Reference to the claim this evidence supports */
    claimRef: string;
    /** Type of evidence collection */
    type: EvidenceType;
    /** Human-readable description */
    description: string;
    /** Data points captured for this evidence */
    observables: Observable[];
}

// ============================================================================
// Tasks
// ============================================================================

/**
 * Base interface for all task types
 */
export interface TaskBase {
    /** Unique identifier within the LU (e.g., T1, T2) */
    id: string;
    /** Task type discriminator */
    type: TaskType;
    /** Reference to the claim this task assesses */
    claimRef: string;
    /** Reference to the evidence this task produces */
    evidenceRef: string;
    /** The prompt or question shown to the learner */
    prompt: string;
}

/**
 * Prediction task with mandatory confidence
 */
export interface PredictionTask extends TaskBase {
    type: 'prediction';
    /** Confidence is always required for predictions */
    confidenceRequired: true;
    /** Answer options */
    options: string[];
    /** Correct answer */
    correctAnswer?: string;
}

/**
 * Simulation-based task with goal criteria
 */
export interface SimulationTask extends TaskBase {
    type: 'simulation';
    /** Reference to the simulation manifest */
    simulationRef: string;
    /** Human-readable goal description */
    goal: string;
    /** Success criteria */
    successCriteria: {
        /** Maximum allowed RMSE (e.g., "<= 0.25") */
        rmse?: string;
        /** Maximum attempts allowed */
        maxAttempts?: number;
        /** Time limit in seconds */
        timeLimit?: number;
    };
}

/**
 * Free-response explanation task
 */
export interface ExplanationTask extends TaskBase {
    type: 'explanation';
    /** Minimum word count */
    minWords?: number;
    /** Reference to scoring rubric */
    rubricRef?: string;
    /** Key terms that should appear */
    expectedTerms?: string[];
}

/**
 * Construction/building task
 */
export interface ConstructionTask extends TaskBase {
    type: 'construction';
    /** Type of artifact to build */
    artifactType: string;
    /** Validation rules for the constructed artifact */
    validationRules?: Record<string, unknown>;
}

/**
 * Union type for all task types
 */
export type Task = PredictionTask | SimulationTask | ExplanationTask | ConstructionTask;

// ============================================================================
// Artifacts
// ============================================================================

/**
 * Content delivery vehicle (simulation, video, article, etc.)
 */
export interface Artifact {
    /** Type of artifact */
    type: ArtifactType;
    /** Reference to the artifact resource */
    ref: string;
    /** Claims this artifact supports */
    claims: string[];
    /** Task IDs this artifact scaffolds (for explainers) */
    scaffolds?: string[];
}

// ============================================================================
// Telemetry
// ============================================================================

/**
 * Configuration for telemetry capture
 */
export interface TelemetryConfig {
    /** Event types to capture */
    events: string[];
    /** Derived process features */
    processFeatures: string[];
}

// ============================================================================
// Assessment
// ============================================================================

/**
 * Viva trigger condition
 */
export interface VivaCondition {
    /** Type of trigger */
    type: VivaConditionType;
    /** Threshold count (e.g., 2 for "2 consecutive overconfident wrong") */
    threshold?: number;
    /** Percentile threshold (e.g., "<= 10" for bottom 10%) */
    completionTimePercentile?: string;
    /** Human-readable description */
    description?: string;
}

/**
 * CBM scoring matrix
 */
export interface CBMScoring {
    correctHighConfidence: number;
    correctMediumConfidence: number;
    correctLowConfidence: number;
    incorrectHighConfidence: number;
    incorrectMediumConfidence: number;
    incorrectLowConfidence: number;
}

/**
 * Canonical CBM+ scoring matrix (Gardner-Medwin, 2006).
 *
 * This is the SINGLE SOURCE OF TRUTH for CBM scoring across the platform.
 * All scoring implementations MUST use these values.
 *
 * Range: -6 to +3
 *   correct + high confidence   = +3 (reward for well-calibrated knowledge)
 *   correct + medium confidence = +2
 *   correct + low confidence    = +1
 *   incorrect + high confidence = -6 (strong penalty for overconfidence)
 *   incorrect + medium confidence = -2
 *   incorrect + low confidence  =  0 (no penalty for honest uncertainty)
 */
export const CANONICAL_CBM_SCORING: Readonly<CBMScoring> = Object.freeze({
    correctHighConfidence: 3,
    correctMediumConfidence: 2,
    correctLowConfidence: 1,
    incorrectHighConfidence: -6,
    incorrectMediumConfidence: -2,
    incorrectLowConfidence: 0,
});

/**
 * Canonical evidence type weights for mastery calculation.
 * Used by ClaimMasteryAggregator and all scoring paths.
 */
export const CANONICAL_EVIDENCE_WEIGHTS: Readonly<Record<EvidenceType, number>> = Object.freeze({
    prediction_vs_outcome: 0.3,
    parameter_targeting: 0.5,
    explanation_quality: 0.2,
    construction_artifact: 0.4,
    observation: 0.2,
    diagnosis: 0.4,
});

/**
 * Utility: get CBM score from the canonical matrix.
 */
export function getCBMScore(correct: boolean, confidence: ConfidenceLevel): number {
    if (correct) {
        switch (confidence) {
            case 'high': return CANONICAL_CBM_SCORING.correctHighConfidence;
            case 'medium': return CANONICAL_CBM_SCORING.correctMediumConfidence;
            case 'low': return CANONICAL_CBM_SCORING.correctLowConfidence;
        }
    } else {
        switch (confidence) {
            case 'high': return CANONICAL_CBM_SCORING.incorrectHighConfidence;
            case 'medium': return CANONICAL_CBM_SCORING.incorrectMediumConfidence;
            case 'low': return CANONICAL_CBM_SCORING.incorrectLowConfidence;
        }
    }
}

/**
 * Normalize a raw CBM score to 0-1 range.
 * CBM range is -6 to +3, so shift by 6 and divide by 9.
 */
export function normalizeCBMScore(rawScore: number): number {
    return Math.max(0, Math.min(1, (rawScore + 6) / 9));
}

/**
 * Assessment configuration
 */
export interface AssessmentConfig {
    /** Scoring model */
    model: AssessmentModel;
    /** Available confidence levels */
    confidenceLevels: ConfidenceLevel[];
    /** CBM scoring matrix */
    scoring: CBMScoring;
    /** Viva trigger configuration */
    vivaTrigger?: {
        conditions: VivaCondition[];
    };
}

// ============================================================================
// Credentials
// ============================================================================

/**
 * Credential/badge configuration
 */
export interface CredentialConfig {
    /** Skill tags for the credential */
    skillTags: string[];
    /** When to issue the credential */
    issueOn: CredentialIssueCondition;
    /** Reference to badge definition */
    badgeRef?: string;
}

// ============================================================================
// Learning Unit (Root)
// ============================================================================

/**
 * Canonical Learning Unit - the atomic unit of evidence-based learning
 */
export interface LearningUnit {
    /** Unique identifier */
    id: string;
    /** Schema version */
    version: number;
    /** Subject domain (e.g., physics, math) */
    domain: string;
    /** Educational level (e.g., secondary, university) */
    level: string;
    /** Publication status */
    status: LearningUnitStatus;

    // Core components
    intent: Intent;
    claims: Claim[];
    evidence: Evidence[];
    tasks: Task[];
    artifacts: Artifact[];
    telemetry: TelemetryConfig;
    assessment: AssessmentConfig;
    credential?: CredentialConfig;

    // Metadata
    createdAt: string;
    updatedAt: string;
    createdBy: string;
    tenantId: string;
}

// ============================================================================
// Analytics Types
// ============================================================================

export interface ClaimMasteryScore {
    learningUnitId: string;
    claimId: string;
    learnerId: string;
    masteryScore: number;
    confidenceCalibration: number | null;
    evidenceScores: Record<string, number>;
    totalAttempts: number;
    timeOnTaskSeconds: number;
}

export interface CalibrationResult {
    byLevel: Record<ConfidenceLevel, {
        expected: number;
        actual: number;
        gap: number;
        n: number;
    }>;
    overallScore: number | null;
    needsViva: boolean;
}

export interface VivaCandidate {
    learnerId: string;
    claimId: string;
    reason: VivaConditionType;
    priority: number;
}

/**
 * Simulation Assessment Item Types
 *
 * TypeScript types for simulation-driven assessment items.
 * Supports prediction, manipulation, explanation, design, and diagnosis modes.
 *
 * @doc.type module
 * @doc.purpose Define types for simulation-based assessment items
 * @doc.layer contracts
 * @doc.pattern Schema
 */

import type { SimulationDomain, SimulationId, SimEntityId } from "../simulation";
import type { AssessmentItemId, LearningObjective } from "../types";

// =============================================================================
// Branded IDs
// =============================================================================

export type SimulationItemId = string & { readonly __simulationItemId: unique symbol };
export type SimulationStateId = string & { readonly __simulationStateId: unique symbol };

// =============================================================================
// Simulation Item Modes
// =============================================================================

/**
 * Assessment mode determining the interaction pattern.
 */
export type SimulationItemMode =
  | "prediction"    // Predict outcome given initial state
  | "manipulation"  // Modify parameters to achieve target state
  | "explanation"   // Explain observed simulation behavior
  | "design"        // Create a simulation configuration
  | "diagnosis";    // Identify issues in a simulation setup

// =============================================================================
// Grading Strategy Types
// =============================================================================

/**
 * Grading method for simulation items.
 */
export type GradingMethod =
  | "kernel_replay"     // Replay through simulation kernel
  | "state_comparison"  // Compare final states
  | "rubric"            // Apply rubric criteria
  | "cbm_weighted"      // Confidence-Based Marking
  | "hybrid";           // Combination of methods

/**
 * Configuration for kernel-based replay grading.
 */
export interface KernelReplayConfig {
  /** Tolerance thresholds per variable */
  tolerances: Record<string, number>;
  /** Metric weights for scoring */
  metricWeights: Record<string, number>;
  /** Maximum allowed deviation */
  maxDeviation?: number;
  /** Whether to grade trajectory or just final state */
  gradeTrajectory?: boolean;
}

/**
 * Configuration for state comparison grading.
 */
export interface StateComparisonConfig {
  /** ID of target state to compare against */
  targetStateId: SimulationStateId;
  /** Entity matching strategy */
  entityMatching: "strict" | "flexible" | "semantic";
  /** Property weights for scoring */
  propertyWeights: Record<string, number>;
  /** Properties to ignore in comparison */
  ignoredProperties?: string[];
}

/**
 * Rubric criterion for grading.
 */
export interface RubricCriterion {
  id: string;
  description: string;
  maxPoints: number;
  levels: Array<{
    level: number;
    description: string;
    points: number;
  }>;
}

/**
 * Configuration for rubric-based grading.
 */
export interface RubricConfig {
  criteria: RubricCriterion[];
}

/**
 * Confidence level for CBM.
 */
export interface CBMConfidenceLevel {
  level: number;
  label: string;
  correctMultiplier: number;
  incorrectPenalty: number;
}

/**
 * Configuration for Confidence-Based Marking.
 */
export interface CBMConfig {
  confidenceLevels: CBMConfidenceLevel[];
  requireConfidence?: boolean;
}

/**
 * Grading strategy for simulation items.
 */
export interface SimulationGradingStrategy {
  method: GradingMethod;
  kernelReplayConfig?: KernelReplayConfig;
  stateComparisonConfig?: StateComparisonConfig;
  rubricConfig?: RubricConfig;
  cbmConfig?: CBMConfig;
  partialCredit: boolean;
}

// =============================================================================
// Simulation Reference Types
// =============================================================================

/**
 * Reference to simulation manifest and state.
 */
export interface SimulationRef {
  manifestId: SimulationId;
  domain?: SimulationDomain;
  initialStateRef?: SimulationStateId;
  targetStateRef?: SimulationStateId;
  stepRange?: {
    startStep: number;
    endStep: number;
  };
}

/**
 * Parameter constraint for assessment.
 */
export interface ParameterConstraint {
  parameterId: string;
  parameterName?: string;
  locked: boolean;
  allowedRange?: {
    min: number;
    max: number;
  };
  allowedValues?: Array<number | string | boolean>;
}

/**
 * Entity focus for assessment.
 */
export interface EntityFocus {
  entityId: SimEntityId;
  entityType: string;
  observableProperties?: string[];
}

// =============================================================================
// Mode-Specific Options
// =============================================================================

/**
 * Target variable for prediction mode.
 */
export interface PredictionTarget {
  variableId: string;
  variableName: string;
  unit?: string;
  expectedValue: number;
  tolerance: number;
  toleranceType: "absolute" | "relative" | "percentage";
}

/**
 * Options for prediction mode.
 */
export interface PredictionOptions {
  targetVariables: PredictionTarget[];
  predictionTime?: number;
  showActualAfterSubmit: boolean;
}

/**
 * Target condition for manipulation mode.
 */
export interface ManipulationCondition {
  conditionId: string;
  description: string;
  evaluator: string;
  partialCreditThreshold?: number;
}

/**
 * Options for manipulation mode.
 */
export interface ManipulationOptions {
  targetConditions: ManipulationCondition[];
  maxActions?: number;
  timeLimit?: number;
}

/**
 * Options for explanation mode.
 */
export interface ExplanationOptions {
  requiredConcepts: string[];
  minWordCount?: number;
  maxWordCount?: number;
  rubricCriteria: Array<{
    criterion: string;
    description?: string;
    weight: number;
  }>;
}

// =============================================================================
// Hint and Feedback Types
// =============================================================================

/**
 * Progressive hint for learners.
 */
export interface SimulationHint {
  hintId: string;
  content: string;
  pointDeduction?: number;
  unlockCondition: "time_elapsed" | "attempts_made" | "request" | "auto";
}

/**
 * Feedback configuration.
 */
export interface SimulationFeedbackConfig {
  showCorrectAnswer: boolean;
  showExplanation: boolean;
  showReplay: boolean;
  customFeedbackByScore?: Array<{
    minScore: number;
    maxScore: number;
    message: string;
  }>;
}

// =============================================================================
// Main Simulation Item Type
// =============================================================================

/**
 * Simulation-based assessment item.
 *
 * Supports multiple assessment modes where learners interact with
 * simulations to demonstrate understanding.
 */
export interface SimulationAssessmentItem {
  /** Unique identifier */
  id: SimulationItemId;
  /** Item type discriminator */
  type: "simulation";
  /** Assessment mode */
  mode: SimulationItemMode;
  /** Question or task prompt */
  prompt: string;
  /** Context or background information */
  stimulus?: string;
  /** Maximum points */
  points: number;
  /** Reference to simulation */
  simulationRef: SimulationRef;
  /** Parameter constraints */
  parameterConstraints?: ParameterConstraint[];
  /** Entities to focus on */
  entityFocus?: EntityFocus[];
  /** Prediction mode options */
  predictionOptions?: PredictionOptions;
  /** Manipulation mode options */
  manipulationOptions?: ManipulationOptions;
  /** Explanation mode options */
  explanationOptions?: ExplanationOptions;
  /** Grading strategy */
  gradingStrategy: SimulationGradingStrategy;
  /** Progressive hints */
  hints?: SimulationHint[];
  /** Feedback configuration */
  feedback?: SimulationFeedbackConfig;
  /** Bloom's taxonomy level */
  taxonomyLevel?: LearningObjective["taxonomyLevel"];
  /** Additional metadata */
  metadata?: {
    authorId?: string;
    createdAt?: string;
    updatedAt?: string;
    version?: number;
    tags?: string[];
    alignedStandards?: string[];
  };
}

// =============================================================================
// Response Types
// =============================================================================

/**
 * Learner's prediction response.
 */
export interface PredictionResponse {
  type: "prediction";
  predictions: Array<{
    variableId: string;
    predictedValue: number;
    confidence?: number;
  }>;
  reasoning?: string;
}

/**
 * Learner's manipulation response.
 */
export interface ManipulationResponse {
  type: "manipulation";
  actions: Array<{
    actionType: string;
    parameterId?: string;
    entityId?: string;
    value?: unknown;
    timestamp: number;
  }>;
  finalStateSnapshot: SimulationStateId;
}

/**
 * Learner's explanation response.
 */
export interface ExplanationResponse {
  type: "explanation";
  explanation: string;
  referencedEntities?: SimEntityId[];
  referencedConcepts?: string[];
}

/**
 * Learner's design response.
 */
export interface DesignResponse {
  type: "design";
  designedStateSnapshot: SimulationStateId;
  designNotes?: string;
}

/**
 * Learner's diagnosis response.
 */
export interface DiagnosisResponse {
  type: "diagnosis";
  identifiedIssues: Array<{
    issueId: string;
    description: string;
    entityId?: SimEntityId;
    suggestedFix?: string;
  }>;
  overallAssessment?: string;
}

/**
 * Union of all simulation response types.
 */
export type SimulationResponse =
  | PredictionResponse
  | ManipulationResponse
  | ExplanationResponse
  | DesignResponse
  | DiagnosisResponse;

// =============================================================================
// Grading Result Types
// =============================================================================

/**
 * Grading result for a simulation item.
 */
export interface SimulationGradingResult {
  itemId: SimulationItemId;
  score: number;
  maxScore: number;
  scorePercent: number;
  gradingMethod: GradingMethod;
  breakdown?: Array<{
    criterionId: string;
    criterionName: string;
    score: number;
    maxScore: number;
    feedback?: string;
  }>;
  trajectoryAnalysis?: {
    deviationFromOptimal: number;
    efficiencyScore: number;
    keyMoments: Array<{
      step: number;
      description: string;
      impact: "positive" | "neutral" | "negative";
    }>;
  };
  feedback: string;
  correctSolution?: {
    stateId: SimulationStateId;
    explanation: string;
  };
}

// =============================================================================
// Factory Functions
// =============================================================================

/**
 * Create a simulation item ID from a string.
 */
export function createSimulationItemId(id: string): SimulationItemId {
  return id as SimulationItemId;
}

/**
 * Create a simulation state ID from a string.
 */
export function createSimulationStateId(id: string): SimulationStateId {
  return id as SimulationStateId;
}

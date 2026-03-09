/**
 * Assessments Module - Barrel Export
 *
 * Exports all assessment types including simulation-based assessment items.
 *
 * @doc.type module
 * @doc.purpose Export assessment contracts
 * @doc.layer contracts
 * @doc.pattern Barrel
 */

// Simulation Assessment Item Types
export type {
  SimulationItemId,
  SimulationStateId,
  SimulationItemMode,
  GradingMethod,
  KernelReplayConfig,
  StateComparisonConfig,
  RubricCriterion,
  RubricConfig,
  CBMConfidenceLevel,
  CBMConfig,
  SimulationGradingStrategy,
  SimulationRef,
  ParameterConstraint,
  EntityFocus,
  PredictionTarget,
  PredictionOptions,
  ManipulationCondition,
  ManipulationOptions,
  ExplanationOptions,
  SimulationHint,
  SimulationFeedbackConfig,
  SimulationAssessmentItem,
  PredictionResponse,
  ManipulationResponse,
  ExplanationResponse,
  DesignResponse,
  DiagnosisResponse,
  SimulationResponse,
  SimulationGradingResult,
} from "./types";

export {
  createSimulationItemId,
  createSimulationStateId,
} from "./types";

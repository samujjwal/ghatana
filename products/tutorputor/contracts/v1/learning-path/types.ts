/**
 * Simulation Step Types for Learning Path Integration
 *
 * @doc.type module
 * @doc.purpose Define types for simulation steps in learning paths
 * @doc.layer contracts
 * @doc.pattern Schema
 */

import type { SimulationDomain, SimulationManifest, SimulationId } from "../simulation/types";
import type { Difficulty, ModuleId, UserId, TenantId, AssessmentId } from "../types";

// =============================================================================
// Branded IDs for Type Safety
// =============================================================================

export type LearningPathId = string & { readonly __learningPathId: unique symbol };
export type LearningPathStepId = string & { readonly __learningPathStepId: unique symbol };
export type SkillId = string & { readonly __skillId: unique symbol };

// =============================================================================
// Skill Types
// =============================================================================

/**
 * A skill that can be taught or assessed through simulation.
 */
export interface SimulationSkill {
  skillId: SkillId;
  name: string;
  weight: number; // 0-1, contribution weight
}

// =============================================================================
// Prerequisite Types
// =============================================================================

/**
 * A prerequisite for a simulation step.
 */
export interface SimulationPrerequisite {
  stepId: LearningPathStepId;
  type: "required" | "recommended";
  minScore?: number; // 0-100
}

// =============================================================================
// Assessment Reference Types
// =============================================================================

/**
 * Reference to an assessment associated with a simulation step.
 */
export interface SimulationAssessmentRef {
  assessmentId: AssessmentId;
  position: "pre" | "post" | "inline";
  required: boolean;
}

// =============================================================================
// Completion Criteria Types
// =============================================================================

/**
 * Criteria for completing a simulation step.
 */
export interface SimulationCompletionCriteria {
  minTimeSpentSeconds?: number;
  requiredSteps?: string[]; // Simulation step IDs within the manifest
  minInteractions?: number;
  assessmentPassScore?: number; // 0-100
}

// =============================================================================
// Simulation Step Metadata
// =============================================================================

/**
 * Display and search metadata for a simulation step.
 */
export interface SimulationStepMetadata {
  title: string;
  description?: string;
  thumbnailUrl?: string;
  tags?: string[];
  keywords?: string[];
}

// =============================================================================
// Simulation Step (Learning Path Node)
// =============================================================================

/**
 * A simulation step in a learning path.
 * Represents a simulation as a first-class learning node.
 */
export interface SimulationLearningStep {
  id: LearningPathStepId;
  type: "simulation";
  simulationId: SimulationId;
  manifestId: string;
  domain: SimulationDomain;
  difficulty: Difficulty;
  skills: SimulationSkill[];
  prerequisites: SimulationPrerequisite[];
  estimatedTimeMinutes: number;
  assessmentRefs: SimulationAssessmentRef[];
  learningObjectives?: string[];
  metadata: SimulationStepMetadata;
  completionCriteria?: SimulationCompletionCriteria;
}

// =============================================================================
// Union Type for All Learning Step Types
// =============================================================================

/**
 * Base learning step interface.
 */
export interface BaseLearningStep {
  id: LearningPathStepId;
  type: string;
  estimatedTimeMinutes: number;
  metadata: {
    title: string;
    description?: string;
  };
}

/**
 * Content-based learning step (reading, video, etc.).
 */
export interface ContentLearningStep extends BaseLearningStep {
  type: "content";
  moduleId: ModuleId;
  blockId: string;
}

/**
 * Assessment-based learning step.
 */
export interface AssessmentLearningStep extends BaseLearningStep {
  type: "assessment";
  assessmentId: AssessmentId;
  passingScore: number;
}

/**
 * Union of all learning step types.
 */
export type LearningStep =
  | SimulationLearningStep
  | ContentLearningStep
  | AssessmentLearningStep;

// =============================================================================
// Learning Path Types
// =============================================================================

/**
 * A complete learning path.
 */
export interface LearningPath {
  id: LearningPathId;
  tenantId: TenantId;
  title: string;
  description?: string;
  domain: string;
  difficulty: Difficulty;
  steps: LearningStep[];
  totalDurationMinutes: number;
  createdAt: string;
  updatedAt: string;
  publishedAt?: string;
}

// =============================================================================
// Enrollment & Progress Types
// =============================================================================

/**
 * Status of a learning path step for a user.
 */
export type StepProgressStatus = "not_started" | "in_progress" | "completed" | "skipped";

/**
 * Progress on a single step.
 */
export interface StepProgress {
  stepId: LearningPathStepId;
  status: StepProgressStatus;
  score?: number;
  timeSpentSeconds: number;
  completedAt?: string;
  attempts?: number;
}

/**
 * User's enrollment in a learning path.
 */
export interface LearningPathEnrollment {
  id: string;
  userId: UserId;
  pathId: LearningPathId;
  tenantId: TenantId;
  status: "active" | "completed" | "paused" | "abandoned";
  currentStepId?: LearningPathStepId;
  stepProgress: StepProgress[];
  enrolledAt: string;
  completedAt?: string;
  lastAccessedAt?: string;
  overallProgress: number; // 0-100
}

// =============================================================================
// Diagnostic & Recommendation Types
// =============================================================================

/**
 * Input for generating a learning path from diagnostic results.
 */
export interface PathFromDiagnosticInput {
  tenantId: TenantId;
  userId: UserId;
  diagnosticResults: DiagnosticResult[];
  goals: string[];
  constraints?: PathConstraints;
}

/**
 * A diagnostic result for a skill or topic.
 */
export interface DiagnosticResult {
  skillId: SkillId;
  skillName: string;
  masteryLevel: number; // 0-1
  confidenceScore: number; // 0-1
}

/**
 * Constraints for path generation.
 */
export interface PathConstraints {
  maxDurationMinutes?: number;
  maxSteps?: number;
  preferredDomains?: SimulationDomain[];
  excludeSimulationIds?: SimulationId[];
  difficultyRange?: {
    min: Difficulty;
    max: Difficulty;
  };
}

/**
 * Result from path generation.
 */
export interface GeneratedPathResult {
  path: LearningPath;
  confidence: number;
  alternativePaths?: LearningPath[];
  rationale: string;
}

// =============================================================================
// API Request/Response Types
// =============================================================================

/**
 * Request to list simulation steps for a user.
 */
export interface ListSimulationStepsRequest {
  tenantId: TenantId;
  userId: UserId;
  pathId?: LearningPathId;
  domain?: SimulationDomain;
  status?: StepProgressStatus;
  cursor?: string;
  limit?: number;
}

/**
 * Response containing simulation steps.
 */
export interface ListSimulationStepsResponse {
  steps: Array<SimulationLearningStep & { progress?: StepProgress }>;
  nextCursor: string | null;
  totalCount: number;
}

/**
 * Request to plan a path from diagnostic.
 */
export interface PlanFromDiagnosticRequest {
  tenantId: TenantId;
  userId: UserId;
  diagnosticResults: DiagnosticResult[];
  goals: string[];
  constraints?: PathConstraints;
}

/**
 * Response from path planning.
 */
export interface PlanFromDiagnosticResponse {
  path: LearningPath;
  simulationSteps: SimulationLearningStep[];
  estimatedDurationMinutes: number;
  confidence: number;
  rationale: string;
}

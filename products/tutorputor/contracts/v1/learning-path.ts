/**
 * Learning Path Type Definitions
 *
 * Defines adaptive learning paths and progression through learning units.
 *
 * @doc.type module
 * @doc.purpose Learning path and progression type definitions
 * @doc.layer contracts
 * @doc.pattern ValueObject
 */

// ============================================================================
// Branded IDs
// ============================================================================

export type LearningPathStepId = string & {
  readonly __brand: "LearningPathStepId";
};
export type SkillId = string & { readonly __brand: "SkillId" };

// ============================================================================
// Learning Path Types
// ============================================================================

export type Difficulty = "beginner" | "intermediate" | "advanced" | "expert";

export interface SimulationSkill {
  skillId: SkillId;
  name: string;
  level: number;
  weight: number;
}

export interface SimulationStepMetadata {
  title: string;
  description: string;
  estimatedDuration: number; // in minutes
  difficulty: Difficulty;
  prerequisites: LearningPathStepId[];
  tags: string[];
  keywords?: string[];
}

export interface SimulationCompletionCriteria {
  minStepsCompleted?: number;
  minParametersExplored?: number;
  minTimeSpent?: number; // in seconds
  minTimeSpentSeconds?: number; // in seconds
  minInteractions?: number;
  requiredOutcomes?: string[];
}

export interface SimulationLearningStep {
  id: LearningPathStepId;
  type: "simulation";
  simulationId: string;
  manifestId: string;
  domain: string;
  difficulty: Difficulty;
  skills: SimulationSkill[];
  prerequisites: Array<{
    stepId: string;
    type: string;
    minScore?: number;
  }>;
  estimatedTimeMinutes: number;
  assessmentRefs: Array<{
    assessmentId: string;
    position: string;
    required: boolean;
  }>;
  metadata: SimulationStepMetadata;
  completionCriteria: SimulationCompletionCriteria;
}

export interface LearningPathNode {
  id: string;
  learningUnitId: string;
  difficulty: Difficulty;
  prerequisites: string[];
  estimatedDuration: number; // in minutes
  skills: Array<{
    name: string;
    level: number;
  }>;
}

export interface LearningPath {
  id: string;
  name: string;
  description: string;
  nodes: LearningPathNode[];
  targetSkills: string[];
  createdAt: Date;
  updatedAt: Date;
}

export interface LearningPathProgress {
  pathId: string;
  learnerId: string;
  completedNodes: string[];
  currentNode: string | null;
  startedAt: Date;
  lastActivityAt: Date;
}

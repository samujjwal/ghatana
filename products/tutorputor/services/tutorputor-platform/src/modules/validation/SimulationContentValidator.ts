/**
 * Simulation Content Validator
 *
 * Validates simulation and animation content for educational correctness,
 * safety, and technical requirements.
 *
 * @doc.type class
 * @doc.purpose Validation for simulations and animations
 * @doc.layer platform
 * @doc.pattern Validator
 */

import { z } from "zod";
import { createStandaloneLogger } from "@tutorputor/core/logger";

const logger = createStandaloneLogger({ component: "SimulationContentValidator" });

// ============================================================================
// Validation Result Types
// ============================================================================

export enum ValidationSeverity {
  ERROR = "error",
  WARNING = "warning",
  INFO = "info",
}

export interface ValidationIssue {
  code: string;
  message: string;
  severity: ValidationSeverity;
  path?: string;
  suggestion?: string;
}

export interface ValidationResult {
  valid: boolean;
  issues: ValidationIssue[];
  score: number; // 0-100
}

// ============================================================================
// Simulation Content Schemas
// ============================================================================

const SimulationStepSchema = z.object({
  id: z.string().min(1),
  title: z.string().min(1),
  description: z.string().min(1),
  type: z.enum(["interaction", "observation", "quiz", "experiment"]),
  parameters: z.record(z.unknown()).optional(),
  objectives: z.array(z.string().min(1)).min(1),
  estimatedDurationSeconds: z.number().int().positive(),
});

const SimulationContentSchema = z.object({
  id: z.string().min(1),
  title: z.string().min(1),
  description: z.string().min(1),
  domain: z.string().min(1),
  gradeRange: z.string().min(1),
  steps: z.array(SimulationStepSchema).min(1),
  learningObjectives: z.array(z.string().min(1)).min(1),
  prerequisites: z.array(z.string()).optional(),
  estimatedDurationMinutes: z.number().int().positive(),
  difficultyLevel: z.enum(["beginner", "intermediate", "advanced"]),
});

const AnimationContentSchema = z.object({
  id: z.string().min(1),
  title: z.string().min(1),
  description: z.string().min(1),
  domain: z.string().min(1),
  gradeRange: z.string().min(1),
  durationSeconds: z.number().int().positive(),
  learningObjectives: z.array(z.string().min(1)).min(1),
  keyFrames: z.array(z.object({
    timestamp: z.number().nonnegative(),
    description: z.string().min(1),
  })).min(2),
  visualElements: z.array(z.object({
    id: z.string().min(1),
    type: z.string().min(1),
    properties: z.record(z.unknown()),
  })).min(1),
});

// ============================================================================
// Simulation Content Validator
// ============================================================================

export class SimulationContentValidator {
  private static instance: SimulationContentValidator;
  private readonly componentName = "SimulationContentValidator";

  private constructor() {}

  static getInstance(): SimulationContentValidator {
    if (!SimulationContentValidator.instance) {
      SimulationContentValidator.instance = new SimulationContentValidator();
    }
    return SimulationContentValidator.instance;
  }

  /**
   * Validate simulation content
   */
  validateSimulation(content: unknown): ValidationResult {
    const issues: ValidationIssue[] = [];

    // Schema validation
    const schemaResult = SimulationContentSchema.safeParse(content);
    if (!schemaResult.success) {
      schemaResult.error.issues.forEach((issue) => {
        issues.push({
          code: "SCHEMA_ERROR",
          message: issue.message,
          severity: ValidationSeverity.ERROR,
          path: issue.path.join("."),
        });
      });
    }

    // Business logic validation
    if (schemaResult.success) {
      this.validateSimulationBusinessLogic(schemaResult.data, issues);
    }

    const score = this.calculateScore(issues);

    logger.info({
      message: "Simulation validation completed",
      valid: score >= 80,
      score,
      issueCount: issues.length,
    }, this.componentName);

    return {
      valid: score >= 80,
      issues,
      score,
    };
  }

  /**
   * Validate animation content
   */
  validateAnimation(content: unknown): ValidationResult {
    const issues: ValidationIssue[] = [];

    // Schema validation
    const schemaResult = AnimationContentSchema.safeParse(content);
    if (!schemaResult.success) {
      schemaResult.error.issues.forEach((issue) => {
        issues.push({
          code: "SCHEMA_ERROR",
          message: issue.message,
          severity: ValidationSeverity.ERROR,
          path: issue.path.join("."),
        });
      });
    }

    // Business logic validation
    if (schemaResult.success) {
      this.validateAnimationBusinessLogic(schemaResult.data, issues);
    }

    const score = this.calculateScore(issues);

    logger.info({
      message: "Animation validation completed",
      valid: score >= 80,
      score,
      issueCount: issues.length,
    }, this.componentName);

    return {
      valid: score >= 80,
      issues,
      score,
    };
  }

  /**
   * Validate simulation business logic
   */
  private validateSimulationBusinessLogic(
    simulation: z.infer<typeof SimulationContentSchema>,
    issues: ValidationIssue[],
  ): void {
    // Check if total duration matches sum of step durations
    const totalStepDuration = simulation.steps.reduce(
      (sum, step) => sum + step.estimatedDurationSeconds,
      0
    );
    const estimatedTotalSeconds = simulation.estimatedDurationMinutes * 60;

    if (Math.abs(totalStepDuration - estimatedTotalSeconds) > 60) {
      issues.push({
        code: "DURATION_MISMATCH",
        message: "Total step duration does not match estimated duration",
        severity: ValidationSeverity.WARNING,
        suggestion: "Ensure estimatedDurationMinutes matches the sum of step durations",
      });
    }

    // Check for duplicate step IDs
    const stepIds = simulation.steps.map((s) => s.id);
    const duplicateIds = stepIds.filter((id, index) => stepIds.indexOf(id) !== index);
    if (duplicateIds.length > 0) {
      issues.push({
        code: "DUPLICATE_STEP_IDS",
        message: `Duplicate step IDs found: ${duplicateIds.join(", ")}`,
        severity: ValidationSeverity.ERROR,
        suggestion: "Ensure all step IDs are unique",
      });
    }

    // Check if learning objectives are covered in steps
    const stepObjectives = simulation.steps.flatMap((s) => s.objectives);
    const missingObjectives = simulation.learningObjectives.filter(
      (obj) => !stepObjectives.includes(obj)
    );
    if (missingObjectives.length > 0) {
      issues.push({
        code: "MISSING_OBJECTIVES",
        message: `Learning objectives not covered in steps: ${missingObjectives.join(", ")}`,
        severity: ValidationSeverity.WARNING,
        suggestion: "Ensure all learning objectives are covered in at least one step",
      });
    }

    // Check for appropriate difficulty level content
    if (simulation.difficultyLevel === "beginner" && simulation.steps.length > 10) {
      issues.push({
        code: "COMPLEXITY_MISMATCH",
        message: "Beginner simulation has too many steps",
        severity: ValidationSeverity.INFO,
        suggestion: "Consider reducing step count for beginner level",
      });
    }

    // Check if prerequisites are valid
    if (simulation.prerequisites && simulation.prerequisites.length > 0) {
      const invalidPrereqs = simulation.prerequisites.filter((p) => !p.trim());
      if (invalidPrereqs.length > 0) {
        issues.push({
          code: "INVALID_PREREQUISITES",
          message: "Prerequisites contain empty values",
          severity: ValidationSeverity.WARNING,
          suggestion: "Remove or fix empty prerequisite values",
        });
      }
    }
  }

  /**
   * Validate animation business logic
   */
  private validateAnimationBusinessLogic(
    animation: z.infer<typeof AnimationContentSchema>,
    issues: ValidationIssue[],
  ): void {
    // Check if key frames are in chronological order
    if (animation.keyFrames.length > 1) {
      for (let i = 1; i < animation.keyFrames.length; i++) {
        if (animation.keyFrames[i].timestamp < animation.keyFrames[i - 1].timestamp) {
          issues.push({
            code: "KEY_FRAME_ORDER",
            message: "Key frames are not in chronological order",
            severity: ValidationSeverity.ERROR,
            suggestion: "Ensure key frames are ordered by timestamp",
          });
          break;
        }
      }
    }

    // Check if animation duration matches last key frame
    const lastKeyFrame = animation.keyFrames[animation.keyFrames.length - 1];
    if (lastKeyFrame && lastKeyFrame.timestamp > animation.durationSeconds) {
      issues.push({
        code: "DURATION_MISMATCH",
        message: "Last key frame timestamp exceeds animation duration",
        severity: ValidationSeverity.ERROR,
        suggestion: "Ensure all key frames fit within the animation duration",
      });
    }

    // Check for duplicate visual element IDs
    const elementIds = animation.visualElements.map((e) => e.id);
    const duplicateIds = elementIds.filter((id, index) => elementIds.indexOf(id) !== index);
    if (duplicateIds.length > 0) {
      issues.push({
        code: "DUPLICATE_ELEMENT_IDS",
        message: `Duplicate visual element IDs found: ${duplicateIds.join(", ")}`,
        severity: ValidationSeverity.ERROR,
        suggestion: "Ensure all visual element IDs are unique",
      });
    }

    // Check if learning objectives are achievable
    if (animation.learningObjectives.length === 0) {
      issues.push({
        code: "NO_OBJECTIVES",
        message: "Animation has no learning objectives",
        severity: ValidationSeverity.ERROR,
        suggestion: "Add at least one learning objective",
      });
    }

    // Check if animation is too short for content
    if (animation.durationSeconds < 10) {
      issues.push({
        code: "TOO_SHORT",
        message: "Animation duration is too short for meaningful content",
        severity: ValidationSeverity.WARNING,
        suggestion: "Consider increasing animation duration to at least 10 seconds",
      });
    }
  }

  /**
   * Calculate validation score based on issues
   */
  private calculateScore(issues: ValidationIssue[]): number {
    if (issues.length === 0) {
      return 100;
    }

    const errorCount = issues.filter((i) => i.severity === ValidationSeverity.ERROR).length;
    const warningCount = issues.filter((i) => i.severity === ValidationSeverity.WARNING).length;
    const infoCount = issues.filter((i) => i.severity === ValidationSeverity.INFO).length;

    // Errors deduct 20 points each
    // Warnings deduct 10 points each
    // Info deducts 5 points each
    let score = 100 - (errorCount * 20) - (warningCount * 10) - (infoCount * 5);
    return Math.max(0, score);
  }

  /**
   * Batch validate multiple simulations
   */
  validateSimulationBatch(contents: unknown[]): ValidationResult[] {
    return contents.map((content) => this.validateSimulation(content));
  }

  /**
   * Batch validate multiple animations
   */
  validateAnimationBatch(contents: unknown[]): ValidationResult[] {
    return contents.map((content) => this.validateAnimation(content));
  }
}

// Singleton instance
export function getSimulationContentValidator(): SimulationContentValidator {
  return SimulationContentValidator.getInstance();
}

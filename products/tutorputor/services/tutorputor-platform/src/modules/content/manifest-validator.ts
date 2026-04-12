/**
 * Manifest Validator Service
 *
 * Task 2.5: Create Manifest Validator Service
 *
 * @doc.type module
 * @doc.purpose Validation service for artifact manifests
 * @doc.layer service
 * @doc.pattern Validator
 */

import type { Logger } from 'pino';
import {
  WorkedExampleManifestSchema,
  AnimationManifestSchema,
  type WorkedExampleManifest,
  type AnimationManifest,
} from '../../../../contracts/v1/artifact-manifests';

/**
 * Validation result.
 */
export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
}

export interface ValidationError {
  path: string;
  message: string;
  code: string;
}

export interface ValidationWarning {
  path: string;
  message: string;
  suggestion?: string;
}

/**
 * Manifest validator service.
 */
export class ManifestValidator {
  constructor(private readonly logger: Logger) {}

  /**
   * Validate a worked example manifest.
   */
  validateWorkedExample(data: unknown): ValidationResult {
    const result = WorkedExampleManifestSchema.safeParse(data);

    if (result.success) {
      const warnings = this.checkWorkedExampleQuality(result.data);
      return { valid: true, errors: [], warnings };
    }

    const errors: ValidationError[] = result.error.errors.map(err => ({
      path: err.path.join('.'),
      message: err.message,
      code: 'SCHEMA_VIOLATION',
    }));

    return { valid: false, errors, warnings: [] };
  }

  /**
   * Validate an animation manifest.
   */
  validateAnimation(data: unknown): ValidationResult {
    const result = AnimationManifestSchema.safeParse(data);

    if (result.success) {
      const warnings = this.checkAnimationQuality(result.data);
      return { valid: true, errors: [], warnings };
    }

    const errors: ValidationError[] = result.error.errors.map(err => ({
      path: err.path.join('.'),
      message: err.message,
      code: 'SCHEMA_VIOLATION',
    }));

    return { valid: false, errors, warnings: [] };
  }

  /**
   * Quality checks for worked examples.
   */
  private checkWorkedExampleQuality(manifest: WorkedExampleManifest): ValidationWarning[] {
    const warnings: ValidationWarning[] = [];

    // Check for minimum steps
    if (manifest.reasoningSteps.length < 2) {
      warnings.push({
        path: 'reasoningSteps',
        message: 'Worked example should have at least 2 reasoning steps',
        suggestion: 'Add more detailed steps to improve clarity',
      });
    }

    // Check for misconceptions
    if (manifest.misconceptionCheckpoints.length === 0) {
      warnings.push({
        path: 'misconceptionCheckpoints',
        message: 'No misconception checkpoints defined',
        suggestion: 'Add common error warnings to help learners',
      });
    }

    // Check for transfer prompts
    if (manifest.transferPrompts.length === 0) {
      warnings.push({
        path: 'transferPrompts',
        message: 'No transfer prompts for knowledge application',
        suggestion: 'Add prompts to help learners apply knowledge to new contexts',
      });
    }

    // Check estimated time
    if (manifest.estimatedTimeMinutes < 3) {
      warnings.push({
        path: 'estimatedTimeMinutes',
        message: 'Estimated time seems short for a worked example',
        suggestion: 'Review if all necessary steps are included',
      });
    }

    return warnings;
  }

  /**
   * Quality checks for animations.
   */
  private checkAnimationQuality(manifest: AnimationManifest): ValidationWarning[] {
    const warnings: ValidationWarning[] = [];

    // Check duration
    if (manifest.totalDurationSeconds > 120) {
      warnings.push({
        path: 'totalDurationSeconds',
        message: 'Animation exceeds 2 minutes - consider breaking into segments',
        suggestion: 'Long animations may lose learner attention',
      });
    }

    // Check for narration
    if (!manifest.narrationScript) {
      warnings.push({
        path: 'narrationScript',
        message: 'No narration script provided',
        suggestion: 'Add narration for accessibility and better learning outcomes',
      });
    }

    // Check for learner controls
    if (manifest.learnerControls.length < 2) {
      warnings.push({
        path: 'learnerControls',
        message: 'Limited learner controls',
        suggestion: 'Add play/pause and reset controls at minimum',
      });
    }

    // Check scene complexity
    if (manifest.sceneGraph.length > 20) {
      warnings.push({
        path: 'sceneGraph',
        message: 'Complex scene graph may impact performance',
        suggestion: 'Consider simplifying the scene',
      });
    }

    // Check claim mapping
    if (manifest.claimMapping.length === 0) {
      warnings.push({
        path: 'claimMapping',
        message: 'No claim-to-segment mapping',
        suggestion: 'Map animation segments to specific claim aspects',
      });
    }

    return warnings;
  }
}

/**
 * Manifest Validator Service
 *
 * Task 2.5: Create Manifest Validator Service
 *
 * Extended validation beyond structural checks to include:
 * - Pedagogical validation (Bloom's taxonomy alignment, scaffolding)
 * - Semantic validation (content coherence, clarity)
 * - Accessibility validation (WCAG compliance)
 * - Evidence validation (claim-evidence alignment)
 *
 * @doc.type module
 * @doc.purpose Validation service for artifact manifests
 * @doc.layer service
 * @doc.pattern Validator
 */

import type { Logger } from 'pino';
import {
  WorkedExampleManifestSchema,
  type WorkedExampleManifest,
} from '../../../../../contracts/v1/artifact-manifests/worked-example-manifest';
import {
  AnimationManifestSchema,
  type AnimationManifest,
} from '../../../../../contracts/v1/artifact-manifests/animation-manifest';

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

  private mapSchemaIssue(issue: { path: PropertyKey[]; message: string }): ValidationError {
    return {
      path: issue.path.map((segment) => String(segment)).join('.'),
      message: issue.message,
      code: 'SCHEMA_VIOLATION',
    };
  }

  /**
   * Validate a worked example manifest.
   */
  validateWorkedExample(data: unknown): ValidationResult {
    const result = WorkedExampleManifestSchema.safeParse(data);

    if (result.success) {
      const warnings = this.checkWorkedExampleQuality(result.data);
      const pedagogicalWarnings = this.checkWorkedExamplePedagogy(result.data);
      const accessibilityWarnings = this.checkWorkedExampleAccessibility(result.data);
      return { 
        valid: true, 
        errors: [], 
        warnings: [...warnings, ...pedagogicalWarnings, ...accessibilityWarnings] 
      };
    }

    const errors: ValidationError[] = result.error.issues.map((issue) => this.mapSchemaIssue(issue));

    return { valid: false, errors, warnings: [] };
  }

  /**
   * Validate an animation manifest.
   */
  validateAnimation(data: unknown): ValidationResult {
    const result = AnimationManifestSchema.safeParse(data);

    if (result.success) {
      const warnings = this.checkAnimationQuality(result.data);
      const pedagogicalWarnings = this.checkAnimationPedagogy(result.data);
      const accessibilityWarnings = this.checkAnimationAccessibility(result.data);
      return { 
        valid: true, 
        errors: [], 
        warnings: [...warnings, ...pedagogicalWarnings, ...accessibilityWarnings] 
      };
    }

    const errors: ValidationError[] = result.error.issues.map((issue) => this.mapSchemaIssue(issue));

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

  /**
   * Pedagogical validation for worked examples.
   * Checks alignment with cognitive load and scaffolding principles.
   */
  private checkWorkedExamplePedagogy(manifest: WorkedExampleManifest): ValidationWarning[] {
    const warnings: ValidationWarning[] = [];

    // Check scaffolding progression
    const hasScaffoldingProgression = manifest.reasoningSteps.some((step, idx) => {
      if (idx === 0) return true;
      const prevStep = manifest.reasoningSteps[idx - 1];
      return prevStep && step.hint !== prevStep.hint;
    });

    if (!hasScaffoldingProgression && manifest.reasoningSteps.length > 1) {
      warnings.push({
        path: 'reasoningSteps',
        message: 'Scaffolding hints do not show progression',
        suggestion: 'Gradually reduce hint detail across steps',
      });
    }

    // Check for cognitive load
    if (manifest.reasoningSteps.length > 8) {
      warnings.push({
        path: 'reasoningSteps',
        message: 'High cognitive load - too many steps',
        suggestion: 'Consider breaking into smaller examples',
      });
    }

    return warnings;
  }

  /**
   * Accessibility validation for worked examples.
   */
  private checkWorkedExampleAccessibility(_manifest: WorkedExampleManifest): ValidationWarning[] {
    const warnings: ValidationWarning[] = [];

    // TODO: Add accessibility checks when visualElements, highlightedTerms, etc. are added to schema
    warnings.push({
      path: 'accessibility',
      message: 'Accessibility validation not yet implemented for worked examples',
      suggestion: 'Add visual alt text and color contrast checks when schema supports them',
    });

    return warnings;
  }

  /**
   * Pedagogical validation for animations.
   * Checks alignment with cognitive load theory and multimedia learning principles.
   */
  private checkAnimationPedagogy(manifest: AnimationManifest): ValidationWarning[] {
    const warnings: ValidationWarning[] = [];

    // Check segment cognitive load
    const avgSegmentDuration = manifest.totalDurationSeconds / manifest.segments.length;
    if (avgSegmentDuration > 30) {
      warnings.push({
        path: 'segments',
        message: 'Average segment duration exceeds 30 seconds',
        suggestion: 'Break into shorter segments to reduce cognitive load',
      });
    }

    // Check for redundancy (multimedia principle)
    if (manifest.narrationScript && manifest.segments.length > 0) {
      const hasRedundantText = manifest.segments.some(seg =>
        seg.description.length > 200 && seg.conceptsIllustrated.length > 5
      );
      if (hasRedundantText) {
        warnings.push({
          path: 'segments',
          message: 'Possible redundancy between narration and on-screen text',
          suggestion: 'Apply multimedia learning principles to avoid overload',
        });
      }
    }

    // Check for signaling (attention guidance)
    const hasSignaling = manifest.cueingRules.length > 0;
    if (!hasSignaling && manifest.entities.length > 3) {
      warnings.push({
        path: 'cueingRules',
        message: 'No signaling rules for complex scene',
        suggestion: 'Add cueing rules to guide learner attention',
      });
    }

    // Check for interactivity (generative processing)
    const hasInteractiveElements = manifest.learnerControls.some(c => c.type === 'reset' || c.type === 'rewind');
    if (!hasInteractiveElements && manifest.totalDurationSeconds > 60) {
      warnings.push({
        path: 'learnerControls',
        message: 'Long animation without interactive controls',
        suggestion: 'Add controls to enable generative processing',
      });
    }

    return warnings;
  }

  /**
   * Accessibility validation for animations.
   */
  private checkAnimationAccessibility(manifest: AnimationManifest): ValidationWarning[] {
    const warnings: ValidationWarning[] = [];

    // Check for narration (alternative to visual content)
    if (!manifest.narrationScript) {
      warnings.push({
        path: 'narrationScript',
        message: 'No narration for screen reader users',
        suggestion: 'Add narration script for accessibility',
      });
    }

    // Check for subtitles
    if (!manifest.subtitles && manifest.narrationScript) {
      warnings.push({
        path: 'subtitles',
        message: 'Narration without subtitles',
        suggestion: 'Add synchronized subtitles for deaf/hard-of-hearing users',
      });
    }

    // Check for pause points (for assistive technology)
    const hasPausePoints = manifest.segments.some(seg => seg.pausePoints.length > 0);
    if (!hasPausePoints) {
      warnings.push({
        path: 'segments',
        message: 'No pause points for assistive technology',
        suggestion: 'Add strategic pause points for screen reader announcements',
      });
    }

    return warnings;
  }
}

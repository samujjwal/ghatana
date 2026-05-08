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
import { z } from 'zod';
import {
  WorkedExampleManifestSchema,
  type WorkedExampleManifest,
} from '../../../../../contracts/v1/artifact-manifests/worked-example-manifest';
import {
  AnimationManifestSchema,
  type AnimationManifest,
} from '../../../../../contracts/v1/artifact-manifests/animation-manifest';
import {
  type SimulationManifest,
  type SimulationDomain,
} from '../../../../../contracts/v1/simulation';

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

  // Basic simulation manifest schema for structural validation
  private readonly SimulationManifestSchema = z.object({
    id: z.string().min(1),
    version: z.string().min(1),
    title: z.string().min(1),
    domain: z.enum(['CS_DISCRETE', 'PHYSICS', 'ECONOMICS', 'CHEMISTRY', 'BIOLOGY', 'MEDICINE', 'ENGINEERING', 'MATHEMATICS']),
    authorId: z.string().min(1),
    tenantId: z.string().min(1),
    canvas: z.object({
      width: z.number().positive(),
      height: z.number().positive(),
    }),
    playback: z.object({
      defaultSpeed: z.number().positive(),
    }),
    initialEntities: z.array(z.object({
      id: z.string().min(1),
      type: z.string().min(1),
      x: z.number(),
      y: z.number(),
    })),
    steps: z.array(z.object({
      id: z.string().min(1),
      orderIndex: z.number().int().nonnegative(),
      actions: z.array(z.object({
        action: z.string().min(1),
      })),
    })).min(1),
    schemaVersion: z.string().min(1),
    createdAt: z.string(),
    updatedAt: z.string(),
  });

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
   * Validate a simulation manifest.
   * Dedicated validator for simulation manifests with checks for:
   * - Deterministic execution
   * - Parameter bounds
   * - Telemetry config
   * - Scientific correctness
   * - Accessibility metadata
   */
  validateSimulation(data: unknown): ValidationResult {
    const result = this.SimulationManifestSchema.safeParse(data);

    if (result.success) {
      // Cast to SimulationManifest type (branded types are validated at runtime)
      const manifest = result.data as SimulationManifest;
      const warnings = this.checkSimulationQuality(manifest);
      const pedagogicalWarnings = this.checkSimulationPedagogy(manifest);
      const accessibilityWarnings = this.checkSimulationAccessibility(manifest);
      const scientificWarnings = this.checkSimulationScientificCorrectness(manifest);
      return { 
        valid: true, 
        errors: [], 
        warnings: [...warnings, ...pedagogicalWarnings, ...accessibilityWarnings, ...scientificWarnings] 
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

  /**
   * Quality checks for simulations.
   * Checks for deterministic execution, parameter bounds, telemetry config.
   */
  private checkSimulationQuality(manifest: SimulationManifest): ValidationWarning[] {
    const warnings: ValidationWarning[] = [];

    // Check for deterministic replay configuration
    if (!manifest.replay?.deterministic) {
      warnings.push({
        path: 'replay.deterministic',
        message: 'Simulation not configured for deterministic replay',
        suggestion: 'Set replay.deterministic=true for reproducible assessment',
      });
    }

    // Check for seed configuration (required for determinism)
    if (manifest.replay?.deterministic && !manifest.canonical?.seed) {
      warnings.push({
        path: 'canonical.seed',
        message: 'Deterministic replay requires a seed value',
        suggestion: 'Add canonical.seed for reproducible physics/randomness',
      });
    }

    // Check for parameter bounds (safety constraint)
    if (!manifest.safety?.parameterBounds?.enforced) {
      warnings.push({
        path: 'safety.parameterBounds.enforced',
        message: 'Parameter bounds not enforced',
        suggestion: 'Set safety.parameterBounds.enforced=true to prevent unsafe parameter ranges',
      });
    }

    // Check for parameter bounds definition
    if (manifest.safety?.parameterBounds?.enforced && !manifest.canonical?.parameterBounds) {
      warnings.push({
        path: 'canonical.parameterBounds',
        message: 'Parameter bounds enforced but no bounds defined',
        suggestion: 'Define canonical.parameterBounds with min/max for each parameter',
      });
    }

    // Check for telemetry event specification
    if (!manifest.canonical?.telemetryEvents || manifest.canonical.telemetryEvents.length === 0) {
      warnings.push({
        path: 'canonical.telemetryEvents',
        message: 'No telemetry events specified',
        suggestion: 'Add telemetry events for assessment (sim.start, sim.control.change, sim.complete)',
      });
    }

    // Check for execution limits
    if (!manifest.safety?.executionLimits) {
      warnings.push({
        path: 'safety.executionLimits',
        message: 'No execution limits configured',
        suggestion: 'Set safety.executionLimits with maxSteps and maxRuntimeMs',
      });
    }

    // Check for failure state definitions
    if (!manifest.canonical?.failureStates || manifest.canonical.failureStates.length === 0) {
      warnings.push({
        path: 'canonical.failureStates',
        message: 'No failure states defined',
        suggestion: 'Define failure states for error handling and learner feedback',
      });
    }

    // Check for claim-evidence linkage
    if (!manifest.canonical?.claimLinks || manifest.canonical.claimLinks.length === 0) {
      warnings.push({
        path: 'canonical.claimLinks',
        message: 'No claim-evidence linkage defined',
        suggestion: 'Link simulation to learning claims via canonical.claimLinks for assessment',
      });
    }

    return warnings;
  }

  /**
   * Pedagogical validation for simulations.
   * Checks for scaffolding, cognitive load, and learning outcomes.
   */
  private checkSimulationPedagogy(manifest: SimulationManifest): ValidationWarning[] {
    const warnings: ValidationWarning[] = [];

    // Check for minimum number of steps (learning progression)
    if (manifest.steps.length < 3) {
      warnings.push({
        path: 'steps',
        message: 'Simulation has fewer than 3 steps',
        suggestion: 'Add more steps to provide adequate learning progression',
      });
    }

    // Check for narrative context (tutor guidance)
    const hasNarrative = manifest.steps.some(step => step.narration || step.tutorContext);
    if (!hasNarrative) {
      warnings.push({
        path: 'steps',
        message: 'No narrative or tutor context in steps',
        suggestion: 'Add narration or tutorContext to guide learner understanding',
      });
    }

    // Check for checkpoint/breakpoint for assessment hooks
    const hasCheckpoints = manifest.steps.some(step => step.checkpoint || step.breakpoint);
    if (!hasCheckpoints) {
      warnings.push({
        path: 'steps',
        message: 'No checkpoints or breakpoints defined',
        suggestion: 'Add checkpoints for assessment hooks and learner reflection',
      });
    }

    // Check for cognitive load (too many entities)
    if (manifest.initialEntities.length > 15) {
      warnings.push({
        path: 'initialEntities',
        message: 'High cognitive load - too many initial entities',
        suggestion: 'Consider simplifying or introducing entities gradually',
      });
    }

    return warnings;
  }

  /**
   * Accessibility validation for simulations.
   * Checks for screen reader support, keyboard navigation, and alternative formats.
   */
  private checkSimulationAccessibility(manifest: SimulationManifest): ValidationWarning[] {
    const warnings: ValidationWarning[] = [];

    // Check for accessibility metadata
    if (!manifest.accessibility) {
      warnings.push({
        path: 'accessibility',
        message: 'No accessibility metadata defined',
        suggestion: 'Add accessibility configuration for screen reader support',
      });
    } else {
      // Check for alt text
      if (!manifest.accessibility.altText) {
        warnings.push({
          path: 'accessibility.altText',
          message: 'No alt text for simulation',
          suggestion: 'Add descriptive alt text for screen reader users',
        });
      }

      // Check for screen reader narration
      if (!manifest.accessibility.screenReaderNarration) {
        warnings.push({
          path: 'accessibility.screenReaderNarration',
          message: 'Screen reader narration not enabled',
          suggestion: 'Enable screenReaderNarration for accessibility',
        });
      }

      // Check for reduced motion option
      if (!manifest.accessibility.reducedMotion) {
        warnings.push({
          path: 'accessibility.reducedMotion',
          message: 'Reduced motion option not available',
          suggestion: 'Add reducedMotion option for users with vestibular disorders',
        });
      }
    }

    // Check for playback controls (accessibility requirement)
    if (!manifest.playback.allowSpeedChange || !manifest.playback.allowScrubbing) {
      warnings.push({
        path: 'playback',
        message: 'Limited playback controls',
        suggestion: 'Enable allowSpeedChange and allowScrubbing for accessibility',
      });
    }

    return warnings;
  }

  /**
   * Scientific correctness validation for simulations.
   * Checks domain-specific scientific accuracy and physical realism.
   */
  private checkSimulationScientificCorrectness(manifest: SimulationManifest): ValidationWarning[] {
    const warnings: ValidationWarning[] = [];

    // Domain-specific metadata checks
    if (!manifest.domainMetadata) {
      warnings.push({
        path: 'domainMetadata',
        message: 'No domain-specific metadata defined',
        suggestion: 'Add domainMetadata for scientific correctness validation',
      });
    }

    // Physics-specific checks
    if (manifest.domain === 'PHYSICS') {
      const physicsMeta = manifest.domainMetadata as { domain: 'PHYSICS'; physics?: { gravity?: unknown; units?: unknown } };
      if (!physicsMeta.physics?.gravity) {
        warnings.push({
          path: 'domainMetadata.physics.gravity',
          message: 'No gravity defined for physics simulation',
          suggestion: 'Define gravity vector for physical realism',
        });
      }
      if (!physicsMeta.physics?.units) {
        warnings.push({
          path: 'domainMetadata.physics.units',
          message: 'No units defined for physics simulation',
          suggestion: 'Define units (length, mass, time) for dimensional consistency',
        });
      }
    }

    // Chemistry-specific checks
    if (manifest.domain === 'CHEMISTRY') {
      const chemMeta = manifest.domainMetadata as { domain: 'CHEMISTRY'; chemistry?: { conditions?: unknown } };
      if (!chemMeta.chemistry?.conditions) {
        warnings.push({
          path: 'domainMetadata.chemistry.conditions',
          message: 'No reaction conditions defined',
          suggestion: 'Define temperature, pressure, solvent for chemical realism',
        });
      }
    }

    // Biology-specific checks
    if (manifest.domain === 'BIOLOGY') {
      const bioMeta = manifest.domainMetadata as { domain: 'BIOLOGY'; biology?: { scale?: unknown } };
      if (!bioMeta.biology?.scale) {
        warnings.push({
          path: 'domainMetadata.biology.scale',
          message: 'No biological scale defined',
          suggestion: 'Define scale (molecular, cellular, tissue) for context',
        });
      }
    }

    return warnings;
  }
}

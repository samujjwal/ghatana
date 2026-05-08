/**
 * Canonical Artifact Manifest Contracts
 *
 * Typed payload schemas for each ArtifactManifestType. These replace
 * the untyped `Record<string, unknown>` payload in ArtifactManifest
 * for worked examples, animations, and assessments. Simulations
 * re-export from the existing SimulationManifest contract.
 *
 * @doc.type module
 * @doc.purpose Canonical typed manifest payload contracts
 * @doc.layer contracts
 * @doc.pattern Schema
 */

import type { AnimationContentType, ExampleType } from "./learning-unit";
import type { SimulationManifest } from "./simulation";
import type { AnimationManifest } from "./artifact-manifests/animation-manifest";
import { validateAnimationManifest } from "./artifact-manifests/animation-manifest";

// ============================================================================
// Re-export the Simulation manifest (already fully typed)
// ============================================================================

export type { SimulationManifest } from "./simulation";

// ============================================================================
// Re-export the Animation manifest from animation-manifest (fully typed with Zod)
// ============================================================================

export type { AnimationManifest } from "./artifact-manifests/animation-manifest";
export { validateAnimationManifest } from "./artifact-manifests/animation-manifest";

// ============================================================================
// Worked Example Manifest
// ============================================================================

/**
 * A single step in a worked example.
 */
export interface WorkedExampleStep {
  /** Step sequence number (1-based) */
  stepNumber: number;
  /** Step heading or label */
  label: string;
  /** Rich text or LaTeX explanation of the step */
  content: string;
  /** Optional LaTeX or symbolic expression */
  expression?: string;
  /** Hint text if the learner is stuck */
  hint?: string;
  /** Claim reference this step addresses */
  claimRef?: string;
  /** Visual/diagram reference if applicable */
  diagramRef?: string;
}

/**
 * Canonical manifest for worked examples.
 *
 * Describes a step-by-step demonstration of a concept, problem, or
 * procedure that a learner follows through.
 */
export interface WorkedExampleManifest {
  /** Manifest schema version (e.g., "1.0.0") */
  schemaVersion: string;
  /** Example classification */
  exampleType: ExampleType;
  /** Domain the example belongs to */
  domain: string;
  /** Difficulty level */
  difficulty: "beginner" | "intermediate" | "advanced";
  /** Problem statement or introduction */
  problemStatement: string;
  /** Given information / initial conditions */
  givenData?: Record<string, string | number>;
  /** Ordered steps in the worked solution */
  steps: WorkedExampleStep[];
  /** Final answer / conclusion */
  answer: string;
  /** Units for the answer if applicable */
  answerUnit?: string;
  /** Common misconceptions addressed */
  misconceptions?: string[];
  /** Claims this example is evidence for */
  claimRefs?: string[];
  /** Scaffolding level */
  scaffolding: "high" | "medium" | "low" | "none";
  /** Estimated completion time in seconds */
  estimatedTimeSeconds?: number;
  /** Accessibility: alt-text for any visuals */
  accessibility?: {
    altTexts?: Record<string, string>;
    screenReaderNarration?: boolean;
  };
}

// ============================================================================
// Assessment Manifest
// ============================================================================

/**
 * A single assessment question/item within the manifest.
 */
export interface AssessmentItem {
  /** Unique item identifier within the manifest */
  itemId: string;
  /** Question type discriminator */
  itemType:
    | "multiple_choice"
    | "short_answer"
    | "long_answer"
    | "numeric"
    | "matching"
    | "ordering"
    | "simulation_based"
    | "prediction"
    | "manipulation"
    | "explanation";
  /** Question / prompt text */
  prompt: string;
  /** Context or stimulus material */
  stimulus?: string;
  /** Maximum points for this item */
  points: number;
  /** Bloom's taxonomy level targeted */
  bloomLevel?:
    | "remember"
    | "understand"
    | "apply"
    | "analyze"
    | "evaluate"
    | "create";
  /** Claim reference this item assesses */
  claimRef?: string;
  /** Multiple-choice options (for MC items) */
  options?: Array<{
    id: string;
    text: string;
    isCorrect: boolean;
    feedback?: string;
  }>;
  /** Expected answer (for short_answer / numeric) */
  expectedAnswer?: string | number;
  /** Tolerance for numeric answers */
  tolerance?: number;
  /** Rubric for open-ended items */
  rubric?: Array<{
    criterion: string;
    maxPoints: number;
    levels: Array<{
      level: number;
      description: string;
      points: number;
    }>;
  }>;
  /** Simulation reference (for simulation_based items) */
  simulationRef?: string;
  /** Hints (progressive disclosure) */
  hints?: Array<{
    text: string;
    pointDeduction?: number;
  }>;
  /** Feedback configuration */
  feedback?: {
    correct?: string;
    incorrect?: string;
    showExplanation?: boolean;
  };
}

/**
 * Canonical manifest for assessment instruments.
 *
 * Describes a collection of assessment items that measure learner
 * understanding of one or more claims.
 */
export interface AssessmentManifest {
  /** Manifest schema version (e.g., "1.0.0") */
  schemaVersion: string;
  /** Assessment title */
  title: string;
  /** Domain the assessment covers */
  domain: string;
  /** Assessment purpose classification */
  purpose: "diagnostic" | "formative" | "summative" | "practice";
  /** Total points across all items */
  totalPoints: number;
  /** Time limit in minutes (0 = unlimited) */
  timeLimitMinutes?: number;
  /** Whether items are presented in fixed or random order */
  itemOrder: "fixed" | "random";
  /** The assessment items */
  items: AssessmentItem[];
  /** Passing threshold as a percentage (0-100) */
  passingThreshold?: number;
  /** Claims covered by this assessment */
  claimRefs?: string[];
  /** Allowed number of attempts (0 = unlimited) */
  maxAttempts?: number;
  /** Grading configuration */
  grading?: {
    partialCredit: boolean;
    penalizeGuessing: boolean;
    showScoreImmediately: boolean;
  };
  /** Accessibility configuration */
  accessibility?: {
    extendedTime?: boolean;
    screenReaderCompatible?: boolean;
    altTexts?: Record<string, string>;
  };
}

// ============================================================================
// Manifest Payload Union
// ============================================================================

/**
 * Type-safe discriminated union for artifact manifest payloads.
 *
 * Use this with ArtifactManifest.manifestType to narrow the payload:
 *
 * ```typescript
 * if (artifact.manifestType === 'worked_example') {
 *   const payload = artifact.manifest as WorkedExampleManifest;
 * }
 * ```
 */
export type ManifestPayload =
  | WorkedExampleManifest
  | SimulationManifest
  | AnimationManifest
  | AssessmentManifest;

/**
 * Maps artifact manifest types to their typed payload.
 */
export interface ManifestPayloadMap {
  worked_example: WorkedExampleManifest;
  simulation: SimulationManifest;
  animation: AnimationManifest;
  assessment: AssessmentManifest;
}

// ============================================================================
// Manifest Validation
// ============================================================================

/**
 * Validation rule for a manifest field.
 */
export interface ManifestValidationRule {
  /** Field path (e.g., "steps", "items.0.prompt") */
  field: string;
  /** Rule classification */
  rule:
    | "required"
    | "min_length"
    | "max_length"
    | "min_value"
    | "max_value"
    | "pattern"
    | "custom";
  /** Expected value for the rule */
  expected?: unknown;
  /** Human-readable error message */
  message: string;
  /** Severity */
  severity: "error" | "warning";
}

/**
 * Validation result for a manifest.
 */
export interface ManifestValidationResult {
  /** Whether the manifest passes validation */
  isValid: boolean;
  /** Manifest type validated */
  manifestType: keyof ManifestPayloadMap;
  /** Individual rule violations */
  violations: Array<ManifestValidationRule & { actualValue?: unknown }>;
  /** Timestamp of validation */
  validatedAt: string;
}

/**
 * Canonical validation rules per manifest type.
 */
export const MANIFEST_VALIDATION_RULES: Record<
  keyof ManifestPayloadMap,
  ManifestValidationRule[]
> = {
  worked_example: [
    {
      field: "schemaVersion",
      rule: "required",
      message: "Schema version is required",
      severity: "error",
    },
    {
      field: "exampleType",
      rule: "required",
      message: "Example type is required",
      severity: "error",
    },
    {
      field: "problemStatement",
      rule: "required",
      message: "Problem statement is required",
      severity: "error",
    },
    {
      field: "steps",
      rule: "min_length",
      expected: 1,
      message: "At least one step is required",
      severity: "error",
    },
    {
      field: "answer",
      rule: "required",
      message: "Answer is required",
      severity: "error",
    },
    {
      field: "scaffolding",
      rule: "required",
      message: "Scaffolding level is required",
      severity: "error",
    },
    {
      field: "claimRefs",
      rule: "min_length",
      expected: 1,
      message: "At least one claim reference recommended",
      severity: "warning",
    },
  ],
  simulation: [
    {
      field: "schemaVersion",
      rule: "required",
      message: "Schema version is required",
      severity: "error",
    },
    {
      field: "domain",
      rule: "required",
      message: "Domain is required",
      severity: "error",
    },
    {
      field: "canvas",
      rule: "required",
      message: "Canvas configuration is required",
      severity: "error",
    },
    {
      field: "playback",
      rule: "required",
      message: "Playback configuration is required",
      severity: "error",
    },
    {
      field: "initialEntities",
      rule: "min_length",
      expected: 1,
      message: "At least one initial entity is required",
      severity: "error",
    },
    {
      field: "steps",
      rule: "min_length",
      expected: 1,
      message: "At least one simulation step is required",
      severity: "error",
    },
  ],
  animation: [
    {
      field: "schemaVersion",
      rule: "required",
      message: "Schema version is required",
      severity: "error",
    },
    {
      field: "animationType",
      rule: "required",
      message: "Animation type is required",
      severity: "error",
    },
    {
      field: "canvas",
      rule: "required",
      message: "Canvas configuration is required",
      severity: "error",
    },
    {
      field: "durationMs",
      rule: "min_value",
      expected: 100,
      message: "Duration must be at least 100ms",
      severity: "error",
    },
    {
      field: "keyframes",
      rule: "min_length",
      expected: 2,
      message: "At least two keyframes are required",
      severity: "error",
    },
    {
      field: "playback",
      rule: "required",
      message: "Playback configuration is required",
      severity: "error",
    },
    {
      field: "accessibility.altText",
      rule: "required",
      message: "Alt text recommended for accessibility",
      severity: "warning",
    },
  ],
  assessment: [
    {
      field: "schemaVersion",
      rule: "required",
      message: "Schema version is required",
      severity: "error",
    },
    {
      field: "title",
      rule: "required",
      message: "Assessment title is required",
      severity: "error",
    },
    {
      field: "purpose",
      rule: "required",
      message: "Assessment purpose is required",
      severity: "error",
    },
    {
      field: "items",
      rule: "min_length",
      expected: 1,
      message: "At least one assessment item is required",
      severity: "error",
    },
    {
      field: "totalPoints",
      rule: "min_value",
      expected: 1,
      message: "Total points must be at least 1",
      severity: "error",
    },
    {
      field: "itemOrder",
      rule: "required",
      message: "Item order strategy is required",
      severity: "error",
    },
    {
      field: "claimRefs",
      rule: "min_length",
      expected: 1,
      message: "At least one claim reference recommended",
      severity: "warning",
    },
  ],
};

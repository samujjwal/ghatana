/**
 * WorkedExampleManifest Schema
 *
 * Task 2.1: Define WorkedExampleManifest Schema
 *
 * Based on research on worked examples (van Gog, Paas, Sweller)
 *
 * @doc.type module
 * @doc.purpose JSON Schema and TypeScript types for worked examples
 * @doc.layer contracts
 * @doc.pattern SchemaDefinition
 */

import { z } from 'zod';

// =============================================================================
// Core Types
// =============================================================================

export const ExampleFamily = z.enum([
  'real-world',
  'analogy',
  'worked-solution',
  'counterexample',
  'case-study',
]);

export const GivenSchema = z.object({
  id: z.string(),
  description: z.string(),
  value: z.union([z.string(), z.number(), z.boolean()]),
  unit: z.string().optional(),
});

export const ReasoningStepSchema = z.object({
  stepNumber: z.number().int().positive(),
  description: z.string(),
  visualAidRef: z.string().optional(),
  checkpoint: z.boolean().default(false),
  hint: z.string().optional(),
});

export const ExplanationStepSchema = z.object({
  stepNumber: z.number().int().positive(),
  content: z.string(),
  emphasizes: z.array(z.string()).optional(),
});

export const MisconceptionCheckpointSchema = z.object({
  id: z.string(),
  commonError: z.string(),
  warningSign: z.string(),
  correctiveGuidance: z.string(),
  relatedStepNumber: z.number().int().optional(),
});

export const TransferPromptSchema = z.object({
  id: z.string(),
  prompt: z.string(),
  expectedAnswer: z.string().optional(),
  hints: z.array(z.string()).optional(),
});

export const GradeAdaptationRuleSchema = z.object({
  gradeBand: z.string(),
  modifications: z.object({
    simplifyLanguage: z.boolean().optional(),
    addScaffolding: z.boolean().optional(),
    reduceSteps: z.boolean().optional(),
    addVisualAids: z.boolean().optional(),
  }),
});

export const EvaluationHintsSchema = z.object({
  correctIndicators: z.array(z.string()),
  misconceptionIndicators: z.array(z.string()),
  followUpQuestions: z.array(z.string()).optional(),
});

// =============================================================================
// Main Manifest Schema
// =============================================================================

export const WorkedExampleManifestSchema = z.object({
  schemaVersion: z.literal('1.0.0'),
  manifestType: z.literal('WorkedExample'),

  // Provenance
  claimRef: z.string(),
  evidenceRefs: z.array(z.string()),
  objectiveRefs: z.array(z.string()).optional(),

  // Context
  domain: z.string(),
  gradeBand: z.string(),
  pedagogicalIntent: z.string(),

  // Example family classification
  exampleFamily: ExampleFamily,

  // Core content
  learnerGoal: z.string(),
  givens: z.array(GivenSchema),
  reasoningSteps: z.array(ReasoningStepSchema).min(1),
  explanationSteps: z.array(ExplanationStepSchema).min(1),

  // Pedagogical scaffolding
  misconceptionCheckpoints: z.array(MisconceptionCheckpointSchema),
  transferPrompts: z.array(TransferPromptSchema),
  adaptationRules: z.array(GradeAdaptationRuleSchema),

  // Quality metadata
  difficultyEstimate: z.number().min(0).max(1),
  estimatedTimeMinutes: z.number().int().positive(),
  prerequisites: z.array(z.string()),

  // Scoring hints
  evaluationHints: EvaluationHintsSchema,

  // Metadata
  createdAt: z.string().datetime().optional(),
  updatedAt: z.string().datetime().optional(),
  generatedBy: z.string().optional(),
  validationStatus: z.enum(['pending', 'valid', 'invalid']).optional(),
});

// =============================================================================
// Type Exports
// =============================================================================

export type WorkedExampleManifest = z.infer<typeof WorkedExampleManifestSchema>;
export type ExampleFamily = z.infer<typeof ExampleFamily>;
export type Given = z.infer<typeof GivenSchema>;
export type ReasoningStep = z.infer<typeof ReasoningStepSchema>;
export type ExplanationStep = z.infer<typeof ExplanationStepSchema>;
export type MisconceptionCheckpoint = z.infer<typeof MisconceptionCheckpointSchema>;
export type TransferPrompt = z.infer<typeof TransferPromptSchema>;
export type GradeAdaptationRule = z.infer<typeof GradeAdaptationRuleSchema>;
export type EvaluationHints = z.infer<typeof EvaluationHintsSchema>;

// =============================================================================
// Validation Helpers
// =============================================================================

/**
 * Validate a worked example manifest.
 */
export function validateWorkedExampleManifest(data: unknown): {
  success: boolean;
  data?: WorkedExampleManifest;
  errors?: z.ZodError;
} {
  const result = WorkedExampleManifestSchema.safeParse(data);
  if (result.success) {
    return { success: true, data: result.data };
  }
  return { success: false, errors: result.error };
}

/**
 * Create a default empty manifest template.
 */
export function createWorkedExampleTemplate(claimRef: string): WorkedExampleManifest {
  return {
    schemaVersion: '1.0.0',
    manifestType: 'WorkedExample',
    claimRef,
    evidenceRefs: [],
    domain: '',
    gradeBand: '',
    pedagogicalIntent: '',
    exampleFamily: 'worked-solution',
    learnerGoal: '',
    givens: [],
    reasoningSteps: [],
    explanationSteps: [],
    misconceptionCheckpoints: [],
    transferPrompts: [],
    adaptationRules: [],
    difficultyEstimate: 0.5,
    estimatedTimeMinutes: 5,
    prerequisites: [],
    evaluationHints: {
      correctIndicators: [],
      misconceptionIndicators: [],
    },
  };
}

/**
 * Artifact Manifests Index
 *
 * Central exports for all artifact manifest types.
 *
 * @doc.type module
 * @doc.purpose Central exports for artifact manifests
 * @doc.layer contracts
 */

// Worked Example Manifest
export {
  WorkedExampleManifestSchema,
  ExampleFamily as ExampleFamilySchema,
  GivenSchema,
  ReasoningStepSchema,
  ExplanationStepSchema,
  MisconceptionCheckpointSchema,
  TransferPromptSchema,
  GradeAdaptationRuleSchema,
  EvaluationHintsSchema,
  type WorkedExampleManifest,
  type ExampleFamily,
  type Given,
  type ReasoningStep,
  type ExplanationStep,
  type MisconceptionCheckpoint,
  type TransferPrompt,
  type GradeAdaptationRule,
  type EvaluationHints,
  validateWorkedExampleManifest,
  createWorkedExampleTemplate,
} from './worked-example-manifest';

// Animation Manifest
export {
  AnimationManifestSchema,
  SceneNodeSchema,
  AnimatedEntitySchema,
  AnimationSegmentSchema,
  CueingRuleSchema,
  PacingMetadataSchema,
  NarrationScriptSchema,
  SubtitleCueSchema,
  LearnerControlSchema,
  ClaimSegmentMappingSchema,
  type AnimationManifest,
  type SceneNode,
  type AnimatedEntity,
  type AnimationSegment,
  type CueingRule,
  type PacingMetadata,
  type NarrationScript,
  type SubtitleCue,
  type LearnerControl,
  type ClaimSegmentMapping,
  validateAnimationManifest,
  createAnimationTemplate,
} from './animation-manifest';

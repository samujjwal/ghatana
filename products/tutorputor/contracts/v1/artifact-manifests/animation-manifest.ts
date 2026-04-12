/**
 * AnimationManifest Schema
 *
 * Task 2.2: Define AnimationManifest Schema
 *
 * @doc.type module
 * @doc.purpose JSON Schema and TypeScript types for declarative animations
 * @doc.layer contracts
 * @doc.pattern SchemaDefinition
 */

import { z } from 'zod';

// =============================================================================
// Core Types
// =============================================================================

export const SceneNodeSchema = z.object({
  nodeId: z.string(),
  type: z.enum(['entity', 'group', 'camera', 'light']),
  parentId: z.string().optional(),
  transform: z.object({
    position: z.tuple([z.number(), z.number(), z.number()]),
    rotation: z.tuple([z.number(), z.number(), z.number()]),
    scale: z.tuple([z.number(), z.number(), z.number()]),
  }),
  properties: z.record(z.unknown()).optional(),
});

export const AnimatedEntitySchema = z.object({
  entityId: z.string(),
  name: z.string(),
  type: z.enum(['sphere', 'cube', 'cylinder', 'arrow', 'text', 'sprite']),
  initialState: z.record(z.unknown()),
  behavior: z.object({
    movable: z.boolean().default(false),
    interactive: z.boolean().default(false),
    physicsEnabled: z.boolean().default(false),
  }),
});

export const AnimationSegmentSchema = z.object({
  segmentId: z.string(),
  startTime: z.number(),
  endTime: z.number(),
  description: z.string(),
  conceptsIllustrated: z.array(z.string()),
  pausePoints: z.array(z.number()),
  speed: z.enum(['slow', 'normal', 'fast']).default('normal'),
});

export const CueingRuleSchema = z.object({
  trigger: z.enum(['time', 'action', 'completion']),
  condition: z.string(),
  effect: z.enum(['highlight', 'focus', 'label', 'fade', 'pulse']),
  target: z.string(),
  duration: z.number().default(1.0),
});

export const PacingMetadataSchema = z.object({
  introDuration: z.number(),
  conceptPresentationDuration: z.number(),
  interactionTime: z.number(),
  outroDuration: z.number(),
  recommendedPauses: z.array(z.number()),
});

export const NarrationScriptSchema = z.object({
  segments: z.array(z.object({
    segmentId: z.string(),
    text: z.string(),
    startTime: z.number(),
    duration: z.number(),
    tone: z.enum(['instructional', 'conversational', 'emphatic']).default('instructional'),
  })),
  language: z.string().default('en'),
});

export const SubtitleCueSchema = z.object({
  startTime: z.number(),
  endTime: z.number(),
  text: z.string(),
  segmentRef: z.string().optional(),
});

export const LearnerControlSchema = z.object({
  type: z.enum(['play', 'pause', 'rewind', 'speed', 'fullscreen', 'reset']),
  enabled: z.boolean(),
  position: z.enum(['bottom', 'top', 'overlay']).default('bottom'),
});

export const ClaimSegmentMappingSchema = z.object({
  claimRef: z.string(),
  segmentIds: z.array(z.string()),
  emphasisLevel: z.enum(['primary', 'secondary', 'tertiary']).default('primary'),
});

// =============================================================================
// Main Manifest Schema
// =============================================================================

export const AnimationManifestSchema = z.object({
  schemaVersion: z.literal('1.0.0'),
  manifestType: z.literal('Animation'),

  // Provenance
  claimRef: z.string(),
  evidenceRefs: z.array(z.string()),

  // Context
  domain: z.string(),
  gradeBand: z.string(),
  pedagogicalIntent: z.string(),

  // Scene structure
  sceneGraph: z.array(SceneNodeSchema),
  entities: z.array(AnimatedEntitySchema),

  // Timeline
  segments: z.array(AnimationSegmentSchema).min(1),
  totalDurationSeconds: z.number().positive(),

  // Cueing and pacing
  cueingRules: z.array(CueingRuleSchema),
  pacingMetadata: PacingMetadataSchema,

  // Accessibility
  narrationScript: NarrationScriptSchema.optional(),
  subtitles: z.array(SubtitleCueSchema).optional(),
  learnerControls: z.array(LearnerControlSchema),

  // Pedagogical
  claimMapping: z.array(ClaimSegmentMappingSchema),

  // Metadata
  createdAt: z.string().datetime().optional(),
  updatedAt: z.string().datetime().optional(),
  generatedBy: z.string().optional(),
  validationStatus: z.enum(['pending', 'valid', 'invalid']).optional(),
  variantKey: z.string().default('primary'),
});

// =============================================================================
// Type Exports
// =============================================================================

export type AnimationManifest = z.infer<typeof AnimationManifestSchema>;
export type SceneNode = z.infer<typeof SceneNodeSchema>;
export type AnimatedEntity = z.infer<typeof AnimatedEntitySchema>;
export type AnimationSegment = z.infer<typeof AnimationSegmentSchema>;
export type CueingRule = z.infer<typeof CueingRuleSchema>;
export type PacingMetadata = z.infer<typeof PacingMetadataSchema>;
export type NarrationScript = z.infer<typeof NarrationScriptSchema>;
export type SubtitleCue = z.infer<typeof SubtitleCueSchema>;
export type LearnerControl = z.infer<typeof LearnerControlSchema>;
export type ClaimSegmentMapping = z.infer<typeof ClaimSegmentMappingSchema>;

// =============================================================================
// Validation Helpers
// =============================================================================

/**
 * Validate an animation manifest.
 */
export function validateAnimationManifest(data: unknown): {
  success: boolean;
  data?: AnimationManifest;
  errors?: z.ZodError;
} {
  const result = AnimationManifestSchema.safeParse(data);
  if (result.success) {
    return { success: true, data: result.data };
  }
  return { success: false, errors: result.error };
}

/**
 * Create a default empty manifest template.
 */
export function createAnimationTemplate(claimRef: string): AnimationManifest {
  return {
    schemaVersion: '1.0.0',
    manifestType: 'Animation',
    claimRef,
    evidenceRefs: [],
    domain: '',
    gradeBand: '',
    pedagogicalIntent: '',
    sceneGraph: [],
    entities: [],
    segments: [],
    totalDurationSeconds: 30,
    cueingRules: [],
    pacingMetadata: {
      introDuration: 2,
      conceptPresentationDuration: 20,
      interactionTime: 5,
      outroDuration: 3,
      recommendedPauses: [],
    },
    learnerControls: [
      { type: 'play', enabled: true },
      { type: 'pause', enabled: true },
      { type: 'reset', enabled: true },
    ],
    claimMapping: [],
    variantKey: 'primary',
  };
}

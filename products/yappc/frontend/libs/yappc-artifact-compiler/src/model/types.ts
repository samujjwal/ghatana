/**
 * @fileoverview SemanticProductModel schemas - normalized product-facing models.
 *
 * The SemanticProductModel is the canonical configuration layer that builder,
 * generators, previews, and platform emitters consume. It is synthesized from
 * the ArtifactGraph and projected into builder documents, design system
 * contracts, token files, and codegen inputs.
 */

import { z } from 'zod';

// ============================================================================
// Base Model Element (all models extend this)
// ============================================================================

export const ModelElementBaseSchema = z.object({
  id: z.string().uuid(),
  name: z.string().min(1),
  description: z.string().optional(),
  confidence: z.number().min(0).max(1),
  provenance: z.object({
    extractorId: z.string().min(1),
    extractorVersion: z.string().min(1),
    sourcePaths: z.array(z.string()).min(1),
    kind: z.enum(['exact', 'inferred', 'synthesized', 'manual', 'assumed']),
    extractedAt: z.string().datetime(),
  }),
  reviewRequirement: z.object({
    required: z.boolean(),
    reason: z.string().optional(),
    confidenceThreshold: z.number().min(0).max(1).optional(),
  }).optional(),
  securityFlags: z.array(z.string()).default([]),
  privacyFlags: z.array(z.string()).default([]),
  tags: z.array(z.string()).default([]),
  /**
   * Graph node IDs that this model element is derived from or associated with.
   * Enables tracing from semantic model back to source graph nodes.
   */
  graphNodeIds: z.array(z.string()).default([]),
  /**
   * Source references (e.g., file paths, symbol refs) that contributed to this element.
   * Provides provenance tracking back to original source locations.
   */
  sourceRefs: z.array(z.string()).default([]),
  /**
   * Residual island IDs that are related to this model element.
   * Enables linking modeled content to its unmodelable parts.
   */
  residualIslandIds: z.array(z.string().uuid()).default([]),
});

export type ModelElementBase = z.infer<typeof ModelElementBaseSchema>;

// ============================================================================
// Component Model
// ============================================================================

export const PropSchemaSchema = z.object({
  name: z.string().min(1),
  type: z.string().min(1),
  required: z.boolean().default(false),
  defaultValue: z.unknown().optional(),
  description: z.string().optional(),
  examples: z.array(z.unknown()).default([]),
});

export type PropSchema = z.infer<typeof PropSchemaSchema>;

export const SlotSchemaSchema = z.object({
  name: z.string().min(1),
  allowedComponents: z.array(z.string()).optional(),
  multiple: z.boolean().default(false),
  required: z.boolean().default(false),
});

export type SlotSchema = z.infer<typeof SlotSchemaSchema>;

export const EventSchemaSchema = z.object({
  name: z.string().min(1),
  payloadType: z.string().optional(),
  description: z.string().optional(),
});

export type EventSchema = z.infer<typeof EventSchemaSchema>;

export const ComponentVariantSchema = z.object({
  name: z.string().min(1),
  propOverrides: z.record(z.string(), z.unknown()).default({}),
  description: z.string().optional(),
});

export type ComponentVariant = z.infer<typeof ComponentVariantSchema>;

export const AccessibilityMetadataSchema = z.object({
  ariaRole: z.string().optional(),
  keyboardNavigation: z.boolean().default(false),
  screenReaderLabel: z.string().optional(),
  focusable: z.boolean().default(false),
  contrastRequirements: z.string().optional(),
});

export type AccessibilityMetadata = z.infer<typeof AccessibilityMetadataSchema>;

export const ComponentModelSchema = ModelElementBaseSchema.extend({
  kind: z.literal('component'),
  contractName: z.string().min(1),
  props: z.array(PropSchemaSchema).default([]),
  slots: z.array(SlotSchemaSchema).default([]),
  events: z.array(EventSchemaSchema).default([]),
  variants: z.array(ComponentVariantSchema).default([]),
  stateConnections: z.array(z.string().uuid()).default([]),
  dataDependencies: z.array(z.string().uuid()).default([]),
  styleDependencies: z.array(z.string().uuid()).default([]),
  accessibility: AccessibilityMetadataSchema.optional(),
  storyIds: z.array(z.string().uuid()).default([]),
  builderCanvasHints: z.record(z.string(), z.unknown()).default({}),
});

export type ComponentModel = z.infer<typeof ComponentModelSchema>;

// ============================================================================
// Page Model
// ============================================================================

export const PageModelSchema = ModelElementBaseSchema.extend({
  kind: z.literal('page'),
  routePath: z.string().min(1),
  layoutId: z.string().uuid().optional(),
  componentIds: z.array(z.string().uuid()).default([]),
  dataDependencies: z.array(z.string().uuid()).default([]),
  authGuard: z.object({
    required: z.boolean(),
    roles: z.array(z.string()).default([]),
    redirectPath: z.string().optional(),
  }).optional(),
  seoMetadata: z.object({
    title: z.string().optional(),
    description: z.string().optional(),
    ogImage: z.string().optional(),
  }).optional(),
  visibility: z.enum(['public', 'authenticated', 'admin', 'role-based']).default('public'),
});

export type PageModel = z.infer<typeof PageModelSchema>;

// ============================================================================
// Layout Model
// ============================================================================

export const LayoutModelSchema = ModelElementBaseSchema.extend({
  kind: z.literal('layout'),
  templateType: z.enum(['default', 'sidebar', 'header', 'fullscreen', 'modal']),
  slotRegions: z.array(z.object({
    name: z.string().min(1),
    defaultComponentId: z.string().uuid().optional(),
  })).default([]),
  appliedToPageIds: z.array(z.string().uuid()).default([]),
});

export type LayoutModel = z.infer<typeof LayoutModelSchema>;

// ============================================================================
// Token Model (aligned with stable design tokens spec 2025.10)
// ============================================================================

export const TokenValueSchema = z.object({
  value: z.union([z.string(), z.number(), z.object({}).passthrough()]),
  type: z.enum(['color', 'dimension', 'fontFamily', 'fontWeight', 'duration', 'cubicBezier', 'number', 'string', 'boolean', 'shadow', 'gradient', 'border', 'custom']),
  description: z.string().optional(),
  colorSpace: z.enum(['srgb', 'display-p3', 'oklch', 'hsl', 'hex', 'rgb', 'rgba']).optional(),
});

export type TokenValue = z.infer<typeof TokenValueSchema>;

export const TokenModelSchema = ModelElementBaseSchema.extend({
  kind: z.literal('token'),
  tokenPath: z.array(z.string().min(1)).min(1),
  value: TokenValueSchema,
  aliases: z.array(z.array(z.string().min(1))).default([]),
  platformOverrides: z.record(z.string(), z.record(z.string(), z.unknown())).default({}),
});

export type TokenModel = z.infer<typeof TokenModelSchema>;

// ============================================================================
// Theme Model
// ============================================================================

export const ThemeModelSchema = ModelElementBaseSchema.extend({
  kind: z.literal('theme'),
  mode: z.enum(['light', 'dark', 'system', 'high-contrast']),
  tokenSetIds: z.array(z.string().uuid()).default([]),
  overrides: z.record(z.string(), z.unknown()).default({}),
});

export type ThemeModel = z.infer<typeof ThemeModelSchema>;

// ============================================================================
// Style Model
// ============================================================================

export const StyleModelSchema = ModelElementBaseSchema.extend({
  kind: z.literal('style'),
  selector: z.string().min(1),
  properties: z.record(z.string(), z.string()).default({}),
  mediaQueries: z.array(z.object({
    condition: z.string().min(1),
    properties: z.record(z.string(), z.string()),
  })).default([]),
  tokenReferences: z.array(z.array(z.string().min(1))).default([]),
});

export type StyleModel = z.infer<typeof StyleModelSchema>;

// ============================================================================
// Data Model (from Prisma and SQL extraction)
// ============================================================================

export const EntityRelationSchema = z.object({
  targetEntityId: z.string().uuid(),
  kind: z.enum(['one-to-one', 'one-to-many', 'many-to-one', 'many-to-many']),
  fieldName: z.string().min(1),
  optional: z.boolean().default(false),
  onDelete: z.enum(['cascade', 'set-null', 'restrict', 'no-action']).optional(),
});

export type EntityRelation = z.infer<typeof EntityRelationSchema>;

export const EntityFieldSchema = z.object({
  name: z.string().min(1),
  type: z.string().min(1),
  required: z.boolean().default(true),
  unique: z.boolean().default(false),
  defaultValue: z.unknown().optional(),
  isPrimaryKey: z.boolean().default(false),
  isForeignKey: z.boolean().default(false),
  description: z.string().optional(),
});

export type EntityField = z.infer<typeof EntityFieldSchema>;

export const EntityIndexSchema = z.object({
  name: z.string().min(1),
  fields: z.array(z.string().min(1)),
  unique: z.boolean().default(false),
  type: z.enum(['btree', 'hash', 'gin', 'gist']).optional(),
});

export type EntityIndex = z.infer<typeof EntityIndexSchema>;

export const DataModelSchema = ModelElementBaseSchema.extend({
  kind: z.literal('data-entity'),
  tableName: z.string().min(1),
  fields: z.array(EntityFieldSchema).default([]),
  relations: z.array(EntityRelationSchema).default([]),
  indexes: z.array(EntityIndexSchema).default([]),
  constraints: z.array(z.string()).default([]),
  unsupportedFeatures: z.array(z.object({
    feature: z.string().min(1),
    reason: z.string().min(1),
    originalSql: z.string().optional(),
  })).default([]),
  migrationLineage: z.array(z.string().uuid()).default([]),
});

export type DataModel = z.infer<typeof DataModelSchema>;

// ============================================================================
// API Model (supporting OpenAPI 3.2.0 features)
// ============================================================================

export const ApiParameterSchema = z.object({
  name: z.string().min(1),
  location: z.enum(['query', 'path', 'header', 'cookie']),
  required: z.boolean().default(false),
  type: z.string().min(1),
  description: z.string().optional(),
});

export type ApiParameter = z.infer<typeof ApiParameterSchema>;

export const ApiResponseSchema = z.object({
  statusCode: z.union([z.string().min(1), z.number().int()]),
  contentType: z.string().min(1),
  schemaRef: z.string().optional(),
  isStreaming: z.boolean().default(false),
  streamingItemSchema: z.string().optional(),
  description: z.string().optional(),
});

export type ApiResponse = z.infer<typeof ApiResponseSchema>;

export const ApiModelSchema = ModelElementBaseSchema.extend({
  kind: z.literal('api-endpoint'),
  path: z.string().min(1),
  methods: z.array(z.string().min(1)).min(1),
  additionalOperations: z.array(z.string().min(1)).default([]),
  tags: z.array(z.object({
    name: z.string().min(1),
    summary: z.string().optional(),
    parentTag: z.string().optional(),
  })).default([]),
  parameters: z.array(ApiParameterSchema).default([]),
  requestBodySchema: z.string().optional(),
  responses: z.array(ApiResponseSchema).default([]),
  authRequired: z.boolean().default(false),
  rateLimited: z.boolean().default(false),
});

export type ApiModel = z.infer<typeof ApiModelSchema>;

// ============================================================================
// State Model
// ============================================================================

export const StateStoreModelSchema = ModelElementBaseSchema.extend({
  kind: z.literal('state-store'),
  storeType: z.enum(['redux', 'zustand', 'context', 'xstate', 'jotai', 'recoil', 'mobx', 'unknown']),
  stateTree: z.record(z.string(), z.unknown()).optional(),
  actionTypes: z.array(z.object({
    name: z.string().min(1),
    payloadType: z.string().optional(),
    description: z.string().optional(),
  })).default([]),
  reducers: z.array(z.object({
    name: z.string().min(1),
    handledActions: z.array(z.string().min(1)),
  })).default([]),
  selectors: z.array(z.object({
    name: z.string().min(1),
    inputPaths: z.array(z.string().min(1)),
    outputType: z.string().optional(),
  })).default([]),
  connectedComponentIds: z.array(z.string().uuid()).default([]),
});

export type StateStoreModel = z.infer<typeof StateStoreModelSchema>;

// ============================================================================
// Behavior / Interaction Model
// ============================================================================

export const InteractionModelSchema = ModelElementBaseSchema.extend({
  kind: z.literal('interaction'),
  trigger: z.enum(['click', 'hover', 'focus', 'blur', 'submit', 'change', 'scroll', 'keydown', 'custom']),
  sourceComponentId: z.string().uuid(),
  targetAction: z.enum(['navigate', 'api-call', 'state-update', 'emit-event', 'toggle', 'validate', 'custom']),
  payloadMapping: z.record(z.string(), z.string()).default({}),
  conditions: z.array(z.string()).default([]),
});

export type InteractionModel = z.infer<typeof InteractionModelSchema>;

// ============================================================================
// Cache Model
// ============================================================================

export const CacheModelSchema = ModelElementBaseSchema.extend({
  kind: z.literal('cache-config'),
  strategy: z.enum(['stale-while-revalidate', 'cache-first', 'network-first', 'cache-only', 'no-cache']),
  cacheKeyPattern: z.string().min(1),
  ttlSeconds: z.number().int().nonnegative().optional(),
  invalidationTriggers: z.array(z.string().min(1)).default([]),
  optimisticUpdates: z.boolean().default(false),
});

export type CacheModel = z.infer<typeof CacheModelSchema>;

// ============================================================================
// Workflow / CI-CD Model
// ============================================================================

export const WorkflowJobSchema = z.object({
  id: z.string().uuid(),
  name: z.string().min(1),
  dependsOn: z.array(z.string().uuid()).default([]),
  parallelGroup: z.string().optional(),
  steps: z.array(z.object({
    name: z.string().min(1),
    command: z.string().min(1),
    environment: z.record(z.string(), z.string()).default({}),
  })).default([]),
});

export type WorkflowJob = z.infer<typeof WorkflowJobSchema>;

export const WorkflowModelSchema = ModelElementBaseSchema.extend({
  kind: z.literal('workflow'),
  trigger: z.enum(['push', 'pull-request', 'schedule', 'manual', 'tag']),
  branchFilter: z.array(z.string().min(1)).default([]),
  jobs: z.array(WorkflowJobSchema).default([]),
  environment: z.record(z.string(), z.string()).default({}),
  artifactOutputs: z.array(z.string().min(1)).default([]),
});

export type WorkflowModel = z.infer<typeof WorkflowModelSchema>;

// ============================================================================
// Semantic Product Model (union + container)
// ============================================================================

export const SemanticModelElementSchema = z.union([
  ComponentModelSchema,
  PageModelSchema,
  LayoutModelSchema,
  TokenModelSchema,
  ThemeModelSchema,
  StyleModelSchema,
  DataModelSchema,
  ApiModelSchema,
  StateStoreModelSchema,
  InteractionModelSchema,
  CacheModelSchema,
  WorkflowModelSchema,
]);

export type SemanticModelElement = z.infer<typeof SemanticModelElementSchema>;

export const SemanticProductModelSchema = z.object({
  id: z.string().uuid(),
  sourceModelRef: z.string().min(1).optional(),
  repositoryRoot: z.string().min(1),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  version: z.number().int().nonnegative(),
  elements: z.array(SemanticModelElementSchema),
  elementIndex: z.record(z.string(), z.array(z.string().uuid())), // kind -> elementIds
  residualIslandIds: z.array(z.string().uuid()).default([]),
});

export type SemanticProductModel = z.infer<typeof SemanticProductModelSchema>;

// ============================================================================
// Model Diff / Version Metadata
// ============================================================================

export const ModelChangeKindSchema = z.enum([
  'added',
  'removed',
  'modified',
  'renamed',
  'moved',
]);

export type ModelChangeKind = z.infer<typeof ModelChangeKindSchema>;

export const ModelChangeSchema = z.object({
  elementId: z.string().uuid(),
  kind: ModelChangeKindSchema,
  previousVersion: z.string().uuid().optional(),
  newVersion: z.string().uuid().optional(),
  changedFields: z.array(z.string()).default([]),
  changeReason: z.string().optional(),
  changedAt: z.string().datetime(),
  changedBy: z.string().optional(), // User or system identifier
});

export type ModelChange = z.infer<typeof ModelChangeSchema>;

export const ModelVersionMetadataSchema = z.object({
  versionId: z.string().uuid(),
  previousVersionId: z.string().uuid().optional(),
  snapshotRef: z.object({
    provider: z.enum(['local-folder', 'github', 'gitlab', 'zip', 'artifact-registry']),
    repoId: z.string().min(1),
    commitSha: z.string().optional(),
    branch: z.string().optional(),
  }).optional(),
  changes: z.array(ModelChangeSchema).default([]),
  changeSummary: z.object({
    added: z.number().int().nonnegative(),
    removed: z.number().int().nonnegative(),
    modified: z.number().int().nonnegative(),
  }),
  createdAt: z.string().datetime(),
  createdBy: z.string().optional(),
  description: z.string().optional(),
});

export type ModelVersionMetadata = z.infer<typeof ModelVersionMetadataSchema>;

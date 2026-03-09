/**
 * Runtime Validation Schemas using Zod
 *
 * Provides runtime validation for all Universal Framework contracts.
 * Ensures data integrity at system boundaries.
 *
 * @doc.type system
 * @doc.purpose Runtime validation schemas
 * @doc.layer core
 * @doc.pattern Schema Validation
 */

import { z } from 'zod';

// ============================================================================
// Primitive Schemas
// ============================================================================

/**
 * Semantic version schema
 */
export const SemanticVersionSchema = z.string().regex(
    /^\d+\.\d+\.\d+$/,
    'Version must be in format X.Y.Z'
);

/**
 * Unique ID schema (UUID format)
 */
export const UniqueIdSchema = z.string().min(1).max(128);

/**
 * Artifact kind schema
 */
export const ArtifactKindSchema = z.string().regex(
    /^[a-z]+:[a-z][a-z0-9-]*$/,
    'Kind must be in format "namespace:name" (e.g., "ui:button")'
);

/**
 * Content modality schema
 */
export const ContentModalitySchema = z.enum([
    'visual',
    'code',
    'diagram',
    'drawing',
    'text',
    'mixed',
]);

/**
 * Zoom level schema
 */
export const ZoomLevelSchema = z.enum(['summary', 'detail', 'editor', 'code']);

/**
 * Export format schema
 */
export const ExportFormatSchema = z.enum(['tsx', 'html', 'svg', 'png', 'pdf', 'json']);

// ============================================================================
// Schema Field Definitions
// ============================================================================

/**
 * Base schema field
 */
const BaseFieldSchema = z.object({
    type: z.string(),
    default: z.unknown().optional(),
    required: z.boolean().optional(),
    description: z.string().optional(),
});

/**
 * String field schema
 */
export const StringFieldSchema = BaseFieldSchema.extend({
    type: z.literal('string'),
    default: z.string().optional(),
    minLength: z.number().optional(),
    maxLength: z.number().optional(),
    pattern: z.string().optional(),
    placeholder: z.string().optional(),
});

/**
 * Number field schema
 */
export const NumberFieldSchema = BaseFieldSchema.extend({
    type: z.literal('number'),
    default: z.number().optional(),
    min: z.number().optional(),
    max: z.number().optional(),
    step: z.number().optional(),
    unit: z.string().optional(),
});

/**
 * Boolean field schema
 */
export const BooleanFieldSchema = BaseFieldSchema.extend({
    type: z.literal('boolean'),
    default: z.boolean().optional(),
});

/**
 * Enum field schema
 */
export const EnumFieldSchema = BaseFieldSchema.extend({
    type: z.literal('enum'),
    values: z.array(z.string()),
    default: z.string().optional(),
    labels: z.record(z.string()).optional(),
});

/**
 * Color field schema
 */
export const ColorFieldSchema = BaseFieldSchema.extend({
    type: z.literal('color'),
    default: z.string().optional(),
    token: z.string().optional(),
    allowCustom: z.boolean().optional(),
});

/**
 * Spacing field schema
 */
export const SpacingFieldSchema = BaseFieldSchema.extend({
    type: z.literal('spacing'),
    default: z.string().optional(),
    tokens: z.array(z.string()).optional(),
});

/**
 * Binding field schema
 */
export const BindingFieldSchema = BaseFieldSchema.extend({
    type: z.literal('binding'),
    bindingType: z.enum(['state', 'expression', 'action']),
    valueType: z.string().optional(),
});

/**
 * Action field schema
 */
export const ActionFieldSchema = BaseFieldSchema.extend({
    type: z.literal('action'),
    parameters: z.record(z.lazy(() => AnyFieldSchema)).optional(),
});

/**
 * Any field schema (union)
 */
export const AnyFieldSchema: z.ZodType<unknown> = z.lazy(() =>
    z.union([
        StringFieldSchema,
        NumberFieldSchema,
        BooleanFieldSchema,
        EnumFieldSchema,
        ColorFieldSchema,
        SpacingFieldSchema,
        BindingFieldSchema,
        ActionFieldSchema,
        ObjectFieldSchema,
        ArrayFieldSchema,
    ])
);

/**
 * Object field schema
 */
export const ObjectFieldSchema = BaseFieldSchema.extend({
    type: z.literal('object'),
    properties: z.record(AnyFieldSchema),
});

/**
 * Array field schema
 */
export const ArrayFieldSchema = BaseFieldSchema.extend({
    type: z.literal('array'),
    items: AnyFieldSchema,
    minItems: z.number().optional(),
    maxItems: z.number().optional(),
});

/**
 * Props schema
 */
export const PropsSchemaDefinition = z.record(AnyFieldSchema);

// ============================================================================
// Artifact Contract Schemas
// ============================================================================

/**
 * Artifact capabilities schema
 */
export const ArtifactCapabilitiesSchema = z.object({
    resizable: z.boolean(),
    droppable: z.boolean(),
    textEditable: z.boolean(),
    connectable: z.boolean(),
    styleable: z.boolean(),
    bindable: z.boolean(),
    lockable: z.boolean(),
    copyable: z.boolean(),
    drillable: z.boolean(),
});

/**
 * Artifact constraints schema
 */
export const ArtifactConstraintsSchema = z.object({
    minWidth: z.number().optional(),
    maxWidth: z.number().optional(),
    minHeight: z.number().optional(),
    maxHeight: z.number().optional(),
    acceptsChildren: z.union([z.array(ArtifactKindSchema), z.boolean()]),
    requiresParent: z.array(ArtifactKindSchema).optional(),
    maxChildren: z.number().optional(),
    aspectRatio: z.number().optional(),
    snapToGrid: z.number().optional(),
});

/**
 * Artifact identity schema
 */
export const ArtifactIdentitySchema = z.object({
    artifactId: UniqueIdSchema,
    kind: ArtifactKindSchema,
    version: SemanticVersionSchema,
    name: z.string().min(1),
    category: z.string().min(1),
    tags: z.array(z.string()),
    icon: z.string(),
    description: z.string(),
});

/**
 * Artifact defaults schema
 */
export const ArtifactDefaultsSchema = z.object({
    props: z.record(z.unknown()),
    style: z.record(z.unknown()),
    width: z.number().positive(),
    height: z.number().positive(),
    children: z.array(ArtifactKindSchema).optional(),
});

/**
 * Complete artifact contract schema
 */
export const ArtifactContractSchema = z.object({
    identity: ArtifactIdentitySchema,
    propsSchema: PropsSchemaDefinition,
    styleSchema: PropsSchemaDefinition,
    bindingsSchema: PropsSchemaDefinition.optional(),
    capabilities: ArtifactCapabilitiesSchema,
    constraints: ArtifactConstraintsSchema,
    defaults: ArtifactDefaultsSchema,
    modality: ContentModalitySchema,
    platforms: z.array(z.enum(['web', 'desktop', 'mobile'])),
    platformNotes: z.string().optional(),
});

// ============================================================================
// State Contract Schemas
// ============================================================================

/**
 * Binding reference schema
 */
export const BindingRefSchema = z.discriminatedUnion('type', [
    z.object({ type: z.literal('static'), value: z.unknown() }),
    z.object({ type: z.literal('state'), path: z.string() }),
    z.object({ type: z.literal('expression'), expr: z.string() }),
    z.object({ type: z.literal('computed'), key: z.string() }),
    z.object({
        type: z.literal('action'),
        actionId: z.string(),
        params: z.record(z.unknown()).optional(),
    }),
]);

/**
 * Event handler schema
 */
export const EventHandlerSchema = z.object({
    type: z.enum(['action', 'navigate', 'setState', 'custom']),
    handler: z.string(),
    params: z.record(z.unknown()).optional(),
    condition: z.string().optional(),
});

/**
 * Node transform schema
 */
export const NodeTransformSchema = z.object({
    x: z.number(),
    y: z.number(),
    width: z.number().positive(),
    height: z.number().positive(),
    rotation: z.number(),
    zIndex: z.number(),
    scaleX: z.number().optional(),
    scaleY: z.number().optional(),
});

/**
 * Node metadata schema
 */
export const NodeMetaSchema = z.object({
    author: z.string(),
    createdAt: z.number(),
    lastEditedBy: z.string(),
    lastEditedAt: z.number(),
});

/**
 * Node annotation schema
 */
export const NodeAnnotationSchema = z.object({
    id: UniqueIdSchema,
    type: z.enum(['comment', 'highlight', 'flag', 'link']),
    content: z.string(),
    author: z.string(),
    createdAt: z.number(),
    resolved: z.boolean().optional(),
});

/**
 * Node content schema
 */
export const NodeContentSchema = z.object({
    type: ContentModalitySchema,
    data: z.unknown(),
    language: z.string().optional(),
    encoding: z.string().optional(),
});

/**
 * Universal node schema
 */
export const UniversalNodeSchema = z.object({
    id: UniqueIdSchema,
    kind: z.string(), // Allow any string for flexibility
    name: z.string(),
    props: z.record(z.unknown()),
    style: z.record(z.unknown()),
    transform: NodeTransformSchema,
    content: NodeContentSchema.optional(),
    parentId: UniqueIdSchema.nullable(),
    children: z.array(UniqueIdSchema),
    bindings: z.record(BindingRefSchema),
    events: z.record(EventHandlerSchema),
    locked: z.boolean(),
    visible: z.boolean(),
    layer: z.string().optional(),
    tags: z.array(z.string()),
    annotations: z.array(NodeAnnotationSchema).optional(),
    version: SemanticVersionSchema,
    runtime: z.record(z.unknown()).optional(),
    meta: NodeMetaSchema,
});

/**
 * Universal edge schema
 */
export const UniversalEdgeSchema = z.object({
    id: UniqueIdSchema,
    sourceId: UniqueIdSchema,
    targetId: UniqueIdSchema,
    sourceHandle: z.string().optional(),
    targetHandle: z.string().optional(),
    kind: z.string(),
    props: z.record(z.unknown()),
    style: z.record(z.unknown()),
    meta: NodeMetaSchema,
});

/**
 * Document resource schema
 */
export const DocumentResourceSchema = z.object({
    id: UniqueIdSchema,
    type: z.enum(['asset', 'style', 'symbol', 'component', 'module']),
    name: z.string(),
    data: z.unknown(),
    meta: NodeMetaSchema,
});

/**
 * Document view schema
 */
export const DocumentViewSchema = z.object({
    id: UniqueIdSchema,
    name: z.string(),
    type: z.enum(['canvas', 'artboard', 'page', 'layer']),
    rootNodes: z.array(UniqueIdSchema),
    viewport: z.object({
        x: z.number(),
        y: z.number(),
        zoom: z.number().positive(),
    }),
    background: z.string().optional(),
    gridVisible: z.boolean().optional(),
    gridSize: z.number().optional(),
});

/**
 * Universal document schema
 */
export const UniversalDocumentSchema = z.object({
    id: UniqueIdSchema,
    name: z.string(),
    version: SemanticVersionSchema,
    nodes: z.record(UniversalNodeSchema),
    edges: z.record(UniversalEdgeSchema),
    resources: z.record(DocumentResourceSchema),
    views: z.record(DocumentViewSchema),
    activeViewId: UniqueIdSchema,
    meta: z.object({
        author: z.string(),
        createdAt: z.number(),
        lastEditedBy: z.string(),
        lastEditedAt: z.number(),
        tags: z.array(z.string()),
        description: z.string().optional(),
    }),
});

// ============================================================================
// Interaction Contract Schemas
// ============================================================================

/**
 * Tool definition schema
 */
export const ToolDefinitionSchema = z.object({
    id: z.string(),
    name: z.string(),
    icon: z.string(),
    shortcut: z.string().optional(),
    cursor: z.string(),
    group: z.enum(['select', 'draw', 'navigate', 'annotate', 'custom']),
    exclusive: z.boolean(),
    supportedModalities: z.array(ContentModalitySchema),
});

/**
 * Gesture definition schema
 */
export const GestureDefinitionSchema = z.object({
    id: z.string(),
    type: z.enum(['drag', 'pinch', 'rotate', 'tap', 'doubleTap', 'longPress', 'swipe']),
    modifiers: z.array(z.enum(['shift', 'ctrl', 'alt', 'meta'])).optional(),
    handler: z.string(),
});

/**
 * Shortcut definition schema
 */
export const ShortcutDefinitionSchema = z.object({
    id: z.string(),
    keys: z.string(),
    action: z.string(),
    context: z.enum(['canvas', 'inspector', 'palette', 'global']).optional(),
    description: z.string(),
});

/**
 * Interaction contract schema
 */
export const InteractionContractSchema = z.object({
    tools: z.array(ToolDefinitionSchema),
    gestures: z.array(GestureDefinitionSchema),
    shortcuts: z.array(ShortcutDefinitionSchema),
    contextMenu: z
        .array(
            z.object({
                id: z.string(),
                label: z.string(),
                icon: z.string().optional(),
                action: z.string(),
                condition: z.string().optional(),
            })
        )
        .optional(),
});

// ============================================================================
// Collaboration Contract Schemas
// ============================================================================

/**
 * Command type schema
 */
export const CommandTypeSchema = z.enum([
    'InsertNode',
    'DeleteNode',
    'UpdateProps',
    'UpdateStyle',
    'UpdateTransform',
    'ReparentNode',
    'ReorderChildren',
    'InsertEdge',
    'DeleteEdge',
    'UpdateResource',
    'RenameResource',
    'BatchCommand',
]);

/**
 * User presence schema
 */
export const UserPresenceSchema = z.object({
    userId: z.string(),
    userName: z.string(),
    color: z.string(),
    cursor: z
        .object({
            x: z.number(),
            y: z.number(),
            viewId: UniqueIdSchema,
        })
        .optional(),
    selection: z.array(UniqueIdSchema).optional(),
    activeTool: z.string().optional(),
    editingNodeId: UniqueIdSchema.optional(),
    lastSeen: z.number(),
});

/**
 * Collaboration contract schema
 */
export const CollaborationContractSchema = z.object({
    supportedCommands: z.array(CommandTypeSchema),
    conflictStrategy: z.enum(['last-write-wins', 'merge', 'reject', 'ask-user']),
    presenceInterval: z.number().positive(),
    snapshotInterval: z.number().positive(),
    maxHistoryDepth: z.number().positive(),
    offlineEnabled: z.boolean(),
});

// ============================================================================
// Validation Helpers
// ============================================================================

/**
 * Validation result type
 */
export interface ValidationResult {
    success: boolean;
    data?: unknown;
    errors?: z.ZodError;
}

/**
 * Validate a node against the schema
 */
export function validateNode(node: unknown): ValidationResult {
    const result = UniversalNodeSchema.safeParse(node);
    return {
        success: result.success,
        data: result.success ? result.data : undefined,
        errors: result.success ? undefined : result.error,
    };
}

/**
 * Validate a document against the schema
 */
export function validateDocument(document: unknown): ValidationResult {
    const result = UniversalDocumentSchema.safeParse(document);
    return {
        success: result.success,
        data: result.success ? result.data : undefined,
        errors: result.success ? undefined : result.error,
    };
}

/**
 * Validate an artifact contract against the schema
 */
export function validateArtifactContract(contract: unknown): ValidationResult {
    const result = ArtifactContractSchema.safeParse(contract);
    return {
        success: result.success,
        data: result.success ? result.data : undefined,
        errors: result.success ? undefined : result.error,
    };
}

/**
 * Create a partial validator for incremental updates
 */
export function createPartialValidator<T extends z.ZodTypeAny>(schema: T) {
    const partialSchema = schema.partial();
    return (data: unknown) => partialSchema.safeParse(data);
}

/**
 * Merge validation results
 */
export function mergeValidationResults(
    results: readonly ValidationResult[]
): ValidationResult {
    const allSuccess = results.every((r) => r.success);
    const allErrors = results
        .filter((r) => !r.success && r.errors)
        .flatMap((r) => r.errors!.errors);

    return {
        success: allSuccess,
        errors: allSuccess
            ? undefined
            : new z.ZodError(allErrors),
    };
}

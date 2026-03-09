/**
 * Universal Framework Contract Definitions
 *
 * This file defines the TypeScript interfaces for all 5 contract layers:
 * 1. Artifact Contract - What an artifact IS
 * 2. Render Contract - How artifacts APPEAR
 * 3. State Contract - How data FLOWS
 * 4. Interaction Contract - What users CAN DO
 * 5. Collaboration Contract - How changes SYNC
 *
 * These contracts form the "type system" for the entire canvas framework.
 *
 * @doc.type system
 * @doc.purpose Contract definitions for Universal Framework
 * @doc.layer core
 * @doc.pattern Contract-First Architecture
 */

import type { ComponentType } from 'react';

// ============================================================================
// Primitive Types
// ============================================================================

/**
 * Semantic version string (e.g., "1.0.0")
 */
export type SemanticVersion = `${number}.${number}.${number}`;

/**
 * Artifact kind identifier (e.g., "ui:button", "diagram:process")
 */
export type ArtifactKind = string;

/**
 * Unique identifier for nodes, artifacts, etc.
 */
export type UniqueId = string;

/**
 * Supported content modalities for multi-modal canvas
 */
export type ContentModality = 'visual' | 'code' | 'diagram' | 'drawing' | 'text' | 'mixed';

/**
 * Supported render targets
 */
export type RenderTarget = 'canvas' | 'preview' | 'outline' | 'thumbnail' | 'export';

/**
 * Supported export formats
 */
export type ExportFormat = 'tsx' | 'html' | 'svg' | 'png' | 'pdf' | 'json';

/**
 * Semantic zoom levels for drill-down navigation
 */
export type ZoomLevel = 'summary' | 'detail' | 'editor' | 'code';

// ============================================================================
// Schema Types (JSON Schema-like for runtime validation)
// ============================================================================

/**
 * Base schema field definition
 */
export interface SchemaField {
    readonly type: 'string' | 'number' | 'boolean' | 'enum' | 'color' | 'spacing' | 'binding' | 'action' | 'object' | 'array';
    readonly default?: unknown;
    readonly required?: boolean;
    readonly description?: string;
}

/**
 * String field schema
 */
export interface StringFieldSchema extends SchemaField {
    readonly type: 'string';
    readonly default?: string;
    readonly minLength?: number;
    readonly maxLength?: number;
    readonly pattern?: string;
    readonly placeholder?: string;
}

/**
 * Number field schema
 */
export interface NumberFieldSchema extends SchemaField {
    readonly type: 'number';
    readonly default?: number;
    readonly min?: number;
    readonly max?: number;
    readonly step?: number;
    readonly unit?: string;
}

/**
 * Boolean field schema
 */
export interface BooleanFieldSchema extends SchemaField {
    readonly type: 'boolean';
    readonly default?: boolean;
}

/**
 * Enum field schema
 */
export interface EnumFieldSchema extends SchemaField {
    readonly type: 'enum';
    readonly values: readonly string[];
    readonly default?: string;
    readonly labels?: Record<string, string>;
}

/**
 * Color field schema
 */
export interface ColorFieldSchema extends SchemaField {
    readonly type: 'color';
    readonly default?: string;
    readonly token?: string; // Design token reference
    readonly allowCustom?: boolean;
}

/**
 * Spacing field schema
 */
export interface SpacingFieldSchema extends SchemaField {
    readonly type: 'spacing';
    readonly default?: string;
    readonly tokens?: readonly string[];
}

/**
 * Binding field schema (for reactive connections)
 */
export interface BindingFieldSchema extends SchemaField {
    readonly type: 'binding';
    readonly bindingType: 'state' | 'expression' | 'action';
    readonly valueType?: string;
}

/**
 * Action field schema (for event handlers)
 */
export interface ActionFieldSchema extends SchemaField {
    readonly type: 'action';
    readonly parameters?: Record<string, SchemaField>;
}

/**
 * Object field schema (nested objects)
 */
export interface ObjectFieldSchema extends SchemaField {
    readonly type: 'object';
    readonly properties: Record<string, AnyFieldSchema>;
}

/**
 * Array field schema
 */
export interface ArrayFieldSchema extends SchemaField {
    readonly type: 'array';
    readonly items: AnyFieldSchema;
    readonly minItems?: number;
    readonly maxItems?: number;
}

/**
 * Union of all field schema types
 */
export type AnyFieldSchema =
    | StringFieldSchema
    | NumberFieldSchema
    | BooleanFieldSchema
    | EnumFieldSchema
    | ColorFieldSchema
    | SpacingFieldSchema
    | BindingFieldSchema
    | ActionFieldSchema
    | ObjectFieldSchema
    | ArrayFieldSchema;

/**
 * Schema for artifact properties
 */
export interface PropsSchema {
    readonly [key: string]: AnyFieldSchema;
}

/**
 * Schema for artifact styles
 */
export interface StyleSchema {
    readonly [key: string]: AnyFieldSchema;
}

// ============================================================================
// Contract 1: Artifact Contract
// ============================================================================

/**
 * Artifact capability flags
 */
export interface ArtifactCapabilities {
    /** Can be resized by the user */
    readonly resizable: boolean;
    /** Can accept child nodes */
    readonly droppable: boolean;
    /** Contains editable text content */
    readonly textEditable: boolean;
    /** Can be connected to other nodes (edges) */
    readonly connectable: boolean;
    /** Can be styled via inspector */
    readonly styleable: boolean;
    /** Supports data bindings */
    readonly bindable: boolean;
    /** Can be locked by user */
    readonly lockable: boolean;
    /** Can be copied/pasted */
    readonly copyable: boolean;
    /** Supports drill-down navigation */
    readonly drillable: boolean;
}

/**
 * Artifact constraints for validation and layout
 */
export interface ArtifactConstraints {
    /** Minimum width in pixels */
    readonly minWidth?: number;
    /** Maximum width in pixels */
    readonly maxWidth?: number;
    /** Minimum height in pixels */
    readonly minHeight?: number;
    /** Maximum height in pixels */
    readonly maxHeight?: number;
    /** Allowed children artifact kinds (empty = none) */
    readonly acceptsChildren: readonly ArtifactKind[] | boolean;
    /** Required parent artifact kinds (empty = any) */
    readonly requiresParent?: readonly ArtifactKind[];
    /** Maximum number of children */
    readonly maxChildren?: number;
    /** Maintain aspect ratio on resize */
    readonly aspectRatio?: number;
    /** Snap to grid size */
    readonly snapToGrid?: number;
}

/**
 * Artifact identity information
 */
export interface ArtifactIdentity {
    /** Unique artifact identifier */
    readonly artifactId: UniqueId;
    /** Kind string used in nodes (e.g., "ui:button") */
    readonly kind: ArtifactKind;
    /** Semantic version */
    readonly version: SemanticVersion;
    /** Human-readable name */
    readonly name: string;
    /** Category for palette organization */
    readonly category: string;
    /** Tags for search */
    readonly tags: readonly string[];
    /** Icon identifier or URL */
    readonly icon: string;
    /** Description for tooltip/docs */
    readonly description: string;
}

/**
 * Default node factory
 */
export interface ArtifactDefaults {
    /** Default props values */
    readonly props: Record<string, unknown>;
    /** Default style values */
    readonly style: Record<string, unknown>;
    /** Default dimensions */
    readonly width: number;
    readonly height: number;
    /** Default children (for containers) */
    readonly children?: readonly ArtifactKind[];
}

/**
 * The complete Artifact Contract
 * Defines what an artifact IS
 */
export interface ArtifactContract {
    /** Identity information */
    readonly identity: ArtifactIdentity;
    /** Property schema for inspector */
    readonly propsSchema: PropsSchema;
    /** Style schema for styling panel */
    readonly styleSchema: StyleSchema;
    /** Binding schema for data connections */
    readonly bindingsSchema?: PropsSchema;
    /** Capability flags */
    readonly capabilities: ArtifactCapabilities;
    /** Layout/validation constraints */
    readonly constraints: ArtifactConstraints;
    /** Default values for new instances */
    readonly defaults: ArtifactDefaults;
    /** Content modality */
    readonly modality: ContentModality;
    /** Platform compatibility */
    readonly platforms: readonly ('web' | 'desktop' | 'mobile')[];
    /** Platform-specific notes */
    readonly platformNotes?: string;
}

// ============================================================================
// Contract 2: Render Contract
// ============================================================================

/**
 * Render context provided to renderers
 */
export interface RenderContext {
    /** Current zoom level */
    readonly zoomLevel: ZoomLevel;
    /** Current zoom factor (0-1 for summary, 1+ for detail) */
    readonly zoomFactor: number;
    /** Is node selected */
    readonly isSelected: boolean;
    /** Is node hovered */
    readonly isHovered: boolean;
    /** Is node locked */
    readonly isLocked: boolean;
    /** Is in preview mode */
    readonly isPreview: boolean;
    /** Theme context */
    readonly theme: 'light' | 'dark';
    /** Viewport bounds */
    readonly viewportBounds: {
        readonly x: number;
        readonly y: number;
        readonly width: number;
        readonly height: number;
    };
}

/**
 * Props passed to canvas renderers
 */
export interface CanvasRendererProps<P = Record<string, unknown>, S = Record<string, unknown>> {
    /** Node ID */
    readonly nodeId: UniqueId;
    /** Node props */
    readonly props: P;
    /** Node styles */
    readonly style: S;
    /** Render context */
    readonly context: RenderContext;
    /** Children nodes */
    readonly children?: React.ReactNode;
    /** Event handlers */
    readonly onEvent?: (event: string, payload: unknown) => void;
}

/**
 * Props passed to preview renderers (palette)
 */
export interface PreviewRendererProps {
    /** Artifact kind */
    readonly kind: ArtifactKind;
    /** Preview size */
    readonly size: 'small' | 'medium' | 'large';
    /** Variant name if applicable */
    readonly variant?: string;
}

/**
 * Codegen options
 */
export interface CodegenOptions {
    /** Target format */
    readonly format: 'tsx' | 'html' | 'vue' | 'svelte';
    /** Include imports */
    readonly includeImports: boolean;
    /** Prettify output */
    readonly prettify: boolean;
    /** Include types */
    readonly includeTypes: boolean;
    /** State management style */
    readonly stateStyle: 'hooks' | 'zustand' | 'jotai' | 'none';
}

/**
 * Codegen result
 */
export interface CodegenResult {
    /** Generated code */
    readonly code: string;
    /** Required imports */
    readonly imports: readonly string[];
    /** Generated types (if applicable) */
    readonly types?: string;
    /** Warnings/notes */
    readonly warnings?: readonly string[];
}

/**
 * Export options
 */
export interface ExportOptions {
    /** Export format */
    readonly format: ExportFormat;
    /** Scale factor */
    readonly scale?: number;
    /** Background color */
    readonly background?: string;
    /** Include metadata */
    readonly includeMetadata?: boolean;
}

/**
 * Codegen function signature
 */
export type CodegenFn<P = Record<string, unknown>, S = Record<string, unknown>> = (
    props: P,
    style: S,
    options: CodegenOptions
) => CodegenResult;

/**
 * Export function signature
 */
export type ExportFn = (
    nodeId: UniqueId,
    options: ExportOptions
) => Promise<Blob | string>;

/**
 * The complete Render Contract
 * Defines how artifacts APPEAR
 */
export interface RenderContract<P = Record<string, unknown>, S = Record<string, unknown>> {
    /** Canvas renderer component */
    readonly canvasRenderer: ComponentType<CanvasRendererProps<P, S>>;
    /** Preview renderer for palette */
    readonly previewRenderer: ComponentType<PreviewRendererProps>;
    /** Outline renderer for layers panel */
    readonly outlineRenderer?: ComponentType<{ nodeId: UniqueId }>;
    /** Summary renderer for semantic zoom (zoomed out) */
    readonly summaryRenderer?: ComponentType<CanvasRendererProps<P, S>>;
    /** Code generators by target format */
    readonly codegen: Partial<Record<CodegenOptions['format'], CodegenFn<P, S>>>;
    /** Exporters by format */
    readonly exporters?: Partial<Record<ExportFormat, ExportFn>>;
}

// ============================================================================
// Contract 3: State Contract
// ============================================================================

/**
 * Binding reference types
 */
export type BindingRef =
    | { readonly type: 'static'; readonly value: unknown }
    | { readonly type: 'state'; readonly path: string }
    | { readonly type: 'expression'; readonly expr: string }
    | { readonly type: 'computed'; readonly key: string }
    | { readonly type: 'action'; readonly actionId: string; readonly params?: Record<string, unknown> };

/**
 * Event handler definition
 */
export interface EventHandler {
    /** Handler type */
    readonly type: 'action' | 'navigate' | 'setState' | 'custom';
    /** Action ID or handler reference */
    readonly handler: string;
    /** Parameters */
    readonly params?: Record<string, unknown>;
    /** Condition expression */
    readonly condition?: string;
}

/**
 * Node transform (position/size/rotation)
 */
export interface NodeTransform {
    readonly x: number;
    readonly y: number;
    readonly width: number;
    readonly height: number;
    readonly rotation: number;
    readonly zIndex: number;
    readonly scaleX?: number;
    readonly scaleY?: number;
}

/**
 * Node metadata
 */
export interface NodeMeta {
    readonly author: string;
    readonly createdAt: number;
    readonly lastEditedBy: string;
    readonly lastEditedAt: number;
}

/**
 * Annotation on a node
 */
export interface NodeAnnotation {
    readonly id: UniqueId;
    readonly type: 'comment' | 'highlight' | 'flag' | 'link';
    readonly content: string;
    readonly author: string;
    readonly createdAt: number;
    readonly resolved?: boolean;
}

/**
 * Multi-modal content wrapper
 */
export interface NodeContent {
    readonly type: ContentModality;
    readonly data: unknown;
    /** Language for code content */
    readonly language?: string;
    /** Encoding for binary content */
    readonly encoding?: string;
}

/**
 * The Universal Node - State Contract
 * Defines how data FLOWS
 */
export interface UniversalNode {
    // Identity
    readonly id: UniqueId;
    readonly kind: ArtifactKind;
    readonly name: string;

    // Data (drives behavior)
    readonly props: Record<string, unknown>;
    readonly style: Record<string, unknown>;
    readonly transform: NodeTransform;

    // Content (multi-modal)
    readonly content?: NodeContent;

    // Structure
    readonly parentId: UniqueId | null;
    readonly children: readonly UniqueId[];

    // Reactivity
    readonly bindings: Record<string, BindingRef>;
    readonly events: Record<string, EventHandler>;

    // Metadata
    readonly locked: boolean;
    readonly visible: boolean;
    readonly layer?: string;
    readonly tags: readonly string[];
    readonly annotations?: readonly NodeAnnotation[];

    // System
    readonly version: SemanticVersion;
    readonly runtime?: Record<string, unknown>;
    readonly meta: NodeMeta;
}

/**
 * Edge definition for diagram connections
 */
export interface UniversalEdge {
    readonly id: UniqueId;
    readonly sourceId: UniqueId;
    readonly targetId: UniqueId;
    readonly sourceHandle?: string;
    readonly targetHandle?: string;
    readonly kind: string;
    readonly props: Record<string, unknown>;
    readonly style: Record<string, unknown>;
    readonly meta: NodeMeta;
}

/**
 * Document resource (asset, style, symbol)
 */
export interface DocumentResource {
    readonly id: UniqueId;
    readonly type: 'asset' | 'style' | 'symbol' | 'component' | 'module';
    readonly name: string;
    readonly data: unknown;
    readonly meta: NodeMeta;
}

/**
 * View/Scene definition
 */
export interface DocumentView {
    readonly id: UniqueId;
    readonly name: string;
    readonly type: 'canvas' | 'artboard' | 'page' | 'layer';
    readonly rootNodes: readonly UniqueId[];
    readonly viewport: {
        readonly x: number;
        readonly y: number;
        readonly zoom: number;
    };
    readonly background?: string;
    readonly gridVisible?: boolean;
    readonly gridSize?: number;
}

/**
 * The Universal Document - State Contract
 */
export interface UniversalDocument {
    readonly id: UniqueId;
    readonly name: string;
    readonly version: SemanticVersion;

    // Content
    readonly nodes: Record<UniqueId, UniversalNode>;
    readonly edges: Record<UniqueId, UniversalEdge>;
    readonly resources: Record<UniqueId, DocumentResource>;
    readonly views: Record<UniqueId, DocumentView>;

    // Active state
    readonly activeViewId: UniqueId;

    // Metadata
    readonly meta: {
        readonly author: string;
        readonly createdAt: number;
        readonly lastEditedBy: string;
        readonly lastEditedAt: number;
        readonly tags: readonly string[];
        readonly description?: string;
    };
}

// ============================================================================
// Contract 4: Interaction Contract
// ============================================================================

/**
 * Tool definition
 */
export interface ToolDefinition {
    readonly id: string;
    readonly name: string;
    readonly icon: string;
    readonly shortcut?: string;
    readonly cursor: string;
    readonly group: 'select' | 'draw' | 'navigate' | 'annotate' | 'custom';
    readonly exclusive: boolean;
    readonly supportedModalities: readonly ContentModality[];
}

/**
 * Gesture definition
 */
export interface GestureDefinition {
    readonly id: string;
    readonly type: 'drag' | 'pinch' | 'rotate' | 'tap' | 'doubleTap' | 'longPress' | 'swipe';
    readonly modifiers?: readonly ('shift' | 'ctrl' | 'alt' | 'meta')[];
    readonly handler: string;
}

/**
 * Keyboard shortcut definition
 */
export interface ShortcutDefinition {
    readonly id: string;
    readonly keys: string;
    readonly action: string;
    readonly context?: 'canvas' | 'inspector' | 'palette' | 'global';
    readonly description: string;
}

/**
 * The Interaction Contract
 * Defines what users CAN DO
 */
export interface InteractionContract {
    /** Supported tools */
    readonly tools: readonly ToolDefinition[];
    /** Supported gestures */
    readonly gestures: readonly GestureDefinition[];
    /** Keyboard shortcuts */
    readonly shortcuts: readonly ShortcutDefinition[];
    /** Context menu items */
    readonly contextMenu?: readonly {
        readonly id: string;
        readonly label: string;
        readonly icon?: string;
        readonly action: string;
        readonly condition?: string;
    }[];
}

// ============================================================================
// Contract 5: Collaboration Contract
// ============================================================================

/**
 * Command types for undo/redo and collaboration
 */
export type CommandType =
    | 'InsertNode'
    | 'DeleteNode'
    | 'UpdateProps'
    | 'UpdateStyle'
    | 'UpdateTransform'
    | 'ReparentNode'
    | 'ReorderChildren'
    | 'InsertEdge'
    | 'DeleteEdge'
    | 'UpdateResource'
    | 'RenameResource'
    | 'BatchCommand';

/**
 * Base command structure
 */
export interface BaseCommand {
    readonly id: UniqueId;
    readonly type: CommandType;
    readonly timestamp: number;
    readonly userId: string;
}

/**
 * User presence state
 */
export interface UserPresence {
    readonly userId: string;
    readonly userName: string;
    readonly color: string;
    readonly cursor?: {
        readonly x: number;
        readonly y: number;
        readonly viewId: UniqueId;
    };
    readonly selection?: readonly UniqueId[];
    readonly activeTool?: string;
    readonly editingNodeId?: UniqueId;
    readonly lastSeen: number;
}

/**
 * Conflict resolution strategy
 */
export type ConflictStrategy = 'last-write-wins' | 'merge' | 'reject' | 'ask-user';

/**
 * The Collaboration Contract
 * Defines how changes SYNC
 */
export interface CollaborationContract {
    /** Supported command types */
    readonly supportedCommands: readonly CommandType[];
    /** Conflict resolution strategy */
    readonly conflictStrategy: ConflictStrategy;
    /** Presence update interval (ms) */
    readonly presenceInterval: number;
    /** Snapshot interval for persistence (ms) */
    readonly snapshotInterval: number;
    /** Maximum history depth */
    readonly maxHistoryDepth: number;
    /** Enable offline queue */
    readonly offlineEnabled: boolean;
}

// ============================================================================
// Artifact Definition (Complete)
// ============================================================================

/**
 * Complete Artifact Definition combining all contracts
 */
export interface ArtifactDefinition<
    P = Record<string, unknown>,
    S = Record<string, unknown>
> {
    /** Artifact contract */
    readonly artifact: ArtifactContract;
    /** Render contract */
    readonly render: RenderContract<P, S>;
    /** Interaction overrides (optional) */
    readonly interaction?: Partial<InteractionContract>;
}

// ============================================================================
// Extension Interfaces
// ============================================================================

/**
 * Validation function signature
 */
export type ValidationFn = (node: UniversalNode, context: UniversalDocument) => {
    readonly valid: boolean;
    readonly errors?: readonly string[];
    readonly warnings?: readonly string[];
};

/**
 * Transform function signature (for migrations)
 */
export type TransformFn = (node: UniversalNode) => UniversalNode;

/**
 * Binding resolver signature
 */
export type BindingResolver = (binding: BindingRef, context: Record<string, unknown>) => unknown;

/**
 * Computed value function signature
 */
export type ComputeFn = (node: UniversalNode, document: UniversalDocument) => unknown;

/**
 * Middleware function signature
 */
export type Middleware = (
    command: BaseCommand,
    next: (cmd: BaseCommand) => void
) => void;

// ============================================================================
// Default Implementations
// ============================================================================

/**
 * Default artifact capabilities
 */
export const DEFAULT_CAPABILITIES: ArtifactCapabilities = {
    resizable: true,
    droppable: false,
    textEditable: false,
    connectable: false,
    styleable: true,
    bindable: true,
    lockable: true,
    copyable: true,
    drillable: false,
};

/**
 * Default artifact constraints
 */
export const DEFAULT_CONSTRAINTS: ArtifactConstraints = {
    minWidth: 20,
    minHeight: 20,
    acceptsChildren: false,
};

/**
 * Create a default node transform
 */
export function createDefaultTransform(
    x = 0,
    y = 0,
    width = 100,
    height = 40
): NodeTransform {
    return {
        x,
        y,
        width,
        height,
        rotation: 0,
        zIndex: 0,
    };
}

/**
 * Create default node metadata
 */
export function createDefaultMeta(userId: string): NodeMeta {
    const now = Date.now();
    return {
        author: userId,
        createdAt: now,
        lastEditedBy: userId,
        lastEditedAt: now,
    };
}

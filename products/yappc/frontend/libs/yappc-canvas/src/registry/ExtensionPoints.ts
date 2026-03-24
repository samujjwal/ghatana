/**
 * Extension Points
 *
 * Defines extension interfaces for customizing artifact behavior.
 * Allows plugins to extend rendering, validation, and transformation.
 *
 * @doc.type interfaces
 * @doc.purpose Extension point definitions
 * @doc.layer core
 * @doc.pattern Extension Point, Strategy
 */

import type { ComponentType, ReactNode } from 'react';
import type {
    ArtifactContract,
    ArtifactKind,
    UniversalNode,
    ContentModality,
    ZoomLevel,
    ExportFormat,
    BindingRef,
} from '../model/contracts';

// ============================================================================
// Render Extension Points
// ============================================================================

/**
 * Props passed to artifact renderers
 */
export interface ArtifactRenderProps {
    /** The node being rendered */
    node: UniversalNode;
    /** The artifact contract */
    contract: ArtifactContract;
    /** Current zoom level */
    zoomLevel: ZoomLevel;
    /** Whether the node is selected */
    selected: boolean;
    /** Whether the node is being hovered */
    hovered: boolean;
    /** Whether the node is being edited */
    editing: boolean;
    /** Whether the node is a drop target */
    dropTarget: boolean;
    /** Resolved binding values */
    resolvedBindings: Record<string, unknown>;
    /** Children to render (for container nodes) */
    children?: ReactNode;
    /** Callback when props change */
    onPropsChange: (props: Record<string, unknown>) => void;
    /** Callback when style changes */
    onStyleChange: (style: Record<string, unknown>) => void;
    /** Callback to start editing */
    onStartEdit: () => void;
    /** Callback to stop editing */
    onStopEdit: () => void;
}

/**
 * Artifact renderer component type
 */
export type ArtifactRenderer = ComponentType<ArtifactRenderProps>;

/**
 * Render extension for customizing how artifacts are rendered
 */
export interface RenderExtension {
    /** Extension ID */
    id: string;
    /** Target artifact kind (or '*' for all) */
    targetKind: ArtifactKind | '*';
    /** Priority (higher = later, can override) */
    priority: number;
    /** Custom renderer component */
    renderer?: ArtifactRenderer;
    /** Wrapper component */
    wrapper?: ComponentType<{ children: ReactNode; node: UniversalNode }>;
    /** Custom overlay component */
    overlay?: ComponentType<{ node: UniversalNode; selected: boolean }>;
    /** Zoom level specific renderers */
    zoomRenderers?: Partial<Record<ZoomLevel, ArtifactRenderer>>;
}

// ============================================================================
// Validation Extension Points
// ============================================================================

/**
 * Validation context
 */
export interface ValidationContext {
    /** The document being validated */
    document: { nodes: Record<string, UniversalNode> };
    /** The current node being validated */
    node: UniversalNode;
    /** Parent node (if any) */
    parent?: UniversalNode;
    /** Sibling nodes */
    siblings: UniversalNode[];
    /** Path from root */
    path: string[];
}

/**
 * Validation result
 */
export interface ValidationResult {
    /** Whether validation passed */
    valid: boolean;
    /** Error messages */
    errors: ValidationError[];
    /** Warning messages */
    warnings: ValidationWarning[];
}

/**
 * Validation error
 */
export interface ValidationError {
    /** Error code */
    code: string;
    /** Error message */
    message: string;
    /** Property path causing the error */
    path?: string;
    /** Severity */
    severity: 'error';
}

/**
 * Validation warning
 */
export interface ValidationWarning {
    /** Warning code */
    code: string;
    /** Warning message */
    message: string;
    /** Property path causing the warning */
    path?: string;
    /** Severity */
    severity: 'warning';
}

/**
 * Validation extension for custom validation logic
 */
export interface ValidationExtension {
    /** Extension ID */
    id: string;
    /** Target artifact kind (or '*' for all) */
    targetKind: ArtifactKind | '*';
    /** Priority */
    priority: number;
    /** Validate the node */
    validate: (context: ValidationContext) => ValidationResult;
    /** Whether this validator can auto-fix issues */
    canAutoFix?: boolean;
    /** Auto-fix function */
    autoFix?: (context: ValidationContext) => UniversalNode;
}

// ============================================================================
// Transform Extension Points
// ============================================================================

/**
 * Transform context
 */
export interface TransformContext {
    /** Source node */
    source: UniversalNode;
    /** Source contract */
    sourceContract: ArtifactContract;
    /** Target kind */
    targetKind: ArtifactKind;
    /** Target contract */
    targetContract: ArtifactContract;
}

/**
 * Transform result
 */
export interface TransformResult {
    /** Whether transform succeeded */
    success: boolean;
    /** Transformed node */
    node?: UniversalNode;
    /** Error if failed */
    error?: string;
    /** Data loss warnings */
    dataLoss?: string[];
}

/**
 * Transform extension for converting between artifact types
 */
export interface TransformExtension {
    /** Extension ID */
    id: string;
    /** Source artifact kind */
    sourceKind: ArtifactKind;
    /** Target artifact kind */
    targetKind: ArtifactKind;
    /** Priority */
    priority: number;
    /** Check if transform is possible */
    canTransform: (context: TransformContext) => boolean;
    /** Perform the transform */
    transform: (context: TransformContext) => TransformResult;
}

// ============================================================================
// Binding Extension Points
// ============================================================================

/**
 * Binding resolution context
 */
export interface BindingContext {
    /** The node with bindings */
    node: UniversalNode;
    /** The binding reference */
    binding: BindingRef;
    /** Current state */
    state: Record<string, unknown>;
    /** Expression evaluation function */
    evaluate: (expr: string) => unknown;
}

/**
 * Binding extension for custom binding resolution
 */
export interface BindingExtension {
    /** Extension ID */
    id: string;
    /** Binding type this handles */
    bindingType: BindingRef['type'];
    /** Priority */
    priority: number;
    /** Resolve the binding to a value */
    resolve: (context: BindingContext) => unknown;
    /** Whether this binding is reactive */
    reactive: boolean;
    /** Subscribe to binding changes */
    subscribe?: (
        context: BindingContext,
        callback: (value: unknown) => void
    ) => () => void;
}

// ============================================================================
// Export Extension Points
// ============================================================================

/**
 * Export context
 */
export interface ExportContext {
    /** Nodes to export */
    nodes: UniversalNode[];
    /** Contracts for nodes */
    contracts: Map<string, ArtifactContract>;
    /** Export format */
    format: ExportFormat;
    /** Export options */
    options: ExportOptions;
}

/**
 * Export options
 */
export interface ExportOptions {
    /** Include children */
    includeChildren?: boolean;
    /** Include metadata */
    includeMetadata?: boolean;
    /** Minify output */
    minify?: boolean;
    /** Pretty print */
    pretty?: boolean;
    /** Custom variables */
    variables?: Record<string, unknown>;
    /** Target platform */
    platform?: 'web' | 'desktop' | 'mobile';
}

/**
 * Export result
 */
export interface ExportResult {
    /** Whether export succeeded */
    success: boolean;
    /** Export output */
    output?: string | Uint8Array;
    /** MIME type */
    mimeType?: string;
    /** File extension */
    extension?: string;
    /** Error if failed */
    error?: string;
    /** Additional files (for multi-file exports) */
    additionalFiles?: Array<{
        name: string;
        content: string | Uint8Array;
        mimeType: string;
    }>;
}

/**
 * Export extension for custom export formats
 */
export interface ExportExtension {
    /** Extension ID */
    id: string;
    /** Export format this handles */
    format: ExportFormat | string;
    /** Supported artifact kinds (or '*' for all) */
    supportedKinds: ArtifactKind[] | '*';
    /** Priority */
    priority: number;
    /** Export the nodes */
    export: (context: ExportContext) => Promise<ExportResult>;
}

// ============================================================================
// Import Extension Points
// ============================================================================

/**
 * Import context
 */
export interface ImportContext {
    /** Input data */
    input: string | Uint8Array | File;
    /** Input format hint */
    format?: string;
    /** MIME type */
    mimeType?: string;
    /** Import options */
    options: ImportOptions;
}

/**
 * Import options
 */
export interface ImportOptions {
    /** Generate new IDs */
    generateNewIds?: boolean;
    /** Parent node to import into */
    parentId?: string;
    /** Position offset */
    offset?: { x: number; y: number };
    /** Validate after import */
    validate?: boolean;
}

/**
 * Import result
 */
export interface ImportResult {
    /** Whether import succeeded */
    success: boolean;
    /** Imported nodes */
    nodes?: UniversalNode[];
    /** Error if failed */
    error?: string;
    /** Warnings */
    warnings?: string[];
}

/**
 * Import extension for custom import formats
 */
export interface ImportExtension {
    /** Extension ID */
    id: string;
    /** Import format this handles */
    format: string;
    /** Supported MIME types */
    mimeTypes: string[];
    /** File extensions */
    extensions: string[];
    /** Priority */
    priority: number;
    /** Check if input can be imported */
    canImport: (context: ImportContext) => boolean;
    /** Import the data */
    import: (context: ImportContext) => Promise<ImportResult>;
}

// ============================================================================
// Tool Extension Points
// ============================================================================

/**
 * Tool context
 */
export interface ToolContext {
    /** Active canvas view */
    viewId: string;
    /** Current selection */
    selection: UniversalNode[];
    /** Current zoom */
    zoom: number;
    /** Current tool state */
    toolState: Record<string, unknown>;
}

/**
 * Tool event
 */
export interface ToolEvent {
    /** Event type */
    type:
    | 'pointerDown'
    | 'pointerMove'
    | 'pointerUp'
    | 'keyDown'
    | 'keyUp'
    | 'wheel';
    /** Canvas position */
    canvasX: number;
    canvasY: number;
    /** Screen position */
    screenX: number;
    screenY: number;
    /** Modifier keys */
    modifiers: {
        shift: boolean;
        ctrl: boolean;
        alt: boolean;
        meta: boolean;
    };
    /** For key events */
    key?: string;
    /** For wheel events */
    deltaX?: number;
    deltaY?: number;
    /** Target node (if any) */
    targetNode?: UniversalNode;
    /** Original DOM event */
    originalEvent: Event;
}

/**
 * Tool extension for custom tools
 */
export interface ToolExtension {
    /** Tool ID */
    id: string;
    /** Tool name */
    name: string;
    /** Tool icon */
    icon: string;
    /** Keyboard shortcut */
    shortcut?: string;
    /** Tool group */
    group: 'select' | 'draw' | 'navigate' | 'annotate' | 'custom';
    /** Cursor CSS */
    cursor: string;
    /** Supported modalities */
    modalities: ContentModality[] | '*';
    /** Priority */
    priority: number;
    /** Activate the tool */
    activate: (context: ToolContext) => void;
    /** Deactivate the tool */
    deactivate: (context: ToolContext) => void;
    /** Handle a tool event */
    handleEvent: (event: ToolEvent, context: ToolContext) => void;
    /** Render tool UI overlay */
    renderOverlay?: ComponentType<{ context: ToolContext }>;
}

// ============================================================================
// Extension Registry
// ============================================================================

/**
 * Extension registry interface
 */
export interface IExtensionRegistry {
    // Render extensions
    registerRenderExtension(ext: RenderExtension): void;
    unregisterRenderExtension(id: string): void;
    getRenderExtensions(kind: ArtifactKind): RenderExtension[];

    // Validation extensions
    registerValidationExtension(ext: ValidationExtension): void;
    unregisterValidationExtension(id: string): void;
    getValidationExtensions(kind: ArtifactKind): ValidationExtension[];

    // Transform extensions
    registerTransformExtension(ext: TransformExtension): void;
    unregisterTransformExtension(id: string): void;
    getTransformExtensions(
        source: ArtifactKind,
        target: ArtifactKind
    ): TransformExtension[];

    // Binding extensions
    registerBindingExtension(ext: BindingExtension): void;
    unregisterBindingExtension(id: string): void;
    getBindingExtension(type: BindingRef['type']): BindingExtension | undefined;

    // Export extensions
    registerExportExtension(ext: ExportExtension): void;
    unregisterExportExtension(id: string): void;
    getExportExtensions(format: ExportFormat | string): ExportExtension[];

    // Import extensions
    registerImportExtension(ext: ImportExtension): void;
    unregisterImportExtension(id: string): void;
    getImportExtensions(format: string): ImportExtension[];

    // Tool extensions
    registerToolExtension(ext: ToolExtension): void;
    unregisterToolExtension(id: string): void;
    getToolExtensions(): ToolExtension[];
    getToolExtension(id: string): ToolExtension | undefined;
}

/**
 * Extension registry implementation
 */
export class ExtensionRegistry implements IExtensionRegistry {
    private static instance: ExtensionRegistry | null = null;

    private renderExtensions: Map<string, RenderExtension> = new Map();
    private validationExtensions: Map<string, ValidationExtension> = new Map();
    private transformExtensions: Map<string, TransformExtension> = new Map();
    private bindingExtensions: Map<string, BindingExtension> = new Map();
    private exportExtensions: Map<string, ExportExtension> = new Map();
    private importExtensions: Map<string, ImportExtension> = new Map();
    private toolExtensions: Map<string, ToolExtension> = new Map();

    private constructor() { }

    static getInstance(): ExtensionRegistry {
        if (!ExtensionRegistry.instance) {
            ExtensionRegistry.instance = new ExtensionRegistry();
        }
        return ExtensionRegistry.instance;
    }

    static resetInstance(): void {
        ExtensionRegistry.instance = null;
    }

    // Render extensions
    registerRenderExtension(ext: RenderExtension): void {
        this.renderExtensions.set(ext.id, ext);
    }

    unregisterRenderExtension(id: string): void {
        this.renderExtensions.delete(id);
    }

    getRenderExtensions(kind: ArtifactKind): RenderExtension[] {
        return Array.from(this.renderExtensions.values())
            .filter((ext) => ext.targetKind === '*' || ext.targetKind === kind)
            .sort((a, b) => a.priority - b.priority);
    }

    // Validation extensions
    registerValidationExtension(ext: ValidationExtension): void {
        this.validationExtensions.set(ext.id, ext);
    }

    unregisterValidationExtension(id: string): void {
        this.validationExtensions.delete(id);
    }

    getValidationExtensions(kind: ArtifactKind): ValidationExtension[] {
        return Array.from(this.validationExtensions.values())
            .filter((ext) => ext.targetKind === '*' || ext.targetKind === kind)
            .sort((a, b) => a.priority - b.priority);
    }

    // Transform extensions
    registerTransformExtension(ext: TransformExtension): void {
        this.transformExtensions.set(ext.id, ext);
    }

    unregisterTransformExtension(id: string): void {
        this.transformExtensions.delete(id);
    }

    getTransformExtensions(
        source: ArtifactKind,
        target: ArtifactKind
    ): TransformExtension[] {
        return Array.from(this.transformExtensions.values())
            .filter(
                (ext) => ext.sourceKind === source && ext.targetKind === target
            )
            .sort((a, b) => a.priority - b.priority);
    }

    // Binding extensions
    registerBindingExtension(ext: BindingExtension): void {
        this.bindingExtensions.set(ext.id, ext);
    }

    unregisterBindingExtension(id: string): void {
        this.bindingExtensions.delete(id);
    }

    getBindingExtension(type: BindingRef['type']): BindingExtension | undefined {
        return Array.from(this.bindingExtensions.values())
            .filter((ext) => ext.bindingType === type)
            .sort((a, b) => b.priority - a.priority)[0];
    }

    // Export extensions
    registerExportExtension(ext: ExportExtension): void {
        this.exportExtensions.set(ext.id, ext);
    }

    unregisterExportExtension(id: string): void {
        this.exportExtensions.delete(id);
    }

    getExportExtensions(format: ExportFormat | string): ExportExtension[] {
        return Array.from(this.exportExtensions.values())
            .filter((ext) => ext.format === format)
            .sort((a, b) => a.priority - b.priority);
    }

    // Import extensions
    registerImportExtension(ext: ImportExtension): void {
        this.importExtensions.set(ext.id, ext);
    }

    unregisterImportExtension(id: string): void {
        this.importExtensions.delete(id);
    }

    getImportExtensions(format: string): ImportExtension[] {
        return Array.from(this.importExtensions.values())
            .filter(
                (ext) =>
                    ext.format === format ||
                    ext.extensions.includes(format) ||
                    ext.mimeTypes.includes(format)
            )
            .sort((a, b) => a.priority - b.priority);
    }

    // Tool extensions
    registerToolExtension(ext: ToolExtension): void {
        this.toolExtensions.set(ext.id, ext);
    }

    unregisterToolExtension(id: string): void {
        this.toolExtensions.delete(id);
    }

    getToolExtensions(): ToolExtension[] {
        return Array.from(this.toolExtensions.values()).sort(
            (a, b) => a.priority - b.priority
        );
    }

    getToolExtension(id: string): ToolExtension | undefined {
        return this.toolExtensions.get(id);
    }
}

// ============================================================================
// Convenience Functions
// ============================================================================

/**
 * Get the global extension registry
 */
export function getExtensionRegistry(): ExtensionRegistry {
    return ExtensionRegistry.getInstance();
}

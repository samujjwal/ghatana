/**
 * Command Pattern Types
 *
 * Defines the command pattern interfaces for undo/redo and collaboration.
 * All canvas mutations MUST go through commands for audit and sync.
 *
 * @doc.type interfaces
 * @doc.purpose Command pattern type definitions
 * @doc.layer core
 * @doc.pattern Command Pattern
 */

import type { UniqueId, SemanticVersion } from './contracts';

// ============================================================================
// Command Types
// ============================================================================

/**
 * All supported command types
 */
export type CommandType =
    | 'InsertNode'
    | 'DeleteNode'
    | 'UpdateProps'
    | 'UpdateStyle'
    | 'UpdateTransform'
    | 'UpdateContent'
    | 'ReparentNode'
    | 'ReorderChildren'
    | 'InsertEdge'
    | 'DeleteEdge'
    | 'UpdateEdge'
    | 'UpdateResource'
    | 'RenameResource'
    | 'DeleteResource'
    | 'CreateView'
    | 'DeleteView'
    | 'UpdateViewport'
    | 'BatchCommand'
    | 'Custom';

/**
 * Command metadata for audit trail
 */
export interface CommandMeta {
    /** Unique command ID */
    readonly commandId: UniqueId;
    /** User who issued the command */
    readonly userId: string;
    /** Timestamp of command creation */
    readonly timestamp: number;
    /** Optional description for audit */
    readonly description?: string;
    /** Source of command (user, ai, sync) */
    readonly source: 'user' | 'ai' | 'sync' | 'system';
    /** Session ID for grouping */
    readonly sessionId: string;
    /** Causality: ID of command this is in response to */
    readonly causedBy?: UniqueId;
}

/**
 * Command result type
 */
export interface CommandResult<T = unknown> {
    /** Whether command succeeded */
    readonly success: boolean;
    /** Result data on success */
    readonly data?: T;
    /** Error message on failure */
    readonly error?: string;
    /** Error code for programmatic handling */
    readonly errorCode?: string;
    /** New document version after command */
    readonly newVersion?: SemanticVersion;
}

/**
 * Base command interface
 */
export interface Command<TPayload = unknown, TResult = unknown> {
    /** Command type discriminator */
    readonly type: CommandType;
    /** Command metadata */
    readonly meta: CommandMeta;
    /** Command payload */
    readonly payload: TPayload;
    /** Execute the command */
    execute(): Promise<CommandResult<TResult>>;
    /** Undo the command */
    undo(): Promise<CommandResult<TResult>>;
    /** Can this command be merged with another? */
    canMerge(other: Command): boolean;
    /** Merge with another command of same type */
    merge(other: Command): Command<TPayload, TResult>;
    /** Validate command before execution */
    validate(): boolean;
    /** Serialize for transmission */
    serialize(): SerializedCommand;
}

/**
 * Serialized command for persistence and sync
 */
export interface SerializedCommand {
    readonly type: CommandType;
    readonly meta: CommandMeta;
    readonly payload: unknown;
    readonly previousState?: unknown;
    readonly checksum?: string;
}

// ============================================================================
// Node Command Payloads
// ============================================================================

/**
 * Insert node command payload
 */
export interface InsertNodePayload {
    readonly nodeId: UniqueId;
    readonly kind: string;
    readonly parentId: UniqueId | null;
    readonly index?: number;
    readonly props: Record<string, unknown>;
    readonly style: Record<string, unknown>;
    readonly transform: {
        readonly x: number;
        readonly y: number;
        readonly width: number;
        readonly height: number;
        readonly rotation?: number;
        readonly zIndex?: number;
    };
    readonly content?: {
        readonly type: string;
        readonly data: unknown;
    };
}

/**
 * Delete node command payload
 */
export interface DeleteNodePayload {
    readonly nodeId: UniqueId;
    /** Whether to cascade delete children */
    readonly cascade: boolean;
    /** Reparent children to this node instead of deleting */
    readonly reparentChildrenTo?: UniqueId | null;
}

/**
 * Update props command payload
 */
export interface UpdatePropsPayload {
    readonly nodeId: UniqueId;
    /** Props to update (merge) */
    readonly props: Record<string, unknown>;
    /** Props to remove */
    readonly remove?: string[];
}

/**
 * Update style command payload
 */
export interface UpdateStylePayload {
    readonly nodeId: UniqueId;
    /** Styles to update (merge) */
    readonly style: Record<string, unknown>;
    /** Styles to remove */
    readonly remove?: string[];
}

/**
 * Update transform command payload
 */
export interface UpdateTransformPayload {
    readonly nodeId: UniqueId;
    readonly transform: {
        readonly x?: number;
        readonly y?: number;
        readonly width?: number;
        readonly height?: number;
        readonly rotation?: number;
        readonly zIndex?: number;
        readonly scaleX?: number;
        readonly scaleY?: number;
    };
}

/**
 * Update content command payload
 */
export interface UpdateContentPayload {
    readonly nodeId: UniqueId;
    readonly content: {
        readonly type?: string;
        readonly data: unknown;
        readonly language?: string;
    };
}

/**
 * Reparent node command payload
 */
export interface ReparentNodePayload {
    readonly nodeId: UniqueId;
    readonly newParentId: UniqueId | null;
    /** Index in new parent's children array */
    readonly index?: number;
    /** Preserve world position when reparenting */
    readonly preserveWorldPosition?: boolean;
}

/**
 * Reorder children command payload
 */
export interface ReorderChildrenPayload {
    readonly parentId: UniqueId;
    /** New order of child IDs */
    readonly children: readonly UniqueId[];
}

// ============================================================================
// Edge Command Payloads
// ============================================================================

/**
 * Insert edge command payload
 */
export interface InsertEdgePayload {
    readonly edgeId: UniqueId;
    readonly sourceId: UniqueId;
    readonly targetId: UniqueId;
    readonly sourceHandle?: string;
    readonly targetHandle?: string;
    readonly kind: string;
    readonly props?: Record<string, unknown>;
    readonly style?: Record<string, unknown>;
}

/**
 * Delete edge command payload
 */
export interface DeleteEdgePayload {
    readonly edgeId: UniqueId;
}

/**
 * Update edge command payload
 */
export interface UpdateEdgePayload {
    readonly edgeId: UniqueId;
    readonly props?: Record<string, unknown>;
    readonly style?: Record<string, unknown>;
    readonly sourceId?: UniqueId;
    readonly targetId?: UniqueId;
    readonly sourceHandle?: string;
    readonly targetHandle?: string;
}

// ============================================================================
// Resource Command Payloads
// ============================================================================

/**
 * Update resource command payload
 */
export interface UpdateResourcePayload {
    readonly resourceId: UniqueId;
    readonly type: 'asset' | 'style' | 'symbol' | 'component' | 'module';
    readonly name?: string;
    readonly data: unknown;
}

/**
 * Rename resource command payload
 */
export interface RenameResourcePayload {
    readonly resourceId: UniqueId;
    readonly newName: string;
}

/**
 * Delete resource command payload
 */
export interface DeleteResourcePayload {
    readonly resourceId: UniqueId;
}

// ============================================================================
// View Command Payloads
// ============================================================================

/**
 * Create view command payload
 */
export interface CreateViewPayload {
    readonly viewId: UniqueId;
    readonly name: string;
    readonly type: 'canvas' | 'artboard' | 'page' | 'layer';
    readonly rootNodes?: readonly UniqueId[];
    readonly viewport?: {
        readonly x: number;
        readonly y: number;
        readonly zoom: number;
    };
}

/**
 * Delete view command payload
 */
export interface DeleteViewPayload {
    readonly viewId: UniqueId;
    /** Move nodes to this view instead of deleting */
    readonly migrateNodesTo?: UniqueId;
}

/**
 * Update viewport command payload
 */
export interface UpdateViewportPayload {
    readonly viewId: UniqueId;
    readonly viewport: {
        readonly x?: number;
        readonly y?: number;
        readonly zoom?: number;
    };
}

// ============================================================================
// Batch Command Payload
// ============================================================================

/**
 * Batch command payload
 */
export interface BatchCommandPayload {
    readonly commands: readonly SerializedCommand[];
    /** Atomic: all succeed or all fail */
    readonly atomic: boolean;
    /** Stop on first error */
    readonly stopOnError: boolean;
}

// ============================================================================
// Custom Command Payload
// ============================================================================

/**
 * Custom command payload for extensibility
 */
export interface CustomCommandPayload {
    readonly customType: string;
    readonly data: unknown;
    readonly handlerId: string;
}

// ============================================================================
// Command Factory Types
// ============================================================================

/**
 * Command factory interface
 */
export interface CommandFactory<TPayload, TResult = unknown> {
    /** Create a new command */
    create(payload: TPayload, meta: Partial<CommandMeta>): Command<TPayload, TResult>;
    /** Deserialize a command */
    deserialize(serialized: SerializedCommand): Command<TPayload, TResult>;
    /** Get command type */
    readonly type: CommandType;
}

/**
 * Command handler for custom commands
 */
export interface CustomCommandHandler {
    /** Handler ID */
    readonly id: string;
    /** Handle a custom command */
    handle(command: Command<CustomCommandPayload>): Promise<CommandResult>;
    /** Undo a custom command */
    undo(command: Command<CustomCommandPayload>): Promise<CommandResult>;
    /** Validate a custom command */
    validate(payload: CustomCommandPayload): boolean;
}

// ============================================================================
// Command Bus Types
// ============================================================================

/**
 * Command listener callback
 */
export type CommandListener = (
    command: Command,
    result: CommandResult
) => void;

/**
 * Command interceptor for middleware
 */
export interface CommandInterceptor {
    /** Unique interceptor ID */
    readonly id: string;
    /** Priority (lower = earlier) */
    readonly priority: number;
    /** Intercept before execution */
    before?(command: Command): Command | null;
    /** Intercept after execution */
    after?(command: Command, result: CommandResult): void;
    /** Handle errors */
    onError?(command: Command, error: Error): void;
}

/**
 * Command dispatcher interface
 */
export interface ICommandDispatcher {
    /** Execute a command */
    execute<T>(command: Command<unknown, T>): Promise<CommandResult<T>>;
    /** Undo last command */
    undo(): Promise<CommandResult>;
    /** Redo last undone command */
    redo(): Promise<CommandResult>;
    /** Check if undo is available */
    canUndo(): boolean;
    /** Check if redo is available */
    canRedo(): boolean;
    /** Get undo stack depth */
    getUndoDepth(): number;
    /** Get redo stack depth */
    getRedoDepth(): number;
    /** Subscribe to command events */
    subscribe(listener: CommandListener): () => void;
    /** Add an interceptor */
    addInterceptor(interceptor: CommandInterceptor): void;
    /** Remove an interceptor */
    removeInterceptor(id: string): void;
    /** Clear history */
    clearHistory(): void;
    /** Get command history */
    getHistory(): readonly SerializedCommand[];
}

// ============================================================================
// Type Guards
// ============================================================================

/**
 * Type guard for insert node payload
 */
export function isInsertNodePayload(payload: unknown): payload is InsertNodePayload {
    return (
        typeof payload === 'object' &&
        payload !== null &&
        'nodeId' in payload &&
        'kind' in payload &&
        'transform' in payload
    );
}

/**
 * Type guard for delete node payload
 */
export function isDeleteNodePayload(payload: unknown): payload is DeleteNodePayload {
    return (
        typeof payload === 'object' &&
        payload !== null &&
        'nodeId' in payload &&
        'cascade' in payload
    );
}

/**
 * Type guard for batch command payload
 */
export function isBatchCommandPayload(payload: unknown): payload is BatchCommandPayload {
    return (
        typeof payload === 'object' &&
        payload !== null &&
        'commands' in payload &&
        Array.isArray((payload as BatchCommandPayload).commands)
    );
}

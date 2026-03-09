/**
 * Node CRUD Commands
 *
 * Command implementations for all node operations.
 * These are the primary commands for manipulating canvas nodes.
 *
 * @doc.type commands
 * @doc.purpose Node manipulation command implementations
 * @doc.layer core
 * @doc.pattern Command Pattern
 */

import { nanoid } from 'nanoid';
import type {
    Command,
    CommandMeta,
    CommandResult,
    SerializedCommand,
    InsertNodePayload,
    DeleteNodePayload,
    UpdatePropsPayload,
    UpdateStylePayload,
    UpdateTransformPayload,
    UpdateContentPayload,
    ReparentNodePayload,
    ReorderChildrenPayload,
} from '../CommandTypes';
import type { UniversalNode, UniqueId } from '../../model/contracts';

// ============================================================================
// Document Store Interface
// ============================================================================

/**
 * Interface for the document store that commands operate on.
 * This is injected to allow different implementations.
 */
export interface IDocumentStore {
    /** Get a node by ID */
    getNode(id: UniqueId): UniversalNode | undefined;
    /** Insert a new node */
    insertNode(node: UniversalNode): void;
    /** Delete a node */
    deleteNode(id: UniqueId): UniversalNode | undefined;
    /** Update node props */
    updateNodeProps(id: UniqueId, props: Record<string, unknown>): void;
    /** Update node style */
    updateNodeStyle(id: UniqueId, style: Record<string, unknown>): void;
    /** Update node transform */
    updateNodeTransform(
        id: UniqueId,
        transform: Partial<UniversalNode['transform']>
    ): void;
    /** Update node content */
    updateNodeContent(
        id: UniqueId,
        content: UniversalNode['content']
    ): void;
    /** Reparent a node */
    reparentNode(id: UniqueId, newParentId: UniqueId | null, index?: number): void;
    /** Reorder children */
    reorderChildren(parentId: UniqueId, children: readonly UniqueId[]): void;
    /** Get all descendant IDs */
    getDescendantIds(id: UniqueId): UniqueId[];
}

// Singleton store reference - set during initialization
let documentStore: IDocumentStore | null = null;

/**
 * Initialize the document store for commands
 */
export function initializeDocumentStore(store: IDocumentStore): void {
    documentStore = store;
}

/**
 * Get the document store
 */
function getStore(): IDocumentStore {
    if (!documentStore) {
        throw new Error('Document store not initialized. Call initializeDocumentStore first.');
    }
    return documentStore;
}

// ============================================================================
// Base Command Implementation
// ============================================================================

/**
 * Abstract base class for node commands
 */
abstract class BaseNodeCommand<TPayload, TResult = void>
    implements Command<TPayload, TResult> {
    abstract readonly type:
        | 'InsertNode'
        | 'DeleteNode'
        | 'UpdateProps'
        | 'UpdateStyle'
        | 'UpdateTransform'
        | 'UpdateContent'
        | 'ReparentNode'
        | 'ReorderChildren';
    readonly meta: CommandMeta;
    readonly payload: TPayload;
    protected previousState?: unknown;

    constructor(payload: TPayload, meta: CommandMeta) {
        this.payload = payload;
        this.meta = meta;
    }

    abstract execute(): Promise<CommandResult<TResult>>;
    abstract undo(): Promise<CommandResult<TResult>>;

    canMerge(other: Command): boolean {
        // By default, commands of the same type on the same node can merge
        return (
            other.type === this.type &&
            this.getTargetNodeId() === (other as BaseNodeCommand<unknown>).getTargetNodeId()
        );
    }

    merge(other: Command): Command<TPayload, TResult> {
        // Default: just keep the latest command
        return this;
    }

    validate(): boolean {
        return true;
    }

    serialize(): SerializedCommand {
        return {
            type: this.type,
            meta: this.meta,
            payload: this.payload,
            previousState: this.previousState,
        };
    }

    protected abstract getTargetNodeId(): UniqueId;
}

// ============================================================================
// Insert Node Command
// ============================================================================

/**
 * Command to insert a new node
 */
export class InsertNodeCommand extends BaseNodeCommand<InsertNodePayload, UniversalNode> {
    readonly type = 'InsertNode' as const;

    async execute(): Promise<CommandResult<UniversalNode>> {
        const store = getStore();

        try {
            // Check if parent exists (if specified)
            if (this.payload.parentId) {
                const parent = store.getNode(this.payload.parentId);
                if (!parent) {
                    return {
                        success: false,
                        error: `Parent node ${this.payload.parentId} not found`,
                        errorCode: 'PARENT_NOT_FOUND',
                    };
                }
            }

            // Create the new node
            const now = Date.now();
            const newNode: UniversalNode = {
                id: this.payload.nodeId,
                kind: this.payload.kind,
                name: this.payload.kind.split(':')[1] || 'Node',
                props: this.payload.props,
                style: this.payload.style,
                transform: {
                    x: this.payload.transform.x,
                    y: this.payload.transform.y,
                    width: this.payload.transform.width,
                    height: this.payload.transform.height,
                    rotation: this.payload.transform.rotation ?? 0,
                    zIndex: this.payload.transform.zIndex ?? 0,
                },
                content: this.payload.content
                    ? {
                        type: this.payload.content.type as UniversalNode['content']['type'],
                        data: this.payload.content.data,
                    }
                    : undefined,
                parentId: this.payload.parentId ?? null,
                children: [],
                bindings: {},
                events: {},
                locked: false,
                visible: true,
                tags: [],
                version: '1.0.0',
                meta: {
                    author: this.meta.userId,
                    createdAt: now,
                    lastEditedBy: this.meta.userId,
                    lastEditedAt: now,
                },
            };

            // Insert the node
            store.insertNode(newNode);

            return {
                success: true,
                data: newNode,
            };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to insert node',
                errorCode: 'INSERT_FAILED',
            };
        }
    }

    async undo(): Promise<CommandResult<UniversalNode>> {
        const store = getStore();

        try {
            const deleted = store.deleteNode(this.payload.nodeId);

            if (!deleted) {
                return {
                    success: false,
                    error: `Node ${this.payload.nodeId} not found for deletion`,
                    errorCode: 'NODE_NOT_FOUND',
                };
            }

            return {
                success: true,
                data: deleted,
            };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to undo insert',
                errorCode: 'UNDO_INSERT_FAILED',
            };
        }
    }

    protected getTargetNodeId(): UniqueId {
        return this.payload.nodeId;
    }

    validate(): boolean {
        return (
            !!this.payload.nodeId &&
            !!this.payload.kind &&
            !!this.payload.transform &&
            this.payload.transform.width > 0 &&
            this.payload.transform.height > 0
        );
    }
}

// ============================================================================
// Delete Node Command
// ============================================================================

/**
 * Command to delete a node
 */
export class DeleteNodeCommand extends BaseNodeCommand<DeleteNodePayload, UniversalNode[]> {
    readonly type = 'DeleteNode' as const;
    private deletedNodes: UniversalNode[] = [];

    async execute(): Promise<CommandResult<UniversalNode[]>> {
        const store = getStore();

        try {
            const node = store.getNode(this.payload.nodeId);

            if (!node) {
                return {
                    success: false,
                    error: `Node ${this.payload.nodeId} not found`,
                    errorCode: 'NODE_NOT_FOUND',
                };
            }

            this.deletedNodes = [];

            if (this.payload.cascade) {
                // Get all descendants first
                const descendantIds = store.getDescendantIds(this.payload.nodeId);

                // Delete in reverse order (children first)
                for (const id of descendantIds.reverse()) {
                    const deleted = store.deleteNode(id);
                    if (deleted) {
                        this.deletedNodes.push(deleted);
                    }
                }
            } else if (this.payload.reparentChildrenTo !== undefined) {
                // Reparent children before deleting
                for (const childId of node.children) {
                    store.reparentNode(childId, this.payload.reparentChildrenTo);
                }
            }

            // Delete the main node
            const deleted = store.deleteNode(this.payload.nodeId);
            if (deleted) {
                this.deletedNodes.push(deleted);
            }

            this.previousState = this.deletedNodes;

            return {
                success: true,
                data: this.deletedNodes,
            };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to delete node',
                errorCode: 'DELETE_FAILED',
            };
        }
    }

    async undo(): Promise<CommandResult<UniversalNode[]>> {
        const store = getStore();

        try {
            // Re-insert nodes in reverse order (parents first)
            const restoredNodes: UniversalNode[] = [];

            for (const node of this.deletedNodes.reverse()) {
                store.insertNode(node);
                restoredNodes.push(node);
            }

            return {
                success: true,
                data: restoredNodes,
            };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to undo delete',
                errorCode: 'UNDO_DELETE_FAILED',
            };
        }
    }

    protected getTargetNodeId(): UniqueId {
        return this.payload.nodeId;
    }

    validate(): boolean {
        return !!this.payload.nodeId;
    }
}

// ============================================================================
// Update Props Command
// ============================================================================

/**
 * Command to update node props
 */
export class UpdatePropsCommand extends BaseNodeCommand<UpdatePropsPayload> {
    readonly type = 'UpdateProps' as const;
    private previousProps: Record<string, unknown> = {};

    async execute(): Promise<CommandResult> {
        const store = getStore();

        try {
            const node = store.getNode(this.payload.nodeId);

            if (!node) {
                return {
                    success: false,
                    error: `Node ${this.payload.nodeId} not found`,
                    errorCode: 'NODE_NOT_FOUND',
                };
            }

            // Store previous props for undo
            this.previousProps = { ...node.props };

            // Build new props
            const newProps = { ...node.props, ...this.payload.props };

            // Remove specified keys
            if (this.payload.remove) {
                for (const key of this.payload.remove) {
                    delete newProps[key];
                }
            }

            store.updateNodeProps(this.payload.nodeId, newProps);
            this.previousState = this.previousProps;

            return { success: true };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to update props',
                errorCode: 'UPDATE_PROPS_FAILED',
            };
        }
    }

    async undo(): Promise<CommandResult> {
        const store = getStore();

        try {
            store.updateNodeProps(this.payload.nodeId, this.previousProps);
            return { success: true };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to undo props update',
                errorCode: 'UNDO_UPDATE_PROPS_FAILED',
            };
        }
    }

    protected getTargetNodeId(): UniqueId {
        return this.payload.nodeId;
    }

    merge(other: Command): Command<UpdatePropsPayload> {
        if (other instanceof UpdatePropsCommand && other.payload.nodeId === this.payload.nodeId) {
            // Merge props from both commands
            return new UpdatePropsCommand(
                {
                    nodeId: this.payload.nodeId,
                    props: { ...other.payload.props, ...this.payload.props },
                    remove: [...(other.payload.remove || []), ...(this.payload.remove || [])],
                },
                this.meta
            );
        }
        return this;
    }
}

// ============================================================================
// Update Style Command
// ============================================================================

/**
 * Command to update node style
 */
export class UpdateStyleCommand extends BaseNodeCommand<UpdateStylePayload> {
    readonly type = 'UpdateStyle' as const;
    private previousStyle: Record<string, unknown> = {};

    async execute(): Promise<CommandResult> {
        const store = getStore();

        try {
            const node = store.getNode(this.payload.nodeId);

            if (!node) {
                return {
                    success: false,
                    error: `Node ${this.payload.nodeId} not found`,
                    errorCode: 'NODE_NOT_FOUND',
                };
            }

            // Store previous style for undo
            this.previousStyle = { ...node.style };

            // Build new style
            const newStyle = { ...node.style, ...this.payload.style };

            // Remove specified keys
            if (this.payload.remove) {
                for (const key of this.payload.remove) {
                    delete newStyle[key];
                }
            }

            store.updateNodeStyle(this.payload.nodeId, newStyle);
            this.previousState = this.previousStyle;

            return { success: true };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to update style',
                errorCode: 'UPDATE_STYLE_FAILED',
            };
        }
    }

    async undo(): Promise<CommandResult> {
        const store = getStore();

        try {
            store.updateNodeStyle(this.payload.nodeId, this.previousStyle);
            return { success: true };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to undo style update',
                errorCode: 'UNDO_UPDATE_STYLE_FAILED',
            };
        }
    }

    protected getTargetNodeId(): UniqueId {
        return this.payload.nodeId;
    }

    merge(other: Command): Command<UpdateStylePayload> {
        if (other instanceof UpdateStyleCommand && other.payload.nodeId === this.payload.nodeId) {
            return new UpdateStyleCommand(
                {
                    nodeId: this.payload.nodeId,
                    style: { ...other.payload.style, ...this.payload.style },
                    remove: [...(other.payload.remove || []), ...(this.payload.remove || [])],
                },
                this.meta
            );
        }
        return this;
    }
}

// ============================================================================
// Update Transform Command
// ============================================================================

/**
 * Command to update node transform
 */
export class UpdateTransformCommand extends BaseNodeCommand<UpdateTransformPayload> {
    readonly type = 'UpdateTransform' as const;
    private previousTransform: UniversalNode['transform'] | null = null;

    async execute(): Promise<CommandResult> {
        const store = getStore();

        try {
            const node = store.getNode(this.payload.nodeId);

            if (!node) {
                return {
                    success: false,
                    error: `Node ${this.payload.nodeId} not found`,
                    errorCode: 'NODE_NOT_FOUND',
                };
            }

            // Store previous transform for undo
            this.previousTransform = { ...node.transform };

            store.updateNodeTransform(this.payload.nodeId, this.payload.transform);
            this.previousState = this.previousTransform;

            return { success: true };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to update transform',
                errorCode: 'UPDATE_TRANSFORM_FAILED',
            };
        }
    }

    async undo(): Promise<CommandResult> {
        const store = getStore();

        try {
            if (this.previousTransform) {
                store.updateNodeTransform(this.payload.nodeId, this.previousTransform);
            }
            return { success: true };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to undo transform update',
                errorCode: 'UNDO_UPDATE_TRANSFORM_FAILED',
            };
        }
    }

    protected getTargetNodeId(): UniqueId {
        return this.payload.nodeId;
    }

    merge(other: Command): Command<UpdateTransformPayload> {
        if (
            other instanceof UpdateTransformCommand &&
            other.payload.nodeId === this.payload.nodeId
        ) {
            return new UpdateTransformCommand(
                {
                    nodeId: this.payload.nodeId,
                    transform: { ...other.payload.transform, ...this.payload.transform },
                },
                this.meta
            );
        }
        return this;
    }
}

// ============================================================================
// Update Content Command
// ============================================================================

/**
 * Command to update node content
 */
export class UpdateContentCommand extends BaseNodeCommand<UpdateContentPayload> {
    readonly type = 'UpdateContent' as const;
    private previousContent: UniversalNode['content'] | undefined;

    async execute(): Promise<CommandResult> {
        const store = getStore();

        try {
            const node = store.getNode(this.payload.nodeId);

            if (!node) {
                return {
                    success: false,
                    error: `Node ${this.payload.nodeId} not found`,
                    errorCode: 'NODE_NOT_FOUND',
                };
            }

            // Store previous content for undo
            this.previousContent = node.content ? { ...node.content } : undefined;

            const newContent: UniversalNode['content'] = {
                type: (this.payload.content.type ||
                    node.content?.type ||
                    'text') as UniversalNode['content']['type'],
                data: this.payload.content.data,
                language: this.payload.content.language,
            };

            store.updateNodeContent(this.payload.nodeId, newContent);
            this.previousState = this.previousContent;

            return { success: true };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to update content',
                errorCode: 'UPDATE_CONTENT_FAILED',
            };
        }
    }

    async undo(): Promise<CommandResult> {
        const store = getStore();

        try {
            store.updateNodeContent(this.payload.nodeId, this.previousContent);
            return { success: true };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to undo content update',
                errorCode: 'UNDO_UPDATE_CONTENT_FAILED',
            };
        }
    }

    protected getTargetNodeId(): UniqueId {
        return this.payload.nodeId;
    }
}

// ============================================================================
// Reparent Node Command
// ============================================================================

/**
 * Command to reparent a node
 */
export class ReparentNodeCommand extends BaseNodeCommand<ReparentNodePayload> {
    readonly type = 'ReparentNode' as const;
    private previousParentId: UniqueId | null = null;
    private previousIndex = 0;
    private previousTransform: UniversalNode['transform'] | null = null;

    async execute(): Promise<CommandResult> {
        const store = getStore();

        try {
            const node = store.getNode(this.payload.nodeId);

            if (!node) {
                return {
                    success: false,
                    error: `Node ${this.payload.nodeId} not found`,
                    errorCode: 'NODE_NOT_FOUND',
                };
            }

            // Validate new parent exists
            if (this.payload.newParentId) {
                const newParent = store.getNode(this.payload.newParentId);
                if (!newParent) {
                    return {
                        success: false,
                        error: `New parent ${this.payload.newParentId} not found`,
                        errorCode: 'NEW_PARENT_NOT_FOUND',
                    };
                }

                // Check for circular reference
                const descendantIds = store.getDescendantIds(this.payload.nodeId);
                if (descendantIds.includes(this.payload.newParentId)) {
                    return {
                        success: false,
                        error: 'Cannot reparent to a descendant',
                        errorCode: 'CIRCULAR_REFERENCE',
                    };
                }
            }

            // Store previous state
            this.previousParentId = node.parentId;
            this.previousTransform = { ...node.transform };

            // Find previous index
            if (node.parentId) {
                const oldParent = store.getNode(node.parentId);
                if (oldParent) {
                    this.previousIndex = oldParent.children.indexOf(this.payload.nodeId);
                }
            }

            // Reparent
            store.reparentNode(
                this.payload.nodeId,
                this.payload.newParentId,
                this.payload.index
            );

            this.previousState = {
                parentId: this.previousParentId,
                index: this.previousIndex,
                transform: this.previousTransform,
            };

            return { success: true };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to reparent node',
                errorCode: 'REPARENT_FAILED',
            };
        }
    }

    async undo(): Promise<CommandResult> {
        const store = getStore();

        try {
            store.reparentNode(
                this.payload.nodeId,
                this.previousParentId,
                this.previousIndex
            );

            // Restore world position if we preserved it
            if (this.payload.preserveWorldPosition && this.previousTransform) {
                store.updateNodeTransform(this.payload.nodeId, this.previousTransform);
            }

            return { success: true };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to undo reparent',
                errorCode: 'UNDO_REPARENT_FAILED',
            };
        }
    }

    protected getTargetNodeId(): UniqueId {
        return this.payload.nodeId;
    }
}

// ============================================================================
// Reorder Children Command
// ============================================================================

/**
 * Command to reorder children of a node
 */
export class ReorderChildrenCommand extends BaseNodeCommand<ReorderChildrenPayload> {
    readonly type = 'ReorderChildren' as const;
    private previousChildren: readonly UniqueId[] = [];

    async execute(): Promise<CommandResult> {
        const store = getStore();

        try {
            const parent = store.getNode(this.payload.parentId);

            if (!parent) {
                return {
                    success: false,
                    error: `Parent node ${this.payload.parentId} not found`,
                    errorCode: 'PARENT_NOT_FOUND',
                };
            }

            // Validate that the new order contains all children
            const currentChildSet = new Set(parent.children);
            const newChildSet = new Set(this.payload.children);

            if (
                currentChildSet.size !== newChildSet.size ||
                !parent.children.every((id) => newChildSet.has(id))
            ) {
                return {
                    success: false,
                    error: 'New order must contain exactly the same children',
                    errorCode: 'INVALID_CHILD_ORDER',
                };
            }

            // Store previous order
            this.previousChildren = [...parent.children];

            // Apply new order
            store.reorderChildren(this.payload.parentId, this.payload.children);
            this.previousState = this.previousChildren;

            return { success: true };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to reorder children',
                errorCode: 'REORDER_FAILED',
            };
        }
    }

    async undo(): Promise<CommandResult> {
        const store = getStore();

        try {
            store.reorderChildren(this.payload.parentId, this.previousChildren);
            return { success: true };
        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Failed to undo reorder',
                errorCode: 'UNDO_REORDER_FAILED',
            };
        }
    }

    protected getTargetNodeId(): UniqueId {
        return this.payload.parentId;
    }
}

// ============================================================================
// Command Factory Functions
// ============================================================================

/**
 * Create an insert node command
 */
export function createInsertNodeCommand(
    payload: InsertNodePayload,
    meta: CommandMeta
): InsertNodeCommand {
    return new InsertNodeCommand(payload, meta);
}

/**
 * Create a delete node command
 */
export function createDeleteNodeCommand(
    payload: DeleteNodePayload,
    meta: CommandMeta
): DeleteNodeCommand {
    return new DeleteNodeCommand(payload, meta);
}

/**
 * Create an update props command
 */
export function createUpdatePropsCommand(
    payload: UpdatePropsPayload,
    meta: CommandMeta
): UpdatePropsCommand {
    return new UpdatePropsCommand(payload, meta);
}

/**
 * Create an update style command
 */
export function createUpdateStyleCommand(
    payload: UpdateStylePayload,
    meta: CommandMeta
): UpdateStyleCommand {
    return new UpdateStyleCommand(payload, meta);
}

/**
 * Create an update transform command
 */
export function createUpdateTransformCommand(
    payload: UpdateTransformPayload,
    meta: CommandMeta
): UpdateTransformCommand {
    return new UpdateTransformCommand(payload, meta);
}

/**
 * Create an update content command
 */
export function createUpdateContentCommand(
    payload: UpdateContentPayload,
    meta: CommandMeta
): UpdateContentCommand {
    return new UpdateContentCommand(payload, meta);
}

/**
 * Create a reparent node command
 */
export function createReparentNodeCommand(
    payload: ReparentNodePayload,
    meta: CommandMeta
): ReparentNodeCommand {
    return new ReparentNodeCommand(payload, meta);
}

/**
 * Create a reorder children command
 */
export function createReorderChildrenCommand(
    payload: ReorderChildrenPayload,
    meta: CommandMeta
): ReorderChildrenCommand {
    return new ReorderChildrenCommand(payload, meta);
}

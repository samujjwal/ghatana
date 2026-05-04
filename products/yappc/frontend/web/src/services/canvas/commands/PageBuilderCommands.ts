/**
 * Page Builder Commands
 *
 * Semantic page-builder commands with undo/redo, audit, telemetry, validation, and autosave.
 *
 * @doc.type service
 * @doc.purpose Page builder command system
 * @doc.layer product
 */

import {
  deleteNode,
  deserializeDocument,
  insertNode,
  moveNode,
  serializeDocument,
  updateNodeProps,
  type BuilderDocument,
  type ComponentInstance,
  type NodeId,
  type SerializedDocument,
  type ValidationResult,
} from '@ghatana/ui-builder';

export type CommandType =
  | 'insert-component'
  | 'move-component'
  | 'update-props'
  | 'delete-component'
  | 'import-document'
  | 'autosave-document';

interface BaseCommand<TType extends CommandType, TData> {
  readonly id: string;
  readonly type: TType;
  readonly timestamp: string;
  readonly userId?: string;
  readonly nodeId?: string;
  readonly artifactId?: string;
  readonly data: TData;
}

export type InsertComponentCommand = BaseCommand<
  'insert-component',
  {
    readonly instance: Omit<ComponentInstance, 'id'>;
    readonly parentId?: NodeId;
    readonly slotName?: string;
    readonly index?: number;
  }
>;

export type MoveComponentCommand = BaseCommand<
  'move-component',
  {
    readonly nodeId: NodeId;
    readonly newParentId: NodeId | null;
    readonly newSlotName?: string;
    readonly index?: number;
  }
>;

export type UpdatePropsCommand = BaseCommand<
  'update-props',
  {
    readonly nodeId: NodeId;
    readonly props: Record<string, unknown>;
    readonly name?: string;
  }
>;

export type DeleteComponentCommand = BaseCommand<
  'delete-component',
  {
    readonly nodeId: NodeId;
  }
>;

export type ImportDocumentCommand = BaseCommand<
  'import-document',
  {
    readonly document: BuilderDocument;
  }
>;

export type AutosaveDocumentCommand = BaseCommand<
  'autosave-document',
  {
    readonly document: BuilderDocument;
  }
>;

export type Command =
  | InsertComponentCommand
  | MoveComponentCommand
  | UpdatePropsCommand
  | DeleteComponentCommand
  | ImportDocumentCommand
  | AutosaveDocumentCommand;

export interface CommandResult {
  readonly success: boolean;
  readonly error?: string;
  readonly document: BuilderDocument;
  readonly command: Command;
  readonly validationErrors?: readonly string[];
  readonly changedNodeIds: readonly NodeId[];
}

export interface CommandAuditRecord {
  readonly commandId: string;
  readonly commandType: CommandType;
  readonly artifactId?: string;
  readonly nodeId?: string;
  readonly changedNodeIds: readonly NodeId[];
  readonly beforeDocumentId: string;
  readonly afterDocumentId: string;
  readonly success: boolean;
}

export interface PageBuilderCommandsOptions {
  readonly initialDocument: BuilderDocument;
  readonly onAudit: (record: CommandAuditRecord, result: CommandResult) => void;
  readonly onTelemetry: (event: string, data: unknown) => void;
  readonly onAutosave?: (
    document: BuilderDocument,
    context: { readonly history: readonly Command[]; readonly artifactId?: string }
  ) => Promise<void>;
  readonly validate?: (document: BuilderDocument) => ValidationResult;
  readonly autosaveIntervalMs?: number;
}

interface CommandHistoryEntry {
  readonly command: Command;
  readonly before: SerializedDocument;
  readonly after: SerializedDocument;
  readonly changedNodeIds: readonly NodeId[];
}

interface ApplyCommandOutput {
  readonly document: BuilderDocument;
  readonly changedNodeIds: readonly NodeId[];
}

interface NodeLocation {
  readonly parentId: NodeId | null;
  readonly slotName?: string;
  readonly index: number;
}

export class PageBuilderCommands {
  private currentDocument: BuilderDocument;
  private readonly commandHistory: Command[] = [];
  private readonly undoStack: CommandHistoryEntry[] = [];
  private readonly redoStack: CommandHistoryEntry[] = [];
  private autosaveTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly autosaveInterval: number;

  constructor(private readonly options: PageBuilderCommandsOptions) {
    this.currentDocument = cloneDocument(options.initialDocument);
    this.autosaveInterval = options.autosaveIntervalMs ?? 30000;
  }

  getDocument(): BuilderDocument {
    return cloneDocument(this.currentDocument);
  }

  getHistory(): Command[] {
    return [...this.commandHistory];
  }

  canUndo(): boolean {
    return this.undoStack.length > 0;
  }

  canRedo(): boolean {
    return this.redoStack.length > 0;
  }

  clearHistory(): void {
    this.commandHistory.length = 0;
    this.undoStack.length = 0;
    this.redoStack.length = 0;
  }

  execute(command: Command): CommandResult {
    const beforeDocument = cloneDocument(this.currentDocument);
    const startTime = Date.now();

    try {
      const applied = this.applyCommand(beforeDocument, command);
      const validationErrors = this.options.validate?.(applied.document).errors.map((error) => error.message) ?? [];
      this.currentDocument = applied.document;

      const result: CommandResult = {
        success: true,
        document: cloneDocument(applied.document),
        command,
        changedNodeIds: applied.changedNodeIds,
        validationErrors,
      };

      this.commandHistory.push(command);
      this.undoStack.push({
        command,
        before: serializeDocument(beforeDocument),
        after: serializeDocument(applied.document),
        changedNodeIds: applied.changedNodeIds,
      });
      this.redoStack.length = 0;

      this.options.onAudit(
        {
          commandId: command.id,
          commandType: command.type,
          artifactId: command.artifactId,
          nodeId: command.nodeId,
          changedNodeIds: applied.changedNodeIds,
          beforeDocumentId: beforeDocument.id,
          afterDocumentId: applied.document.id,
          success: true,
        },
        result
      );

      this.options.onTelemetry('page_builder_command_executed', {
        commandType: command.type,
        artifactId: command.artifactId,
        nodeId: command.nodeId,
        changedNodeIds: applied.changedNodeIds,
        durationMs: Date.now() - startTime,
        validationErrorCount: validationErrors.length,
      });

      if (command.type !== 'autosave-document') {
        this.scheduleAutosave();
      }

      return result;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      this.options.onTelemetry('page_builder_command_failed', {
        commandType: command.type,
        artifactId: command.artifactId,
        nodeId: command.nodeId,
        error: message,
      });

      return {
        success: false,
        error: message,
        document: cloneDocument(beforeDocument),
        command,
        changedNodeIds: [],
      };
    }
  }

  undo(): CommandResult {
    const entry = this.undoStack.pop();
    if (!entry) {
      return {
        success: false,
        error: 'No command to undo',
        document: cloneDocument(this.currentDocument),
        command: createSyntheticCommand('import-document', this.currentDocument),
        changedNodeIds: [],
      };
    }

    this.currentDocument = deserializeDocument(entry.before);
    this.redoStack.push(entry);

    this.options.onTelemetry('page_builder_command_undone', {
      commandType: entry.command.type,
      changedNodeIds: entry.changedNodeIds,
    });

    return {
      success: true,
      document: cloneDocument(this.currentDocument),
      command: entry.command,
      changedNodeIds: entry.changedNodeIds,
      validationErrors: this.options.validate?.(this.currentDocument).errors.map((error) => error.message) ?? [],
    };
  }

  redo(): CommandResult {
    const entry = this.redoStack.pop();
    if (!entry) {
      return {
        success: false,
        error: 'No command to redo',
        document: cloneDocument(this.currentDocument),
        command: createSyntheticCommand('import-document', this.currentDocument),
        changedNodeIds: [],
      };
    }

    this.currentDocument = deserializeDocument(entry.after);
    this.undoStack.push(entry);

    this.options.onTelemetry('page_builder_command_redone', {
      commandType: entry.command.type,
      changedNodeIds: entry.changedNodeIds,
    });

    return {
      success: true,
      document: cloneDocument(this.currentDocument),
      command: entry.command,
      changedNodeIds: entry.changedNodeIds,
      validationErrors: this.options.validate?.(this.currentDocument).errors.map((error) => error.message) ?? [],
    };
  }

  destroy(): void {
    if (this.autosaveTimer) {
      clearTimeout(this.autosaveTimer);
      this.autosaveTimer = null;
    }
  }

  private applyCommand(document: BuilderDocument, command: Command): ApplyCommandOutput {
    switch (command.type) {
      case 'insert-component':
        return this.applyInsertComponent(document, command);
      case 'move-component':
        return this.applyMoveComponent(document, command);
      case 'update-props':
        return this.applyUpdateProps(document, command);
      case 'delete-component':
        return this.applyDeleteComponent(document, command);
      case 'import-document':
      case 'autosave-document':
        return {
          document: cloneDocument(command.data.document),
          changedNodeIds: [...command.data.document.nodes.keys()],
        };
      default:
        return assertNever(command);
    }
  }

  private applyInsertComponent(document: BuilderDocument, command: InsertComponentCommand): ApplyCommandOutput {
    let insertedNodeId: NodeId | null = null;
    let nextDocument = insertNode(
      document,
      command.data.instance,
      command.data.parentId,
      command.data.slotName,
      {
        onNodeInserted: ({ nodeId }) => {
          insertedNodeId = nodeId;
        },
      }
    );

    if (insertedNodeId && command.data.index != null) {
      nextDocument = reorderNode(
        nextDocument,
        insertedNodeId,
        command.data.parentId ?? null,
        command.data.slotName,
        command.data.index
      );
    }

    return {
      document: nextDocument,
      changedNodeIds: insertedNodeId ? [insertedNodeId] : [],
    };
  }

  private applyMoveComponent(document: BuilderDocument, command: MoveComponentCommand): ApplyCommandOutput {
    const nodeId = command.data.nodeId;
    let nextDocument = moveNode(
      document,
      nodeId,
      command.data.newParentId,
      command.data.newSlotName
    );

    if (command.data.index != null) {
      nextDocument = reorderNode(
        nextDocument,
        nodeId,
        command.data.newParentId,
        command.data.newSlotName,
        command.data.index
      );
    }

    return {
      document: nextDocument,
      changedNodeIds: [nodeId],
    };
  }

  private applyUpdateProps(document: BuilderDocument, command: UpdatePropsCommand): ApplyCommandOutput {
    const nodeId = command.data.nodeId;
    let nextDocument = updateNodeProps(document, nodeId, command.data.props);
    const node = nextDocument.nodes.get(nodeId);

    if (node && command.data.name !== undefined && command.data.name !== node.metadata.name) {
      const updatedNode: ComponentInstance = {
        ...node,
        metadata: {
          ...node.metadata,
          name: command.data.name,
        },
      };
      const nextNodes = new Map(nextDocument.nodes);
      nextNodes.set(nodeId, updatedNode);
      nextDocument = {
        ...nextDocument,
        nodes: nextNodes,
      };
    }

    return {
      document: nextDocument,
      changedNodeIds: [nodeId],
    };
  }

  private applyDeleteComponent(document: BuilderDocument, command: DeleteComponentCommand): ApplyCommandOutput {
    const changedNodeIds = collectNodeIds(document, command.data.nodeId);
    return {
      document: deleteNode(document, command.data.nodeId),
      changedNodeIds,
    };
  }

  private scheduleAutosave(): void {
    if (!this.options.onAutosave) {
      return;
    }

    if (this.autosaveTimer) {
      clearTimeout(this.autosaveTimer);
    }

    this.autosaveTimer = setTimeout(() => {
      void this.performAutosave();
    }, this.autosaveInterval);
  }

  private async performAutosave(): Promise<void> {
    if (!this.options.onAutosave) {
      return;
    }

    const documentSnapshot = cloneDocument(this.currentDocument);
    const autosaveCommand: AutosaveDocumentCommand = {
      id: `autosave-${Date.now()}`,
      type: 'autosave-document',
      timestamp: new Date().toISOString(),
      artifactId: undefined,
      data: {
        document: documentSnapshot,
      },
    };

    try {
      await this.options.onAutosave(documentSnapshot, {
        history: this.getHistory(),
        artifactId: autosaveCommand.artifactId,
      });
      this.commandHistory.push(autosaveCommand);
      this.options.onTelemetry('page_builder_autosave_completed', {
        documentId: documentSnapshot.id,
        nodeCount: documentSnapshot.nodes.size,
      });
    } catch (error) {
      this.options.onTelemetry('page_builder_autosave_failed', {
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }
}

function createSyntheticCommand(
  type: 'import-document',
  document: BuilderDocument
): ImportDocumentCommand {
  return {
    id: `synthetic-${Date.now()}`,
    type,
    timestamp: new Date().toISOString(),
    data: { document },
  };
}

function cloneDocument(document: BuilderDocument): BuilderDocument {
  return deserializeDocument(serializeDocument(document));
}

function collectNodeIds(document: BuilderDocument, nodeId: NodeId): readonly NodeId[] {
  const ids = new Set<NodeId>();
  const queue: NodeId[] = [nodeId];

  while (queue.length > 0) {
    const currentId = queue.shift();
    if (!currentId || ids.has(currentId)) {
      continue;
    }

    ids.add(currentId);
    const currentNode = document.nodes.get(currentId);
    if (!currentNode) {
      continue;
    }

    for (const childIds of Object.values(currentNode.slots)) {
      queue.push(...childIds);
    }
  }

  return [...ids];
}

function findNodeLocation(document: BuilderDocument, nodeId: NodeId): NodeLocation | null {
  const rootIndex = document.rootNodes.indexOf(nodeId);
  if (rootIndex >= 0) {
    return {
      parentId: null,
      index: rootIndex,
    };
  }

  for (const [parentId, instance] of document.nodes.entries()) {
    for (const [slotName, children] of Object.entries(instance.slots)) {
      const childIndex = children.indexOf(nodeId);
      if (childIndex >= 0) {
        return {
          parentId,
          slotName,
          index: childIndex,
        };
      }
    }
  }

  return null;
}

function reorderNode(
  document: BuilderDocument,
  nodeId: NodeId,
  parentId: NodeId | null,
  slotName: string | undefined,
  targetIndex: number
): BuilderDocument {
  const location = findNodeLocation(document, nodeId);
  if (!location) {
    return document;
  }

  const nextOrder = reorderIntoIndex(
    parentId === null ? document.rootNodes : document.nodes.get(parentId)?.slots[slotName ?? ''] ?? [],
    nodeId,
    targetIndex
  );

  if (parentId === null) {
    return {
      ...document,
      rootNodes: [...nextOrder],
    };
  }

  if (!slotName) {
    return document;
  }

  const parent = document.nodes.get(parentId);
  if (!parent) {
    return document;
  }

  const nextParent: ComponentInstance = {
    ...parent,
    slots: {
      ...parent.slots,
      [slotName]: [...nextOrder],
    },
  };

  const nextNodes = new Map(document.nodes);
  nextNodes.set(parentId, nextParent);

  return {
    ...document,
    nodes: nextNodes,
  };
}

function reorderIntoIndex(items: readonly NodeId[], nodeId: NodeId, targetIndex: number): readonly NodeId[] {
  const withoutNode = items.filter((id) => id !== nodeId);
  const clampedIndex = Math.max(0, Math.min(targetIndex, withoutNode.length));
  return [
    ...withoutNode.slice(0, clampedIndex),
    nodeId,
    ...withoutNode.slice(clampedIndex),
  ];
}

function assertNever(value: never): never {
  throw new Error(`Unhandled page builder command: ${JSON.stringify(value)}`);
}

export default PageBuilderCommands;

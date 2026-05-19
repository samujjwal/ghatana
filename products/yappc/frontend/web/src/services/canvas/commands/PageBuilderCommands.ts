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
  addBinding,
  attachBuilderDocumentCompatibility,
  deleteNode,
  deserializeDocument,
  insertNode,
  moveNode,
  normalizeBuilderDocument,
  removeBinding,
  serializeDocument,
  updateNodeProps,
  type BuilderDocument,
  type Binding,
  type ComponentInstance,
  type NodeId,
  type ResponsiveVariant,
  type ReviewStatusKind,
  type SerializedDocument,
  type StateVariant,
  type ValidationResult,
} from '@ghatana/ui-builder';

import { migrateRegistryContractInstance } from '@/components/canvas/page/registry';

type InstanceDataClassification = ComponentInstance['metadata']['dataClassification'];

export type CommandType =
  | 'insert-component'
  | 'move-component'
  | 'reorder-component'
  | 'add-action-binding'
  | 'add-data-binding'
  | 'set-responsive-variant'
  | 'set-state-variant'
  | 'apply-suggested-improvement'
  | 'map-component-to-design-system'
  | 'review-residual-island'
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
    readonly dataClassification?: InstanceDataClassification;
  }
>;

export type ReorderComponentCommand = BaseCommand<
  'reorder-component',
  {
    readonly nodeId: NodeId;
    readonly parentId: NodeId | null;
    readonly slotName?: string;
    readonly index: number;
  }
>;

export type AddActionBindingCommand = BaseCommand<
  'add-action-binding',
  {
    readonly nodeId: NodeId;
    readonly binding: Binding;
  }
>;

export type AddDataBindingCommand = BaseCommand<
  'add-data-binding',
  {
    readonly nodeId: NodeId;
    readonly binding: Binding;
  }
>;

export type SetResponsiveVariantCommand = BaseCommand<
  'set-responsive-variant',
  {
    readonly nodeId: NodeId;
    readonly variant: ResponsiveVariant;
  }
>;

export type SetStateVariantCommand = BaseCommand<
  'set-state-variant',
  {
    readonly nodeId: NodeId;
    readonly variant: StateVariant;
  }
>;

export type ApplySuggestedImprovementCommand = BaseCommand<
  'apply-suggested-improvement',
  {
    readonly suggestionId: string;
    readonly nodeId: NodeId;
    readonly props: Record<string, unknown>;
    readonly name?: string;
  }
>;

export type MapComponentToDesignSystemCommand = BaseCommand<
  'map-component-to-design-system',
  {
    readonly nodeId: NodeId;
    readonly contractName: string;
    readonly contractVersion?: string;
    readonly props?: Record<string, unknown>;
    readonly name?: string;
  }
>;

export type ReviewResidualIslandCommand = BaseCommand<
  'review-residual-island',
  {
    readonly nodeId: NodeId;
    readonly residualIslandId: string;
    readonly reviewStatus: Exclude<ReviewStatusKind, 'none'>;
    readonly notes?: string;
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
  | ReorderComponentCommand
  | AddActionBindingCommand
  | AddDataBindingCommand
  | SetResponsiveVariantCommand
  | SetStateVariantCommand
  | ApplySuggestedImprovementCommand
  | MapComponentToDesignSystemCommand
  | ReviewResidualIslandCommand
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
  readonly correlationId: string;
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
  private readonly userCommandHistory: Command[] = [];
  private readonly systemCommandHistory: Command[] = [];
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
    return [...this.userCommandHistory];
  }

  getSystemHistory(): Command[] {
    return [...this.systemCommandHistory];
  }

  canUndo(): boolean {
    return this.undoStack.length > 0;
  }

  canRedo(): boolean {
    return this.redoStack.length > 0;
  }

  clearHistory(): void {
    this.userCommandHistory.length = 0;
    this.systemCommandHistory.length = 0;
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

      if (command.type === 'autosave-document') {
        this.systemCommandHistory.push(command);
      } else {
        this.userCommandHistory.push(command);
      }
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
          correlationId: command.id,
          commandType: command.type,
          artifactId: command.artifactId,
          nodeId: command.nodeId,
          changedNodeIds: applied.changedNodeIds,
          beforeDocumentId: beforeDocument.documentId,
          afterDocumentId: applied.document.documentId,
          success: true,
        },
        result
      );

      this.options.onTelemetry('page_builder_command_executed', {
        correlationId: command.id,
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
        correlationId: command.id,
        commandType: command.type,
        artifactId: command.artifactId,
        nodeId: command.nodeId,
        error: message,
        durationMs: Date.now() - startTime,
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
      correlationId: entry.command.id,
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
      correlationId: entry.command.id,
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
      case 'reorder-component':
        return this.applyReorderComponent(document, command);
      case 'add-action-binding':
        return this.applyAddActionBinding(document, command);
      case 'add-data-binding':
        return this.applyAddDataBinding(document, command);
      case 'set-responsive-variant':
        return this.applySetResponsiveVariant(document, command);
      case 'set-state-variant':
        return this.applySetStateVariant(document, command);
      case 'apply-suggested-improvement':
        return this.applySuggestedImprovement(document, command);
      case 'map-component-to-design-system':
        return this.applyMapComponentToDesignSystem(document, command);
      case 'review-residual-island':
        return this.applyReviewResidualIsland(document, command);
      case 'update-props':
        return this.applyUpdateProps(document, command);
      case 'delete-component':
        return this.applyDeleteComponent(document, command);
      case 'import-document':
      case 'autosave-document':
        return {
          document: cloneDocument(command.data.document),
          changedNodeIds: Object.keys(command.data.document.nodes) as NodeId[],
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
    const node = readDocumentNode(nextDocument, nodeId);

    if (
      node &&
      (
        (command.data.name !== undefined && command.data.name !== node.metadata.name) ||
        (
          command.data.dataClassification !== undefined &&
          command.data.dataClassification !== node.metadata.dataClassification
        )
      )
    ) {
      const updatedNode: ComponentInstance = {
        ...node,
        metadata: {
          ...node.metadata,
          ...(command.data.name !== undefined ? { name: command.data.name } : {}),
          ...(command.data.dataClassification !== undefined
            ? { dataClassification: command.data.dataClassification }
            : {}),
        },
      };
      const nextNodes = replaceDocumentNode(nextDocument, nodeId, updatedNode);
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

  private applyReorderComponent(document: BuilderDocument, command: ReorderComponentCommand): ApplyCommandOutput {
    const nodeId = command.data.nodeId;
    return {
      document: reorderNode(
        document,
        nodeId,
        command.data.parentId,
        command.data.slotName,
        command.data.index
      ),
      changedNodeIds: [nodeId],
    };
  }

  private applyAddActionBinding(document: BuilderDocument, command: AddActionBindingCommand): ApplyCommandOutput {
    if (command.data.binding.type !== 'event') {
      throw new Error('Action bindings must use event binding type.');
    }

    return {
      document: replaceBinding(document, command.data.nodeId, command.data.binding),
      changedNodeIds: [command.data.nodeId],
    };
  }

  private applyAddDataBinding(document: BuilderDocument, command: AddDataBindingCommand): ApplyCommandOutput {
    if (command.data.binding.type !== 'data') {
      throw new Error('Data bindings must use data binding type.');
    }

    return {
      document: replaceBinding(document, command.data.nodeId, command.data.binding),
      changedNodeIds: [command.data.nodeId],
    };
  }

  private applySetResponsiveVariant(document: BuilderDocument, command: SetResponsiveVariantCommand): ApplyCommandOutput {
    return {
      document: setComponentResponsiveVariant(document, command.data.nodeId, command.data.variant),
      changedNodeIds: [command.data.nodeId],
    };
  }

  private applySetStateVariant(document: BuilderDocument, command: SetStateVariantCommand): ApplyCommandOutput {
    return {
      document: setStateVariant(document, command.data.nodeId, command.data.variant),
      changedNodeIds: [command.data.nodeId],
    };
  }

  private applySuggestedImprovement(document: BuilderDocument, command: ApplySuggestedImprovementCommand): ApplyCommandOutput {
    return this.applyUpdateProps(document, {
      ...command,
      type: 'update-props',
      data: {
        nodeId: command.data.nodeId,
        props: command.data.props,
        name: command.data.name,
      },
    });
  }

  private applyMapComponentToDesignSystem(document: BuilderDocument, command: MapComponentToDesignSystemCommand): ApplyCommandOutput {
    const node = document.nodes[command.data.nodeId];
    if (!node) {
      throw new Error(`Cannot map missing node '${command.data.nodeId}' to design-system contract.`);
    }

    const migrated = migrateRegistryContractInstance({
      contractName: command.data.contractName,
      version: command.data.contractVersion,
      props: {
        ...node.props,
        ...(command.data.props ?? {}),
      },
    });

    if (!migrated.compatibility.compatible) {
      throw new Error(migrated.compatibility.errors.join(' '));
    }

    const mappedNode: ComponentInstance = {
      ...node,
      contractName: migrated.contractName,
      props: migrated.props,
      metadata: {
        ...node.metadata,
        name: command.data.name ?? node.metadata.name,
      },
    };

    const nextNodes = {
      ...document.nodes,
      [command.data.nodeId]: mappedNode,
    };

    return {
      document: {
        ...document,
        nodes: nextNodes,
        metadata: {
          ...document.metadata,
          updatedAt: new Date().toISOString(),
        },
      },
      changedNodeIds: [command.data.nodeId],
    };
  }

  private applyReviewResidualIsland(document: BuilderDocument, command: ReviewResidualIslandCommand): ApplyCommandOutput {
    const node = document.nodes[command.data.nodeId];
    if (!node) {
      throw new Error(`Cannot review residual island for missing node '${command.data.nodeId}'.`);
    }

    const updatedNode: ComponentInstance = {
      ...node,
      metadata: {
        ...node.metadata,
        reviewStatus: {
          status: command.data.reviewStatus,
          reviewedBy: command.userId,
          reviewedAt: new Date().toISOString(),
          notes: command.data.notes
            ? `[Residual ${command.data.residualIslandId}] ${command.data.notes}`
            : `[Residual ${command.data.residualIslandId}] reviewed`,
        },
      },
    };

    const nextNodes = {
      ...document.nodes,
      [command.data.nodeId]: updatedNode,
    };

    return {
      document: {
        ...document,
        nodes: nextNodes,
        metadata: {
          ...document.metadata,
          updatedAt: new Date().toISOString(),
        },
      },
      changedNodeIds: [command.data.nodeId],
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
      this.systemCommandHistory.push(autosaveCommand);
      this.options.onTelemetry('page_builder_autosave_completed', {
        documentId: documentSnapshot.documentId,
        nodeCount: Object.keys(documentSnapshot.nodes).length,
      });
    } catch (error) {
      this.options.onTelemetry('page_builder_autosave_failed', {
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }
}

function readDocumentNode(document: BuilderDocument, nodeId: NodeId): ComponentInstance | undefined {
  const nodes = document.nodes as unknown as Map<NodeId, ComponentInstance> & Record<string, ComponentInstance>;
  return typeof nodes.get === 'function'
    ? nodes.get(nodeId)
    : nodes[nodeId];
}

function replaceDocumentNode(
  document: BuilderDocument,
  nodeId: NodeId,
  node: ComponentInstance,
): BuilderDocument['nodes'] {
  const nodes = document.nodes as unknown as Map<NodeId, ComponentInstance> & Record<string, ComponentInstance>;
  if (nodes instanceof Map) {
    const nextNodes = new Map(nodes) as Map<NodeId, ComponentInstance> & Record<string, ComponentInstance>;
    nextNodes.set(nodeId, node);
    Object.defineProperty(nextNodes, nodeId, {
      value: node,
      enumerable: true,
      configurable: true,
      writable: true,
    });
    return nextNodes as unknown as BuilderDocument['nodes'];
  }

  const nodeRecord = nodes as Record<string, ComponentInstance>;
  return {
    ...nodeRecord,
    [nodeId]: node,
  } as BuilderDocument['nodes'];
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
  return attachBuilderDocumentCompatibility(normalizeBuilderDocument(deserializeDocument(serializeDocument(document))));
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
    const currentNode = document.nodes[currentId];
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
  const rootNodeIds = document.layout.nodes[document.layout.rootId]?.children ?? [];
  const rootIndex = rootNodeIds.indexOf(nodeId);
  if (rootIndex >= 0) {
    return {
      parentId: null,
      index: rootIndex,
    };
  }

  for (const [parentId, instance] of Object.entries(document.nodes) as [NodeId, ComponentInstance][]) {
    for (const [slotName, children] of Object.entries(instance.slots) as [string, NodeId[]][]) {
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
    parentId === null
      ? document.layout.nodes[document.layout.rootId]?.children ?? []
      : document.nodes[parentId]?.slots[slotName ?? ''] ?? [],
    nodeId,
    targetIndex
  );

  if (parentId === null) {
    return {
      ...document,
      layout: {
        ...document.layout,
        nodes: {
          ...document.layout.nodes,
          [document.layout.rootId]: {
            ...document.layout.nodes[document.layout.rootId],
            children: [...nextOrder],
          },
        },
      },
    };
  }

  if (!slotName) {
    return document;
  }

  const parent = document.nodes[parentId];
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

  const nextNodes = {
    ...document.nodes,
    [parentId]: nextParent,
  };

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

function replaceBinding(
  document: BuilderDocument,
  nodeId: NodeId,
  binding: Binding
): BuilderDocument {
  const node = document.nodes[nodeId];
  const existingBinding = node?.bindings.find(
    (candidate: Binding) => candidate.type === binding.type && candidate.target === binding.target,
  );
  const documentWithoutExisting = existingBinding
    ? removeBinding(document, nodeId, existingBinding.id)
    : document;

  return addBinding(documentWithoutExisting, nodeId, binding);
}

function setStateVariant(
  document: BuilderDocument,
  nodeId: NodeId,
  variant: StateVariant
): BuilderDocument {
  const node = document.nodes[nodeId];
  if (!node) {
    return document;
  }

  const existing = node.metadata.stateVariants ?? [];
  const filtered = existing.filter((candidate: StateVariant) => candidate.state !== variant.state);
  const updatedNode: ComponentInstance = {
    ...node,
    metadata: {
      ...node.metadata,
      stateVariants: [...filtered, variant],
    },
  };

  const updatedNodes = {
    ...document.nodes,
    [nodeId]: updatedNode,
  };

  return {
    ...document,
    nodes: updatedNodes,
    metadata: {
      ...document.metadata,
      updatedAt: new Date().toISOString(),
    },
  };
}

function setComponentResponsiveVariant(
  document: BuilderDocument,
  nodeId: NodeId,
  variant: ResponsiveVariant
): BuilderDocument {
  const node = document.nodes[nodeId];
  if (!node) {
    return document;
  }

  const existing = node.metadata.responsiveVariants ?? [];
  const filtered = existing.filter((candidate: ResponsiveVariant) => candidate.breakpoint !== variant.breakpoint);
  const updatedNode: ComponentInstance = {
    ...node,
    metadata: {
      ...node.metadata,
      responsiveVariants: [...filtered, variant],
    },
  };

  const updatedNodes = {
    ...document.nodes,
    [nodeId]: updatedNode,
  };

  return {
    ...document,
    nodes: updatedNodes,
    metadata: {
      ...document.metadata,
      updatedAt: new Date().toISOString(),
    },
  };
}

function assertNever(value: never): never {
  throw new Error(`Unhandled page builder command: ${JSON.stringify(value)}`);
}

export default PageBuilderCommands;

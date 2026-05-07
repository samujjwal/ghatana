import { Plus as AddIcon, Trash2 as DeleteIcon, Pencil as EditIcon, AlertTriangle, Upload, X } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  IconButton,
  Button,
  Surface as Paper,
  TextArea,
} from '@ghatana/design-system';
import { Drawer } from '@ghatana/design-system';
import React, { useState, useCallback, useEffect, useMemo, useRef } from 'react';

import { ComponentRenderer } from './ComponentRenderer';
import { PropertyForm } from './PropertyForm';
import { getDefaultComponentData } from './schemas';
import {
  componentDataToBuilderDocument,
  componentDataToInsertableInstance,
  builderDocumentToComponentData,
  isBuilderDocument,
} from './builder-document-adapter';
import {
  getBuilderPalette,
  getContractMap,
  getDefaultSlotName,
  isContainerContract,
  toLegacyComponentType,
  normalizeContractName,
  type LegacyComponentType,
} from './registry';
import type { DropRequest, KeyboardMoveRequest } from './ComponentRenderer';
import { importPageArtifactsFromCode } from './artifactCompilerBridge';
import { AIActionLineageTracker, createAIChangeRecord } from './pageArtifactDocument';
import type { PageArtifactAIChangeRecord } from './pageArtifactDocument';
import { importSourceToPageArtifacts, type ImportSourceType } from '../../../services/compiler/ImportSourceWorkflow';
import { PageBuilderCommands, type Command, type InsertComponentCommand, type MoveComponentCommand } from '../../../services/canvas/commands/PageBuilderCommands';
import {
  emitPageBuilderCommandAudit,
  type PageBuilderCommandAuditContext,
} from '../../../services/canvas/commands/PageBuilderCommandAuditService';
import type { PhaseCanvasConfig } from '../../../services/canvas/phase-config/PhaseCanvasConfig';

import type { ComponentData } from './schemas';
import {
  validateDocument,
  deserializeDocument,
} from '@ghatana/ui-builder';
import type { BuilderDocument, NodeId, ValidationResult } from '@ghatana/ui-builder';

interface NodeLocation {
  readonly parentId: NodeId | null;
  readonly slotName?: string;
  readonly index: number;
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
      const index = children.indexOf(nodeId);
      if (index >= 0) {
        return {
          parentId,
          slotName,
          index,
        };
      }
    }
  }

  return null;
}

function getNodeChildren(document: BuilderDocument, parentId: NodeId | null, slotName?: string): readonly NodeId[] {
  if (!parentId) {
    return document.rootNodes;
  }

  const parent = document.nodes.get(parentId);
  if (!parent || !slotName) {
    return [];
  }

  return parent.slots[slotName] ?? [];
}

function isDescendant(document: BuilderDocument, ancestorId: NodeId, candidateId: NodeId): boolean {
  const visited = new Set<NodeId>();
  const stack: NodeId[] = [ancestorId];

  while (stack.length > 0) {
    const currentId = stack.pop();
    if (!currentId || visited.has(currentId)) {
      continue;
    }

    visited.add(currentId);
    const current = document.nodes.get(currentId);
    if (!current) {
      continue;
    }

    const childIds = Object.values(current.slots).flat();
    for (const childId of childIds) {
      if (childId === candidateId) {
        return true;
      }
      stack.push(childId);
    }
  }

  return false;
}

interface SourceImportCommand {
  readonly sourceType: ImportSourceType;
  readonly source: string;
}

type ImportWorkflowMode = 'semantic-model' | 'source';

function parseSourceImportCommand(input: string): SourceImportCommand | null {
  const trimmed = input.trim();
  if (!trimmed.startsWith('source:')) {
    return null;
  }

  const command = trimmed.slice('source:'.length);
  const separatorIndex = command.indexOf(':');
  if (separatorIndex <= 0) {
    return null;
  }

  const sourceType = command.slice(0, separatorIndex) as ImportSourceType;
  const source = command.slice(separatorIndex + 1).trim();
  if (!source) {
    return null;
  }

  const allowedSourceTypes: readonly ImportSourceType[] = ['tsx', 'route', 'storybook', 'artifact', 'zip'];
  if (!allowedSourceTypes.includes(sourceType)) {
    return null;
  }

  return {
    sourceType,
    source,
  };
}

/**
 * @doc.type component
 * @doc.purpose Registry-driven YAPPC page designer using shared BuilderDocument contract from @ghatana/ui-builder
 * @doc.layer product
 * @doc.pattern Widget
 */
interface PageDesignerProps {
  readonly initialComponents?: ComponentData[] | BuilderDocument;
  readonly projectId?: string;
  readonly onComponentsChange?: (components: ComponentData[]) => void;
  readonly onDocumentChange?: (document: BuilderDocument, validation: ValidationResult) => void;
  /** Called when the designer imports a decompiled model, producing one or more page artifacts */
  readonly onImportArtifacts?: (artifacts: readonly import('./pageArtifactDocument').PageArtifactDocument[]) => void;
  /** Called when an AI change is applied to the page (for audit/governance recording) */
  readonly onAIChangeRecord?: (record: PageArtifactAIChangeRecord) => void;
  /** Called when a pending AI/automation change is accepted or rejected. */
  readonly onAIReviewDecision?: (actionId: string, decision: 'accepted' | 'rejected') => void;
  /** Called when the user selects a node in the designer canvas */
  readonly onSelectionChange?: (nodeId: string | null) => void;
  /** Called when the user hovers a node in the designer canvas */
  readonly onHoverChange?: (nodeId: string | null) => void;
  /** Optional externally controlled selection (used by preview click sync) */
  readonly externalSelectedNodeId?: string | null;
  /** Optional externally controlled hover state (used by preview hover sync) */
  readonly externalHoveredNodeId?: string | null;
  /** Phase-aware canvas configuration controlling available tools and editing capabilities */
  readonly phaseConfig?: PhaseCanvasConfig;
  /** Scope used to persist immutable command audit records when available. */
  readonly auditContext?: PageBuilderCommandAuditContext;
}

export const PageDesigner: React.FC<PageDesignerProps> = ({
  initialComponents = [],
  projectId,
  onComponentsChange,
  onDocumentChange,
  onImportArtifacts,
  onAIChangeRecord,
  onAIReviewDecision,
  onSelectionChange,
  onHoverChange,
  externalSelectedNodeId,
  externalHoveredNodeId,
  phaseConfig,
  auditContext,
}) => {
  const canEdit = phaseConfig?.allowEditing ?? true;
  const canAdd = phaseConfig?.allowAddComponent ?? true;
  const canDelete = phaseConfig?.allowDelete ?? true;
  const contracts = useMemo(() => getContractMap(), []);
  const palette = useMemo(() => getBuilderPalette(), []);
  const [document, setDocument] = useState<BuilderDocument>(() => {
    if (isBuilderDocument(initialComponents)) {
      return initialComponents;
    }
    return componentDataToBuilderDocument(initialComponents as ComponentData[]);
  });
  const commandServiceRef = useRef<PageBuilderCommands | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [hoveredId, setHoveredId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [importPanelOpen, setImportPanelOpen] = useState(false);
  const [importInput, setImportInput] = useState('');
  const [importWorkflowMode, setImportWorkflowMode] = useState<ImportWorkflowMode>('semantic-model');
  const [guidedSourceType, setGuidedSourceType] = useState<ImportSourceType>('tsx');
  const [guidedSourceLocator, setGuidedSourceLocator] = useState('');
  const [importError, setImportError] = useState<string | null>(null);
  const [importResiduals, setImportResiduals] = useState<readonly string[]>([]);
  const [importFidelity, setImportFidelity] = useState<
    import('./pageArtifactDocument').PageArtifactDocument['roundTripFidelity'] | null
  >(null);
  const [commandAuditWarning, setCommandAuditWarning] = useState<string | null>(null);
  const [dropFeedback, setDropFeedback] = useState<string | null>(null);
  const importTextareaRef = useRef<HTMLTextAreaElement>(null);
  const auditContextRef = useRef<PageBuilderCommandAuditContext | undefined>(auditContext);

  // Governance trace tracker — scoped to this designer session
  const lineageTrackerRef = useRef(new AIActionLineageTracker());
  const [pendingAIActions, setPendingAIActions] = useState<readonly import('./pageArtifactDocument').AIActionLineage[]>([]);

  const validation = useMemo(() => validateDocument(document, contracts), [contracts, document]);
  const announceDropFeedback = useCallback((message: string): void => {
    setDropFeedback(message);
  }, []);

  useEffect(() => {
    auditContextRef.current = auditContext;
  }, [auditContext]);

  if (!commandServiceRef.current) {
    commandServiceRef.current = new PageBuilderCommands({
      initialDocument: document,
      onAudit: (record, result) => {
        const scopedAuditContext = auditContextRef.current;
        if (!scopedAuditContext) {
          return;
        }

        void emitPageBuilderCommandAudit(scopedAuditContext, record, result)
          .then(() => setCommandAuditWarning(null))
          .catch((error: unknown) => {
            const message = error instanceof Error ? error.message : 'Command audit failed.';
            setCommandAuditWarning(`Command audit could not be persisted: ${message}`);
          });
      },
      onTelemetry: () => undefined,
      validate: (candidate) => validateDocument(candidate, contracts),
    });
  }

  useEffect(() => () => {
    commandServiceRef.current?.destroy();
  }, []);

  useEffect(() => {
    onDocumentChange?.(document, validation);
  }, [document, onDocumentChange, validation]);

  useEffect(() => {
    onSelectionChange?.(selectedId);
  }, [selectedId, onSelectionChange]);

  useEffect(() => {
    if (externalSelectedNodeId !== undefined && externalSelectedNodeId !== selectedId) {
      setSelectedId(externalSelectedNodeId);
    }
  }, [externalSelectedNodeId, selectedId]);

  const handleNodeHover = useCallback((nodeId: string | null) => {
    setHoveredId(nodeId);
    onHoverChange?.(nodeId);
  }, [onHoverChange]);

  const selectedInstance = selectedId ? document.nodes.get(selectedId as NodeId) : undefined;
  const effectiveHoveredNodeId = externalHoveredNodeId ?? hoveredId;

  const publishDocument = useCallback(
    (nextDocument: BuilderDocument) => {
      setDocument(nextDocument);
      onComponentsChange?.(builderDocumentToComponentData(nextDocument));
    },
    [onComponentsChange],
  );

  const executeCommand = useCallback(
    (command: Command): BuilderDocument | null => {
      const result = commandServiceRef.current?.execute(command);
      if (!result?.success) {
        return null;
      }

      publishDocument(result.document);
      return result.document;
    },
    [publishDocument],
  );

  /**
   * Records an AI-originated node mutation into the session lineage tracker
   * and surfaces it in the governance panel.
   */
  const recordAIChange = useCallback(
    (record: PageArtifactAIChangeRecord) => {
      lineageTrackerRef.current.record(record.lineage);
      setPendingAIActions(lineageTrackerRef.current.getPending());
      onAIChangeRecord?.(record);
    },
    [onAIChangeRecord],
  );

  /**
   * Accepts or rejects a pending AI action in the governance panel.
   */
  const handleAIActionReview = useCallback(
    (actionId: string, decision: 'accepted' | 'rejected') => {
      lineageTrackerRef.current.setReviewState(actionId, decision);
      setPendingAIActions(lineageTrackerRef.current.getPending());
      onAIReviewDecision?.(actionId, decision);
    },
    [onAIReviewDecision],
  );

  const resolveInsertionTarget = useCallback((): {
    readonly parentId?: NodeId;
    readonly slotName?: string;
  } => {
    if (!selectedInstance || !isContainerContract(selectedInstance.contractName)) {
      return {};
    }

    const slotName = getDefaultSlotName(selectedInstance.contractName);
    if (!slotName) {
      return {};
    }

    return {
      parentId: selectedInstance.id,
      slotName,
    };
  }, [selectedInstance]);

  const handleImportConfirm = useCallback(async () => {
    setImportError(null);
    const trimmedInput = importInput.trim();
    const trimmedSourceLocator = guidedSourceLocator.trim();
    if (importWorkflowMode === 'semantic-model' && !trimmedInput) {
      setImportError('Paste a JSON semantic model to import.');
      return;
    }
    if (importWorkflowMode === 'source' && !trimmedSourceLocator) {
      setImportError('Enter a source URL, route, Storybook story, artifact ID, or zip locator to import.');
      return;
    }

    let artifacts: readonly import('./pageArtifactDocument').PageArtifactDocument[];
    const sourceImportCommand = importWorkflowMode === 'source'
      ? { sourceType: guidedSourceType, source: trimmedSourceLocator }
      : parseSourceImportCommand(trimmedInput);
    if (sourceImportCommand) {
      if (!projectId) {
        setImportError('Source imports require an active project context.');
        return;
      }
      if (!auditContext?.tenantId || !auditContext.workspaceId) {
        setImportError('Source imports require authenticated tenant and workspace context.');
        return;
      }

      const importFromSourceResult = await importSourceToPageArtifacts(
        {
          sourceType: sourceImportCommand.sourceType,
          source: sourceImportCommand.source,
          projectId,
          options: {
            requireServerImport: true,
            tenantId: auditContext.tenantId,
            workspaceId: auditContext.workspaceId,
            maxSourceLength: 4096,
          },
        },
        'import',
      );

      if (!importFromSourceResult.importResult.success) {
        setImportError(importFromSourceResult.importResult.errors[0] ?? 'Source import failed.');
        return;
      }

      artifacts = importFromSourceResult.pageArtifacts;
    } else {
      try {
        artifacts = importPageArtifactsFromCode(trimmedInput, 'import');
      } catch (err) {
        setImportError(err instanceof Error ? err.message : 'Invalid JSON — could not parse semantic model.');
        return;
      }
    }

    const first = artifacts[0];
    if (!first) {
      setImportError('No pages found in the imported model.');
      return;
    }

    // Load the first page into the editor
    const imported = first.serializedBuilderDocument;
    const importedBuilderDocument = deserializeDocument(imported);
    const importedDocument = executeCommand({
      id: `import-document-${Date.now()}`,
      type: 'import-document',
      timestamp: new Date().toISOString(),
      data: {
        document: importedBuilderDocument,
      },
    });
    if (!importedDocument) {
      setImportError('Could not load the imported page into the builder.');
      return;
    }
    setSelectedId(importedDocument.rootNodes[0] ?? null);

    // Surface residual islands if any
    const residuals = first.residualIslandIds ?? [];
    setImportResiduals(residuals);
    setImportFidelity(first.roundTripFidelity ?? null);

    const importedNodes = importedDocument.nodes;
    const affectedNodeIds: readonly string[] =
      importedNodes instanceof Map
        ? [...importedNodes.keys()].map(String)
        : Object.keys(importedNodes as unknown as Record<string, unknown>);
    recordAIChange(
      createAIChangeRecord(
        first.artifactId,
        first.documentId,
        'property-completion',
        'Imported and decompiled semantic page model into builder document.',
        0.85,
        affectedNodeIds,
        {
          reviewState: residuals.length > 0 ? 'pending' : 'auto-accepted',
          evidence: [
            `source:${first.source}`,
            `residuals:${residuals.length}`,
          ],
        },
      ),
    );

    // Notify parent about all imported artifacts (multi-page)
    if (onImportArtifacts) {
      onImportArtifacts(artifacts);
    }

    setImportPanelOpen(false);
    setImportInput('');
    setGuidedSourceLocator('');
  }, [auditContext?.tenantId, auditContext?.workspaceId, executeCommand, guidedSourceLocator, guidedSourceType, importInput, importWorkflowMode, onImportArtifacts, projectId, recordAIChange]);

  const handleAddComponent = useCallback(
    (contractOrType: string) => {
      const contractName = normalizeContractName(contractOrType);
      const legacyType = toLegacyComponentType(contractName) as LegacyComponentType;
      const newComponent = getDefaultComponentData(legacyType) as ComponentData;
      const target = resolveInsertionTarget();
      const result = commandServiceRef.current?.execute({
        id: `insert-component-${Date.now()}`,
        type: 'insert-component',
        timestamp: new Date().toISOString(),
        data: {
          instance: componentDataToInsertableInstance(newComponent),
          parentId: target.parentId,
          slotName: target.slotName,
        },
      } satisfies InsertComponentCommand);

      if (!result?.success) {
        return;
      }

      publishDocument(result.document);
      setSelectedId((result.changedNodeIds[0] as string | undefined) ?? null);
    },
    [publishDocument, resolveInsertionTarget],
  );

  const handlePaletteDragStart = useCallback(
    (event: React.DragEvent<HTMLButtonElement>, contractName: string) => {
      event.dataTransfer.setData('application/x-page-component', contractName);
      event.dataTransfer.effectAllowed = 'copy';
    },
    [],
  );

  const handleDesignAreaDrop = useCallback(
    (event: React.DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      const contractName = event.dataTransfer.getData('application/x-page-component');
      if (contractName) {
        const normalizedName = normalizeContractName(contractName);
        const legacyType = toLegacyComponentType(normalizedName) as LegacyComponentType;
        const newComponent = getDefaultComponentData(legacyType) as ComponentData;
        const result = commandServiceRef.current?.execute({
          id: `insert-component-${Date.now()}`,
          type: 'insert-component',
          timestamp: new Date().toISOString(),
          data: {
            instance: componentDataToInsertableInstance(newComponent),
          },
        } satisfies InsertComponentCommand);

        if (!result?.success) {
          return;
        }

        publishDocument(result.document);
        setSelectedId((result.changedNodeIds[0] as string | undefined) ?? null);
        return;
      }

      const draggedNodeId = event.dataTransfer.getData('application/x-page-node');
      if (draggedNodeId && document.nodes.has(draggedNodeId as NodeId)) {
        const result = commandServiceRef.current?.execute({
          id: `move-component-${Date.now()}`,
          type: 'move-component',
          timestamp: new Date().toISOString(),
          data: {
            nodeId: draggedNodeId as NodeId,
            newParentId: null,
            index: document.rootNodes.length,
          },
        } satisfies MoveComponentCommand);

        if (!result?.success) {
          announceDropFeedback('Could not move component to the root canvas.');
          return;
        }

        publishDocument(result.document);
        setDropFeedback(null);
        setSelectedId(draggedNodeId);
      }
    },
    [announceDropFeedback, document, publishDocument],
  );

  const handleRendererDropRequest = useCallback(
    (request: DropRequest) => {
      if (request.source.kind === 'node' && request.source.nodeId === request.targetNodeId) {
        announceDropFeedback('Cannot drop a component onto itself.');
        return;
      }

      if (request.placement === 'slot' && !request.slotName) {
        announceDropFeedback('Cannot drop into a slot without a target slot.');
        return;
      }

      const targetLocation = findNodeLocation(document, request.targetNodeId);
      if (!targetLocation && request.placement !== 'slot') {
        announceDropFeedback('Cannot find the target position for this drop.');
        return;
      }

      const destinationParentId: NodeId | null =
        request.placement === 'slot' ? request.targetNodeId : targetLocation?.parentId ?? null;
      const destinationSlotName: string | undefined =
        request.placement === 'slot' ? request.slotName : targetLocation?.slotName;

      if (request.source.kind === 'palette') {
        const normalizedName = normalizeContractName(request.source.contractName);
        const legacyType = toLegacyComponentType(normalizedName) as LegacyComponentType;
        const newComponent = getDefaultComponentData(legacyType) as ComponentData;
        const destinationChildren = getNodeChildren(document, destinationParentId, destinationSlotName);
        const insertionIndex =
          request.placement === 'slot'
            ? destinationChildren.length
            : (targetLocation?.index ?? destinationChildren.length) +
              (request.placement === 'after' ? 1 : 0);
        const result = commandServiceRef.current?.execute({
          id: `insert-component-${Date.now()}`,
          type: 'insert-component',
          timestamp: new Date().toISOString(),
          data: {
            instance: componentDataToInsertableInstance(newComponent),
            parentId: destinationParentId ?? undefined,
            slotName: destinationSlotName,
            index: insertionIndex,
          },
        } satisfies InsertComponentCommand);

        if (!result?.success) {
          announceDropFeedback('Could not insert component at the requested position.');
          return;
        }

        publishDocument(result.document);
        setDropFeedback(null);
        setSelectedId((result.changedNodeIds[0] as string | undefined) ?? null);
        return;
      }

      const sourceNodeId = request.source.nodeId;
      if (!document.nodes.has(sourceNodeId)) {
        announceDropFeedback('Cannot move a component that is no longer in the document.');
        return;
      }

      if (destinationParentId && isDescendant(document, sourceNodeId, destinationParentId)) {
        announceDropFeedback('Cannot move a component into one of its own descendants.');
        return;
      }

      const destinationChildren = getNodeChildren(document, destinationParentId, destinationSlotName);
      let insertionIndex =
        request.placement === 'slot'
          ? destinationChildren.length
          : (targetLocation?.index ?? destinationChildren.length) +
            (request.placement === 'after' ? 1 : 0);
      const sourceLocation = findNodeLocation(document, sourceNodeId);
      if (
        sourceLocation &&
        sourceLocation.parentId === destinationParentId &&
        sourceLocation.slotName === destinationSlotName &&
        sourceLocation.index < insertionIndex
      ) {
        insertionIndex -= 1;
      }
      const result = commandServiceRef.current?.execute({
        id: `move-component-${Date.now()}`,
        type: 'move-component',
        timestamp: new Date().toISOString(),
        data: {
          nodeId: sourceNodeId,
          newParentId: destinationParentId,
          newSlotName: destinationSlotName,
          index: insertionIndex,
        },
      } satisfies MoveComponentCommand);

      if (!result?.success) {
        announceDropFeedback('Could not move component to the requested position.');
        return;
      }

      publishDocument(result.document);
      setDropFeedback(null);
      setSelectedId(sourceNodeId);
    },
    [announceDropFeedback, document, publishDocument],
  );

  const handleKeyboardMoveRequest = useCallback(
    (request: KeyboardMoveRequest) => {
      const sourceLocation = findNodeLocation(document, request.nodeId);
      if (!sourceLocation) {
        announceDropFeedback('Cannot move a component that is no longer in the document.');
        return;
      }

      const siblings = getNodeChildren(document, sourceLocation.parentId, sourceLocation.slotName);
      let newParentId = sourceLocation.parentId;
      let newSlotName = sourceLocation.slotName;
      let index = sourceLocation.index;

      if (request.direction === 'previous') {
        if (sourceLocation.index === 0) {
          announceDropFeedback('Component is already first in this group.');
          return;
        }
        index = sourceLocation.index - 1;
      } else if (request.direction === 'next') {
        if (sourceLocation.index >= siblings.length - 1) {
          announceDropFeedback('Component is already last in this group.');
          return;
        }
        index = sourceLocation.index + 1;
      } else {
        if (!sourceLocation.parentId) {
          announceDropFeedback('Component is already at the top level.');
          return;
        }

        const parentLocation = findNodeLocation(document, sourceLocation.parentId);
        newParentId = parentLocation?.parentId ?? null;
        newSlotName = parentLocation?.slotName;
        index = (parentLocation?.index ?? document.rootNodes.length - 1) + 1;
      }

      const result = commandServiceRef.current?.execute({
        id: `move-component-${Date.now()}`,
        type: 'move-component',
        timestamp: new Date().toISOString(),
        data: {
          nodeId: request.nodeId,
          newParentId,
          newSlotName,
          index,
        },
      } satisfies MoveComponentCommand);

      if (!result?.success) {
        announceDropFeedback('Could not move component with the keyboard command.');
        return;
      }

      publishDocument(result.document);
      setDropFeedback(null);
      setSelectedId(request.nodeId);
    },
    [announceDropFeedback, document, publishDocument],
  );

  const handleDeleteComponent = useCallback(() => {
    if (!selectedId) {
      return;
    }

    const nextDocument = executeCommand({
      id: `delete-component-${Date.now()}`,
      type: 'delete-component',
      timestamp: new Date().toISOString(),
      data: {
        nodeId: selectedId as NodeId,
      },
    });
    if (!nextDocument) {
      return;
    }

    setSelectedId(null);
  }, [executeCommand, selectedId]);

  const handleUpdateComponent = useCallback(
    (payload: { readonly props: Record<string, unknown>; readonly name?: string }) => {
      if (!selectedInstance) {
        return;
      }

      const nextDocument = executeCommand({
        id: `update-props-${Date.now()}`,
        type: 'update-props',
        timestamp: new Date().toISOString(),
        data: {
          nodeId: selectedInstance.id,
          props: payload.props,
          name: payload.name,
        },
      });
      if (!nextDocument) {
        return;
      }

      setDrawerOpen(false);
      setEditingId(null);
    },
    [executeCommand, selectedInstance],
  );

  const handleNestSelectedIntoParent = useCallback(() => {
    if (!selectedId || !selectedInstance) {
      return;
    }

    const rootTargetId = document.rootNodes.find((nodeId) => {
      if (nodeId === selectedInstance.id) {
        return false;
      }

      const candidate = document.nodes.get(nodeId);
      return candidate ? isContainerContract(candidate.contractName) : false;
    });

    if (!rootTargetId) {
      return;
    }

    const parent = document.nodes.get(rootTargetId);
    const slotName = parent ? getDefaultSlotName(parent.contractName) : undefined;
    if (!slotName) {
      return;
    }

    const nextDocument = executeCommand({
      id: `move-component-${Date.now()}`,
      type: 'move-component',
      timestamp: new Date().toISOString(),
      data: {
        nodeId: selectedId as NodeId,
        newParentId: rootTargetId,
        newSlotName: slotName,
      },
    });
    if (!nextDocument) {
      return;
    }
  }, [document, executeCommand, selectedId, selectedInstance]);

  const topLevelNodes = useMemo(
    () =>
      document.rootNodes.filter((nodeId) => document.nodes.has(nodeId)),
    [document],
  );

  const editingInstance = editingId
    ? document.nodes.get(editingId as NodeId)
    : undefined;

  return (
    <Box className="flex h-full relative" data-testid="page-designer">
      <Paper elevation={2} className="w-[240px] overflow-y-auto rounded-none border-r p-4">
        <Box className="flex items-center justify-between mb-2">
          <Typography variant="h6">
            Registry Components
          </Typography>
          <IconButton
            size="small"
            onClick={() => {
              setImportInput('');
              setGuidedSourceLocator('');
              setImportError(null);
              setImportWorkflowMode('semantic-model');
              setImportPanelOpen(true);
            }}
            title="Import from code / Decompile"
            aria-label="Import from code"
            data-testid="page-designer-import-btn"
          >
            <Upload size={14} />
          </IconButton>
        </Box>
        <Stack spacing={1}>
          {palette.map((entry) => (
            <Button
              key={entry.id}
              variant="outline"
              onClick={() => handleAddComponent(entry.name)}
              fullWidth
              className="justify-start"
              title={entry.tooltip}
              data-testid={`page-component-${entry.name.toLowerCase()}`}
              disabled={!canAdd}
              draggable
              onDragStart={(event) => handlePaletteDragStart(event, entry.name)}
            >
              {entry.displayName}
            </Button>
          ))}
        </Stack>

        {/* Import panel — shown below palette when open */}
        {importPanelOpen && (
          <Box
            className="mt-4 rounded-lg border border-border bg-muted/20 p-3"
            data-testid="page-designer-import-panel"
          >
            <Box className="flex items-center justify-between mb-2">
              <Typography variant="body2" style={{ fontWeight: 600 }}>
                Import from code
              </Typography>
              <IconButton
                size="small"
                onClick={() => setImportPanelOpen(false)}
                aria-label="Close import panel"
              >
                <X size={12} />
              </IconButton>
            </Box>
            <Typography variant="caption" color="muted" style={{ display: 'block', marginBottom: 6 }}>
              Step 1: choose an import path. Step 2: review source details and confidence. Step 3: decompile and apply reviewed artifacts.
            </Typography>
            <Box className="mb-2 flex gap-2" role="group" aria-label="Import workflow mode">
              <Button
                variant={importWorkflowMode === 'semantic-model' ? 'solid' : 'outline'}
                size="small"
                onClick={() => {
                  setImportWorkflowMode('semantic-model');
                  setImportError(null);
                }}
                data-testid="page-import-mode-semantic"
              >
                Semantic model
              </Button>
              <Button
                variant={importWorkflowMode === 'source' ? 'solid' : 'outline'}
                size="small"
                onClick={() => {
                  setImportWorkflowMode('source');
                  setImportError(null);
                }}
                data-testid="page-import-mode-source"
              >
                Governed source
              </Button>
            </Box>
            {importWorkflowMode === 'source' ? (
              <Box>
                <Typography variant="caption" style={{ display: 'block', marginBottom: 4 }}>
                  Source type
                </Typography>
                <select
                  value={guidedSourceType}
                  onChange={(event) => setGuidedSourceType(event.target.value as ImportSourceType)}
                  aria-label="Source import type"
                  data-testid="page-designer-source-type"
                  className="mb-2 w-full rounded border border-border bg-surface px-2 py-1 text-xs"
                >
                  <option value="tsx">TSX component</option>
                  <option value="route">Route file</option>
                  <option value="storybook">Storybook story</option>
                  <option value="artifact">Artifact ID</option>
                  <option value="zip">Zip archive</option>
                </select>
                <input
                  value={guidedSourceLocator}
                  onChange={(event) => {
                    setGuidedSourceLocator(event.target.value);
                    setImportError(null);
                  }}
                  placeholder="https://example.com/Page.tsx"
                  aria-label="Source locator"
                  data-testid="page-designer-source-locator"
                  className="w-full rounded border border-border bg-surface px-2 py-1 text-xs"
                />
              </Box>
            ) : (
              <TextArea
                ref={importTextareaRef}
                value={importInput}
                onChange={(e) => {
                  setImportInput(e.target.value);
                  setImportError(null);
                }}
                rows={6}
                placeholder='{"pages": [{"name": "Home", ...}]}'
                aria-label="Paste semantic model JSON"
                data-testid="page-designer-import-textarea"
                className="w-full font-mono text-xs"
              />
            )}
            {importError && (
              <Typography variant="caption" color="danger" style={{ display: 'block', marginTop: 4 }}>
                {importError}
              </Typography>
            )}
            <Button
              variant="solid"
              size="small"
              onClick={handleImportConfirm}
              fullWidth
              style={{ marginTop: 8 }}
              data-testid="page-designer-import-confirm"
            >
              Decompile &amp; load
            </Button>
          </Box>
        )}

        {/* Residual islands notice */}
        {importResiduals.length > 0 && (
          <Box
            className="mt-4 rounded-lg border border-warning-border bg-warning-bg p-3"
            data-testid="page-designer-residuals"
          >
            <Typography variant="caption" style={{ fontWeight: 600, display: 'block', marginBottom: 4 }}>
              {importResiduals.length} residual island{importResiduals.length !== 1 ? 's' : ''} (review required)
            </Typography>
            {importResiduals.map((id) => (
              <Typography key={id} variant="caption" style={{ display: 'block', fontFamily: 'monospace', fontSize: '0.7rem' }}>
                {id}
              </Typography>
            ))}
          </Box>
        )}

        {/* Round-trip fidelity summary */}
        {importFidelity ? (
          <Box
            className="mt-4 rounded-lg border border-sky-200 bg-sky-50 p-3"
            data-testid="page-designer-roundtrip-fidelity"
          >
            <Typography variant="caption" style={{ fontWeight: 600, display: 'block', marginBottom: 4 }}>
              Round-trip fidelity: {Math.round(importFidelity.confidence * 100)}%
            </Typography>
            <Typography variant="caption" style={{ display: 'block' }}>
              {importFidelity.canRoundTrip ? 'Can fully round-trip' : 'Loss points detected'}
            </Typography>
            {importFidelity.lossPoints.length > 0 ? (
              <Typography variant="caption" style={{ display: 'block', marginTop: 4 }}>
                {importFidelity.lossPoints.length} loss point{importFidelity.lossPoints.length !== 1 ? 's' : ''}
              </Typography>
            ) : null}
          </Box>
        ) : null}
      </Paper>

      <Box
        className="flex-1 overflow-y-auto p-6"
        style={{ backgroundColor: 'var(--bg-surface)' }}
        data-testid="page-design-area"
        onDragOver={(event) => event.preventDefault()}
        onDrop={handleDesignAreaDrop}
      >
        {validation.errors.length > 0 || validation.warnings.length > 0 ? (
          <Paper
            elevation={1}
            className="mb-4 flex items-start gap-3 border border-warning-border bg-warning-bg p-3"
          >
            <AlertTriangle size={16} />
            <Box>
              <Typography variant="body2">
                {validation.errors.length} error(s), {validation.warnings.length} warning(s)
              </Typography>
              {validation.errors[0] ? (
                <Typography variant="caption" color="danger">
                  {validation.errors[0].message}
                </Typography>
              ) : null}
            </Box>
          </Paper>
        ) : null}

        {commandAuditWarning ? (
          <Paper
            elevation={1}
            className="mb-4 flex items-start gap-3 border border-warning-border bg-warning-bg p-3"
            data-testid="page-builder-audit-warning"
          >
            <AlertTriangle size={16} />
            <Typography variant="caption" color="danger">
              {commandAuditWarning}
            </Typography>
          </Paper>
        ) : null}

        {dropFeedback ? (
          <Paper
            elevation={1}
            className="mb-4 flex items-start gap-3 border border-warning-border bg-warning-bg p-3"
            data-testid="page-drop-feedback"
            role="alert"
            aria-live="polite"
          >
            <AlertTriangle size={16} />
            <Typography variant="caption" color="danger">
              {dropFeedback}
            </Typography>
          </Paper>
        ) : null}

        {selectedInstance ? (
          <Paper elevation={3} className="absolute right-4 top-4 z-10 flex gap-2 p-2">
            <IconButton
              size="small"
              color="primary"
              onClick={() => {
                if (!canEdit) return;
                setEditingId(selectedInstance.id);
                setDrawerOpen(true);
              }}
              title="Edit Properties"
              disabled={!canEdit}
            >
              <EditIcon size={16} />
            </IconButton>
            <IconButton
              size="small"
              color="error"
              onClick={handleDeleteComponent}
              title="Delete"
              disabled={!canDelete}
            >
              <DeleteIcon size={16} />
            </IconButton>
            {document.rootNodes.includes(selectedInstance.id) ? (
              <Button variant="outline" size="small" onClick={handleNestSelectedIntoParent}>
                Nest
              </Button>
            ) : null}
          </Paper>
        ) : null}

        <Paper elevation={1} className="mx-auto min-h-[600px] max-w-[900px] bg-white p-8">
          {topLevelNodes.length === 0 ? (
            <Box
              className="flex h-[400px] items-center justify-center rounded-lg"
              style={{ border: '2px dashed var(--border-subtle, #d1d5db)' }}
            >
              <Stack alignItems="center" spacing={2}>
                <AddIcon className="text-5xl text-fg-muted dark:text-fg-muted" />
                <Typography variant="h6" color="text.secondary">
                  Drag components from the registry to start designing
                </Typography>
              </Stack>
            </Box>
          ) : (
            <Stack spacing={2}>
              {topLevelNodes.map((nodeId) => (
                <ComponentRenderer
                  key={nodeId}
                  document={document}
                  nodeId={nodeId}
                  selectedNodeId={selectedId}
                  hoveredNodeId={effectiveHoveredNodeId}
                  onSelect={setSelectedId}
                  onNodeHover={handleNodeHover}
                  onDropRequest={handleRendererDropRequest}
                  onKeyboardMoveRequest={handleKeyboardMoveRequest}
                />
              ))}
            </Stack>
          )}
        </Paper>
      </Box>

      <Drawer
        anchor="right"
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setEditingId(null);
        }}
        PaperProps={{
          style: { width: 360 },
        }}
      >
        <Box className="flex items-center justify-between p-4">
          <Typography variant="h6">Registry Properties</Typography>
        </Box>
        {editingInstance ? (
          <PropertyForm
            contractName={editingInstance.contractName}
            instanceName={editingInstance.metadata.name}
            initialProps={editingInstance.props}
            onUpdate={handleUpdateComponent}
            onCancel={() => {
              setDrawerOpen(false);
              setEditingId(null);
            }}
          />
        ) : null}
      </Drawer>

      {/* AI Governance panel — shown when there are pending AI actions */}
      {pendingAIActions.length > 0 && (
        <Box
          className="absolute bottom-4 right-4 z-20 w-80 rounded-xl border border-info-border bg-white shadow-lg"
          data-testid="governance-panel"
          role="region"
          aria-label="Governance review panel"
        >
          <Box className="flex items-center justify-between px-4 py-3 border-b border-info-border">
            <Typography variant="body2" style={{ fontWeight: 600 }}>
              Suggested improvements — review required ({pendingAIActions.length})
            </Typography>
          </Box>
          <Box className="max-h-60 overflow-y-auto p-3 space-y-2">
            {pendingAIActions.map((action) => (
              <Box
                key={action.actionId}
                className="rounded-lg border border-border bg-surface-muted p-2"
                data-testid={`ai-action-${action.actionId}`}
              >
                <Typography variant="caption" style={{ display: 'block', fontWeight: 600 }}>
                  {action.hookKind}
                </Typography>
                <Typography variant="caption" style={{ display: 'block', color: '#4b5563' }}>
                  {action.reason}
                </Typography>
                <Typography variant="caption" style={{ display: 'block', color: '#9ca3af' }}>
                  Confidence: {Math.round(action.confidence * 100)}%
                </Typography>
                <Box className="flex gap-2 mt-2">
                  <Button
                    variant="solid"
                    size="small"
                    onClick={() => handleAIActionReview(action.actionId, 'accepted')}
                    data-testid={`ai-action-accept-${action.actionId}`}
                  >
                    Accept
                  </Button>
                  <Button
                    variant="outline"
                    size="small"
                    onClick={() => handleAIActionReview(action.actionId, 'rejected')}
                    data-testid={`ai-action-reject-${action.actionId}`}
                  >
                    Reject
                  </Button>
                </Box>
              </Box>
            ))}
          </Box>
        </Box>
      )}
    </Box>
  );
};

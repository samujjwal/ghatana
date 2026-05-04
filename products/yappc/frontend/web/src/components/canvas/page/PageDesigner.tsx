import { Plus as AddIcon, Trash2 as DeleteIcon, Pencil as EditIcon, AlertTriangle, Upload, X } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  IconButton,
  Button,
  Surface as Paper,
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
import type { DropRequest } from './ComponentRenderer';
import { importPageArtifactsFromCode } from './artifactCompilerBridge';
import { AIActionLineageTracker, createAIChangeRecord } from './pageArtifactDocument';
import type { PageArtifactAIChangeRecord } from './pageArtifactDocument';

import type { ComponentData } from './schemas';
import {
  deleteNode,
  insertNode,
  moveNode,
  updateNodeProps,
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

function withRootOrder(document: BuilderDocument, orderedRootNodes: readonly NodeId[]): BuilderDocument {
  return {
    ...document,
    rootNodes: [...orderedRootNodes],
  };
}

function withSlotOrder(
  document: BuilderDocument,
  parentId: NodeId,
  slotName: string,
  orderedChildren: readonly NodeId[],
): BuilderDocument {
  const parent = document.nodes.get(parentId);
  if (!parent) {
    return document;
  }

  const nextParent = {
    ...parent,
    slots: {
      ...parent.slots,
      [slotName]: [...orderedChildren],
    },
  };

  const nextNodes = new Map(document.nodes);
  nextNodes.set(parentId, nextParent);

  return {
    ...document,
    nodes: nextNodes,
  };
}

function reorderIntoIndex(
  items: readonly NodeId[],
  nodeId: NodeId,
  targetIndex: number,
): readonly NodeId[] {
  const withoutNode = items.filter((id) => id !== nodeId);
  const clampedIndex = Math.max(0, Math.min(targetIndex, withoutNode.length));
  return [
    ...withoutNode.slice(0, clampedIndex),
    nodeId,
    ...withoutNode.slice(clampedIndex),
  ];
}

function applyOrderInContainer(
  document: BuilderDocument,
  nodeId: NodeId,
  parentId: NodeId | null,
  slotName: string | undefined,
  targetIndex: number,
): BuilderDocument {
  const children = getNodeChildren(document, parentId, slotName);
  const nextOrder = reorderIntoIndex(children, nodeId, targetIndex);

  if (!parentId) {
    return withRootOrder(document, nextOrder);
  }

  if (!slotName) {
    return document;
  }

  return withSlotOrder(document, parentId, slotName, nextOrder);
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

/**
 * @doc.type component
 * @doc.purpose Registry-driven YAPPC page designer using shared BuilderDocument contract from @ghatana/ui-builder
 * @doc.layer product
 * @doc.pattern Widget
 */
interface PageDesignerProps {
  readonly initialComponents?: ComponentData[] | BuilderDocument;
  readonly onComponentsChange?: (components: ComponentData[]) => void;
  readonly onDocumentChange?: (document: BuilderDocument, validation: ValidationResult) => void;
  /** Called when the designer imports a decompiled model, producing one or more page artifacts */
  readonly onImportArtifacts?: (artifacts: readonly import('./pageArtifactDocument').PageArtifactDocument[]) => void;
  /** Called when an AI change is applied to the page (for audit/governance recording) */
  readonly onAIChangeRecord?: (record: PageArtifactAIChangeRecord) => void;
}

export const PageDesigner: React.FC<PageDesignerProps> = ({
  initialComponents = [],
  onComponentsChange,
  onDocumentChange,
  onImportArtifacts,
  onAIChangeRecord,
}) => {
  const contracts = useMemo(() => getContractMap(), []);
  const palette = useMemo(() => getBuilderPalette(), []);
  const [document, setDocument] = useState<BuilderDocument>(() => {
    if (isBuilderDocument(initialComponents)) {
      return initialComponents;
    }
    return componentDataToBuilderDocument(initialComponents as ComponentData[]);
  });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [importPanelOpen, setImportPanelOpen] = useState(false);
  const [importInput, setImportInput] = useState('');
  const [importError, setImportError] = useState<string | null>(null);
  const [importResiduals, setImportResiduals] = useState<readonly string[]>([]);
  const [importFidelity, setImportFidelity] = useState<
    import('./pageArtifactDocument').PageArtifactDocument['roundTripFidelity'] | null
  >(null);
  const importTextareaRef = useRef<HTMLTextAreaElement>(null);

  // Governance trace tracker — scoped to this designer session
  const lineageTrackerRef = useRef(new AIActionLineageTracker());
  const [pendingAIActions, setPendingAIActions] = useState<readonly import('./pageArtifactDocument').AIActionLineage[]>([]);

  const validation = useMemo(() => validateDocument(document, contracts), [contracts, document]);

  useEffect(() => {
    onDocumentChange?.(document, validation);
  }, [document, onDocumentChange, validation]);

  const selectedInstance = selectedId ? document.nodes.get(selectedId as NodeId) : undefined;

  const publishDocument = useCallback(
    (nextDocument: BuilderDocument) => {
      setDocument(nextDocument);
      onComponentsChange?.(builderDocumentToComponentData(nextDocument));
    },
    [onComponentsChange],
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
    },
    [],
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

  const handleImportConfirm = useCallback(() => {
    setImportError(null);
    if (!importInput.trim()) {
      setImportError('Paste a JSON semantic model to import.');
      return;
    }

    let artifacts: readonly import('./pageArtifactDocument').PageArtifactDocument[];
    try {
      artifacts = importPageArtifactsFromCode(importInput.trim(), 'import');
    } catch (err) {
      setImportError(err instanceof Error ? err.message : 'Invalid JSON — could not parse semantic model.');
      return;
    }

    const first = artifacts[0];
    if (!first) {
      setImportError('No pages found in the imported model.');
      return;
    }

    // Load the first page into the editor
    const imported = first.serializedBuilderDocument;
    if (imported) {
      publishDocument(deserializeDocument(imported));
    }

    // Surface residual islands if any
    const residuals = first.residualIslandIds ?? [];
    setImportResiduals(residuals);
    setImportFidelity(first.roundTripFidelity ?? null);

    const importedDocument = deserializeDocument(first.serializedBuilderDocument);
    const importedNodes = importedDocument.nodes;
    const affectedNodeIds: readonly string[] =
      importedNodes instanceof Map
        ? [...importedNodes.keys()]
        : Object.keys(importedNodes as Record<string, unknown>);
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
  }, [importInput, onImportArtifacts, publishDocument, recordAIChange]);

  const handleAddComponent = useCallback(
    (contractOrType: string) => {
      const contractName = normalizeContractName(contractOrType);
      const legacyType = toLegacyComponentType(contractName) as LegacyComponentType;
      const newComponent = getDefaultComponentData(legacyType) as ComponentData;
      let insertedNodeId: string | null = null;
      const target = resolveInsertionTarget();

      const nextDocument = insertNode(
        document,
        componentDataToInsertableInstance(newComponent),
        target.parentId,
        target.slotName,
        {
          onNodeInserted: ({ nodeId }) => {
            insertedNodeId = nodeId;
          },
        },
      );

      publishDocument(nextDocument);
      setSelectedId(insertedNodeId);
    },
    [document, publishDocument, resolveInsertionTarget],
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
        let insertedNodeId: string | null = null;

        const insertedDocument = insertNode(
          document,
          componentDataToInsertableInstance(newComponent),
          undefined,
          undefined,
          {
            onNodeInserted: ({ nodeId }) => {
              insertedNodeId = nodeId;
            },
          },
        );

        publishDocument(insertedDocument);
        setSelectedId(insertedNodeId);
        return;
      }

      const draggedNodeId = event.dataTransfer.getData('application/x-page-node');
      if (draggedNodeId && document.nodes.has(draggedNodeId as NodeId)) {
        const movedDocument = moveNode(document, draggedNodeId as NodeId, null);
        const reordered = applyOrderInContainer(
          movedDocument,
          draggedNodeId as NodeId,
          null,
          undefined,
          movedDocument.rootNodes.length,
        );
        publishDocument(reordered);
        setSelectedId(draggedNodeId);
      }
    },
    [document, publishDocument],
  );

  const handleRendererDropRequest = useCallback(
    (request: DropRequest) => {
      if (request.source.kind === 'node' && request.source.nodeId === request.targetNodeId) {
        return;
      }

      if (request.placement === 'slot' && !request.slotName) {
        return;
      }

      const targetLocation = findNodeLocation(document, request.targetNodeId);
      if (!targetLocation && request.placement !== 'slot') {
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
        let insertedNodeId: NodeId | null = null;

        let nextDocument = insertNode(
          document,
          componentDataToInsertableInstance(newComponent),
          destinationParentId ?? undefined,
          destinationSlotName,
          {
            onNodeInserted: ({ nodeId }) => {
              insertedNodeId = nodeId;
            },
          },
        );

        if (!insertedNodeId) {
          return;
        }

        const destinationChildren = getNodeChildren(nextDocument, destinationParentId, destinationSlotName);
        const insertionIndex =
          request.placement === 'slot'
            ? destinationChildren.length
            : (targetLocation?.index ?? destinationChildren.length) +
              (request.placement === 'after' ? 1 : 0);

        nextDocument = applyOrderInContainer(
          nextDocument,
          insertedNodeId,
          destinationParentId,
          destinationSlotName,
          insertionIndex,
        );

        publishDocument(nextDocument);
        setSelectedId(insertedNodeId);
        return;
      }

      const sourceNodeId = request.source.nodeId;
      if (!document.nodes.has(sourceNodeId)) {
        return;
      }

      if (destinationParentId && isDescendant(document, sourceNodeId, destinationParentId)) {
        return;
      }

      let nextDocument = moveNode(
        document,
        sourceNodeId,
        destinationParentId,
        destinationSlotName,
      );

      const destinationChildren = getNodeChildren(nextDocument, destinationParentId, destinationSlotName);
      const insertionIndex =
        request.placement === 'slot'
          ? destinationChildren.length
          : (targetLocation?.index ?? destinationChildren.length) +
            (request.placement === 'after' ? 1 : 0);

      nextDocument = applyOrderInContainer(
        nextDocument,
        sourceNodeId,
        destinationParentId,
        destinationSlotName,
        insertionIndex,
      );

      publishDocument(nextDocument);
      setSelectedId(sourceNodeId);
    },
    [document, publishDocument],
  );

  const handleDeleteComponent = useCallback(() => {
    if (!selectedId) {
      return;
    }

    const nextDocument = deleteNode(document, selectedId as NodeId);
    publishDocument(nextDocument);
    setSelectedId(null);
  }, [document, publishDocument, selectedId]);

  const handleUpdateComponent = useCallback(
    (payload: { readonly props: Record<string, unknown>; readonly name?: string }) => {
      if (!selectedInstance) {
        return;
      }

      let nextDocument = updateNodeProps(
        document,
        selectedInstance.id,
        payload.props,
      );

      const currentNode = nextDocument.nodes.get(selectedInstance.id);
      if (currentNode && payload.name !== currentNode.metadata.name) {
        const updatedNode = {
          ...currentNode,
          metadata: {
            ...currentNode.metadata,
            name: payload.name,
          },
        };
        const nextNodes = new Map(nextDocument.nodes);
        nextNodes.set(selectedInstance.id, updatedNode);
        nextDocument = {
          ...nextDocument,
          nodes: nextNodes,
        };
      }

      publishDocument(nextDocument);
      setDrawerOpen(false);
      setEditingId(null);
    },
    [document, publishDocument, selectedInstance],
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

    const nextDocument = moveNode(
      document,
      selectedId as NodeId,
      rootTargetId,
      slotName,
    );
    publishDocument(nextDocument);
  }, [document, publishDocument, selectedId, selectedInstance]);

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
              setImportError(null);
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
            className="mt-4 rounded-lg border border-indigo-200 bg-indigo-50 p-3"
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
              Paste a JSON semantic model (SemanticProductModel) to decompile into canvas components.
            </Typography>
            <textarea
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
              style={{
                width: '100%',
                fontSize: '0.75rem',
                fontFamily: 'monospace',
                border: importError ? '1px solid #ef4444' : '1px solid #c7d2fe',
                borderRadius: 4,
                padding: '6px 8px',
                resize: 'vertical',
                background: 'white',
                outline: 'none',
              }}
            />
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
            className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-3"
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
            className="mb-4 flex items-start gap-3 border border-amber-300 bg-amber-50 p-3"
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

        {selectedInstance ? (
          <Paper elevation={3} className="absolute right-4 top-4 z-10 flex gap-2 p-2">
            <IconButton
              size="small"
              color="primary"
              onClick={() => {
                setEditingId(selectedInstance.id);
                setDrawerOpen(true);
              }}
              title="Edit Properties"
            >
              <EditIcon size={16} />
            </IconButton>
            <IconButton
              size="small"
              color="error"
              onClick={handleDeleteComponent}
              title="Delete"
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
              style={{ border: '2px dashed #ccc' }}
            >
              <Stack alignItems="center" spacing={2}>
                <AddIcon className="text-5xl text-gray-500 dark:text-gray-400" />
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
                  onSelect={setSelectedId}
                  onDropRequest={handleRendererDropRequest}
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
          className="absolute bottom-4 right-4 z-20 w-80 rounded-xl border border-indigo-200 bg-white shadow-lg"
          data-testid="governance-panel"
          role="region"
          aria-label="Governance review panel"
        >
          <Box className="flex items-center justify-between px-4 py-3 border-b border-indigo-100">
            <Typography variant="body2" style={{ fontWeight: 600 }}>
              Suggested improvements — review required ({pendingAIActions.length})
            </Typography>
          </Box>
          <Box className="max-h-60 overflow-y-auto p-3 space-y-2">
            {pendingAIActions.map((action) => (
              <Box
                key={action.actionId}
                className="rounded-lg border border-gray-100 bg-gray-50 p-2"
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

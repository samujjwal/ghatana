/**
 * PageDesignerNode
 *
 * A ReactFlow custom node that hosts the full BuilderDocument-based PageDesigner
 * directly inside the canvas. Supports two states:
 *
 * - **Collapsed**: shows a compact card with the page title, component count, and
 *   a double-click affordance to enter editing mode.
 * - **Expanded**: renders the full interactive PageDesigner as an in-canvas panel.
 *   The node resizes to accommodate the editor and the user can resize freely.
 *
 * Architecture note:
 *   The canvas (ReactFlow graph layer) acts as the spatial container.
 *   Artifact content is rendered as HTML DOM inside ReactFlow nodes, not as
 *   custom 2D canvas primitives.  This preserves full HTML/CSS fidelity for
 *   designed UI components (Button, Card, TextField, etc.) and keeps pan/zoom/
 *   resize/selection handled natively by ReactFlow.
 *
 * @doc.type component
 * @doc.purpose Canvas node embedding the full PageDesigner for in-canvas UI editing
 * @doc.layer product
 * @doc.pattern ReactFlow Custom Node
 */

import { Input } from '../../ui/Input';
import React, { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Handle, Position, NodeResizer, type Node, type NodeProps } from '@xyflow/react';
import { Box, Button, IconButton, TextArea, Typography } from '@ghatana/design-system';
import { Maximize2 as ExpandIcon, Minimize2 as CollapseIcon, Layout as PageIcon } from 'lucide-react';
import { useAtomValue, useSetAtom } from 'jotai';
import { useParams } from 'react-router';
import { usePhaseContext } from '@/context';
import { getPhaseCanvasConfig, type PhaseCanvasConfig } from '@/services/canvas/phase-config/PhaseCanvasConfig';
import { currentWorkspaceIdAtom } from '@/state/atoms/workspaceAtom';
import { currentUserAtom } from '@/stores/user.store';

import { PageDesigner, type PageDesignerDocumentChangeContext } from '@/components/canvas/page/PageDesigner';
import { LivePreviewPanel } from '@/components/studio/LivePreviewPanel';
import { AddNodeCommand, executeCommandAtom, UpdateNodeDataCommand } from '../workspace/canvasCommands';
import {
  appendAIChangeRecord,
  appendPageArtifactOperationRecord,
  getBuilderDocument,
  getSerializedNodeCount,
  updateAIChangeRecordReviewState,
  updatePageArtifactDocument,
  type PageArtifactAIChangeRecord,
  type PageArtifactDocument,
} from '../page/pageArtifactDocument';
import { type BuilderDocument, type ValidationResult } from '@ghatana/ui-builder';
import {
  HttpPageArtifactPersistenceAdapter,
  LocalStoragePageArtifactPersistenceAdapter,
  ResilientPageArtifactPersistenceAdapter,
  isConflictError,
} from '../page/pageArtifactPersistence';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface PageDesignerNodeData extends Record<string, unknown> {
  /** Human-readable page label shown in collapsed state */
  label?: string;
  /**
   * The live BuilderDocument for this page.
   * Stored in node data so the canvas persistence layer can round-trip it.
   * When undefined the PageDesigner starts with an empty document.
   */
  pageDocument?: PageArtifactDocument;
  /** Whether the node is in expanded editing mode (persisted in node data) */
  expanded?: boolean;
  /** Latest validation summary for inline governance badges */
  validationSummary?: PageArtifactDocument['validationSummary'];
  /** Backend/canvas policy lock propagated from the parent canvas surface. */
  readOnly?: boolean;
  /** Human-readable reason shown when page-builder mutations are locked. */
  readOnlyReason?: string;
  /** Runtime bridge for canvas hosts that do not use workspace command atoms. */
  onDataChange?: (updates: Partial<PageDesignerNodeData>) => void;
}

export type PageDesignerCanvasNode = Node<PageDesignerNodeData, 'page-designer'>;

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const COLLAPSED_WIDTH = 220;
const COLLAPSED_HEIGHT = 90;
const EXPANDED_WIDTH = 880;
const EXPANDED_HEIGHT = 640;

function hasSameSerializedDocument(left: PageArtifactDocument, right: PageArtifactDocument): boolean {
  return JSON.stringify(left.serializedBuilderDocument) === JSON.stringify(right.serializedBuilderDocument);
}

function hasSameValidationSummary(
  current: PageDesignerNodeData['validationSummary'],
  next: NonNullable<PageDesignerNodeData['validationSummary']>,
): boolean {
  return (
    current?.valid === next.valid &&
    current?.errorCount === next.errorCount &&
    current?.warningCount === next.warningCount
  );
}

function getPersistenceKey(pageDocument: PageArtifactDocument): string {
  return [
    pageDocument.artifactId,
    pageDocument.documentId,
    pageDocument.updatedAt,
    pageDocument.syncStatus,
  ].join(':');
}

type ConflictMergeChoice = 'local' | 'remote';

interface ConflictMergeRow {
  readonly nodeId: string;
  readonly status: 'local-only' | 'remote-only' | 'changed' | 'same';
  readonly localLabel: string;
  readonly remoteLabel: string;
}

function getSerializedNodes(pageDocument: PageArtifactDocument): Record<string, unknown> {
  return pageDocument.serializedBuilderDocument.nodes as Record<string, unknown>;
}

function getSerializedRootNodes(pageDocument: PageArtifactDocument): readonly string[] {
  return pageDocument.serializedBuilderDocument.rootNodes.map(String);
}

function getSerializedNodeLabel(node: unknown): string {
  if (!node || typeof node !== 'object') {
    return 'missing';
  }

  const candidate = node as {
    readonly contractName?: unknown;
    readonly metadata?: { readonly name?: unknown };
  };
  if (typeof candidate.metadata?.name === 'string' && candidate.metadata.name.trim()) {
    return candidate.metadata.name;
  }
  if (typeof candidate.contractName === 'string' && candidate.contractName.trim()) {
    return candidate.contractName;
  }
  return 'component';
}

function buildConflictMergeRows(
  localDocument: PageArtifactDocument | undefined,
  remoteDocument: PageArtifactDocument | null,
): readonly ConflictMergeRow[] {
  if (!localDocument || !remoteDocument) {
    return [];
  }

  const localNodes = getSerializedNodes(localDocument);
  const remoteNodes = getSerializedNodes(remoteDocument);
  const nodeIds = Array.from(new Set([...Object.keys(localNodes), ...Object.keys(remoteNodes)])).sort();
  return nodeIds.map((nodeId) => {
    const localNode = localNodes[nodeId];
    const remoteNode = remoteNodes[nodeId];
    const status = !remoteNode
      ? 'local-only'
      : !localNode
        ? 'remote-only'
        : JSON.stringify(localNode) === JSON.stringify(remoteNode)
          ? 'same'
          : 'changed';

    return {
      nodeId,
      status,
      localLabel: getSerializedNodeLabel(localNode),
      remoteLabel: getSerializedNodeLabel(remoteNode),
    };
  });
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

const PageDesignerNodeInner: React.FC<NodeProps<PageDesignerCanvasNode>> = ({
  id,
  data,
  positionAbsoluteX,
  positionAbsoluteY,
  selected,
}) => {
  const executeCommand = useSetAtom(executeCommandAtom);
  const { projectId } = useParams<{ projectId: string }>();
  const currentUser = useAtomValue(currentUserAtom);
  const currentWorkspaceId = useAtomValue(currentWorkspaceIdAtom);
  const [isExpanded, setIsExpanded] = useState<boolean>(data.expanded ?? false);
  const [previewSelectedNodeId, setPreviewSelectedNodeId] = useState<string | null>(null);
  const [previewHoveredNodeId, setPreviewHoveredNodeId] = useState<string | null>(null);
  const [overwriteReason, setOverwriteReason] = useState<string>('');
  const [remoteConflictDocument, setRemoteConflictDocument] = useState<PageArtifactDocument | null>(null);
  const [conflictMergeChoices, setConflictMergeChoices] = useState<Readonly<Record<string, ConflictMergeChoice>>>({});
  const { currentPhase } = usePhaseContext();
  const phaseConfig = useMemo(
    () => (currentPhase ? getPhaseCanvasConfig(currentPhase) : undefined),
    [currentPhase],
  );
  const isPolicyReadOnly = data.readOnly === true;
  const effectivePhaseConfig = useMemo<PhaseCanvasConfig | undefined>(() => {
    if (!phaseConfig && isPolicyReadOnly) {
      return {
        mode: 'validate',
        visibleTools: ['select', 'inspect', 'preview'],
        defaultTool: 'inspect',
        allowEditing: false,
        allowAddComponent: false,
        allowDelete: false,
        allowEdgeManipulation: false,
        enableValidation: true,
        enablePreview: true,
      };
    }

    if (!phaseConfig || !isPolicyReadOnly) {
      return phaseConfig;
    }

    return {
      ...phaseConfig,
      allowEditing: false,
      allowAddComponent: false,
      allowDelete: false,
      allowEdgeManipulation: false,
    };
  }, [isPolicyReadOnly, phaseConfig]);
  const canMutatePageDocument = effectivePhaseConfig?.allowEditing ?? !isPolicyReadOnly;
  const pageBuilderReadOnlyReason = data.readOnlyReason ?? 'Page builder edits are unavailable in this canvas mode.';
  const builderDocument = useMemo(() => getBuilderDocument(data.pageDocument), [data.pageDocument]);
  const latestPageDocumentRef = useRef<PageArtifactDocument | undefined>(data.pageDocument);
  const onDataChangeRef = useRef<PageDesignerNodeData['onDataChange']>(data.onDataChange);
  const persistingDocumentKeyRef = useRef<string | null>(null);
  const pagePersistenceAdapter = useMemo(
    () =>
      new ResilientPageArtifactPersistenceAdapter(
        new HttpPageArtifactPersistenceAdapter({
          scopeProvider: () => {
            if (!currentUser?.tenantId || !currentWorkspaceId || !projectId) {
              return null;
            }

            return {
              tenantId: currentUser.tenantId,
              workspaceId: currentWorkspaceId,
              projectId,
            };
          },
        }),
        new LocalStoragePageArtifactPersistenceAdapter('@ghatana/yappc:page-builder:'),
      ),
    [currentUser?.tenantId, currentWorkspaceId, projectId],
  );

  const nodeCount = getSerializedNodeCount(data.pageDocument);
  const conflictMergeRows = useMemo(
    () => buildConflictMergeRows(data.pageDocument, remoteConflictDocument),
    [data.pageDocument, remoteConflictDocument],
  );
  const operationActor = currentUser?.id ?? 'page-designer';
  const appendOperation = useCallback(
    (
      pageDocument: PageArtifactDocument,
      operation: Parameters<typeof appendPageArtifactOperationRecord>[1]['operation'],
      status: Parameters<typeof appendPageArtifactOperationRecord>[1]['status'],
      summary: string,
      metadata?: Parameters<typeof appendPageArtifactOperationRecord>[1]['metadata'],
    ): PageArtifactDocument =>
      appendPageArtifactOperationRecord(pageDocument, {
        operation,
        status,
        actor: operationActor,
        summary,
        phase: currentPhase ?? undefined,
        metadata,
      }),
    [currentPhase, operationActor],
  );
  const updateNodeData = useCallback(
    (
      from: Partial<PageDesignerNodeData>,
      to: Partial<PageDesignerNodeData>,
      label: string,
    ) => {
      if (to.pageDocument) {
        latestPageDocumentRef.current = to.pageDocument;
      }

      if (onDataChangeRef.current) {
        onDataChangeRef.current(to);
        return;
      }

      executeCommand(
        new UpdateNodeDataCommand(
          id,
          from as Record<string, unknown>,
          to as Record<string, unknown>,
          label,
        ),
      );
    },
    [executeCommand, id],
  );

  const persistDirtyDocument = useCallback(
    (pending: PageArtifactDocument) => {
      if (pending.syncStatus !== 'dirty' || !canMutatePageDocument) {
        return;
      }

      const pendingKey = getPersistenceKey(pending);
      if (persistingDocumentKeyRef.current === pendingKey) {
        return;
      }

      persistingDocumentKeyRef.current = pendingKey;
      latestPageDocumentRef.current = pending;

      void pagePersistenceAdapter
        .save(pending)
        .then(() => {
          const latest = latestPageDocumentRef.current;
          if (!latest || latest.syncStatus !== 'dirty' || getPersistenceKey(latest) !== pendingKey) {
            return;
          }

          updateNodeData(
            { pageDocument: latest },
            {
              pageDocument: appendOperation(
                {
                  ...latest,
                  syncStatus: 'synced',
                },
                'persist-success',
                'succeeded',
                'Persisted page document to the scoped artifact store.',
              ),
            },
            'Persist page document',
          );
        })
        .catch((err: unknown) => {
          const latest = latestPageDocumentRef.current;
          if (!latest || latest.syncStatus !== 'dirty' || getPersistenceKey(latest) !== pendingKey) {
            return;
          }

          // For conflict errors, mark as error so the user sees it and can reload.
          const conflict = isConflictError(err);
          const nextStatus = conflict ? 'error' : 'offline';
          const errorMessage = err instanceof Error ? err.message : 'Unknown persistence error';

          updateNodeData(
            { pageDocument: latest },
            {
              pageDocument: appendOperation(
                {
                  ...latest,
                  syncStatus: nextStatus,
                },
                conflict ? 'persist-conflict' : 'persist-offline',
                conflict ? 'requires-review' : 'failed',
                conflict
                  ? 'Detected a newer remote page document version during persistence.'
                  : 'Page document could not be persisted to the server and remains local/offline.',
                { errorMessage },
              ),
            },
            conflict
              ? 'Persist page document (conflict - remote version newer)'
              : 'Persist page document (offline fallback)',
          );
        })
        .finally(() => {
          if (persistingDocumentKeyRef.current === pendingKey) {
            persistingDocumentKeyRef.current = null;
          }
        });
    },
    [appendOperation, canMutatePageDocument, pagePersistenceAdapter, updateNodeData],
  );

  useEffect(() => {
    onDataChangeRef.current = data.onDataChange;
  }, [data.onDataChange]);

  useEffect(() => {
    latestPageDocumentRef.current = data.pageDocument;
  }, [data.pageDocument]);

  useEffect(() => {
    if (data.pageDocument?.syncStatus !== 'error') {
      setRemoteConflictDocument(null);
      setOverwriteReason('');
      setConflictMergeChoices({});
    }
  }, [data.pageDocument?.syncStatus]);

  useEffect(() => {
    if (!data.pageDocument || data.pageDocument.syncStatus !== 'dirty' || !canMutatePageDocument) {
      return;
    }

    persistDirtyDocument(data.pageDocument);
  }, [canMutatePageDocument, data.pageDocument, persistDirtyDocument]);

  const handleToggleExpand = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      const next = !isExpanded;
      setIsExpanded(next);
      updateNodeData(
        { expanded: data.expanded },
        { expanded: next },
        next ? 'Expand page designer' : 'Collapse page designer',
      );
    },
    [data.expanded, isExpanded, updateNodeData],
  );

  const handleDocumentChange = useCallback(
    (
      pageDocument: PageArtifactDocument,
      _document: BuilderDocument,
      validation: ValidationResult,
      context?: PageDesignerDocumentChangeContext,
    ) => {
      if (!canMutatePageDocument) {
        return;
      }
      const currentPageDocument = latestPageDocumentRef.current ?? data.pageDocument;
      const nextValidationSummary = {
        valid: validation.valid,
        errorCount: validation.errors.length,
        warningCount: validation.warnings.length,
      };
      const hasSameDocument =
        currentPageDocument && hasSameSerializedDocument(currentPageDocument, pageDocument);
      if (hasSameDocument) {
        if (hasSameValidationSummary(data.validationSummary, nextValidationSummary)) {
          return;
        }

        updateNodeData(
          {
            validationSummary: data.validationSummary,
          },
          {
            validationSummary: nextValidationSummary,
          },
          'Update page validation summary',
        );
        return;
      }

      if (
        currentPageDocument &&
        hasSameSerializedDocument(currentPageDocument, pageDocument) &&
        hasSameValidationSummary(data.validationSummary, nextValidationSummary)
      ) {
        return;
      }
      const nextPageDocument = appendOperation(
        pageDocument,
        context?.operation ?? 'document-update',
        validation.valid ? 'pending' : 'requires-review',
        context?.summary ?? 'Updated page builder document from in-canvas editor.',
        {
          valid: validation.valid,
          errorCount: validation.errors.length,
          warningCount: validation.warnings.length,
          commandId: context?.commandId ?? null,
          commandType: context?.commandType ?? null,
          changedNodeCount: context?.changedNodeIds?.length ?? 0,
        },
      );
      updateNodeData(
        {
          pageDocument: currentPageDocument,
          validationSummary: data.validationSummary,
        },
        {
          pageDocument: nextPageDocument,
          validationSummary: nextValidationSummary,
        },
        'Update page document',
      );
      persistDirtyDocument(nextPageDocument);
    },
    [appendOperation, canMutatePageDocument, data.pageDocument, data.validationSummary, persistDirtyDocument, updateNodeData],
  );
  const handlePageDesignerDocumentChange = useCallback((
    document: BuilderDocument,
    validation: ValidationResult,
    context?: PageDesignerDocumentChangeContext,
  ) => {
    if (!data.pageDocument || !canMutatePageDocument) {
      return;
    }

    const pageDocument: PageArtifactDocument = updatePageArtifactDocument(
      data.pageDocument,
      document,
      'page-designer',
      'dirty',
      {
        valid: validation.valid,
        errorCount: validation.errors.length,
        warningCount: validation.warnings.length,
      },
    );
    handleDocumentChange(pageDocument, document, validation, context);
  }, [canMutatePageDocument, data.pageDocument, handleDocumentChange]);

  const handleImportArtifacts = useCallback(
    (artifacts: readonly PageArtifactDocument[]) => {
      if (!canMutatePageDocument) {
        return;
      }
      const additional = artifacts.slice(1);
      if (additional.length === 0) {
        return;
      }

      additional.forEach((artifact, index) => {
        const importedDocument = getBuilderDocument(artifact);
        const newNodeId = `page-designer-${artifact.artifactId}-${Date.now()}-${index}`;
        const nextX = (positionAbsoluteX ?? 0) + 420;
        const nextY = (positionAbsoluteY ?? 0) + (index + 1) * 170;

        const newNode: Node<Record<string, unknown>> = {
          id: newNodeId,
          type: 'page-designer',
          position: { x: nextX, y: nextY },
          data: {
            label: importedDocument?.name ?? `Imported ${index + 2}`,
            pageDocument: appendOperation(
              artifact,
              'import-page',
              'succeeded',
              'Created an additional page designer node from imported multi-page artifact.',
              { sourceIndex: index + 2 },
            ),
            expanded: false,
            validationSummary: artifact.validationSummary,
          },
        };

        executeCommand(new AddNodeCommand(newNode));
      });
    },
    [appendOperation, canMutatePageDocument, executeCommand, positionAbsoluteX, positionAbsoluteY],
  );

  const handleAIChangeRecord = useCallback(
    (record: PageArtifactAIChangeRecord) => {
      const basePageDocument = latestPageDocumentRef.current ?? data.pageDocument;
      if (!basePageDocument || !canMutatePageDocument) {
        return;
      }
      if (data.onDataChange && record.artifactId !== basePageDocument.artifactId) {
        return;
      }

      const updatedPageDocument = appendOperation(
        appendAIChangeRecord(basePageDocument, record),
        'governance-record',
        record.lineage.reviewState === 'pending' ? 'requires-review' : 'succeeded',
        'Recorded page builder governance lineage for an assisted document change.',
        {
          confidence: record.lineage.confidence,
          affectedNodeCount: record.lineage.affectedNodeIds.length,
        },
      );
      updateNodeData(
        { pageDocument: basePageDocument },
        { pageDocument: updatedPageDocument },
        'Record governance event',
      );
    },
    [appendOperation, canMutatePageDocument, data.pageDocument, updateNodeData],
  );

  const handleAIReviewDecision = useCallback(
    (actionId: string, decision: 'accepted' | 'rejected') => {
      if (!data.pageDocument || !canMutatePageDocument) {
        return;
      }

      const updatedPageDocument = appendOperation(
        {
          ...updateAIChangeRecordReviewState(data.pageDocument, actionId, decision),
          syncStatus: 'dirty',
        },
        'governance-record',
        decision === 'accepted' ? 'succeeded' : 'requires-review',
        `Recorded ${decision} review decision for automation change.`,
        {
          actionId,
          decision,
        },
      );

      updateNodeData(
        { pageDocument: data.pageDocument },
        { pageDocument: updatedPageDocument },
        'Record automation review decision',
      );
    },
    [appendOperation, canMutatePageDocument, data.pageDocument, updateNodeData],
  );

  const loadRemoteConflictDocument = useCallback(async (): Promise<PageArtifactDocument | null> => {
    if (!data.pageDocument || !canMutatePageDocument) {
      return null;
    }

    const loaded = await pagePersistenceAdapter.load(data.pageDocument.artifactId);
    if (!loaded) {
      return null;
    }

    setRemoteConflictDocument(loaded);
    setConflictMergeChoices(
      Object.fromEntries(
        buildConflictMergeRows(data.pageDocument, loaded).map((row) => [
          row.nodeId,
          row.status === 'remote-only' ? 'remote' : 'local',
        ]),
      ),
    );
    return loaded;
  }, [canMutatePageDocument, data.pageDocument, pagePersistenceAdapter]);

  const handleReloadFromServer = useCallback(async () => {
    if (!data.pageDocument || !canMutatePageDocument) {
      return;
    }

    const loaded = remoteConflictDocument ?? (await loadRemoteConflictDocument());
    if (!loaded) {
      return;
    }

    updateNodeData(
      { pageDocument: data.pageDocument },
      {
        pageDocument: appendOperation(
          {
            ...loaded,
            syncStatus: 'synced',
          },
          'reload-remote',
          'succeeded',
          'Reloaded the remote page document to resolve local conflict state.',
          {
            discardedLocalDocumentId: data.pageDocument.documentId,
            remoteDocumentId: loaded.documentId,
            localNodeCount: getSerializedNodeCount(data.pageDocument),
            remoteNodeCount: getSerializedNodeCount(loaded),
          },
        ),
      },
      'Reload page document from server',
    );
    setRemoteConflictDocument(null);
  }, [appendOperation, canMutatePageDocument, data.pageDocument, loadRemoteConflictDocument, remoteConflictDocument, updateNodeData]);

  const handleOverwriteRemote = useCallback(async () => {
    if (!data.pageDocument || !canMutatePageDocument) {
      return;
    }

    const auditReason = overwriteReason.trim();
    if (auditReason.length < 8) {
      return;
    }

    const forceDocument: PageArtifactDocument = {
      ...data.pageDocument,
      documentId: `${data.pageDocument.documentId}-force-${Date.now()}`,
      syncStatus: 'dirty',
    };

    await pagePersistenceAdapter.save(forceDocument);
    updateNodeData(
      { pageDocument: data.pageDocument },
      {
        pageDocument: appendOperation(
          {
            ...forceDocument,
            syncStatus: 'synced',
          },
          'overwrite-remote',
          'requires-review',
          'Overwrote remote page document after an explicit conflict action.',
          {
            auditReason,
            localDocumentId: data.pageDocument.documentId,
            remoteDocumentId: remoteConflictDocument?.documentId ?? null,
            localNodeCount: getSerializedNodeCount(data.pageDocument),
            remoteNodeCount: remoteConflictDocument ? getSerializedNodeCount(remoteConflictDocument) : null,
          },
        ),
      },
      'Overwrite remote page document with audit reason',
    );
    setOverwriteReason('');
    setRemoteConflictDocument(null);
    setConflictMergeChoices({});
  }, [appendOperation, canMutatePageDocument, data.pageDocument, overwriteReason, pagePersistenceAdapter, remoteConflictDocument, updateNodeData]);

  const handleMergeConflictDocument = useCallback(async () => {
    if (!data.pageDocument || !remoteConflictDocument || !canMutatePageDocument) {
      return;
    }
    const localDocument = data.pageDocument;
    const remoteDocument = remoteConflictDocument;

    const auditReason = overwriteReason.trim();
    if (auditReason.length < 8) {
      return;
    }

    const localNodes = getSerializedNodes(localDocument);
    const remoteNodes = getSerializedNodes(remoteDocument);
    const mergedNodes: Record<string, unknown> = { ...localNodes };
    const selectedRemoteNodeIds: string[] = [];
    for (const row of conflictMergeRows) {
      const choice = conflictMergeChoices[row.nodeId] ?? (row.status === 'remote-only' ? 'remote' : 'local');
      if (choice !== 'remote') {
        continue;
      }

      selectedRemoteNodeIds.push(row.nodeId);
      if (remoteNodes[row.nodeId]) {
        mergedNodes[row.nodeId] = remoteNodes[row.nodeId];
      } else {
        delete mergedNodes[row.nodeId];
      }
    }

    const mergedRootNodes = ([
      ...getSerializedRootNodes(localDocument),
      ...getSerializedRootNodes(remoteDocument).filter((nodeId) => !getSerializedRootNodes(localDocument).includes(nodeId)),
    ].filter((nodeId, index, allNodeIds) => mergedNodes[nodeId] && allNodeIds.indexOf(nodeId) === index)) as unknown as PageArtifactDocument['serializedBuilderDocument']['rootNodes'];
    const mergedDocument: PageArtifactDocument = {
      ...localDocument,
      documentId: `${localDocument.documentId}-merge-${Date.now()}`,
      serializedBuilderDocument: {
        ...localDocument.serializedBuilderDocument,
        rootNodes: mergedRootNodes,
        nodes: mergedNodes as PageArtifactDocument['serializedBuilderDocument']['nodes'],
      },
      syncStatus: 'dirty',
    };

    await pagePersistenceAdapter.save(mergedDocument);
    updateNodeData(
      { pageDocument: localDocument },
      {
        pageDocument: appendOperation(
          {
            ...mergedDocument,
            syncStatus: 'synced',
          },
          'merge-conflict',
          'requires-review',
          'Merged selected remote page document nodes after conflict review.',
          {
            auditReason,
            localDocumentId: localDocument.documentId,
            remoteDocumentId: remoteDocument.documentId,
            selectedRemoteNodeCount: selectedRemoteNodeIds.length,
            localNodeCount: getSerializedNodeCount(localDocument),
            remoteNodeCount: getSerializedNodeCount(remoteDocument),
          },
        ),
      },
      'Merge remote page document nodes with audit reason',
    );
    setOverwriteReason('');
    setRemoteConflictDocument(null);
    setConflictMergeChoices({});
  }, [
    appendOperation,
    canMutatePageDocument,
    conflictMergeChoices,
    conflictMergeRows,
    data.pageDocument,
    overwriteReason,
    pagePersistenceAdapter,
    remoteConflictDocument,
    updateNodeData,
  ]);

  const borderColor = selected ? 'var(--info-color, #6366f1)' : 'var(--color-border, #d1d5db)';

  return (
    <>
      {/* Allow resize in expanded mode */}
      {isExpanded && (
        <NodeResizer
          minWidth={560}
          minHeight={400}
          isVisible={selected}
          color={borderColor}
        />
      )}

      {/* Node frame */}
      <Box
        className="flex flex-col h-full overflow-hidden rounded-xl"
        style={{
          width: isExpanded ? '100%' : COLLAPSED_WIDTH,
          height: isExpanded ? '100%' : COLLAPSED_HEIGHT,
          minWidth: isExpanded ? 560 : COLLAPSED_WIDTH,
          minHeight: isExpanded ? 400 : COLLAPSED_HEIGHT,
          border: `2px solid ${borderColor}`,
          backgroundColor: 'var(--color-surface, #ffffff)',
          boxShadow: selected ? '0 0 0 2px rgba(99,102,241,0.3)' : '0 1px 4px rgba(0,0,0,0.08)',
          transition: 'border-color 0.15s, box-shadow 0.15s',
        }}
        onDoubleClick={!isExpanded ? handleToggleExpand : undefined}
      >
        {/* Header bar */}
        <Box
          className="flex items-center gap-2 px-3 py-2 border-b select-none"
          style={{
            borderColor: 'var(--color-border, #e5e7eb)',
            backgroundColor: 'var(--color-surface-raised, #f9fafb)',
            cursor: isExpanded ? 'default' : 'grab',
            flexShrink: 0,
          }}
        >
          <PageIcon size={14} color="var(--color-text-secondary, #6b7280)" />

          <Typography
            variant="body2"
            className="flex-1 font-medium truncate"
            style={{ color: 'var(--text-primary, #111827)' }}
          >
            {data.label ?? 'Page'}
          </Typography>

          {/* Sync status badge */}
          {data.pageDocument?.syncStatus === 'dirty' && (
            <Typography
              variant="caption"
              style={{ color: '#f59e0b', flexShrink: 0 }}
              title="Unsaved changes"
              data-testid="page-node-sync-status"
            >
              ●
            </Typography>
          )}
          {data.pageDocument?.syncStatus === 'offline' && (
            <Typography
              variant="caption"
              style={{ color: '#6b7280', flexShrink: 0 }}
              title="Saved locally — not synced to server"
              data-testid="page-node-sync-status"
            >
              ⚡
            </Typography>
          )}
          {data.pageDocument?.syncStatus === 'error' && (
            <Typography
              variant="caption"
              style={{ color: '#ef4444', flexShrink: 0 }}
              title="Conflict — remote version is newer. Reload to resolve."
              data-testid="page-node-sync-status"
            >
              ⚠ conflict
            </Typography>
          )}

          {(data.pageDocument?.aiChangeRecords?.length ?? 0) > 0 ? (
            <Typography
              variant="caption"
              style={{ color: '#2563eb', flexShrink: 0 }}
              title={`${data.pageDocument?.aiChangeRecords?.length ?? 0} governance record(s)`}
              data-testid="page-node-governance-trace-count"
            >
              Governance {data.pageDocument?.aiChangeRecords?.length}
            </Typography>
          ) : null}

          {!isExpanded && nodeCount > 0 ? (
            <Typography
              variant="caption"
              style={{ color: 'var(--color-text-secondary, #6b7280)', flexShrink: 0 }}
            >
              {nodeCount} component{nodeCount !== 1 ? 's' : ''}
            </Typography>
          ) : null}

          <IconButton
            size="small"
            onClick={handleToggleExpand}
            aria-label={isExpanded ? 'Collapse page designer' : 'Expand page designer'}
            title={isExpanded ? 'Collapse' : 'Expand (or double-click)'}
          >
            {isExpanded ? <CollapseIcon size={14} /> : <ExpandIcon size={14} />}
          </IconButton>
        </Box>

        {/* Body */}
        {isExpanded ? (
          /* Full PageDesigner embedded directly in the canvas node */
          <Box className="flex h-full flex-1 overflow-hidden" style={{ contain: 'strict' }}>
            <Box className="min-w-0 flex-1 overflow-hidden">
              {data.pageDocument?.syncStatus === 'error' ? (
                <Box className="m-3 rounded-lg border border-destructive-border bg-destructive-bg p-3">
                    <Typography variant="caption" style={{ display: 'block', marginBottom: 8 }}>
                      Save conflict detected. The remote version is newer. Compare the versions before reloading the remote document or overwriting it with an audit reason.
                    </Typography>
                    <Box className="mb-2 rounded border border-destructive-border bg-surface p-2 text-xs">
                      <Typography variant="caption" style={{ display: 'block' }}>
                        Local: {data.pageDocument.documentId} · {getSerializedNodeCount(data.pageDocument)} component{getSerializedNodeCount(data.pageDocument) === 1 ? '' : 's'}
                      </Typography>
                      {remoteConflictDocument ? (
                        <Typography
                          variant="caption"
                          style={{ display: 'block' }}
                          data-testid="page-conflict-remote-summary"
                        >
                          Remote: {remoteConflictDocument.documentId} · {getSerializedNodeCount(remoteConflictDocument)} component{getSerializedNodeCount(remoteConflictDocument) === 1 ? '' : 's'}
                        </Typography>
                      ) : (
                        <Typography variant="caption" style={{ display: 'block' }}>
                          Remote: not loaded yet
                        </Typography>
                      )}
	                    </Box>
	                    {remoteConflictDocument ? (
	                      <Box
	                        className="mb-2 rounded border border-destructive-border bg-surface p-2 text-xs"
	                        data-testid="page-conflict-node-merge"
	                      >
	                        <Typography variant="caption" style={{ display: 'block', fontWeight: 600, marginBottom: 6 }}>
	                          Node-level merge choices
	                        </Typography>
	                        {conflictMergeRows.map((row) => (
	                          <Box
	                            key={row.nodeId}
	                            className="mb-1 grid grid-cols-[1fr_auto_auto] items-center gap-2"
	                            data-testid={`page-conflict-node-row-${row.nodeId}`}
	                          >
	                            <Typography variant="caption">
	                              {row.nodeId} · {row.status} · local {row.localLabel} / remote {row.remoteLabel}
	                            </Typography>
	                            <label>
	                              <Input
	                                type="radio"
	                                name={`page-conflict-choice-${row.nodeId}`}
	                                checked={(conflictMergeChoices[row.nodeId] ?? (row.status === 'remote-only' ? 'remote' : 'local')) === 'local'}
	                                onChange={() =>
	                                  setConflictMergeChoices((current) => ({
	                                    ...current,
	                                    [row.nodeId]: 'local',
	                                  }))
	                                }
	                              />
	                              Local
	                            </label>
	                            <label>
	                              <Input
	                                type="radio"
	                                name={`page-conflict-choice-${row.nodeId}`}
	                                checked={(conflictMergeChoices[row.nodeId] ?? (row.status === 'remote-only' ? 'remote' : 'local')) === 'remote'}
	                                onChange={() =>
	                                  setConflictMergeChoices((current) => ({
	                                    ...current,
	                                    [row.nodeId]: 'remote',
	                                  }))
	                                }
	                                data-testid={`page-conflict-node-use-remote-${row.nodeId}`}
	                              />
	                              Remote
	                            </label>
	                          </Box>
	                        ))}
	                      </Box>
	                    ) : null}
	                    <TextArea
	                      value={overwriteReason}
	                      onChange={(event) => setOverwriteReason(event.target.value)}
                      disabled={!canMutatePageDocument}
                      rows={2}
                      aria-label="Overwrite audit reason"
                      placeholder="Why is overwriting the remote version safe?"
                      data-testid="page-conflict-overwrite-reason"
                      className="mb-2 w-full text-xs"
                    />
                    <Box className="flex gap-2">
                      <Button
                        variant="outline"
                        size="small"
                        onClick={() => void loadRemoteConflictDocument()}
                        disabled={!canMutatePageDocument}
                        data-testid="page-conflict-compare-remote"
                      >
                        Compare remote
                      </Button>
                      <Button
                        variant="outline"
                        size="small"
                        onClick={() => void handleReloadFromServer()}
                        data-testid="page-conflict-reload-remote"
                      >
                        Reload remote
                      </Button>
	                      <Button
	                        variant="solid"
	                        size="small"
	                        onClick={() => void handleMergeConflictDocument()}
	                        disabled={!remoteConflictDocument || overwriteReason.trim().length < 8 || !canMutatePageDocument}
	                        data-testid="page-conflict-merge-selection"
	                      >
	                        Merge selection
	                      </Button>
	                      <Button
	                        variant="solid"
	                        size="small"
	                        onClick={() => void handleOverwriteRemote()}
	                        disabled={overwriteReason.trim().length < 8 || !canMutatePageDocument}
	                        data-testid="page-conflict-overwrite-remote"
                      >
                        Overwrite with reason
                      </Button>
                    </Box>
                </Box>
              ) : null}
              <PageDesigner
                initialComponents={builderDocument}
                projectId={projectId}
                externalSelectedNodeId={previewSelectedNodeId}
                externalHoveredNodeId={previewHoveredNodeId}
                onImportArtifacts={handleImportArtifacts}
                onAIChangeRecord={handleAIChangeRecord}
                onAIReviewDecision={handleAIReviewDecision}
                onSelectionChange={setPreviewSelectedNodeId}
                onHoverChange={setPreviewHoveredNodeId}
                phaseConfig={effectivePhaseConfig}
                readOnlyReason={isPolicyReadOnly ? pageBuilderReadOnlyReason : undefined}
                auditContext={
                  currentUser?.id && currentUser.tenantId && currentWorkspaceId && projectId
                    ? {
                        userId: currentUser.id,
                        tenantId: currentUser.tenantId,
                        workspaceId: currentWorkspaceId,
                        projectId,
                        artifactId: data.pageDocument?.artifactId,
                        phase: currentPhase ?? undefined,
                      }
                    : undefined
                }
                onDocumentChange={handlePageDesignerDocumentChange}
              />
            </Box>
            <Box className="w-[360px] border-l border-border">
              <LivePreviewPanel
                document={builderDocument}
                previewContext={
                  projectId && data.pageDocument
                    ? { projectId, artifactId: data.pageDocument.artifactId }
                    : undefined
                }
                validation={data.validationSummary}
                selectedNodeId={previewSelectedNodeId}
                onElementClick={setPreviewSelectedNodeId}
                onElementHover={setPreviewHoveredNodeId}
              />
            </Box>
          </Box>
        ) : (
          /* Collapsed placeholder */
          <Box
            className="flex flex-col items-center justify-center flex-1 gap-2 px-4"
            style={{ color: 'var(--color-text-secondary, #6b7280)' }}
          >
            {nodeCount > 0 ? (
              <Typography variant="caption" className="text-center">
                {nodeCount} component{nodeCount !== 1 ? 's' : ''} — double-click to edit
              </Typography>
            ) : (
              <>
                <Typography variant="caption" className="text-center">
                  Empty page — double-click to start designing
                </Typography>
                <Button
                  variant="outlined"
                  size="small"
                  onClick={handleToggleExpand}
                  aria-label="Open page designer"
                >
                  Design
                </Button>
              </>
            )}
          </Box>
        )}
      </Box>

      {/* ReactFlow handles — only on edges so they don't overlap the editor */}
      <Handle
        id="top"
        type="target"
        position={Position.Top}
        style={{ background: 'var(--info-color, #6366f1)', width: 10, height: 10 }}
      />
      <Handle
        id="right"
        type="source"
        position={Position.Right}
        style={{ background: 'var(--info-color, #6366f1)', width: 10, height: 10 }}
      />
      <Handle
        id="bottom"
        type="source"
        position={Position.Bottom}
        style={{ background: 'var(--info-color, #6366f1)', width: 10, height: 10 }}
      />
      <Handle
        id="left"
        type="target"
        position={Position.Left}
        style={{ background: 'var(--info-color, #6366f1)', width: 10, height: 10 }}
      />
    </>
  );
};

/**
 * PageDesignerNode wrapped in React.memo with a data-aware comparator.
 * Only re-renders when the node's own data, selection, or position changes.
 */
export const PageDesignerNode = memo(PageDesignerNodeInner, (prev, next) => {
  return (
    prev.selected === next.selected &&
    prev.data.label === next.data.label &&
    prev.data.expanded === next.data.expanded &&
    prev.data.pageDocument === next.data.pageDocument &&
    prev.data.validationSummary === next.data.validationSummary &&
    prev.data.readOnly === next.data.readOnly &&
    prev.data.readOnlyReason === next.data.readOnlyReason
  );
});

export default PageDesignerNode;

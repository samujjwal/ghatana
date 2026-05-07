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

import React, { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Handle, Position, NodeResizer, type Node, type NodeProps } from '@xyflow/react';
import { Box, Button, IconButton, TextArea, Typography } from '@ghatana/design-system';
import { Maximize2 as ExpandIcon, Minimize2 as CollapseIcon, Layout as PageIcon } from 'lucide-react';
import { useAtomValue, useSetAtom } from 'jotai';
import { useParams } from 'react-router';
import { usePhaseContext } from '@/context';
import { getPhaseCanvasConfig } from '@/services/canvas/phase-config/PhaseCanvasConfig';
import { currentWorkspaceIdAtom } from '@/state/atoms/workspaceAtom';
import { currentUserAtom } from '@/stores/user.store';

import { PageDesigner } from '@/components/canvas/page/PageDesigner';
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
}

export type PageDesignerCanvasNode = Node<PageDesignerNodeData, 'page-designer'>;

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const COLLAPSED_WIDTH = 220;
const COLLAPSED_HEIGHT = 90;
const EXPANDED_WIDTH = 880;
const EXPANDED_HEIGHT = 640;

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
  const { currentPhase } = usePhaseContext();
  const phaseConfig = useMemo(
    () => (currentPhase ? getPhaseCanvasConfig(currentPhase) : undefined),
    [currentPhase],
  );
  const builderDocument = useMemo(() => getBuilderDocument(data.pageDocument), [data.pageDocument]);
  const latestPageDocumentRef = useRef<PageArtifactDocument | undefined>(data.pageDocument);
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
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

  useEffect(() => {
    latestPageDocumentRef.current = data.pageDocument;
  }, [data.pageDocument]);

  useEffect(() => {
    return () => {
      if (saveTimerRef.current) {
        clearTimeout(saveTimerRef.current);
        saveTimerRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    if (!data.pageDocument || data.pageDocument.syncStatus !== 'dirty') {
      return;
    }

    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
      saveTimerRef.current = null;
    }

    saveTimerRef.current = setTimeout(() => {
      const pending = latestPageDocumentRef.current;
      if (!pending || pending.syncStatus !== 'dirty') {
        return;
      }

      void pagePersistenceAdapter
        .save(pending)
        .then(() => {
          const latest = latestPageDocumentRef.current;
          if (!latest || latest.syncStatus !== 'dirty') {
            return;
          }

          executeCommand(
            new UpdateNodeDataCommand(
              id,
              { pageDocument: latest } as Record<string, unknown>,
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
              } as Record<string, unknown>,
              'Persist page document',
            ),
          );
        })
        .catch((err: unknown) => {
          const latest = latestPageDocumentRef.current;
          if (!latest || latest.syncStatus !== 'dirty') {
            return;
          }

          // For conflict errors, mark as error so the user sees it and can reload.
          const conflict = isConflictError(err);
          const nextStatus = conflict ? 'error' : 'offline';
          const errorMessage = err instanceof Error ? err.message : 'Unknown persistence error';

          executeCommand(
            new UpdateNodeDataCommand(
              id,
              { pageDocument: latest } as Record<string, unknown>,
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
              } as Record<string, unknown>,
              conflict
                ? 'Persist page document (conflict — remote version newer)'
                : 'Persist page document (offline fallback)',
            ),
          );
        });
    }, 700);

    return () => {
      if (saveTimerRef.current) {
        clearTimeout(saveTimerRef.current);
        saveTimerRef.current = null;
      }
    };
  }, [appendOperation, data.pageDocument, executeCommand, id, pagePersistenceAdapter]);

  const handleToggleExpand = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      const next = !isExpanded;
      setIsExpanded(next);
      executeCommand(
        new UpdateNodeDataCommand(
          id,
          { expanded: data.expanded } as Record<string, unknown>,
          { expanded: next } as Record<string, unknown>,
          next ? 'Expand page designer' : 'Collapse page designer',
        ),
      );
    },
    [data.expanded, executeCommand, id, isExpanded],
  );

  const handleDocumentChange = useCallback(
    (pageDocument: PageArtifactDocument, _document: BuilderDocument, validation: ValidationResult) => {
      executeCommand(
        new UpdateNodeDataCommand(
          id,
          {
            pageDocument: data.pageDocument,
            validationSummary: data.validationSummary,
          } as Record<string, unknown>,
          {
              pageDocument: appendOperation(
                pageDocument,
                'document-update',
                validation.valid ? 'pending' : 'requires-review',
                'Updated page builder document from in-canvas editor.',
                {
                  valid: validation.valid,
                  errorCount: validation.errors.length,
                  warningCount: validation.warnings.length,
                },
              ),
            validationSummary: {
              valid: validation.valid,
              errorCount: validation.errors.length,
              warningCount: validation.warnings.length,
            },
          } as Record<string, unknown>,
          'Update page document',
        ),
      );
    },
    [appendOperation, data.pageDocument, data.validationSummary, executeCommand, id],
  );

  const handleImportArtifacts = useCallback(
    (artifacts: readonly PageArtifactDocument[]) => {
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
    [executeCommand, positionAbsoluteX, positionAbsoluteY],
  );

  const handleAIChangeRecord = useCallback(
    (record: PageArtifactAIChangeRecord) => {
      if (!data.pageDocument) {
        return;
      }

      const updatedPageDocument = appendOperation(
        appendAIChangeRecord(data.pageDocument, record),
        'governance-record',
        record.lineage.reviewState === 'pending' ? 'requires-review' : 'succeeded',
        'Recorded page builder governance lineage for an assisted document change.',
        {
          confidence: record.lineage.confidence,
          affectedNodeCount: record.lineage.affectedNodeIds.length,
        },
      );
      executeCommand(
        new UpdateNodeDataCommand(
          id,
          { pageDocument: data.pageDocument } as Record<string, unknown>,
          { pageDocument: updatedPageDocument } as Record<string, unknown>,
          'Record governance event',
        ),
      );
    },
    [appendOperation, data.pageDocument, executeCommand, id],
  );

  const handleAIReviewDecision = useCallback(
    (actionId: string, decision: 'accepted' | 'rejected') => {
      if (!data.pageDocument) {
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

      executeCommand(
        new UpdateNodeDataCommand(
          id,
          { pageDocument: data.pageDocument } as Record<string, unknown>,
          { pageDocument: updatedPageDocument } as Record<string, unknown>,
          'Record automation review decision',
        ),
      );
    },
    [appendOperation, data.pageDocument, executeCommand, id],
  );

  const handleReloadFromServer = useCallback(async () => {
    if (!data.pageDocument) {
      return;
    }

    const loaded = await pagePersistenceAdapter.load(data.pageDocument.artifactId);
    if (!loaded) {
      return;
    }

    executeCommand(
      new UpdateNodeDataCommand(
        id,
        { pageDocument: data.pageDocument } as Record<string, unknown>,
        {
            pageDocument: appendOperation(
              {
                ...loaded,
                syncStatus: 'synced',
              },
              'reload-remote',
              'succeeded',
              'Reloaded the remote page document to resolve local conflict state.',
            ),
        } as Record<string, unknown>,
        'Reload page document from server',
      ),
    );
  }, [appendOperation, data.pageDocument, executeCommand, id, pagePersistenceAdapter]);

  const handleOverwriteRemote = useCallback(async () => {
    if (!data.pageDocument) {
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
    executeCommand(
      new UpdateNodeDataCommand(
        id,
        { pageDocument: data.pageDocument } as Record<string, unknown>,
        {
          pageDocument: appendOperation(
            {
              ...forceDocument,
              syncStatus: 'synced',
            },
            'overwrite-remote',
            'requires-review',
            'Overwrote remote page document after an explicit conflict action.',
            { auditReason },
          ),
        } as Record<string, unknown>,
        'Overwrite remote page document with audit reason',
      ),
    );
    setOverwriteReason('');
  }, [appendOperation, data.pageDocument, executeCommand, id, overwriteReason, pagePersistenceAdapter]);

  const borderColor = selected ? 'var(--color-primary-500, #6366f1)' : 'var(--color-border, #d1d5db)';

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
            style={{ color: 'var(--color-text-primary, #111827)' }}
          >
            {data.label ?? 'Page'}
          </Typography>

          {/* Sync status badge */}
          {data.pageDocument?.syncStatus === 'dirty' && (
            <Typography variant="caption" style={{ color: '#f59e0b', flexShrink: 0 }} title="Unsaved changes">
              ●
            </Typography>
          )}
          {data.pageDocument?.syncStatus === 'offline' && (
            <Typography variant="caption" style={{ color: '#6b7280', flexShrink: 0 }} title="Saved locally — not synced to server">
              ⚡
            </Typography>
          )}
          {data.pageDocument?.syncStatus === 'error' && (
            <Typography variant="caption" style={{ color: '#ef4444', flexShrink: 0 }} title="Conflict — remote version is newer. Reload to resolve.">
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
                      Save conflict detected. The remote version is newer. Reload the remote version, or provide an audit reason before overwriting it.
                    </Typography>
                    <TextArea
                      value={overwriteReason}
                      onChange={(event) => setOverwriteReason(event.target.value)}
                      rows={2}
                      aria-label="Overwrite audit reason"
                      placeholder="Why is overwriting the remote version safe?"
                      data-testid="page-conflict-overwrite-reason"
                      className="mb-2 w-full text-xs"
                    />
                    <Box className="flex gap-2">
                      <Button variant="outline" size="small" onClick={() => void handleReloadFromServer()}>
                        Reload remote
                      </Button>
                      <Button
                        variant="solid"
                        size="small"
                        onClick={() => void handleOverwriteRemote()}
                        disabled={overwriteReason.trim().length < 8}
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
                phaseConfig={phaseConfig}
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
                onDocumentChange={(document, validation) => {
                  if (!data.pageDocument) {
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
                  handleDocumentChange(pageDocument, document, validation);
                }}
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
        style={{ background: 'var(--color-primary-500, #6366f1)', width: 10, height: 10 }}
      />
      <Handle
        id="right"
        type="source"
        position={Position.Right}
        style={{ background: 'var(--color-primary-500, #6366f1)', width: 10, height: 10 }}
      />
      <Handle
        id="bottom"
        type="source"
        position={Position.Bottom}
        style={{ background: 'var(--color-primary-500, #6366f1)', width: 10, height: 10 }}
      />
      <Handle
        id="left"
        type="target"
        position={Position.Left}
        style={{ background: 'var(--color-primary-500, #6366f1)', width: 10, height: 10 }}
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
    prev.data.validationSummary === next.data.validationSummary
  );
});

export default PageDesignerNode;

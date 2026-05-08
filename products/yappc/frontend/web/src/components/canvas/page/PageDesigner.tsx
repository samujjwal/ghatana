import { Input } from '../../ui/Input';
import { Select } from '../../ui/Select';
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
  getBuilderPaletteCategories,
  getFilteredBuilderPalette,
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
import type { PageArtifactAIChangeRecord, PageArtifactGraphSnapshot, PageArtifactOperationKind } from './pageArtifactDocument';
import { importSourceToPageArtifacts, type ImportSourceType } from '../../../services/compiler/ImportSourceWorkflow';
import {
  checkArtifactCompilerRuntimeHealth,
  type ArtifactCompilerRuntimeHealth,
} from '../../../services/compiler/ArtifactCompilerRuntimeHealth';
import { PageBuilderCommands, type Command, type CommandResult, type InsertComponentCommand, type MoveComponentCommand } from '../../../services/canvas/commands/PageBuilderCommands';
import { persistResidualIslandReview } from '../../../services/canvas/commands/ResidualIslandReviewService';
import { persistImportReviewDecision } from '../../../services/canvas/commands/ImportReviewDecisionService';
import {
  promoteResidualIslandToRegistryCandidate,
  type RegistryCandidatePromotionResponse,
} from '../../../services/canvas/commands/RegistryCandidatePromotionService';
import {
  buildArtifactGraphMergeReviewRequest,
  runArtifactGraphMergeReview,
  type ArtifactGraphMergeReviewResult,
} from '../../../services/canvas/commands/ArtifactGraphMergeReviewService';
import {
  emitPageBuilderCommandAudit,
  emitPageBuilderCommandTelemetry,
  type PageBuilderCommandAuditContext,
} from '../../../services/canvas/commands/PageBuilderCommandAuditService';
import type { PhaseCanvasConfig } from '../../../services/canvas/phase-config/PhaseCanvasConfig';

import type { ComponentData } from './schemas';
import {
  validateDocument,
  deserializeDocument,
  serializeDocument,
} from '@ghatana/ui-builder';
import type { Binding, BuilderDocument, ComponentInstance, LossPoint, NodeId, ResponsiveVariant, StateVariant, ValidationResult } from '@ghatana/ui-builder';

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

type ImportWorkflowMode = 'semantic-model' | 'source';
type ImportReviewDecision = 'applied' | 'skipped' | 'promoted';

interface ImportWizardTemplate {
  readonly id: 'paste-code' | 'upload-zip' | 'connect-repo' | 'import-storybook' | 'import-route';
  readonly label: string;
  readonly description: string;
  readonly mode: ImportWorkflowMode;
  readonly sourceType?: ImportSourceType;
  readonly placeholder: string;
}

const IMPORT_WIZARD_TEMPLATES = [
  {
    id: 'paste-code',
    label: 'Paste code',
    description: 'Paste a reviewed semantic page model when you already have generated JSON.',
    mode: 'semantic-model',
    placeholder: '{"pages": [{"name": "Home", "confidence": 0.92}]}',
  },
  {
    id: 'upload-zip',
    label: 'Upload zip',
    description: 'Point to an HTTPS zip archive for governed server-side extraction.',
    mode: 'source',
    sourceType: 'zip',
    placeholder: 'https://example.com/artifacts/app-pages.zip',
  },
  {
    id: 'connect-repo',
    label: 'Connect repo',
    description: 'Use an HTTPS repository path that resolves to the route or component source.',
    mode: 'source',
    sourceType: 'route',
    placeholder: 'https://github.com/org/repo/tree/main/apps/web/src/routes',
  },
  {
    id: 'import-storybook',
    label: 'Import Storybook',
    description: 'Import a CSF story URL or artifact reference through the compiler runtime.',
    mode: 'source',
    sourceType: 'storybook',
    placeholder: 'https://example.com/Button.stories.tsx#Primary',
  },
  {
    id: 'import-route',
    label: 'Import route',
    description: 'Import a route file and decompile the page structure into a builder document.',
    mode: 'source',
    sourceType: 'route',
    placeholder: 'https://example.com/routes/Home.tsx',
  },
] as const satisfies readonly ImportWizardTemplate[];

interface ImportReviewQueueItem {
  readonly id: string;
  readonly kind: 'loss-point' | 'residual-island';
  readonly label: string;
  readonly details: string;
  readonly sourceEvidence: string;
  readonly governedEvidence: string;
  readonly reviewImpact: string;
}

interface RegistryCandidateSummary {
  readonly candidateId: string;
  readonly artifactId: string;
  readonly residualIslandId: string;
  readonly proposedContractName: string;
  readonly status: RegistryCandidatePromotionResponse['status'];
  readonly auditRecordId: string;
  readonly createdAt: string;
}

type ArtifactGraphMergeReviewStatus = 'required' | 'running' | 'passed' | 'conflicts' | 'failed';

interface ArtifactGraphMergeReviewState {
  readonly artifactId: string;
  readonly graph: PageArtifactGraphSnapshot;
  readonly status: ArtifactGraphMergeReviewStatus;
  readonly attemptedAt?: string;
  readonly result?: ArtifactGraphMergeReviewResult;
  readonly error?: string;
}

function buildImportReviewQueue(
  lossPoints: readonly LossPoint[] | undefined,
  residualIslandIds: readonly string[],
): readonly ImportReviewQueueItem[] {
  const lossPointItems = (lossPoints ?? []).map((lossPoint, index) => ({
    id: `loss-${index}-${lossPoint.type}-${lossPoint.location ?? 'unknown'}`,
    kind: 'loss-point' as const,
    label: `${lossPoint.type}${lossPoint.location ? ` at ${lossPoint.location}` : ''}`,
    details: lossPoint.description,
    sourceEvidence: lossPoint.location
      ? `Source path: ${lossPoint.location}`
      : 'Source path unavailable from decompiler metadata.',
    governedEvidence: `Builder impact: ${lossPoint.type} requires an explicit apply or skip decision before trust promotion.`,
    reviewImpact: lossPoint.description,
  }));
  const residualItems = residualIslandIds.map((residualIslandId) => ({
    id: `residual-${residualIslandId}`,
    kind: 'residual-island' as const,
    label: residualIslandId,
    details: 'Residual island requires an accept or reject decision before handoff.',
    sourceEvidence: `Residual source island: ${residualIslandId}`,
    governedEvidence: 'Builder impact: no reviewed registry contract was available for this imported element.',
    reviewImpact: 'Accept, reject, or promote the residual island so graph handoff does not hide unsupported structure.',
  }));

  return [...lossPointItems, ...residualItems];
}

function buildRegistryCandidateContractName(residualIslandId: string): string {
  const segments = residualIslandId
    .split(/[^a-zA-Z0-9]+/)
    .map((segment) => segment.trim())
    .filter((segment) => segment.length > 0);
  const baseName = segments.length > 0
    ? segments.map((segment) => `${segment.charAt(0).toUpperCase()}${segment.slice(1)}`).join('')
    : 'ResidualIsland';

  return `${baseName}Candidate`;
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
  readonly onDocumentChange?: (
    document: BuilderDocument,
    validation: ValidationResult,
    context?: PageDesignerDocumentChangeContext,
  ) => void;
  /** Called when the designer imports a decompiled model, producing one or more page artifacts */
  readonly onImportArtifacts?: (artifacts: readonly import('./pageArtifactDocument').PageArtifactDocument[]) => void;
  /** Called when an AI change is applied to the page (for audit/governance recording) */
  readonly onAIChangeRecord?: (record: PageArtifactAIChangeRecord) => void;
  /** Called when a pending AI/automation change is accepted or rejected. */
  readonly onAIReviewDecision?: (
    actionId: string,
    decision: 'accepted' | 'rejected',
    rollbackMetadata?: PageArtifactAIChangeRecord['rollbackMetadata'],
  ) => void;
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
  /** Human-readable lock reason when the builder is in a read-only policy state. */
  readonly readOnlyReason?: string;
  /** Scope used to persist immutable command audit records when available. */
  readonly auditContext?: PageBuilderCommandAuditContext;
}

export interface PageDesignerDocumentChangeContext {
  readonly operation: Extract<PageArtifactOperationKind, 'document-update' | 'undo-command' | 'redo-command'>;
  readonly summary: string;
  readonly commandId?: string;
  readonly commandType?: Command['type'];
  readonly changedNodeIds?: readonly string[];
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
  readOnlyReason,
  auditContext,
}) => {
  const canEdit = phaseConfig?.allowEditing ?? true;
  const canAdd = phaseConfig?.allowAddComponent ?? true;
  const canDelete = phaseConfig?.allowDelete ?? true;
  const contracts = useMemo(() => getContractMap(), []);
  const palette = useMemo(() => getBuilderPalette(), []);
  const [paletteSearch, setPaletteSearch] = useState('');
  const [paletteCategory, setPaletteCategory] = useState('');
  const paletteCategories = useMemo(() => getBuilderPaletteCategories(palette), [palette]);
  const filteredPalette = useMemo(
    () =>
      getFilteredBuilderPalette(
        {
          query: paletteSearch,
          category: paletteCategory || undefined,
          phaseMode: phaseConfig?.mode,
          includeReadOnlyPhaseComponents: true,
        },
        palette
      ),
    [palette, paletteCategory, paletteSearch, phaseConfig?.mode]
  );
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
  const [importTemplateId, setImportTemplateId] = useState<ImportWizardTemplate['id']>('paste-code');
  const [importError, setImportError] = useState<string | null>(null);
  const [artifactRuntimeHealth, setArtifactRuntimeHealth] = useState<ArtifactCompilerRuntimeHealth | null>(null);
  const [importResiduals, setImportResiduals] = useState<readonly string[]>([]);
  const [importResidualArtifactId, setImportResidualArtifactId] = useState<string | null>(null);
  const [reviewingResidualId, setReviewingResidualId] = useState<string | null>(null);
  const [promotingResidualId, setPromotingResidualId] = useState<string | null>(null);
  const [registryCandidates, setRegistryCandidates] = useState<readonly RegistryCandidateSummary[]>([]);
  const [importReviewQueue, setImportReviewQueue] = useState<readonly ImportReviewQueueItem[]>([]);
  const [importReviewDecisions, setImportReviewDecisions] = useState<Readonly<Record<string, ImportReviewDecision>>>({});
  const [artifactGraphMergeReview, setArtifactGraphMergeReview] = useState<ArtifactGraphMergeReviewState | null>(null);
  const [importFidelity, setImportFidelity] = useState<
    import('./pageArtifactDocument').PageArtifactDocument['roundTripFidelity'] | null
  >(null);
  const [commandAuditWarning, setCommandAuditWarning] = useState<string | null>(null);
  const [dropFeedback, setDropFeedback] = useState<string | null>(null);
  const [commandHistoryState, setCommandHistoryState] = useState({
    canUndo: false,
    canRedo: false,
  });
  const importTextareaRef = useRef<HTMLTextAreaElement>(null);
  const auditContextRef = useRef<PageBuilderCommandAuditContext | undefined>(auditContext);

  // Governance trace tracker — scoped to this designer session
  const lineageTrackerRef = useRef(new AIActionLineageTracker());
  const aiChangeRecordRef = useRef(new Map<string, PageArtifactAIChangeRecord>());
  const initialDocumentPublishedRef = useRef(false);
  const [pendingAIActions, setPendingAIActions] = useState<readonly import('./pageArtifactDocument').AIActionLineage[]>([]);

  const validation = useMemo(() => validateDocument(document, contracts), [contracts, document]);
  const announceDropFeedback = useCallback((message: string): void => {
    setDropFeedback(message);
  }, []);
  const announceReadOnly = useCallback((): void => {
    announceDropFeedback(readOnlyReason ?? 'Page builder edits are unavailable in this canvas mode.');
  }, [announceDropFeedback, readOnlyReason]);

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
      onTelemetry: (event, data) => {
        const scopedAuditContext = auditContextRef.current;
        if (!scopedAuditContext) {
          return;
        }

        void emitPageBuilderCommandTelemetry(scopedAuditContext, event, data)
          .then(() => setCommandAuditWarning(null))
          .catch((error: unknown) => {
            const message = error instanceof Error ? error.message : 'Command telemetry failed.';
            setCommandAuditWarning(`Command telemetry could not be persisted: ${message}`);
          });
      },
      validate: (candidate) => validateDocument(candidate, contracts),
    });
  }

  useEffect(() => () => {
    commandServiceRef.current?.destroy();
  }, []);

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

  const refreshCommandHistoryState = useCallback((): void => {
    const commandService = commandServiceRef.current;
    setCommandHistoryState({
      canUndo: commandService?.canUndo() ?? false,
      canRedo: commandService?.canRedo() ?? false,
    });
  }, []);

  const publishDocument = useCallback(
    (nextDocument: BuilderDocument, context?: PageDesignerDocumentChangeContext) => {
      setDocument(nextDocument);
      onComponentsChange?.(builderDocumentToComponentData(nextDocument));
      onDocumentChange?.(
        nextDocument,
        validateDocument(nextDocument, contracts),
        context ?? {
          operation: 'document-update',
          summary: 'Updated page builder document from in-canvas editor.',
        },
      );
    },
    [contracts, onComponentsChange, onDocumentChange],
  );

  useEffect(() => {
    if (initialDocumentPublishedRef.current) {
      return;
    }

    initialDocumentPublishedRef.current = true;
    onComponentsChange?.(builderDocumentToComponentData(document));
    onDocumentChange?.(
      document,
      validation,
      {
        operation: 'document-update',
        summary: 'Published initial page builder document.',
      },
    );
  }, [document, onComponentsChange, onDocumentChange, validation]);

  const executeCommandResult = useCallback(
    (command: Command): CommandResult | null => {
      const result = commandServiceRef.current?.execute(command);
      if (!result?.success) {
        return null;
      }

      publishDocument(result.document, {
        operation: 'document-update',
        summary: `Executed page-builder command ${result.command.type}.`,
        commandId: result.command.id,
        commandType: result.command.type,
        changedNodeIds: result.changedNodeIds.map(String),
      });
      refreshCommandHistoryState();
      return result;
    },
    [publishDocument, refreshCommandHistoryState],
  );

  const executeCommand = useCallback(
    (command: Command): BuilderDocument | null => executeCommandResult(command)?.document ?? null,
    [executeCommandResult],
  );

  const handleUndoCommand = useCallback((): void => {
    if (!canEdit) {
      announceReadOnly();
      return;
    }

    const result = commandServiceRef.current?.undo();
    if (!result?.success) {
      announceDropFeedback(result?.error ?? 'No page-builder command is available to undo.');
      refreshCommandHistoryState();
      return;
    }

    publishDocument(result.document, {
      operation: 'undo-command',
      summary: `Undid page-builder command ${result.command.type}.`,
      commandId: result.command.id,
      commandType: result.command.type,
      changedNodeIds: result.changedNodeIds.map(String),
    });
    refreshCommandHistoryState();
    setDropFeedback(null);
  }, [announceDropFeedback, announceReadOnly, canEdit, publishDocument, refreshCommandHistoryState]);

  const handleRedoCommand = useCallback((): void => {
    if (!canEdit) {
      announceReadOnly();
      return;
    }

    const result = commandServiceRef.current?.redo();
    if (!result?.success) {
      announceDropFeedback(result?.error ?? 'No page-builder command is available to redo.');
      refreshCommandHistoryState();
      return;
    }

    publishDocument(result.document, {
      operation: 'redo-command',
      summary: `Redid page-builder command ${result.command.type}.`,
      commandId: result.command.id,
      commandType: result.command.type,
      changedNodeIds: result.changedNodeIds.map(String),
    });
    refreshCommandHistoryState();
    setDropFeedback(null);
  }, [announceDropFeedback, announceReadOnly, canEdit, publishDocument, refreshCommandHistoryState]);

  /**
   * Records an AI-originated node mutation into the session lineage tracker
   * and surfaces it in the governance panel.
   */
  const recordAIChange = useCallback(
    (record: PageArtifactAIChangeRecord) => {
      aiChangeRecordRef.current.set(record.lineage.actionId, record);
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
      if (!canEdit) {
        announceReadOnly();
        return;
      }
      const record = aiChangeRecordRef.current.get(actionId);
      if (decision === 'rejected' && record?.rollbackMetadata?.strategy === 'restore-builder-document') {
        const rollbackDocument = deserializeDocument(record.rollbackMetadata.serializedBuilderDocument);
        const rollbackResult = executeCommandResult({
          id: `rollback-ai-${actionId}-${Date.now()}`,
          type: 'import-document',
          timestamp: new Date().toISOString(),
          data: {
            document: rollbackDocument,
          },
        });
        if (!rollbackResult) {
          announceDropFeedback('Rejected automation change could not be rolled back; review decision was not recorded.');
          return;
        }
      }
      lineageTrackerRef.current.setReviewState(actionId, decision);
      setPendingAIActions(lineageTrackerRef.current.getPending());
      onAIReviewDecision?.(actionId, decision, record?.rollbackMetadata);
    },
    [announceDropFeedback, announceReadOnly, canEdit, executeCommandResult, onAIReviewDecision],
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
    if (!canAdd || !canEdit) {
      announceReadOnly();
      return;
    }
    setImportError(null);
    const trimmedInput = importInput.trim();
    const trimmedSourceLocator = guidedSourceLocator.trim();
    if (importWorkflowMode === 'semantic-model' && !trimmedInput) {
      setImportError('Paste a JSON semantic model to import.');
      return;
    }
    if (importWorkflowMode === 'semantic-model' && trimmedInput.startsWith('source:')) {
      setImportError('Source imports must use the governed source workflow. Choose Governed source instead of pasting source commands.');
      return;
    }
    if (importWorkflowMode === 'source' && !trimmedSourceLocator) {
      setImportError('Enter a source URL, route, Storybook story, artifact ID, or zip locator to import.');
      return;
    }

    let artifacts: readonly import('./pageArtifactDocument').PageArtifactDocument[];
    const sourceImportCommand = importWorkflowMode === 'source'
      ? { sourceType: guidedSourceType, source: trimmedSourceLocator }
      : null;
    if (sourceImportCommand) {
      if (!projectId) {
        setImportError('Source imports require an active project context.');
        return;
      }
      if (!auditContext?.tenantId || !auditContext.workspaceId) {
        setImportError('Source imports require authenticated tenant and workspace context.');
        return;
      }

      const runtimeHealth = await checkArtifactCompilerRuntimeHealth();
      setArtifactRuntimeHealth(runtimeHealth);
      if (runtimeHealth.status !== 'available') {
        setImportError(runtimeHealth.message);
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

    const rollbackDocument = commandServiceRef.current?.getDocument() ?? document;
    const rollbackMetadata: PageArtifactAIChangeRecord['rollbackMetadata'] = {
      strategy: 'restore-builder-document',
      serializedBuilderDocument: serializeDocument(rollbackDocument),
      capturedAt: new Date().toISOString(),
      reason: 'Restore the builder document that was active before importing the decompiled semantic model.',
    };

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
    setImportResidualArtifactId(first.artifactId);
    setImportFidelity(first.roundTripFidelity ?? null);
    setImportReviewQueue(buildImportReviewQueue(first.roundTripFidelity?.lossPoints, residuals));
    setImportReviewDecisions({});
    setArtifactGraphMergeReview(first.artifactGraph
      ? {
          artifactId: first.artifactId,
          graph: first.artifactGraph,
          status: 'required',
        }
      : null);
    setRegistryCandidates([]);

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
          rollbackMetadata,
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
  }, [announceReadOnly, auditContext?.tenantId, auditContext?.workspaceId, canAdd, canEdit, executeCommand, guidedSourceLocator, guidedSourceType, importInput, importWorkflowMode, onImportArtifacts, projectId, recordAIChange]);

  const selectedImportTemplate = useMemo(
    () => IMPORT_WIZARD_TEMPLATES.find((template) => template.id === importTemplateId) ?? IMPORT_WIZARD_TEMPLATES[0],
    [importTemplateId],
  );

  const handleImportTemplateSelect = useCallback((template: ImportWizardTemplate) => {
    setImportTemplateId(template.id);
    setImportWorkflowMode(template.mode);
    setImportError(null);

    if (template.mode === 'source' && template.sourceType) {
      setGuidedSourceType(template.sourceType);
      setGuidedSourceLocator('');
      return;
    }

    setImportInput('');
  }, []);

  const handleResidualIslandReview = useCallback(
    async (residualIslandId: string, decision: 'ACCEPTED' | 'REJECTED') => {
      if (!canEdit) {
        announceReadOnly();
        return;
      }
      if (!importResidualArtifactId) {
        setImportError('Cannot persist residual island review without an artifact id.');
        return;
      }

      setReviewingResidualId(residualIslandId);
      try {
        await persistResidualIslandReview({
          artifactId: importResidualArtifactId,
          residualIslandId,
          decision,
          notes:
            decision === 'ACCEPTED'
              ? 'Residual island accepted during PageDesigner import review.'
              : 'Residual island rejected during PageDesigner import review.',
        });
        setImportResiduals((current) => current.filter((id) => id !== residualIslandId));
        setImportReviewDecisions((current) => ({
          ...current,
          [`residual-${residualIslandId}`]: decision === 'ACCEPTED' ? 'applied' : 'skipped',
        }));
        setImportError(null);
      } catch (error) {
        setImportError(
          error instanceof Error
            ? error.message
            : `Could not persist residual island review for ${residualIslandId}.`,
        );
      } finally {
        setReviewingResidualId(null);
      }
    },
    [announceReadOnly, canEdit, importResidualArtifactId],
  );

  const handlePromoteRegistryCandidate = useCallback(
    async (residualIslandId: string) => {
      if (!canEdit) {
        announceReadOnly();
        return;
      }
      if (!importResidualArtifactId) {
        setImportError('Cannot promote residual island without an artifact id.');
        return;
      }

      const proposedContractName = buildRegistryCandidateContractName(residualIslandId);
      setPromotingResidualId(residualIslandId);
      try {
        const candidate = await promoteResidualIslandToRegistryCandidate({
          artifactId: importResidualArtifactId,
          residualIslandId,
          proposedContractName,
          source: 'decompiled-import',
          notes: 'Promote decompiled residual island to reviewed registry candidate.',
        });
        setRegistryCandidates((current) => [
          ...current.filter((item) => item.candidateId !== candidate.candidateId),
          candidate,
        ]);
        setImportResiduals((current) => current.filter((id) => id !== residualIslandId));
        setImportReviewDecisions((current) => ({
          ...current,
          [`residual-${residualIslandId}`]: 'promoted',
        }));
        setImportError(null);
      } catch (error) {
        setImportError(
          error instanceof Error
            ? error.message
            : `Could not promote residual island ${residualIslandId} to a registry candidate.`,
        );
      } finally {
        setPromotingResidualId(null);
      }
    },
    [announceReadOnly, canEdit, importResidualArtifactId],
  );

  const handleArtifactGraphMergeReview = useCallback(async () => {
    if (!canEdit) {
      announceReadOnly();
      return;
    }
    if (!artifactGraphMergeReview) {
      return;
    }
    if (!auditContext?.tenantId) {
      setArtifactGraphMergeReview((current) => current
        ? {
            ...current,
            status: 'failed',
            attemptedAt: new Date().toISOString(),
            error: 'Artifact graph merge review requires authenticated tenant context.',
          }
        : current);
      return;
    }

    setArtifactGraphMergeReview((current) => {
      if (!current) {
        return current;
      }
      const { error: _error, ...reviewWithoutError } = current;
      return {
        ...reviewWithoutError,
        status: 'running',
        attemptedAt: new Date().toISOString(),
      };
    });

    try {
      const result = await runArtifactGraphMergeReview(
        buildArtifactGraphMergeReviewRequest(artifactGraphMergeReview.graph, auditContext.tenantId),
      );
      setArtifactGraphMergeReview((current) => {
        if (!current) {
          return current;
        }
        const { error: _error, ...reviewWithoutError } = current;
        return {
          ...reviewWithoutError,
          status: result.conflictCount > 0 ? 'conflicts' : 'passed',
          result,
        };
      });
      setImportError(null);
    } catch (error) {
      const message = error instanceof Error
        ? error.message
        : 'Artifact graph merge review failed.';
      setArtifactGraphMergeReview((current) => current
        ? {
            ...current,
            status: 'failed',
            error: message,
          }
        : current);
      setImportError(message);
    }
  }, [announceReadOnly, artifactGraphMergeReview, auditContext?.tenantId, canEdit]);

  const handleImportReviewDecision = useCallback(
    async (reviewItem: ImportReviewQueueItem, decision: ImportReviewDecision) => {
      if (!canEdit) {
        announceReadOnly();
        return;
      }
      if (!importResidualArtifactId) {
        setImportError('Cannot persist import review decision without an artifact id.');
        return;
      }

      try {
        await persistImportReviewDecision({
          artifactId: importResidualArtifactId,
          reviewItemId: reviewItem.id,
          kind: reviewItem.kind,
          decision,
          label: reviewItem.label,
          details: reviewItem.details,
          notes: `Import review queue decision recorded from PageDesigner: ${decision}.`,
        });
        setImportReviewDecisions((current) => ({
          ...current,
          [reviewItem.id]: decision,
        }));
        setImportError(null);
      } catch (error) {
        setImportError(
          error instanceof Error
            ? error.message
            : `Could not persist import review decision for ${reviewItem.id}.`,
        );
      }
    },
    [announceReadOnly, canEdit, importResidualArtifactId],
  );

  const handleAddComponent = useCallback(
    (contractOrType: string) => {
      if (!canAdd) {
        announceReadOnly();
        return;
      }
      const contractName = normalizeContractName(contractOrType);
      const legacyType = toLegacyComponentType(contractName) as LegacyComponentType;
      const newComponent = getDefaultComponentData(legacyType) as ComponentData;
      const target = resolveInsertionTarget();
      const result = executeCommandResult({
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

      setSelectedId((result.changedNodeIds[0] as string | undefined) ?? null);
    },
    [announceReadOnly, canAdd, executeCommandResult, resolveInsertionTarget],
  );

  const handlePaletteDragStart = useCallback(
    (event: React.DragEvent<HTMLButtonElement>, contractName: string) => {
      if (!canAdd) {
        event.preventDefault();
        announceReadOnly();
        return;
      }
      event.dataTransfer.setData('application/x-page-component', contractName);
      event.dataTransfer.effectAllowed = 'copy';
    },
    [announceReadOnly, canAdd],
  );

  const handleDesignAreaDrop = useCallback(
    (event: React.DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      if (!canEdit) {
        announceReadOnly();
        return;
      }
      const contractName = event.dataTransfer.getData('application/x-page-component');
      if (contractName) {
        if (!canAdd) {
          announceReadOnly();
          return;
        }
        const normalizedName = normalizeContractName(contractName);
        const legacyType = toLegacyComponentType(normalizedName) as LegacyComponentType;
        const newComponent = getDefaultComponentData(legacyType) as ComponentData;
        const result = executeCommandResult({
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

        setSelectedId((result.changedNodeIds[0] as string | undefined) ?? null);
        return;
      }

      const draggedNodeId = event.dataTransfer.getData('application/x-page-node');
      if (draggedNodeId && document.nodes.has(draggedNodeId as NodeId)) {
        const result = executeCommandResult({
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

        setDropFeedback(null);
        setSelectedId(draggedNodeId);
      }
    },
    [announceDropFeedback, announceReadOnly, canAdd, canEdit, document, executeCommandResult],
  );

  const handleRendererDropRequest = useCallback(
    (request: DropRequest) => {
      if (!canEdit) {
        announceReadOnly();
        return;
      }
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
        if (!canAdd) {
          announceReadOnly();
          return;
        }
        const normalizedName = normalizeContractName(request.source.contractName);
        const legacyType = toLegacyComponentType(normalizedName) as LegacyComponentType;
        const newComponent = getDefaultComponentData(legacyType) as ComponentData;
        const destinationChildren = getNodeChildren(document, destinationParentId, destinationSlotName);
        const insertionIndex =
          request.placement === 'slot'
            ? destinationChildren.length
            : (targetLocation?.index ?? destinationChildren.length) +
              (request.placement === 'after' ? 1 : 0);
        const result = executeCommandResult({
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
      const result = executeCommandResult({
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

      setDropFeedback(null);
      setSelectedId(sourceNodeId);
    },
    [announceDropFeedback, announceReadOnly, canAdd, canEdit, document, executeCommandResult],
  );

  const handleKeyboardMoveRequest = useCallback(
    (request: KeyboardMoveRequest) => {
      if (!canEdit) {
        announceReadOnly();
        return;
      }
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
      } else if (request.direction === 'into') {
        const previousSiblingId = siblings[sourceLocation.index - 1];
        if (!previousSiblingId) {
          announceDropFeedback('Cannot move into a container because there is no previous component.');
          return;
        }

        const previousSibling = document.nodes.get(previousSiblingId);
        const slotName = previousSibling ? getDefaultSlotName(previousSibling.contractName) : undefined;
        if (!previousSibling || !isContainerContract(previousSibling.contractName) || !slotName) {
          announceDropFeedback('Cannot move into the previous component because it is not a container.');
          return;
        }

        if (isDescendant(document, request.nodeId, previousSiblingId)) {
          announceDropFeedback('Cannot move a component into one of its own descendants.');
          return;
        }

        newParentId = previousSiblingId;
        newSlotName = slotName;
        index = getNodeChildren(document, previousSiblingId, slotName).length;
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

      const result = executeCommandResult({
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

      setDropFeedback(null);
      setSelectedId(request.nodeId);
    },
    [announceDropFeedback, announceReadOnly, canEdit, document, executeCommandResult],
  );

  const handleDeleteComponent = useCallback(() => {
    if (!canDelete) {
      announceReadOnly();
      return;
    }
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
  }, [announceReadOnly, canDelete, executeCommand, selectedId]);

  const handleUpdateComponent = useCallback(
    (payload: {
      readonly props: Record<string, unknown>;
      readonly name?: string;
      readonly responsiveVariant?: ResponsiveVariant;
      readonly stateVariant?: StateVariant;
      readonly dataBinding?: Binding;
      readonly actionBinding?: Binding;
      readonly privacyClassification?: NonNullable<ComponentInstance['metadata']['dataClassification']>;
    }) => {
      if (!canEdit) {
        announceReadOnly();
        return;
      }
      if (!selectedInstance) {
        return;
      }

      let nextDocument = executeCommand({
        id: `update-props-${Date.now()}`,
        type: 'update-props',
        timestamp: new Date().toISOString(),
        data: {
          nodeId: selectedInstance.id,
          props: payload.props,
          name: payload.name,
          dataClassification: payload.privacyClassification,
        },
      });
      if (!nextDocument) {
        return;
      }

      if (payload.responsiveVariant) {
        nextDocument = executeCommand({
          id: `set-responsive-variant-${Date.now()}`,
          type: 'set-responsive-variant',
          timestamp: new Date().toISOString(),
          data: {
            nodeId: selectedInstance.id,
            variant: payload.responsiveVariant,
          },
        });
      }

      if (nextDocument && payload.stateVariant) {
        nextDocument = executeCommand({
          id: `set-state-variant-${Date.now()}`,
          type: 'set-state-variant',
          timestamp: new Date().toISOString(),
          data: {
            nodeId: selectedInstance.id,
            variant: payload.stateVariant,
          },
        });
      }

      if (nextDocument && payload.dataBinding) {
        nextDocument = executeCommand({
          id: `add-data-binding-${Date.now()}`,
          type: 'add-data-binding',
          timestamp: new Date().toISOString(),
          data: {
            nodeId: selectedInstance.id,
            binding: payload.dataBinding,
          },
        });
      }

      if (nextDocument && payload.actionBinding) {
        nextDocument = executeCommand({
          id: `add-action-binding-${Date.now()}`,
          type: 'add-action-binding',
          timestamp: new Date().toISOString(),
          data: {
            nodeId: selectedInstance.id,
            binding: payload.actionBinding,
          },
        });
      }

      if (!nextDocument) {
        return;
      }

      setDrawerOpen(false);
      setEditingId(null);
    },
    [announceReadOnly, canEdit, executeCommand, selectedInstance],
  );

  const handleNestSelectedIntoParent = useCallback(() => {
    if (!canEdit) {
      announceReadOnly();
      return;
    }
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
  }, [announceReadOnly, canEdit, document, executeCommand, selectedId, selectedInstance]);

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
              if (!canAdd || !canEdit) {
                announceReadOnly();
                return;
              }
              setImportInput('');
              setGuidedSourceLocator('');
              setImportTemplateId('paste-code');
              setImportError(null);
              setImportWorkflowMode('semantic-model');
              setImportPanelOpen(true);
            }}
            title="Import from code / Decompile"
            aria-label="Import from code"
            data-testid="page-designer-import-btn"
            disabled={!canAdd || !canEdit}
          >
            <Upload size={14} />
          </IconButton>
        </Box>
        <Stack spacing={1}>
          <Box className="mb-2 flex gap-2" role="group" aria-label="Page builder command history">
            <Button
              type="button"
              variant="outline"
              size="small"
              onClick={handleUndoCommand}
              disabled={!canEdit || !commandHistoryState.canUndo}
              data-testid="page-designer-undo-command"
            >
              Undo
            </Button>
            <Button
              type="button"
              variant="outline"
              size="small"
              onClick={handleRedoCommand}
              disabled={!canEdit || !commandHistoryState.canRedo}
              data-testid="page-designer-redo-command"
            >
              Redo
            </Button>
          </Box>
          <Input
            aria-label="Search registry components"
            value={paletteSearch}
            onChange={(event) => setPaletteSearch(event.target.value)}
            placeholder="Search components"
            className="w-full rounded-md border border-border bg-background px-2 py-1 text-sm"
            data-testid="page-component-search"
          />
          <Select
            aria-label="Filter registry category"
            value={paletteCategory}
            onChange={(event) => setPaletteCategory(event.target.value)}
            className="w-full rounded-md border border-border bg-background px-2 py-1 text-sm"
            data-testid="page-component-category"
          >
            <option value="">All categories</option>
            {paletteCategories.map((category) => (
              <option key={category} value={category}>
                {category}
              </option>
            ))}
          </Select>
          <Typography variant="caption" color="muted" data-testid="page-component-palette-summary">
            {filteredPalette.length} of {palette.length} registry components shown
          </Typography>
          {filteredPalette.map((entry) => (
            <Button
              key={entry.id}
              variant="outline"
              onClick={() => handleAddComponent(entry.name)}
              fullWidth
              className="justify-start"
              title={entry.tooltip}
              data-testid={`page-component-${entry.name.toLowerCase()}`}
              disabled={!canAdd}
              draggable={canAdd}
              onDragStart={(event) => handlePaletteDragStart(event, entry.name)}
            >
              {entry.featured ? `Recommended: ${entry.displayName}` : entry.displayName}
            </Button>
          ))}
          {filteredPalette.length === 0 ? (
            <Typography variant="caption" color="muted" data-testid="page-component-empty-filter">
              No registry components match this search and category.
            </Typography>
          ) : null}
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
            <Box
              className="mb-3 grid gap-2"
              role="list"
              aria-label="Import wizard templates"
              data-testid="page-designer-import-templates"
            >
              {IMPORT_WIZARD_TEMPLATES.map((template) => (
                <Button
                  key={template.id}
                  type="button"
                  variant={importTemplateId === template.id ? 'solid' : 'outline'}
                  size="small"
                  className="justify-start text-left"
                  onClick={() => handleImportTemplateSelect(template)}
                  data-testid={`page-import-template-${template.id}`}
                >
                  <span>
                    <span style={{ display: 'block', fontWeight: 600 }}>{template.label}</span>
                    <span style={{ display: 'block', fontSize: '0.72rem', opacity: 0.8 }}>{template.description}</span>
                  </span>
                </Button>
              ))}
            </Box>
            <Box className="mb-2 flex gap-2" role="group" aria-label="Import workflow mode">
              <Button
                variant={importWorkflowMode === 'semantic-model' ? 'solid' : 'outline'}
                size="small"
                onClick={() => {
                  setImportTemplateId('paste-code');
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
                  setImportTemplateId('import-route');
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
                <Select
                  value={guidedSourceType}
                  onChange={(event) => {
                    setImportTemplateId('import-route');
                    setGuidedSourceType(event.target.value as ImportSourceType);
                  }}
                  aria-label="Source import type"
                  data-testid="page-designer-source-type"
                  className="mb-2 w-full rounded border border-border bg-surface px-2 py-1 text-xs"
                >
                  <option value="tsx">TSX component</option>
                  <option value="route">Route file</option>
                  <option value="storybook">Storybook story</option>
                  <option value="artifact">Artifact ID</option>
                  <option value="zip">Zip archive</option>
                </Select>
                <Input
                  value={guidedSourceLocator}
                  onChange={(event) => {
                    setGuidedSourceLocator(event.target.value);
                    setImportError(null);
                  }}
                  placeholder={selectedImportTemplate.placeholder}
                  aria-label="Source locator"
                  data-testid="page-designer-source-locator"
                  className="w-full rounded border border-border bg-surface px-2 py-1 text-xs"
                />
                <Box
                  className="mt-2 rounded border border-border bg-surface p-2 text-xs"
                  data-testid="page-designer-artifact-runtime-status"
                >
                  <Typography variant="caption" style={{ display: 'block', fontWeight: 600 }}>
                    Artifact compiler runtime
                  </Typography>
                  <Typography variant="caption" color="muted" style={{ display: 'block', marginTop: 2 }}>
                    {artifactRuntimeHealth
                      ? artifactRuntimeHealth.message
                      : 'Required for governed source import. Health check runs before decompile.'}
                  </Typography>
                  <p className="sr-only" data-testid="import-trust-explanation">
                    Preview trust level determines whether imported source can be rendered directly, rendered in a controlled runtime, or held for review before preview.
                  </p>
                </Box>
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
                placeholder={selectedImportTemplate.placeholder}
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
              disabled={!canAdd || !canEdit}
            >
              Decompile &amp; load
            </Button>
          </Box>
        )}

        {importReviewQueue.length > 0 && (
          <Box
            className="mt-4 rounded-lg border border-info-border bg-info-bg p-3"
            data-testid="page-designer-import-review-queue"
          >
            <Typography variant="caption" style={{ fontWeight: 600, display: 'block', marginBottom: 4 }}>
              Import review queue: {Object.keys(importReviewDecisions).length}/{importReviewQueue.length} decided
            </Typography>
            <p className="sr-only" data-testid="import-review-gate-explanation">
              Import review gates identify decompile loss points and residual islands that need an explicit apply or skip decision before the imported artifact should be trusted.
            </p>
            {importReviewQueue.map((item) => {
              const decision = importReviewDecisions[item.id];
              return (
                <Box key={item.id} className="mt-2 rounded-md border border-info-border bg-white p-2">
                  <Typography variant="caption" style={{ display: 'block', fontWeight: 600 }}>
                    {item.kind === 'loss-point' ? 'Loss point' : 'Residual island'}: {item.label}
                  </Typography>
                  <Typography variant="caption" color="muted" style={{ display: 'block', marginTop: 2 }}>
                    {item.details}
                  </Typography>
                  <Box
                    className="mt-2 grid gap-2 md:grid-cols-2"
                    data-testid={`page-designer-import-review-diff-${item.id}`}
                  >
                    <Box className="rounded border border-info-border bg-surface p-2">
                      <Typography variant="caption" style={{ display: 'block', fontWeight: 600 }}>
                        Source evidence
                      </Typography>
                      <Typography variant="caption" color="muted" style={{ display: 'block', marginTop: 2 }}>
                        {item.sourceEvidence}
                      </Typography>
                    </Box>
                    <Box className="rounded border border-info-border bg-surface p-2">
                      <Typography variant="caption" style={{ display: 'block', fontWeight: 600 }}>
                        Governed builder impact
                      </Typography>
                      <Typography variant="caption" color="muted" style={{ display: 'block', marginTop: 2 }}>
                        {item.governedEvidence}
                      </Typography>
                    </Box>
                  </Box>
                  <Typography variant="caption" color="muted" style={{ display: 'block', marginTop: 6 }}>
                    Review impact: {item.reviewImpact}
                  </Typography>
                  {decision ? (
                    <Typography
                      variant="caption"
                      color={decision === 'applied' || decision === 'promoted' ? 'success' : 'warning'}
                      data-testid={`page-designer-import-review-decision-${item.id}`}
                      style={{ display: 'block', marginTop: 6 }}
                    >
                      Decision: {decision}
                    </Typography>
                  ) : (
                    <Box className="mt-2 flex gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        size="small"
                        data-testid={`page-designer-import-review-apply-${item.id}`}
                        onClick={() => void handleImportReviewDecision(item, 'applied')}
                      >
                        Apply item
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="small"
                        data-testid={`page-designer-import-review-skip-${item.id}`}
                        onClick={() => void handleImportReviewDecision(item, 'skipped')}
                      >
                        Skip item
                      </Button>
                    </Box>
                  )}
                </Box>
              );
            })}
          </Box>
        )}

        {artifactGraphMergeReview ? (
          <Box
            className="mt-4 rounded-lg border border-info-border bg-info-bg p-3"
            data-testid="page-designer-graph-merge-review"
          >
            <Typography variant="caption" style={{ fontWeight: 600, display: 'block', marginBottom: 4 }}>
              Artifact graph merge review: {artifactGraphMergeReview.status}
            </Typography>
            <Typography variant="caption" color="muted" style={{ display: 'block', marginBottom: 6 }}>
              Graph {artifactGraphMergeReview.graph.graphId} includes {artifactGraphMergeReview.graph.nodes.length} nodes, {artifactGraphMergeReview.graph.edges.length} edges, and {artifactGraphMergeReview.graph.provenance.residualIslandIds.length} residual island references. Run merge review before trusting graph-wide handoff.
            </Typography>
            {artifactGraphMergeReview.result ? (
              <Typography
                variant="caption"
                color={artifactGraphMergeReview.result.conflictCount > 0 ? 'warning' : 'success'}
                data-testid="page-designer-graph-merge-review-result"
                style={{ display: 'block', marginBottom: 6 }}
              >
                Merge result: {artifactGraphMergeReview.result.conflictCount} conflict{artifactGraphMergeReview.result.conflictCount === 1 ? '' : 's'} · {artifactGraphMergeReview.result.message}
              </Typography>
            ) : null}
            {artifactGraphMergeReview.error ? (
              <Typography
                variant="caption"
                color="danger"
                data-testid="page-designer-graph-merge-review-error"
                style={{ display: 'block', marginBottom: 6 }}
              >
                {artifactGraphMergeReview.error}
              </Typography>
            ) : null}
            <Button
              type="button"
              variant="outline"
              size="small"
              disabled={!canEdit || artifactGraphMergeReview.status === 'running'}
              data-testid="page-designer-graph-merge-review-run"
              onClick={() => {
                void handleArtifactGraphMergeReview();
              }}
            >
              {artifactGraphMergeReview.status === 'failed' || artifactGraphMergeReview.status === 'conflicts'
                ? 'Retry graph merge review'
                : 'Run graph merge review'}
            </Button>
          </Box>
        ) : null}

        {registryCandidates.length > 0 && (
          <Box
            className="mt-4 rounded-lg border border-success-border bg-success-bg p-3"
            data-testid="page-designer-registry-candidates"
          >
            <Typography variant="caption" style={{ fontWeight: 600, display: 'block', marginBottom: 4 }}>
              Registry candidates: {registryCandidates.length} awaiting review
            </Typography>
            <Typography variant="caption" color="muted" style={{ display: 'block', marginBottom: 6 }}>
              Decompiled components promoted from residual islands are now queued for reviewed registry contract authoring.
            </Typography>
            {registryCandidates.map((candidate) => (
              <Box
                key={candidate.candidateId}
                className="mt-2 rounded-md border border-success-border bg-white p-2"
                data-testid={`page-designer-registry-candidate-${candidate.residualIslandId}`}
              >
                <Typography variant="caption" style={{ display: 'block', fontWeight: 600 }}>
                  {candidate.proposedContractName}
                </Typography>
                <Typography variant="caption" color="muted" style={{ display: 'block', marginTop: 2 }}>
                  Source residual: {candidate.residualIslandId} · Status: {candidate.status.toLowerCase().replace('_', ' ')}
                </Typography>
                <Typography variant="caption" color="muted" style={{ display: 'block', marginTop: 2 }}>
                  Audit record: {candidate.auditRecordId}
                </Typography>
              </Box>
            ))}
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
            <p className="sr-only" data-testid="residual-island-explanation">
              Residual islands are imported elements that could not be mapped to a reviewed registry contract and must be accepted or rejected by an operator.
            </p>
            {importResiduals.map((id) => (
              <Box key={id} className="mt-2 rounded-md border border-warning-border bg-white p-2">
                <Typography variant="caption" style={{ display: 'block', fontFamily: 'monospace', fontSize: '0.7rem' }}>
                  {id}
                </Typography>
                <Box className="mt-2 flex gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="small"
                    disabled={!canEdit || promotingResidualId === id}
                    data-testid={`page-designer-residual-promote-${id}`}
                    onClick={() => {
                      void handlePromoteRegistryCandidate(id);
                    }}
                  >
                    Promote to registry candidate
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="small"
                    disabled={!canEdit || reviewingResidualId === id}
                    data-testid={`page-designer-residual-accept-${id}`}
                    onClick={() => {
                      void handleResidualIslandReview(id, 'ACCEPTED');
                    }}
                  >
                    Accept residual
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="small"
                    disabled={!canEdit || reviewingResidualId === id}
                    data-testid={`page-designer-residual-reject-${id}`}
                    onClick={() => {
                      void handleResidualIslandReview(id, 'REJECTED');
                    }}
                  >
                    Reject residual
                  </Button>
                </Box>
              </Box>
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
              <Button variant="outline" size="small" onClick={handleNestSelectedIntoParent} disabled={!canEdit}>
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
            initialBindings={editingInstance.bindings}
            initialResponsiveVariants={editingInstance.metadata.responsiveVariants}
            initialStateVariants={editingInstance.metadata.stateVariants}
            initialDataClassification={
              editingInstance.metadata.dataClassification ??
              document.metadata.dataClassification
            }
            onUpdate={handleUpdateComponent}
            readOnly={!canEdit}
            readOnlyReason={readOnlyReason}
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
                    disabled={!canEdit}
                  >
                    Accept
                  </Button>
                  <Button
                    variant="outline"
                    size="small"
                    onClick={() => handleAIActionReview(action.actionId, 'rejected')}
                    data-testid={`ai-action-reject-${action.actionId}`}
                    disabled={!canEdit}
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
